package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AiAuditLogEntity
import com.example.data.local.CleanupRuleEntity
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.ScreenshotSubType
import com.example.data.local.TriageStatus
import com.example.data.repository.LuminaRepository
import com.example.domain.ai.AiAdapterConfig
import com.example.domain.ai.AiProviderType
import com.example.domain.rules.GoalCleanupPlan
import com.example.domain.rules.NaturalLanguageRuleEngine
import com.example.domain.rules.RuleEvaluationPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppDestination(val route: String, val label: String) {
    QUEUES("queues", "Queues"),
    SWIPE_DECK("swipe_deck", "Swipe Deck"),
    CLUSTERS("clusters", "Clusters"),
    AI_RULES("ai_rules", "AI Rules"),
    TRASH_VAULT("trash_vault", "Vault")
}

data class SmartCleanupBatch(
    val id: String,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val spineColorHex: Long,
    val photos: List<PhotoEntity>,
    val totalBytes: Long,
    val averageConfidence: Float
)

data class DuplicateClusterGroup(
    val clusterId: String,
    val title: String,
    val bestShot: PhotoEntity,
    val redundantVariants: List<PhotoEntity>,
    val allMembers: List<PhotoEntity>,
    val recoverableBytes: Long
)

data class UndoTriageRecord(
    val photoIds: List<Long>,
    val previousStatuses: Map<Long, TriageStatus>,
    val description: String
)

data class SprintSessionState(
    val isActive: Boolean = false,
    val remainingSeconds: Int = 300,
    val targetCount: Int = 15,
    val completedCount: Int = 0,
    val recoveredBytesInSprint: Long = 0L
)

data class LuminaUiState(
    val isScanning: Boolean = false,
    val statusBannerMessage: String? = null,
    val allPhotos: List<PhotoEntity> = emptyList(),
    val activePhotos: List<PhotoEntity> = emptyList(),
    val vaultPhotos: List<PhotoEntity> = emptyList(),
    val smartBatches: List<SmartCleanupBatch> = emptyList(),
    val duplicateClusters: List<DuplicateClusterGroup> = emptyList(),
    val cleanupRules: List<CleanupRuleEntity> = emptyList(),
    val ruleEvaluations: List<RuleEvaluationPreview> = emptyList(),
    val aiAuditLogs: List<AiAuditLogEntity> = emptyList(),
    val totalApiSpendUsd: Double = 0.0,
    val aiConfig: AiAdapterConfig = AiAdapterConfig(),
    val encryptedCacheEntries: Int = 0,
    val encryptedCacheHitRate: Int = 96,
    val encryptedCacheSizeBytes: Long = 0L,
    val searchQuery: String = "",
    val selectedCategoryFilter: PhotoCategory? = null,
    val activeQueueIdForSwipe: String? = null,
    val lastUndoRecord: UndoTriageRecord? = null,
    val sprintState: SprintSessionState = SprintSessionState(),
    val goalTargetMegabytes: Int = 28,
    val currentGoalPlan: GoalCleanupPlan? = null
) {
    val totalLibraryBytes: Long
        get() = allPhotos.sumOf { it.fileSizeBytes }

    val vaultRecoverableBytes: Long
        get() = vaultPhotos.sumOf { it.fileSizeBytes }

    val potentialQueueSavingsBytes: Long
        get() = activePhotos
            .filter { it.junkConfidence >= 0.65f && !it.isBestShotInCluster && !it.sentimentalProtected }
            .sumOf { it.fileSizeBytes }
}

