package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AiAuditLogEntity
import com.example.data.local.CleanupRuleEntity
import com.example.data.local.DuplicatePairExclusionEntity
import com.example.data.local.LuminaDao
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.data.security.EncryptedMediaCache
import com.example.domain.ai.AiAdapterConfig
import com.example.domain.ai.AiProviderType
import com.example.domain.ai.ByokAiAdapterLayer
import com.example.domain.rules.NaturalLanguageRuleEngine
import com.example.domain.scanner.AndroidSourceMediaDeletionGateway
import com.example.domain.scanner.DeletionExecutionOutcome
import com.example.domain.scanner.DeletionResult
import com.example.domain.scanner.FailedDeletionItem
import com.example.domain.scanner.IdentityVerificationResult
import com.example.domain.scanner.IncrementalScanCoordinator
import com.example.domain.scanner.MediaDeletionCoordinator
import com.example.domain.scanner.MediaIdentityVerifier
import com.example.domain.scanner.MediaScannerEngine
import com.example.domain.scanner.ScanProgress
import com.example.domain.scanner.SourceMediaDeletionGateway
import com.example.domain.scanner.TriageActionOutcome
import com.example.domain.scanner.VerifiedUndoEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class LuminaRepository(
    private val appContext: Context,
    private val dao: LuminaDao,
    val encryptedCache: EncryptedMediaCache,
    val scannerEngine: MediaScannerEngine,
    val aiAdapterLayer: ByokAiAdapterLayer,
    val identityVerifier: MediaIdentityVerifier = MediaIdentityVerifier(dao),
    deletionGateway: SourceMediaDeletionGateway = AndroidSourceMediaDeletionGateway(appContext)
) {
    val deletionCoordinator: MediaDeletionCoordinator = MediaDeletionCoordinator(
        dao = dao,
        identityVerifier = identityVerifier,
        deletionGateway = deletionGateway
    )

    val incrementalScanCoordinator: IncrementalScanCoordinator = IncrementalScanCoordinator(
        appContext = appContext,
        dao = dao,
        scannerEngine = scannerEngine
    )

    val scanProgressFlow: StateFlow<ScanProgress> = incrementalScanCoordinator.scanProgressFlow

    val allPhotosFlow: Flow<List<PhotoEntity>> = dao.observeAllPhotos()
    val cleanupRulesFlow: Flow<List<CleanupRuleEntity>> = dao.observeCleanupRules()
    val aiAuditLogsFlow: Flow<List<AiAuditLogEntity>> = dao.observeAiAuditLogs()
    val totalProviderTokensFlow: Flow<Long> = dao.observeTotalProviderTokens()

    suspend fun ensureSeededAndScanned() {
        dao.deleteLegacySamplePhotos()
        scannerEngine.cleanupLegacySampleDir(appContext)

        val rules = dao.getCleanupRulesOnce()
        if (rules.isEmpty()) {
            NaturalLanguageRuleEngine.defaultStarterRules().forEach { rule ->
                dao.insertCleanupRule(rule)
            }
        }

        val checkpoint = incrementalScanCoordinator.restoreCheckpointFromDb()
        val shouldResume = checkpoint.isPaused || checkpoint.isActive
        scanFullPhoneMediaLibrary(resumeFromCheckpoint = shouldResume)
    }

    /**
     * Runs the Pass 2 incremental, chunked scanning pipeline over the phone's MediaStore for all
     * accessible Photos & Videos, preserving checkpoints, detecting in-place file revisions, removing
     * confirmed-deleted local source rows, and clustering near-duplicates with staged LSH + BK-Tree indexing.
     */
    suspend fun scanFullPhoneMediaLibrary(resumeFromCheckpoint: Boolean = false): Int {
        val summary = incrementalScanCoordinator.runIncrementalScan(
            resumeFromCheckpoint = resumeFromCheckpoint
        )
        return summary.newItemsAdded.size
    }

    fun pauseIncrementalScan() {
        incrementalScanCoordinator.requestPause()
    }

    fun resumeIncrementalScan() {
        incrementalScanCoordinator.resumePausedScan()
    }

    fun cancelIncrementalScan() {
        incrementalScanCoordinator.requestCancel()
    }

    suspend fun rescanAndClusterLibrary(): Int {
        val newlyAdded = scanFullPhoneMediaLibrary(resumeFromCheckpoint = false)
        val allNow = dao.getAllPhotosOnce()
        if (allNow.isNotEmpty()) {
            val pairExclusions = dao.getAllPairExclusionsOnce().map { it.canonicalPairKey }.toSet()
            val reclustered = scannerEngine.clusterAndNominateBestShots(
                photos = allNow,
                explicitExcludedPairKeys = pairExclusions
            )
            dao.updatePhotos(reclustered)
        }
        return newlyAdded
    }

    suspend fun importPhotosFromPicker(uris: List<Uri>): Int {
        if (uris.isEmpty()) return 0
        val existing = dao.getAllPhotosOnce()
        val combinedClustered = scannerEngine.scanImportedUris(appContext, uris, existing)
        val (newItems, existingUpdated) = combinedClustered.partition { it.id == 0L }

        var insertedCount = 0
        if (newItems.isNotEmpty()) {
            val rowIds = dao.insertPhotosIgnoreConflicts(newItems)
            insertedCount = rowIds.count { it != -1L }
        }
        if (existingUpdated.isNotEmpty()) {
            dao.updatePhotos(existingUpdated)
        }
        return insertedCount
    }

    /**
     * Verifies both `photo.id` and `photo.uriString` (plus revision metadata) against the database row
     * before updating triage status.
     *
     * NEVER falls back to updating by ID alone. If verification fails, no mutation is performed and
     * a non-alarming refresh notice + audit event are returned in [TriageActionOutcome].
     */
    suspend fun triagePhotoVerified(
        photo: PhotoEntity,
        newStatus: TriageStatus
    ): TriageActionOutcome {
        return identityVerifier.applyVerifiedSingleTriage(photo, newStatus)
    }

    /**
     * Batch triage that verifies each PhotoEntity's `(id, uriString)` pair individually.
     * Never falls back to ID-only mutations.
     */
    suspend fun triageBatchVerified(
        photos: List<PhotoEntity>,
        newStatus: TriageStatus,
        actionName: String = "batch_triage_${newStatus.name}"
    ): TriageActionOutcome {
        return identityVerifier.applyVerifiedBatchTriage(photos, newStatus, actionName)
    }

    /**
     * Restores items from Review Bin ONLY after verifying `(id, uriString)` for each item.
     */
    suspend fun restoreVaultItemsVerified(photos: List<PhotoEntity>): TriageActionOutcome {
        return identityVerifier.applyVerifiedBatchTriage(
            photos = photos,
            newStatus = TriageStatus.UNREVIEWED,
            actionName = "restore_from_review_bin"
        )
    }

    /**
     * Applies undo entries ONLY after verifying `(photoId, expectedUri)` for each entry.
     */
    suspend fun undoTriageVerified(entries: List<VerifiedUndoEntry>): TriageActionOutcome {
        return identityVerifier.applyVerifiedUndo(entries)
    }

    /**
     * Executes P0 identity-verified permanent deletion via [MediaDeletionCoordinator].
     * May complete immediately or return [DeletionExecutionOutcome.RequiresSystemDialog] for
     * Android 11+ MediaStore confirmation.
     */
    suspend fun executePermanentDelete(photos: List<PhotoEntity>): DeletionExecutionOutcome {
        return deletionCoordinator.executePermanentDelete(photos)
    }

    /**
     * Finalizes a system-confirmed MediaStore deletion dialog after re-verifying source removal.
     */
    suspend fun finalizeSystemDelete(
        totalRequestedCount: Int,
        pendingItems: List<PhotoEntity>,
        systemDialogConfirmedOk: Boolean,
        partialDirectDeletedCount: Int,
        partialDirectFreedBytes: Long,
        preFailedItems: List<FailedDeletionItem>
    ): DeletionResult {
        return deletionCoordinator.finalizeAfterSystemConfirmation(
            totalRequestedCount = totalRequestedCount,
            pendingItems = pendingItems,
            systemDialogConfirmedOk = systemDialogConfirmedOk,
            partialDirectDeletedCount = partialDirectDeletedCount,
            partialDirectFreedBytes = partialDirectFreedBytes,
            preFailedItems = preFailedItems
        )
    }

    /**
     * Convenience wrapper that executes permanent deletion and returns a structured [DeletionResult].
     * Never deletes Room rows if source media deletion fails or requires an unconfirmed dialog.
     */
    suspend fun permanentlyDeleteVaultItems(photos: List<PhotoEntity>): DeletionResult {
        return when (val outcome = deletionCoordinator.executePermanentDelete(photos)) {
            is DeletionExecutionOutcome.Completed -> outcome.result
            is DeletionExecutionOutcome.RequiresSystemDialog -> {
                val unconfirmedFailures = outcome.pendingItems.map { pending ->
                    FailedDeletionItem(
                        photoId = pending.id,
                        uriString = pending.uriString,
                        title = pending.title,
                        reason = "Requires Android system deletion confirmation dialog"
                    )
                }
                val allFailures = outcome.preFailedItems + unconfirmedFailures
                DeletionResult(
                    requestedCount = photos.size,
                    confirmedDeletedCount = outcome.partialDirectDeletedCount,
                    failedCount = allFailures.size,
                    confirmedFreedBytes = outcome.partialDirectFreedBytes,
                    failedItems = allFailures
                )
            }
        }
    }

    /**
     * Renames a duplicate/similar cluster group across all its verified members.
     */
    suspend fun renameDuplicateCluster(clusterId: String, newTitle: String) {
        val all = dao.getAllPhotosOnce()
        val clusterMembers = all.filter { it.duplicateClusterId == clusterId }
        if (clusterMembers.isEmpty()) return
        val trimmed = newTitle.trim()
        dao.updatePhotos(clusterMembers.map { it.copy(clusterTitleOverride = trimmed) })
    }

    /**
     * Marks a specific item as "Not a Duplicate" after verifying `(id, uriString)` identity,
     * removing it from its cluster and recording pairwise [DuplicatePairExclusionEntity] entries
     * so future rescans never re-group it with any member of that cluster.
     */
    suspend fun markPhotoNotDuplicate(photo: PhotoEntity): Boolean {
        val verification = identityVerifier.verifyPhotoIdentity(photo, "mark_not_duplicate")
        if (verification !is IdentityVerificationResult.Verified) {
            return false
        }
        val dbPhoto = verification.currentDbPhoto
        val oldClusterId = dbPhoto.duplicateClusterId
        val allPhotos = dao.getAllPhotosOnce()
        val siblings = if (!oldClusterId.isNullOrBlank()) {
            allPhotos.filter { it.duplicateClusterId == oldClusterId && it.id != dbPhoto.id }
        } else {
            emptyList()
        }

        // Persist pairwise exclusions in duplicate_pair_exclusions table
        if (siblings.isNotEmpty()) {
            val pairEntities = siblings.map { sibling ->
                val a = minOf(dbPhoto.uriString, sibling.uriString)
                val b = maxOf(dbPhoto.uriString, sibling.uriString)
                DuplicatePairExclusionEntity(
                    uriA = a,
                    uriB = b,
                    clusterId = oldClusterId.orEmpty()
                )
            }
            dao.insertPairExclusions(pairEntities)
        }

        val updatedExcludedCsv = buildSet {
            dbPhoto.excludedClusterKeys.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
            siblings.forEach { add(it.uriString) }
            add("ALL_EXCLUDED")
        }.joinToString(",")

        val updatedPhoto = dbPhoto.copy(
            duplicateClusterId = null,
            isBestShotInCluster = false,
            userSelectedBestShot = false,
            bestShotReason = "",
            clusterType = "",
            clusterConfidence = 0f,
            clusterReason = "",
            excludedClusterKeys = updatedExcludedCsv,
            junkConfidence = minOf(dbPhoto.junkConfidence, 0.20f)
        )
        dao.updatePhoto(updatedPhoto)

        if (!oldClusterId.isNullOrBlank()) {
            if (siblings.size <= 1) {
                dao.updatePhotos(
                    siblings.map {
                        it.copy(
                            duplicateClusterId = null,
                            isBestShotInCluster = false,
                            bestShotReason = "",
                            clusterType = "",
                            clusterConfidence = 0f,
                            clusterReason = ""
                        )
                    }
                )
            } else {
                val pairExclusions = dao.getAllPairExclusionsOnce().map { it.canonicalPairKey }.toSet()
                val reclustered = scannerEngine.clusterAndNominateBestShots(
                    photos = siblings,
                    explicitExcludedPairKeys = pairExclusions
                )
                dao.updatePhotos(reclustered)
            }
        }
        return true
    }

    /**
     * Lets the user manually promote a specific item in a cluster as the "Best Shot" to keep.
     * Sets `userSelectedBestShot = true` on the chosen item so future rescans preserve the user's choice.
     */
    suspend fun selectBestShotInCluster(clusterId: String, chosenBestPhotoId: Long): Boolean {
        val members = dao.getAllPhotosOnce().filter { it.duplicateClusterId == clusterId }
        if (members.size < 2) return false
        val chosen = members.find { it.id == chosenBestPhotoId } ?: return false
        val verification = identityVerifier.verifyPhotoIdentity(chosen, "select_best_shot")
        if (verification !is IdentityVerificationResult.Verified) return false

        val updated = members.map { member ->
            val isBest = member.id == chosen.id && member.uriString == chosen.uriString
            member.copy(
                isBestShotInCluster = isBest,
                userSelectedBestShot = isBest,
                bestShotReason = if (isBest) {
                    "Selected by you as Best Shot"
                } else {
                    "Similar to ${chosen.title}"
                },
                junkConfidence = if (isBest) 0.10f else maxOf(member.junkConfidence, 0.88f)
            )
        }
        dao.updatePhotos(updated)
        return true
    }

    suspend fun inspectPhotoWithByokAi(
        photo: PhotoEntity,
        knownCatalogModels: List<com.example.domain.ai.DiscoveredAiModel> = emptyList()
    ): PhotoEntity? {
        val verification = identityVerifier.verifyPhotoIdentity(photo, "ai_inspect_single")
        if (verification !is IdentityVerificationResult.Verified) {
            return null
        }
        val dbPhoto = verification.currentDbPhoto
        val result = aiAdapterLayer.inspectPhotoWithPolicies(
            photo = dbPhoto,
            requestType = "SINGLE_PHOTO_INSPECTION",
            knownCatalogModels = knownCatalogModels
        )
        val recheck = dao.getVerifiedPhoto(dbPhoto.id, dbPhoto.uriString) ?: return null
        val updated = recheck.copy(
            category = result.category.name,
            screenshotSubType = result.screenshotSubType.name,
            ocrText = result.ocrText,
            aiDescription = result.aiDescription,
            semanticTags = result.semanticTags,
            junkConfidence = result.junkConfidence,
            junkReason = result.junkReason,
            sentimentalProtected = result.sentimentalProtected,
            analyzedByProvider = result.providerUsed
        )
        dao.updatePhoto(updated)
        dao.insertAiAuditLog(result.auditLog)
        return updated
    }

    suspend fun runBatchAiInspection(
        photos: List<PhotoEntity>,
        knownCatalogModels: List<com.example.domain.ai.DiscoveredAiModel> = emptyList()
    ): Int {
        var count = 0
        for (photo in photos.take(12)) {
            val verification = identityVerifier.verifyPhotoIdentity(photo, "ai_inspect_batch")
            if (verification !is IdentityVerificationResult.Verified) continue
            val dbPhoto = verification.currentDbPhoto
            val result = aiAdapterLayer.inspectPhotoWithPolicies(
                photo = dbPhoto,
                requestType = "BATCH_PHOTO_INSPECTION",
                knownCatalogModels = knownCatalogModels
            )
            val recheck = dao.getVerifiedPhoto(dbPhoto.id, dbPhoto.uriString) ?: continue
            val updated = recheck.copy(
                category = result.category.name,
                screenshotSubType = result.screenshotSubType.name,
                ocrText = result.ocrText,
                aiDescription = result.aiDescription,
                semanticTags = result.semanticTags,
                junkConfidence = result.junkConfidence,
                junkReason = result.junkReason,
                sentimentalProtected = result.sentimentalProtected,
                analyzedByProvider = result.providerUsed
            )
            dao.updatePhoto(updated)
            dao.insertAiAuditLog(result.auditLog)
            count++
        }
        return count
    }

    suspend fun addNaturalLanguageRule(prompt: String): CleanupRuleEntity {
        val rule = NaturalLanguageRuleEngine.parseNaturalLanguageToRule(prompt)
        val enriched = rule.copy(
            lastEvaluatedAtEpochMs = System.currentTimeMillis()
        )
        val id = dao.insertCleanupRule(enriched)
        return enriched.copy(id = id)
    }

    suspend fun toggleRule(rule: CleanupRuleEntity) {
        dao.updateCleanupRule(rule.copy(isEnabled = !rule.isEnabled))
    }

    suspend fun deleteRule(ruleId: Long) {
        dao.deleteCleanupRule(ruleId)
    }

    fun loadAiConfig(): AiAdapterConfig = aiAdapterLayer.loadConfig()

    suspend fun saveAiConfig(config: AiAdapterConfig) = aiAdapterLayer.saveConfig(config)

    fun hasSecretKeyFor(provider: AiProviderType): Boolean = aiAdapterLayer.hasConfiguredSecretKey(provider)

    suspend fun fetchAvailableModels(
        rawEndpoint: String,
        provider: AiProviderType,
        apiKeyOverride: String,
        forceRefresh: Boolean = false
    ) = aiAdapterLayer.fetchAvailableModelsFromEndpoint(
        rawEndpoint = rawEndpoint,
        provider = provider,
        apiKeyOverride = apiKeyOverride,
        forceRefresh = forceRefresh
    )

    suspend fun testAiConnection(
        config: AiAdapterConfig,
        catalogOverride: com.example.domain.ai.EndpointModelCatalogResult? = null
    ) = aiAdapterLayer.testConnection(config, catalogOverride)

    suspend fun clearEncryptedAnalysisCache() {
        encryptedCache.clearAnalysisCacheKeepKeys()
    }
}
