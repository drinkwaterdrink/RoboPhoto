package com.example.domain.scanner

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.local.PhotoEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class MediaScanDecision {
    NEW_ITEM,
    UNCHANGED_ITEM,
    CHANGED_ITEM,
    DELETED_SOURCE_ITEM
}

data class SourceMediaMetadata(
    val uri: Uri,
    val displayName: String,
    val folderName: String,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val isVideo: Boolean,
    val durationMs: Long,
    val capturedAtEpochMs: Long?,
    val dateModifiedEpochMs: Long,
    val importedAtEpochMs: Long,
    val sourceGenerationModified: Long,
    val isUriPermissionPersisted: Boolean
) {
    /**
     * Genuine capture timestamp if known, or last-modified timestamp if available, or 0L if unknown.
     * NEVER substitutes System.currentTimeMillis() when capture date is unknown.
     */
    val canonicalDateTakenEpochMs: Long
        get() = capturedAtEpochMs?.takeIf { it > 0L }
            ?: dateModifiedEpochMs.takeIf { it > 0L }
            ?: 0L
}

object MediaMetadataReader {

    /**
     * Determines whether an existing database record is UNCHANGED or CHANGED compared to current
     * source metadata (generation counter, modification timestamp, file size, or dimensions).
     */
    fun classifyRevision(
        existing: PhotoEntity?,
        currentSizeBytes: Long,
        currentDateModifiedMs: Long,
        currentGenerationModified: Long = 0L,
        currentWidth: Int = 0,
        currentHeight: Int = 0
    ): MediaScanDecision {
        if (existing == null) return MediaScanDecision.NEW_ITEM

        if (currentSizeBytes > 0L && existing.fileSizeBytes != currentSizeBytes) {
            return MediaScanDecision.CHANGED_ITEM
        }
        if (currentGenerationModified > 0L &&
            existing.sourceGenerationModified > 0L &&
            existing.sourceGenerationModified != currentGenerationModified
        ) {
            return MediaScanDecision.CHANGED_ITEM
        }
        if (currentDateModifiedMs > 0L &&
            existing.dateModifiedEpochMs > 0L &&
            existing.dateModifiedEpochMs != currentDateModifiedMs
        ) {
            return MediaScanDecision.CHANGED_ITEM
        }
        if (currentWidth > 0 && existing.width > 0 && existing.width != currentWidth) {
            return MediaScanDecision.CHANGED_ITEM
        }
        if (currentHeight > 0 && existing.height > 0 && existing.height != currentHeight) {
            return MediaScanDecision.CHANGED_ITEM
        }

        return MediaScanDecision.UNCHANGED_ITEM
    }

