package com.example.domain.ai

import com.example.data.local.AiAuditLogEntity
import com.example.data.local.CleanupRuleEntity
import com.example.data.local.MediaClusterType
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.ScreenshotSubType
import com.example.data.local.TokenUsageSource
import com.example.data.local.TriageStatus
import com.example.domain.rules.NaturalLanguageRuleEngine
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Time windows for informational AI token usage reporting (Pass 3 Section C).
 * Resets naturally at device-local midnight without destructively clearing historical logs.
 */
enum class UsageRangeOption(val label: String, val daysInclusive: Int) {
    TODAY("Today", 1),
    LAST_7_DAYS("7 Days", 7),
    LAST_30_DAYS("30 Days", 30)
}

typealias UsageTimeWindow = UsageRangeOption

/**
 * Aggregated token usage summary for a selected [UsageRangeOption].
 * Informational only — never enforces a spending limit or blocks AI usage.
 */
data class AiUsageSummary(
    val rangeOption: UsageRangeOption = UsageRangeOption.TODAY,
    val windowStartEpochMs: Long = 0L,
    val windowEndEpochMs: Long = 0L,
    val totalTokens: Long = 0L,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val actualTokens: Long = 0L,
    val estimatedTokens: Long = 0L,
    val requestCount: Int = 0,
    val actualRequestCount: Int = 0,
    val estimatedRequestCount: Int = 0,
    val cachedHitCount: Int = 0,
    val localAnalysisCount: Int = 0,
    val failedRequestCount: Int = 0,
    val activeProviderLabel: String = "Custom",
    val activeModelLabel: String = "gpt-4o-mini"
) {
    val hasActualUsage: Boolean
        get() = actualRequestCount > 0

    val hasEstimatedUsage: Boolean
        get() = estimatedRequestCount > 0

    val formattedTotalTokens: String
        get() = String.format(Locale.US, "%,d", totalTokens)

    val compactTokensLabel: String
        get() = when {
            totalTokens >= 1_000_000L -> String.format(Locale.US, "%.1fM tokens", totalTokens / 1_000_000.0)
            totalTokens >= 10_000L -> String.format(Locale.US, "%.1fK tokens", totalTokens / 1_000.0)
            else -> String.format(Locale.US, "%,d tokens", totalTokens)
        }

    val sourceBreakdownLabel: String
        get() = when {
            requestCount == 0 -> "0 network requests"
            actualRequestCount > 0 && estimatedRequestCount == 0 -> "✓ Actual usage ($actualRequestCount)"
            actualRequestCount == 0 && estimatedRequestCount > 0 -> "~ Estimated usage ($estimatedRequestCount)"
            else -> "✓ $actualRequestCount actual • ~ $estimatedRequestCount est."
        }
}

typealias AiUsageWindowSummary = AiUsageSummary

/**
 * Breakdown of automatically protected items in a Natural-Language Cleanup Plan (Pass 3 Section J).
 */
data class ProtectedSafetyBreakdown(
    val receiptsCount: Int = 0,
    val confirmationNumbersCount: Int = 0,
    val conversationsCount: Int = 0,
    val credentialsCount: Int = 0,
    val bestShotsCount: Int = 0,
    val favoritesCount: Int = 0,
    val otherProtectedCount: Int = 0
) {
    val bestShotsAndFavoritesCount: Int
        get() = bestShotsCount + favoritesCount

    val totalProtectedCount: Int
        get() = receiptsCount + confirmationNumbersCount + conversationsCount +
            credentialsCount + bestShotsCount + favoritesCount + otherProtectedCount

    fun toBulletLines(): List<String> = buildList {
        if (receiptsCount > 0) add("$receiptsCount receipt${if (receiptsCount == 1) "" else "s"}")
        if (confirmationNumbersCount > 0) add("$confirmationNumbersCount confirmation number${if (confirmationNumbersCount == 1) "" else "s"}")
        if (conversationsCount > 0) add("$conversationsCount conversation${if (conversationsCount == 1) "" else "s"}")
        if (credentialsCount > 0) add("$credentialsCount credential screenshot${if (credentialsCount == 1) "" else "s"}")
        if (bestShotsCount > 0) add("$bestShotsCount best shot${if (bestShotsCount == 1) "" else "s"}")
        if (favoritesCount > 0) add("$favoritesCount favorite${if (favoritesCount == 1) "" else "s"}")
        if (otherProtectedCount > 0) add("$otherProtectedCount protected item${if (otherProtectedCount == 1) "" else "s"}")
    }
}

