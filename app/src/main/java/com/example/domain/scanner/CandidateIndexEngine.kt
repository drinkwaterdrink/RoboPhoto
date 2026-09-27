package com.example.domain.scanner

import com.example.data.local.MediaClusterType
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * First-class typed cluster representation separating Exact Duplicates, Near Duplicates,
 * Burst Sequences, Similar Photos, Similar Videos, and Screenshot Variants (Pass 2 Section C).
 */
data class MediaCluster(
    val clusterId: String,
    val type: MediaClusterType,
    val confidence: Float,
    val reason: String,
    val bestShotCandidate: PhotoEntity?,
    val recoverableBytes: Long,
    val members: List<PhotoEntity>,
    val clusterGenerationVersion: Int = CLUSTER_GENERATION_VERSION
) {
    companion object {
        const val CLUSTER_GENERATION_VERSION = 2
    }
}

/**
 * 4x16-bit Locality-Sensitive Hashing (LSH) bands extracted from a 64-bit hex hash.
 * By the pigeonhole principle, any two 64-bit hashes with Hamming distance <= 3 are guaranteed
 * to share at least one identical 16-bit band.
 */
data class LshBands64(
    val band0: Int,
    val band1: Int,
    val band2: Int,
    val band3: Int
)

/**
 * Metric BK-Tree (Burkhard-Keller Tree) over 64-bit perceptual hashes using Hamming distance.
 * Enables sub-linear neighborhood queries `queryWithinDistance(targetBits, maxDistance)`
 * instead of O(N^2) all-pairs comparisons.
 */
class HammingBkTree {
    private class Node(
        val hashBits: ULong,
        val items: MutableList<PhotoEntity> = mutableListOf(),
        val children: MutableMap<Int, Node> = mutableMapOf()
    )

    private var root: Node? = null

    fun insert(hashBits: ULong, photo: PhotoEntity) {
        val currentRoot = root
        if (currentRoot == null) {
            root = Node(hashBits, mutableListOf(photo))
            return
        }
        var curr: Node = currentRoot
        while (true) {
            val dist = (curr.hashBits xor hashBits).countOneBits()
            if (dist == 0) {
                curr.items.add(photo)
                return
            }
            val child = curr.children[dist]
            if (child == null) {
                curr.children[dist] = Node(hashBits, mutableListOf(photo))
                return
            }
            curr = child
        }
    }

    fun queryWithinDistance(
        targetBits: ULong,
        maxDistance: Int,
        outResults: MutableSet<String>,
        byUri: Map<String, PhotoEntity>
    ) {
        val start = root ?: return
        val stack = ArrayDeque<Node>()
        stack.addLast(start)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val dist = (node.hashBits xor targetBits).countOneBits()
            if (dist <= maxDistance) {
                for (item in node.items) {
                    if (byUri.containsKey(item.uriString)) {
                        outResults.add(item.uriString)
                    }
                }
            }
            val minChildDist = (dist - maxDistance).coerceAtLeast(1)
            val maxChildDist = (dist + maxDistance).coerceAtMost(64)
            for ((edgeDist, childNode) in node.children) {
                if (edgeDist in minChildDist..maxChildDist) {
                    stack.addLast(childNode)
                }
            }
        }
    }
}

/**
 * Telemetry describing how candidate generation performed compared to naive O(N^2) all-pairs.
 */
data class CandidateIndexStats(
    val totalItems: Int,
    val theoreticalAllPairsCount: Long,
    val candidatePairsEvaluated: Long,
    val verifiedSimilarPairs: Int,
    val clustersFormed: Int
) {
    val reductionPercent: Float
        get() = if (theoreticalAllPairsCount > 0L) {
            ((1.0 - (candidatePairsEvaluated.toDouble() / theoreticalAllPairsCount.toDouble())) * 100.0).toFloat()
        } else {
            100f
        }
}

