package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

enum class PhotoCategory(val label: String, val spineHex: Long) {
    VIDEO("Videos", 0xFFEC4899),
    SCREENSHOT("Screenshots", 0xFF06B6D4),
    RECEIPT("Receipts", 0xFF3B82F6),
    DOCUMENT("Documents", 0xFF6366F1),
    MEME("Memes & Social", 0xFFF59E0B),
    BLURRY("Blurry Shots", 0xFFF43F5E),
    PET("Pets & Animals", 0xFF10B981),
    PLANT("Plants & Botanicals", 0xFF22C55E),
    LANDSCAPE("Landscapes & Camera", 0xFF8B5CF6),
    SELFIE("Portraits", 0xFFEC4899),
    DOWNLOAD("Downloads", 0xFF94A3B8)
}

enum class ScreenshotSubType(val label: String, val isImportantDefault: Boolean) {
    NONE("Standard Media", false),
    TEMPORARY_OTP("Expired 2FA / OTP Code", false),
    OLD_UI_CAPTURE("Legacy App / System UI", false),
    MEME_SOCIAL("Saved Social Post / Meme", false),
    CONFIRMATION_NUMBER("Order / Flight Confirmation", true),
    RECEIPT_INVOICE("Tax Receipt / Invoice", true),
    IMPORTANT_CONVERSATION("Important Chat / Address", true),
    PASSWORD_WIFI("Credentials / Serial / Wi-Fi", true)
}

enum class TriageStatus(val label: String) {
    UNREVIEWED("Unreviewed"),
    KEEP("Kept"),
    FAVORITE_ARCHIVE("Archived / Favorite"),
    TRASH_VAULT("Quarantine Vault")
}

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uriString: String,
    val drawableResName: String? = null,
    val title: String,
    val folderName: String,
    val dateTakenEpochMs: Long,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
    val dHash: String,
    val pHash: String,
    val sharpnessScore: Int, // 0..100 derived from Laplacian variance
    val laplacianVariance: Float,
    val exposureScore: Int, // 0..100 derived from luminance histogram
    val framingScore: Int, // 0..100 derived from center/edge contrast
    val overallQualityScore: Int, // 0..100 weighted composite
    val category: String, // PhotoCategory.name
    val screenshotSubType: String = ScreenshotSubType.NONE.name,
    val ocrText: String = "",
    val aiDescription: String = "",
    val semanticTags: String = "",
    val junkConfidence: Float = 0f, // 0.0f..1.0f
    val junkReason: String = "",
    val duplicateClusterId: String? = null,
    val isBestShotInCluster: Boolean = false,
    val triageStatus: String = TriageStatus.UNREVIEWED.name,
    val vaultedAtEpochMs: Long? = null,
    val sentimentalProtected: Boolean = false,
    val analyzedByProvider: String = "LOCAL_FORENSICS",
    val mediaType: String = "IMAGE", // "IMAGE" or "VIDEO"
    val durationMs: Long = 0L,
    val mimeType: String = "image/jpeg"
) {
    val categoryEnum: PhotoCategory
        get() = runCatching { PhotoCategory.valueOf(category) }.getOrDefault(PhotoCategory.LANDSCAPE)

    val screenshotSubTypeEnum: ScreenshotSubType
        get() = runCatching { ScreenshotSubType.valueOf(screenshotSubType) }.getOrDefault(ScreenshotSubType.NONE)

    val triageStatusEnum: TriageStatus
        get() = runCatching { TriageStatus.valueOf(triageStatus) }.getOrDefault(TriageStatus.UNREVIEWED)

    val isVideo: Boolean
        get() = mediaType.equals("VIDEO", ignoreCase = true) || mimeType.startsWith("video/", ignoreCase = true)

    val formattedDuration: String
        get() {
            if (durationMs <= 0L) return "0:00"
            val totalSecs = (durationMs / 1000L).coerceAtLeast(1L)
            val mins = totalSecs / 60L
            val secs = totalSecs % 60L
            return if (mins >= 60L) {
                val hrs = mins / 60L
                val remMins = mins % 60L
                String.format(Locale.US, "%d:%02d:%02d", hrs, remMins, secs)
            } else {
                String.format(Locale.US, "%d:%02d", mins, secs)
            }
        }

    val ageInDays: Int
        get() = ((System.currentTimeMillis() - dateTakenEpochMs) / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
}

@Entity(tableName = "cleanup_rules")
data class CleanupRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawNaturalPrompt: String,
    val minAgeDays: Int = 0,
    val maxAgeDays: Int? = null,
    val targetCategoriesCsv: String = "", // e.g., "SCREENSHOT,MEME,VIDEO"
    val excludedKeywordsCsv: String = "", // e.g., "receipt,password,address,confirmation,conversation,serial"
    val requiredKeywordsCsv: String = "",
    val maxSharpnessScore: Int? = null,
    val onlyNonBestDuplicates: Boolean = false,
    val minJunkConfidence: Float = 0.35f,
    val isEnabled: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_audit_logs")
data class AiAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