/**
 * Structured preview produced from an "Ask RoboPhoto" natural-language request (Pass 3 Sections I & J).
 * Never immediately deletes anything; lets the user Review candidates or Save as an Automation.
 */
data class NaturalLanguageCleanupPlan(
    val prompt: String,
    val headline: String,
    val compiledRule: CleanupRuleEntity,
    val candidatePhotos: List<PhotoEntity>,
    val candidateBytes: Long,
    val protectedBreakdown: ProtectedSafetyBreakdown,
    val isInspectionSearchOnly: Boolean = false
) {
    val rawPrompt: String
        get() = prompt

    val title: String
        get() = headline

    val plainEnglishSummary: String
        get() = compiledRule.compiledRuleSummary

    val candidates: List<PhotoEntity>
        get() = candidatePhotos

    val recoverableBytes: Long
        get() = candidateBytes
}

/**
 * High-signal Home suggestion item generated from local + AI library intelligence (Pass 3 Section L).
 */
data class AiHomeSuggestion(
    val id: String,
    val headline: String,
    val supportingText: String,
    val badgeText: String,
    val accentColorHex: Long = 0xFF38BDF8,
    val candidatePhotos: List<PhotoEntity>,
    val recoverableBytes: Long
) {
    val title: String
        get() = headline

    val subtitle: String
        get() = supportingText
}

object AiWorkflowsAndUsageEngine {

    val askRoboPhotoPromptChips: List<String> = listOf(
        "Help me free 5 GB",
        "Find old screenshots I probably don't need",
        "Show me duplicate grow room photos",
        "Find receipts",
        "Find blurry videos",
        "Show screenshots containing confirmation numbers"
    )

    /**
     * Computes device-local midnight (00:00:00.000) for the given [referenceEpochMs] and [timeZone].
     */
    fun computeLocalMidnightEpochMs(
        referenceEpochMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = referenceEpochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Computes the start timestamp (inclusive) for [window] anchored to device-local midnight.
     */
    fun computeWindowStartEpochMs(
        window: UsageRangeOption,
        nowEpochMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val todayMidnight = computeLocalMidnightEpochMs(nowEpochMs, timeZone)
        if (window == UsageRangeOption.TODAY) {
            return todayMidnight
        }
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = todayMidnight
            add(Calendar.DAY_OF_YEAR, -(window.daysInclusive - 1))
        }
        return cal.timeInMillis
    }

    fun computeTodayUsageSummary(
        allLogs: List<AiAuditLogEntity>,
        activeConfig: AiAdapterConfig = AiAdapterConfig(),
        nowEpochMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): AiUsageSummary {
        return computeUsageSummary(
            allLogs = allLogs,
            rangeOption = UsageRangeOption.TODAY,
            activeConfig = activeConfig,
            nowEpochMs = nowEpochMs,
            timeZone = timeZone
        )
    }

