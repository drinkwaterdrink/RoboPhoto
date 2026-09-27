package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PhotoCategory(val label: String, val spineHex: Long) {
    VIDEO("Large Videos", 0xFF168BFF),
    SCREENSHOT("Screenshots", 0xFF38BDF8),
    BLURRY("Blurry Photos", 0xFFE5484D),
    RECEIPT("Receipts & Bills", 0xFF14B8A6),
    DOCUMENT("Documents", 0xFF6366F1),
    DOWNLOAD("Downloads", 0xFFF59E0B),
    MEME("Memes & Social", 0xFFA855F7),
    PLANT("Nature & Plants", 0xFF10B981),
    PET("Pets & Animals", 0xFFF97316),
    PEOPLE("People & Portraits", 0xFFEC4899),
    LANDSCAPE("Camera Photos", 0xFF168BFF),
    UNKNOWN("Uncategorized", 0xFF646A75);

    val displayName: String
        get() = label

    companion object {
        /**
         * Single canonical list of category names shared with AI prompts and schema parsers.
         */
        fun canonicalPromptValues(): String =
            entries.filter { it != UNKNOWN }.joinToString(", ") { it.name }

        /**
         * Parses an AI-returned category string against the canonical enum.
         * Returns the resolved [PhotoCategory] and an optional audit note if a synonym or fallback was applied.
         */
        fun parseFromAi(raw: String?): Pair<PhotoCategory, String?> {
            val normalized = raw?.trim()?.uppercase(Locale.US).orEmpty()
            if (normalized.isEmpty()) {
                return UNKNOWN to "Missing AI category -> mapped to UNKNOWN"
            }
            entries.firstOrNull { it.name == normalized }?.let { exact ->
                return exact to null
            }
            return when (normalized) {
                "SELFIE", "PORTRAIT", "PERSON", "FACES" ->
                    PEOPLE to "Mapped legacy AI category '$normalized' -> PEOPLE"
                "MEME_SOCIAL", "SOCIAL" ->
                    MEME to "Mapped legacy AI category '$normalized' -> MEME"
                else ->
                    UNKNOWN to "Unrecognized AI category '$normalized' -> mapped to UNKNOWN"
            }
        }
    }
}

enum class ScreenshotSubType(val label: String, val isImportantDefault: Boolean) {
    NONE("Not a Screenshot", false),
    OLD_UI_CAPTURE("Temporary Screenshot", false),
    SOCIAL_FEED("Social Feed Capture", false),
    MEME_REPOST("Saved Meme / Image", false),
    TEMPORARY_OTP("Expired Verification Code", false),
    RECEIPT_INVOICE("Receipt / Order Invoice", true),
    CONFIRMATION_NUMBER("Booking / Confirmation Code", true),
    IMPORTANT_CONVERSATION("Important Conversation", true),
    ADDRESS_MAP("Saved Address / Directions", true),
    PASSWORD_WIFI("Credentials / Recovery Key", true),
    SERIAL_MODEL_TAG("Serial / Hardware Label", true);

    val displayName: String
        get() = label

    companion object {
        /**
         * Single canonical list of screenshot sub-types shared with AI prompts and schema parsers.
         */
        fun canonicalPromptValues(): String =
            entries.joinToString(", ") { it.name }

        /**
         * Parses an AI-returned screenshotSubType string against the canonical enum.
         * Never crashes; maps unrecognized values to [NONE] with an audit note.
         */
        fun parseFromAi(raw: String?): Pair<ScreenshotSubType, String?> {
            val normalized = raw?.trim()?.uppercase(Locale.US).orEmpty()
            if (normalized.isEmpty()) {
                return NONE to "Missing AI screenshotSubType -> mapped to NONE"
            }
            entries.firstOrNull { it.name == normalized }?.let { exact ->
                return exact to null
            }
            return when (normalized) {
                "MEME_SOCIAL" ->
                    MEME_REPOST to "Mapped legacy AI screenshotSubType 'MEME_SOCIAL' -> MEME_REPOST"
                "ADDRESS", "MAP", "DIRECTIONS" ->
                    ADDRESS_MAP to "Mapped AI screenshotSubType '$normalized' -> ADDRESS_MAP"
                "SERIAL", "SERIAL_NUMBER" ->
                    SERIAL_MODEL_TAG to "Mapped AI screenshotSubType '$normalized' -> SERIAL_MODEL_TAG"
                else ->
                    NONE to "Unrecognized AI screenshotSubType '$normalized' -> mapped to NONE"
            }
        }
    }
}

enum class TriageStatus(val label: String) {
    UNREVIEWED("Ready to Review"),
    KEEP("Kept"),
    FAVORITE_ARCHIVE("Favorite"),
    TRASH_VAULT("Review Bin")
}