class LuminaViewModel(
    private val repository: LuminaRepository
) : ViewModel() {

    private val _currentDestination = MutableStateFlow(AppDestination.QUEUES)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<PhotoCategory?>(null)
    private val _activeSwipeQueueId = MutableStateFlow<String?>(null)
    private val _aiConfig = MutableStateFlow(repository.loadAiConfig())
    private val _lastUndo = MutableStateFlow<UndoTriageRecord?>(null)
    private val _sprintState = MutableStateFlow(SprintSessionState())
    private val _goalTargetMb = MutableStateFlow(28)

    private var sprintTimerJob: Job? = null

    private data class CoreFlowsBundle(
        val photos: List<PhotoEntity>,
        val rules: List<CleanupRuleEntity>,
        val logs: List<AiAuditLogEntity>,
        val spend: Double
    )

    private data class FilterStateBundle(
        val isScanning: Boolean,
        val banner: String?,
        val query: String,
        val category: PhotoCategory?,
        val swipeQueueId: String?,
        val aiCfg: AiAdapterConfig,
        val undo: UndoTriageRecord?,
        val sprint: SprintSessionState,
        val goalMb: Int
    )

    private val coreBundleFlow = combine(
        repository.allPhotosFlow,
        repository.cleanupRulesFlow,
        repository.aiAuditLogsFlow,
        repository.totalApiSpendUsdFlow
    ) { photos, rules, logs, spend ->
        CoreFlowsBundle(photos, rules, logs, spend)
    }

    private val filterBundleFlow = combine(
        combine(_isScanning, _statusMessage, _searchQuery) { a, b, c -> Triple(a, b, c) },
        combine(_selectedCategory, _activeSwipeQueueId, _aiConfig) { d, e, f -> Triple(d, e, f) },
        combine(_lastUndo, _sprintState, _goalTargetMb) { g, h, i -> Triple(g, h, i) }
    ) { first, second, third ->
        FilterStateBundle(
            isScanning = first.first,
            banner = first.second,
            query = first.third,
            category = second.first,
            swipeQueueId = second.second,
            aiCfg = second.third,
            undo = third.first,
            sprint = third.second,
            goalMb = third.third
        )
    }

    val uiState: StateFlow<LuminaUiState> = combine(
        coreBundleFlow,
        filterBundleFlow
    ) { core, filter ->
        val activePhotos = core.photos.filter { it.triageStatusEnum != TriageStatus.TRASH_VAULT }
        val vaultPhotos = core.photos.filter { it.triageStatusEnum == TriageStatus.TRASH_VAULT }

        val smartBatches = buildSmartBatches(activePhotos)
        val duplicateClusters = buildDuplicateClusters(activePhotos)
        val ruleEvaluations = core.rules.map { rule ->
            NaturalLanguageRuleEngine.evaluateRule(rule, core.photos)
        }
        val goalPlan = NaturalLanguageRuleEngine.buildCleanupGoalPlan(
            targetBytes = filter.goalMb * 1_000_000L,
            allPhotos = core.photos
        )

        LuminaUiState(
            isScanning = filter.isScanning,
            statusBannerMessage = filter.banner,
            allPhotos = core.photos,
            activePhotos = activePhotos,
            vaultPhotos = vaultPhotos,
            smartBatches = smartBatches,
            duplicateClusters = duplicateClusters,
            cleanupRules = core.rules,
            ruleEvaluations = ruleEvaluations,
            aiAuditLogs = core.logs,
            totalApiSpendUsd = core.spend,
            aiConfig = filter.aiCfg,
            encryptedCacheEntries = repository.encryptedCache.entryCount(),
            encryptedCacheHitRate = repository.encryptedCache.hitRatePercent(),
            encryptedCacheSizeBytes = repository.encryptedCache.encryptedSizeBytes(),
            searchQuery = filter.query,
            selectedCategoryFilter = filter.category,
            activeQueueIdForSwipe = filter.swipeQueueId,
            lastUndoRecord = filter.undo,
            sprintState = filter.sprint,
            goalTargetMegabytes = filter.goalMb,
            currentGoalPlan = goalPlan
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LuminaUiState(isScanning = true)
    )

    init {
        viewModelScope.launch {
            _isScanning.value = true
            repository.ensureSeededAndScanned()
            _isScanning.value = false
        }
    }

    fun navigateTo(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun openQueueInSwipeDeck(queueId: String?) {
        _activeSwipeQueueId.value = queueId
        _currentDestination.value = AppDestination.SWIPE_DECK
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategoryFilter(category: PhotoCategory?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun dismissBanner() {
        _statusMessage.value = null
    }

    fun triggerLibraryRescan() {
        viewModelScope.launch {
            _isScanning.value = true
            _statusMessage.value = "Scanning phone photos & videos (dHash/pHash, Laplacian sharpness & clustering)..."
            delay(250)
            val newCount = repository.rescanAndClusterLibrary()
            _isScanning.value = false
            val totalNow = uiState.value.allPhotos.size
            _statusMessage.value = if (newCount > 0) {
                "Indexed $newCount new photo(s)/video(s) • $totalNow total in library."
            } else if (totalNow > 0) {
                "Forensics scan complete ($totalNow items) • Cache hit rate ${repository.encryptedCache.hitRatePercent()}%"
            } else {
                "No local media found yet. Tap 'Scan Phone' to grant photo/video access or 'Google Photos' to pick cloud media."
            }
        }
    }

    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isScanning.value = true
            _statusMessage.value = "Analyzing ${uris.size} photo(s)/video(s) with on-device dHash & Laplacian variance..."
            val count = repository.importPhotosFromPicker(uris)
            _isScanning.value = false
            _statusMessage.value = "Imported & clustered $count photo(s)/video(s) into local database."
        }
    }

    fun swipeTriagePhoto(photo: PhotoEntity, targetStatus: TriageStatus) {
        viewModelScope.launch {
            val prevStatus = photo.triageStatusEnum
            repository.triagePhoto(photo.id, targetStatus)
            _lastUndo.value = UndoTriageRecord(
                photoIds = listOf(photo.id),
                previousStatuses = mapOf(photo.id to prevStatus),
                description = "${photo.title} → ${targetStatus.label}"
            )

            if (_sprintState.value.isActive) {
                val cur = _sprintState.value
                val addedBytes = if (targetStatus == TriageStatus.TRASH_VAULT) photo.fileSizeBytes else 0L
                val nextCount = cur.completedCount + 1
                _sprintState.value = cur.copy(
                    completedCount = nextCount,
                    recoveredBytesInSprint = cur.recoveredBytesInSprint + addedBytes,
                    isActive = nextCount < cur.targetCount
                )
                if (nextCount >= cur.targetCount) {
                    sprintTimerJob?.cancel()
                    _statusMessage.value = "5-Minute Cleanup Sprint Complete! Reviewed $nextCount photos."
                }
            }
        }
    }

    fun batchMoveToVault(photos: List<PhotoEntity>, reasonLabel: String) {
        if (photos.isEmpty()) return
        viewModelScope.launch {
            val prevMap = photos.associate { it.id to it.triageStatusEnum }
            repository.triageBatch(photos.map { it.id }, TriageStatus.TRASH_VAULT)
            _lastUndo.value = UndoTriageRecord(
                photoIds = photos.map { it.id },
                previousStatuses = prevMap,
                description = "$reasonLabel (${photos.size} items moved to Quarantine Vault)"
            )
            _statusMessage.value = "Moved ${photos.size} items to reversible Quarantine Vault."
        }
    }

    fun undoLastTriage() {
        val record = _lastUndo.value ?: return
        viewModelScope.launch {
            for ((photoId, prevStatus) in record.previousStatuses) {
                repository.triagePhoto(photoId, prevStatus)
            }
            _lastUndo.value = null
            _statusMessage.value = "Undid: ${record.description}"
        }
    }

    fun restoreFromVault(photoIds: List<Long>) {
        if (photoIds.isEmpty()) return
        viewModelScope.launch {
            repository.triageBatch(photoIds, TriageStatus.UNREVIEWED)
            _statusMessage.value = "Restored ${photoIds.size} item(s) from Quarantine Vault."
        }
    }

    fun confirmTwoStepPermanentDelete(vaultPhotos: List<PhotoEntity>) {
        if (vaultPhotos.isEmpty()) return
        viewModelScope.launch {
            val bytes = vaultPhotos.sumOf { it.fileSizeBytes }
            val count = vaultPhotos.size
            repository.permanentlyDeleteVaultItems(vaultPhotos)
            _statusMessage.value = "Permanently purged $count item(s) • Freed ${formatBytes(bytes)}."
        }
    }

    fun startFiveMinuteSprint() {
        sprintTimerJob?.cancel()
        val unreviewedCount = uiState.value.activePhotos.count { it.triageStatusEnum == TriageStatus.UNREVIEWED }
        val target = minOf(50, maxOf(5, unreviewedCount))
        _sprintState.value = SprintSessionState(
            isActive = true,
            remainingSeconds = 300,
            targetCount = target,
            completedCount = 0,
            recoveredBytesInSprint = 0L
        )
        _activeSwipeQueueId.value = null
        _currentDestination.value = AppDestination.SWIPE_DECK

        sprintTimerJob = viewModelScope.launch {
            while (_sprintState.value.isActive && _sprintState.value.remainingSeconds > 0) {
                delay(1000L)
                val cur = _sprintState.value
                if (!cur.isActive) break
                _sprintState.value = cur.copy(remainingSeconds = (cur.remainingSeconds - 1).coerceAtLeast(0))
            }
        }
    }

    fun stopSprintSession() {
        sprintTimerJob?.cancel()
        _sprintState.value = _sprintState.value.copy(isActive = false)
    }

    fun updateGoalTargetMb(megabytes: Int) {
        _goalTargetMb.value = megabytes.coerceIn(5, 250)
    }

    fun createNaturalLanguageRule(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            val created = repository.addNaturalLanguageRule(prompt)
            _statusMessage.value = "Created AI Rule: ${created.title}"
        }
    }

    fun toggleCleanupRule(rule: CleanupRuleEntity) {
        viewModelScope.launch {
            repository.toggleRule(rule)
        }
    }

    fun deleteCleanupRule(ruleId: Long) {
        viewModelScope.launch {
            repository.deleteRule(ruleId)
        }
    }

    fun updateAiAdapterConfig(newConfig: AiAdapterConfig) {
        viewModelScope.launch {
            repository.saveAiConfig(newConfig)
            _aiConfig.value = newConfig
            _statusMessage.value = "Activated BYOK Profile: ${newConfig.activeProfileName} (${newConfig.selectedModel})"
        }
    }

    fun inspectSinglePhotoWithAi(photo: PhotoEntity) {
        viewModelScope.launch {
            _isScanning.value = true
            val updated = repository.inspectPhotoWithByokAi(photo, uiState.value.totalApiSpendUsd)
            _isScanning.value = false
            _statusMessage.value = "Inspected ${updated.title} via ${updated.analyzedByProvider}"
        }
    }

    fun runBatchByokAiPass() {
        viewModelScope.launch {
            val unreviewed = uiState.value.activePhotos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }
            if (unreviewed.isEmpty()) return@launch
            _isScanning.value = true
            _statusMessage.value = "Running BYOK AI & Encrypted Cache inspection on ${unreviewed.size} items..."
            val count = repository.runBatchAiInspection(unreviewed, uiState.value.totalApiSpendUsd)
            _isScanning.value = false
            _statusMessage.value = "Analyzed $count photos (${repository.encryptedCache.hitRatePercent()}% encrypted cache hit rate)."
        }
    }

    fun clearEncryptedCache() {
        viewModelScope.launch {
            repository.clearEncryptedAnalysisCache()
            _statusMessage.value = "Cleared encrypted analysis cache (API keys & rules preserved)."
        }
    }

    fun hasConfiguredKey(provider: AiProviderType): Boolean {
        return repository.hasSecretKeyFor(provider)
    }

    suspend fun fetchEndpointModels(
        rawEndpoint: String,
        provider: AiProviderType,
        apiKeyOverride: String
    ): com.example.domain.ai.EndpointModelCatalogResult {
        return repository.fetchAvailableModels(rawEndpoint, provider, apiKeyOverride)
    }

    private fun buildSmartBatches(activePhotos: List<PhotoEntity>): List<SmartCleanupBatch> {
        val unreviewed = activePhotos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }

        val oldScreenshots = unreviewed.filter {
            (it.categoryEnum == PhotoCategory.SCREENSHOT || it.categoryEnum == PhotoCategory.MEME) &&
                it.ageInDays >= 90 &&
                !it.screenshotSubTypeEnum.isImportantDefault
        }

        val blurryPhotos = unreviewed.filter {
            it.categoryEnum == PhotoCategory.BLURRY || it.sharpnessScore < 42
        }

        val duplicateExtras = unreviewed.filter {
            it.duplicateClusterId != null && !it.isBestShotInCluster
        }

        val videoItems = unreviewed.filter {
            it.isVideo || it.categoryEnum == PhotoCategory.VIDEO
        }

        val receiptsAndConfirmations = activePhotos.filter {
            it.categoryEnum == PhotoCategory.RECEIPT ||
                it.categoryEnum == PhotoCategory.DOCUMENT ||
                it.screenshotSubTypeEnum.isImportantDefault
        }

        return listOfNotNull(
            SmartCleanupBatch(
                id = "queue_videos",
                title = "${videoItems.size} videos consuming high storage",
                subtitle = "Video clips & screen recordings • Review keyframes & duration",
                badgeText = "High Storage Impact",
                spineColorHex = 0xFFEC4899,
                photos = videoItems,
                totalBytes = videoItems.sumOf { it.fileSizeBytes },
                averageConfidence = if (videoItems.isEmpty()) 0.72f else videoItems.map { it.junkConfidence }.average().toFloat()
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_old_screenshots",
                title = "${oldScreenshots.size} screenshots probably no longer useful",
                subtitle = "Older than 90 days • Excludes receipts, passwords, addresses & confirmations",
                badgeText = "High Confidence",
                spineColorHex = 0xFF06B6D4,
                photos = oldScreenshots,
                totalBytes = oldScreenshots.sumOf { it.fileSizeBytes },
                averageConfidence = if (oldScreenshots.isEmpty()) 0.92f else oldScreenshots.map { it.junkConfidence }.average().toFloat()
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_duplicates",
                title = "${duplicateExtras.size} near-duplicate burst extras",
                subtitle = "64-bit dHash/pHash matched • Sharpest Best-Shot automatically locked",
                badgeText = "Best-Shot Safe",
                spineColorHex = 0xFFF59E0B,
                photos = duplicateExtras,
                totalBytes = duplicateExtras.sumOf { it.fileSizeBytes },
                averageConfidence = 0.89f
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_blurry",
                title = "${blurryPhotos.size} blurry & misfocused photos",
                subtitle = "Laplacian variance sharpness < 42/100 • Motion blur or pocket triggers",
                badgeText = "96% Junk Confidence",
                spineColorHex = 0xFFF43F5E,
                photos = blurryPhotos,
                totalBytes = blurryPhotos.sumOf { it.fileSizeBytes },
                averageConfidence = 0.96f
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_receipts_protected",
                title = "${receiptsAndConfirmations.size} receipts, credentials & confirmations",
                subtitle = "OCR detected invoices, serial numbers, passwords & booking codes",
                badgeText = "Protected Vault",
                spineColorHex = 0xFF10B981,
                photos = receiptsAndConfirmations,
                totalBytes = receiptsAndConfirmations.sumOf { it.fileSizeBytes },
                averageConfidence = 0.12f
            ).takeIf { it.photos.isNotEmpty() }
        )
    }

    private fun buildDuplicateClusters(activePhotos: List<PhotoEntity>): List<DuplicateClusterGroup> {
        return activePhotos
            .filter { !it.duplicateClusterId.isNullOrBlank() }
            .groupBy { it.duplicateClusterId!! }
            .values
            .filter { it.size > 1 }
            .map { members ->
                val sorted = members.sortedByDescending {
                    if (it.isBestShotInCluster) 10_000 else it.overallQualityScore
                }
                val best = sorted.first()
                val redundant = sorted.drop(1)
                val clusterTitle = when {
                    best.categoryEnum == PhotoCategory.PLANT -> "August Botanical Plant Burst (${members.size} shots)"
                    best.categoryEnum == PhotoCategory.PET -> "Golden Retriever Outdoor Burst (${members.size} shots)"
                    best.categoryEnum == PhotoCategory.LANDSCAPE -> "Golden Hour Alpine Lake Burst (${members.size} shots)"
                    else -> "${best.categoryEnum.label} Similarity Cluster (${members.size} shots)"
                }
                DuplicateClusterGroup(
                    clusterId = best.duplicateClusterId ?: "cluster",
                    title = clusterTitle,
                    bestShot = best,
                    redundantVariants = redundant,
                    allMembers = sorted,
                    recoverableBytes = redundant.sumOf { it.fileSizeBytes }
                )
            }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 MB"
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
            } else {
                String.format(java.util.Locale.US, "%.1f MB", mb)
            }
        }

        fun provideFactory(repository: LuminaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LuminaViewModel(repository) as T
                }
            }
    }
}
