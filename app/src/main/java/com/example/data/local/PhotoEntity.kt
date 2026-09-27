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
    LANDSCAPE("Camera Photos", 0xFF168BFF)
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
    SERIAL_MODEL_TAG("Serial / Hardware Label", true)
}

enum class TriageStatus(val label: String) {
    UNREVIEWED("Ready to Review"),
    KEEP("Kept"),
    FAVORITE_ARCHIVE("Favorite"),
    TRASH_VAULT("Review Bin")
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
    val mimeType: String = "image/jpeg"
) {
    val categoryEnum: PhotoCategory
        get() = runCatching { PhotoCategory.valueOf(category) }.getOrDefault(PhotoCategory.LANDSCAPE)

    val screenshotSubTypeEnum: ScreenshotSubType
        get() = runCatching { ScreenshotSubType.valueOf(screenshotSubType) }.getOrDefault(ScreenshotSubType.NONE)

    val triageStatusEnum: TriageStatus
        get() = runCatching { TriageStatus.valueOf(triageStatus) }.getOrDefault(TriageStatus.UNREVIEWED)

    val ageInDays: Int
        get() {
            val diff = (System.currentTimeMillis() - dateTakenEpochMs).coerceAtLeast(0L)
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
        get() = runCatching {
            SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date(dateTakenEpochMs))
        }.getOrDefault("")

    val shortDateLabel: String
        get() = runCatching {
            SimpleDateFormat("MMM d", Locale.US).format(Date(dateTakenEpochMs))
        }.getOrDefault("")

    /**
     * Unique, deterministic cache & UI identity key combining database ID, canonical URI, size, and timestamp.
     * Prevents any positional or filename-only collisions across lists, grids, and swipe decks.
     */
    val stableIdentityKey: String
        get() = "${id}_${uriString}_${fileSizeBytes}_${dateTakenEpochMs}"

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
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    val naturalLanguagePrompt: String
        get() = rawNaturalPrompt

    val isProtectionRule: Boolean
        get() = rawNaturalPrompt.lowercase().let {
            it.startsWith("never ") || it.startsWith("protect ") || it.startsWith("keep ")
        }
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
    val estimatedCostUsd: Double,
    val servedFromEncryptedCache: Boolean,
    val verdictSummary: String
)