enum class TokenUsageSource {
    ACTUAL_FROM_PROVIDER,
    ESTIMATED,
    LOCAL_CACHE_ZERO
}

enum class MediaClusterType(
    val label: String,
    val badgeColorHex: Long,
    val allowsStrongAutomation: Boolean
) {
    EXACT_DUPLICATE("Exact Duplicate", 0xFF10B981, true),
    NEAR_DUPLICATE("Near Duplicate", 0xFF168BFF, true),
    BURST_SEQUENCE("Burst Sequence", 0xFF38BDF8, false),
    SIMILAR_PHOTO("Similar Photo", 0xFFA855F7, false),
    SIMILAR_VIDEO("Similar Video", 0xFF818CF8, false),
    SCREENSHOT_VARIANT("Screenshot Variant", 0xFFF59E0B, false);

    val displayName: String
        get() = label
}

@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["uriString"], unique = true),
        Index(value = ["duplicateClusterId"]),
        Index(value = ["triageStatus"])
    ]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uriString: String,
    val title: String,
    val folderName: String,
    val dateTakenEpochMs: Long,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
    val dHash: String,
    val pHash: String,
    val sharpnessScore: Int,
    val laplacianVariance: Float,
    val exposureScore: Int,
    val framingScore: Int,
    val overallQualityScore: Int,
    val duplicateClusterId: String? = null,
    val isBestShotInCluster: Boolean = false,
    val bestShotReason: String = "",
    val clusterTitleOverride: String = "",
    val excludedClusterKeys: String = "",
    val category: String,
    val screenshotSubType: String = ScreenshotSubType.NONE.name,
    val ocrText: String = "",
    val aiDescription: String = "",
    val semanticTags: String = "",
    val junkConfidence: Float = 0f,
    val junkReason: String = "",
    val sentimentalProtected: Boolean = false,
    val triageStatus: String = TriageStatus.UNREVIEWED.name,
    val vaultedAtEpochMs: Long? = null,
    val analyzedByProvider: String = "On-Device analysis",
    val mediaType: String = "IMAGE",
    val durationMs: Long = 0L,
    val mimeType: String = "image/jpeg",
    val capturedAtEpochMs: Long? = if (dateTakenEpochMs > 0L) dateTakenEpochMs else null,
    val importedAtEpochMs: Long = System.currentTimeMillis(),
    val dateModifiedEpochMs: Long = dateTakenEpochMs.coerceAtLeast(0L),
    val sourceGenerationModified: Long = 0L,
    val isUriPermissionPersisted: Boolean = false,
    val videoSignatureHashes: String = "",
    val aspectRatioBucket: Int = 0,
    val temporalBucket: Long = 0L,
    val lshBand0: Int = 0,
    val lshBand1: Int = 0,
    val lshBand2: Int = 0,
    val lshBand3: Int = 0,
    val userSelectedBestShot: Boolean = false,
    val motionStabilityScore: Int = 75,
    val analysisStageVersion: Int = 2,
    val clusterType: String = "",
    val clusterConfidence: Float = 0f,
    val clusterReason: String = "",
    val clusterGenerationVersion: Int = 2
) {
    val clusterTypeEnum: MediaClusterType?
        get() = if (clusterType.isBlank()) {
            null
        } else {
            runCatching { MediaClusterType.valueOf(clusterType) }.getOrNull()
        }

    val categoryEnum: PhotoCategory
        get() = runCatching { PhotoCategory.valueOf(category) }.getOrDefault(PhotoCategory.UNKNOWN)

    val screenshotSubTypeEnum: ScreenshotSubType
        get() = runCatching { ScreenshotSubType.valueOf(screenshotSubType) }.getOrDefault(ScreenshotSubType.NONE)

    val triageStatusEnum: TriageStatus
        get() = runCatching { TriageStatus.valueOf(triageStatus) }.getOrDefault(TriageStatus.UNREVIEWED)

    /**
     * True only when a genuine capture timestamp (or reliable MediaStore/EXIF timestamp) is known.
     */
    val hasReliableCaptureDate: Boolean
        get() = (capturedAtEpochMs != null && capturedAtEpochMs > 0L) || dateTakenEpochMs > 0L

    val effectiveCaptureOrModifiedEpochMs: Long?
        get() = capturedAtEpochMs?.takeIf { it > 0L }
            ?: dateTakenEpochMs.takeIf { it > 0L }
            ?: dateModifiedEpochMs.takeIf { it > 0L }

    val ageInDays: Int
        get() {
            val refTs = effectiveCaptureOrModifiedEpochMs ?: return 0
            val diff = (System.currentTimeMillis() - refTs).coerceAtLeast(0L)
            return (diff / (1000L * 60 * 60 * 24)).toInt()
        }

    val isVideo: Boolean
        get() = mediaType.equals("VIDEO", ignoreCase = true) || mimeType.startsWith("video/", ignoreCase = true)

    val formattedDuration: String
        get() {
            if (!isVideo || durationMs <= 0L) return "0:00"
            val totalSeconds = (durationMs / 1000L).coerceAtLeast(1L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        }

    val formattedCaptureDate: String
        get() {
            val ts = effectiveCaptureOrModifiedEpochMs ?: return "Capture date unknown"
            return runCatching {
                SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date(ts))
            }.getOrDefault("Capture date unknown")
        }

    val shortDateLabel: String
        get() {
            val ts = effectiveCaptureOrModifiedEpochMs ?: return ""
            return runCatching {
                SimpleDateFormat("MMM d", Locale.US).format(Date(ts))
            }.getOrDefault("")
        }

    /**
     * Unique, deterministic cache & UI identity key combining database ID, canonical URI, size,
     * capture timestamp, modification timestamp, and generation counter.
     * Prevents any positional or filename-only collisions and invalidates automatically on in-place edits.
     */
    val stableIdentityKey: String
        get() = "${id}_${uriString}_${fileSizeBytes}_${dateTakenEpochMs}_${dateModifiedEpochMs}_${sourceGenerationModified}"

    /**
     * Concise, human-friendly explanation for why RoboPhoto highlighted this item (no raw hashes).
     */
    val humanFriendlyWhy: String
        get() = when {
            isBestShotInCluster -> bestShotReason.ifBlank { "Best shot in group • Sharpest subject & balanced exposure" }
            duplicateClusterId != null -> "Similar photo • Sharper best shot already selected"
            isVideo && fileSizeBytes >= 20_000_000L -> "Large video (${formattedDuration}) taking significant space"
            isVideo && sharpnessScore < 40 -> "Shaky or low-clarity video clip (${formattedDuration})"
            categoryEnum == PhotoCategory.BLURRY || sharpnessScore < 42 -> "Low sharpness • Likely motion blur or misfocus"
            screenshotSubTypeEnum.isImportantDefault -> "Important screenshot (${screenshotSubTypeEnum.label})"
            categoryEnum == PhotoCategory.SCREENSHOT && ageInDays >= 90 -> "Old screenshot (${ageInDays} days old)"
            categoryEnum == PhotoCategory.SCREENSHOT -> "Screenshot in $folderName"
            junkReason.isNotBlank() && !junkReason.contains("dHash") && !junkReason.contains("Laplacian") -> junkReason
            else -> "${categoryEnum.label} • $folderName"
        }

    val ocrSnippet: String
        get() = ocrText

    val semanticLabels: String
        get() = semanticTags

    val sceneDescription: String
        get() = aiDescription

    val flaggedReason: String
        get() = humanFriendlyWhy

    val bucketName: String
        get() = folderName

    val faceCount: Int
        get() = if (
            categoryEnum == PhotoCategory.PEOPLE ||
            semanticTags.contains("face", ignoreCase = true) ||
            semanticTags.contains("person", ignoreCase = true) ||
            semanticTags.contains("portrait", ignoreCase = true)
        ) 1 else 0
}

