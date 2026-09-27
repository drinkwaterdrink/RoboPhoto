package com.example.domain.scanner

import com.example.data.local.LuminaDao
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import java.util.concurrent.CopyOnWriteArrayList

data class IdentityVerificationAuditEvent(
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val actionName: String,
    val requestedId: Long,
    val requestedUri: String,
    val databaseUriFound: String?,
    val reason: String
)

sealed class IdentityVerificationResult {
    data class Verified(val currentDbPhoto: PhotoEntity) : IdentityVerificationResult()
    data class StaleOrMismatched(
        val requestedId: Long,
        val requestedUri: String,
        val refreshedPhoto: PhotoEntity?,
        val auditEvent: IdentityVerificationAuditEvent
    ) : IdentityVerificationResult()
}

data class VerifiedUndoEntry(
    val photoId: Long,
    val expectedUri: String,
    val previousStatus: TriageStatus
)

data class TriageActionOutcome(
    val requestedCount: Int,
    val verifiedUpdatedCount: Int,
    val staleRejectedCount: Int,
    val auditEvents: List<IdentityVerificationAuditEvent>,
    val userRefreshNotice: String?
) {
    val allSucceeded: Boolean
        get() = verifiedUpdatedCount == requestedCount && staleRejectedCount == 0
}

/**
 * Enforces the P0 invariant:
 * DISPLAYED MEDIA -> PhotoEntity -> stable canonical URI -> action/deletion target
 * must ALWAYS represent the exact same underlying media item.
 *
 * Never falls back to ID-only mutations. If (id, expectedUri) verification fails,
 * the mutation is rejected, the item is re-queried, and an audit event is recorded.
 */
