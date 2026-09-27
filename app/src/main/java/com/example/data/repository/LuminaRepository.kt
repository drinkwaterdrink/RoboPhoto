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
        // Remove any legacy sample photos from previous builds
        dao.deleteLegacySamplePhotos()
        scannerEngine.cleanupLegacySampleDir(appContext)

        val rules = dao.getCleanupRulesOnce()
        if (rules.isEmpty()) {
            NaturalLanguageRuleEngine.defaultStarterRules().forEach { rule ->
                dao.insertCleanupRule(rule)
            }
        }

        // Automatically scan phone MediaStore (Photos & Videos) if permission is already granted
        scanFullPhoneMediaLibrary()
    }

    /**
     * Scans the phone's MediaStore for all accessible Photos & Videos, runs pixel forensics
     * (dHash, pHash, Laplacian sharpness, exposure), and clusters near-duplicates.
     */
    suspend fun scanFullPhoneMediaLibrary(): Int {
        val existing = dao.getAllPhotosOnce()
        val combinedClustered = scannerEngine.scanDeviceMediaStore(appContext, existing)
        val newItems = combinedClustered.drop(existing.size)
        if (newItems.isNotEmpty()) {
            dao.insertPhotos(newItems)
        }
        val updatedExisting = combinedClustered.take(existing.size)
        if (updatedExisting.isNotEmpty()) {
            dao.updatePhotos(updatedExisting)
        }
        return newItems.size
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
        val newItems = combinedClustered.drop(existing.size)
        if (newItems.isNotEmpty()) {
            dao.insertPhotos(newItems)
        }
        val updatedExisting = combinedClustered.take(existing.size)
        if (updatedExisting.isNotEmpty()) {
            dao.updatePhotos(updatedExisting)
        }
        return newItems.size
    }

    suspend fun triagePhoto(photoId: Long, newStatus: TriageStatus) {
        val vaultedAt = if (newStatus == TriageStatus.TRASH_VAULT) System.currentTimeMillis() else null
        dao.updateTriageStatus(photoId, newStatus.name, vaultedAt)
    }

    suspend fun triageBatch(photoIds: List<Long>, newStatus: TriageStatus) {
        if (photoIds.isEmpty()) return
        val vaultedAt = if (newStatus == TriageStatus.TRASH_VAULT) System.currentTimeMillis() else null
        dao.updateBatchTriageStatus(photoIds, newStatus.name, vaultedAt)
    }

    suspend fun permanentlyDeleteVaultItems(photos: List<PhotoEntity>) {
        if (photos.isEmpty()) return
        photos.forEach { photo ->
            runCatching {
                val uri = Uri.parse(photo.uriString)
                if (uri.scheme == "file") {
                    uri.path?.let { File(it).delete() }
                } else if (uri.scheme == "content") {
                    appContext.contentResolver.delete(uri, null, null)
                }
            }
        }
        dao.permanentlyDeletePhotosByIds(photos.map { it.id })
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