@Entity(tableName = "cleanup_rules")
data class CleanupRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val rawNaturalPrompt: String,
    val minAgeDays: Int = 0,
    val maxAgeDays: Int? = null,
    val targetCategoriesCsv: String = "",
    val excludedKeywordsCsv: String = "",
    val requiredKeywordsCsv: String = "",
    val maxSharpnessScore: Int? = null,
    val onlyNonBestDuplicates: Boolean = false,
    val minJunkConfidence: Float = 0.3f,
    val isEnabled: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val minFileSizeBytes: Long = 0L,
    val minDurationMs: Long = 0L,
    val maxDurationMs: Long? = null,
    val lastEvaluatedAtEpochMs: Long = System.currentTimeMillis()
) {
    val naturalLanguagePrompt: String
        get() = rawNaturalPrompt

    val isProtectionRule: Boolean
        get() = rawNaturalPrompt.lowercase().trim().let {
            it.startsWith("never ") ||
                it.startsWith("protect ") ||
                it.startsWith("keep ") ||
                it.startsWith("do not delete ") ||
                it.startsWith("don't delete ")
        }

    val safetyExclusionsList: List<String>
        get() = excludedKeywordsCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    val formattedLastEvaluated: String
        get() {
            val ts = lastEvaluatedAtEpochMs.takeIf { it > 0L } ?: createdAtEpochMs
            val diffMin = ((System.currentTimeMillis() - ts).coerceAtLeast(0L)) / 60_000L
            return when {
                diffMin < 1L -> "Just now"
                diffMin < 60L -> "${diffMin}m ago"
                else -> runCatching {
                    SimpleDateFormat("MMM d • h:mm a", Locale.US).format(Date(ts))
                }.getOrDefault("Today")
            }
        }

    val compiledRuleSummary: String
        get() = buildList {
            if (targetCategoriesCsv.isNotBlank()) add("categories=[$targetCategoriesCsv]")
            if (minAgeDays > 0) add("minAge=${minAgeDays}d")
            if (maxAgeDays != null) add("maxAge=${maxAgeDays}d")
            if (minFileSizeBytes > 0L) add("minSize=${minFileSizeBytes / (1024 * 1024)}MB")
            if (minDurationMs > 0L) add("minDuration=${minDurationMs / 1000}s")
            if (maxSharpnessScore != null) add("maxSharpness<=${maxSharpnessScore}")
            if (onlyNonBestDuplicates) add("onlyNonBestDuplicates=true")
            if (requiredKeywordsCsv.isNotBlank()) add("require=[$requiredKeywordsCsv]")
            if (excludedKeywordsCsv.isNotBlank()) add("exclude=[$excludedKeywordsCsv]")
        }.joinToString(" • ").ifBlank { "match=all_unreviewed" }

    val compiledSummary: String
        get() = compiledRuleSummary
}

