package com.example.domain.scanner

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.local.LuminaDao
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.ui.components.MediaThumbnailIdentityCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FailedDeletionItem(
    val photoId: Long,
    val uriString: String,
    val title: String,
    val reason: String
)

data class DeletionResult(
    val requestedCount: Int,
    val confirmedDeletedCount: Int,
    val failedCount: Int,
    val confirmedFreedBytes: Long,
    val failedItems: List<FailedDeletionItem>
) {
    val allSucceeded: Boolean
        get() = requestedCount > 0 && confirmedDeletedCount == requestedCount && failedCount == 0
}

sealed class SourceDeleteAttemptResult {
    data object ConfirmedDeleted : SourceDeleteAttemptResult()
    data class RequiresSystemConfirmation(
        val uri: Uri,
        val fallbackIntentSender: IntentSender? = null
    ) : SourceDeleteAttemptResult()
    data class Failed(val reason: String) : SourceDeleteAttemptResult()
}

sealed class DeletionExecutionOutcome {
    data class Completed(val result: DeletionResult) : DeletionExecutionOutcome()
    data class RequiresSystemDialog(
        val intentSender: IntentSender,
        val pendingItems: List<PhotoEntity>,
        val partialDirectDeletedCount: Int,
        val partialDirectFreedBytes: Long,
        val preFailedItems: List<FailedDeletionItem>
    ) : DeletionExecutionOutcome()
}

interface SourceMediaDeletionGateway {
    suspend fun attemptDirectSourceDelete(photo: PhotoEntity): SourceDeleteAttemptResult
    fun isSourceUriStillAccessible(uri: Uri): Boolean
    fun createBatchDeleteIntentSender(uris: List<Uri>): IntentSender?
}

class AndroidSourceMediaDeletionGateway(
    private val context: Context
) : SourceMediaDeletionGateway {

    override suspend fun attemptDirectSourceDelete(photo: PhotoEntity): SourceDeleteAttemptResult =
        withContext(Dispatchers.IO) {
            val rawUri = photo.uriString.trim()
            if (rawUri.isEmpty()) {
                return@withContext SourceDeleteAttemptResult.Failed("Missing URI string")
            }
            val uri = runCatching { Uri.parse(rawUri) }.getOrNull()
                ?: return@withContext SourceDeleteAttemptResult.Failed("Malformed URI: $rawUri")

            when (uri.scheme?.lowercase()) {
                "file" -> {
                    val path = uri.path
                    if (path.isNullOrBlank()) {
                        return@withContext SourceDeleteAttemptResult.Failed("Empty file path")
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        return@withContext SourceDeleteAttemptResult.Failed("Source file does not exist at $path")
                    }
                    val deleted = runCatching { file.delete() }.getOrDefault(false)
                    if (deleted && !file.exists()) {
                        SourceDeleteAttemptResult.ConfirmedDeleted
                    } else {
                        SourceDeleteAttemptResult.Failed("File system rejected deletion for $path")
                    }
                }

                "content" -> {
                    try {
                        val rowsDeleted = context.contentResolver.delete(uri, null, null)
                        if (rowsDeleted > 0 && !isSourceUriStillAccessible(uri)) {
                            SourceDeleteAttemptResult.ConfirmedDeleted
                        } else if (rowsDeleted > 0) {
                            SourceDeleteAttemptResult.Failed("ContentResolver reported $rowsDeleted row(s) but URI is still accessible")
                        } else {
                            // On Android 11+, MediaStore URIs owned by other apps may return 0 or throw SecurityException
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isMediaStoreVolumeUri(uri)) {
                                SourceDeleteAttemptResult.RequiresSystemConfirmation(uri)
                            } else {
                                SourceDeleteAttemptResult.Failed("ContentResolver.delete returned 0 rows (read-only or missing URI)")
                            }
                        }
                    } catch (secEx: SecurityException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isMediaStoreVolumeUri(uri)) {
                            return@withContext SourceDeleteAttemptResult.RequiresSystemConfirmation(uri)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && secEx is RecoverableSecurityException) {
                            return@withContext SourceDeleteAttemptResult.RequiresSystemConfirmation(
                                uri = uri,
                                fallbackIntentSender = secEx.userAction.actionIntent.intentSender
                            )
                        }
                        SourceDeleteAttemptResult.Failed("Permission denied by provider: ${secEx.message ?: "SecurityException"}")
                    } catch (e: Exception) {
                        SourceDeleteAttemptResult.Failed("Source deletion error: ${e.message ?: e.javaClass.simpleName}")
                    }
                }

                else -> SourceDeleteAttemptResult.Failed("Unsupported URI scheme: ${uri.scheme}")
            }
        }

    override fun isSourceUriStillAccessible(uri: Uri): Boolean {
        return when (uri.scheme?.lowercase()) {
            "file" -> {
                val path = uri.path ?: return false
                File(path).exists()
            }
            "content" -> {
                val queryFound = runCatching {
                    context.contentResolver.query(uri, arrayOf("_id"), null, null, null)?.use { cursor ->
                        cursor.moveToFirst()
                    } ?: false
                }.getOrDefault(false)
                if (queryFound) return true

                runCatching {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                }.getOrDefault(false)
            }
            else -> false
        }
    }

    override fun createBatchDeleteIntentSender(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return runCatching {
                val mediaStoreUris = uris.filter { isMediaStoreVolumeUri(it) }
                if (mediaStoreUris.isEmpty()) return null
                MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris).intentSender
            }.getOrNull()
        }
        return null
    }

    private fun isMediaStoreVolumeUri(uri: Uri): Boolean {
        val auth = uri.authority?.lowercase().orEmpty()
        return auth == MediaStore.AUTHORITY || auth == "media"
    }
}

