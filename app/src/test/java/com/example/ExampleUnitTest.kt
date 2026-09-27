package com.example

import com.example.data.local.AiAuditLogEntity
import com.example.data.local.MediaClusterType
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.ScreenshotSubType
import com.example.data.local.TokenUsageSource
import com.example.domain.ai.AiAdapterConfig
import com.example.domain.ai.AiProviderType
import com.example.domain.ai.AiWorkflowsAndUsageEngine
import com.example.domain.ai.UsageRangeOption
import com.example.domain.rules.NaturalLanguageRuleEngine
import com.example.domain.scanner.CandidateIndexEngine
import com.example.domain.scanner.MediaScannerEngine
import com.example.domain.scanner.ScanPhase
import com.example.domain.scanner.ScanProgress
import com.example.domain.scanner.VideoFrameSample
import com.example.domain.scanner.VideoSignatureAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun naturalLanguageRule_parsesAgeAndExclusionsAndProtectsReceipts() {
        val prompt =
            "Screenshots older than 90 days can be suggested for deletion unless they contain receipts, passwords, addresses, important conversations, or confirmation numbers."
        val rule = NaturalLanguageRuleEngine.parseNaturalLanguageToRule(prompt)

        assertEquals(90, rule.minAgeDays)
        assertTrue(rule.targetCategoriesCsv.contains("SCREENSHOT"))
        assertTrue(rule.excludedKeywordsCsv.contains("receipt"))
        assertTrue(rule.excludedKeywordsCsv.contains("password"))
        assertTrue(rule.excludedKeywordsCsv.contains("confirmation"))

        val now = System.currentTimeMillis()
        val dayMs = 1000L * 60 * 60 * 24

        val oldOtpScreenshot = PhotoEntity(
            id = 1,
            uriString = "file:///tmp/otp.png",
            title = "Screenshot_OTP.png",
            folderName = "Screenshots",
            dateTakenEpochMs = now - 180 * dayMs,
            fileSizeBytes = 2_000_000L,
            width = 1080,
            height = 2400,
            dHash = "8f8f0f0f8f8f0f0f",
            pHash = "8f8f0f0f8f8f0f0f",
            sharpnessScore = 80,
            laplacianVariance = 120f,
            exposureScore = 85,
            framingScore = 75,
            overallQualityScore = 80,
            category = PhotoCategory.SCREENSHOT.name,
            screenshotSubType = ScreenshotSubType.TEMPORARY_OTP.name,
            ocrText = "Temporary login code 491022",
            junkConfidence = 0.95f
        )

        val oldFlightConfirmation = oldOtpScreenshot.copy(
            id = 2,
            title = "Screenshot_Flight_Confirmation.png",
            screenshotSubType = ScreenshotSubType.CONFIRMATION_NUMBER.name,
            ocrText = "Booking Confirmation Number #DL-7749K"
        )

        val preview = NaturalLanguageRuleEngine.evaluateRule(
            rule = rule,
            allPhotos = listOf(oldOtpScreenshot, oldFlightConfirmation)
        )

        assertEquals(1, preview.matchedForCleanup.size)
        assertEquals(1L, preview.matchedForCleanup.first().id)
        assertEquals(1, preview.protectedByExclusions.size)
        assertEquals(2L, preview.protectedByExclusions.first().first.id)
    }

    @Test
    fun semanticSearch_matchesNaturalLanguageQueries() {
        val dogPhoto = PhotoEntity(
            id = 10,
            uriString = "file:///tmp/dog.jpg",
            title = "IMG_DOG_PARK.jpg",
            folderName = "Camera",
            dateTakenEpochMs = System.currentTimeMillis(),
            fileSizeBytes = 4_000_000L,
            width = 4000,
            height = 3000,
            dHash = "1122334455667788",
            pHash = "1122334455667788",
            sharpnessScore = 94,
            laplacianVariance = 240f,
            exposureScore = 90,
            framingScore = 88,
            overallQualityScore = 92,
            category = PhotoCategory.PET.name,
            aiDescription = "Golden retriever dog playing outside on sunlit grass",
            semanticTags = "dog,outside,park,pet"
        )

        val results = NaturalLanguageRuleEngine.semanticSearch(
            query = "pictures of my dog outside",
            allPhotos = listOf(dogPhoto)
        )
        assertEquals(1, results.size)
        assertEquals(10L, results.first().id)
    }

    @Test
    fun candidateIndexEngine_scalesLinearlyFor1k10kAnd25kItemsWithoutQuadraticExplosion() {
        val scanner = MediaScannerEngine()

        fun buildSyntheticLibrary(count: Int): List<PhotoEntity> {
            val baseTime = 1_750_000_000_000L
            return List(count) { idx ->
                // Plant a genuine 2-item burst every 500 items; all other items have distinct hashes & timestamps
                val isPlantedSecond = idx % 500 == 1
                val seedIdx = if (isPlantedSecond) idx - 1 else idx
                val hashA = String.format("%016x", (seedIdx.toLong() + 1L) * -7046029254386353131L)
                val hashB = String.format("%016x", (seedIdx.toLong() + 1L) * 6364136223846793005L)
                val captureTime = if (isPlantedSecond) {
                    baseTime + (seedIdx * 600_000L) + 1_500L
                } else {
                    baseTime + (seedIdx * 600_000L)
                }
                PhotoEntity(
                    id = (idx + 1).toLong(),
                    uriString = "content://media/external/images/media/${idx + 1}",
                    title = "IMG_${idx + 1}.jpg",
                    folderName = "Camera",
                    dateTakenEpochMs = captureTime,
                    capturedAtEpochMs = captureTime,
                    fileSizeBytes = 2_500_000L + (seedIdx % 7) * 50_000L,
                    width = 4000,
                    height = 3000,
                    dHash = hashA,
                    pHash = hashB,
                    sharpnessScore = if (isPlantedSecond) 72 else 91,
                    laplacianVariance = 300f,
                    exposureScore = 82,
                    framingScore = 80,
                    overallQualityScore = if (isPlantedSecond) 74 else 90,
                    category = PhotoCategory.LANDSCAPE.name
                )
            }
        }

        for (size in listOf(1_000, 10_000, 25_000)) {
            val library = buildSyntheticLibrary(size)
            val clustered = scanner.clusterAndNominateBestShots(library)
            val stats = scanner.lastCandidateIndexStats
            assertNotNull(stats)
            assertEquals(size, stats!!.totalItems)
            // Must eliminate >99% of theoretical O(N^2) pairs
            assertTrue(
                "Expected >99% reduction for N=$size, got ${stats.reductionPercent}%",
                stats.reductionPercent > 99.0f
            )
            // Verify planted duplicate bursts were discovered accurately
            val expectedClusters = size / 500
            val actualClusters = clustered.mapNotNull { it.duplicateClusterId }.distinct().size
            assertEquals(expectedClusters, actualClusters)
        }
    }

    @Test
    fun multiFrameVideoFingerprinting_rejectsBlackFirstFramesStaticScreenUisAndSharedTransitions() {
        val scanner = MediaScannerEngine()
        val now = 1_750_000_000_000L

        fun makeSample(frac: Float, dHash: String, pHash: String, bits: Int = 32): VideoFrameSample {
            return VideoFrameSample(
                positionFraction = frac,
                dHash = dHash,
                pHash = pHash,
                sharpnessScore = 80,
                exposureScore = 80,
                framingScore = 80,
                laplacianVariance = 350f,
                setBitCount = bits
            )
        }

        // Case 1: Two unrelated videos that BOTH start with a black frame at 10% and similar transition at 30%,
        // but diverge at 50%, 70%, 90% MUST NOT cluster.
        val videoASamples = listOf(
            makeSample(0.10f, "0000000000000000", "0000000000000000", bits = 0),
            makeSample(0.30f, "1111222233334444", "1111222233334444"),
            makeSample(0.50f, "aaaa5555aaaa5555", "aaaa5555aaaa5555"),
            makeSample(0.70f, "f0f0f0f00f0f0f0f", "f0f0f0f00f0f0f0f"),
            makeSample(0.90f, "123456789abcdef0", "123456789abcdef0")
        )
        val videoBSamples = listOf(
            makeSample(0.10f, "0000000000000000", "0000000000000000", bits = 0),
            makeSample(0.30f, "1111222233334444", "1111222233334444"),
            makeSample(0.50f, "00ff00ff00ff00ff", "00ff00ff00ff00ff"),
            makeSample(0.70f, "9999666699996666", "9999666699996666"),
            makeSample(0.90f, "fedcba9876543210", "fedcba9876543210")
        )

        val baseVideoA = PhotoEntity(
            id = 501L,
            uriString = "content://media/external/video/media/501",
            title = "Clip_A.mp4",
            folderName = "Camera",
            mediaType = "VIDEO",
            mimeType = "video/mp4",
            durationMs = 20_000L,
            fileSizeBytes = 18_000_000L,
            width = 1920,
            height = 1080,
            dateTakenEpochMs = now,
            capturedAtEpochMs = now,
            dHash = "aaaa5555aaaa5555",
            pHash = "aaaa5555aaaa5555",
            videoSignatureHashes = VideoSignatureAnalyzer.encodeFrameSamples(videoASamples),
            sharpnessScore = 82,
            laplacianVariance = 350f,
            exposureScore = 80,
            framingScore = 80,
            overallQualityScore = 82,
            category = PhotoCategory.VIDEO.name
        )
        val baseVideoB = baseVideoA.copy(
            id = 502L,
            uriString = "content://media/external/video/media/502",
            title = "Clip_B.mp4",
            dateTakenEpochMs = now + 5_000L,
            capturedAtEpochMs = now + 5_000L,
            dHash = "00ff00ff00ff00ff",
            pHash = "00ff00ff00ff00ff",
            videoSignatureHashes = VideoSignatureAnalyzer.encodeFrameSamples(videoBSamples)
        )
        assertFalse(scanner.areCandidatesSimilar(baseVideoA, baseVideoB))

        // Case 2: Two static screen recordings that share the same static app chrome/status bar
        // but have different durations/sizes MUST NOT cluster.
        val staticScreenSamplesA = List(5) { idx ->
            makeSample(VideoSignatureAnalyzer.STANDARD_SAMPLE_FRACTIONS[idx], "3c3c3c3c3c3c3c3c", "5a5a5a5a5a5a5a5a")
        }
        val staticScreenSamplesB = List(5) { idx ->
            // Slightly different text in middle (distance 5 -> rejected by static UI threshold <= 3)
            makeSample(VideoSignatureAnalyzer.STANDARD_SAMPLE_FRACTIONS[idx], "3c3c3c3c3c3c3c03", "5a5a5a5a5a5a5a05")
        }
        val screenRecA = baseVideoA.copy(
            id = 503L,
            uriString = "content://media/external/video/media/503",
            title = "Screen_Recording_1.mp4",
            folderName = "ScreenRecorder",
            width = 1080,
            height = 2400,
            dHash = "3c3c3c3c3c3c3c3c",
            pHash = "5a5a5a5a5a5a5a5a",
            videoSignatureHashes = VideoSignatureAnalyzer.encodeFrameSamples(staticScreenSamplesA)
        )
        val screenRecB = screenRecA.copy(
            id = 504L,
            uriString = "content://media/external/video/media/504",
            title = "Screen_Recording_2.mp4",
            dHash = "3c3c3c3c3c3c3c03",
            pHash = "5a5a5a5a5a5a5a05",
            videoSignatureHashes = VideoSignatureAnalyzer.encodeFrameSamples(staticScreenSamplesB)
        )
        assertFalse(scanner.areCandidatesSimilar(screenRecA, screenRecB))

        // Case 3: Genuine duplicate/re-encoded video matching across all 5 timeline checkpoints DOES cluster
        val genuineRetakeB = baseVideoA.copy(
            id = 505L,
            uriString = "content://media/external/video/media/505",
            title = "Clip_A_WhatsApp_Copy.mp4",
            fileSizeBytes = 16_500_000L,
            sharpnessScore = 70,
            overallQualityScore = 71,
            motionStabilityScore = 65
        )
        assertTrue(scanner.areCandidatesSimilar(baseVideoA, genuineRetakeB))
        val clusteredVideos = scanner.clusterAndNominateBestShots(listOf(baseVideoA, genuineRetakeB))
        val winner = clusteredVideos.first { it.id == 501L }
        assertTrue(winner.isBestShotInCluster)
        assertTrue(
            winner.clusterTypeEnum == MediaClusterType.NEAR_DUPLICATE ||
                winner.clusterTypeEnum == MediaClusterType.SIMILAR_VIDEO
        )
    }

    @Test
    fun typedMediaClustersAndScanProgressFormattingAndSizeDurationRulesWork() {
        // 1. ScanProgress userFacingHeadline formatting
        val progress = ScanProgress(
            phase = ScanPhase.LOCAL_QUALITY_ANALYSIS,
            discovered = 8_431,
            analyzed = 742,
            total = 8_431
        )
        assertEquals("Analyzing 742 / 8,431", progress.userFacingHeadline)

        // 2. NaturalLanguageRuleEngine size and video duration parsing
        val rule = NaturalLanguageRuleEngine.parseNaturalLanguageToRule(
            "Videos over 20MB and longer than 30 seconds older than 14 days"
        )
        assertEquals(14, rule.minAgeDays)
        assertEquals(20L * 1024L * 1024L, rule.minFileSizeBytes)
        assertEquals(30_000L, rule.minDurationMs)

        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val bigLongVideo = PhotoEntity(
            id = 901L,
            uriString = "content://media/external/video/media/901",
            title = "Big_4K_Clip.mp4",
            folderName = "Camera",
            mediaType = "VIDEO",
            mimeType = "video/mp4",
            durationMs = 45_000L,
            fileSizeBytes = 32L * 1024L * 1024L,
            width = 3840,
            height = 2160,
            dateTakenEpochMs = now - 30 * dayMs,
            capturedAtEpochMs = now - 30 * dayMs,
            dHash = "1122334455667788",
            pHash = "8877665544332211",
            sharpnessScore = 80,
            laplacianVariance = 300f,
            exposureScore = 80,
            framingScore = 80,
            overallQualityScore = 80,
            category = PhotoCategory.VIDEO.name
        )
        val shortSmallVideo = bigLongVideo.copy(
            id = 902L,
            uriString = "content://media/external/video/media/902",
            durationMs = 8_000L,
            fileSizeBytes = 4L * 1024L * 1024L
        )
        val eval = NaturalLanguageRuleEngine.evaluateRule(rule, listOf(bigLongVideo, shortSmallVideo))
        assertEquals(1, eval.matchedForCleanup.size)
        assertEquals(901L, eval.matchedForCleanup.first().id)
    }

    @Test
    fun pass3UsageAccountingMidnightRolloverCleanupPlansAndTitlesWork() {
        val tz = java.util.TimeZone.getTimeZone("America/Los_Angeles")
        val nowMs = 1_758_980_000_000L // fixed timestamp
        val midnightMs = AiWorkflowsAndUsageEngine.computeLocalMidnightEpochMs(nowMs, tz)
        assertTrue(midnightMs <= nowMs)

        val todayActualLog = AiAuditLogEntity(
            timestampEpochMs = midnightMs + 3_600_000L,
            providerId = "NanoGPT",
            modelName = "google/gemini-2.0-flash-001",
            photoTitle = "IMG_101.jpg",
            downscaledDimension = "512x384",
            payloadBytes = 42_000,
            estimatedTokens = 600,
            servedFromEncryptedCache = false,
            verdictSummary = "Analyzed",
            promptTokens = 480,
            completionTokens = 120,
            totalTokens = 600,
            tokenUsageSource = TokenUsageSource.ACTUAL_FROM_PROVIDER.name,
            requestSucceeded = true,
            providerType = AiProviderType.NANOGPT.name
        )
        val todayEstimatedLog = todayActualLog.copy(
            timestampEpochMs = midnightMs + 7_200_000L,
            photoTitle = "IMG_102.jpg",
            promptTokens = 300,
            completionTokens = 80,
            totalTokens = 380,
            tokenUsageSource = TokenUsageSource.ESTIMATED.name
        )
        val todayCacheHitLog = todayActualLog.copy(
            timestampEpochMs = midnightMs + 8_000_000L,
            photoTitle = "IMG_103.jpg",
            servedFromEncryptedCache = true,
            promptTokens = 0,
            completionTokens = 0,
            totalTokens = 0,
            tokenUsageSource = TokenUsageSource.LOCAL_CACHE_ZERO.name
        )
        val threeDaysAgoLog = todayActualLog.copy(
            timestampEpochMs = midnightMs - 3L * 86_400_000L,
            photoTitle = "IMG_OLD.jpg",
            promptTokens = 1000,
            completionTokens = 250,
            totalTokens = 1250
        )

        val logs = listOf(todayActualLog, todayEstimatedLog, todayCacheHitLog, threeDaysAgoLog)
        val cfg = AiAdapterConfig(
            activeProfileName = "NanoGPT",
            activeProvider = AiProviderType.NANOGPT,
            selectedModel = "google/gemini-2.0-flash-001"
        )

        val todaySummary = AiWorkflowsAndUsageEngine.computeTodayUsageSummary(logs, cfg, nowMs, tz)
        assertEquals(980L, todaySummary.totalTokens)
        assertEquals(780L, todaySummary.inputTokens)
        assertEquals(200L, todaySummary.outputTokens)
        assertEquals(2, todaySummary.requestCount)
        assertEquals(1, todaySummary.actualRequestCount)
        assertEquals(1, todaySummary.estimatedRequestCount)
        assertEquals(1, todaySummary.cachedHitCount)

        val sevenDaySummary = AiWorkflowsAndUsageEngine.computeUsageSummary(logs, UsageRangeOption.LAST_7_DAYS, cfg, nowMs, tz)
        assertEquals(2230L, sevenDaySummary.totalTokens)
        assertEquals(3, sevenDaySummary.requestCount)

        // Verify Natural-Language Cleanup Plan & Protected Safety Breakdown (Pass 3 Section J)
        val dayMs = 86_400_000L
        val oldTempScreenshot = PhotoEntity(
            id = 11L,
            uriString = "content://media/external/images/media/11",
            title = "Screenshot_Meme.png",
            folderName = "Screenshots",
            dateTakenEpochMs = nowMs - 210 * dayMs,
            capturedAtEpochMs = nowMs - 210 * dayMs,
            fileSizeBytes = 3_500_000L,
            width = 1080,
            height = 2400,
            dHash = "1111222233334444",
            pHash = "1111222233334444",
            sharpnessScore = 80,
            laplacianVariance = 250f,
            exposureScore = 80,
            framingScore = 80,
            overallQualityScore = 80,
            category = PhotoCategory.SCREENSHOT.name,
            screenshotSubType = ScreenshotSubType.MEME_REPOST.name,
            junkConfidence = 0.85f
        )
        val oldReceiptScreenshot = oldTempScreenshot.copy(
            id = 12L,
            uriString = "content://media/external/images/media/12",
            title = "Screenshot_Receipt.png",
            screenshotSubType = ScreenshotSubType.RECEIPT_INVOICE.name,
            ocrText = "Order Total $48.99 Receipt"
        )
        val oldConfirmationScreenshot = oldTempScreenshot.copy(
            id = 13L,
            uriString = "content://media/external/images/media/13",
            title = "Screenshot_Confirm.png",
            screenshotSubType = ScreenshotSubType.CONFIRMATION_NUMBER.name,
            ocrText = "Confirmation number #AB1928"
        )

        val plan = AiWorkflowsAndUsageEngine.generateCleanupPlan(
            naturalQuery = "Find screenshots older than 6 months that probably aren't important",
            allPhotos = listOf(oldTempScreenshot, oldReceiptScreenshot, oldConfirmationScreenshot)
        )
        assertEquals("Screenshots older than 6 months", plan.title)
        assertEquals(1, plan.candidates.size)
        assertEquals(11L, plan.candidates.first().id)
        assertEquals(1, plan.protectedBreakdown.receiptsCount)
        assertEquals(1, plan.protectedBreakdown.confirmationNumbersCount)

        // Verify Descriptive Cluster Titles & Neutral Fallbacks (Pass 3 Section M)
        val amazonShot1 = oldTempScreenshot.copy(
            id = 21L,
            ocrText = "Amazon.com Order Confirmation shipped",
            aiDescription = ""
        )
        val amazonShot2 = oldTempScreenshot.copy(
            id = 22L,
            ocrText = "Amazon.com Order Details",
            aiDescription = ""
        )
        val amazonTitle = AiWorkflowsAndUsageEngine.generateDescriptiveClusterTitle(
            members = listOf(amazonShot1, amazonShot2),
            clusterType = MediaClusterType.SCREENSHOT_VARIANT
        )
        assertEquals("Amazon order screenshots", amazonTitle)

        // Verify Best-Shot Explanations & Sanitization (Pass 3 Section N: no dHash/pHash/Laplacian)
        val sanitized = AiWorkflowsAndUsageEngine.sanitizeReasonForNormalUi(
            "Sharper subject • dHash=55aa55aa • pHash distance 2 • Laplacian=420.5"
        )
        assertFalse(sanitized.contains("dHash", ignoreCase = true))
        assertFalse(sanitized.contains("pHash", ignoreCase = true))
        assertFalse(sanitized.contains("Laplacian", ignoreCase = true))
        assertTrue(sanitized.contains("Sharper subject"))
    }

    @Test
    fun pass4StealthGraphiteDesignSystemAndSwipePhysics_maintainConsumerSpec() {
        assertEquals(0xFF08090BL, com.example.ui.theme.RoboColors.Background.value.shr(32).toLong())
        assertEquals(0xFF168BFFL, com.example.ui.theme.RoboColors.ElectricBlue.value.shr(32).toLong())
        assertTrue(com.example.ui.theme.RoboMotion.SWIPE_MAX_ROTATION_DEG in 5f..8f)
        assertTrue(com.example.ui.theme.RoboMotion.SWIPE_COMMIT_DISTANCE_FRACTION in 0.25f..0.40f)
        assertTrue(com.example.ui.theme.RoboMotion.CLUSTER_COLLAPSE_MS in 350..650)
        assertTrue(com.example.ui.theme.RoboMotion.BEST_SHOT_BADGE_MS in 250..350)
    }
}