    /**
     * Aggregates token usage for [rangeOption] without mutating or deleting historical logs.
     * Cache hits and local-only analyses are tracked separately with 0 network tokens.
     */
    fun computeUsageSummary(
        allLogs: List<AiAuditLogEntity>,
        rangeOption: UsageRangeOption,
        activeConfig: AiAdapterConfig = AiAdapterConfig(),
        nowEpochMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): AiUsageSummary {
        val startMs = computeWindowStartEpochMs(rangeOption, nowEpochMs, timeZone)
        val inWindow = allLogs.filter { it.timestampEpochMs in startMs..nowEpochMs }

        var totalTokens = 0L
        var inputTokens = 0L
        var outputTokens = 0L
        var actualTokens = 0L
        var estimatedTokens = 0L
        var networkRequests = 0
        var actualRequests = 0
        var estimatedRequests = 0
        var cacheHits = 0
        var localOnlyCount = 0
        var failedCount = 0

        for (log in inWindow) {
            val source = log.tokenUsageSourceEnum
            val isLocalEngine = log.providerId.contains("Local", ignoreCase = true) ||
                log.requestType.equals("LOCAL_ANALYSIS", ignoreCase = true)

            when {
                log.servedFromEncryptedCache -> {
                    cacheHits++
                }
                isLocalEngine || source == TokenUsageSource.LOCAL_CACHE_ZERO -> {
                    localOnlyCount++
                }
                !log.requestSucceeded -> {
                    failedCount++
                    if (log.totalTokens > 0) {
                        networkRequests++
                        totalTokens += log.totalTokens
                        inputTokens += log.promptTokens
                        outputTokens += log.completionTokens
                        if (source == TokenUsageSource.ACTUAL_FROM_PROVIDER) {
                            actualTokens += log.totalTokens
                            actualRequests++
                        } else {
                            estimatedTokens += log.totalTokens
                            estimatedRequests++
                        }
                    }
                }
                else -> {
                    networkRequests++
                    val prompt = log.promptTokens.coerceAtLeast(0)
                    val completion = log.completionTokens.coerceAtLeast(0)
                    val tot = if (log.totalTokens > 0) log.totalTokens else (prompt + completion)
                    inputTokens += prompt
                    outputTokens += completion
                    totalTokens += tot
                    if (source == TokenUsageSource.ACTUAL_FROM_PROVIDER) {
                        actualTokens += tot
                        actualRequests++
                    } else {
                        estimatedTokens += tot
                        estimatedRequests++
                    }
                }
            }
        }

        val recentNetworkLog = inWindow.firstOrNull {
            !it.servedFromEncryptedCache &&
                it.tokenUsageSourceEnum != TokenUsageSource.LOCAL_CACHE_ZERO &&
                !it.providerId.contains("Local", ignoreCase = true)
        }
        val providerLabel = recentNetworkLog?.providerId
            ?.substringBefore(" (")
            ?.takeIf { it.isNotBlank() }
            ?: activeConfig.activeProfileName.ifBlank { activeConfig.activeProvider.displayName }

        val modelLabel = recentNetworkLog?.modelName
            ?.takeIf { it.isNotBlank() }
            ?: activeConfig.selectedModel.ifBlank { activeConfig.activeProvider.defaultModel }

        return AiUsageSummary(
            rangeOption = rangeOption,
            windowStartEpochMs = startMs,
            windowEndEpochMs = nowEpochMs,
            totalTokens = totalTokens,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            actualTokens = actualTokens,
            estimatedTokens = estimatedTokens,
            requestCount = networkRequests,
            actualRequestCount = actualRequests,
            estimatedRequestCount = estimatedRequests,
            cachedHitCount = cacheHits,
            localAnalysisCount = localOnlyCount,
            failedRequestCount = failedCount,
            activeProviderLabel = providerLabel,
            activeModelLabel = modelLabel
        )
    }

    fun generateCleanupPlan(
        naturalQuery: String,
        allPhotos: List<PhotoEntity>
    ): NaturalLanguageCleanupPlan {
        return buildNaturalLanguageCleanupPlan(naturalQuery, allPhotos)
    }

