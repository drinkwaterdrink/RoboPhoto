package com.example.domain.rules

import com.example.data.local.CleanupRuleEntity
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.ScreenshotSubType
import com.example.data.local.TriageStatus

data class RuleEvaluationPreview(
    val rule: CleanupRuleEntity,
    val matchedForCleanup: List<PhotoEntity>,
    val protectedByExclusions: List<Pair<PhotoEntity, String>>,
    val totalRecoverableBytes: Long
) {
    val matchingPhotos: List<PhotoEntity>
        get() = matchedForCleanup

    val recoverableBytes: Long
        get() = totalRecoverableBytes
}

data class GoalCleanupTier(
    val tierNumber: Int,
    val title: String,
    val riskBadge: String,
    val description: String,
    val photos: List<PhotoEntity>,
    val bytesRecoverable: Long
)

data class GoalCleanupPlan(
    val targetBytes: Long,
    val selectedPhotos: List<PhotoEntity>,
    val achievedBytes: Long,
    val tiers: List<GoalCleanupTier>
)

object NaturalLanguageRuleEngine {

    /**
     * Converts a natural-language cleanup instruction into a structured, executable CleanupRuleEntity.
     * Supports commands like:
     * - "Screenshots older than 90 days can be suggested for deletion unless they contain receipts, passwords, addresses, important conversations, or confirmation numbers."
     * - "Show me duplicate cannabis plant photos from August"
     * - "Find screenshots older than six months that don't contain anything important"
     */
    fun parseNaturalLanguageToRule(prompt: String): CleanupRuleEntity {
        val lower = prompt.lowercase().trim()

        // 1. Parse Age Threshold ("older than X days / months / weeks / year")
        var minAgeDays = 0
        val daysRegex = Regex("""older than\s+(\d+)\s*day""")
        val monthsRegex = Regex("""older than\s+(\d+|six|three|two|one|nine|twelve)\s*month""")
        daysRegex.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            minAgeDays = it
        }
        monthsRegex.find(lower)?.groupValues?.getOrNull(1)?.let { token ->
            val months = when (token) {
                "one" -> 1
                "two" -> 2
                "three" -> 3
                "six" -> 6
                "nine" -> 9
                "twelve" -> 12
                else -> token.toIntOrNull() ?: 6
            }
            minAgeDays = months * 30
        }
        if (lower.contains("2023")) {
            minAgeDays = maxOf(minAgeDays, 365)
        }
        if (minAgeDays == 0 && (lower.contains("old screenshot") || lower.contains("old video") || lower.contains("old photo"))) {
            minAgeDays = 90
        }

        // 2. Parse Target Categories
        val categories = mutableSetOf<PhotoCategory>()
        if (lower.contains("video") || lower.contains("clip") || lower.contains("recording")) {
            categories += PhotoCategory.VIDEO
        }
        if (lower.contains("screenshot")) categories += PhotoCategory.SCREENSHOT
        if (lower.contains("meme")) categories += PhotoCategory.MEME
        if (lower.contains("blur") || lower.contains("out of focus") || lower.contains("shaky")) {
            categories += PhotoCategory.BLURRY
        }
        if (lower.contains("plant") || lower.contains("cannabis") || lower.contains("botanical") || lower.contains("grow")) {
            categories += PhotoCategory.PLANT
        }
        if (lower.contains("dog") || lower.contains("pet") || lower.contains("cat")) {
            categories += PhotoCategory.PET
        }
        if (lower.contains("receipt") && !lower.contains("unless") && !lower.contains("exclud") && !lower.contains("without")) {
            categories += PhotoCategory.RECEIPT
        }

        // 3. Parse Exclusions ("unless they contain ...", "excluding ...", "except ...", "don't contain ...", "aren't important", "don't need")
        val excludedKeywords = mutableSetOf<String>()
        val exclusionSplit = lower.split(
            "unless", "exclud", "except", "without", "don't contain", "do not contain",
            "aren't important", "are not important", "not important", "don't need", "do not need"
        )
        if (exclusionSplit.size > 1) {
            val exclusionClause = exclusionSplit.drop(1).joinToString(" ")
            val candidateExclusions = listOf(
                "receipt", "password", "address", "confirmation", "conversation",
                "serial", "invoice", "important", "ticket", "flight", "wifi"
            )
            for (kw in candidateExclusions) {
                if (exclusionClause.contains(kw)) {
                    excludedKeywords += kw
                }
            }
            if (
                exclusionClause.contains("anything important") ||
                lower.contains("aren't important") ||
                lower.contains("are not important") ||
                lower.contains("not important") ||
                lower.contains("don't need") ||
                lower.contains("do not need")
            ) {
                excludedKeywords.addAll(listOf("receipt", "password", "address", "confirmation", "conversation", "serial"))
            }
        }

