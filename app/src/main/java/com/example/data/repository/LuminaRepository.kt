package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AiAuditLogEntity
import com.example.data.local.CleanupRuleEntity
import com.example.data.local.LuminaDao
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.data.security.EncryptedMediaCache
import com.example.domain.ai.AiAdapterConfig
import com.example.domain.ai.AiProviderType
import com.example.domain.ai.ByokAiAdapterLayer
import com.example.domain.rules.NaturalLanguageRuleEngine
import com.example.domain.scanner.MediaScannerEngine
import java.io.File
import kotlinx.coroutines.flow.Flow

class LuminaRepository(
    private val appContext: Context,
    private val dao: LuminaDao,
    val encryptedCache: EncryptedMediaCache,
    val scannerEngine: MediaScannerEngine,
    val aiAdapterLayer: ByokAiAdapterLayer
) {
    val allPhotosFlow: Flow<List<PhotoEntity>> = dao.observeAllPhotos()
    val cleanupRulesFlow: Flow<List<CleanupRuleEntity>> = dao.observeCleanupRules()
    val aiAuditLogsFlow: Flow<List<AiAuditLogEntity>> = dao.observeAiAuditLogs()
    val totalApiSpendUsdFlow: Flow<Double> = dao.observeTotalApiSpendUsd()

    suspend fun ensureSeededAndScanned() {
        dao.deleteLegacySamplePhotos()
        scannerEngine.cleanupLegacySampleDir(appContext)

        val rules = dao.getCleanupRulesOnce()
        if (rules.isEmpty()) {
            NaturalLanguageRuleEngine.defaultStarterRules().forEach { rule ->
                dao.insertCleanupRule(rule)
            }
        }

        scanFullPhoneMediaLibrary()
    }

    /**
     * Scans the phone's MediaStore for all accessible Photos & Videos, runs pixel forensics,
     * and clusters near-duplicates.
     *
     * CRITICAL SAFETY INVARIANT:
     * Partitions strictly by `id == 0L` (new items) vs `id > 0L` (existing DB items) and uses
     * `insertPhotosIgnoreConflicts` so existing primary keys and URIs are never shifted or overwritten.
     */
    suspend fun scanFullPhoneMediaLibrary(): Int {
        val existing = dao.getAllPhotosOnce()
        val combinedClustered = scannerEngine.scanDeviceMediaStore(appContext, existing)
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

    suspend fun rescanAndClusterLibrary(): Int {
        val newlyAdded = scanFullPhoneMediaLibrary()
        val allNow = dao.getAllPhotosOnce()
        if (allNow.isNotEmpty()) {
            val reclustered = scannerEngine.clusterAndNominateBestShots(allNow)
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
     * Verifies both `photo.id` and `photo.uriString` against the database row before updating
     * triage status, guaranteeing the displayed item matches the acted-upon record.
     */
    suspend fun triagePhotoVerified(photo: PhotoEntity, newStatus: TriageStatus): Boolean {
        val vaultedAt = if (newStatus == TriageStatus.TRASH_VAULT) System.currentTimeMillis() else null
        val updatedRows = dao.updateTriageStatusVerified(
            photoId = photo.id,
            expectedUri = photo.uriString,
            status = newStatus.name,
            vaultedAt = vaultedAt
        )
        return updatedRows > 0
    }

    suspend fun triagePhoto(photoId: Long, newStatus: TriageStatus) {
        val vaultedAt = if (newStatus == TriageStatus.TRASH_VAULT) System.currentTimeMillis() else null
        dao.updateTriageStatus(photoId, newStatus.name, vaultedAt)
    }

    /**
     * Batch triage that verifies each PhotoEntity's (id, uriString) pair individually.
     */
    suspend fun triageBatchVerified(photos: List<PhotoEntity>, newStatus: TriageStatus): Int {
        if (photos.isEmpty()) return 0
        val vaultedAt = if (newStatus == TriageStatus.TRASH_VAULT) System.currentTimeMillis() else null
        var count = 0
        for (photo in photos) {
            val rows = dao.updateTriageStatusVerified(
                photoId = photo.id,
                expectedUri = photo.uriString,
                status = newStatus.name,
                vaultedAt = vaultedAt
            )
            if (rows > 0) count++
        }
        return count
    }

    suspend fun triageBatch(photoIds: List<Long>, newStatus: TriageStatus) {
        if (photoIds.isEmpty()) return
        val vaultedAt = if (newStatus == TriageStatus.TRASH_VAULT) System.currentTimeMillis() else null
        dao.updateBatchTriageStatus(photoIds, newStatus.name, vaultedAt)
    }

    /**
     * Permanently deletes items from the Review Bin ONLY after verifying that the database record
     * at `photo.id` still has the exact `photo.uriString` and is currently in `TRASH_VAULT`.
     */
    suspend fun permanentlyDeleteVaultItems(photos: List<PhotoEntity>): Int {
        if (photos.isEmpty()) return 0
        var deletedCount = 0
        for (photo in photos) {
            val dbRecord = dao.getPhotoById(photo.id)
            if (dbRecord == null || dbRecord.uriString != photo.uriString) {
                continue
            }
            if (dbRecord.triageStatusEnum != TriageStatus.TRASH_VAULT) {
                continue
            }
            runCatching {
                val uri = Uri.parse(dbRecord.uriString)
                if (uri.scheme == "file") {
                    uri.path?.let { File(it).delete() }
                } else if (uri.scheme == "content") {
                    appContext.contentResolver.delete(uri, null, null)
                }
            }
            val rows = dao.permanentlyDeleteVerifiedPhoto(dbRecord.id, dbRecord.uriString)
            if (rows > 0) {
                deletedCount++
            }
        }
        return deletedCount
    }

    /**
     * Renames a duplicate/similar cluster group across all its members.
     */
    suspend fun renameDuplicateCluster(clusterId: String, newTitle: String) {
        val all = dao.getAllPhotosOnce()
        val clusterMembers = all.filter { it.duplicateClusterId == clusterId }
        if (clusterMembers.isEmpty()) return
        val trimmed = newTitle.trim()
        dao.updatePhotos(clusterMembers.map { it.copy(clusterTitleOverride = trimmed) })
    }

    /**
     * Marks a specific item as "Not a Duplicate", removing it from its cluster and recording an
     * exclusion key so future rescans never re-group it into a duplicate cluster.
     */
    suspend fun markPhotoNotDuplicate(photo: PhotoEntity) {
        val dbPhoto = dao.getPhotoById(photo.id) ?: return
        if (dbPhoto.uriString != photo.uriString) return
        val oldClusterId = dbPhoto.duplicateClusterId
        val updatedPhoto = dbPhoto.copy(
            duplicateClusterId = null,
            isBestShotInCluster = false,
            bestShotReason = "",
            excludedClusterKeys = "ALL_EXCLUDED",
            junkConfidence = minOf(dbPhoto.junkConfidence, 0.20f)
        )
        dao.updatePhoto(updatedPhoto)

        // If the remaining cluster now has fewer than 2 items, dissolve the cluster
        if (!oldClusterId.isNullOrBlank()) {
            val remaining = dao.getAllPhotosOnce().filter {
                it.duplicateClusterId == oldClusterId && it.id != dbPhoto.id
            }
            if (remaining.size <= 1) {
                dao.updatePhotos(
                    remaining.map {
                        it.copy(
                            duplicateClusterId = null,
                            isBestShotInCluster = false,
                            bestShotReason = ""
                        )
                    }
                )
            } else if (remaining.none { it.isBestShotInCluster }) {
                val reclustered = scannerEngine.clusterAndNominateBestShots(remaining)
                dao.updatePhotos(reclustered)
            }
        }
    }

    /**
     * Lets the user manually promote a specific item in a cluster as the "Best Shot" to keep.
     */
    suspend fun selectBestShotInCluster(clusterId: String, chosenBestPhotoId: Long) {
        val members = dao.getAllPhotosOnce().filter { it.duplicateClusterId == clusterId }
        if (members.size < 2) return
        val chosen = members.find { it.id == chosenBestPhotoId } ?: return
        val updated = members.map { member ->
            val isBest = member.id == chosen.id
            member.copy(
                isBestShotInCluster = isBest,
                bestShotReason = if (isBest) {
                    "Selected as Best Shot"
                } else {
                    "Similar to ${chosen.title}"
                },
                junkConfidence = if (isBest) 0.10f else 0.88f
            )
        }
        dao.updatePhotos(updated)
    }

    suspend fun inspectPhotoWithByokAi(photo: PhotoEntity, currentSpendUsd: Double): PhotoEntity {
        val result = aiAdapterLayer.inspectPhotoWithPolicies(photo, currentSpendUsd)
        val updated = photo.copy(
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

    suspend fun runBatchAiInspection(photos: List<PhotoEntity>, currentSpendUsd: Double): Int {
        var runningSpend = currentSpendUsd
        var count = 0
        for (photo in photos.take(12)) {
            val result = aiAdapterLayer.inspectPhotoWithPolicies(photo, runningSpend)
            runningSpend += result.auditLog.estimatedCostUsd
            val updated = photo.copy(
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
        val id = dao.insertCleanupRule(rule)
        return rule.copy(id = id)
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
        apiKeyOverride: String
    ) = aiAdapterLayer.fetchAvailableModelsFromEndpoint(rawEndpoint, provider, apiKeyOverride)

    suspend fun clearEncryptedAnalysisCache() {
        encryptedCache.clearAnalysisCacheKeepKeys()
    }
}