    /**
     * Converts a user's natural-language request on Home or Automations into a structured,
     * non-destructive [NaturalLanguageCleanupPlan] (Pass 3 Sections I & J).
     */
    fun buildNaturalLanguageCleanupPlan(
        rawQuery: String,
        allPhotos: List<PhotoEntity>
    ): NaturalLanguageCleanupPlan {
        val clean = rawQuery.trim()
        val lower = clean.lowercase(Locale.US)
        val activePhotos = allPhotos.filter { it.triageStatusEnum != TriageStatus.TRASH_VAULT }

        // 1. Check if the user is asking for a storage target goal (e.g. "Help me free 5 GB" or "Free 200 MB")
        val gbMatch = Regex("""free\s+(?:up\s+)?(\d+(?:\.\d+)?)\s*gb""").find(lower)
        val mbMatch = Regex("""free\s+(?:up\s+)?(\d+)\s*mb""").find(lower)
        if (gbMatch != null || mbMatch != null) {
            val targetBytes = when {
                gbMatch != null -> ((gbMatch.groupValues[1].toDoubleOrNull() ?: 1.0) * 1024.0 * 1024.0 * 1024.0).toLong()
                mbMatch != null -> (mbMatch.groupValues[1].toLongOrNull() ?: 100L) * 1024L * 1024L
                else -> 500L * 1024L * 1024L
            }
            val goalPlan = NaturalLanguageRuleEngine.buildCleanupGoalPlan(targetBytes, activePhotos)
            val protected = countProtectedItemsInLibrary(activePhotos)
            val targetLabel = if (gbMatch != null) "${gbMatch.groupValues[1]} GB" else "${mbMatch?.groupValues?.get(1)} MB"
            val rule = NaturalLanguageRuleEngine.parseNaturalLanguageToRule(
                "Screenshots older than 90 days and duplicate extras unless they contain receipts, passwords, confirmation numbers, or conversations"
            ).copy(title = "Storage Goal • Free $targetLabel", rawNaturalPrompt = clean)

            return NaturalLanguageCleanupPlan(
                prompt = clean,
                headline = "Storage Goal Plan • Free $targetLabel",
                compiledRule = rule,
                candidatePhotos = goalPlan.selectedPhotos,
                candidateBytes = goalPlan.achievedBytes,
                protectedBreakdown = protected,
                isInspectionSearchOnly = false
            )
        }

        // 2. Check if the user is searching for important items to view (e.g. "Find receipts", "Show screenshots containing confirmation numbers")
        val isLookingForProtectedItems = (
            lower.contains("find receipt") ||
                lower.contains("show receipt") ||
                lower.contains("confirmation number") ||
                lower.contains("serial number")
            ) && !lower.contains("unless") && !lower.contains("except") && !lower.contains("delete") && !lower.contains("clean")

        if (isLookingForProtectedItems) {
            val matched = when {
                lower.contains("confirmation") -> activePhotos.filter {
                    it.screenshotSubTypeEnum == ScreenshotSubType.CONFIRMATION_NUMBER ||
                        it.ocrText.contains("confirmation", ignoreCase = true) ||
                        it.title.contains("confirmation", ignoreCase = true)
                }
                lower.contains("receipt") -> activePhotos.filter {
                    it.categoryEnum == PhotoCategory.RECEIPT ||
                        it.screenshotSubTypeEnum == ScreenshotSubType.RECEIPT_INVOICE ||
                        it.ocrText.contains("receipt", ignoreCase = true) ||
                        it.ocrText.contains("invoice", ignoreCase = true) ||
                        it.title.contains("receipt", ignoreCase = true)
                }
                else -> NaturalLanguageRuleEngine.semanticSearch(clean, activePhotos)
            }
            val syntheticRule = NaturalLanguageRuleEngine.parseNaturalLanguageToRule(clean)
            return NaturalLanguageCleanupPlan(
                prompt = clean,
                headline = when {
                    lower.contains("confirmation") -> "Screenshots with Confirmation Numbers"
                    lower.contains("receipt") -> "Receipts & Invoices"
                    else -> syntheticRule.title
                },
                compiledRule = syntheticRule,
                candidatePhotos = matched,
                candidateBytes = matched.sumOf { it.fileSizeBytes },
                protectedBreakdown = ProtectedSafetyBreakdown(),
                isInspectionSearchOnly = true
            )
        }

        // 3. Standard Cleanup Plan with automatic safety exclusions for screenshots/duplicates
        val augmentedPrompt = if (
            lower.contains("screenshot") &&
            (lower.contains("aren't important") || lower.contains("don't need") || lower.contains("not important") || !lower.contains("unless"))
        ) {
            "$clean unless they contain receipts, passwords, addresses, important conversations, or confirmation numbers"
        } else {
            clean
        }

        val compiledRule = NaturalLanguageRuleEngine.parseNaturalLanguageToRule(augmentedPrompt)
            .copy(rawNaturalPrompt = clean)
        val eval = NaturalLanguageRuleEngine.evaluateRule(compiledRule, activePhotos)

        var receipts = 0
        var confirmations = 0
        var conversations = 0
        var credentials = 0
        var bestShots = 0
        var favorites = 0
        var otherProtected = 0

        for ((photo, reason) in eval.protectedByExclusions) {
            val sub = photo.screenshotSubTypeEnum
            val lowerReason = reason.lowercase(Locale.US)
            when {
                sub == ScreenshotSubType.RECEIPT_INVOICE || photo.categoryEnum == PhotoCategory.RECEIPT || lowerReason.contains("receipt") ->
                    receipts++
                sub == ScreenshotSubType.CONFIRMATION_NUMBER || lowerReason.contains("confirmation") ->
                    confirmations++
                sub == ScreenshotSubType.IMPORTANT_CONVERSATION || sub == ScreenshotSubType.ADDRESS_MAP ||
                    lowerReason.contains("conversation") || lowerReason.contains("address") ->
                    conversations++
                sub == ScreenshotSubType.PASSWORD_WIFI || lowerReason.contains("password") ->
                    credentials++
                photo.isBestShotInCluster || lowerReason.contains("best-shot") ->
                    bestShots++
                photo.triageStatusEnum == TriageStatus.FAVORITE_ARCHIVE ->
                    favorites++
                else ->
                    otherProtected++
            }
        }

        val cleanHeadline = when {
            compiledRule.targetCategoriesCsv.contains("SCREENSHOT") && compiledRule.minAgeDays >= 180 ->
                "Screenshots older than ${compiledRule.minAgeDays / 30} months"
            compiledRule.targetCategoriesCsv.contains("SCREENSHOT") && compiledRule.minAgeDays > 0 ->
                "Screenshots older than ${compiledRule.minAgeDays} days"
            else -> compiledRule.title
        }

        return NaturalLanguageCleanupPlan(
            prompt = clean,
            headline = cleanHeadline,
            compiledRule = compiledRule.copy(title = cleanHeadline),
            candidatePhotos = eval.matchedForCleanup,
            candidateBytes = eval.totalRecoverableBytes,
            protectedBreakdown = ProtectedSafetyBreakdown(
                receiptsCount = receipts,
                confirmationNumbersCount = confirmations,
                conversationsCount = conversations,
                credentialsCount = credentials,
                bestShotsCount = bestShots,
                favoritesCount = favorites,
                otherProtectedCount = otherProtected
            ),
            isInspectionSearchOnly = false
        )
    }

