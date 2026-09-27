package com.example.domain.scanner

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.ScreenshotSubType
import com.example.data.local.TriageStatus
import com.example.data.security.EncryptedMediaCache
import java.io.File
import java.lang.Long.bitCount
import java.lang.Long.parseUnsignedLong
import java.lang.Long.toUnsignedString
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ForensicsMetrics(
    val dHash: String,
    val pHash: String,
    val laplacianVariance: Float,
    val sharpnessScore: Int,
    val exposureScore: Int,
    val framingScore: Int,
    val overallQualityScore: Int,
    val width: Int,
    val height: Int
)

class MediaScannerEngine(
    private val encryptedCache: EncryptedMediaCache
) {

    /**
     * Computes 64-bit Difference Hash (dHash) by scaling to 9x8 grayscale and comparing horizontal neighbors.
     */
    fun computeDHash(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var bits = 0L
        var bitIndex = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val leftLum = luminance(scaled.getPixel(x, y))
                val rightLum = luminance(scaled.getPixel(x + 1, y))
                if (leftLum > rightLum) {
                    bits = bits or (1L shl bitIndex)
                }
                bitIndex++
            }
        }
        return toUnsignedString(bits, 16).padStart(16, '0')
    }

    /**
     * Computes 64-bit Perceptual Average Hash (pHash) by scaling to 8x8 grayscale and comparing to mean luminance.
     */
    fun computePHash(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val lums = IntArray(64)
        var sum = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val l = luminance(scaled.getPixel(x, y))
                lums[y * 8 + x] = l
                sum += l
            }
        }
        val avg = sum / 64
        var bits = 0L
        for (i in 0 until 64) {
            if (lums[i] >= avg) {
                bits = bits or (1L shl i)
            }
        }
        return toUnsignedString(bits, 16).padStart(16, '0')
    }

    /**
     * Computes Hamming distance (0..64) between two 64-bit hex perceptual hashes.
     */
    fun computeHammingDistance(hashA: String, hashB: String): Int {
        return try {
            val a = parseUnsignedLong(hashA.take(16), 16)
            val b = parseUnsignedLong(hashB.take(16), 16)
            bitCount(a xor b)
        } catch (_: Exception) {
            64
        }
    }

    /**
     * Computes 3x3 Laplacian variance for blur/focus detection and maps to a 0..100 sharpness score.
     */
    fun computeLaplacianSharpness(bitmap: Bitmap): Pair<Float, Int> {
        val w = 96
        val h = 96
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val gray = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                gray[y * w + x] = luminance(scaled.getPixel(x, y))
            }
        }

        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val center = gray[y * w + x]
                val lap = (
                    gray[(y - 1) * w + x] +
                        gray[(y + 1) * w + x] +
                        gray[y * w + (x - 1)] +
                        gray[y * w + (x + 1)] -
                        (4 * center)
                    ).toDouble()
                sum += lap
                sumSq += lap * lap
                count++
            }
        }
        if (count == 0) return 0f to 50
        val mean = sum / count
        val variance = (sumSq / count - (mean * mean)).toFloat().coerceAtLeast(0f)
        val normalized = (sqrt(variance.toDouble()) * 2.35).roundToInt().coerceIn(8, 99)
        return variance to normalized
    }

    /**
     * Computes Exposure Balance (0..100) and Center-Weighted Framing Score (0..100).
     */
    fun computeExposureAndFraming(bitmap: Bitmap): Pair<Int, Int> {
        val w = 64
        val h = 64
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        var totalLum = 0L
        var clippedDark = 0
        var clippedBright = 0
        var centerEdgeSum = 0L
        var outerEdgeSum = 0L

        for (y in 0 until h) {
            for (x in 0 until w) {
                val l = luminance(scaled.getPixel(x, y))
                totalLum += l
                if (l < 18) clippedDark++
                if (l > 242) clippedBright++

                if (x < w - 1 && y < h - 1) {
                    val gx = abs(l - luminance(scaled.getPixel(x + 1, y)))
                    val gy = abs(l - luminance(scaled.getPixel(x, y + 1)))
                    val grad = gx + gy
                    val inCenter = x in (w / 4)..(3 * w / 4) && y in (h / 4)..(3 * h / 4)
                    if (inCenter) centerEdgeSum += grad else outerEdgeSum += grad
                }
            }
        }

        val pixelCount = w * h
        val meanLum = (totalLum / pixelCount).toInt()
        val lumDeviationPenalty = (abs(meanLum - 128) * 0.48f).roundToInt()
        val clipRatioPenalty = (((clippedDark + clippedBright).toFloat() / pixelCount) * 65f).roundToInt()
        val exposureScore = (98 - lumDeviationPenalty - clipRatioPenalty).coerceIn(18, 98)

        val totalEdge = (centerEdgeSum + outerEdgeSum).coerceAtLeast(1L)
        val centerRatio = centerEdgeSum.toFloat() / totalEdge.toFloat()
        val framingScore = (52 + (centerRatio * 75f).roundToInt()).coerceIn(35, 97)

        return exposureScore to framingScore
    }

    suspend fun analyzeBitmapForensics(cacheKey: String, bitmap: Bitmap): ForensicsMetrics {
        val cachedJson = encryptedCache.get("forensics_$cacheKey")
        if (cachedJson != null) {
            runCatching {
                val obj = JSONObject(cachedJson)
                return ForensicsMetrics(
                    dHash = obj.getString("dHash"),
                    pHash = obj.getString("pHash"),
                    laplacianVariance = obj.getDouble("laplacianVariance").toFloat(),
                    sharpnessScore = obj.getInt("sharpnessScore"),
                    exposureScore = obj.getInt("exposureScore"),
                    framingScore = obj.getInt("framingScore"),
                    overallQualityScore = obj.getInt("overallQualityScore"),
                    width = obj.getInt("width"),
                    height = obj.getInt("height")
                )
            }
        }

        val dHash = computeDHash(bitmap)
        val pHash = computePHash(bitmap)
        val (lapVar, sharpness) = computeLaplacianSharpness(bitmap)
        val (exposure, framing) = computeExposureAndFraming(bitmap)
        val overall = ((sharpness * 0.50f) + (exposure * 0.30f) + (framing * 0.20f)).roundToInt().coerceIn(5, 99)

        val metrics = ForensicsMetrics(
            dHash = dHash,
            pHash = pHash,
            laplacianVariance = lapVar,
            sharpnessScore = sharpness,
            exposureScore = exposure,
            framingScore = framing,
            overallQualityScore = overall,
            width = bitmap.width,
            height = bitmap.height
        )

        val json = JSONObject().apply {
            put("dHash", metrics.dHash)
            put("pHash", metrics.pHash)
            put("laplacianVariance", metrics.laplacianVariance.toDouble())
            put("sharpnessScore", metrics.sharpnessScore)
            put("exposureScore", metrics.exposureScore)
            put("framingScore", metrics.framingScore)
            put("overallQualityScore", metrics.overallQualityScore)
            put("width", metrics.width)
            put("height", metrics.height)
        }
        encryptedCache.put("forensics_$cacheKey", json.toString())
        return metrics
    }

    /**
     * Groups near-duplicate photos and videos by perceptual hash Hamming distance (<= 12 bits)
     * and nominates the single sharpest / best-exposed item in each cluster as the Best-Shot.
     */
    fun clusterAndNominateBestShots(photos: List<PhotoEntity>): List<PhotoEntity> {
        if (photos.isEmpty()) return emptyList()

        val parent = IntArray(photos.size) { it }
        fun find(i: Int): Int {
            var root = i
            while (root != parent[root]) root = parent[root]
            var curr = i
            while (curr != root) {
                val nxt = parent[curr]
                parent[curr] = root
                curr = nxt
            }
            return root
        }
        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) parent[rootJ] = rootI
        }

        for (i in photos.indices) {
            for (j in i + 1 until photos.size) {
                val a = photos[i]
                val b = photos[j]
                val sameExplicitCluster = !a.duplicateClusterId.isNullOrBlank() &&
                    a.duplicateClusterId == b.duplicateClusterId
                val dDist = computeHammingDistance(a.dHash, b.dHash)
                val pDist = computeHammingDistance(a.pHash, b.pHash)
                val perceptuallySimilar = (dDist <= 12 || pDist <= 10) &&
                    a.mediaType == b.mediaType &&
                    abs(a.dateTakenEpochMs - b.dateTakenEpochMs) <= 1000L * 60 * 60 * 48

                if (sameExplicitCluster || perceptuallySimilar) {
                    union(i, j)
                }
            }
        }

        val groups = photos.indices.groupBy { find(it) }
        val updated = photos.toMutableList()

        for ((rootIdx, memberIndices) in groups) {
            if (memberIndices.size <= 1) {
                val idx = memberIndices.first()
                val current = updated[idx]
                if (current.duplicateClusterId != null || current.isBestShotInCluster) {
                    updated[idx] = current.copy(duplicateClusterId = null, isBestShotInCluster = false)
                }
                continue
            }

            val existingClusterId = memberIndices.mapNotNull { updated[it].duplicateClusterId }.firstOrNull()
                ?: "cluster_${updated[rootIdx].dHash.take(6)}"

            val bestIdx = memberIndices.maxByOrNull { idx ->
                val p = updated[idx]
                (p.sharpnessScore * 55) + (p.exposureScore * 30) + (p.framingScore * 15) + ((p.width * p.height) / 500_000)
            } ?: memberIndices.first()

            val bestPhoto = updated[bestIdx]

            for (idx in memberIndices) {
                val p = updated[idx]
                val isBest = (idx == bestIdx)
                val newJunkConfidence = if (isBest) {
                    min(p.junkConfidence, 0.12f)
                } else {
                    maxOf(p.junkConfidence, 0.86f)
                }
                val newJunkReason = if (isBest) {
                    "Best-Shot Winner in cluster (${p.sharpnessScore}/100 Sharpness, ${p.exposureScore}/100 Exposure)"
                } else {
                    "Near-duplicate variant (Sharpness ${p.sharpnessScore} vs Best Shot ${bestPhoto.sharpnessScore})"
                }
                updated[idx] = p.copy(
                    duplicateClusterId = existingClusterId,
                    isBestShotInCluster = isBest,
                    junkConfidence = newJunkConfidence,
                    junkReason = newJunkReason,
                    sentimentalProtected = if (isBest) true else p.sentimentalProtected
                )
            }
        }
        return updated
    }

    /**
     * Cleans up any legacy sample files from disk so only real phone & Google Photos media exist.
     */
    fun cleanupLegacySampleDir(context: Context) {
        runCatching {
            val sampleDir = File(context.filesDir, "lumina_sample_media")
            if (sampleDir.exists()) {
                sampleDir.deleteRecursively()
            }
        }
    }

    /**
     * Full Phone Scanner: Queries Android MediaStore for both Photos (Images) and Videos on the device,
     * computes perceptual hashes (dHash/pHash), Laplacian sharpness, and clusters near-duplicates.
     */
    suspend fun scanDeviceMediaStore(
        context: Context,
        existingPhotos: List<PhotoEntity>,
        maxItemsPerPass: Int = 250
    ): List<PhotoEntity> = withContext(Dispatchers.IO) {
        val existingByUri = existingPhotos.associateBy { it.uriString }
        val newlyDiscovered = mutableListOf<PhotoEntity>()
        val resolver = context.contentResolver

        // 1. Scan Device Photos (MediaStore.Images)
        runCatching {
            val imageProjection = arrayOf(
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
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)

                var count = 0
                while (cursor.moveToNext() && count < maxItemsPerPass) {
                    val mediaId = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    val uriStr = contentUri.toString()
                    if (existingByUri.containsKey(uriStr)) {
                        count++
                        continue
                    }

                    val title = (if (nameCol >= 0) cursor.getString(nameCol) else null)
                        ?: "IMG_$mediaId.jpg"
                    val folder = (if (bucketCol >= 0) cursor.getString(bucketCol) else null)
                        ?: "Camera"
                    val dateTakenMs = (if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L).let {
                        if (it > 0L) it else ((if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L) * 1000L)
                    }.let { if (it > 0L) it else System.currentTimeMillis() }
                    val fileSize = (if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L).coerceAtLeast(10_240L)
                    val rawW = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val rawH = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    val mime = (if (mimeCol >= 0) cursor.getString(mimeCol) else null) ?: "image/jpeg"

                    val thumbBmp = loadMediaThumbnailBitmap(context, contentUri, isVideo = false)
                    if (thumbBmp != null) {
                        val metrics = analyzeBitmapForensics("ms_img_${mediaId}_$fileSize", thumbBmp)
                        val finalW = if (rawW > 0) rawW else metrics.width
                        val finalH = if (rawH > 0) rawH else metrics.height

                        newlyDiscovered += buildPhotoEntityFromMetrics(
                            uriString = uriStr,
                            title = title,
                            folderName = folder,
                            dateTakenEpochMs = dateTakenMs,
                            fileSizeBytes = fileSize,
                            width = finalW,
                            height = finalH,
                            metrics = metrics,
                            isVideo = false,
                            durationMs = 0L,
                            mimeType = mime
                        )
                        count++
                    }
                }
            }
        }

        // 2. Scan Device Videos (MediaStore.Video)
        runCatching {
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.MIME_TYPE
            )
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)

                var videoCount = 0
                while (cursor.moveToNext() && videoCount < (maxItemsPerPass / 2)) {
                    val mediaId = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    val uriStr = contentUri.toString()
                    if (existingByUri.containsKey(uriStr)) {
                        videoCount++
                        continue
                    }

                    val title = (if (nameCol >= 0) cursor.getString(nameCol) else null)
                        ?: "VID_$mediaId.mp4"
                    val folder = (if (bucketCol >= 0) cursor.getString(bucketCol) else null)
                        ?: "Videos"
                    val dateTakenMs = (if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L).let {
                        if (it > 0L) it else ((if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L) * 1000L)
                    }.let { if (it > 0L) it else System.currentTimeMillis() }
                    val fileSize = (if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L).coerceAtLeast(100_000L)
                    val rawW = if (widthCol >= 0) cursor.getInt(widthCol) else 1920
                    val rawH = if (heightCol >= 0) cursor.getInt(heightCol) else 1080
                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val mime = (if (mimeCol >= 0) cursor.getString(mimeCol) else null) ?: "video/mp4"

                    val thumbBmp = loadMediaThumbnailBitmap(context, contentUri, isVideo = true)
                    if (thumbBmp != null) {
                        val metrics = analyzeBitmapForensics("ms_vid_${mediaId}_$fileSize", thumbBmp)
                        newlyDiscovered += buildPhotoEntityFromMetrics(
                            uriString = uriStr,
                            title = title,
                            folderName = folder,
                            dateTakenEpochMs = dateTakenMs,
                            fileSizeBytes = fileSize,
                            width = if (rawW > 0) rawW else metrics.width,
                            height = if (rawH > 0) rawH else metrics.height,
                            metrics = metrics,
                            isVideo = true,
                            durationMs = durationMs,
                            mimeType = mime
                        )
                        videoCount++
                    }
                }
            }
        }

        clusterAndNominateBestShots(existingPhotos + newlyDiscovered)
    }

    /**
     * Imports user-selected photos & videos via the Android Photo & Video Picker (including cloud Google Photos).
     */
    suspend fun scanImportedUris(
        context: Context,
        uris: List<Uri>,
        existingPhotos: List<PhotoEntity>
    ): List<PhotoEntity> = withContext(Dispatchers.IO) {
        val existingByUri = existingPhotos.associateBy { it.uriString }
        val newlyScanned = mutableListOf<PhotoEntity>()
        val contentResolver = context.contentResolver

        for ((index, uri) in uris.withIndex()) {
            runCatching {
                val uriStr = uri.toString()
                if (existingByUri.containsKey(uriStr)) return@runCatching

                // Persist read permission for picked URIs when supported
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val isVideo = mimeType.startsWith("video/", ignoreCase = true)

                var displayName = if (isVideo) {
                    "Video_${System.currentTimeMillis()}_$index.mp4"
                } else {
                    "Photo_${System.currentTimeMillis()}_$index.jpg"
                }
                var byteSize = if (isVideo) 14_500_000L else 3_400_000L

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                        if (sizeIdx >= 0) byteSize = cursor.getLong(sizeIdx).coerceAtLeast(50_000L)
                    }
                }

                var durationMs = 0L
                if (isVideo) {
                    runCatching {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, uri)
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: 0L
                        retriever.release()
                    }
                }

                val bitmap = loadMediaThumbnailBitmap(context, uri, isVideo = isVideo) ?: return@runCatching
                val metrics = analyzeBitmapForensics("${uri}_$byteSize", bitmap)

                newlyScanned += buildPhotoEntityFromMetrics(
                    uriString = uriStr,
                    title = displayName,
                    folderName = if (isVideo) "Google Photos / Videos" else "Google Photos / Library",
                    dateTakenEpochMs = System.currentTimeMillis() - (index * 60_000L),
                    fileSizeBytes = byteSize,
                    width = metrics.width,
                    height = metrics.height,
                    metrics = metrics,
                    isVideo = isVideo,
                    durationMs = durationMs,
                    mimeType = mimeType
                )
            }
        }
        clusterAndNominateBestShots(existingPhotos + newlyScanned)
    }

    /**
     * Loads a memory-safe thumbnail bitmap for any photo or video URI.
     */
    fun loadMediaThumbnailBitmap(context: Context, uri: Uri, isVideo: Boolean, targetPx: Int = 320): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                return resolver.loadThumbnail(uri, Size(targetPx, targetPx), null)
            }
        }
        if (isVideo) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                retriever.release()
                if (frame != null) return frame
            }
        }
        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
            var sampleSize = 1
            while ((boundsOpts.outWidth / sampleSize) > targetPx * 2 || (boundsOpts.outHeight / sampleSize) > targetPx * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        }.getOrNull()
    }

    private fun buildPhotoEntityFromMetrics(
        uriString: String,
        title: String,
        folderName: String,
        dateTakenEpochMs: Long,
        fileSizeBytes: Long,
        width: Int,
        height: Int,
        metrics: ForensicsMetrics,
        isVideo: Boolean,
        durationMs: Long,
        mimeType: String
    ): PhotoEntity {
        val lowerName = title.lowercase()
        val lowerFolder = folderName.lowercase()
        val isScreenshot = !isVideo && (
            lowerName.contains("screen") ||
                lowerFolder.contains("screenshot") ||
                (width in 720..1600 && height > width * 1.75)
            )
        val isDownload = lowerFolder.contains("download")
        val isBlurry = metrics.sharpnessScore < 40
        val isLargeVideo = isVideo && fileSizeBytes >= 15_000_000L

        val category = when {
            isVideo -> PhotoCategory.VIDEO
            isBlurry -> PhotoCategory.BLURRY
            lowerName.contains("receipt") || lowerName.contains("invoice") -> PhotoCategory.RECEIPT
            lowerName.contains("doc") || lowerName.contains("scan") -> PhotoCategory.DOCUMENT
            isScreenshot -> PhotoCategory.SCREENSHOT
            isDownload -> PhotoCategory.DOWNLOAD
            else -> PhotoCategory.LANDSCAPE
        }

        val junkConf = when {
            isBlurry -> 0.91f
            isScreenshot -> 0.74f
            isLargeVideo -> 0.69f
            metrics.overallQualityScore < 54 -> 0.66f
            else -> 0.16f
        }

        val junkReason = when {
            isVideo && isBlurry -> "Shaky / low-sharpness video (${metrics.sharpnessScore}/100 sharpness)"
            isLargeVideo -> "High-storage video clip • Check if still needed"
            isVideo -> "Video clip (${metrics.sharpnessScore}/100 frame sharpness, ${metrics.exposureScore}/100 exposure)"
            isBlurry -> "Laplacian blur detector flagged low sharpness (${metrics.sharpnessScore}/100)"
            isScreenshot -> "Screenshot in $folderName • Ready for OCR & cleanup rules"
            isDownload -> "Downloaded image in $folderName"
            else -> "Sharp media (${metrics.sharpnessScore}/100 Sharpness, ${metrics.exposureScore}/100 Exposure)"
        }

        val mediaLabel = if (isVideo) "video" else "photo"
        val tags = buildList {
            add(mediaLabel)
            add(category.label.lowercase())
            add(folderName.lowercase())
            if (isVideo) add("videos")
            if (isScreenshot) add("screenshot")
            if (isBlurry) add("blurry")
        }.joinToString(",")

        return PhotoEntity(
            uriString = uriString,
            title = title,
            folderName = folderName,
            dateTakenEpochMs = dateTakenEpochMs,
            fileSizeBytes = fileSizeBytes,
            width = width,
            height = height,
            dHash = metrics.dHash,
            pHash = metrics.pHash,
            sharpnessScore = metrics.sharpnessScore,
            laplacianVariance = metrics.laplacianVariance,
            exposureScore = metrics.exposureScore,
            framingScore = metrics.framingScore,
            overallQualityScore = metrics.overallQualityScore,
            category = category.name,
            screenshotSubType = if (isScreenshot) ScreenshotSubType.OLD_UI_CAPTURE.name else ScreenshotSubType.NONE.name,
            ocrText = if (isScreenshot) "Screenshot: $title ($folderName)" else "",
            aiDescription = "Scanned $mediaLabel ($title) in $folderName • dHash ${metrics.dHash.take(8)}",
            semanticTags = tags,
            junkConfidence = junkConf,
            junkReason = junkReason,
            triageStatus = TriageStatus.UNREVIEWED.name,
            mediaType = if (isVideo) "VIDEO" else "IMAGE",
            durationMs = durationMs,
            mimeType = mimeType
        )
    }

    private fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return ((r * 77) + (g * 150) + (b * 29)) shr 8
    }
}
