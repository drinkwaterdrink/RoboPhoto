package com.example.domain.scanner

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import androidx.core.content.ContextCompat
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.ScreenshotSubType
import com.example.data.local.TriageStatus
import com.example.data.security.EncryptedMediaCache
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device media forensics engine:
 * - Queries Android MediaStore for both Photos and Videos with canonical content:// URIs
 * - Computes 64-bit Difference Hash (dHash) and 64-bit DCT Perceptual Hash (pHash)
 * - Computes 3x3 Laplacian variance sharpness and luminance exposure histograms
 * - Performs strict, media-type-safe, temporally-aware duplicate & burst clustering
 */
class MediaScannerEngine(
    @Suppress("unused")
    private val encryptedCache: EncryptedMediaCache? = null
) {

    fun computeDHash(bitmap: Bitmap): String = computeDifferenceHash64(bitmap)

    fun computePHash(bitmap: Bitmap): String = computePerceptualHash64(bitmap)

    fun computeHammingDistance(hashA: String, hashB: String): Int = hammingDistanceHex64(hashA, hashB)

    fun cleanupLegacySampleDir(context: Context) {
        runCatching {
            val sampleDir = File(context.filesDir, "lumina_sample_media")
            if (sampleDir.exists()) {
                sampleDir.deleteRecursively()
            }
        }
    }

    fun hasMediaStorePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImages = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            val hasVideo = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            val hasVisualSelected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
            hasImages || hasVideo || hasVisualSelected
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Scans the device's MediaStore for both Images and Videos.
     * Existing database items are matched strictly by canonical uriString so their database IDs,
     * user triage status, and custom overrides are always preserved.
     */
    suspend fun scanDeviceMediaStore(
        context: Context,
        existingPhotos: List<PhotoEntity>,
        maxItemsToScan: Int = 300
    ): List<PhotoEntity> = withContext(Dispatchers.IO) {
        if (!hasMediaStorePermission(context)) return@withContext existingPhotos

        val existingByUri = existingPhotos.associateBy { it.uriString }
        val newlyScanned = mutableListOf<PhotoEntity>()

        data class RawMediaRecord(
            val uri: Uri,
            val displayName: String,
            val bucketName: String,
            val dateModifiedMs: Long,
            val sizeBytes: Long,
            val width: Int,
            val height: Int,
            val mimeType: String,
            val durationMs: Long,
            val isVideo: Boolean
        )

        val candidateRecords = mutableListOf<RawMediaRecord>()

        // 1. Query Images
        runCatching {
            val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                imageCollection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                var count = 0
                while (cursor.moveToNext() && count < maxItemsToScan) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(imageCollection, id)
                    val uriStr = contentUri.toString()
                    if (existingByUri.containsKey(uriStr)) continue

                    val name = cursor.getString(nameCol) ?: "IMG_$id.jpg"
                    val bucket = cursor.getString(bucketCol) ?: "Camera"
                    val takenMs = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val addedSec = cursor.getLong(dateAddedCol)
                    val timestampMs = when {
                        takenMs > 100_000_000_000L -> takenMs
                        addedSec > 0L -> addedSec * 1000L
                        else -> System.currentTimeMillis()
                    }
                    val size = cursor.getLong(sizeCol).coerceAtLeast(1024L)
                    val w = cursor.getInt(widthCol).coerceAtLeast(0)
                    val h = cursor.getInt(heightCol).coerceAtLeast(0)
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"

                    candidateRecords += RawMediaRecord(
                        uri = contentUri,
                        displayName = name,
                        bucketName = bucket,
                        dateModifiedMs = timestampMs,
                        sizeBytes = size,
                        width = w,
                        height = h,
                        mimeType = mime,
                        durationMs = 0L,
                        isVideo = false
                    )
                    count++
                }
            }
        }

        // 2. Query Videos
        runCatching {
            val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DURATION
            )
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                videoCollection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                var count = 0
                while (cursor.moveToNext() && count < (maxItemsToScan / 2)) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(videoCollection, id)
                    val uriStr = contentUri.toString()
                    if (existingByUri.containsKey(uriStr)) continue

                    val name = cursor.getString(nameCol) ?: "VID_$id.mp4"
                    val bucket = cursor.getString(bucketCol) ?: "Videos"
                    val takenMs = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val addedSec = cursor.getLong(dateAddedCol)
                    val timestampMs = when {
                        takenMs > 100_000_000_000L -> takenMs
                        addedSec > 0L -> addedSec * 1000L
                        else -> System.currentTimeMillis()
                    }
                    val size = cursor.getLong(sizeCol).coerceAtLeast(1024L)
                    val w = cursor.getInt(widthCol).coerceAtLeast(0)
                    val h = cursor.getInt(heightCol).coerceAtLeast(0)
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val duration = cursor.getLong(durationCol).coerceAtLeast(0L)

                    candidateRecords += RawMediaRecord(
                        uri = contentUri,
                        displayName = name,
                        bucketName = bucket,
                        dateModifiedMs = timestampMs,
                        sizeBytes = size,
                        width = w,
                        height = h,
                        mimeType = mime,
                        durationMs = duration,
                        isVideo = true
                    )
                    count++
                }
            }
        }

        val sortedRecords = candidateRecords
            .sortedByDescending { it.dateModifiedMs }
            .take(maxItemsToScan)

        for (record in sortedRecords) {
            val analyzed = analyzeMediaRecord(
                context = context,
                uri = record.uri,
                displayName = record.displayName,
                folderName = record.bucketName,
                dateTakenMs = record.dateModifiedMs,
                fileSizeBytes = record.sizeBytes,
                knownWidth = record.width,
                knownHeight = record.height,
                mimeType = record.mimeType,
                durationMs = record.durationMs,
                isVideo = record.isVideo
            )
            if (analyzed != null) {
                newlyScanned += analyzed
            }
        }

        val combined = existingPhotos + newlyScanned
        clusterAndNominateBestShots(combined)
    }

    /**
     * Scans user-picked URIs from the Android Photo Picker / Google Photos cloud provider.
     */
    suspend fun scanImportedUris(
        context: Context,
        uris: List<Uri>,
        existingPhotos: List<PhotoEntity>
    ): List<PhotoEntity> = withContext(Dispatchers.IO) {
        val existingByUri = existingPhotos.associateBy { it.uriString }
        val newlyScanned = mutableListOf<PhotoEntity>()

        for (uri in uris) {
            val uriStr = uri.toString()
            if (existingByUri.containsKey(uriStr)) continue

            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val isVideo = mimeType.startsWith("video/", ignoreCase = true)
            var displayName = if (isVideo) "Picked_Video.mp4" else "Picked_Photo.jpg"
            var fileSize = 1_500_000L

            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx) ?: displayName
                        }
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            fileSize = cursor.getLong(sizeIdx).coerceAtLeast(1024L)
                        }
                    }
                }
            }

            var durationMs = 0L
            if (isVideo) {
                durationMs = extractVideoDurationMs(context, uri)
            }

            val analyzed = analyzeMediaRecord(
                context = context,
                uri = uri,
                displayName = displayName,
                folderName = "Google Photos",
                dateTakenMs = System.currentTimeMillis(),
                fileSizeBytes = fileSize,
                knownWidth = 0,
                knownHeight = 0,
                mimeType = mimeType,
                durationMs = durationMs,
                isVideo = isVideo
            )
            if (analyzed != null) {
                newlyScanned += analyzed
            }
        }

        val combined = existingPhotos + newlyScanned
        clusterAndNominateBestShots(combined)
    }

    private fun extractVideoDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            } ?: retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Extracts a representative keyframe for a video URI.
     * Uses FileDescriptor-backed MediaMetadataRetriever first to ensure exact per-URI frame accuracy,
     * with ContentResolver.loadThumbnail as fallback.
     */
    fun extractVideoThumbnail(context: Context, uri: Uri, durationMs: Long = 0L): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            var dataSourceSet = false
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    dataSourceSet = true
                }
            }
            if (!dataSourceSet) {
                retriever.setDataSource(context, uri)
            }
            val seekTimeUs = if (durationMs > 2000L) {
                minOf(1_500_000L, (durationMs * 250L))
            } else {
                300_000L
            }
            val frame = retriever.getFrameAtTime(seekTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (frame != null) {
                return frame
            }
        } catch (_: Exception) {
            // Fall through to loadThumbnail
        } finally {
            runCatching { retriever.release() }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                return context.contentResolver.loadThumbnail(uri, Size(384, 384), null)
            }
        }
        return null
    }

    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, reqSize: Int = 256): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }
            var inSampleSize = 1
            val halfW = boundsOptions.outWidth / 2
            val halfH = boundsOptions.outHeight / 2
            while (halfW / inSampleSize >= reqSize && halfH / inSampleSize >= reqSize) {
                inSampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize.coerceAtLeast(1)
                this.inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun analyzeMediaRecord(
        context: Context,
        uri: Uri,
        displayName: String,
        folderName: String,
        dateTakenMs: Long,
        fileSizeBytes: Long,
        knownWidth: Int,
        knownHeight: Int,
        mimeType: String,
        durationMs: Long,
        isVideo: Boolean
    ): PhotoEntity? {
        val bitmap = if (isVideo) {
            extractVideoThumbnail(context, uri, durationMs)
        } else {
            decodeSampledBitmapFromUri(context, uri, 256)
        }

        val width = if (knownWidth > 0) knownWidth else (bitmap?.width ?: 1080)
        val height = if (knownHeight > 0) knownHeight else (bitmap?.height ?: 1920)

        val dHash: String
        val pHash: String
        val laplacianVariance: Float
        val sharpnessScore: Int
        val exposureScore: Int
        val framingScore: Int

        if (bitmap != null) {
            dHash = computeDifferenceHash64(bitmap)
            pHash = computePerceptualHash64(bitmap)
            val (lapVar, sharp) = computeLaplacianSharpness(bitmap)
            laplacianVariance = lapVar
            sharpnessScore = sharp
            exposureScore = computeExposureScore(bitmap)
            framingScore = computeFramingScore(bitmap)
        } else {
            // Deterministic fallback hash derived from URI + size + timestamp so unreadable files never collide!
            val uniqueSeed = "${uri}_${fileSizeBytes}_${dateTakenMs}".hashCode().toUInt().toString(16).padStart(8, '0')
            dHash = "f${uniqueSeed}e${uniqueSeed.reversed()}".take(16).padEnd(16, 'a')
            pHash = "a${uniqueSeed.reversed()}c${uniqueSeed}".take(16).padEnd(16, '5')
            laplacianVariance = 180f
            sharpnessScore = 68
            exposureScore = 76
            framingScore = 74
        }

        val overallQuality = (sharpnessScore * 0.50f + exposureScore * 0.30f + framingScore * 0.20f)
            .roundToInt()
            .coerceIn(1, 100)

        val lowerName = displayName.lowercase()
        val lowerFolder = folderName.lowercase()
        val isScreenshotLikely = !isVideo && (
            lowerName.contains("screenshot") ||
                lowerFolder.contains("screenshot") ||
                lowerName.startsWith("scr_")
            )
        val isReceiptLikely = !isVideo && (
            lowerName.contains("receipt") ||
                lowerName.contains("invoice") ||
                lowerName.contains("order")
            )
        val isDocumentLikely = !isVideo && (
            lowerName.contains("doc") ||
                lowerName.contains("scan") ||
                lowerName.contains("id_") ||
                lowerFolder.contains("document")
            )
        val isDownloadLikely = lowerFolder.contains("download") ||
            lowerFolder.contains("whatsapp") ||
            lowerFolder.contains("telegram")

        val category = when {
            isVideo -> PhotoCategory.VIDEO
            isReceiptLikely -> PhotoCategory.RECEIPT
            isDocumentLikely -> PhotoCategory.DOCUMENT
            isScreenshotLikely -> PhotoCategory.SCREENSHOT
            sharpnessScore < 40 -> PhotoCategory.BLURRY
            isDownloadLikely -> PhotoCategory.DOWNLOAD
            else -> PhotoCategory.LANDSCAPE
        }

        val screenshotSubType = when {
            category == PhotoCategory.RECEIPT -> ScreenshotSubType.RECEIPT_INVOICE
            category == PhotoCategory.SCREENSHOT && lowerName.contains("otp") -> ScreenshotSubType.TEMPORARY_OTP
            category == PhotoCategory.SCREENSHOT -> ScreenshotSubType.OLD_UI_CAPTURE
            else -> ScreenshotSubType.NONE
        }

        val ageDays = ((System.currentTimeMillis() - dateTakenMs).coerceAtLeast(0L) / (1000L * 60 * 60 * 24)).toInt()

        val (junkConfidence, junkReason) = when {
            isVideo && fileSizeBytes >= 35_000_000L ->
                0.78f to "Large video taking ${(fileSizeBytes / (1024 * 1024))} MB"
            isVideo && sharpnessScore < 38 ->
                0.76f to "Low-clarity or shaky video clip"
            category == PhotoCategory.BLURRY || sharpnessScore < 40 ->
                0.91f to "Blurry or out-of-focus capture (Sharpness $sharpnessScore/100)"
            category == PhotoCategory.SCREENSHOT && ageDays >= 60 ->
                0.86f to "Old screenshot ($ageDays days old)"
            category == PhotoCategory.SCREENSHOT ->
                0.68f to "Temporary screen capture"
            category == PhotoCategory.RECEIPT || category == PhotoCategory.DOCUMENT ->
                0.10f to "Important receipt or document"
            else ->
                0.18f to "Standard camera media"
        }

        return PhotoEntity(
            uriString = uri.toString(),
            title = displayName,
            folderName = folderName,
            dateTakenEpochMs = dateTakenMs,
            fileSizeBytes = fileSizeBytes,
            width = width,
            height = height,
            dHash = dHash,
            pHash = pHash,
            sharpnessScore = sharpnessScore,
            laplacianVariance = laplacianVariance,
            exposureScore = exposureScore,
            framingScore = framingScore,
            overallQualityScore = overallQuality,
            duplicateClusterId = null,
            isBestShotInCluster = false,
            bestShotReason = "",
            category = category.name,
            screenshotSubType = screenshotSubType.name,
            ocrText = "",
            aiDescription = if (isVideo) {
                "Video in $folderName (${width}x${height})"
            } else {
                "${category.label} in $folderName (${width}x${height})"
            },
            semanticTags = buildList {
                add(category.label.lowercase())
                add(folderName.lowercase())
                if (isVideo) add("video") else add("photo")
                if (sharpnessScore < 42) add("blurry")
                if (fileSizeBytes >= 20_000_000L) add("large file")
            }.joinToString(","),
            junkConfidence = junkConfidence,
            junkReason = junkReason,
            sentimentalProtected = category == PhotoCategory.RECEIPT || category == PhotoCategory.DOCUMENT,
            triageStatus = TriageStatus.UNREVIEWED.name,
            analyzedByProvider = "On-Device analysis",
            mediaType = if (isVideo) "VIDEO" else "IMAGE",
            durationMs = durationMs,
            mimeType = mimeType
        )
    }

    /**
     * Computes 64-bit Difference Hash (dHash) as a 16-char lowercase hex string.
     */
    fun computeDifferenceHash64(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var bits = 0UL
        var bitIndex = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val leftPixel = scaled.getPixel(x, y)
                val rightPixel = scaled.getPixel(x + 1, y)
                val leftLuma = (Color.red(leftPixel) * 299 + Color.green(leftPixel) * 587 + Color.blue(leftPixel) * 114) / 1000
                val rightLuma = (Color.red(rightPixel) * 299 + Color.green(rightPixel) * 587 + Color.blue(rightPixel) * 114) / 1000
                if (leftLuma > rightLuma) {
                    bits = bits or (1UL shl bitIndex)
                }
                bitIndex++
            }
        }
        return bits.toString(16).padStart(16, '0')
    }

    /**
     * Computes 64-bit DCT Perceptual Hash (pHash) as a 16-char lowercase hex string.
     */
    fun computePerceptualHash64(bitmap: Bitmap): String {
        val size = 16
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val luma = Array(size) { FloatArray(size) }
        for (y in 0 until size) {
            for (x in 0 until size) {
                val p = scaled.getPixel(x, y)
                luma[y][x] = (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p))
            }
        }

        val dct = FloatArray(64)
        var idx = 0
        for (u in 0 until 8) {
            for (v in 0 until 8) {
                var sum = 0f
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        sum += luma[y][x] *
                            cos(((2 * x + 1) * u * PI) / (2.0 * size)).toFloat() *
                            cos(((2 * y + 1) * v * PI) / (2.0 * size)).toFloat()
                    }
                }
                dct[idx++] = sum
            }
        }

        val acValues = dct.drop(1)
        val mean = acValues.average().toFloat()
        var bits = 0UL
        for (i in 0 until 64) {
            val value = if (i == 0) mean else dct[i]
            if (value > mean) {
                bits = bits or (1UL shl i)
            }
        }
        return bits.toString(16).padStart(16, '0')
    }

    /**
     * Computes 3x3 Laplacian variance sharpness metric and normalized 0..100 score.
     */
    fun computeLaplacianSharpness(bitmap: Bitmap): Pair<Float, Int> {
        val w = minOf(bitmap.width, 96).coerceAtLeast(8)
        val h = minOf(bitmap.height, 96).coerceAtLeast(8)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val gray = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = scaled.getPixel(x, y)
                gray[y * w + x] = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            }
        }

        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val center = gray[y * w + x]
                val lap = (-4 * center) +
                    gray[(y - 1) * w + x] +
                    gray[(y + 1) * w + x] +
                    gray[y * w + (x - 1)] +
                    gray[y * w + (x + 1)]
                val d = lap.toDouble()
                sum += d
                sumSq += d * d
                count++
            }
        }
        if (count == 0) return 100f to 60
        val mean = sum / count
        val variance = (sumSq / count - mean * mean).coerceAtLeast(0.0).toFloat()
        val score = ((variance / 850f) * 100f).roundToInt().coerceIn(8, 99)
        return variance to score
    }

    fun computeExposureScore(bitmap: Bitmap): Int {
        val w = minOf(bitmap.width, 48).coerceAtLeast(4)
        val h = minOf(bitmap.height, 48).coerceAtLeast(4)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        var totalLuma = 0L
        var clippedDark = 0
        var clippedBright = 0
        val totalPixels = w * h
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = scaled.getPixel(x, y)
                val luma = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                totalLuma += luma
                if (luma < 18) clippedDark++
                if (luma > 242) clippedBright++
            }
        }
        val avgLuma = (totalLuma / totalPixels).toInt()
        val distanceFromIdeal = abs(avgLuma - 128)
        val clipPenalty = ((clippedDark + clippedBright).toFloat() / totalPixels * 45f).roundToInt()
        return (100 - (distanceFromIdeal * 0.45f).roundToInt() - clipPenalty).coerceIn(15, 98)
    }

    fun computeFramingScore(bitmap: Bitmap): Int {
        val w = minOf(bitmap.width, 32).coerceAtLeast(4)
        val h = minOf(bitmap.height, 32).coerceAtLeast(4)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        var centerContrast = 0L
        var centerCount = 0
        for (y in (h / 4) until (3 * h / 4)) {
            for (x in (w / 4) until (3 * w / 4)) {
                val p = scaled.getPixel(x, y)
                val c = abs(Color.red(p) - Color.blue(p)) + abs(Color.green(p) - Color.red(p))
                centerContrast += c
                centerCount++
            }
        }
        val avgContrast = if (centerCount > 0) (centerContrast / centerCount).toInt() else 40
        return (58 + (avgContrast / 3)).coerceIn(35, 96)
    }

    fun hammingDistanceHex64(hashA: String, hashB: String): Int {
        val a = hashA.take(16).toULongOrNull(16) ?: return 64
        val b = hashB.take(16).toULongOrNull(16) ?: return 64
        return (a xor b).countOneBits()
    }

    private fun countBitsHex64(hash: String): Int {
        val v = hash.take(16).toULongOrNull(16) ?: return 32
        return v.countOneBits()
    }

    /**
     * Strict similarity check that prevents false-positive clustering of unrelated videos or dark frames:
     * 1. Media type must match (never group a video with a photo).
     * 2. User "Not Duplicate" exclusions are honored.
     * 3. Aspect ratio must be compatible.
     * 4. Low-entropy solid/dark frames require near-simultaneous capture & matching file sizes.
     * 5. Burst shots (<= 2 min apart) allow moderate perceptual tolerance; distant captures require near-identical hashes.
     */
    private fun areCandidatesSimilar(a: PhotoEntity, b: PhotoEntity): Boolean {
        if (a.uriString == b.uriString) return false
        if (a.isVideo != b.isVideo) return false

        // Respect manual "Not Duplicate" exclusions
        if (a.excludedClusterKeys.contains("ALL_EXCLUDED") || b.excludedClusterKeys.contains("ALL_EXCLUDED")) {
            return false
        }
        if (a.excludedClusterKeys.contains(b.uriString) || b.excludedClusterKeys.contains(a.uriString)) {
            return false
        }

        // Aspect ratio compatibility check
        if (a.width > 0 && a.height > 0 && b.width > 0 && b.height > 0) {
            val ratioA = a.width.toFloat() / a.height.toFloat()
            val ratioB = b.width.toFloat() / b.height.toFloat()
            if (abs(ratioA - ratioB) > 0.16f) return false
        }

        // Video duration compatibility check
        if (a.isVideo && b.isVideo && a.durationMs > 0L && b.durationMs > 0L) {
            val durationDiffMs = abs(a.durationMs - b.durationMs)
            val maxAllowedDiff = maxOf(2_500L, (maxOf(a.durationMs, b.durationMs) * 0.15f).toLong())
            if (durationDiffMs > maxAllowedDiff) return false
        }

        val dDist = hammingDistanceHex64(a.dHash, b.dHash)
        val pDist = hammingDistanceHex64(a.pHash, b.pHash)
        val timeDiffMs = abs(a.dateTakenEpochMs - b.dateTakenEpochMs)

        // Guard against uniform dark/blank keyframes (e.g., fade-in black video frames)
        val bitsA = countBitsHex64(a.dHash)
        val bitsB = countBitsHex64(a.dHash)
        val isLowEntropy = bitsA !in 6..58 || bitsB !in 6..58
        if (isLowEntropy) {
            val maxBytes = maxOf(a.fileSizeBytes, b.fileSizeBytes, 1L)
            val sizeRatioDiff = abs(a.fileSizeBytes - b.fileSizeBytes).toFloat() / maxBytes.toFloat()
            return timeDiffMs <= 45_000L && sizeRatioDiff <= 0.20f && dDist <= 2 && pDist <= 2
        }

        return when {
            // Burst / rapid sequence within 2 minutes
            timeDiffMs <= 120_000L -> dDist <= 8 && pDist <= 10
            // Same day in same folder
            timeDiffMs <= 86_400_000L && a.folderName == b.folderName -> dDist <= 4 && pDist <= 5
            // Different days: only group if virtually identical copy/re-save
            else -> {
                val maxBytes = maxOf(a.fileSizeBytes, b.fileSizeBytes, 1L)
                val sizeRatioDiff = abs(a.fileSizeBytes - b.fileSizeBytes).toFloat() / maxBytes.toFloat()
                dDist <= 2 && pDist <= 2 && sizeRatioDiff <= 0.25f
            }
        }
    }

    /**
     * Groups near-duplicate photos/videos into deterministic clusters and nominates the sharpest Best Shot.
     * Items that are not in any multi-item cluster have their duplicateClusterId reset to null.
     */
    fun clusterAndNominateBestShots(photos: List<PhotoEntity>): List<PhotoEntity> {
        if (photos.isEmpty()) return emptyList()

        // Sort deterministically by dateTakenEpochMs DESC, uriString ASC
        val ordered = photos.sortedWith(
            compareByDescending<PhotoEntity> { it.dateTakenEpochMs }
                .thenBy { it.uriString }
        )

        val assignedCluster = mutableMapOf<String, String>()
        val clusterMembers = mutableMapOf<String, MutableList<PhotoEntity>>()

        for (i in ordered.indices) {
            val current = ordered[i]
            if (assignedCluster.containsKey(current.uriString)) continue

            val matches = mutableListOf(current)
            for (j in (i + 1) until ordered.size) {
                val candidate = ordered[j]
                if (assignedCluster.containsKey(candidate.uriString)) continue
                if (areCandidatesSimilar(current, candidate)) {
                    matches += candidate
                }
            }

            if (matches.size > 1) {
                val anchor = matches.minByOrNull { it.uriString } ?: current
                val deterministicId = "cluster_${anchor.dHash.take(8)}_${anchor.uriString.hashCode().toUInt().toString(16)}"
                matches.forEach { member ->
                    assignedCluster[member.uriString] = deterministicId
                }
                clusterMembers[deterministicId] = matches
            }
        }

        val updatedByUri = mutableMapOf<String, PhotoEntity>()

        // Process multi-item clusters
        for ((clusterId, members) in clusterMembers) {
            val existingCustomTitle = members.firstNotNullOfOrNull {
                it.clusterTitleOverride.takeIf { title -> title.isNotBlank() }
            } ?: ""

            val ranked = members.sortedWith(
                compareByDescending<PhotoEntity> { it.overallQualityScore }
                    .thenByDescending { it.sharpnessScore }
                    .thenByDescending { it.fileSizeBytes }
            )
            val bestWinner = ranked.first()

            for (member in ranked) {
                val isBest = member.uriString == bestWinner.uriString
                val reason = if (isBest) {
                    "Best shot • Sharpness ${member.sharpnessScore}/100 & balanced exposure"
                } else {
                    "Similar to ${bestWinner.title} (Sharpness ${member.sharpnessScore} vs ${bestWinner.sharpnessScore})"
                }
                val newJunkConf = if (isBest) {
                    minOf(member.junkConfidence, 0.12f)
                } else {
                    maxOf(member.junkConfidence, 0.88f)
                }
                updatedByUri[member.uriString] = member.copy(
                    duplicateClusterId = clusterId,
                    isBestShotInCluster = isBest,
                    bestShotReason = reason,
                    clusterTitleOverride = existingCustomTitle,
                    junkConfidence = newJunkConf
                )
            }
        }

        // Return in original input order, clearing stale cluster fields on singletons
        return photos.map { original ->
            updatedByUri[original.uriString] ?: original.copy(
                duplicateClusterId = null,
                isBestShotInCluster = false,
                bestShotReason = ""
            )
        }
    }
}
