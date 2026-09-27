package com.example.domain.scanner

import android.content.Context
import com.example.data.local.LuminaDao
import com.example.data.local.PhotoEntity
import com.example.data.local.ScanCheckpointEntity
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

enum class ScanPhase(val displayLabel: String) {
    IDLE("Ready"),
    DISCOVER("Discovering Media"),
    METADATA("Reading Metadata"),
    THUMBNAIL_FINGERPRINT("Computing Fingerprints"),
    LOCAL_QUALITY_ANALYSIS("Analyzing Quality"),
    OCR("Extracting Text Signals"),
    CANDIDATE_INDEX("Indexing Candidates"),
    CLUSTER("Grouping Similar Media"),
    BEST_SHOT("Selecting Best Shots"),
    COMPLETE("Scan Complete"),
    PAUSED("Scan Paused"),
    CANCELLED("Scan Cancelled"),
    FAILED("Scan Interrupted");

    val userLabel: String
        get() = displayLabel
}

data class ScanProgress(
    val phase: ScanPhase = ScanPhase.IDLE,
    val discovered: Int = 0,
    val analyzed: Int = 0,
    val total: Int = 0,
    val changed: Int = 0,
    val unchangedSkipped: Int = 0,
    val errors: Int = 0,
    val startedAt: Long = 0L,
    val isPaused: Boolean = false,
    val isCancelled: Boolean = false,
    val lastProcessedOffset: Int = 0,
    val lastProcessedUri: String = ""
) {
    val userFacingHeadline: String
        get() = homeStatusText
    val isActive: Boolean
        get() = phase !in setOf(
            ScanPhase.IDLE,
            ScanPhase.COMPLETE,
            ScanPhase.PAUSED,
            ScanPhase.CANCELLED,
            ScanPhase.FAILED
        ) && !isPaused && !isCancelled

    val progressFraction: Float
        get() = if (total > 0) {
            (analyzed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    /**
     * Clean, user-friendly progress text for Home ("Analyzing 742 / 8,431") without technical jargon.
     */
    val homeStatusText: String
        get() = when {
            isPaused || phase == ScanPhase.PAUSED ->
                if (total > 0) {
                    "Paused at ${formatCount(analyzed)} / ${formatCount(total)}"
                } else {
                    "Scan paused"
                }
            isCancelled || phase == ScanPhase.CANCELLED ->
                "Scan cancelled (${formatCount(analyzed)} analyzed)"
            phase == ScanPhase.DISCOVER ->
                if (discovered > 0) {
                    "Finding media (${formatCount(discovered)} found)..."
                } else {
                    "Scanning device media..."
                }
            phase in setOf(
                ScanPhase.METADATA,
                ScanPhase.THUMBNAIL_FINGERPRINT,
                ScanPhase.LOCAL_QUALITY_ANALYSIS,
                ScanPhase.OCR
            ) ->
                if (total > 0) {
                    "Analyzing ${formatCount(analyzed)} / ${formatCount(total)}"
                } else {
                    "Analyzing media..."
                }
            phase in setOf(ScanPhase.CANDIDATE_INDEX, ScanPhase.CLUSTER, ScanPhase.BEST_SHOT) ->
                if (total > 0) {
                    "Grouping similar items (${formatCount(total)})..."
                } else {
                    "Grouping similar items..."
                }
            phase == ScanPhase.COMPLETE && total > 0 ->
                "Analyzed ${formatCount(total)} items"
            else -> ""
        }

    fun toCheckpointEntity(id: String = CHECKPOINT_ID): ScanCheckpointEntity {
        return ScanCheckpointEntity(
            id = id,
            phase = phase.name,
            discoveredCount = discovered,
            analyzedCount = analyzed,
            totalCount = total,
            changedCount = changed,
            unchangedSkippedCount = unchangedSkipped,
            errorCount = errors,
            lastProcessedCursorOffset = lastProcessedOffset,
            lastProcessedUri = lastProcessedUri,
            startedAtEpochMs = startedAt,
            updatedAtEpochMs = System.currentTimeMillis(),
            isPaused = isPaused || phase == ScanPhase.PAUSED,
            isCancelled = isCancelled || phase == ScanPhase.CANCELLED
        )
    }

    companion object {
        const val CHECKPOINT_ID = "primary_library_scan"

        private fun formatCount(value: Int): String =
            String.format(Locale.US, "%,d", value)

        fun fromCheckpointEntity(entity: ScanCheckpointEntity?): ScanProgress {
            if (entity == null) return ScanProgress()
            val parsedPhase = runCatching { ScanPhase.valueOf(entity.phase) }.getOrDefault(ScanPhase.IDLE)
            return ScanProgress(
                phase = parsedPhase,
                discovered = entity.discoveredCount,
                analyzed = entity.analyzedCount,
                total = entity.totalCount,
                changed = entity.changedCount,
                unchangedSkipped = entity.unchangedSkippedCount,
                errors = entity.errorCount,
                startedAt = entity.startedAtEpochMs,
                isPaused = entity.isPaused,
                isCancelled = entity.isCancelled,
                lastProcessedOffset = entity.lastProcessedCursorOffset,
                lastProcessedUri = entity.lastProcessedUri
            )
        }
    }
}

/**
 * Coordinates Pass 2's incremental, chunked media scanning pipeline:
 * - Processes media in non-blocking chunks (`chunkSize`, default 32)
 * - Persists a durable [ScanCheckpointEntity] in Room after every stage & chunk
 * - Supports pause, resume, cancel, and retry
 * - Never re-decodes unchanged items
 * - Preserves all Pass 1 database ID and URI identity invariants
 */
class IncrementalScanCoordinator(
    private val appContext: Context,
    private val dao: LuminaDao,
    private val scannerEngine: MediaScannerEngine
) {
    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgressFlow: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val pauseRequested = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)

    suspend fun restoreCheckpointFromDb(): ScanProgress {
        val saved = dao.getScanCheckpointOnce()
        val progress = ScanProgress.fromCheckpointEntity(saved)
        _scanProgress.value = progress
        return progress
    }

    fun requestPause() {
        pauseRequested.set(true)
        val cur = _scanProgress.value
        if (cur.isActive) {
            _scanProgress.value = cur.copy(phase = ScanPhase.PAUSED, isPaused = true)
        }
    }

    fun requestCancel() {
        cancelRequested.set(true)
        pauseRequested.set(false)
        val cur = _scanProgress.value
        _scanProgress.value = cur.copy(phase = ScanPhase.CANCELLED, isPaused = false, isCancelled = true)
    }

    private suspend fun emitAndPersist(progress: ScanProgress) {
        _scanProgress.value = progress
        dao.upsertScanCheckpoint(progress.toCheckpointEntity())
    }

    /**
     * Runs the full incremental scanning pipeline over [discoveredRecords] (or discovers from MediaStore
     * when [discoveredRecords] is null).
     *
     * Supports resuming from a paused/interrupted checkpoint when [resumeFromCheckpoint] is true.
     */
    suspend fun runIncrementalScan(
        discoveredRecordsOverride: List<SourceMediaMetadata>? = null,
        chunkSize: Int = 32,
        resumeFromCheckpoint: Boolean = false
    ): LibraryScanSummary = withContext(Dispatchers.IO) {
        pauseRequested.set(false)
        cancelRequested.set(false)

        val existingCheckpoint = if (resumeFromCheckpoint) {
            dao.getScanCheckpointOnce()
        } else {
            null
        }

        val startedAt = existingCheckpoint?.startedAtEpochMs?.takeIf { it > 0L } ?: System.currentTimeMillis()
        val startOffset = if (resumeFromCheckpoint && existingCheckpoint != null &&
            (existingCheckpoint.isPaused || existingCheckpoint.phase !in setOf(ScanPhase.COMPLETE.name, ScanPhase.IDLE.name))
        ) {
            existingCheckpoint.lastProcessedCursorOffset.coerceAtLeast(0)
        } else {
            0
        }

        // Stage 1: DISCOVER
        emitAndPersist(
            ScanProgress(
                phase = ScanPhase.DISCOVER,
                discovered = existingCheckpoint?.discoveredCount ?: 0,
                analyzed = startOffset,
                total = existingCheckpoint?.totalCount ?: 0,
                startedAt = startedAt
            )
        )

        val candidateRecords = discoveredRecordsOverride
            ?: scannerEngine.discoverAllMediaStoreMetadata(appContext) { discoveredSoFar ->
                _scanProgress.value = _scanProgress.value.copy(
                    phase = ScanPhase.DISCOVER,
                    discovered = discoveredSoFar
                )
            }

        val totalDiscovered = candidateRecords.size
        val existingPhotos = dao.getAllPhotosOnce()
        val existingByUri = existingPhotos.associateBy { it.uriString }.toMutableMap()

        emitAndPersist(
            ScanProgress(
                phase = ScanPhase.METADATA,
                discovered = totalDiscovered,
                analyzed = startOffset.coerceAtMost(totalDiscovered),
                total = totalDiscovered,
                startedAt = startedAt,
                lastProcessedOffset = startOffset
            )
        )

        val newlyAddedTotal = mutableListOf<PhotoEntity>()
        val changedReanalyzedTotal = mutableListOf<PhotoEntity>()
        val unchangedReusedTotal = mutableListOf<PhotoEntity>()
        val seenExistingUris = HashSet<String>()

        var analyzedCount = startOffset.coerceAtMost(totalDiscovered)
        var changedCount = existingCheckpoint?.changedCount ?: 0
        var unchangedSkippedCount = existingCheckpoint?.unchangedSkippedCount ?: 0
        var errorCount = existingCheckpoint?.errorCount ?: 0

        // Mark already-processed slice when resuming from checkpoint
        if (startOffset > 0 && startOffset <= candidateRecords.size) {
            for (idx in 0 until startOffset) {
                val uriStr = candidateRecords[idx].uri.toString()
                existingByUri[uriStr]?.let { existing ->
                    seenExistingUris.add(uriStr)
                    unchangedReusedTotal.add(existing)
                }
            }
        }

        // Stages 2–5: Process discovered records in bounded chunks
        val remainingSlice = if (startOffset in 0..candidateRecords.size) {
            candidateRecords.subList(startOffset, candidateRecords.size)
        } else {
            candidateRecords
        }

        val chunks = remainingSlice.chunked(chunkSize.coerceAtLeast(1))
        for (chunk in chunks) {
            yield()

            if (cancelRequested.get()) {
                val cancelledProgress = _scanProgress.value.copy(
                    phase = ScanPhase.CANCELLED,
                    isCancelled = true,
                    isPaused = false
                )
                emitAndPersist(cancelledProgress)
                return@withContext LibraryScanSummary(
                    photosAfterScanAndCluster = dao.getAllPhotosOnce(),
                    newItemsAdded = newlyAddedTotal,
                    changedItemsReanalyzed = changedReanalyzedTotal,
                    unchangedItemsReused = unchangedReusedTotal,
                    deletedSourceItemsDetected = emptyList()
                )
            }

            while (pauseRequested.get() && !cancelRequested.get()) {
                if (_scanProgress.value.phase != ScanPhase.PAUSED) {
                    emitAndPersist(
                        _scanProgress.value.copy(
                            phase = ScanPhase.PAUSED,
                            isPaused = true
                        )
                    )
                }
                delay(120L)
            }

            val newInChunk = mutableListOf<PhotoEntity>()
            val updatedInChunk = mutableListOf<PhotoEntity>()
            var lastUriInChunk = ""

            for (metadata in chunk) {
                if (cancelRequested.get() || pauseRequested.get()) break
                val uriStr = metadata.uri.toString()
                lastUriInChunk = uriStr
                val existing = existingByUri[uriStr]
                if (existing != null) {
                    seenExistingUris.add(uriStr)
                }

                try {
                    val decision = MediaMetadataReader.classifyRevision(
                        existing = existing,
                        currentSizeBytes = metadata.fileSizeBytes,
                        currentDateModifiedMs = metadata.dateModifiedEpochMs,
                        currentGenerationModified = metadata.sourceGenerationModified,
                        currentWidth = metadata.width,
                        currentHeight = metadata.height
                    )

                    when (decision) {
                        MediaScanDecision.NEW_ITEM -> {
                            _scanProgress.value = _scanProgress.value.copy(
                                phase = ScanPhase.THUMBNAIL_FINGERPRINT
                            )
                            val analyzed = scannerEngine.analyzeMediaFromMetadataInternal(
                                context = appContext,
                                metadata = metadata,
                                existingToUpdate = null
                            )
                            if (analyzed != null) {
                                newInChunk += analyzed
                                newlyAddedTotal += analyzed
                            }
                        }
                        MediaScanDecision.UNCHANGED_ITEM -> {
                            if (existing != null) {
                                unchangedReusedTotal += existing
                                unchangedSkippedCount++
                            }
                        }
                        MediaScanDecision.CHANGED_ITEM -> {
                            if (existing != null) {
                                _scanProgress.value = _scanProgress.value.copy(
                                    phase = ScanPhase.LOCAL_QUALITY_ANALYSIS
                                )
                                com.example.ui.components.MediaThumbnailIdentityCache.evict(existing.stableIdentityKey)
                                val reanalyzed = scannerEngine.analyzeMediaFromMetadataInternal(
                                    context = appContext,
                                    metadata = metadata,
                                    existingToUpdate = existing
                                )
                                if (reanalyzed != null) {
                                    updatedInChunk += reanalyzed
                                    changedReanalyzedTotal += reanalyzed
                                    changedCount++
                                } else {
                                    unchangedReusedTotal += existing
                                }
                            }
                        }
                        MediaScanDecision.DELETED_SOURCE_ITEM -> Unit
                    }
                } catch (_: CancellationException) {
                    throw CancellationException()
                } catch (_: Exception) {
                    errorCount++
                    if (existing != null) {
                        unchangedReusedTotal += existing
                    }
                }

                analyzedCount++
            }

            // Persist chunk results immediately so progress is durable even if app closes mid-scan
            if (newInChunk.isNotEmpty()) {
                dao.insertPhotosIgnoreConflicts(newInChunk)
            }
            if (updatedInChunk.isNotEmpty()) {
                dao.updatePhotos(updatedInChunk)
            }

            val chunkProgress = ScanProgress(
                phase = if (pauseRequested.get()) ScanPhase.PAUSED else ScanPhase.LOCAL_QUALITY_ANALYSIS,
                discovered = totalDiscovered,
                analyzed = analyzedCount,
                total = totalDiscovered,
                changed = changedCount,
                unchangedSkipped = unchangedSkippedCount,
                errors = errorCount,
                startedAt = startedAt,
                isPaused = pauseRequested.get(),
                isCancelled = cancelRequested.get(),
                lastProcessedOffset = analyzedCount,
                lastProcessedUri = lastUriInChunk
            )
            emitAndPersist(chunkProgress)
        }

        // Remove confirmed-deleted local file:// items
        val deletedFromSource = mutableListOf<PhotoEntity>()
        val gateway = AndroidSourceMediaDeletionGateway(appContext)
        for (existing in existingPhotos) {
            if (existing.uriString in seenExistingUris) continue
            val uri = runCatching { android.net.Uri.parse(existing.uriString) }.getOrNull()
            if (uri?.scheme == "file" && !gateway.isSourceUriStillAccessible(uri)) {
                deletedFromSource += existing
                com.example.ui.components.MediaThumbnailIdentityCache.evict(existing.stableIdentityKey)
                dao.permanentlyDeleteVerifiedPhoto(existing.id, existing.uriString)
            }
        }

        // Stage 6 & 7 & 8: CANDIDATE_INDEX -> CLUSTER -> BEST_SHOT
        emitAndPersist(
            _scanProgress.value.copy(
                phase = ScanPhase.CANDIDATE_INDEX,
                analyzed = totalDiscovered,
                total = totalDiscovered
            )
        )

        val allNowInDb = dao.getAllPhotosOnce()
        val pairExclusions = dao.getAllPairExclusionsOnce().map { it.canonicalPairKey }.toSet()

        emitAndPersist(_scanProgress.value.copy(phase = ScanPhase.CLUSTER))
        val clustered = scannerEngine.clusterAndNominateBestShots(
            photos = allNowInDb,
            explicitExcludedPairKeys = pairExclusions
        )

        emitAndPersist(_scanProgress.value.copy(phase = ScanPhase.BEST_SHOT))
        if (clustered.isNotEmpty()) {
            dao.updatePhotos(clustered)
        }

        // Stage 9: COMPLETE
        val completedProgress = ScanProgress(
            phase = ScanPhase.COMPLETE,
            discovered = totalDiscovered,
            analyzed = totalDiscovered,
            total = totalDiscovered,
            changed = changedCount,
            unchangedSkipped = unchangedSkippedCount,
            errors = errorCount,
            startedAt = startedAt,
            isPaused = false,
            isCancelled = false,
            lastProcessedOffset = totalDiscovered
        )
        emitAndPersist(completedProgress)

        LibraryScanSummary(
            photosAfterScanAndCluster = clustered,
            newItemsAdded = newlyAddedTotal,
            changedItemsReanalyzed = changedReanalyzedTotal,
            unchangedItemsReused = unchangedReusedTotal,
            deletedSourceItemsDetected = deletedFromSource
        )
    }

    fun resumePausedScan() {
        pauseRequested.set(false)
    }
}