/**
 * Staged Candidate Generation & Sub-Quadratic Clustering Engine (Pass 2 Section B).
 *
 * Replaces global O(N^2) pairwise loops with a 6-stage filter pipeline:
 * 1. Media type partition (IMAGE vs VIDEO never cross-compare)
 * 2. Aspect ratio bucket compatibility
 * 3. Temporal neighborhood window (bursts within 2 minutes & same-folder 24h window)
 * 4. Resolution / duration / file-size compatibility
 * 5. Perceptual hash neighborhood via 4x16-bit LSH bands + metric BK-Tree (Hamming distance)
 * 6. Strict final verification (including multi-frame video signature agreement & pair exclusions)
 */
object CandidateIndexEngine {

    fun parseHex64Bits(hashHex: String): ULong? {
        if (hashHex.length < 16) return null
        return hashHex.take(16).toULongOrNull(16)
    }

    fun extractLshBands(hashHex: String): LshBands64 {
        val bits = parseHex64Bits(hashHex) ?: 0UL
        val b0 = ((bits shr 48) and 0xFFFFUL).toInt()
        val b1 = ((bits shr 32) and 0xFFFFUL).toInt()
        val b2 = ((bits shr 16) and 0xFFFFUL).toInt()
        val b3 = (bits and 0xFFFFUL).toInt()
        return LshBands64(b0, b1, b2, b3)
    }

    /**
     * Quantizes aspect ratio into integer buckets of 0.05 width (e.g., 16:9 -> 36, 4:3 -> 27, 9:16 -> 11).
     * Candidate pairs must live in `[bucket - 3, bucket + 3]` (i.e. within ~0.16 ratio difference).
     */
    fun computeAspectRatioBucket(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 0
        val ratio = width.toFloat() / height.toFloat()
        return (ratio * 20f).roundToInt().coerceIn(1, 200)
    }

    /**
     * Computes a 2-minute temporal bucket for items with a genuine capture timestamp, or -1L if unknown.
     */
    fun computeTemporalBucket(photo: PhotoEntity): Long {
        if (!photo.hasReliableCaptureDate) return -1L
        val ts = photo.effectiveCaptureOrModifiedEpochMs ?: return -1L
        if (ts <= 0L) return -1L
        return ts / 120_000L
    }

    fun canonicalPairKey(uriA: String, uriB: String): String {
        return if (uriA <= uriB) "$uriA|$uriB" else "$uriB|$uriA"
    }

