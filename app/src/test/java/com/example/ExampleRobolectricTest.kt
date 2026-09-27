package com.example

import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LuminaDatabase
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.ScreenshotSubType
import com.example.data.local.TokenUsageSource
import com.example.data.local.TriageStatus
import com.example.data.repository.LuminaRepository
import com.example.data.security.EncryptedMediaCache
import com.example.domain.ai.AiAdapterConfig
import com.example.domain.ai.AiProviderType
import com.example.domain.ai.ByokAiAdapterLayer
import com.example.domain.ai.ByokConnectionProfile
import com.example.domain.ai.SubscriptionInclusionStatus
import com.example.domain.rules.NaturalLanguageRuleEngine
import com.example.domain.scanner.DeletionExecutionOutcome
import com.example.domain.scanner.MediaDeletionCoordinator
import com.example.domain.scanner.MediaIdentityVerifier
import com.example.domain.scanner.MediaMetadataReader
import com.example.domain.scanner.MediaScanDecision
import com.example.domain.scanner.MediaScannerEngine
import com.example.domain.scanner.SourceDeleteAttemptResult
import com.example.domain.scanner.SourceMediaDeletionGateway
import com.example.domain.scanner.SourceMediaMetadata
import com.example.domain.scanner.VerifiedUndoEntry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun appNameAndByokProfilesAndScannerForensicsWork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("RoboPhoto", appName)

        // Verify AES-256-GCM EncryptedMediaCache round-trip & security description
        val cache = EncryptedMediaCache(context)
        cache.put("test_dhash_key", "verified_encrypted_payload")
        assertEquals("verified_encrypted_payload", cache.get("test_dhash_key"))
        assertTrue(cache.securityStorageDescription().contains("AES-256-GCM"))
        assertEquals("sk-••••••••2345", EncryptedMediaCache.maskApiKey("sk-custom-test-12345"))

        // Verify BYOK Connection Profiles & Custom OpenAI-Compatible Adapter persistence
        val adapterLayer = ByokAiAdapterLayer(context, cache)
        val initialCfg = adapterLayer.loadConfig()
        assertEquals("Custom", initialCfg.activeProfileName)

        assertEquals(
            "https://custom-provider.example.com/api/v1/chat/completions",
            adapterLayer.normalizeOpenAiEndpoint("https://custom-provider.example.com/api/v1")
        )
        assertEquals(
            "https://custom-provider.example.com/api/v1/chat/completions",
            adapterLayer.normalizeOpenAiEndpoint("https://custom-provider.example.com/api/v1/chat/completions")
        )

        val renamedCustomProfile = ByokConnectionProfile(
            id = "profile_custom_default",
            profileName = "My Renamed Provider",
            providerType = AiProviderType.CUSTOM_OPENAI,
            endpointUrl = "https://custom-provider.example.com/api/v1",
            modelId = "custom-vision-model-v1",
            apiKey = "sk-custom-test-12345"
        )
        adapterLayer.saveConfig(
            AiAdapterConfig(
                activeProfileId = renamedCustomProfile.id,
                activeProfileName = renamedCustomProfile.profileName,
                profiles = listOf(renamedCustomProfile),
                activeProvider = AiProviderType.CUSTOM_OPENAI,
                selectedModel = renamedCustomProfile.modelId,
                customEndpointUrl = renamedCustomProfile.endpointUrl,
                customApiKey = renamedCustomProfile.apiKey
            )
        )
        val loadedCfg = adapterLayer.loadConfig()
        assertEquals("My Renamed Provider", loadedCfg.activeProfileName)
        assertEquals("My Renamed Provider", loadedCfg.profiles.first().profileName)
        assertEquals("custom-vision-model-v1", loadedCfg.selectedModel)
        assertEquals("https://custom-provider.example.com/api/v1", loadedCfg.customEndpointUrl)
        assertEquals("sk-custom-test-12345", adapterLayer.resolveEffectiveApiKey(AiProviderType.CUSTOM_OPENAI))
        assertNull(cache.peekWithoutStats("byok_budget_usd"))

        // Verify Perceptual Hash & Laplacian Sharpness
        val scanner = MediaScannerEngine(cache)
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                bmp.setPixel(x, y, if ((x + y) % 2 == 0) Color.WHITE else Color.BLACK)
            }
        }
        val dHash = scanner.computeDHash(bmp)
        val pHash = scanner.computePHash(bmp)
        assertNotNull(dHash)
        assertEquals(16, dHash.length)
        assertEquals(0, scanner.computeHammingDistance(dHash, dHash))
        assertEquals(16, pHash.length)

        val (_, sharpness) = scanner.computeLaplacianSharpness(bmp)
        assertTrue(sharpness > 50)

        // Verify Video PhotoEntity formatting & NaturalLanguageRuleEngine video rules
        val videoEntity = PhotoEntity(
            uriString = "content://media/external/video/media/42",
            title = "Screen_Recording_Clip.mp4",
            folderName = "Movies",
            mimeType = "video/mp4",
            durationMs = 125_000L,
            fileSizeBytes = 28_500_000L,
            width = 1920,
            height = 1080,
            dateTakenEpochMs = System.currentTimeMillis(),
            dHash = dHash,
            pHash = pHash,
            laplacianVariance = 420.0f,
            sharpnessScore = 65,
            exposureScore = 78,
            framingScore = 80,
            overallQualityScore = 72,
            category = PhotoCategory.VIDEO.name,
            screenshotSubType = ScreenshotSubType.NONE.name,
            ocrText = "",
            aiDescription = "Video clip (2:05) • 1920x1080",
            semanticTags = "video,clip",
            junkConfidence = 0.62f,
            junkReason = "Large video clip"
        )
        assertTrue(videoEntity.isVideo)
        assertEquals("2:05", videoEntity.formattedDuration)

        val videoRule = NaturalLanguageRuleEngine.parseNaturalLanguageToRule(
            "Videos older than 30 days can be suggested for deletion"
        )
        assertTrue(videoRule.targetCategoriesCsv.contains("VIDEO"))

        // Verify NanoGPT /models endpoint resolution & Vision-Only model filtering
        assertEquals(
            "https://nano-gpt.com/api/v1/models?detailed=true",
            adapterLayer.resolveModelsEndpointUrl("https://nano-gpt.com/api/v1/chat/completions")
        )
        assertEquals(
            "https://nano-gpt.com/api/v1/models?detailed=true",
            adapterLayer.resolveModelsEndpointUrl("https://nano-gpt.com")
        )
        assertEquals(
            "https://openrouter.ai/api/v1/models",
            adapterLayer.resolveModelsEndpointUrl("https://openrouter.ai/api/v1/chat/completions")
        )

        val sampleModelsJson = """
            {
              "data": [
                {
                  "id": "gpt-4o-mini",
                  "name": "GPT-4o Mini",
                  "capabilities": { "vision": true }
                },
                {
                  "id": "qwen/qwen2.5-vl-72b-instruct",
                  "name": "Qwen 2.5 VL 72B",
                  "input_modalities": ["text", "image"],
                  "output_modalities": ["text"]
                },
                {
                  "id": "deepseek-r1",
                  "name": "DeepSeek R1",
                  "capabilities": { "vision": false }
                },
                {
                  "id": "flux-pro",
                  "name": "Flux Pro Image Generator",
                  "input_modalities": ["text"],
                  "output_modalities": ["image"]
                }
              ]
            }
        """.trimIndent()

        val parsedModels = adapterLayer.parseModelsJsonAndDetectVision(sampleModelsJson)
        assertEquals(4, parsedModels.size)
        val visionOnly = parsedModels.filter { it.supportsVision }
        assertEquals(2, visionOnly.size)
        assertTrue(visionOnly.any { it.id == "gpt-4o-mini" })
        assertTrue(visionOnly.any { it.id == "qwen/qwen2.5-vl-72b-instruct" })

        // Verify strict clustering safety & media identity invariants:
        val nowMs = 1_750_000_000_000L
        val photoItem = videoEntity.copy(
            id = 101L,
            uriString = "content://media/external/images/media/101",
            title = "IMG_001.jpg",
            mediaType = "IMAGE",
            mimeType = "image/jpeg",
            durationMs = 0L,
            dateTakenEpochMs = nowMs,
            dHash = "55aa55aa55aa55aa",
            pHash = "33cc33cc33cc33cc",
            sharpnessScore = 88,
            overallQualityScore = 88
        )
        val videoWithSameHash = videoEntity.copy(
            id = 102L,
            uriString = "content://media/external/video/media/102",
            title = "VID_001.mp4",
            mediaType = "VIDEO",
            mimeType = "video/mp4",
            durationMs = 15_000L,
            dateTakenEpochMs = nowMs + 1000L,
            dHash = "55aa55aa55aa55aa",
            pHash = "33cc33cc33cc33cc",
            sharpnessScore = 70,
            overallQualityScore = 72
        )
        val mixedClusterResult = scanner.clusterAndNominateBestShots(listOf(photoItem, videoWithSameHash))
        assertTrue(mixedClusterResult.all { it.duplicateClusterId == null })

        // Two genuine burst photos taken 2 seconds apart ARE clustered and sharpest is Best Shot
        val burstShotSharp = photoItem.copy(
            id = 201L,
            uriString = "content://media/external/images/media/201",
            sharpnessScore = 92,
            overallQualityScore = 91
        )
        val burstShotSoft = photoItem.copy(
            id = 202L,
            uriString = "content://media/external/images/media/202",
            dateTakenEpochMs = nowMs + 2000L,
            sharpnessScore = 54,
            overallQualityScore = 58
        )
        val burstClustered = scanner.clusterAndNominateBestShots(listOf(burstShotSharp, burstShotSoft))
        val bestWinner = burstClustered.first { it.id == 201L }
        val extraVariant = burstClustered.first { it.id == 202L }
        assertNotNull(bestWinner.duplicateClusterId)
        assertEquals(bestWinner.duplicateClusterId, extraVariant.duplicateClusterId)
        assertTrue(bestWinner.isBestShotInCluster)
        assertFalse(extraVariant.isBestShotInCluster)

        // Marking an item as 'Not Duplicate' (ALL_EXCLUDED) prevents clustering
        val excludedBurst = scanner.clusterAndNominateBestShots(
            listOf(burstShotSharp, burstShotSoft.copy(excludedClusterKeys = "ALL_EXCLUDED"))
        )
        assertTrue(excludedBurst.all { it.duplicateClusterId == null })

        // Verify stableIdentityKey uniqueness even when filenames match
        val sameTitleDifferentUri = burstShotSharp.copy(
            id = 203L,
            uriString = "content://media/external/images/media/203"
        )
        assertNotEquals(burstShotSharp.stableIdentityKey, sameTitleDifferentUri.stableIdentityKey)
    }

    @Test
    fun identitySafetyRejectsMismatchedUriWithoutIdOnlyFallback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LuminaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.luminaDao()
        val verifier = MediaIdentityVerifier(dao)
        val cache = EncryptedMediaCache(context)
        val scanner = MediaScannerEngine(cache)
        val aiLayer = ByokAiAdapterLayer(context, cache)
        val repository = LuminaRepository(context, dao, cache, scanner, aiLayer, verifier)

        val itemA = PhotoEntity(
            uriString = "content://media/external/images/media/5001",
            title = "Family_Photo_A.jpg",
            folderName = "Camera",
            dateTakenEpochMs = 1_750_000_000_000L,
            fileSizeBytes = 2_400_000L,
            width = 1920,
            height = 1080,
            dHash = "55aa55aa55aa55aa",
            pHash = "33cc33cc33cc33cc",
            sharpnessScore = 85,
            laplacianVariance = 500f,
            exposureScore = 80,
            framingScore = 80,
            overallQualityScore = 83,
            category = PhotoCategory.LANDSCAPE.name
        )
        val rowIds = dao.insertPhotosIgnoreConflicts(listOf(itemA))
        val insertedId = rowIds.first()
        val dbItemA = dao.getPhotoById(insertedId)!!

        // 1. Stale/mismatched UI snapshot with same ID but DIFFERENT URI must be REJECTED (no ID-only fallback!)
        val mismatchedSnapshot = dbItemA.copy(uriString = "content://media/external/images/media/9999_wrong")
        val staleOutcome = repository.triagePhotoVerified(mismatchedSnapshot, TriageStatus.TRASH_VAULT)
        assertFalse(staleOutcome.allSucceeded)
        assertEquals(0, staleOutcome.verifiedUpdatedCount)
        assertEquals(1, staleOutcome.staleRejectedCount)
        assertEquals(MediaIdentityVerifier.STALE_SINGLE_ITEM_NOTICE, staleOutcome.userRefreshNotice)
        assertEquals(TriageStatus.UNREVIEWED, dao.getPhotoById(insertedId)!!.triageStatusEnum)
        assertTrue(verifier.getAuditEvents().isNotEmpty())

        // 2. Verified snapshot with matching (id, uriString) succeeds
        val validOutcome = repository.triagePhotoVerified(dbItemA, TriageStatus.TRASH_VAULT)
        assertTrue(validOutcome.allSucceeded)
        assertEquals(1, validOutcome.verifiedUpdatedCount)
        assertEquals(TriageStatus.TRASH_VAULT, dao.getPhotoById(insertedId)!!.triageStatusEnum)

        // 3. Undo with mismatched URI is rejected without modifying DB row
        val badUndoOutcome = repository.undoTriageVerified(
            listOf(
                VerifiedUndoEntry(
                    photoId = insertedId,
                    expectedUri = "content://media/external/images/media/9999_wrong",
                    previousStatus = TriageStatus.UNREVIEWED
                )
            )
        )
        assertFalse(badUndoOutcome.allSucceeded)
        assertEquals(TriageStatus.TRASH_VAULT, dao.getPhotoById(insertedId)!!.triageStatusEnum)

        // 4. Undo with exact matching (id, expectedUri) succeeds
        val goodUndoOutcome = repository.undoTriageVerified(
            listOf(
                VerifiedUndoEntry(
                    photoId = insertedId,
                    expectedUri = dbItemA.uriString,
                    previousStatus = TriageStatus.UNREVIEWED
                )
            )
        )
        assertTrue(goodUndoOutcome.allSucceeded)
        assertEquals(TriageStatus.UNREVIEWED, dao.getPhotoById(insertedId)!!.triageStatusEnum)

        db.close()
    }

    @Test
    fun permanentDeleteFlowOnlyRemovesRoomRowAndCountsBytesWhenSourceDeleted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LuminaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.luminaDao()
        val verifier = MediaIdentityVerifier(dao)

        // Create two temporary files to test real file deletion vs failed source deletion
        val deletableFile = File(context.cacheDir, "test_deletable_photo.jpg").apply {
            writeBytes(ByteArray(4096) { 0x42 })
        }
        val protectedUri = "content://media/external/images/media/7777"

        val itemDeletable = PhotoEntity(
            uriString = Uri.fromFile(deletableFile).toString(),
            title = "test_deletable_photo.jpg",
            folderName = "Cache",
            dateTakenEpochMs = 1_750_000_000_000L,
            fileSizeBytes = 4096L,
            width = 800,
            height = 600,
            dHash = "123456789abcdef0",
            pHash = "0fedcba987654321",
            sharpnessScore = 50,
            laplacianVariance = 200f,
            exposureScore = 70,
            framingScore = 70,
            overallQualityScore = 60,
            category = PhotoCategory.SCREENSHOT.name,
            triageStatus = TriageStatus.TRASH_VAULT.name,
            vaultedAtEpochMs = System.currentTimeMillis()
        )
        val itemFailingContent = itemDeletable.copy(
            uriString = protectedUri,
            title = "protected_media.jpg",
            fileSizeBytes = 10_000_000L
        )

        val ids = dao.insertPhotosIgnoreConflicts(listOf(itemDeletable, itemFailingContent))
        val savedDeletable = dao.getPhotoById(ids[0])!!
        val savedFailing = dao.getPhotoById(ids[1])!!

        val fakeGateway = object : SourceMediaDeletionGateway {
            override suspend fun attemptDirectSourceDelete(photo: PhotoEntity): SourceDeleteAttemptResult {
                return if (photo.uriString.startsWith("file://")) {
                    val f = File(Uri.parse(photo.uriString).path!!)
                    if (f.delete() && !f.exists()) {
                        SourceDeleteAttemptResult.ConfirmedDeleted
                    } else {
                        SourceDeleteAttemptResult.Failed("Could not delete file")
                    }
                } else {
                    SourceDeleteAttemptResult.Failed("ContentResolver permission denied")
                }
            }

            override fun isSourceUriStillAccessible(uri: Uri): Boolean {
                return if (uri.scheme == "file") {
                    File(uri.path!!).exists()
                } else {
                    true
                }
            }

            override fun createBatchDeleteIntentSender(uris: List<Uri>): IntentSender? = null
        }

        val coordinator = MediaDeletionCoordinator(dao, verifier, fakeGateway)
        val outcome = coordinator.executePermanentDelete(listOf(savedDeletable, savedFailing))
        assertTrue(outcome is DeletionExecutionOutcome.Completed)
        val result = (outcome as DeletionExecutionOutcome.Completed).result

        // Only the confirmed-deleted file is counted and removed from Room; the failed content:// item remains in DB!
        assertEquals(2, result.requestedCount)
        assertEquals(1, result.confirmedDeletedCount)
        assertEquals(1, result.failedCount)
        assertEquals(4096L, result.confirmedFreedBytes)
        assertFalse(deletableFile.exists())
        assertNull(dao.getPhotoById(savedDeletable.id))
        assertNotNull(dao.getPhotoById(savedFailing.id))
        assertEquals(TriageStatus.TRASH_VAULT, dao.getPhotoById(savedFailing.id)!!.triageStatusEnum)

        db.close()
    }

    @Test
    fun databaseExplicitMigrationsPreserveUserTriageRulesAndBackfillMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_${System.currentTimeMillis()}.db"
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `photos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uriString` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `folderName` TEXT NOT NULL,
                        `dateTakenEpochMs` INTEGER NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `dHash` TEXT NOT NULL,
                        `pHash` TEXT NOT NULL,
                        `sharpnessScore` INTEGER NOT NULL,
                        `laplacianVariance` REAL NOT NULL,
                        `exposureScore` INTEGER NOT NULL,
                        `framingScore` INTEGER NOT NULL,
                        `overallQualityScore` INTEGER NOT NULL,
                        `duplicateClusterId` TEXT,
                        `isBestShotInCluster` INTEGER NOT NULL,
                        `bestShotReason` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `screenshotSubType` TEXT NOT NULL,
                        `ocrText` TEXT NOT NULL,
                        `aiDescription` TEXT NOT NULL,
                        `semanticTags` TEXT NOT NULL,
                        `junkConfidence` REAL NOT NULL,
                        `junkReason` TEXT NOT NULL,
                        `sentimentalProtected` INTEGER NOT NULL,
                        `triageStatus` TEXT NOT NULL,
                        `vaultedAtEpochMs` INTEGER,
                        `analyzedByProvider` TEXT NOT NULL,
                        `mediaType` TEXT NOT NULL DEFAULT 'IMAGE',
                        `durationMs` INTEGER NOT NULL DEFAULT 0,
                        `mimeType` TEXT NOT NULL DEFAULT 'image/jpeg'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cleanup_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `rawNaturalPrompt` TEXT NOT NULL,
                        `minAgeDays` INTEGER NOT NULL,
                        `maxAgeDays` INTEGER,
                        `targetCategoriesCsv` TEXT NOT NULL,
                        `excludedKeywordsCsv` TEXT NOT NULL,
                        `requiredKeywordsCsv` TEXT NOT NULL,
                        `maxSharpnessScore` INTEGER,
                        `onlyNonBestDuplicates` INTEGER NOT NULL,
                        `minJunkConfidence` REAL NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_audit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestampEpochMs` INTEGER NOT NULL,
                        `providerId` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `photoTitle` TEXT NOT NULL,
                        `downscaledDimension` TEXT NOT NULL,
                        `payloadBytes` INTEGER NOT NULL,
                        `estimatedTokens` INTEGER NOT NULL,
                        `estimatedCostUsd` REAL NOT NULL,
                        `servedFromEncryptedCache` INTEGER NOT NULL,
                        `verdictSummary` TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                // Insert a row at schema v2 with KEEP status
                db.execSQL(
                    """
                    INSERT INTO `photos` (
                        `id`, `uriString`, `title`, `folderName`, `dateTakenEpochMs`, `fileSizeBytes`,
                        `width`, `height`, `dHash`, `pHash`, `sharpnessScore`, `laplacianVariance`,
                        `exposureScore`, `framingScore`, `overallQualityScore`, `duplicateClusterId`,
                        `isBestShotInCluster`, `bestShotReason`, `category`, `screenshotSubType`,
                        `ocrText`, `aiDescription`, `semanticTags`, `junkConfidence`, `junkReason`,
                        `sentimentalProtected`, `triageStatus`, `vaultedAtEpochMs`, `analyzedByProvider`,
                        `mediaType`, `durationMs`, `mimeType`
                    ) VALUES (
                        1, 'content://media/external/images/media/321', 'Kept_Memory.jpg', 'Camera',
                        1740000000000, 3500000, 1920, 1080, '55aa55aa55aa55aa', '33cc33cc33cc33cc',
                        90, 600.0, 85, 85, 88, NULL, 0, '', 'LANDSCAPE', 'NONE', '', 'Saved photo',
                        'photo', 0.1, 'Keep', 1, 'KEEP', NULL, 'On-Device analysis', 'IMAGE', 0, 'image/jpeg'
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        val sqliteDb = helper.writableDatabase
        LuminaDatabase.MIGRATION_2_3.migrate(sqliteDb)
        LuminaDatabase.MIGRATION_3_4.migrate(sqliteDb)
        LuminaDatabase.MIGRATION_4_5.migrate(sqliteDb)
        LuminaDatabase.MIGRATION_5_6.migrate(sqliteDb)
        sqliteDb.close()
        helper.close()

        // Now open with Room at version 6 with ALL_MIGRATIONS (no fallbackToDestructiveMigration!)
        val migratedRoomDb = Room.databaseBuilder(context, LuminaDatabase::class.java, dbName)
            .addMigrations(*LuminaDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val photos = migratedRoomDb.luminaDao().getAllPhotosOnce()
            assertEquals(1, photos.size)
            val kept = photos.first()
            assertEquals("content://media/external/images/media/321", kept.uriString)
            assertEquals(TriageStatus.KEEP, kept.triageStatusEnum)
            assertEquals(1_740_000_000_000L, kept.capturedAtEpochMs)
            assertEquals(1_740_000_000_000L, kept.dateModifiedEpochMs)
        }

        migratedRoomDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun staleMediaRevisionAndGenuineCaptureTimestampsWork() {
        val scanner = MediaScannerEngine()

        // 1. Imported metadata without a known capture date never invents System.currentTimeMillis()
        val unknownDateMeta = SourceMediaMetadata(
            uri = Uri.parse("content://picker/media/10"),
            displayName = "Picked_No_Exif.jpg",
            folderName = "Picked Media",
            fileSizeBytes = 150_000L,
            width = 1080,
            height = 1080,
            mimeType = "image/jpeg",
            isVideo = false,
            durationMs = 0L,
            capturedAtEpochMs = null,
            dateModifiedEpochMs = 0L,
            importedAtEpochMs = 1_755_000_000_000L,
            sourceGenerationModified = 1L,
            isUriPermissionPersisted = false
        )
        assertEquals(0L, unknownDateMeta.canonicalDateTakenEpochMs)
        val builtEntity = scanner.buildAnalyzedPhotoEntity(unknownDateMeta, bitmap = null)
        assertFalse(builtEntity.hasReliableCaptureDate)
        assertEquals("Capture date unknown", builtEntity.formattedCaptureDate)

        // 2. Two items without reliable capture dates and moderate hash distance are NEVER clustered as a 2-minute burst
        val undatedA = builtEntity.copy(
            id = 1L,
            uriString = "content://picker/media/10",
            dHash = "55aa55aa55aa55aa",
            pHash = "33cc33cc33cc33cc"
        )
        val undatedB = builtEntity.copy(
            id = 2L,
            uriString = "content://picker/media/11",
            dHash = "55aa55aa55aa5500", // distance 4 (> 2 required when capture time is unknown)
            pHash = "33cc33cc33cc3300"
        )
        assertFalse(scanner.areCandidatesSimilar(undatedA, undatedB))

        // 3. Low-entropy check guards when EITHER candidate A or candidate B has low entropy
        val normalPhoto = undatedA.copy(
            id = 3L,
            uriString = "content://media/external/images/media/3",
            capturedAtEpochMs = 1_750_000_000_000L,
            dateTakenEpochMs = 1_750_000_000_000L,
            dHash = "000000000000003f", // 6 bits set (normal boundary)
            pHash = "000000000000003f"
        )
        val darkFrameB = normalPhoto.copy(
            id = 4L,
            uriString = "content://media/external/images/media/4",
            capturedAtEpochMs = 1_750_000_090_000L, // 90s apart (> 45s low-entropy window)
            dateTakenEpochMs = 1_750_000_090_000L,
            dHash = "0000000000000001", // 1 bit set (low entropy!)
            pHash = "0000000000000001"
        )
        assertFalse(scanner.areCandidatesSimilar(normalPhoto, darkFrameB))

        // 4. Revision classification detects UNCHANGED_ITEM vs CHANGED_ITEM and preserves ID & triageStatus
        val keptOriginal = normalPhoto.copy(
            id = 44L,
            triageStatus = TriageStatus.KEEP.name,
            fileSizeBytes = 200_000L,
            dateModifiedEpochMs = 1_750_000_000_000L,
            sourceGenerationModified = 10L
        )
        assertEquals(
            MediaScanDecision.UNCHANGED_ITEM,
            MediaMetadataReader.classifyRevision(
                existing = keptOriginal,
                currentSizeBytes = 200_000L,
                currentDateModifiedMs = 1_750_000_000_000L,
                currentGenerationModified = 10L
            )
        )

        val editedMetadata = unknownDateMeta.copy(
            uri = Uri.parse(keptOriginal.uriString),
            fileSizeBytes = 265_000L,
            capturedAtEpochMs = 1_750_000_000_000L,
            dateModifiedEpochMs = 1_750_000_500_000L,
            sourceGenerationModified = 11L
        )
        val (decision, reanalyzed) = scanner.evaluateAndReanalyzeIfChanged(
            existing = keptOriginal,
            currentMetadata = editedMetadata,
            updatedBitmap = null
        )
        assertEquals(MediaScanDecision.CHANGED_ITEM, decision)
        assertEquals(44L, reanalyzed.id)
        assertEquals(TriageStatus.KEEP, reanalyzed.triageStatusEnum)
        assertEquals(265_000L, reanalyzed.fileSizeBytes)
        assertNotEquals(keptOriginal.stableIdentityKey, reanalyzed.stableIdentityKey)
    }

    @Test
    fun aiSchemaCanonicalEnumsAndProviderTokenUsageParsingWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = EncryptedMediaCache(context)
        val adapterLayer = ByokAiAdapterLayer(context, cache)

        // 1. Prompt embeds canonical enum lists directly from PhotoCategory & ScreenshotSubType
        val samplePhoto = PhotoEntity(
            id = 77L,
            uriString = "content://media/external/images/media/77",
            title = "IMG_0077.jpg",
            folderName = "Screenshots",
            dateTakenEpochMs = 1_750_000_000_000L,
            fileSizeBytes = 500_000L,
            width = 1080,
            height = 2400,
            dHash = "55aa55aa55aa55aa",
            pHash = "33cc33cc33cc33cc",
            sharpnessScore = 80,
            laplacianVariance = 400f,
            exposureScore = 80,
            framingScore = 80,
            overallQualityScore = 80,
            category = PhotoCategory.SCREENSHOT.name
        )
        val prompt = adapterLayer.buildMultimodalForensicPrompt(samplePhoto)
        assertTrue(prompt.contains(PhotoCategory.canonicalPromptValues()))
        assertTrue(prompt.contains(ScreenshotSubType.canonicalPromptValues()))

        // 2. Legacy synonyms and unknown AI categories map safely with audit notes
        val (mappedSelfie, selfieNote) = PhotoCategory.parseFromAi("SELFIE")
        assertEquals(PhotoCategory.PEOPLE, mappedSelfie)
        assertNotNull(selfieNote)

        val (mappedUnknown, unknownNote) = PhotoCategory.parseFromAi("TOTALLY_INVENTED_CATEGORY")
        assertEquals(PhotoCategory.UNKNOWN, mappedUnknown)
        assertNotNull(unknownNote)

        val (mappedSubType, subTypeNote) = ScreenshotSubType.parseFromAi("MEME_SOCIAL")
        assertEquals(ScreenshotSubType.MEME_REPOST, mappedSubType)
        assertNotNull(subTypeNote)

        // 3. Provider token usage extraction distinguishes ACTUAL_FROM_PROVIDER vs ESTIMATED
        val openAiResponse = JSONObject(
            """
            {
              "choices": [{"message": {"content": "{}"}}],
              "usage": {
                "prompt_tokens": 310,
                "completion_tokens": 45,
                "total_tokens": 355
              }
            }
            """.trimIndent()
        )
        val actualUsage = adapterLayer.extractTokenUsageFromProviderResponse(openAiResponse, fallbackEstimatedTokens = 500)
        assertEquals(TokenUsageSource.ACTUAL_FROM_PROVIDER, actualUsage.source)
        assertEquals(310, actualUsage.promptTokens)
        assertEquals(45, actualUsage.completionTokens)
        assertEquals(355, actualUsage.totalTokens)

        val missingUsageResponse = JSONObject("""{"choices": [{"message": {"content": "{}"}}]}""")
        val estimatedUsage = adapterLayer.extractTokenUsageFromProviderResponse(missingUsageResponse, fallbackEstimatedTokens = 420)
        assertEquals(TokenUsageSource.ESTIMATED, estimatedUsage.source)
        assertEquals(420, estimatedUsage.totalTokens)
    }

    @Test
    fun persistentPairwiseExclusionsAndUserBestShotOverrideSurviveRescans() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LuminaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.luminaDao()
        val verifier = MediaIdentityVerifier(dao)
        val cache = EncryptedMediaCache(context)
        val scanner = MediaScannerEngine(cache)
        val aiLayer = ByokAiAdapterLayer(context, cache)
        val repository = LuminaRepository(context, dao, cache, scanner, aiLayer, verifier)

        val now = 1_750_000_000_000L
        val shot1 = PhotoEntity(
            uriString = "content://media/external/images/media/8001",
            title = "Burst_1.jpg",
            folderName = "Camera",
            dateTakenEpochMs = now,
            capturedAtEpochMs = now,
            fileSizeBytes = 3_000_000L,
            width = 4000,
            height = 3000,
            dHash = "55aa55aa55aa55aa",
            pHash = "33cc33cc33cc33cc",
            sharpnessScore = 94,
            laplacianVariance = 400f,
            exposureScore = 88,
            framingScore = 86,
            overallQualityScore = 91,
            category = PhotoCategory.PEOPLE.name
        )
        val shot2 = shot1.copy(
            uriString = "content://media/external/images/media/8002",
            title = "Burst_2.jpg",
            dateTakenEpochMs = now + 1_000L,
            capturedAtEpochMs = now + 1_000L,
            sharpnessScore = 76,
            overallQualityScore = 78
        )
        val shot3 = shot1.copy(
            uriString = "content://media/external/images/media/8003",
            title = "Burst_3.jpg",
            dateTakenEpochMs = now + 2_000L,
            capturedAtEpochMs = now + 2_000L,
            sharpnessScore = 70,
            overallQualityScore = 72
        )

        val initialClustered = scanner.clusterAndNominateBestShots(listOf(shot1, shot2, shot3))
        dao.insertPhotosIgnoreConflicts(initialClustered)
        val allInDb = dao.getAllPhotosOnce()
        val dbShot1 = allInDb.first { it.title == "Burst_1.jpg" }
        val dbShot2 = allInDb.first { it.title == "Burst_2.jpg" }
        val dbShot3 = allInDb.first { it.title == "Burst_3.jpg" }
        val clusterId = dbShot1.duplicateClusterId!!

        // 1. User manually overrides Best Shot to Burst_2.jpg (even though Burst_1 has higher sharpness)
        val selectedOk = repository.selectBestShotInCluster(clusterId, dbShot2.id)
        assertTrue(selectedOk)
        assertTrue(dao.getPhotoById(dbShot2.id)!!.userSelectedBestShot)
        assertTrue(dao.getPhotoById(dbShot2.id)!!.isBestShotInCluster)

        // Re-running clustering preserves Burst_2.jpg as the best shot because userSelectedBestShot = true!
        val reclustered = scanner.clusterAndNominateBestShots(dao.getAllPhotosOnce())
        dao.updatePhotos(reclustered)
        assertTrue(dao.getPhotoById(dbShot2.id)!!.isBestShotInCluster)
        assertFalse(dao.getPhotoById(dbShot1.id)!!.isBestShotInCluster)

        // 2. User marks Burst_3.jpg as "Not a Duplicate" -> persists pairwise exclusions in Room
        val excludedOk = repository.markPhotoNotDuplicate(dao.getPhotoById(dbShot3.id)!!)
        assertTrue(excludedOk)
        val savedExclusions = dao.getAllPairExclusionsOnce()
        assertTrue(savedExclusions.isNotEmpty())

        // Even if excludedClusterKeys on Burst_3 were cleared, pairwise exclusions prevent Burst_3 from re-clustering with Burst_1/Burst_2
        val pairSet = savedExclusions.map { it.canonicalPairKey }.toSet()
        val reclusteredWithPairExclusions = scanner.clusterAndNominateBestShots(
            photos = dao.getAllPhotosOnce().map { if (it.id == dbShot3.id) it.copy(excludedClusterKeys = "") else it },
            explicitExcludedPairKeys = pairSet
        )
        val afterShot3 = reclusteredWithPairExclusions.first { it.id == dbShot3.id }
        val afterShot1 = reclusteredWithPairExclusions.first { it.id == dbShot1.id }
        val afterShot2 = reclusteredWithPairExclusions.first { it.id == dbShot2.id }
        assertNull(afterShot3.duplicateClusterId)
        assertNotNull(afterShot1.duplicateClusterId)
        assertEquals(afterShot1.duplicateClusterId, afterShot2.duplicateClusterId)

        // 3. IncrementalScanCoordinator persists checkpoint and skips unchanged items on subsequent scan
        val coordinator = com.example.domain.scanner.IncrementalScanCoordinator(context, dao, scanner)
        val existingMeta = dao.getAllPhotosOnce().map { photo ->
            SourceMediaMetadata(
                uri = Uri.parse(photo.uriString),
                displayName = photo.title,
                folderName = photo.folderName,
                fileSizeBytes = photo.fileSizeBytes,
                width = photo.width,
                height = photo.height,
                mimeType = photo.mimeType,
                isVideo = photo.isVideo,
                durationMs = photo.durationMs,
                capturedAtEpochMs = photo.capturedAtEpochMs,
                dateModifiedEpochMs = photo.dateModifiedEpochMs,
                importedAtEpochMs = photo.importedAtEpochMs,
                sourceGenerationModified = photo.sourceGenerationModified,
                isUriPermissionPersisted = photo.isUriPermissionPersisted
            )
        }
        val summary = coordinator.runIncrementalScan(
            discoveredRecordsOverride = existingMeta,
            chunkSize = 2,
            resumeFromCheckpoint = false
        )
        assertEquals(3, summary.unchangedItemsReused.size)
        assertEquals(0, summary.newItemsAdded.size)
        val checkpoint = dao.getScanCheckpointOnce()
        assertNotNull(checkpoint)
        assertEquals("COMPLETE", checkpoint!!.phase)
        assertEquals(3, checkpoint.analyzedCount)
        assertEquals(3, checkpoint.unchangedSkippedCount)

        db.close()
    }

    @Test
    fun nanoGptSubscriptionModelFilteringProviderTokenParsingAndPrivacyPreviewWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = EncryptedMediaCache(context)
        val adapterLayer = ByokAiAdapterLayer(context, cache)

        val nanoGptDetailedResponse = """
            {
              "data": [
                {
                  "id": "google/gemini-2.0-flash-001",
                  "name": "Gemini 2.0 Flash",
                  "input_modalities": ["text", "image"],
                  "output_modalities": ["text"],
                  "subscription": { "included": true, "plan": "NanoGPT Subscription" },
                  "pricing": { "prompt": "0", "completion": "0" }
                },
                {
                  "id": "openai/gpt-4o",
                  "name": "GPT-4o",
                  "input_modalities": ["text", "image"],
                  "output_modalities": ["text"],
                  "subscription": { "included": false },
                  "pricing": { "prompt": "0.0025", "completion": "0.01" }
                },
                {
                  "id": "deepseek/deepseek-chat",
                  "name": "DeepSeek V3",
                  "input_modalities": ["text"],
                  "output_modalities": ["text"],
                  "free_with_subscription": true
                },
                {
                  "id": "custom/mystery-vision-model",
                  "name": "Mystery Vision Model",
                  "capabilities": { "vision": true }
                }
              ]
            }
        """.trimIndent()

        val models = adapterLayer.parseModelsJsonAndDetectVision(
            rawJson = nanoGptDetailedResponse
        )
        assertEquals(4, models.size)

        val geminiFlash = models.first { it.id == "google/gemini-2.0-flash-001" }
        assertTrue(geminiFlash.supportsVision)
        assertEquals(SubscriptionInclusionStatus.INCLUDED, geminiFlash.subscriptionStatus)
        assertTrue(geminiFlash.isConfirmedSubscriptionIncluded)

        val gpt4o = models.first { it.id == "openai/gpt-4o" }
        assertTrue(gpt4o.supportsVision)
        assertEquals(SubscriptionInclusionStatus.NOT_INCLUDED, gpt4o.subscriptionStatus)
        assertFalse(gpt4o.isConfirmedSubscriptionIncluded)

        val deepseek = models.first { it.id == "deepseek/deepseek-chat" }
        assertFalse(deepseek.supportsVision)
        assertEquals(SubscriptionInclusionStatus.INCLUDED, deepseek.subscriptionStatus)

        // When subscription metadata is absent, status MUST be UNKNOWN (never falsely labeled Included!)
        val mystery = models.first { it.id == "custom/mystery-vision-model" }
        assertTrue(mystery.supportsVision)
        assertEquals(SubscriptionInclusionStatus.UNKNOWN, mystery.subscriptionStatus)
        assertFalse(mystery.isConfirmedSubscriptionIncluded)

        // Combined filter: Vision Only + Subscription Included -> returns ONLY Gemini 2.0 Flash
        val combinedVisionAndSub = models.filter { it.supportsVision && it.isConfirmedSubscriptionIncluded }
        assertEquals(1, combinedVisionAndSub.size)
        assertEquals("google/gemini-2.0-flash-001", combinedVisionAndSub.first().id)

        // Verify Anthropic & Gemini token usage extraction
        val anthropicJson = JSONObject(
            """
            {
              "content": [{"type": "text", "text": "{}"}],
              "usage": { "input_tokens": 520, "output_tokens": 95 }
            }
            """.trimIndent()
        )
        val anthropicUsage = adapterLayer.extractTokenUsageFromProviderResponse(anthropicJson, 400)
        assertEquals(TokenUsageSource.ACTUAL_FROM_PROVIDER, anthropicUsage.source)
        assertEquals(520, anthropicUsage.promptTokens)
        assertEquals(95, anthropicUsage.completionTokens)
        assertEquals(615, anthropicUsage.totalTokens)

        val geminiJson = JSONObject(
            """
            {
              "candidates": [],
              "usageMetadata": {
                "promptTokenCount": 410,
                "candidatesTokenCount": 60,
                "totalTokenCount": 470
              }
            }
            """.trimIndent()
        )
        val geminiUsage = adapterLayer.extractTokenUsageFromProviderResponse(geminiJson, 400)
        assertEquals(TokenUsageSource.ACTUAL_FROM_PROVIDER, geminiUsage.source)
        assertEquals(410, geminiUsage.promptTokens)
        assertEquals(60, geminiUsage.completionTokens)
        assertEquals(470, geminiUsage.totalTokens)
    }
}