@Entity(tableName = "scan_checkpoints")
data class ScanCheckpointEntity(
    @PrimaryKey
    val id: String = "primary_library_scan",
    val phase: String = "IDLE",
    val discoveredCount: Int = 0,
    val analyzedCount: Int = 0,
    val totalCount: Int = 0,
    val changedCount: Int = 0,
    val unchangedSkippedCount: Int = 0,
    val errorCount: Int = 0,
    val lastProcessedCursorOffset: Int = 0,
    val lastProcessedUri: String = "",
    val startedAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val isPaused: Boolean = false,
    val isCancelled: Boolean = false,
    val lastErrorSummary: String = ""
)

@Entity(
    tableName = "duplicate_pair_exclusions",
    indices = [
        Index(value = ["uriA", "uriB"], unique = true)
    ]
)
data class DuplicatePairExclusionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uriA: String,
    val uriB: String,
    val clusterId: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    val canonicalPairKey: String
        get() = if (uriA <= uriB) "$uriA|$uriB" else "$uriB|$uriA"
}

@Entity(tableName = "ai_audit_logs")
data class AiAuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val providerId: String,
    val modelName: String,
    val photoTitle: String,
    val downscaledDimension: String,
    val payloadBytes: Int,
    val estimatedTokens: Int,
    val estimatedCostUsd: Double = 0.0,
    val servedFromEncryptedCache: Boolean,
    val verdictSummary: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = estimatedTokens,
    val tokenUsageSource: String = if (servedFromEncryptedCache) {
        TokenUsageSource.LOCAL_CACHE_ZERO.name
    } else {
        TokenUsageSource.ESTIMATED.name
    },
    val requestSucceeded: Boolean = true,
    val providerType: String = "CUSTOM_OPENAI",
    val requestType: String = "PHOTO_INSPECTION",
    val latencyMs: Long = 0L,
    val endpointUsed: String = ""
) {
    val tokenUsageSourceEnum: TokenUsageSource
        get() = runCatching { TokenUsageSource.valueOf(tokenUsageSource) }
            .getOrDefault(TokenUsageSource.ESTIMATED)

    val isLocalOnly: Boolean
        get() = tokenUsageSourceEnum == TokenUsageSource.LOCAL_CACHE_ZERO && !servedFromEncryptedCache ||
            providerId.contains("Local", ignoreCase = true) ||
            requestType.equals("LOCAL_ANALYSIS", ignoreCase = true)

    val formattedTime: String
        get() = runCatching {
            SimpleDateFormat("h:mm a", Locale.US).format(Date(timestampEpochMs))
        }.getOrDefault("")

    val usageSourceBadgeLabel: String
        get() = when {
            servedFromEncryptedCache -> "Cached • 0 tokens"
            tokenUsageSourceEnum == TokenUsageSource.LOCAL_CACHE_ZERO -> "Local • 0 network tokens"
            !requestSucceeded -> "Failed • ${totalTokens} tokens"
            tokenUsageSourceEnum == TokenUsageSource.ACTUAL_FROM_PROVIDER ->
                "✓ ${String.format(Locale.US, "%,d", totalTokens)} actual tokens"
            else ->
                "~ ${String.format(Locale.US, "%,d", totalTokens)} est. tokens"
        }
}