    private fun countProtectedItemsInLibrary(activePhotos: List<PhotoEntity>): ProtectedSafetyBreakdown {
        var receipts = 0
        var confirmations = 0
        var conversations = 0
        var credentials = 0
        var bestShots = 0
        var favorites = 0
        for (p in activePhotos) {
            when {
                p.categoryEnum == PhotoCategory.RECEIPT || p.screenshotSubTypeEnum == ScreenshotSubType.RECEIPT_INVOICE -> receipts++
                p.screenshotSubTypeEnum == ScreenshotSubType.CONFIRMATION_NUMBER -> confirmations++
                p.screenshotSubTypeEnum == ScreenshotSubType.IMPORTANT_CONVERSATION || p.screenshotSubTypeEnum == ScreenshotSubType.ADDRESS_MAP -> conversations++
                p.screenshotSubTypeEnum == ScreenshotSubType.PASSWORD_WIFI -> credentials++
                p.isBestShotInCluster -> bestShots++
                p.triageStatusEnum == TriageStatus.FAVORITE_ARCHIVE -> favorites++
            }
        }
        return ProtectedSafetyBreakdown(
            receiptsCount = receipts,
            confirmationNumbersCount = confirmations,
            conversationsCount = conversations,
            credentialsCount = credentials,
            bestShotsCount = bestShots,
            favoritesCount = favorites
        )
    }

    fun generateHomeSuggestions(
        activePhotos: List<PhotoEntity>
    ): List<AiHomeSuggestion> {
        return buildAiSuggestionFeed(activePhotos) { bytes ->
            if (bytes <= 0L) "0 MB"
            else {
                val mb = bytes.toDouble() / (1024.0 * 1024.0)
                if (mb >= 1024.0) String.format(Locale.US, "%.2f GB", mb / 1024.0)
                else String.format(Locale.US, "%.1f MB", mb)
            }
        }
    }

