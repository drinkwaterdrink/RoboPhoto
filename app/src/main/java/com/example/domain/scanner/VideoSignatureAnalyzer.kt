package com.example.domain.scanner

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import com.example.data.local.PhotoEntity
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

data class VideoFrameSample(
    val positionFraction: Float,
    val dHash: String,
    val pHash: String,
    val sharpnessScore: Int,
    val exposureScore: Int,
    val framingScore: Int,
    val laplacianVariance: Float,
    val setBitCount: Int
) {
    val isLowEntropy: Boolean
        get() = setBitCount !in 6..58
}

data class VideoMultiFrameAnalysis(
    val representativeBitmap: Bitmap?,
    val storyboardBitmaps: List<Bitmap>,
    val primaryDHash: String,
    val primaryPHash: String,
    val sharpnessScore: Int,
    val laplacianVariance: Float,
    val exposureScore: Int,
    val framingScore: Int,
    val motionStabilityScore: Int,
    val encodedSignatureHashes: String,
    val frameSamples: List<VideoFrameSample>
)

/**
 * Multi-Frame Video Perceptual Signature & Storyboard Analyzer (Pass 2 Sections D & E):
 * - Samples 5 representative temporal positions (`10%, 30%, 50%, 70%, 90%`) of video duration,
 *   adapting intelligently for very short videos (`< 2.5s`).
 * - Crops out top status-bar / bottom navigation-bar chrome when computing perceptual hashes on
 *   portrait screen-aspect frames so screen recordings sharing a status bar do not falsely match.
 * - Selects the highest-entropy, sharpest frame as the poster frame (preventing black fade-in
 *   frames or static intro splashes from representing the video).
 * - Encodes compact 64-bit frame hashes into `videoSignatureHashes`
 *   (`"10@dHash:pHash:sharp|30@dHash:pHash:sharp|50@...|70@...|90@..."`) without storing bitmaps in Room.
 */
object VideoSignatureAnalyzer {

    val STANDARD_SAMPLE_FRACTIONS = listOf(0.10f, 0.30f, 0.50f, 0.70f, 0.90f)
    val SHORT_VIDEO_SAMPLE_FRACTIONS = listOf(0.20f, 0.50f, 0.80f)
    val MICRO_VIDEO_SAMPLE_FRACTIONS = listOf(0.30f, 0.75f)

    /**
     * Intelligently adapts frame sampling positions to the video's duration:
     * - Normal videos (>= 2,500ms or unknown 0ms): 5 frames at 10%, 30%, 50%, 70%, 90%
     * - Short videos (1,000ms .. 2,499ms): 3 frames at 20%, 50%, 80%
     * - Micro clips (1ms .. 999ms): 2 frames at 30%, 75%
     */
    fun computeSampleFractions(durationMs: Long): List<Float> {
        return when {
            durationMs == 0L || durationMs >= 2_500L -> STANDARD_SAMPLE_FRACTIONS
            durationMs >= 1_000L -> SHORT_VIDEO_SAMPLE_FRACTIONS
            else -> MICRO_VIDEO_SAMPLE_FRACTIONS
        }
    }

    fun encodeFrameSamples(samples: List<VideoFrameSample>): String {
        if (samples.isEmpty()) return ""
        return samples.joinToString("|") { s ->
            val pct = (s.positionFraction * 100f).roundToInt().coerceIn(0, 100)
            "$pct@${s.dHash.take(16)}:${s.pHash.take(16)}:${s.sharpnessScore}"
        }
    }

    fun decodeFrameSamples(encoded: String): List<VideoFrameSample> {
        if (encoded.isBlank()) return emptyList()
        val parts = encoded.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val defaultFractions = STANDARD_SAMPLE_FRACTIONS
        return parts.mapIndexedNotNull { idx, rawToken ->
            val hasPctPrefix = rawToken.contains("@")
            val pctFromToken = if (hasPctPrefix) {
                rawToken.substringBefore("@").toIntOrNull()?.let { it / 100f }
            } else {
                null
            }
            val token = if (hasPctPrefix) rawToken.substringAfter("@") else rawToken
            val fields = token.split(":")
            if (fields.size < 2) return@mapIndexedNotNull null
            val dHash = fields[0].trim()
            val pHash = fields[1].trim()
            if (dHash.length < 16 || pHash.length < 16) return@mapIndexedNotNull null
            val sharp = fields.getOrNull(2)?.toIntOrNull() ?: 68
            val bits = dHash.take(16).toULongOrNull(16)?.countOneBits() ?: 32
            val defaultFraction = defaultFractions.getOrElse(idx) {
                ((idx + 1).toFloat() / (parts.size + 1).toFloat()).coerceIn(0.10f, 0.90f)
            }
            VideoFrameSample(
                positionFraction = pctFromToken ?: defaultFraction,
                dHash = dHash,
                pHash = pHash,
                sharpnessScore = sharp,
                exposureScore = 75,
                framingScore = 75,
                laplacianVariance = sharp * 8.5f,
                setBitCount = bits
            )
        }
    }