class MediaIdentityVerifier(
    private val dao: LuminaDao
) {
    private val recentAuditEvents = CopyOnWriteArrayList<IdentityVerificationAuditEvent>()

    fun getAuditEvents(): List<IdentityVerificationAuditEvent> = recentAuditEvents.toList()

    fun clearAuditEvents() {
        recentAuditEvents.clear()
    }

    suspend fun verifyPhotoIdentity(
        photo: PhotoEntity,
        actionName: String
    ): IdentityVerificationResult {
        if (photo.id <= 0L || photo.uriString.isBlank()) {
            val event = IdentityVerificationAuditEvent(
                actionName = actionName,
                requestedId = photo.id,
                requestedUri = photo.uriString,
                databaseUriFound = null,
                reason = "Invalid photo identity (id=${photo.id}, uri='${photo.uriString}')"
            )
            recentAuditEvents.add(event)
            return IdentityVerificationResult.StaleOrMismatched(
                requestedId = photo.id,
                requestedUri = photo.uriString,
                refreshedPhoto = null,
                auditEvent = event
            )
        }

        val exactMatch = dao.getVerifiedPhoto(photo.id, photo.uriString)
        if (exactMatch != null) {
            // Check whether the file size or modification timestamp changed in DB since the UI snapshot
            if (exactMatch.fileSizeBytes != photo.fileSizeBytes ||
                (photo.dateModifiedEpochMs > 0L && exactMatch.dateModifiedEpochMs > 0L &&
                    exactMatch.dateModifiedEpochMs != photo.dateModifiedEpochMs)
            ) {
                val event = IdentityVerificationAuditEvent(
                    actionName = actionName,
                    requestedId = photo.id,
                    requestedUri = photo.uriString,
                    databaseUriFound = exactMatch.uriString,
                    reason = "Media revision changed since item was loaded (size ${photo.fileSizeBytes}->${exactMatch.fileSizeBytes})"
                )
                recentAuditEvents.add(event)
                return IdentityVerificationResult.StaleOrMismatched(
                    requestedId = photo.id,
                    requestedUri = photo.uriString,
                    refreshedPhoto = exactMatch,
                    auditEvent = event
                )
            }
            return IdentityVerificationResult.Verified(exactMatch)
        }

        val byId = dao.getPhotoById(photo.id)
        val byUri = dao.getPhotoByUri(photo.uriString)
        val event = IdentityVerificationAuditEvent(
            actionName = actionName,
            requestedId = photo.id,
            requestedUri = photo.uriString,
            databaseUriFound = byId?.uriString,
            reason = if (byId == null) {
                "Database row id=${photo.id} no longer exists"
            } else {
                "URI mismatch for id=${photo.id}: expected '${photo.uriString}', found '${byId.uriString}'"
            }
        )
        recentAuditEvents.add(event)
        return IdentityVerificationResult.StaleOrMismatched(
            requestedId = photo.id,
            requestedUri = photo.uriString,
            refreshedPhoto = byUri ?: byId,
            auditEvent = event
        )
    }

    suspend fun applyVerifiedSingleTriage(
        photo: PhotoEntity,
        newStatus: TriageStatus
    ): TriageActionOutcome {
        return applyVerifiedBatchTriage(listOf(photo), newStatus, actionName = "single_triage_${newStatus.name}")
    }

    suspend fun applyVerifiedBatchTriage(
        photos: List<PhotoEntity>,
        newStatus: TriageStatus,
        actionName: String = "batch_triage_${newStatus.name}"
    ): TriageActionOutcome {
        if (photos.isEmpty()) {
            return TriageActionOutcome(
                requestedCount = 0,
                verifiedUpdatedCount = 0,
                staleRejectedCount = 0,
                auditEvents = emptyList(),
                userRefreshNotice = null
            )
        }

        val vaultedAt = if (newStatus == TriageStatus.TRASH_VAULT) System.currentTimeMillis() else null
        var updatedCount = 0
        var rejectedCount = 0
        val events = mutableListOf<IdentityVerificationAuditEvent>()

        for (candidate in photos) {
            when (val verification = verifyPhotoIdentity(candidate, actionName)) {
                is IdentityVerificationResult.Verified -> {
                    val rows = dao.updateTriageStatusVerified(
                        photoId = verification.currentDbPhoto.id,
                        expectedUri = verification.currentDbPhoto.uriString,
                        status = newStatus.name,
                        vaultedAt = vaultedAt
                    )
                    if (rows == 1) {
                        updatedCount++
                    } else {
                        rejectedCount++
                        val ev = IdentityVerificationAuditEvent(
                            actionName = actionName,
                            requestedId = candidate.id,
                            requestedUri = candidate.uriString,
                            databaseUriFound = null,
                            reason = "Concurrent modification prevented verified update"
                        )
                        recentAuditEvents.add(ev)
                        events.add(ev)
                    }
                }
                is IdentityVerificationResult.StaleOrMismatched -> {
                    rejectedCount++
                    events.add(verification.auditEvent)
                }
            }
        }

        val notice = if (rejectedCount > 0) {
            if (photos.size == 1) {
                STALE_SINGLE_ITEM_NOTICE
            } else {
                "Skipped $rejectedCount item(s) that changed since loading. RoboPhoto refreshed your view without modifying them."
            }
        } else {
            null
        }

        return TriageActionOutcome(
            requestedCount = photos.size,
            verifiedUpdatedCount = updatedCount,
            staleRejectedCount = rejectedCount,
            auditEvents = events,
            userRefreshNotice = notice
        )
    }

    suspend fun applyVerifiedUndo(entries: List<VerifiedUndoEntry>): TriageActionOutcome {
        if (entries.isEmpty()) {
            return TriageActionOutcome(0, 0, 0, emptyList(), null)
        }
        var restoredCount = 0
        var rejectedCount = 0
        val events = mutableListOf<IdentityVerificationAuditEvent>()

        for (entry in entries) {
            if (entry.photoId <= 0L || entry.expectedUri.isBlank()) {
                rejectedCount++
                continue
            }
            val vaultedAt = if (entry.previousStatus == TriageStatus.TRASH_VAULT) System.currentTimeMillis() else null
            val rows = dao.updateTriageStatusVerified(
                photoId = entry.photoId,
                expectedUri = entry.expectedUri,
                status = entry.previousStatus.name,
                vaultedAt = vaultedAt
            )
            if (rows == 1) {
                restoredCount++
            } else {
                rejectedCount++
                val current = dao.getPhotoById(entry.photoId)
                val ev = IdentityVerificationAuditEvent(
                    actionName = "undo_triage",
                    requestedId = entry.photoId,
                    requestedUri = entry.expectedUri,
                    databaseUriFound = current?.uriString,
                    reason = "Undo rejected because (id, uri) no longer matched"
                )
                recentAuditEvents.add(ev)
                events.add(ev)
            }
        }

        val notice = if (rejectedCount > 0) STALE_SINGLE_ITEM_NOTICE else null
        return TriageActionOutcome(
            requestedCount = entries.size,
            verifiedUpdatedCount = restoredCount,
            staleRejectedCount = rejectedCount,
            auditEvents = events,
            userRefreshNotice = notice
        )
    }

    companion object {
        const val STALE_SINGLE_ITEM_NOTICE =
            "This item changed since it was loaded. RoboPhoto refreshed it without making changes."
    }
}