    /**
     * Generates useful, high-signal Home suggestions from local and AI analysis (Pass 3 Section L).
     * Never generates empty or low-value noisy suggestions.
     */
    fun buildAiSuggestionFeed(
        activePhotos: List<PhotoEntity>,
        formatBytes: (Long) -> String
    ): List<AiHomeSuggestion> {
        val unreviewed = activePhotos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }
        val suggestions = mutableListOf<AiHomeSuggestion>()

        // 1. Near-identical videos
        val redundantVideos = unreviewed.filter {
            it.isVideo && it.duplicateClusterId != null && !it.isBestShotInCluster
        }
        if (redundantVideos.isNotEmpty()) {
            val bytes = redundantVideos.sumOf { it.fileSizeBytes }
            suggestions += AiHomeSuggestion(
                id = "suggest_duplicate_videos",
                headline = "${formatBytes(bytes)} in near-identical videos",
                supportingText = "${redundantVideos.size} extra video clip${if (redundantVideos.size == 1) "" else "s"} • Best takes kept",
                badgeText = "Review ${redundantVideos.size}",
                accentColorHex = 0xFF818CF8,
                candidatePhotos = redundantVideos,
                recoverableBytes = bytes
            )
        }

        // 2. Temporary screenshots older than 90 days
        val oldTempScreenshots = unreviewed.filter {
            it.categoryEnum == PhotoCategory.SCREENSHOT &&
                it.ageInDays >= 90 &&
                !it.screenshotSubTypeEnum.isImportantDefault &&
                !it.sentimentalProtected
        }
        if (oldTempScreenshots.isNotEmpty()) {
            val bytes = oldTempScreenshots.sumOf { it.fileSizeBytes }
            suggestions += AiHomeSuggestion(
                id = "suggest_old_screenshots_90d",
                headline = "${oldTempScreenshots.size} temporary screenshot${if (oldTempScreenshots.size == 1) "" else "s"} older than 90 days",
                supportingText = "${formatBytes(bytes)} • Receipts & confirmations excluded",
                badgeText = "Review ${oldTempScreenshots.size}",
                accentColorHex = 0xFF38BDF8,
                candidatePhotos = oldTempScreenshots,
                recoverableBytes = bytes
            )
        }

        // 3. Duplicate burst groups
        val burstPhotos = unreviewed.filter {
            !it.isVideo && it.duplicateClusterId != null && !it.isBestShotInCluster
        }
        val burstClusterCount = burstPhotos.mapNotNull { it.duplicateClusterId }.distinct().size
        if (burstClusterCount > 0) {
            val bytes = burstPhotos.sumOf { it.fileSizeBytes }
            suggestions += AiHomeSuggestion(
                id = "suggest_burst_groups",
                headline = "$burstClusterCount duplicate burst group${if (burstClusterCount == 1) "" else "s"}",
                supportingText = "${burstPhotos.size} extra shots (${formatBytes(bytes)}) • Sharpest shots selected",
                badgeText = "Review ${burstPhotos.size}",
                accentColorHex = 0xFF168BFF,
                candidatePhotos = burstPhotos,
                recoverableBytes = bytes
            )
        }

        // 4. Very large screen recordings / videos
        val largeVideos = unreviewed.filter {
            it.isVideo && it.fileSizeBytes >= 20L * 1024L * 1024L && it !in redundantVideos
        }.sortedByDescending { it.fileSizeBytes }
        if (largeVideos.isNotEmpty()) {
            val bytes = largeVideos.sumOf { it.fileSizeBytes }
            val isScreenRecording = largeVideos.all {
                it.title.contains("screen", ignoreCase = true) || it.folderName.contains("screen", ignoreCase = true)
            }
            val noun = if (isScreenRecording) "very large screen recording" else "large video"
            suggestions += AiHomeSuggestion(
                id = "suggest_large_videos",
                headline = "${largeVideos.size} $noun${if (largeVideos.size == 1) "" else "s"} (${formatBytes(bytes)})",
                supportingText = "Over 20 MB each • Inspect with 5-frame storyboard",
                badgeText = "Review ${largeVideos.size}",
                accentColorHex = 0xFFF59E0B,
                candidatePhotos = largeVideos,
                recoverableBytes = bytes
            )
        }

