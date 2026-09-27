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
import android.util.Size
import androidx.core.content.ContextCompat
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.ScreenshotSubType
import com.example.data.local.TriageStatus
import com.example.data.security.EncryptedMediaCache
import com.example.ui.components.MediaThumbnailIdentityCache
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LibraryScanSummary(
    val photosAfterScanAndCluster: List<PhotoEntity>,
    val newItemsAdded: List<PhotoEntity>,
    val changedItemsReanalyzed: List<PhotoEntity>,
    val unchangedItemsReused: List<PhotoEntity>,
    val deletedSourceItemsDetected: List<PhotoEntity>
)

/**
 * On-device media forensics engine:
 * - Queries Android MediaStore for both Photos and Videos with canonical content:// URIs
 * - Tracks media revisions (SIZE, DATE_MODIFIED, GENERATION_MODIFIED) to detect in-place edits
 * - Extracts genuine capture timestamps (MediaStore, EXIF, MediaMetadataRetriever) without inventing fake timestamps
 * - Computes 64-bit Difference Hash (dHash) and 64-bit DCT Perceptual Hash (pHash)
 * - Performs strict, media-type-safe, entropy-aware duplicate & burst clustering
 */
class MediaScannerEngine(
    @Suppress("unused")
    private val encryptedCache: EncryptedMediaCache? = null
) {
    @Volatile
    var lastCandidateIndexStats: CandidateIndexStats? = null
        private set

    @Volatile
    var lastTypedClusters: List<MediaCluster> = emptyList()
        private set

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
     * Discovers all accessible Images and Videos from MediaStore without an arbitrary 300-item cap.
     */
    fun discoverAllMediaStoreMetadata(
        context: Context,
        maxItemsToScan: Int = Int.MAX_VALUE,
        onDiscoveredProgress: ((Int) -> Unit)? = null
    ): List<SourceMediaMetadata> {
        if (!hasMediaStorePermission(context)) return emptyList()

        val candidateRecords = mutableListOf<SourceMediaMetadata>()
        val nowImportMs = System.currentTimeMillis()

        // 1. Query Images
        runCatching {
            val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val baseProjection = mutableListOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.MIME_TYPE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                baseProjection += MediaStore.Images.Media.GENERATION_MODIFIED
            }
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                imageCollection,
                baseProjection.toTypedArray(),
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dateModCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val genCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.GENERATION_MODIFIED)
                } else -1

                var count = 0
                while (cursor.moveToNext() && count < maxItemsToScan) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(imageCollection, id)
                    val name = cursor.getString(nameCol) ?: "IMG_$id.jpg"
                    val bucket = cursor.getString(bucketCol) ?: "Camera"
                    val takenMs = if (dateTakenCol >= 0 && !cursor.isNull(dateTakenCol)) cursor.getLong(dateTakenCol) else 0L
                    val modSec = if (dateModCol >= 0 && !cursor.isNull(dateModCol)) cursor.getLong(dateModCol) else 0L
                    val capturedAt = takenMs.takeIf { it > 100_000_000_000L }
                    val modifiedMs = if (modSec in 1L..99_999_999_999L) modSec * 1000L else modSec.coerceAtLeast(0L)
                    val size = cursor.getLong(sizeCol).coerceAtLeast(1024L)
                    val w = cursor.getInt(widthCol).coerceAtLeast(0)
                    val h = cursor.getInt(heightCol).coerceAtLeast(0)
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    val genMod = if (genCol >= 0 && !cursor.isNull(genCol)) cursor.getLong(genCol).coerceAtLeast(0L) else 0L

                    candidateRecords += SourceMediaMetadata(
                        uri = contentUri,
                        displayName = name,
                        folderName = bucket,
                        fileSizeBytes = size,
                        width = w,
                        height = h,
                        mimeType = mime,
                        isVideo = false,
                        durationMs = 0L,
                        capturedAtEpochMs = capturedAt,
                        dateModifiedEpochMs = modifiedMs,
                        importedAtEpochMs = nowImportMs,
                        sourceGenerationModified = genMod,
                        isUriPermissionPersisted = true
                    )
                    count++
                    if (candidateRecords.size % 100 == 0) {
                        onDiscoveredProgress?.invoke(candidateRecords.size)
                    }
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
            val baseProjection = mutableListOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DURATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                baseProjection += MediaStore.Video.Media.GENERATION_MODIFIED
            }
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                videoCollection,
                baseProjection.toTypedArray(),
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
                val dateModCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val genCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.GENERATION_MODIFIED)
                } else -1

                var count = 0
                while (cursor.moveToNext() && count < maxItemsToScan) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(videoCollection, id)
                    val name = cursor.getString(nameCol) ?: "VID_$id.mp4"
                    val bucket = cursor.getString(bucketCol) ?: "Videos"
                    val takenMs = if (dateTakenCol >= 0 && !cursor.isNull(dateTakenCol)) cursor.getLong(dateTakenCol) else 0L
                    val modSec = if (dateModCol >= 0 && !cursor.isNull(dateModCol)) cursor.getLong(dateModCol) else 0L
                    val capturedAt = takenMs.takeIf { it > 100_000_000_000L }
                    val modifiedMs = if (modSec in 1L..99_999_999_999L) modSec * 1000L else modSec.coerceAtLeast(0L)
                    val size = cursor.getLong(sizeCol).coerceAtLeast(1024L)
                    val w = cursor.getInt(widthCol).coerceAtLeast(0)
                    val h = cursor.getInt(heightCol).coerceAtLeast(0)
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val duration = cursor.getLong(durationCol).coerceAtLeast(0L)
                    val genMod = if (genCol >= 0 && !cursor.isNull(genCol)) cursor.getLong(genCol).coerceAtLeast(0L) else 0L

                    candidateRecords += SourceMediaMetadata(
                        uri = contentUri,
                        displayName = name,
                        folderName = bucket,
                        fileSizeBytes = size,
                        width = w,
                        height = h,
                        mimeType = mime,
                        isVideo = true,
                        durationMs = duration,
                        capturedAtEpochMs = capturedAt,
                        dateModifiedEpochMs = modifiedMs,
                        importedAtEpochMs = nowImportMs,
                        sourceGenerationModified = genMod,
                        isUriPermissionPersisted = true
                    )
                    count++
                    if (candidateRecords.size % 100 == 0) {
                        onDiscoveredProgress?.invoke(candidateRecords.size)
                    }
                }
            }
        }

        val sorted = candidateRecords.sortedByDescending { it.canonicalDateTakenEpochMs }
        val result = if (maxItemsToScan == Int.MAX_VALUE) sorted else sorted.take(maxItemsToScan)
        onDiscoveredProgress?.invoke(result.size)
        return result
    }

    /**
     * Scans the device's MediaStore for both Images and Videos with full revision tracking:
     * - NEW_ITEM -> full pixel analysis
     * - UNCHANGED_ITEM -> reuse existing analysis without decoding bitmap
     * - CHANGED_ITEM -> invalidate thumbnail cache, re-analyze, preserve DB id & triage status
     */
    suspend fun scanDeviceMediaStore(
        context: Context,
        existingPhotos: List<PhotoEntity>,
        maxItemsToScan: Int = Int.MAX_VALUE
    ): List<PhotoEntity> = withContext(Dispatchers.IO) {
        scanDeviceMediaStoreDetailed(context, existingPhotos, maxItemsToScan).photosAfterScanAndCluster
    }

    suspend fun scanDeviceMediaStoreDetailed(
        context: Context,
        existingPhotos: List<PhotoEntity>,
        maxItemsToScan: Int = Int.MAX_VALUE
    ): LibraryScanSummary = withContext(Dispatchers.IO) {
        if (!hasMediaStorePermission(context)) {
            return@withContext LibraryScanSummary(
                photosAfterScanAndCluster = existingPhotos,
                newItemsAdded = emptyList(),
                changedItemsReanalyzed = emptyList(),
                unchangedItemsReused = existingPhotos,
                deletedSourceItemsDetected = emptyList()
            )
        }

        val existingByUri = existingPhotos.associateBy { it.uriString }
        val newlyScanned = mutableListOf<PhotoEntity>()
        val changedUpdated = mutableListOf<PhotoEntity>()
        val unchangedReused = mutableListOf<PhotoEntity>()
        val seenExistingUris = mutableSetOf<String>()

        val sortedRecords = discoverAllMediaStoreMetadata(context, maxItemsToScan)

        for (metadata in sortedRecords) {
            val uriStr = metadata.uri.toString()
            val existing = existingByUri[uriStr]
            if (existing != null) {
                seenExistingUris += uriStr
            }
            when (
                MediaMetadataReader.classifyRevision(
                    existing = existing,
                    currentSizeBytes = metadata.fileSizeBytes,
                    currentDateModifiedMs = metadata.dateModifiedEpochMs,
                    currentGenerationModified = metadata.sourceGenerationModified,
                    currentWidth = metadata.width,
                    currentHeight = metadata.height
                )
            ) {
                MediaScanDecision.NEW_ITEM -> {
                    val analyzed = analyzeMediaFromMetadata(context, metadata, existingToUpdate = null)
                    if (analyzed != null) {
                        newlyScanned += analyzed
                    }
                }
                MediaScanDecision.UNCHANGED_ITEM -> {
                    if (existing != null) {
                        unchangedReused += existing
                    }
                }
                MediaScanDecision.CHANGED_ITEM -> {
                    if (existing != null) {
                        MediaThumbnailIdentityCache.evict(existing.stableIdentityKey)
                        val reanalyzed = analyzeMediaFromMetadata(context, metadata, existingToUpdate = existing)
                        if (reanalyzed != null) {
                            changedUpdated += reanalyzed
                        } else {
                            unchangedReused += existing
                        }
                    }
                }
                MediaScanDecision.DELETED_SOURCE_ITEM -> Unit
            }
        }

        // Preserve existing items that weren't in the top cursor slice unless their source URI is confirmed deleted
        val deletedFromSource = mutableListOf<PhotoEntity>()
        val gateway = AndroidSourceMediaDeletionGateway(context)
        for (existing in existingPhotos) {
            if (existing.uriString in seenExistingUris) continue
            val uri = runCatching { Uri.parse(existing.uriString) }.getOrNull()
            val isLocalFile = uri?.scheme == "file"
            if (isLocalFile && uri != null && !gateway.isSourceUriStillAccessible(uri)) {
                deletedFromSource += existing
                MediaThumbnailIdentityCache.evict(existing.stableIdentityKey)
            } else {
                unchangedReused += existing
            }
        }

        val combinedActive = unchangedReused + changedUpdated + newlyScanned
        val clustered = clusterAndNominateBestShots(combinedActive)

        LibraryScanSummary(
            photosAfterScanAndCluster = clustered,
            newItemsAdded = newlyScanned,
            changedItemsReanalyzed = changedUpdated,
            unchangedItemsReused = unchangedReused,
            deletedSourceItemsDetected = deletedFromSource
        )
    }

    /**
     * Scans user-picked URIs from the Android Photo Picker / Google Photos cloud provider.
     * Extracts genuine capture dates where available without inventing System.currentTimeMillis()
     * when capture date is unknown, and supports revision detection if a picked URI is re-imported after editing.
     */
    suspend fun scanImportedUris(
        context: Context,
        uris: List<Uri>,
        existingPhotos: List<PhotoEntity>
    ): List<PhotoEntity> = withContext(Dispatchers.IO) {
        val existingByUri = existingPhotos.associateBy { it.uriString }
        val newlyScanned = mutableListOf<PhotoEntity>()
        val changedUpdated = mutableMapOf<String, PhotoEntity>()
        val nowImportMs = System.currentTimeMillis()

        for (uri in uris) {
            val uriStr = uri.toString()
            val metadata = MediaMetadataReader.readImportedUriMetadata(context, uri, nowImportMs)
            val existing = existingByUri[uriStr]

            when (
                MediaMetadataReader.classifyRevision(
                    existing = existing,
                    currentSizeBytes = metadata.fileSizeBytes,
                    currentDateModifiedMs = metadata.dateModifiedEpochMs,
                    currentGenerationModified = metadata.sourceGenerationModified,
                    currentWidth = metadata.width,
                    currentHeight = metadata.height
                )
            ) {
                MediaScanDecision.NEW_ITEM -> {
                    val analyzed = analyzeMediaFromMetadata(context, metadata, existingToUpdate = null)
                    if (analyzed != null) {
                        newlyScanned += analyzed
                    }
                }
                MediaScanDecision.CHANGED_ITEM -> {
                    if (existing != null) {
                        MediaThumbnailIdentityCache.evict(existing.stableIdentityKey)
                        val reanalyzed = analyzeMediaFromMetadata(context, metadata, existingToUpdate = existing)
                        if (reanalyzed != null) {
                            changedUpdated[uriStr] = reanalyzed
                        }
                    }
                }
                MediaScanDecision.UNCHANGED_ITEM,
                MediaScanDecision.DELETED_SOURCE_ITEM -> Unit
            }
        }

        val mergedExisting = existingPhotos.map { changedUpdated[it.uriString] ?: it }
        val combined = mergedExisting + newlyScanned
        clusterAndNominateBestShots(combined)
    }

    /**
     * Evaluates whether [existing] changed based on [currentMetadata], and if so, invalidates its
     * thumbnail cache entry and recomputes hashes/sharpness from [updatedBitmap] while preserving
     * database identity (`id`) and user triage state.
     */
    fun evaluateAndReanalyzeIfChanged(
        existing: PhotoEntity,
        currentMetadata: SourceMediaMetadata,
        updatedBitmap: Bitmap?
    ): Pair<MediaScanDecision, PhotoEntity> {
        val decision = MediaMetadataReader.classifyRevision(
            existing = existing,
            currentSizeBytes = currentMetadata.fileSizeBytes,
            currentDateModifiedMs = currentMetadata.dateModifiedEpochMs,
            currentGenerationModified = currentMetadata.sourceGenerationModified,
            currentWidth = currentMetadata.width,
            currentHeight = currentMetadata.height
        )
        if (decision != MediaScanDecision.CHANGED_ITEM) {
            return decision to existing
        }

        MediaThumbnailIdentityCache.evict(existing.stableIdentityKey)
        val reanalyzed = buildAnalyzedPhotoEntity(
            metadata = currentMetadata,
            bitmap = updatedBitmap,
            existingToUpdate = existing
        )
        return MediaScanDecision.CHANGED_ITEM to reanalyzed
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

    internal fun analyzeMediaFromMetadataInternal(
        context: Context,
        metadata: SourceMediaMetadata,
        existingToUpdate: PhotoEntity?
    ): PhotoEntity? = analyzeMediaFromMetadata(context, metadata, existingToUpdate)

    private fun analyzeMediaFromMetadata(
        context: Context,
        metadata: SourceMediaMetadata,
        existingToUpdate: PhotoEntity?
    ): PhotoEntity? {
        return if (metadata.isVideo) {
            val videoAnalysis = VideoSignatureAnalyzer.analyzeVideoUri(
                context = context,
                uri = metadata.uri,
                durationMs = metadata.durationMs,
                scanner = this
            )
            buildAnalyzedPhotoEntity(
                metadata = metadata,
                bitmap = videoAnalysis?.representativeBitmap,
                existingToUpdate = existingToUpdate,
                videoMultiFrameAnalysis = videoAnalysis
            )
        } else {
            val bitmap = decodeSampledBitmapFromUri(context, metadata.uri, 256)
            buildAnalyzedPhotoEntity(
                metadata = metadata,
                bitmap = bitmap,
                existingToUpdate = existingToUpdate,
                videoMultiFrameAnalysis = null
            )
        }
    }

    fun buildAnalyzedPhotoEntity(
        metadata: SourceMediaMetadata,
        bitmap: Bitmap?,
        existingToUpdate: PhotoEntity? = null,
        videoMultiFrameAnalysis: VideoMultiFrameAnalysis? = null
    ): PhotoEntity {
        val width = if (metadata.width > 0) metadata.width else (bitmap?.width ?: existingToUpdate?.width ?: 1080)
        val height = if (metadata.height > 0) metadata.height else (bitmap?.height ?: existingToUpdate?.height ?: 1920)

        val dHash: String
        val pHash: String
        val laplacianVariance: Float
        val sharpnessScore: Int
        val exposureScore: Int
        val framingScore: Int
        val motionStabilityScore: Int
        val videoSignatureHashes: String

        if (videoMultiFrameAnalysis != null) {
            dHash = videoMultiFrameAnalysis.primaryDHash
            pHash = videoMultiFrameAnalysis.primaryPHash
            laplacianVariance = videoMultiFrameAnalysis.laplacianVariance
            sharpnessScore = videoMultiFrameAnalysis.sharpnessScore
            exposureScore = videoMultiFrameAnalysis.exposureScore
            framingScore = videoMultiFrameAnalysis.framingScore
            motionStabilityScore = videoMultiFrameAnalysis.motionStabilityScore
            videoSignatureHashes = videoMultiFrameAnalysis.encodedSignatureHashes
        } else if (bitmap != null) {
            dHash = computeDifferenceHash64(bitmap)
            pHash = computePerceptualHash64(bitmap)
            val (lapVar, sharp) = computeLaplacianSharpness(bitmap)
            laplacianVariance = lapVar
            sharpnessScore = sharp
            exposureScore = computeExposureScore(bitmap)
            framingScore = computeFramingScore(bitmap)
            motionStabilityScore = sharp
            videoSignatureHashes = if (metadata.isVideo) "$dHash:$pHash:$sharp" else ""
        } else {
            // Deterministic fallback hash derived from URI + size + revision so unreadable files never collide
            val uniqueSeed = "${metadata.uri}_${metadata.fileSizeBytes}_${metadata.dateModifiedEpochMs}_${metadata.sourceGenerationModified}"
                .hashCode().toUInt().toString(16).padStart(8, '0')
            dHash = "f${uniqueSeed}e${uniqueSeed.reversed()}".take(16).padEnd(16, 'a')
            pHash = "a${uniqueSeed.reversed()}c${uniqueSeed}".take(16).padEnd(16, '5')
            laplacianVariance = 180f
            sharpnessScore = 68
            exposureScore = 76
            framingScore = 74
            motionStabilityScore = 72
            videoSignatureHashes = if (metadata.isVideo) "$dHash:$pHash:68" else ""
        }

        val overallQuality = if (metadata.isVideo) {
            (sharpnessScore * 0.45f + motionStabilityScore * 0.25f + exposureScore * 0.20f + framingScore * 0.10f)
                .roundToInt()
                .coerceIn(1, 100)
        } else {
            (sharpnessScore * 0.50f + exposureScore * 0.30f + framingScore * 0.20f)
                .roundToInt()
                .coerceIn(1, 100)
        }

        val lowerName = metadata.displayName.lowercase()
        val lowerFolder = metadata.folderName.lowercase()
        val isScreenshotLikely = !metadata.isVideo && (
            lowerName.contains("screenshot") ||
                lowerFolder.contains("screenshot") ||
                lowerName.startsWith("scr_")
            )
        val isReceiptLikely = !metadata.isVideo && (
            lowerName.contains("receipt") ||
                lowerName.contains("invoice") ||
                lowerName.contains("order")
            )
        val isDocumentLikely = !metadata.isVideo && (
            lowerName.contains("doc") ||
                lowerName.contains("scan") ||
                lowerName.contains("id_") ||
                lowerFolder.contains("document")
            )
        val isDownloadLikely = lowerFolder.contains("download") ||
            lowerFolder.contains("whatsapp") ||
            lowerFolder.contains("telegram")

        val category = when {
            metadata.isVideo -> PhotoCategory.VIDEO
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

        val effectiveCaptureTs = metadata.canonicalDateTakenEpochMs
        val ageDays = if (effectiveCaptureTs > 0L) {
            ((System.currentTimeMillis() - effectiveCaptureTs).coerceAtLeast(0L) / (1000L * 60 * 60 * 24)).toInt()
        } else {
            0
        }

        val (junkConfidence, junkReason) = when {
            metadata.isVideo && metadata.fileSizeBytes >= 35_000_000L ->
                0.78f to "Large video taking ${(metadata.fileSizeBytes / (1024 * 1024))} MB"
            metadata.isVideo && sharpnessScore < 38 ->
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

        val lshBands = CandidateIndexEngine.extractLshBands(dHash)
        val aspectBucket = CandidateIndexEngine.computeAspectRatioBucket(width, height)
        val tempBucket = if (metadata.capturedAtEpochMs != null && metadata.capturedAtEpochMs > 0L) {
            metadata.capturedAtEpochMs / 120_000L
        } else if (effectiveCaptureTs > 0L) {
            effectiveCaptureTs / 120_000L
        } else {
            -1L
        }

        val built = PhotoEntity(
            id = existingToUpdate?.id ?: 0L,
            uriString = metadata.uri.toString(),
            title = metadata.displayName,
            folderName = metadata.folderName,
            dateTakenEpochMs = effectiveCaptureTs,
            fileSizeBytes = metadata.fileSizeBytes,
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
            clusterTitleOverride = existingToUpdate?.clusterTitleOverride ?: "",
            excludedClusterKeys = existingToUpdate?.excludedClusterKeys ?: "",
            category = category.name,
            screenshotSubType = screenshotSubType.name,
            ocrText = existingToUpdate?.ocrText ?: "",
            aiDescription = if (metadata.isVideo) {
                "Video in ${metadata.folderName} (${width}x${height})"
            } else {
                "${category.label} in ${metadata.folderName} (${width}x${height})"
            },
            semanticTags = buildList {
                add(category.label.lowercase())
                add(metadata.folderName.lowercase())
                if (metadata.isVideo) add("video") else add("photo")
                if (sharpnessScore < 42) add("blurry")
                if (metadata.fileSizeBytes >= 20_000_000L) add("large file")
            }.joinToString(","),
            junkConfidence = junkConfidence,
            junkReason = junkReason,
            sentimentalProtected = existingToUpdate?.sentimentalProtected
                ?: (category == PhotoCategory.RECEIPT || category == PhotoCategory.DOCUMENT),
            triageStatus = existingToUpdate?.triageStatus ?: TriageStatus.UNREVIEWED.name,
            vaultedAtEpochMs = existingToUpdate?.vaultedAtEpochMs,
            analyzedByProvider = "On-Device analysis",
            mediaType = if (metadata.isVideo) "VIDEO" else "IMAGE",
            durationMs = metadata.durationMs,
            mimeType = metadata.mimeType,
            capturedAtEpochMs = metadata.capturedAtEpochMs,
            importedAtEpochMs = existingToUpdate?.importedAtEpochMs ?: metadata.importedAtEpochMs,
            dateModifiedEpochMs = metadata.dateModifiedEpochMs,
            sourceGenerationModified = metadata.sourceGenerationModified,
            isUriPermissionPersisted = metadata.isUriPermissionPersisted,
            videoSignatureHashes = videoSignatureHashes,
            aspectRatioBucket = aspectBucket,
            temporalBucket = tempBucket,
            lshBand0 = lshBands.band0,
            lshBand1 = lshBands.band1,
            lshBand2 = lshBands.band2,
            lshBand3 = lshBands.band3,
            userSelectedBestShot = existingToUpdate?.userSelectedBestShot ?: false,
            motionStabilityScore = motionStabilityScore,
            analysisStageVersion = 2
        )

        if (videoMultiFrameAnalysis?.representativeBitmap != null) {
            MediaThumbnailIdentityCache.put(built.stableIdentityKey, videoMultiFrameAnalysis.representativeBitmap)
        }
        return built
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

    fun countBitsHex64(hash: String): Int {
        val v = hash.take(16).toULongOrNull(16) ?: return 32
        return v.countOneBits()
    }

    /**
     * Strict similarity check that prevents false-positive clustering of unrelated videos or dark frames:
     * 1. Media type must match (never group a video with a photo).
     * 2. User "Not Duplicate" exclusions are honored.
     * 3. Aspect ratio must be compatible.
     * 4. Low-entropy solid/dark frames (where EITHER A or B has < 6 or > 58 set bits) require
     *    BOTH items to have reliable capture timestamps within 45 seconds AND matching file sizes.
     * 5. Items without a reliable capture date are NEVER treated as a 2-minute burst.
     */
    fun areCandidatesSimilar(a: PhotoEntity, b: PhotoEntity): Boolean {
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

        // Guard against uniform dark/blank keyframes (check BOTH candidate A and candidate B!)
        val bitsA = countBitsHex64(a.dHash)
        val bitsB = countBitsHex64(b.dHash)
        val isLowEntropy = bitsA !in 6..58 || bitsB !in 6..58

        val bothHaveReliableCaptureTime = a.hasReliableCaptureDate && b.hasReliableCaptureDate
        val tsA = a.effectiveCaptureOrModifiedEpochMs ?: 0L
        val tsB = b.effectiveCaptureOrModifiedEpochMs ?: 0L
        val timeDiffMs = if (bothHaveReliableCaptureTime && tsA > 0L && tsB > 0L) {
            abs(tsA - tsB)
        } else {
            Long.MAX_VALUE
        }

        val maxBytes = maxOf(a.fileSizeBytes, b.fileSizeBytes, 1L)
        val sizeRatioDiff = abs(a.fileSizeBytes - b.fileSizeBytes).toFloat() / maxBytes.toFloat()

        if (isLowEntropy) {
            return bothHaveReliableCaptureTime &&
                timeDiffMs <= 45_000L &&
                sizeRatioDiff <= 0.20f &&
                dDist <= 2 &&
                pDist <= 2
        }

        if (!bothHaveReliableCaptureTime) {
            // Without reliable capture timestamps (e.g. imported files lacking EXIF), only cluster near-exact copies
            val baseMatch = dDist <= 2 && pDist <= 2 && sizeRatioDiff <= 0.20f
            if (!baseMatch) return false
            if (a.isVideo && b.isVideo) {
                return VideoSignatureAnalyzer.verifyMultiFrameVideoSimilarity(
                    encodedA = a.videoSignatureHashes,
                    encodedB = b.videoSignatureHashes,
                    maxDHashDist = 2,
                    maxPHashDist = 2,
                    bothHaveReliableCloseTimestamps = false,
                    sizeRatioDiff = sizeRatioDiff,
                    scanner = this,
                    photoA = a,
                    photoB = b
                )
            }
            return true
        }

        val (allowedDDist, allowedPDist, baseMatched) = when {
            // Burst / rapid sequence within 2 minutes
            timeDiffMs <= 120_000L -> Triple(8, 10, dDist <= 8 && pDist <= 10)
            // Same day in same folder
            timeDiffMs <= 86_400_000L && a.folderName == b.folderName -> Triple(4, 5, dDist <= 4 && pDist <= 5)
            // Different days: only group if virtually identical copy/re-save
            else -> Triple(2, 2, dDist <= 2 && pDist <= 2 && sizeRatioDiff <= 0.25f)
        }
        if (!baseMatched) return false

        if (a.isVideo && b.isVideo) {
            return VideoSignatureAnalyzer.verifyMultiFrameVideoSimilarity(
                encodedA = a.videoSignatureHashes,
                encodedB = b.videoSignatureHashes,
                maxDHashDist = allowedDDist,
                maxPHashDist = allowedPDist,
                bothHaveReliableCloseTimestamps = timeDiffMs <= 120_000L,
                sizeRatioDiff = sizeRatioDiff,
                scanner = this,
                photoA = a,
                photoB = b
            )
        }

        return true
    }

    /**
     * Groups near-duplicate photos/videos into deterministic clusters using [CandidateIndexEngine]
     * (LSH bands + BK-Tree + bounded temporal windows instead of O(N^2) all-pairs), classifies each
     * cluster into an explicit [com.example.data.local.MediaClusterType], and nominates the Best Shot
     * while preserving manual user overrides (`userSelectedBestShot`).
     */
    fun clusterAndNominateBestShots(
        photos: List<PhotoEntity>,
        explicitExcludedPairKeys: Set<String> = emptySet()
    ): List<PhotoEntity> {
        if (photos.isEmpty()) {
            lastTypedClusters = emptyList()
            return emptyList()
        }

        val (clusterMembers, stats) = CandidateIndexEngine.buildClustersStaged(
            photos = photos,
            explicitExcludedPairKeys = explicitExcludedPairKeys,
            similarityVerifier = { a, b -> areCandidatesSimilar(a, b) }
        )
        lastCandidateIndexStats = stats

        val updatedByUri = mutableMapOf<String, PhotoEntity>()
        val builtClusters = mutableListOf<MediaCluster>()

        for ((clusterId, members) in clusterMembers) {
            val existingCustomTitle = members.firstNotNullOfOrNull {
                it.clusterTitleOverride.takeIf { title -> title.isNotBlank() }
            } ?: ""

            // Honor user's manual Best Shot selection if present; otherwise rank by composite quality
            val ranked = members.sortedWith(
                compareByDescending<PhotoEntity> { if (it.userSelectedBestShot) 1 else 0 }
                    .thenByDescending { it.overallQualityScore }
                    .thenByDescending { it.sharpnessScore }
                    .thenByDescending { if (it.isVideo) it.motionStabilityScore else it.exposureScore }
                    .thenByDescending { it.width.toLong() * it.height.toLong() }
                    .thenByDescending { it.fileSizeBytes }
            )
            val bestWinner = ranked.first()
            val classified = CandidateIndexEngine.classifyCluster(clusterId, ranked, bestWinner)

            val updatedMembers = ranked.map { member ->
                val isBest = member.uriString == bestWinner.uriString
                val reason = when {
                    isBest && member.userSelectedBestShot ->
                        "Selected by you as Best Shot"
                    isBest && member.isVideo ->
                        "Best video • Sharpness ${member.sharpnessScore}/100 & stability ${member.motionStabilityScore}/100"
                    isBest ->
                        "Best shot • Sharpness ${member.sharpnessScore}/100 & balanced exposure"
                    else ->
                        "${classified.type.label} • ${classified.reason} (Sharpness ${member.sharpnessScore} vs ${bestWinner.sharpnessScore})"
                }
                val newJunkConf = if (isBest) {
                    minOf(member.junkConfidence, 0.12f)
                } else {
                    maxOf(member.junkConfidence, classified.confidence)
                }
                val updatedMember = member.copy(
                    duplicateClusterId = clusterId,
                    isBestShotInCluster = isBest,
                    bestShotReason = reason,
                    clusterTitleOverride = existingCustomTitle,
                    junkConfidence = newJunkConf,
                    clusterType = classified.type.name,
                    clusterConfidence = classified.confidence,
                    clusterReason = classified.reason,
                    clusterGenerationVersion = classified.clusterGenerationVersion
                )
                updatedByUri[member.uriString] = updatedMember
                updatedMember
            }

            builtClusters += classified.copy(
                bestShotCandidate = updatedMembers.firstOrNull(),
                members = updatedMembers
            )
        }

        lastTypedClusters = builtClusters

        return photos.map { original ->
            updatedByUri[original.uriString] ?: original.copy(
                duplicateClusterId = null,
                isBestShotInCluster = false,
                bestShotReason = "",
                clusterType = "",
                clusterConfidence = 0f,
                clusterReason = ""
            )
        }
    }
}