    /**
     * Crops out the top 10% (status bar) and bottom 8% (navigation bar) of portrait frames before
     * computing perceptual hashes so screen recordings with identical status bars do not collide.
     */
    fun cropContentRegionForHashing(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 24) return bitmap
        val topInset = (h * 0.10f).roundToInt().coerceAtLeast(1)
        val bottomInset = (h * 0.08f).roundToInt().coerceAtLeast(1)
        val cropHeight = (h - topInset - bottomInset).coerceAtLeast(8)
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, topInset, w, cropHeight)
        }.getOrDefault(bitmap)
    }

    /**
     * Downscales a frame bitmap to a compact storyboard cell (max 160px on longest edge) so full-resolution
     * bitmaps are never retained in memory.
     */
    fun downscaleForStoryboard(bitmap: Bitmap, maxDimension: Int = 160): Bitmap {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val longest = maxOf(w, h)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest.toFloat()
        val targetW = (w * scale).roundToInt().coerceAtLeast(24)
        val targetH = (h * scale).roundToInt().coerceAtLeast(24)
        return runCatching {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        }.getOrDefault(bitmap)
    }

    /**
     * Extracts temporal keyframes (`10%, 30%, 50%, 70%, 90%`) from [uri], computes compact hashes,
     * and produces downscaled storyboard thumbnails.
     */
    fun analyzeVideoUri(
        context: Context,
        uri: Uri,
        durationMs: Long,
        scanner: MediaScannerEngine
    ): VideoMultiFrameAnalysis? {
        val sampleBitmaps = mutableListOf<Pair<Float, Bitmap>>()
        val retriever = MediaMetadataRetriever()
        try {
            var dsSet = false
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    dsSet = true
                }
            }
            if (!dsSet) {
                retriever.setDataSource(context, uri)
            }

            val effectiveDurationMs = if (durationMs > 0L) {
                durationMs
            } else {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            }

            val fractions = computeSampleFractions(effectiveDurationMs)

            for (fraction in fractions) {
                val seekUs = if (effectiveDurationMs > 0L) {
                    (effectiveDurationMs * fraction * 1000f).toLong().coerceAtLeast(50_000L)
                } else {
                    (fraction * 2_000_000f).toLong()
                }
                val bmp = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            seekUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            256,
                            256
                        ) ?: retriever.getFrameAtTime(seekUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } else {
                        retriever.getFrameAtTime(seekUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                }.getOrNull()
                if (bmp != null) {
                    sampleBitmaps += fraction to downscaleForStoryboard(bmp, 220)
                }
            }

            if (sampleBitmaps.isEmpty()) {
                val fallback = runCatching {
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                }.getOrNull()
                if (fallback != null) {
                    sampleBitmaps += 0.50f to downscaleForStoryboard(fallback, 220)
                }
            }
        } catch (_: Exception) {
            // Fall through to loadThumbnail fallback
        } finally {
            runCatching { retriever.release() }
        }

        if (sampleBitmaps.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val thumb = context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
                sampleBitmaps += 0.50f to downscaleForStoryboard(thumb, 220)
            }
        }

        if (sampleBitmaps.isEmpty()) return null

        val analyzedSamples = mutableListOf<Pair<VideoFrameSample, Bitmap>>()
        for ((fraction, bmp) in sampleBitmaps) {
            val contentBmp = cropContentRegionForHashing(bmp)
            val dHash = scanner.computeDifferenceHash64(contentBmp)
            val pHash = scanner.computePerceptualHash64(contentBmp)
            val (lapVar, sharp) = scanner.computeLaplacianSharpness(contentBmp)
            val exp = scanner.computeExposureScore(contentBmp)
            val fram = scanner.computeFramingScore(contentBmp)
            val bits = scanner.countBitsHex64(dHash)
            val sample = VideoFrameSample(
                positionFraction = fraction,
                dHash = dHash,
                pHash = pHash,
                sharpnessScore = sharp,
                exposureScore = exp,
                framingScore = fram,
                laplacianVariance = lapVar,
                setBitCount = bits
            )
            analyzedSamples += sample to bmp
        }

        // Choose the sharpest non-low-entropy frame (preferring mid-video frames on ties) as representative poster
        val bestPair = analyzedSamples
            .sortedWith(
                compareByDescending<Pair<VideoFrameSample, Bitmap>> { if (!it.first.isLowEntropy) 1 else 0 }
                    .thenByDescending { it.first.sharpnessScore }
                    .thenByDescending { it.first.exposureScore }
                    .thenBy { abs(it.first.positionFraction - 0.50f) }
            )
            .first()

        val allSamples = analyzedSamples.map { it.first }
        val storyboardThumbs = analyzedSamples.map { downscaleForStoryboard(it.second, 144) }
        val avgSharp = allSamples.map { it.sharpnessScore }.average().roundToInt().coerceIn(8, 99)
        val avgExp = allSamples.map { it.exposureScore }.average().roundToInt().coerceIn(15, 98)
        val avgFram = allSamples.map { it.framingScore }.average().roundToInt().coerceIn(35, 96)
        val maxSharp = allSamples.maxOf { it.sharpnessScore }
        val minSharp = allSamples.minOf { it.sharpnessScore }
        val sharpSpreadPenalty = ((maxSharp - minSharp) * 0.35f).roundToInt()
        val motionStability = (avgSharp + 12 - sharpSpreadPenalty).coerceIn(20, 98)

        return VideoMultiFrameAnalysis(
            representativeBitmap = bestPair.second,
            storyboardBitmaps = storyboardThumbs,
            primaryDHash = bestPair.first.dHash,
            primaryPHash = bestPair.first.pHash,
            sharpnessScore = bestPair.first.sharpnessScore,
            laplacianVariance = bestPair.first.laplacianVariance,
            exposureScore = avgExp,
            framingScore = avgFram,
            motionStabilityScore = motionStability,
            encodedSignatureHashes = encodeFrameSamples(allSamples),
            frameSamples = allSamples
        )
    }

    /**
     * Strict multi-frame video similarity verification (Pass 2 Section D):
     *
     * Considers:
     * - Multi-frame perceptual similarity across 10%, 30%, 50%, 70%, 90%
     * - Duration compatibility
     * - Aspect ratio & dimensions compatibility
     * - File size compatibility
     * - Time proximity
     *
     * Explicitly guards against false-positive grouping when:
     * 1. First frame is black (low-entropy start frame while body frames differ)
     * 2. First frame is a static UI (shared splash/launcher screen at 10% while body frames differ)
     * 3. Screen recordings share the same status bar / static UI chrome
     * 4. Videos begin with identical camera transitions (matching 10% & 30% frames while 50%, 70%, 90% diverge)
     */
    fun verifyMultiFrameVideoSimilarity(
        encodedA: String,
        encodedB: String,
        maxDHashDist: Int,
        maxPHashDist: Int,
        bothHaveReliableCloseTimestamps: Boolean,
        sizeRatioDiff: Float,
        scanner: MediaScannerEngine,
        photoA: PhotoEntity? = null,
        photoB: PhotoEntity? = null
    ): Boolean {
        // Check metadata-level dimension & duration guards when PhotoEntity instances are provided
        if (photoA != null && photoB != null) {
            if (!photoA.isVideo || !photoB.isVideo) return false
            if (photoA.width > 0 && photoA.height > 0 && photoB.width > 0 && photoB.height > 0) {
                val ratioA = photoA.width.toFloat() / photoA.height.toFloat()
                val ratioB = photoB.width.toFloat() / photoB.height.toFloat()
                if (abs(ratioA - ratioB) > 0.12f) return false
            }
            if (photoA.durationMs > 0L && photoB.durationMs > 0L) {
                val durDiff = abs(photoA.durationMs - photoB.durationMs)
                val maxDurDiff = maxOf(1_800L, (maxOf(photoA.durationMs, photoB.durationMs) * 0.12f).toLong())
                if (durDiff > maxDurDiff) return false
            }
        }

        val samplesA = decodeFrameSamples(encodedA)
        val samplesB = decodeFrameSamples(encodedB)

        val isLikelyScreenRecording = (photoA != null && isScreenRecordingCandidate(photoA)) ||
            (photoB != null && isScreenRecordingCandidate(photoB))

        if (samplesA.size < 2 || samplesB.size < 2) {
            // Single-frame fallback: reject if screen recording without tight size/time match
            if (isLikelyScreenRecording && (!bothHaveReliableCloseTimestamps || sizeRatioDiff > 0.12f)) {
                return false
            }
            return true
        }

        val pairCount = minOf(samplesA.size, samplesB.size)

        // Detect static UI / status-bar-dominated recordings where internal frames barely change
        val internalDriftA = computeInternalFrameDrift(samplesA, scanner)
        val internalDriftB = computeInternalFrameDrift(samplesB, scanner)
        val isStaticUiOrScreenCapture = isLikelyScreenRecording || internalDriftA <= 3.0f || internalDriftB <= 3.0f

        val effectiveMaxDDist = if (isStaticUiOrScreenCapture) minOf(maxDHashDist, 3) else maxDHashDist
        val effectiveMaxPDist = if (isStaticUiOrScreenCapture) minOf(maxPHashDist, 3) else maxPHashDist

        if (isStaticUiOrScreenCapture && sizeRatioDiff > 0.22f) {
            return false
        }

        var totalMatchedFrames = 0
        var highEntropyMatches = 0
        var bodyMatchedFrames = 0
        var bodyHighEntropyMatches = 0

        // Indices >= bodyStartIndex represent the core/later body of the video (e.g. 50%, 70%, 90%).
        // Matching only the intro frames (10%, 30%) from a black frame, static UI, or camera transition is NEVER enough.
        val bodyStartIndex = when {
            pairCount >= 5 -> 2 // frames at 50%, 70%, 90%
            pairCount >= 3 -> 1 // frames at 50%, 80%
            else -> 1           // second frame
        }
        val bodyFrameCount = (pairCount - bodyStartIndex).coerceAtLeast(1)

        for (i in 0 until pairCount) {
            val fa = samplesA[i]
            val fb = samplesB[i]
            val dDist = scanner.hammingDistanceHex64(fa.dHash, fb.dHash)
            val pDist = scanner.hammingDistanceHex64(fa.pHash, fb.pHash)
            val eitherLowEntropy = fa.isLowEntropy || fb.isLowEntropy
            val isBodyFrame = i >= bodyStartIndex

            if (eitherLowEntropy) {
                // Low-entropy (black/blank) frames only count if BOTH videos have close capture timestamps,
                // near-identical file sizes, AND near-zero hash distance
                if (bothHaveReliableCloseTimestamps && sizeRatioDiff <= 0.15f && dDist <= 1 && pDist <= 1) {
                    totalMatchedFrames++
                    if (isBodyFrame) bodyMatchedFrames++
                }
            } else if (dDist <= effectiveMaxDDist && pDist <= effectiveMaxPDist) {
                totalMatchedFrames++
                highEntropyMatches++
                if (isBodyFrame) {
                    bodyMatchedFrames++
                    bodyHighEntropyMatches++
                }
            }
        }

        // Guard against screen recordings that only share a status bar / static UI header:
        // If any single body frame diverges sharply (dDist > 12 or pDist > 12), they are different recordings!
        for (i in bodyStartIndex until pairCount) {
            val fa = samplesA[i]
            val fb = samplesB[i]
            val dDist = scanner.hammingDistanceHex64(fa.dHash, fb.dHash)
            val pDist = scanner.hammingDistanceHex64(fa.pHash, fb.pHash)
            if (isStaticUiOrScreenCapture && (dDist > 6 || pDist > 6)) {
                return false
            }
            if (dDist > 16 || pDist > 16) {
                // A major scene divergence in the body of the video means these are distinct clips
                // (e.g. same intro/camera transition, different main content)
                if (pairCount >= 4 && bodyHighEntropyMatches < bodyFrameCount) {
                    return false
                }
            }
        }

        val requiredTotalMatches = when {
            isStaticUiOrScreenCapture -> ceil(pairCount * 0.80).toInt().coerceAtLeast(pairCount - 1)
            pairCount >= 5 -> 3
            pairCount >= 3 -> 2
            else -> pairCount
        }

        val requiredBodyHighEntropyMatches = when {
            bodyFrameCount >= 3 -> 2 // At least 2 of (50%, 70%, 90%) must be high-entropy matches
            else -> 1
        }

        val hasEnoughTotal = totalMatchedFrames >= requiredTotalMatches
        val hasEnoughBody = bodyHighEntropyMatches >= requiredBodyHighEntropyMatches ||
            (bodyMatchedFrames == bodyFrameCount && bothHaveReliableCloseTimestamps && sizeRatioDiff <= 0.10f)

        return hasEnoughTotal && hasEnoughBody &&
            (highEntropyMatches >= 2 || (highEntropyMatches >= 1 && pairCount == 2) ||
                (bothHaveReliableCloseTimestamps && sizeRatioDiff <= 0.10f && totalMatchedFrames == pairCount))
    }

    private fun isScreenRecordingCandidate(photo: PhotoEntity): Boolean {
        val blob = "${photo.title} ${photo.folderName}".lowercase()
        return blob.contains("screen") ||
            blob.contains("record") ||
            blob.contains("scr_") ||
            blob.contains("srecorder") ||
            blob.contains("az_recorder")
    }

    private fun computeInternalFrameDrift(
        samples: List<VideoFrameSample>,
        scanner: MediaScannerEngine
    ): Float {
        if (samples.size < 2) return 10f
        var totalDist = 0
        var comparisons = 0
        for (i in 0 until samples.size - 1) {
            totalDist += scanner.hammingDistanceHex64(samples[i].dHash, samples[i + 1].dHash)
            comparisons++
        }
        return if (comparisons > 0) totalDist.toFloat() / comparisons.toFloat() else 10f
    }
}