/**
 * Coordinates P0 identity-verified permanent deletion:
 * - Verifies (id, uriString) in Room and ensures item is in Review Bin (TRASH_VAULT).
 * - Never deletes the Room record if the underlying source media deletion fails or is cancelled.
 * - Counts ONLY confirmed-deleted items toward confirmedFreedBytes.
 */
class MediaDeletionCoordinator(
    private val dao: LuminaDao,
    private val identityVerifier: MediaIdentityVerifier,
    private val deletionGateway: SourceMediaDeletionGateway
) {

    suspend fun executePermanentDelete(
        requestedPhotos: List<PhotoEntity>
    ): DeletionExecutionOutcome = withContext(Dispatchers.IO) {
        if (requestedPhotos.isEmpty()) {
            return@withContext DeletionExecutionOutcome.Completed(
                DeletionResult(
                    requestedCount = 0,
                    confirmedDeletedCount = 0,
                    failedCount = 0,
                    confirmedFreedBytes = 0L,
                    failedItems = emptyList()
                )
            )
        }

        var confirmedCount = 0
        var confirmedBytes = 0L
        val failedList = mutableListOf<FailedDeletionItem>()
        val pendingSystemItems = mutableListOf<PhotoEntity>()
        var singleRecoverableSender: IntentSender? = null

        for (candidate in requestedPhotos) {
            when (val verification = identityVerifier.verifyPhotoIdentity(candidate, "permanent_delete")) {
                is IdentityVerificationResult.StaleOrMismatched -> {
                    failedList += FailedDeletionItem(
                        photoId = candidate.id,
                        uriString = candidate.uriString,
                        title = candidate.title,
                        reason = verification.auditEvent.reason
                    )
                }

                is IdentityVerificationResult.Verified -> {
                    val dbPhoto = verification.currentDbPhoto
                    if (dbPhoto.triageStatusEnum != TriageStatus.TRASH_VAULT) {
                        failedList += FailedDeletionItem(
                            photoId = dbPhoto.id,
                            uriString = dbPhoto.uriString,
                            title = dbPhoto.title,
                            reason = "Item is not in Review Bin (status=${dbPhoto.triageStatus})"
                        )
                        continue
                    }

                    when (val sourceAttempt = deletionGateway.attemptDirectSourceDelete(dbPhoto)) {
                        is SourceDeleteAttemptResult.ConfirmedDeleted -> {
                            val deletedRows = dao.permanentlyDeleteVerifiedPhoto(dbPhoto.id, dbPhoto.uriString)
                            if (deletedRows == 1) {
                                MediaThumbnailIdentityCache.evict(dbPhoto.stableIdentityKey)
                                confirmedCount++
                                confirmedBytes += dbPhoto.fileSizeBytes
                            } else {
                                failedList += FailedDeletionItem(
                                    photoId = dbPhoto.id,
                                    uriString = dbPhoto.uriString,
                                    title = dbPhoto.title,
                                    reason = "Database row identity changed before removal"
                                )
                            }
                        }

                        is SourceDeleteAttemptResult.RequiresSystemConfirmation -> {
                            pendingSystemItems += dbPhoto
                            if (sourceAttempt.fallbackIntentSender != null && singleRecoverableSender == null) {
                                singleRecoverableSender = sourceAttempt.fallbackIntentSender
                            }
                        }

                        is SourceDeleteAttemptResult.Failed -> {
                            // Retain Room record because source deletion did not succeed
                            failedList += FailedDeletionItem(
                                photoId = dbPhoto.id,
                                uriString = dbPhoto.uriString,
                                title = dbPhoto.title,
                                reason = sourceAttempt.reason
                            )
                        }
                    }
                }
            }
        }

        if (pendingSystemItems.isNotEmpty()) {
            val uris = pendingSystemItems.mapNotNull { runCatching { Uri.parse(it.uriString) }.getOrNull() }
            val batchSender = deletionGateway.createBatchDeleteIntentSender(uris) ?: singleRecoverableSender
            if (batchSender != null) {
                return@withContext DeletionExecutionOutcome.RequiresSystemDialog(
                    intentSender = batchSender,
                    pendingItems = pendingSystemItems,
                    partialDirectDeletedCount = confirmedCount,
                    partialDirectFreedBytes = confirmedBytes,
                    preFailedItems = failedList
                )
            } else {
                // Could not create OS confirmation intent sender -> retain all pending items in DB
                pendingSystemItems.forEach { pending ->
                    failedList += FailedDeletionItem(
                        photoId = pending.id,
                        uriString = pending.uriString,
                        title = pending.title,
                        reason = "System deletion confirmation unavailable for URI"
                    )
                }
            }
        }

        DeletionExecutionOutcome.Completed(
            DeletionResult(
                requestedCount = requestedPhotos.size,
                confirmedDeletedCount = confirmedCount,
                failedCount = failedList.size,
                confirmedFreedBytes = confirmedBytes,
                failedItems = failedList
            )
        )
    }

    /**
     * Completes a system-confirmed deletion request after the Android OS MediaStore dialog finishes.
     * Re-verifies every item's source URI is actually gone before removing its Room record.
     */
    suspend fun finalizeAfterSystemConfirmation(
        totalRequestedCount: Int,
        pendingItems: List<PhotoEntity>,
        systemDialogConfirmedOk: Boolean,
        partialDirectDeletedCount: Int,
        partialDirectFreedBytes: Long,
        preFailedItems: List<FailedDeletionItem>
    ): DeletionResult = withContext(Dispatchers.IO) {
        var confirmedCount = partialDirectDeletedCount
        var confirmedBytes = partialDirectFreedBytes
        val failedList = preFailedItems.toMutableList()

        if (!systemDialogConfirmedOk) {
            pendingItems.forEach { item ->
                failedList += FailedDeletionItem(
                    photoId = item.id,
                    uriString = item.uriString,
                    title = item.title,
                    reason = "Deletion cancelled or denied in system dialog"
                )
            }
            return@withContext DeletionResult(
                requestedCount = totalRequestedCount,
                confirmedDeletedCount = confirmedCount,
                failedCount = failedList.size,
                confirmedFreedBytes = confirmedBytes,
                failedItems = failedList
            )
        }

        for (item in pendingItems) {
            val uri = runCatching { Uri.parse(item.uriString) }.getOrNull()
            val stillAccessible = uri != null && deletionGateway.isSourceUriStillAccessible(uri)
            if (stillAccessible) {
                // Source media was NOT removed -> retain Room record!
                failedList += FailedDeletionItem(
                    photoId = item.id,
                    uriString = item.uriString,
                    title = item.title,
                    reason = "Source media still present after system dialog"
                )
            } else {
                val rows = dao.permanentlyDeleteVerifiedPhoto(item.id, item.uriString)
                if (rows == 1) {
                    MediaThumbnailIdentityCache.evict(item.stableIdentityKey)
                    confirmedCount++
                    confirmedBytes += item.fileSizeBytes
                } else {
                    failedList += FailedDeletionItem(
                        photoId = item.id,
                        uriString = item.uriString,
                        title = item.title,
                        reason = "Database row changed before final deletion"
                    )
                }
            }
        }

        DeletionResult(
            requestedCount = totalRequestedCount,
            confirmedDeletedCount = confirmedCount,
            failedCount = failedList.size,
            confirmedFreedBytes = confirmedBytes,
            failedItems = failedList
        )
    }
}