        // 5. Expired OTP / verification screenshots
        val expiredOtps = unreviewed.filter {
            it.screenshotSubTypeEnum == ScreenshotSubType.TEMPORARY_OTP && it !in oldTempScreenshots
        }
        if (expiredOtps.isNotEmpty()) {
            val bytes = expiredOtps.sumOf { it.fileSizeBytes }
            suggestions += AiHomeSuggestion(
                id = "suggest_expired_otp",
                headline = "${expiredOtps.size} screenshot${if (expiredOtps.size == 1) "" else "s"} look like temporary verification codes",
                supportingText = "${formatBytes(bytes)} • One-time login codes safe to clean",
                badgeText = "Review ${expiredOtps.size}",
                accentColorHex = 0xFF10B981,
                candidatePhotos = expiredOtps,
                recoverableBytes = bytes
            )
        }

        return suggestions
    }

    fun generateDescriptiveClusterTitle(
        members: List<PhotoEntity>,
        clusterType: MediaClusterType
    ): String {
        val bestShot = members.firstOrNull { it.isBestShotInCluster } ?: members.firstOrNull() ?: return "Similar Photos"
        return generateClusterTitle(members, bestShot, clusterType)
    }

    /**
     * Generates a descriptive cluster title from semantic/OCR/local metadata when confident,
     * falling back to a neutral deterministic title when confidence is weak (Pass 3 Section M).
     * Never hallucinates overly specific titles.
     */
    fun generateClusterTitle(
        members: List<PhotoEntity>,
        bestShot: PhotoEntity,
        clusterType: MediaClusterType
    ): String {
        // 1. Respect explicit user rename override
        val manualOverride = members.firstNotNullOfOrNull {
            it.clusterTitleOverride.takeIf { title -> title.isNotBlank() }
        }
        if (manualOverride != null) return manualOverride

        val dateSuffix = bestShot.shortDateLabel.let { if (it.isNotBlank()) " • $it" else "" }

        // 2. Check for rich, non-generic AI description on any member
        val richDescription = members.mapNotNull { photo ->
            val desc = photo.aiDescription.trim()
            val isGenericBoilerplate = desc.isEmpty() ||
                desc.startsWith("Verified by local forensics", ignoreCase = true) ||
                desc.matches(Regex("""^.+ in .+ \(\d+x\d+\)$""")) ||
                desc.matches(Regex("""^Video clip .*"""))
            if (!isGenericBoilerplate && desc.length in 8..60) {
                desc.removeSuffix(".")
            } else {
                null
            }
        }.firstOrNull()

        if (richDescription != null) {
            val words = richDescription.split(Regex("""\s+""")).take(5).joinToString(" ")
            return words.replaceFirstChar { it.uppercase(Locale.US) }
        }

        // 3. Check high-confidence OCR or domain-specific tags (e.g. Amazon order, Grow room plants, Golden retriever)
        val combinedOcr = members.joinToString(" ") { it.ocrText }.lowercase(Locale.US)
        val combinedTags = members.joinToString(",") { it.semanticTags }.lowercase(Locale.US)
        val combinedTitles = members.joinToString(" ") { it.title }.lowercase(Locale.US)

        when {
            combinedOcr.contains("amazon") && bestShot.categoryEnum == PhotoCategory.SCREENSHOT ->
                return "Amazon order screenshots"
            combinedTags.contains("golden retriever") || combinedTitles.contains("retriever") ->
                return "Golden retriever photos$dateSuffix"
            bestShot.categoryEnum == PhotoCategory.PLANT || combinedTags.contains("grow") || combinedTags.contains("cannabis") ->
                return if (combinedTags.contains("grow")) {
                    "Grow room plant closeups"
                } else {
                    "Plant closeups$dateSuffix"
                }
            bestShot.categoryEnum == PhotoCategory.PET || combinedTags.contains("dog") || combinedTags.contains("cat") ->
                return "Pet sequence$dateSuffix"
            bestShot.categoryEnum == PhotoCategory.RECEIPT || bestShot.screenshotSubTypeEnum == ScreenshotSubType.RECEIPT_INVOICE ->
                return "Receipt copies$dateSuffix"
        }

        // 4. Deterministic neutral fallback when semantic confidence is weak
        return when (clusterType) {
            MediaClusterType.SIMILAR_VIDEO -> "Video Sequence$dateSuffix"
            MediaClusterType.BURST_SEQUENCE -> "Burst Sequence$dateSuffix"
            MediaClusterType.SCREENSHOT_VARIANT -> "Screenshot Variants$dateSuffix"
            MediaClusterType.EXACT_DUPLICATE -> "Exact Duplicates$dateSuffix"
            MediaClusterType.NEAR_DUPLICATE -> "Similar Photos$dateSuffix"
            MediaClusterType.SIMILAR_PHOTO -> "Similar Photos$dateSuffix"
        }
    }

    /**
     * Produces short, human-friendly best-shot explanations merging deterministic quality signals
     * with AI reasoning (Pass 3 Section N). Never exposes raw dHash, pHash, or Laplacian variance.
     */
    fun formatUserFacingBestShotExplanations(
        bestShot: PhotoEntity,
        clusterMembers: List<PhotoEntity> = emptyList()
    ): List<String> {
        if (bestShot.userSelectedBestShot) {
            return listOf("Selected by you")
        }
        val runnersUp = clusterMembers.filter { it.id != bestShot.id }
        val reasons = mutableListOf<String>()
        val secondBest = runnersUp.maxByOrNull { it.overallQualityScore }

        val aiText = (bestShot.aiDescription + " " + bestShot.junkReason + " " + bestShot.bestShotReason).lowercase(Locale.US)
        if (aiText.contains("eyes open") || aiText.contains("smiling")) {
            reasons += "Eyes open"
        }

        if (secondBest != null) {
            val sharpGain = bestShot.sharpnessScore - secondBest.sharpnessScore
            val expGain = bestShot.exposureScore - secondBest.exposureScore
            val motionGain = bestShot.motionStabilityScore - secondBest.motionStabilityScore
            val winnerPixels = bestShot.width.toLong() * bestShot.height.toLong()
            val secondPixels = secondBest.width.toLong() * secondBest.height.toLong()

            if (bestShot.isVideo && motionGain >= 6) {
                reasons += "Less motion blur"
            }
            if (sharpGain >= 5) {
                reasons += "Sharper subject"
            } else if (bestShot.sharpnessScore >= 75 && !bestShot.isVideo) {
                reasons += "Sharper subject"
            }
            if (expGain >= 5 || bestShot.exposureScore >= 80) {
                reasons += "Better exposure"
            }
            if (winnerPixels > secondPixels && secondPixels > 0L) {
                reasons += "Higher resolution"
            }
        } else {
            if (bestShot.isVideo && bestShot.motionStabilityScore >= 70) {
                reasons += "Less motion blur"
            }
            if (bestShot.sharpnessScore >= 72) {
                reasons += "Sharper subject"
            }
            if (bestShot.exposureScore >= 75) {
                reasons += "Better exposure"
            }
            if (bestShot.width * bestShot.height >= 1920 * 1080) {
                reasons += "Higher resolution"
            }
        }

        if (reasons.isEmpty()) {
            if (bestShot.isVideo) {
                reasons += "Less motion blur"
                reasons += "Sharper subject"
            } else {
                reasons += "Sharper subject"
                reasons += "Better exposure"
            }
        }

        return reasons.distinct().take(3)
    }

    fun buildUserFacingBestShotReason(
        winner: PhotoEntity,
        runnersUp: List<PhotoEntity>
    ): String {
        return formatUserFacingBestShotExplanations(winner, listOf(winner) + runnersUp).joinToString(" • ")
    }

    /**
     * Strips internal forensic jargon (dHash, pHash, Laplacian) from normal UI strings (Pass 3 Section N).
     */
    fun sanitizeReasonForNormalUi(rawReason: String): String {
        if (rawReason.isBlank()) return ""
        return rawReason
            .replace(Regex("""(?i)\b(dhash|phash|laplacian|hamming\s*distance)\b[^•.,;]*[•.,;]?"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trim('•', ',', ';', ' ')
    }
}