    /**
     * Attempts to take persistable read URI permission for Photo Picker / SAF URIs.
     * Returns true if granted, false if the provider does not support persistable permissions.
     */
    fun tryTakePersistableReadPermission(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reads genuine metadata from an imported/picked URI without inventing a fake current capture timestamp.
     */
    fun readImportedUriMetadata(
        context: Context,
        uri: Uri,
        importedAtEpochMs: Long = System.currentTimeMillis()
    ): SourceMediaMetadata {
        val persisted = tryTakePersistableReadPermission(context, uri)
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "image/jpeg"
        val isVideo = mimeType.startsWith("video/", ignoreCase = true)

        var displayName = if (isVideo) "Picked_Video.mp4" else "Picked_Photo.jpg"
        var folderName = "Picked Media"
        var fileSize = 0L
        var width = 0
        var height = 0
        var durationMs = 0L
        var capturedAtMs: Long? = null
        var dateModifiedMs = 0L
        var generationMod = 0L

        if (uri.scheme == "file") {
            val file = uri.path?.let { File(it) }
            if (file != null && file.exists()) {
                displayName = file.name
                folderName = file.parentFile?.name ?: "Local Files"
                fileSize = file.length().coerceAtLeast(1L)
                dateModifiedMs = file.lastModified().coerceAtLeast(0L)
            }
        } else {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                            displayName = cursor.getString(nameIdx) ?: displayName
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            fileSize = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                        }
                        val bucketIdx = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                        if (bucketIdx >= 0 && !cursor.isNull(bucketIdx)) {
                            val b = cursor.getString(bucketIdx)
                            if (!b.isNullOrBlank()) folderName = b
                        }
                        val takenIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                        if (takenIdx >= 0 && !cursor.isNull(takenIdx)) {
                            val rawTaken = cursor.getLong(takenIdx)
                            if (rawTaken > 100_000_000_000L) {
                                capturedAtMs = rawTaken
                            }
                        }
                        val modIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                        if (modIdx >= 0 && !cursor.isNull(modIdx)) {
                            val rawMod = cursor.getLong(modIdx)
                            dateModifiedMs = if (rawMod in 1L..99_999_999_999L) rawMod * 1000L else rawMod.coerceAtLeast(0L)
                        }
                        val wIdx = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                        if (wIdx >= 0 && !cursor.isNull(wIdx)) {
                            width = cursor.getInt(wIdx).coerceAtLeast(0)
                        }
                        val hIdx = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                        if (hIdx >= 0 && !cursor.isNull(hIdx)) {
                            height = cursor.getInt(hIdx).coerceAtLeast(0)
                        }
                        val durIdx = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                        if (durIdx >= 0 && !cursor.isNull(durIdx)) {
                            durationMs = cursor.getLong(durIdx).coerceAtLeast(0L)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val genIdx = cursor.getColumnIndex(MediaStore.MediaColumns.GENERATION_MODIFIED)
                            if (genIdx >= 0 && !cursor.isNull(genIdx)) {
                                generationMod = cursor.getLong(genIdx).coerceAtLeast(0L)
                            }
                        }
                    }
                }
            }
        }

        // Try EXIF capture date for images if DATE_TAKEN was not returned by the picker cursor
        if (capturedAtMs == null && !isVideo) {
            capturedAtMs = extractExifDateTimeFromStream(context, uri)
        }

        // Try MediaMetadataRetriever for videos (duration & capture date)
        if (isVideo && (durationMs <= 0L || capturedAtMs == null)) {
            val retriever = MediaMetadataRetriever()
            try {
                var set = false
                runCatching {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        set = true
                    }
                }
                if (!set) {
                    retriever.setDataSource(context, uri)
                }
                if (durationMs <= 0L) {
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                }
                if (capturedAtMs == null) {
                    val dateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    capturedAtMs = parseMetadataRetrieverDate(dateStr)
                }
            } catch (_: Exception) {
                // Ignore if unreadable
            } finally {
                runCatching { retriever.release() }
            }
        }

        return SourceMediaMetadata(
            uri = uri,
            displayName = displayName,
            folderName = folderName,
            fileSizeBytes = fileSize.coerceAtLeast(1024L),
            width = width,
            height = height,
            mimeType = mimeType,
            isVideo = isVideo,
            durationMs = durationMs,
            capturedAtEpochMs = capturedAtMs,
            dateModifiedEpochMs = dateModifiedMs,
            importedAtEpochMs = importedAtEpochMs,
            sourceGenerationModified = generationMod,
            isUriPermissionPersisted = persisted
        )
    }

    /**
     * Scans the first 64KB of an image stream for an ASCII EXIF timestamp ("YYYY:MM:DD HH:MM:SS").
     * Avoids requiring extra dependencies while reliably recovering EXIF capture dates from picked JPEGs.
     */
    internal fun extractExifDateTimeFromStream(context: Context, uri: Uri): Long? {
        return try {
            val buffer = ByteArray(65536)
            val bytesRead = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.read(buffer)
            } ?: return null
            if (bytesRead <= 0) return null
            val ascii = String(buffer, 0, bytesRead, Charsets.ISO_8859_1)
            parseExifAsciiTimestamp(ascii)
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseExifAsciiTimestamp(asciiWindow: String): Long? {
        val regex = Regex("""\b(20\d{2}|19\d{2}):(0[1-9]|1[0-2]):([0-2]\d|3[01]) ([01]\d|2[0-3]):([0-5]\d):([0-5]\d)\b""")
        val match = regex.find(asciiWindow) ?: return null
        return runCatching {
            val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            sdf.parse(match.value)?.time
        }.getOrNull()
    }

    internal fun parseMetadataRetrieverDate(rawDate: String?): Long? {
        if (rawDate.isNullOrBlank()) return null
        val cleaned = rawDate.trim()
        val formats = listOf(
            "yyyyMMdd'T'HHmmss.SSS'Z'",
            "yyyyMMdd'T'HHmmss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy:MM:dd HH:mm:ss"
        )
        for (pattern in formats) {
            val parsed = runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    if (pattern.endsWith("'Z'")) {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                }
                sdf.parse(cleaned)?.time
            }.getOrNull()
            if (parsed != null && parsed > 100_000_000_000L) {
                return parsed
            }
        }
        return null
    }
}