    /**
     * Generates candidate pairs for [photos] using LSH bands, BK-Tree neighborhood queries,
     * and bounded temporal sliding windows, then verifies candidates with [similarityVerifier].
     */
    fun buildClustersStaged(
        photos: List<PhotoEntity>,
        explicitExcludedPairKeys: Set<String> = emptySet(),
        similarityVerifier: (PhotoEntity, PhotoEntity) -> Boolean
    ): Pair<Map<String, List<PhotoEntity>>, CandidateIndexStats> {
        val n = photos.size
        val theoreticalAllPairs = if (n > 1) (n.toLong() * (n - 1L)) / 2L else 0L
        if (n <= 1) {
            return emptyMap<String, List<PhotoEntity>>() to CandidateIndexStats(
                totalItems = n,
                theoreticalAllPairsCount = 0L,
                candidatePairsEvaluated = 0L,
                verifiedSimilarPairs = 0,
                clustersFormed = 0
            )
        }

        // Deterministically order items by effective capture timestamp DESC, uriString ASC
        val ordered = photos.sortedWith(
            compareByDescending<PhotoEntity> { it.effectiveCaptureOrModifiedEpochMs ?: 0L }
                .thenBy { it.uriString }
        )
        val byUri = ordered.associateBy { it.uriString }

        // Build fast lookup for pairwise exclusions (from both PhotoEntity.excludedClusterKeys and explicit table)
        val excludedPairs = HashSet<String>(explicitExcludedPairKeys)
        val globallyExcludedUris = HashSet<String>()
        for (item in ordered) {
            if (item.excludedClusterKeys.isNotBlank()) {
                val tokens = item.excludedClusterKeys.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                for (token in tokens) {
                    if (token == "ALL_EXCLUDED") {
                        globallyExcludedUris.add(item.uriString)
                    } else {
                        excludedPairs.add(canonicalPairKey(item.uriString, token))
                    }
                }
            }
        }

        val eligible = ordered.filterNot { it.uriString in globallyExcludedUris }
        val (videos, images) = eligible.partition { it.isVideo }

        val candidateEdges = HashMap<String, MutableSet<String>>()
        var evaluatedPairsCount = 0L
        var verifiedPairsCount = 0
        val seenCandidatePairs = HashSet<String>()

        fun considerPair(a: PhotoEntity, b: PhotoEntity) {
            if (a.uriString == b.uriString) return
            val pairKey = canonicalPairKey(a.uriString, b.uriString)
            if (!seenCandidatePairs.add(pairKey)) return
            if (pairKey in excludedPairs) return

            // Fast Stage 2: Aspect ratio compatibility
            if (a.width > 0 && a.height > 0 && b.width > 0 && b.height > 0) {
                val ratioA = a.width.toFloat() / a.height.toFloat()
                val ratioB = b.width.toFloat() / b.height.toFloat()
                if (abs(ratioA - ratioB) > 0.16f) return
            }

            // Fast Stage 4: Video duration compatibility
            if (a.isVideo && b.isVideo && a.durationMs > 0L && b.durationMs > 0L) {
                val durationDiffMs = abs(a.durationMs - b.durationMs)
                val maxAllowedDiff = maxOf(2_500L, (maxOf(a.durationMs, b.durationMs) * 0.15f).toLong())
                if (durationDiffMs > maxAllowedDiff) return
            }

            evaluatedPairsCount++
            if (similarityVerifier(a, b)) {
                verifiedPairsCount++
                candidateEdges.getOrPut(a.uriString) { LinkedHashSet() }.add(b.uriString)
                candidateEdges.getOrPut(b.uriString) { LinkedHashSet() }.add(a.uriString)
            }
        }

        fun indexPartition(partition: List<PhotoEntity>) {
            if (partition.size <= 1) return

            // 1. LSH Band Inverted Index (4 bands for dHash + 4 bands for pHash)
            val dBandIndex = Array(4) { HashMap<Int, MutableList<PhotoEntity>>() }
            val pBandIndex = Array(4) { HashMap<Int, MutableList<PhotoEntity>>() }
            val bkTree = HammingBkTree()

            for (item in partition) {
                val dBands = extractLshBands(item.dHash)
                dBandIndex[0].getOrPut(dBands.band0) { mutableListOf() }.add(item)
                dBandIndex[1].getOrPut(dBands.band1) { mutableListOf() }.add(item)
                dBandIndex[2].getOrPut(dBands.band2) { mutableListOf() }.add(item)
                dBandIndex[3].getOrPut(dBands.band3) { mutableListOf() }.add(item)

                val pBands = extractLshBands(item.pHash)
                pBandIndex[0].getOrPut(pBands.band0) { mutableListOf() }.add(item)
                pBandIndex[1].getOrPut(pBands.band1) { mutableListOf() }.add(item)
                pBandIndex[2].getOrPut(pBands.band2) { mutableListOf() }.add(item)
                pBandIndex[3].getOrPut(pBands.band3) { mutableListOf() }.add(item)

                parseHex64Bits(item.dHash)?.let { bits ->
                    bkTree.insert(bits, item)
                }
            }

            // Collect LSH bucket candidates (exact/near-exact copies across any date)
            for (bandMap in dBandIndex) {
                for (bucket in bandMap.values) {
                    if (bucket.size in 2..64) {
                        for (i in 0 until bucket.size) {
                            for (j in (i + 1) until bucket.size) {
                                considerPair(bucket[i], bucket[j])
                            }
                        }
                    }
                }
            }
            for (bandMap in pBandIndex) {
                for (bucket in bandMap.values) {
                    if (bucket.size in 2..64) {
                        for (i in 0 until bucket.size) {
                            for (j in (i + 1) until bucket.size) {
                                considerPair(bucket[i], bucket[j])
                            }
                        }
                    }
                }
            }

            // 2. Bounded Temporal Window Index for burst sequences (within 2 minutes)
            // Since `datedItems` are sorted by capture time DESC, we only scan forward while timeDiff <= 120_000L.
            // (Same-day pairs with dDist <= 4 are already guaranteed to be found by the BK-Tree below.)
            val datedItems = partition.filter { it.hasReliableCaptureDate && (it.effectiveCaptureOrModifiedEpochMs ?: 0L) > 0L }
                .sortedByDescending { it.effectiveCaptureOrModifiedEpochMs ?: 0L }

            for (i in datedItems.indices) {
                val anchor = datedItems[i]
                val tsA = anchor.effectiveCaptureOrModifiedEpochMs ?: continue
                for (j in (i + 1) until datedItems.size) {
                    val other = datedItems[j]
                    val tsB = other.effectiveCaptureOrModifiedEpochMs ?: continue
                    val diffMs = tsA - tsB
                    if (diffMs > 120_000L) {
                        // Because datedItems is sorted descending, all subsequent items are > 2 minutes older
                        break
                    }
                    considerPair(anchor, other)
                }
            }

            // 3. BK-Tree Hamming Neighborhood Query (distance <= 4) for near-copies regardless of folder/timestamp
            val bkHits = LinkedHashSet<String>()
            for (item in partition) {
                val bits = parseHex64Bits(item.dHash) ?: continue
                bkHits.clear()
                bkTree.queryWithinDistance(bits, maxDistance = 4, outResults = bkHits, byUri = byUri)
                for (hitUri in bkHits) {
                    if (hitUri != item.uriString) {
                        byUri[hitUri]?.let { neighbor ->
                            considerPair(item, neighbor)
                        }
                    }
                }
            }
        }

        indexPartition(images)
        indexPartition(videos)

        // Form deterministic, non-chaining clusters:
        // Every member of a cluster must be directly verified similar to the cluster's anchor
        // AND not excluded against any existing member in that cluster.
        val assignedCluster = HashMap<String, String>()
        val clusterMembers = LinkedHashMap<String, List<PhotoEntity>>()

        for (item in ordered) {
            if (assignedCluster.containsKey(item.uriString)) continue
            val neighbors = candidateEdges[item.uriString] ?: continue
            if (neighbors.isEmpty()) continue

            val members = mutableListOf(item)
            for (neighborUri in neighbors) {
                if (assignedCluster.containsKey(neighborUri)) continue
                val candidate = byUri[neighborUri] ?: continue
                val canJoinAll = members.all { existingMember ->
                    val pk = canonicalPairKey(existingMember.uriString, candidate.uriString)
                    pk !in excludedPairs &&
                        (existingMember.uriString == item.uriString ||
                            candidateEdges[existingMember.uriString]?.contains(candidate.uriString) == true)
                }
                if (canJoinAll) {
                    members.add(candidate)
                }
            }

            if (members.size > 1) {
                val anchor = members.minByOrNull { it.uriString } ?: item
                val deterministicId = "cluster_${anchor.dHash.take(8)}_${anchor.uriString.hashCode().toUInt().toString(16)}"
                for (m in members) {
                    assignedCluster[m.uriString] = deterministicId
                }
                clusterMembers[deterministicId] = members
            }
        }

        val stats = CandidateIndexStats(
            totalItems = n,
            theoreticalAllPairsCount = theoreticalAllPairs,
            candidatePairsEvaluated = evaluatedPairsCount,
            verifiedSimilarPairs = verifiedPairsCount,
            clustersFormed = clusterMembers.size
        )
        return clusterMembers to stats
    }

