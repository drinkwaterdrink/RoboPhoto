package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AiAuditLogEntity
import com.example.data.local.CleanupRuleEntity
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
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
    QUEUES("queues", "Home"),
    SWIPE_DECK("swipe_deck", "Review"),
    CLUSTERS("clusters", "Collections"),
    AI_RULES("ai_rules", "Automations"),
    TRASH_VAULT("trash_vault", "Review Bin")
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
    val skippedPhotoIds: Set<Long> = emptySet(),
    val lastUndoRecord: UndoTriageRecord? = null,
    val sprintState: SprintSessionState = SprintSessionState(),
    val goalTargetMegabytes: Int = 50,
    val currentGoalPlan: GoalCleanupPlan? = null,
    val sessionSavedBytes: Long = 0L,
    val sessionReviewedCount: Int = 0
) {
    val totalLibraryBytes: Long
        get() = allPhotos.sumOf { it.fileSizeBytes }

    val vaultRecoverableBytes: Long
        get() = vaultPhotos.sumOf { it.fileSizeBytes }

    val potentialQueueSavingsBytes: Long
        get() = activePhotos
            .filter {
                (it.junkConfidence >= 0.60f || (it.duplicateClusterId != null && !it.isBestShotInCluster)) &&
                    !it.isBestShotInCluster &&
                    !it.sentimentalProtected
            }
            .sumOf { it.fileSizeBytes }

    val totalCleanableBytes: Long
        get() = potentialQueueSavingsBytes + vaultRecoverableBytes

    val unreviewedCount: Int
        get() = activePhotos.count { it.triageStatusEnum == TriageStatus.UNREVIEWED }
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
    private val _skippedPhotoIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _aiConfig = MutableStateFlow(repository.loadAiConfig())
    private val _lastUndo = MutableStateFlow<UndoTriageRecord?>(null)
    private val _sprintState = MutableStateFlow(SprintSessionState())
    private val _goalTargetMb = MutableStateFlow(50)
    private val _sessionSavedBytes = MutableStateFlow(0L)
    private val _sessionReviewedCount = MutableStateFlow(0)

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
        val skippedIds: Set<Long>,
        val aiCfg: AiAdapterConfig,
        val undo: UndoTriageRecord?,
        val sprint: SprintSessionState,
        val goalMb: Int,
        val sessionSaved: Long,
        val sessionReviewed: Int
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
        combine(_isScanning, _statusMessage, _searchQuery, _selectedCategory) { a, b, c, d ->
            listOf(a, b, c, d)
        },
        combine(_activeSwipeQueueId, _skippedPhotoIds, _aiConfig, _lastUndo) { e, f, g, h ->
            listOf(e, f, g, h)
        },
        combine(_sprintState, _goalTargetMb, _sessionSavedBytes, _sessionReviewedCount) { i, j, k, l ->
            listOf(i, j, k, l)
        }
    ) { first, second, third ->
        @Suppress("UNCHECKED_CAST")
        FilterStateBundle(
            isScanning = first[0] as Boolean,
            banner = first[1] as String?,
            query = first[2] as String,
            category = first[3] as PhotoCategory?,
            swipeQueueId = second[0] as String?,
            skippedIds = second[1] as Set<Long>,
            aiCfg = second[2] as AiAdapterConfig,
            undo = second[3] as UndoTriageRecord?,
            sprint = third[0] as SprintSessionState,
            goalMb = third[1] as Int,
            sessionSaved = third[2] as Long,
            sessionReviewed = third[3] as Int
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
            skippedPhotoIds = filter.skippedIds,
            lastUndoRecord = filter.undo,
            sprintState = filter.sprint,
            goalTargetMegabytes = filter.goalMb,
            currentGoalPlan = goalPlan,
            sessionSavedBytes = filter.sessionSaved,
            sessionReviewedCount = filter.sessionReviewed
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
        _skippedPhotoIds.value = emptySet()
        _currentDestination.value = AppDestination.SWIPE_DECK
    }

    fun openCategoryInCollections(category: PhotoCategory?) {
        _selectedCategory.value = category
        _currentDestination.value = AppDestination.CLUSTERS
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
            _statusMessage.value = "Scanning phone photos & videos..."
            delay(200)
            val newCount = repository.rescanAndClusterLibrary()
            _isScanning.value = false
            val totalNow = uiState.value.allPhotos.size
            _statusMessage.value = if (newCount > 0) {
                "Added $newCount new item(s) • $totalNow total in library."
            } else if (totalNow > 0) {
                "Library up to date ($totalNow items)."
            } else {
                "Grant photo & video permission or pick from Google Photos to begin."
            }
        }
    }

    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isScanning.value = true
            _statusMessage.value = "Importing ${uris.size} item(s)..."
            val count = repository.importPhotosFromPicker(uris)
            _isScanning.value = false
            _statusMessage.value = "Imported $count photo(s)/video(s)."
        }
    }

    fun skipPhotoInReview(photo: PhotoEntity) {
        _skippedPhotoIds.value = _skippedPhotoIds.value + photo.id
    }

    fun clearSkippedPhotos() {
        _skippedPhotoIds.value = emptySet()
    }

    /**
     * Identity-verified swipe triage: verifies both `photo.id` and `photo.uriString` before updating
     * the database so the item shown on the card is guaranteed to be the item acted upon.
     */
    fun swipeTriagePhoto(photo: PhotoEntity, targetStatus: TriageStatus) {
        viewModelScope.launch {
            val prevStatus = photo.triageStatusEnum
            val verified = repository.triagePhotoVerified(photo, targetStatus)
            if (!verified) {
                repository.triagePhoto(photo.id, targetStatus)
            }

            _sessionReviewedCount.value += 1
            if (targetStatus == TriageStatus.TRASH_VAULT) {
                _sessionSavedBytes.value += photo.fileSizeBytes
            }

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
                    _statusMessage.value = "Cleanup Sprint Complete! Reviewed $nextCount items."
                }
            }
        }
    }

    fun batchMoveToVault(photos: List<PhotoEntity>, reasonLabel: String) {
        if (photos.isEmpty()) return
        viewModelScope.launch {
            val prevMap = photos.associate { it.id to it.triageStatusEnum }
            val movedCount = repository.triageBatchVerified(photos, TriageStatus.TRASH_VAULT)
            val movedBytes = photos.sumOf { it.fileSizeBytes }
            _sessionSavedBytes.value += movedBytes
            _sessionReviewedCount.value += movedCount
            _lastUndo.value = UndoTriageRecord(
                photoIds = photos.map { it.id },
                previousStatuses = prevMap,
                description = "$reasonLabel ($movedCount moved to Review Bin)"
            )
            _statusMessage.value = "Moved $movedCount item(s) (${formatBytes(movedBytes)}) to Review Bin."
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
            _statusMessage.value = "Restored ${photoIds.size} item(s) from Review Bin."
        }
    }

    fun confirmTwoStepPermanentDelete(vaultPhotos: List<PhotoEntity>) {
        if (vaultPhotos.isEmpty()) return
        viewModelScope.launch {
            val bytes = vaultPhotos.sumOf { it.fileSizeBytes }
            val deletedCount = repository.permanentlyDeleteVaultItems(vaultPhotos)
            _statusMessage.value = "Permanently deleted $deletedCount item(s) • Freed ${formatBytes(bytes)}."
        }
    }

    fun renameDuplicateCluster(clusterId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameDuplicateCluster(clusterId, newTitle)
            _statusMessage.value = "Renamed group to \"${newTitle.trim()}\""
        }
    }

    fun markPhotoNotDuplicate(photo: PhotoEntity) {
        viewModelScope.launch {
            repository.markPhotoNotDuplicate(photo)
            _statusMessage.value = "Removed \"${photo.title}\" from duplicate group."
        }
    }

    fun selectBestShotInCluster(clusterId: String, photoId: Long) {
        viewModelScope.launch {
            repository.selectBestShotInCluster(clusterId, photoId)
            _statusMessage.value = "Updated Best Shot selection."
        }
    }

    fun startFiveMinuteSprint() {
        sprintTimerJob?.cancel()
        val unreviewedCount = uiState.value.activePhotos.count { it.triageStatusEnum == TriageStatus.UNREVIEWED }
        val target = minOf(30, maxOf(5, unreviewedCount))
        _sprintState.value = SprintSessionState(
            isActive = true,
            remainingSeconds = 300,
            targetCount = target,
            completedCount = 0,
            recoveredBytesInSprint = 0L
        )
        _activeSwipeQueueId.value = null
        _skippedPhotoIds.value = emptySet()
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
        _goalTargetMb.value = megabytes.coerceIn(5, 500)
    }

    fun createNaturalLanguageRule(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            val created = repository.addNaturalLanguageRule(prompt)
            _statusMessage.value = "Created automation: ${created.title}"
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
            _statusMessage.value = "Saved AI profile: ${newConfig.activeProfileName} (${newConfig.selectedModel})"
        }
    }

    fun inspectSinglePhotoWithAi(photo: PhotoEntity) {
        viewModelScope.launch {
            _isScanning.value = true
            val updated = repository.inspectPhotoWithByokAi(photo, uiState.value.totalApiSpendUsd)
            _isScanning.value = false
            _statusMessage.value = "Analyzed ${updated.title}"
        }
    }

    fun runBatchByokAiPass() {
        viewModelScope.launch {
            val unreviewed = uiState.value.activePhotos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }
            if (unreviewed.isEmpty()) return@launch
            _isScanning.value = true
            _statusMessage.value = "Running AI analysis on ${minOf(12, unreviewed.size)} items..."
            val count = repository.runBatchAiInspection(unreviewed, uiState.value.totalApiSpendUsd)
            _isScanning.value = false
            _statusMessage.value = "AI analysis complete for $count items."
        }
    }

    fun clearEncryptedCache() {
        viewModelScope.launch {
            repository.clearEncryptedAnalysisCache()
            _statusMessage.value = "Cleared analysis cache (API keys & rules preserved)."
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

        val duplicateExtras = unreviewed.filter {
            it.duplicateClusterId != null && !it.isBestShotInCluster
        }

        val screenshots = unreviewed.filter {
            (it.categoryEnum == PhotoCategory.SCREENSHOT || it.categoryEnum == PhotoCategory.MEME) &&
                !it.screenshotSubTypeEnum.isImportantDefault
        }

        val blurryPhotos = unreviewed.filter {
            !it.isVideo && (it.categoryEnum == PhotoCategory.BLURRY || it.sharpnessScore < 42)
        }

        val videoItems = unreviewed.filter {
            it.isVideo || it.categoryEnum == PhotoCategory.VIDEO
        }.sortedByDescending { it.fileSizeBytes }

        val downloadsAndDocs = activePhotos.filter {
            it.categoryEnum == PhotoCategory.DOWNLOAD ||
                it.categoryEnum == PhotoCategory.RECEIPT ||
                it.categoryEnum == PhotoCategory.DOCUMENT ||
                it.screenshotSubTypeEnum.isImportantDefault
        }

        return listOfNotNull(
            SmartCleanupBatch(
                id = "queue_duplicates",
                title = "Duplicates & Similar",
                subtitle = "${duplicateExtras.size} extra shots • Best shots safely kept",
                badgeText = formatBytes(duplicateExtras.sumOf { it.fileSizeBytes }),
                spineColorHex = 0xFF168BFF,
                photos = duplicateExtras,
                totalBytes = duplicateExtras.sumOf { it.fileSizeBytes },
                averageConfidence = 0.90f
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_old_screenshots",
                title = "Screenshots",
                subtitle = "${screenshots.size} screen captures ready to clean",
                badgeText = formatBytes(screenshots.sumOf { it.fileSizeBytes }),
                spineColorHex = 0xFF38BDF8,
                photos = screenshots,
                totalBytes = screenshots.sumOf { it.fileSizeBytes },
                averageConfidence = 0.88f
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_videos",
                title = "Large Videos",
                subtitle = "${videoItems.size} video clips sorted by size",
                badgeText = formatBytes(videoItems.sumOf { it.fileSizeBytes }),
                spineColorHex = 0xFF818CF8,
                photos = videoItems,
                totalBytes = videoItems.sumOf { it.fileSizeBytes },
                averageConfidence = 0.76f
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_blurry",
                title = "Blurry Photos",
                subtitle = "${blurryPhotos.size} low-sharpness or misfocused shots",
                badgeText = formatBytes(blurryPhotos.sumOf { it.fileSizeBytes }),
                spineColorHex = 0xFFE5484D,
                photos = blurryPhotos,
                totalBytes = blurryPhotos.sumOf { it.fileSizeBytes },
                averageConfidence = 0.94f
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_receipts_protected",
                title = "Documents & Downloads",
                subtitle = "${downloadsAndDocs.size} receipts, documents & saved files",
                badgeText = formatBytes(downloadsAndDocs.sumOf { it.fileSizeBytes }),
                spineColorHex = 0xFF10B981,
                photos = downloadsAndDocs,
                totalBytes = downloadsAndDocs.sumOf { it.fileSizeBytes },
                averageConfidence = 0.15f
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
                val sorted = members.sortedWith(
                    compareByDescending<PhotoEntity> { if (it.isBestShotInCluster) 1 else 0 }
                        .thenByDescending { it.overallQualityScore }
                        .thenByDescending { it.sharpnessScore }
                )
                val best = sorted.first()
                val redundant = sorted.drop(1)
                val customTitle = members.firstNotNullOfOrNull {
                    it.clusterTitleOverride.takeIf { t -> t.isNotBlank() }
                }
                val dateSuffix = best.shortDateLabel.let { if (it.isNotBlank()) " • $it" else "" }
                val defaultTitle = when {
                    best.isVideo -> "Similar Videos$dateSuffix (${members.size})"
                    best.categoryEnum == PhotoCategory.SCREENSHOT -> "Similar Screenshots$dateSuffix (${members.size})"
                    else -> "${best.folderName.ifBlank { "Photo" }} Burst$dateSuffix (${members.size})"
                }
                DuplicateClusterGroup(
                    clusterId = best.duplicateClusterId ?: "cluster",
                    title = customTitle ?: defaultTitle,
                    bestShot = best,
                    redundantVariants = redundant,
                    allMembers = sorted,
                    recoverableBytes = redundant.sumOf { it.fileSizeBytes }
                )
            }
            .sortedByDescending { it.recoverableBytes }
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