        // 4. Parse Required Semantic / Date Keywords
        val requiredKeywords = mutableSetOf<String>()
        if (lower.contains("august")) requiredKeywords += "august"
        if (lower.contains("2023")) requiredKeywords += "2023"
        if (lower.contains("cannabis") || lower.contains("plant")) requiredKeywords += "plant"
        if (lower.contains("dog")) requiredKeywords += "dog"
        if (lower.contains("outside") || lower.contains("outdoor")) requiredKeywords += "outside"
        if (lower.contains("serial")) requiredKeywords += "serial"

        // 5. Parse Duplicate / Sharpness / Size / Duration Flags
        val onlyDuplicates = lower.contains("duplicate") || lower.contains("similar") || lower.contains("burst")
        val maxSharpness = if (lower.contains("blur")) 45 else null

        var minFileSizeBytes = 0L
        val mbRegex = Regex("""(?:over|larger than|bigger than|at least|>)\s*(\d+)\s*mb""")
        val gbRegex = Regex("""(?:over|larger than|bigger than|at least|>)\s*(\d+)\s*gb""")
        mbRegex.find(lower)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { mb ->
            minFileSizeBytes = mb * 1024L * 1024L
        }
        gbRegex.find(lower)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { gb ->
            minFileSizeBytes = gb * 1024L * 1024L * 1024L
        }

        var minDurationMs = 0L
        var maxDurationMs: Long? = null
        val minMinutesRegex = Regex("""(?:longer than|over|at least|>)\s*(\d+)\s*(?:minute|min)""")
        val minSecondsRegex = Regex("""(?:longer than|over|at least|>)\s*(\d+)\s*(?:second|sec)""")
        val maxSecondsRegex = Regex("""(?:shorter than|under|less than|<)\s*(\d+)\s*(?:second|sec)""")
        minMinutesRegex.find(lower)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { mins ->
            minDurationMs = mins * 60_000L
        }
        minSecondsRegex.find(lower)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { secs ->
            minDurationMs = maxOf(minDurationMs, secs * 1_000L)
        }
        maxSecondsRegex.find(lower)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { secs ->
            maxDurationMs = secs * 1_000L
        }

        // Build concise title
        val title = when {
            categories.contains(PhotoCategory.VIDEO) && minFileSizeBytes > 0L ->
                "Videos > ${minFileSizeBytes / (1024 * 1024)} MB"
            categories.contains(PhotoCategory.VIDEO) && minDurationMs > 0L ->
                "Videos > ${minDurationMs / 1000}s"
            categories.contains(PhotoCategory.SCREENSHOT) && minAgeDays > 0 ->
                "Screenshots > ${minAgeDays}d (Safe Exclusions)"
            onlyDuplicates && categories.contains(PhotoCategory.PLANT) ->
                "Duplicate Plant Photos (Keep Best)"
            onlyDuplicates ->
                "Near-Duplicate Burst Extras"
            categories.contains(PhotoCategory.BLURRY) ->
                "Blurry & Defocused Captures"
            else ->
                prompt.take(42).replaceFirstChar { it.uppercase() }
        }