    /**
     * Classifies a verified cluster of [members] into one of the 6 explicit [MediaClusterType] categories
     * with a calibrated confidence score and human-readable reason (Pass 2 Section C).
     */
    fun classifyCluster(
        clusterId: String,
        members: List<PhotoEntity>,
        bestShot: PhotoEntity?
    ): MediaCluster {
        val anchor = bestShot ?: members.firstOrNull()
        if (anchor == null || members.isEmpty()) {
            return MediaCluster(
                clusterId = clusterId,
                type = MediaClusterType.SIMILAR_PHOTO,
                confidence = 0.75f,
                reason = "Similar media group",
                bestShotCandidate = null,
                recoverableBytes = 0L,
                members = members
            )
        }

        var maxDDist = 0
        var maxPDist = 0
        val anchorDBits = parseHex64Bits(anchor.dHash)
        val anchorPBits = parseHex64Bits(anchor.pHash)

        for (m in members) {
            if (m.uriString == anchor.uriString) continue
            val mDBits = parseHex64Bits(m.dHash)
            val mPBits = parseHex64Bits(m.pHash)
            val dDist = if (anchorDBits != null && mDBits != null) (anchorDBits xor mDBits).countOneBits() else 8
            val pDist = if (anchorPBits != null && mPBits != null) (anchorPBits xor mPBits).countOneBits() else 8
            if (dDist > maxDDist) maxDDist = dDist
            if (pDist > maxPDist) maxPDist = pDist
        }

        val minBytes = members.minOf { it.fileSizeBytes }.coerceAtLeast(1L)
        val maxBytes = members.maxOf { it.fileSizeBytes }.coerceAtLeast(1L)
        val maxSizeDiffRatio = (maxBytes - minBytes).toFloat() / maxBytes.toFloat()

        val allHaveCaptureDates = members.all { it.hasReliableCaptureDate && (it.effectiveCaptureOrModifiedEpochMs ?: 0L) > 0L }
        val maxTimeSpanMs = if (allHaveCaptureDates) {
            val minTs = members.minOf { it.effectiveCaptureOrModifiedEpochMs ?: 0L }
            val maxTs = members.maxOf { it.effectiveCaptureOrModifiedEpochMs ?: 0L }
            (maxTs - minTs).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }

        val allVideos = members.all { it.isVideo }
        val anyScreenshot = members.any {
            it.categoryEnum == PhotoCategory.SCREENSHOT ||
                it.title.lowercase().contains("screenshot") ||
                it.folderName.lowercase().contains("screenshot")
        }

        val (type, confidence, reason) = when {
            allVideos && maxDDist == 0 && maxPDist <= 1 && maxSizeDiffRatio <= 0.01f ->
                Triple(
                    MediaClusterType.EXACT_DUPLICATE,
                    0.99f,
                    "Identical video copies (${anchor.formattedDuration})"
                )
            allVideos ->
                Triple(
                    MediaClusterType.SIMILAR_VIDEO,
                    (0.94f - (maxDDist * 0.015f)).coerceIn(0.78f, 0.95f),
                    "Multi-frame video match (${anchor.formattedDuration})"
                )
            maxDDist == 0 && maxPDist == 0 && maxSizeDiffRatio <= 0.01f ->
                Triple(
                    MediaClusterType.EXACT_DUPLICATE,
                    0.99f,
                    if (anyScreenshot) "Identical screenshot files" else "Exact duplicate photo files"
                )
            anyScreenshot ->
                Triple(
                    MediaClusterType.SCREENSHOT_VARIANT,
                    (0.93f - (maxDDist * 0.01f)).coerceIn(0.80f, 0.95f),
                    "Repeated or scrolled screenshot captures (${members.size} items)"
                )
            maxDDist <= 2 && maxPDist <= 3 && maxSizeDiffRatio <= 0.25f ->
                Triple(
                    MediaClusterType.NEAR_DUPLICATE,
                    (0.95f - (maxDDist * 0.02f)).coerceIn(0.88f, 0.97f),
                    "Near-identical photo copies (re-saved or compressed)"
                )
            allHaveCaptureDates && maxTimeSpanMs <= 120_000L ->
                Triple(
                    MediaClusterType.BURST_SEQUENCE,
                    (0.91f - (maxDDist * 0.01f)).coerceIn(0.82f, 0.93f),
                    "Camera burst sequence (${members.size} shots within ${(maxTimeSpanMs / 1000L).coerceAtLeast(1L)}s)"
                )
            else ->
                Triple(
                    MediaClusterType.SIMILAR_PHOTO,
                    (0.85f - (maxDDist * 0.015f)).coerceIn(0.74f, 0.88f),
                    "Visually similar photos in ${anchor.folderName.ifBlank { "Library" }}"
                )
        }

        val recoverableBytes = members
            .filter { bestShot == null || it.uriString != bestShot.uriString }
            .sumOf { it.fileSizeBytes }

        return MediaCluster(
            clusterId = clusterId,
            type = type,
            confidence = confidence,
            reason = reason,
            bestShotCandidate = bestShot,
            recoverableBytes = recoverableBytes,
            members = members,
            clusterGenerationVersion = MediaCluster.CLUSTER_GENERATION_VERSION
        )
    }
}