        return CleanupRuleEntity(
            title = title,
            rawNaturalPrompt = prompt.trim(),
            minAgeDays = minAgeDays,
            targetCategoriesCsv = categories.joinToString(",") { it.name },
            excludedKeywordsCsv = excludedKeywords.joinToString(","),
            requiredKeywordsCsv = requiredKeywords.joinToString(","),
            maxSharpnessScore = maxSharpness,
            onlyNonBestDuplicates = onlyDuplicates,
            minJunkConfidence = 0.30f,
            isEnabled = true,
            minFileSizeBytes = minFileSizeBytes,
            minDurationMs = minDurationMs,
            maxDurationMs = maxDurationMs
        )
    }

    /**
     * Evaluates a CleanupRuleEntity against the active photo library, returning both matched candidates
     * and items explicitly protected by the rule's exclusion clauses.
     */
    fun evaluateRule(rule: CleanupRuleEntity, allPhotos: List<PhotoEntity>): RuleEvaluationPreview {
        val activePhotos = allPhotos.filter { it.triageStatusEnum != TriageStatus.TRASH_VAULT }

        val targetCategories = rule.targetCategoriesCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val excludedKeywords = rule.excludedKeywordsCsv
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        val requiredKeywords = rule.requiredKeywordsCsv
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        val matched = mutableListOf<PhotoEntity>()
        val protectedList = mutableListOf<Pair<PhotoEntity, String>>()

        for (photo in activePhotos) {
            // 1. Category check
            if (targetCategories.isNotEmpty() && photo.category !in targetCategories) continue

            // 2. Age check
            if (photo.ageInDays < rule.minAgeDays) continue
            if (rule.maxAgeDays != null && photo.ageInDays > rule.maxAgeDays) continue

            // 3. Sharpness, file size, and duration checks
            if (rule.maxSharpnessScore != null && photo.sharpnessScore > rule.maxSharpnessScore) continue
            if (rule.minFileSizeBytes > 0L && photo.fileSizeBytes < rule.minFileSizeBytes) continue
            if (rule.minDurationMs > 0L && photo.durationMs < rule.minDurationMs) continue
            if (rule.maxDurationMs != null && photo.durationMs > rule.maxDurationMs) continue

            // 4. Required keywords check
            val searchableBlob = "${photo.title} ${photo.folderName} ${photo.ocrText} ${photo.aiDescription} ${photo.semanticTags}".lowercase()
            if (requiredKeywords.isNotEmpty() && !requiredKeywords.all { kw -> searchableBlob.contains(kw) }) {
                continue
            }

            // 5. Duplicate check
            if (rule.onlyNonBestDuplicates) {
                if (photo.duplicateClusterId == null) continue
                if (photo.isBestShotInCluster) {
                    protectedList += photo to "Protected: Nominated Best-Shot in duplicate cluster (${photo.sharpnessScore}/100 Sharpness)"
                    continue
                }
            }

            // 6. Check Exclusion Clauses (receipts, passwords, addresses, important conversations, confirmation numbers)
            val triggeredExclusion = excludedKeywords.firstOrNull { kw ->
                searchableBlob.contains(kw) ||
                    (kw == "receipt" && photo.screenshotSubTypeEnum == ScreenshotSubType.RECEIPT_INVOICE) ||
                    (kw == "confirmation" && photo.screenshotSubTypeEnum == ScreenshotSubType.CONFIRMATION_NUMBER) ||
                    (kw == "password" && photo.screenshotSubTypeEnum == ScreenshotSubType.PASSWORD_WIFI) ||
                    (kw == "conversation" && photo.screenshotSubTypeEnum == ScreenshotSubType.IMPORTANT_CONVERSATION) ||
                    (kw == "address" && photo.screenshotSubTypeEnum == ScreenshotSubType.IMPORTANT_CONVERSATION)
            }

            if (triggeredExclusion != null) {
                protectedList += photo to "Excluded by rule: Contains '$triggeredExclusion' (${photo.screenshotSubTypeEnum.label})"
                continue
            }

            if (photo.triageStatusEnum == TriageStatus.FAVORITE_ARCHIVE) {
                protectedList += photo to "Protected: Marked as Favorite / Archive"
                continue
            }

            matched += photo
        }

        val recoverableBytes = matched.sumOf { it.fileSizeBytes }
        return RuleEvaluationPreview(
            rule = rule,
            matchedForCleanup = matched,
            protectedByExclusions = protectedList,
            totalRecoverableBytes = recoverableBytes
        )
    }

    /**
     * Semantic & OCR Search across natural language queries:
     * e.g., "pictures of my dog outside", "receipts from 2023", "grow room equipment", "photos containing serial numbers"
     */
    fun semanticSearch(query: String, allPhotos: List<PhotoEntity>): List<PhotoEntity> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return allPhotos.filter { it.triageStatusEnum != TriageStatus.TRASH_VAULT }

        val stopWords = setOf(
            "pictures", "photos", "images", "of", "my", "the", "show", "me", "find",
            "containing", "with", "from", "in", "that", "are", "all", "for"
        )
        val tokens = clean.split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.length > 1 && it !in stopWords }

        if (tokens.isEmpty()) return allPhotos.filter { it.triageStatusEnum != TriageStatus.TRASH_VAULT }

        return allPhotos
            .filter { it.triageStatusEnum != TriageStatus.TRASH_VAULT }
            .mapNotNull { photo ->
                val haystack = buildString {
                    append(photo.title.lowercase()).append(' ')
                    append(photo.folderName.lowercase()).append(' ')
                    append(photo.categoryEnum.label.lowercase()).append(' ')
                    append(photo.screenshotSubTypeEnum.label.lowercase()).append(' ')
                    append(photo.ocrText.lowercase()).append(' ')
                    append(photo.aiDescription.lowercase()).append(' ')
                    append(photo.semanticTags.lowercase()).append(' ')
                    if (photo.duplicateClusterId != null) append("duplicate similar burst ")
                }

                val matchedTokens = tokens.count { token ->
                    val stem = token.removeSuffix("s")
                    haystack.contains(token) || (stem.length >= 3 && haystack.contains(stem))
                }
                if (matchedTokens > 0) {
                    photo to matchedTokens
                } else {
                    null
                }
            }
            .sortedWith(
                compareByDescending<Pair<PhotoEntity, Int>> { it.second }
                    .thenByDescending { it.first.overallQualityScore }
            )
            .map { it.first }
    }

    /**
     * Constructs a safest-first tiered Cleanup Plan to achieve a user's target byte recovery goal.
     */
    fun buildCleanupGoalPlan(targetBytes: Long, allPhotos: List<PhotoEntity>): GoalCleanupPlan {
        val candidates = allPhotos.filter {
            it.triageStatusEnum == TriageStatus.UNREVIEWED &&
                !it.isBestShotInCluster &&
                !it.sentimentalProtected
        }

        val exactAndClusterDuplicates = candidates.filter {
            it.duplicateClusterId != null && !it.isBestShotInCluster
        }.sortedBy { it.sharpnessScore }

        val blurryShots = candidates.filter {
            it !in exactAndClusterDuplicates &&
                !it.isVideo &&
                (it.categoryEnum == PhotoCategory.BLURRY || it.sharpnessScore < 42)
        }.sortedByDescending { it.junkConfidence }

        val tempScreenshots = candidates.filter {
            it !in exactAndClusterDuplicates &&
                it !in blurryShots &&
                (it.categoryEnum == PhotoCategory.SCREENSHOT ||
                    it.categoryEnum == PhotoCategory.MEME ||
                    it.screenshotSubTypeEnum == ScreenshotSubType.TEMPORARY_OTP) &&
                !it.screenshotSubTypeEnum.isImportantDefault
        }.sortedByDescending { it.junkConfidence }

        val largeVideos = candidates.filter {
            it !in exactAndClusterDuplicates &&
                it !in blurryShots &&
                it !in tempScreenshots &&
                (it.isVideo || it.categoryEnum == PhotoCategory.VIDEO)
        }.sortedByDescending { it.fileSizeBytes }

        val tiers = listOf(
            GoalCleanupTier(
                tierNumber = 1,
                title = "Exact duplicates",
                riskBadge = "Best-Shot Preserved",
                description = "Extra duplicate & burst frames where a sharper Best Shot is already kept.",
                photos = exactAndClusterDuplicates,
                bytesRecoverable = exactAndClusterDuplicates.sumOf { it.fileSizeBytes }
            ),
            GoalCleanupTier(
                tierNumber = 2,
                title = "Blurry shots",
                riskBadge = "Zero Sentimental Risk",
                description = "Out-of-focus pocket shots and motion-blurred frames.",
                photos = blurryShots,
                bytesRecoverable = blurryShots.sumOf { it.fileSizeBytes }
            ),
            GoalCleanupTier(
                tierNumber = 3,
                title = "Temporary screenshots",
                riskBadge = "OCR Verified Safe",
                description = "Old temporary screenshots & memes with receipts and passwords excluded.",
                photos = tempScreenshots,
                bytesRecoverable = tempScreenshots.sumOf { it.fileSizeBytes }
            ),
            GoalCleanupTier(
                tierNumber = 4,
                title = "Large videos",
                riskBadge = "High Space Recovery",
                description = "Large video clips ready for quick storyboard review.",
                photos = largeVideos,
                bytesRecoverable = largeVideos.sumOf { it.fileSizeBytes }
            )
        )

        val orderedPool = exactAndClusterDuplicates + blurryShots + tempScreenshots + largeVideos
        val selected = mutableListOf<PhotoEntity>()
        var runningBytes = 0L
        for (photo in orderedPool) {
            if (runningBytes >= targetBytes && selected.isNotEmpty()) break
            selected += photo
            runningBytes += photo.fileSizeBytes
        }

        return GoalCleanupPlan(
            targetBytes = targetBytes,
            selectedPhotos = selected,
            achievedBytes = runningBytes,
            tiers = tiers
        )
    }

    fun defaultStarterRules(): List<CleanupRuleEntity> {
        return listOf(
            parseNaturalLanguageToRule(
                "Screenshots older than 90 days can be suggested for deletion unless they contain receipts, passwords, addresses, important conversations, or confirmation numbers."
            ),
            parseNaturalLanguageToRule(
                "Show me duplicate cannabis plant photos from August excluding best shots."
            ),
            parseNaturalLanguageToRule(
                "Find blurry photos that don't contain anything important."
            )
        )
    }
}
