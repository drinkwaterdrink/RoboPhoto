package com.example.ui.viewmodel

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AiAuditLogEntity
import com.example.data.local.CleanupRuleEntity
import com.example.data.local.MediaClusterType
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.data.repository.LuminaRepository
import com.example.domain.ai.AiAdapterConfig
import com.example.domain.ai.AiConnectionTestResult
import com.example.domain.ai.AiHomeSuggestion
import com.example.domain.ai.AiPrivacyPreview
import com.example.domain.ai.AiProviderType
import com.example.domain.ai.AiUsageSummary
import com.example.domain.ai.AiWorkflowsAndUsageEngine
import com.example.domain.ai.NaturalLanguageCleanupPlan
import com.example.domain.ai.PrivacyMode
import com.example.domain.ai.UsageRangeOption
import com.example.domain.rules.GoalCleanupPlan
import com.example.domain.rules.NaturalLanguageRuleEngine
import com.example.domain.rules.RuleEvaluationPreview
import com.example.domain.scanner.CandidateIndexEngine
import com.example.domain.scanner.DeletionExecutionOutcome
import com.example.domain.scanner.DeletionResult
import com.example.domain.scanner.FailedDeletionItem
import com.example.domain.scanner.MediaCluster
import com.example.domain.scanner.MediaIdentityVerifier
import com.example.domain.scanner.ScanProgress
import com.example.domain.scanner.VerifiedUndoEntry
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
    val recoverableBytes: Long,
    val type: MediaClusterType = MediaClusterType.NEAR_DUPLICATE,
    val confidence: Float = 0.90f,
    val reason: String = "Similar media group",
    val bestShotExplanations: List<String> = emptyList(),
    val clusterGenerationVersion: Int = MediaCluster.CLUSTER_GENERATION_VERSION
) {
    val bestShotCandidate: PhotoEntity
        get() = bestShot

    val members: List<PhotoEntity>
        get() = allMembers
}

data class UndoTriageRecord(
    val entries: List<VerifiedUndoEntry>,
    val description: String,
    val actionCategory: String = "Review decision",
    val bytesAffected: Long = 0L,
    val bestShotRevertClusterId: String? = null,
    val bestShotRevertPhotoId: Long? = null,
    val timestampEpochMs: Long = System.currentTimeMillis()
) {
    val photoIds: List<Long>
        get() = entries.map { it.photoId }
}

data class PendingSystemDeleteRequest(
    val totalRequestedCount: Int,
    val intentSender: IntentSender,
    val pendingItems: List<PhotoEntity>,
    val partialDirectDeletedCount: Int,
    val partialDirectFreedBytes: Long,
    val preFailedItems: List<FailedDeletionItem>
)

data class SprintSessionState(
    val isActive: Boolean = false,
    val remainingSeconds: Int = 300,
    val targetCount: Int = 25,
    val completedCount: Int = 0,
    val recoveredBytesInSprint: Long = 0L
)

data class LuminaUiState(
    val isScanning: Boolean = false,
    val scanProgress: ScanProgress = ScanProgress(),
    val statusBannerMessage: String? = null,
    val allPhotos: List<PhotoEntity> = emptyList(),
    val activePhotos: List<PhotoEntity> = emptyList(),
    val vaultPhotos: List<PhotoEntity> = emptyList(),
    val smartBatches: List<SmartCleanupBatch> = emptyList(),
    val duplicateClusters: List<DuplicateClusterGroup> = emptyList(),
    val cleanupRules: List<CleanupRuleEntity> = emptyList(),
    val ruleEvaluations: List<RuleEvaluationPreview> = emptyList(),
    val aiAuditLogs: List<AiAuditLogEntity> = emptyList(),
    val totalProviderTokens: Long = 0L,
    val aiConfig: AiAdapterConfig = AiAdapterConfig(),
    val todayUsageSummary: AiUsageSummary = AiUsageSummary(
        rangeOption = UsageRangeOption.TODAY,
        totalTokens = 0L,
        inputTokens = 0L,
        outputTokens = 0L,
        requestCount = 0,
        actualRequestCount = 0,
        estimatedRequestCount = 0,
        cachedHitCount = 0,
        localAnalysisCount = 0,
        activeProviderLabel = "Custom",
        activeModelLabel = "gpt-4o-mini"
    ),
    val sevenDayUsageSummary: AiUsageSummary = AiUsageSummary(
        rangeOption = UsageRangeOption.LAST_7_DAYS,
        totalTokens = 0L,
        inputTokens = 0L,
        outputTokens = 0L,
        requestCount = 0,
        actualRequestCount = 0,
        estimatedRequestCount = 0,
        cachedHitCount = 0,
        localAnalysisCount = 0,
        activeProviderLabel = "Custom",
        activeModelLabel = "gpt-4o-mini"
    ),
    val thirtyDayUsageSummary: AiUsageSummary = AiUsageSummary(
        rangeOption = UsageRangeOption.LAST_30_DAYS,
        totalTokens = 0L,
        inputTokens = 0L,
        outputTokens = 0L,
        requestCount = 0,
        actualRequestCount = 0,
        estimatedRequestCount = 0,
        cachedHitCount = 0,
        localAnalysisCount = 0,
        activeProviderLabel = "Custom",
        activeModelLabel = "gpt-4o-mini"
    ),
    val homeSuggestions: List<AiHomeSuggestion> = emptyList(),
    val activeNaturalCleanupPlan: NaturalLanguageCleanupPlan? = null,
    val pendingPrivacyPreview: AiPrivacyPreview? = null,
    val customReviewSetTitle: String? = null,
    val customReviewSetPhotos: List<PhotoEntity>? = null,
    val encryptedCacheEntries: Int = 0,
    val encryptedCacheHitRate: Int = 96,
    val encryptedCacheSizeBytes: Long = 0L,
    val securityStorageDescription: String = "",
    val searchQuery: String = "",
    val selectedCategoryFilter: PhotoCategory? = null,
    val activeQueueIdForSwipe: String? = null,
    val skippedPhotoIds: Set<Long> = emptySet(),
    val undoHistory: List<UndoTriageRecord> = emptyList(),
    val lastUndoRecord: UndoTriageRecord? = null,
    val pendingSystemDeleteRequest: PendingSystemDeleteRequest? = null,
    val lastDeletionResult: DeletionResult? = null,
    val sprintState: SprintSessionState = SprintSessionState(),
    val goalTargetMegabytes: Int = 500,
    val currentGoalPlan: GoalCleanupPlan? = null,
    val sessionSavedBytes: Long = 0L,
    val sessionReviewedCount: Int = 0,
    val sessionKeptCount: Int = 0,
    val sessionMovedToBinCount: Int = 0,
    val showCleanupReceipt: Boolean = false,
    val isSprintCompletionReceipt: Boolean = false
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
    private val _undoHistory = MutableStateFlow<List<UndoTriageRecord>>(emptyList())
    private val _pendingSystemDelete = MutableStateFlow<PendingSystemDeleteRequest?>(null)
    private val _lastDeletionResult = MutableStateFlow<DeletionResult?>(null)
    private val _sprintState = MutableStateFlow(SprintSessionState())
    private val _goalTargetMb = MutableStateFlow(500)
    private val _sessionSavedBytes = MutableStateFlow(0L)
    private val _sessionReviewedCount = MutableStateFlow(0)
    private val _sessionKeptCount = MutableStateFlow(0)
    private val _sessionMovedToBinCount = MutableStateFlow(0)
    private val _showCleanupReceipt = MutableStateFlow(false)
    private val _isSprintCompletionReceipt = MutableStateFlow(false)
    private val _activeNaturalCleanupPlan = MutableStateFlow<NaturalLanguageCleanupPlan?>(null)
    private val _pendingPrivacyPreview = MutableStateFlow<AiPrivacyPreview?>(null)
    private val _customReviewSetTitle = MutableStateFlow<String?>(null)
    private val _customReviewSetPhotos = MutableStateFlow<List<PhotoEntity>?>(null)

    private var sprintTimerJob: Job? = null

    private data class CoreFlowsBundle(
        val photos: List<PhotoEntity>,
        val rules: List<CleanupRuleEntity>,
        val logs: List<AiAuditLogEntity>,
        val totalTokens: Long
    )

    private data class FilterStateBundle(
        val isScanning: Boolean,
        val banner: String?,
        val query: String,
        val category: PhotoCategory?,
        val swipeQueueId: String?,
        val skippedIds: Set<Long>,
        val aiCfg: AiAdapterConfig,
        val undoStack: List<UndoTriageRecord>,
        val pendingDelete: PendingSystemDeleteRequest?,
        val lastDeleteResult: DeletionResult?,
        val sprint: SprintSessionState,
        val goalMb: Int,
        val sessionSaved: Long,
        val sessionReviewed: Int,
        val sessionKept: Int,
        val sessionMovedToBin: Int,
        val showReceipt: Boolean,
        val sprintReceipt: Boolean,
        val activeCleanupPlan: NaturalLanguageCleanupPlan?,
        val pendingPrivacy: AiPrivacyPreview?,
        val customReviewTitle: String?,
        val customReviewPhotos: List<PhotoEntity>?
    )

    private val coreBundleFlow = combine(
        repository.allPhotosFlow,
        repository.cleanupRulesFlow,
        repository.aiAuditLogsFlow,
        repository.totalProviderTokensFlow
    ) { photos, rules, logs, tokens ->
        CoreFlowsBundle(photos, rules, logs, tokens)
    }

    private val filterBundleFlow = combine(
        combine(_isScanning, _statusMessage, _searchQuery, _selectedCategory) { a, b, c, d ->
            listOf(a, b, c, d)
        },
        combine(_activeSwipeQueueId, _skippedPhotoIds, _aiConfig, _undoHistory) { e, f, g, h ->
            listOf(e, f, g, h)
        },
        combine(_sprintState, _goalTargetMb, _sessionSavedBytes, _sessionReviewedCount) { i, j, k, l ->
            listOf(i, j, k, l)
        },
        combine(_pendingSystemDelete, _lastDeletionResult, _activeNaturalCleanupPlan, _pendingPrivacyPreview) { m, n, o, p ->
            listOf(m, n, o, p)
        },
        combine(
            combine(_customReviewSetTitle, _customReviewSetPhotos) { q, r -> listOf(q, r) },
            combine(_sessionKeptCount, _sessionMovedToBinCount, _showCleanupReceipt, _isSprintCompletionReceipt) { s, t, u, v ->
                listOf(s, t, u, v)
            }
        ) { customPair, receiptQuad ->
            customPair + receiptQuad
        }
    ) { first, second, third, fourth, fifth ->
        @Suppress("UNCHECKED_CAST")
        FilterStateBundle(
            isScanning = first[0] as Boolean,
            banner = first[1] as String?,
            query = first[2] as String,
            category = first[3] as PhotoCategory?,
            swipeQueueId = second[0] as String?,
            skippedIds = second[1] as Set<Long>,
            aiCfg = second[2] as AiAdapterConfig,
            undoStack = second[3] as List<UndoTriageRecord>,
            pendingDelete = fourth[0] as PendingSystemDeleteRequest?,
            lastDeleteResult = fourth[1] as DeletionResult?,
            sprint = third[0] as SprintSessionState,
            goalMb = third[1] as Int,
            sessionSaved = third[2] as Long,
            sessionReviewed = third[3] as Int,
            activeCleanupPlan = fourth[2] as NaturalLanguageCleanupPlan?,
            pendingPrivacy = fourth[3] as AiPrivacyPreview?,
            customReviewTitle = fifth[0] as String?,
            customReviewPhotos = fifth[1] as List<PhotoEntity>?,
            sessionKept = fifth[2] as Int,
            sessionMovedToBin = fifth[3] as Int,
            showReceipt = fifth[4] as Boolean,
            sprintReceipt = fifth[5] as Boolean
        )
    }

    val uiState: StateFlow<LuminaUiState> = combine(
        coreBundleFlow,
        filterBundleFlow,
        repository.scanProgressFlow
    ) { core, filter, scanProgress ->
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
        val todayUsage = AiWorkflowsAndUsageEngine.computeTodayUsageSummary(
            allLogs = core.logs,
            activeConfig = filter.aiCfg
        )
        val sevenDayUsage = AiWorkflowsAndUsageEngine.computeUsageSummary(
            allLogs = core.logs,
            rangeOption = UsageRangeOption.LAST_7_DAYS,
            activeConfig = filter.aiCfg
        )
        val thirtyDayUsage = AiWorkflowsAndUsageEngine.computeUsageSummary(
            allLogs = core.logs,
            rangeOption = UsageRangeOption.LAST_30_DAYS,
            activeConfig = filter.aiCfg
        )
        val suggestions = AiWorkflowsAndUsageEngine.generateHomeSuggestions(activePhotos)

        LuminaUiState(
            isScanning = filter.isScanning || scanProgress.isActive,
            scanProgress = scanProgress,
            statusBannerMessage = filter.banner,
            allPhotos = core.photos,
            activePhotos = activePhotos,
            vaultPhotos = vaultPhotos,
            smartBatches = smartBatches,
            duplicateClusters = duplicateClusters,
            cleanupRules = core.rules,
            ruleEvaluations = ruleEvaluations,
            aiAuditLogs = core.logs,
            totalProviderTokens = core.totalTokens,
            aiConfig = filter.aiCfg,
            todayUsageSummary = todayUsage,
            sevenDayUsageSummary = sevenDayUsage,
            thirtyDayUsageSummary = thirtyDayUsage,
            homeSuggestions = suggestions,
            activeNaturalCleanupPlan = filter.activeCleanupPlan,
            pendingPrivacyPreview = filter.pendingPrivacy,
            customReviewSetTitle = filter.customReviewTitle,
            customReviewSetPhotos = filter.customReviewPhotos,
            encryptedCacheEntries = repository.encryptedCache.entryCount(),
            encryptedCacheHitRate = repository.encryptedCache.hitRatePercent(),
            encryptedCacheSizeBytes = repository.encryptedCache.encryptedSizeBytes(),
            securityStorageDescription = repository.encryptedCache.securityStorageDescription(),
            searchQuery = filter.query,
            selectedCategoryFilter = filter.category,
            activeQueueIdForSwipe = filter.swipeQueueId,
            skippedPhotoIds = filter.skippedIds,
            undoHistory = filter.undoStack,
            lastUndoRecord = filter.undoStack.firstOrNull(),
            pendingSystemDeleteRequest = filter.pendingDelete,
            lastDeletionResult = filter.lastDeleteResult,
            sprintState = filter.sprint,
            goalTargetMegabytes = filter.goalMb,
            currentGoalPlan = goalPlan,
            sessionSavedBytes = filter.sessionSaved,
            sessionReviewedCount = filter.sessionReviewed,
            sessionKeptCount = filter.sessionKept,
            sessionMovedToBinCount = filter.sessionMovedToBin,
            showCleanupReceipt = filter.showReceipt,
            isSprintCompletionReceipt = filter.sprintReceipt
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

    private fun pushUndoRecord(record: UndoTriageRecord) {
        _undoHistory.value = (listOf(record) + _undoHistory.value).take(MAX_UNDO_HISTORY_SIZE)
    }

    fun navigateTo(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun openQueueInSwipeDeck(queueId: String?) {
        _customReviewSetPhotos.value = null
        _customReviewSetTitle.value = null
        _activeSwipeQueueId.value = queueId
        _skippedPhotoIds.value = emptySet()
        _currentDestination.value = AppDestination.SWIPE_DECK
    }

    fun openCustomReviewSet(title: String, photos: List<PhotoEntity>) {
        _customReviewSetTitle.value = title
        _customReviewSetPhotos.value = photos
        _activeSwipeQueueId.value = null
        _skippedPhotoIds.value = emptySet()
        _currentDestination.value = AppDestination.SWIPE_DECK
    }

    fun clearCustomReviewSet() {
        _customReviewSetTitle.value = null
        _customReviewSetPhotos.value = null
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

    fun openCleanupReceipt() {
        _isSprintCompletionReceipt.value = false
        _showCleanupReceipt.value = true
    }

    fun dismissCleanupReceipt() {
        _showCleanupReceipt.value = false
        _isSprintCompletionReceipt.value = false
    }

    fun dismissLastDeletionResult() {
        _lastDeletionResult.value = null
    }

    fun triggerLibraryRescan(resumeFromCheckpoint: Boolean = false) {
        viewModelScope.launch {
            _isScanning.value = true
            _statusMessage.value = if (resumeFromCheckpoint) {
                "Resuming library scan..."
            } else {
                "Scanning phone photos & videos..."
            }
            delay(100)
            val newCount = if (resumeFromCheckpoint) {
                repository.scanFullPhoneMediaLibrary(resumeFromCheckpoint = true)
            } else {
                repository.rescanAndClusterLibrary()
            }
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

    fun pauseLibraryScan() {
        repository.pauseIncrementalScan()
        _isScanning.value = false
        _statusMessage.value = "Library scan paused. You can resume anytime."
    }

    fun resumeLibraryScan() {
        repository.resumeIncrementalScan()
        triggerLibraryRescan(resumeFromCheckpoint = true)
    }

    fun cancelLibraryScan() {
        repository.cancelIncrementalScan()
        _isScanning.value = false
        _statusMessage.value = "Library scan cancelled."
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
     * P0 Identity-verified swipe triage:
     * Verifies both `photo.id` and `photo.uriString` (plus revision metadata) against the database row.
     * NEVER falls back to updating by ID alone. If verification fails, no mutation occurs and the
     * user sees a non-alarming refresh notice.
     */
    fun swipeTriagePhoto(photo: PhotoEntity, targetStatus: TriageStatus) {
        viewModelScope.launch {
            val prevStatus = photo.triageStatusEnum
            val outcome = repository.triagePhotoVerified(photo, targetStatus)
            if (!outcome.allSucceeded) {
                _statusMessage.value = outcome.userRefreshNotice
                    ?: MediaIdentityVerifier.STALE_SINGLE_ITEM_NOTICE
                return@launch
            }

            _sessionReviewedCount.value += 1
            if (targetStatus == TriageStatus.TRASH_VAULT) {
                _sessionSavedBytes.value += photo.fileSizeBytes
                _sessionMovedToBinCount.value += 1
            } else if (targetStatus == TriageStatus.KEEP) {
                _sessionKeptCount.value += 1
            }

            val actionPrefix = when (targetStatus) {
                TriageStatus.TRASH_VAULT -> "Review Bin"
                TriageStatus.KEEP -> "Keep"
                TriageStatus.FAVORITE_ARCHIVE -> "Favorite"
                TriageStatus.UNREVIEWED -> "Unreviewed"
            }
            val categoryLabel = when (targetStatus) {
                TriageStatus.TRASH_VAULT -> "Moved to Review Bin"
                TriageStatus.KEEP -> "Kept safe"
                TriageStatus.FAVORITE_ARCHIVE -> "Saved to Favorites"
                TriageStatus.UNREVIEWED -> "Reset to Unreviewed"
            }

            pushUndoRecord(
                UndoTriageRecord(
                    entries = listOf(
                        VerifiedUndoEntry(
                            photoId = photo.id,
                            expectedUri = photo.uriString,
                            previousStatus = prevStatus
                        )
                    ),
                    description = "$actionPrefix — ${photo.title}",
                    actionCategory = categoryLabel,
                    bytesAffected = photo.fileSizeBytes
                )
            )

            if (_sprintState.value.isActive) {
                val cur = _sprintState.value
                val addedBytes = if (targetStatus == TriageStatus.TRASH_VAULT) photo.fileSizeBytes else 0L
                val nextCount = cur.completedCount + 1
                val finished = nextCount >= cur.targetCount
                _sprintState.value = cur.copy(
                    completedCount = nextCount,
                    recoveredBytesInSprint = cur.recoveredBytesInSprint + addedBytes,
                    isActive = !finished
                )
                if (finished) {
                    sprintTimerJob?.cancel()
                    _isSprintCompletionReceipt.value = true
                    _showCleanupReceipt.value = true
                    _statusMessage.value = "Nice work • $nextCount reviewed • ${formatBytes(cur.recoveredBytesInSprint + addedBytes)} cleaned"
                }
            }
        }
    }

    fun batchMoveToVault(photos: List<PhotoEntity>, reasonLabel: String) {
        if (photos.isEmpty()) return
        viewModelScope.launch {
            val outcome = repository.triageBatchVerified(
                photos = photos,
                newStatus = TriageStatus.TRASH_VAULT,
                actionName = "batch_vault_$reasonLabel"
            )
            val rejectedIds = outcome.auditEvents.map { it.requestedId }.toSet()
            val verifiedPhotos = photos.filterNot { it.id in rejectedIds }

            if (verifiedPhotos.isNotEmpty()) {
                val movedBytes = verifiedPhotos.sumOf { it.fileSizeBytes }
                _sessionSavedBytes.value += movedBytes
                _sessionReviewedCount.value += verifiedPhotos.size
                _sessionMovedToBinCount.value += verifiedPhotos.size
                val isClusterCleanup = reasonLabel.contains("Cluster", ignoreCase = true)
                val desc = if (isClusterCleanup) {
                    "Cluster cleanup — ${verifiedPhotos.size} extras"
                } else {
                    "Review Bin — ${verifiedPhotos.size} items ($reasonLabel)"
                }
                pushUndoRecord(
                    UndoTriageRecord(
                        entries = verifiedPhotos.map { item ->
                            VerifiedUndoEntry(
                                photoId = item.id,
                                expectedUri = item.uriString,
                                previousStatus = item.triageStatusEnum
                            )
                        },
                        description = desc,
                        actionCategory = reasonLabel,
                        bytesAffected = movedBytes
                    )
                )
                _statusMessage.value = outcome.userRefreshNotice
                    ?: "Moved ${verifiedPhotos.size} item(s) (${formatBytes(movedBytes)}) to Review Bin."
            } else {
                _statusMessage.value = outcome.userRefreshNotice
                    ?: MediaIdentityVerifier.STALE_SINGLE_ITEM_NOTICE
            }
        }
    }

    fun undoLastTriage() {
        if (_undoHistory.value.isEmpty()) return
        undoDecisionAt(0)
    }

    fun undoDecisionAt(index: Int) {
        val stack = _undoHistory.value
        if (index !in stack.indices) return
        val record = stack[index]
        _undoHistory.value = stack.toMutableList().also { it.removeAt(index) }

        viewModelScope.launch {
            if (record.bestShotRevertClusterId != null && record.bestShotRevertPhotoId != null) {
                val ok = repository.selectBestShotInCluster(
                    clusterId = record.bestShotRevertClusterId,
                    chosenBestPhotoId = record.bestShotRevertPhotoId
                )
                _statusMessage.value = if (ok) {
                    "Undid: ${record.description}"
                } else {
                    MediaIdentityVerifier.STALE_SINGLE_ITEM_NOTICE
                }
            } else if (record.entries.isNotEmpty()) {
                val outcome = repository.undoTriageVerified(record.entries)
                _statusMessage.value = outcome.userRefreshNotice ?: "Undid: ${record.description}"
            }
        }
    }

    fun restoreFromVault(photos: List<PhotoEntity>) {
        if (photos.isEmpty()) return
        viewModelScope.launch {
            val outcome = repository.restoreVaultItemsVerified(photos)
            _statusMessage.value = outcome.userRefreshNotice
                ?: "Restored ${outcome.verifiedUpdatedCount} item(s) from Review Bin."
        }
    }

    /**
     * Initiates P0 permanent deletion via [MediaDeletionCoordinator]:
     * - Never removes Room rows unless the underlying source file/URI is confirmed deleted.
     * - Reports ONLY confirmed-deleted bytes.
     * - If Android 11+ MediaStore requires a system confirmation dialog, emits [pendingSystemDeleteRequest].
     * - Note: Confirmed permanent deletions NEVER enter the Undo stack.
     */
    fun confirmTwoStepPermanentDelete(vaultPhotos: List<PhotoEntity>) {
        if (vaultPhotos.isEmpty()) return
        viewModelScope.launch {
            when (val outcome = repository.executePermanentDelete(vaultPhotos)) {
                is DeletionExecutionOutcome.Completed -> {
                    applyDeletionResultBanner(outcome.result)
                }
                is DeletionExecutionOutcome.RequiresSystemDialog -> {
                    _pendingSystemDelete.value = PendingSystemDeleteRequest(
                        totalRequestedCount = vaultPhotos.size,
                        intentSender = outcome.intentSender,
                        pendingItems = outcome.pendingItems,
                        partialDirectDeletedCount = outcome.partialDirectDeletedCount,
                        partialDirectFreedBytes = outcome.partialDirectFreedBytes,
                        preFailedItems = outcome.preFailedItems
                    )
                }
            }
        }
    }

    fun onSystemDeleteDialogResult(confirmedOk: Boolean) {
        val pending = _pendingSystemDelete.value ?: return
        _pendingSystemDelete.value = null
        viewModelScope.launch {
            val finalResult = repository.finalizeSystemDelete(
                totalRequestedCount = pending.totalRequestedCount,
                pendingItems = pending.pendingItems,
                systemDialogConfirmedOk = confirmedOk,
                partialDirectDeletedCount = pending.partialDirectDeletedCount,
                partialDirectFreedBytes = pending.partialDirectFreedBytes,
                preFailedItems = pending.preFailedItems
            )
            applyDeletionResultBanner(finalResult)
        }
    }

    private fun applyDeletionResultBanner(result: DeletionResult) {
        _lastDeletionResult.value = result
        if (result.confirmedDeletedCount > 0) {
            // Prune any undo history records referencing permanently deleted photo IDs
            val remainingIds = uiState.value.allPhotos.map { it.id }.toSet()
            _undoHistory.value = _undoHistory.value.filter { rec ->
                rec.photoIds.all { it in remainingIds }
            }
        }
        _statusMessage.value = when {
            result.confirmedDeletedCount > 0 && result.failedCount == 0 ->
                "${formatBytes(result.confirmedFreedBytes)} actually recovered (${result.confirmedDeletedCount} item(s) permanently deleted)."
            result.confirmedDeletedCount > 0 && result.failedCount > 0 ->
                "${formatBytes(result.confirmedFreedBytes)} actually recovered (${result.confirmedDeletedCount} deleted). ${result.failedCount} item(s) remain safely in Review Bin."
            else ->
                "No items were deleted (${result.failedCount} item(s) retained safely in Review Bin)."
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
            val ok = repository.markPhotoNotDuplicate(photo)
            _statusMessage.value = if (ok) {
                "Removed \"${photo.title}\" from duplicate group."
            } else {
                MediaIdentityVerifier.STALE_SINGLE_ITEM_NOTICE
            }
        }
    }

    fun selectBestShotInCluster(clusterId: String, photoId: Long) {
        viewModelScope.launch {
            val existingCluster = uiState.value.duplicateClusters.find { it.clusterId == clusterId }
            val previousBestShot = existingCluster?.bestShot
            val targetPhoto = existingCluster?.allMembers?.find { it.id == photoId }
            val ok = repository.selectBestShotInCluster(clusterId, photoId)
            if (ok) {
                if (previousBestShot != null && previousBestShot.id != photoId) {
                    pushUndoRecord(
                        UndoTriageRecord(
                            entries = emptyList(),
                            description = "Best Shot — ${targetPhoto?.title ?: "#$photoId"}",
                            actionCategory = "Manual Best Shot selection",
                            bytesAffected = 0L,
                            bestShotRevertClusterId = clusterId,
                            bestShotRevertPhotoId = previousBestShot.id
                        )
                    )
                }
                _statusMessage.value = "Selected Best Shot: ${targetPhoto?.title ?: "Updated"}"
            } else {
                _statusMessage.value = MediaIdentityVerifier.STALE_SINGLE_ITEM_NOTICE
            }
        }
    }

    fun startFiveMinuteSprint() {
        sprintTimerJob?.cancel()
        val unreviewedCount = uiState.value.activePhotos.count { it.triageStatusEnum == TriageStatus.UNREVIEWED }
        val target = minOf(25, maxOf(5, unreviewedCount))
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
                val nextSeconds = (cur.remainingSeconds - 1).coerceAtLeast(0)
                _sprintState.value = cur.copy(
                    remainingSeconds = nextSeconds,
                    isActive = nextSeconds > 0
                )
                if (nextSeconds == 0) {
                    _isSprintCompletionReceipt.value = true
                    _showCleanupReceipt.value = true
                }
            }
        }
    }

    fun stopSprintSession() {
        sprintTimerJob?.cancel()
        val cur = _sprintState.value
        _sprintState.value = cur.copy(isActive = false)
        if (cur.completedCount > 0) {
            _isSprintCompletionReceipt.value = true
            _showCleanupReceipt.value = true
        }
    }

    fun updateGoalTargetMb(megabytes: Int) {
        _goalTargetMb.value = megabytes.coerceIn(50, 10_000)
    }

    fun createNaturalLanguageRule(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            val created = repository.addNaturalLanguageRule(prompt)
            _statusMessage.value = "Saved automation: ${created.title}"
        }
    }

    /**
     * Generates a structured Natural-Language Cleanup Plan preview (Pass 3 Sections I & J)
     * without immediately deleting anything.
     */
    fun previewNaturalLanguageCleanupPlan(prompt: String) {
        if (prompt.isBlank()) return
        val plan = AiWorkflowsAndUsageEngine.generateCleanupPlan(
            naturalQuery = prompt,
            allPhotos = uiState.value.allPhotos
        )
        _activeNaturalCleanupPlan.value = plan
    }

    fun dismissNaturalCleanupPlan() {
        _activeNaturalCleanupPlan.value = null
    }

    fun reviewNaturalCleanupPlan(plan: NaturalLanguageCleanupPlan) {
        _activeNaturalCleanupPlan.value = null
        if (plan.candidates.isNotEmpty()) {
            openCustomReviewSet(title = plan.title, photos = plan.candidates)
        } else {
            _statusMessage.value = "No matching items to review for \"${plan.title}\"."
        }
    }

    fun saveCleanupPlanAsAutomation(plan: NaturalLanguageCleanupPlan) {
        viewModelScope.launch {
            val created = repository.addNaturalLanguageRule(plan.rawPrompt)
            _activeNaturalCleanupPlan.value = null
            _statusMessage.value = "Saved automation: ${created.title}"
        }
    }

    fun openHomeSuggestionInReview(suggestion: AiHomeSuggestion) {
        if (suggestion.candidatePhotos.isEmpty()) return
        openCustomReviewSet(
            title = suggestion.title,
            photos = suggestion.candidatePhotos
        )
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
            val updated = repository.inspectPhotoWithByokAi(photo)
            _isScanning.value = false
            _statusMessage.value = if (updated != null) {
                "Analyzed ${updated.title}"
            } else {
                MediaIdentityVerifier.STALE_SINGLE_ITEM_NOTICE
            }
        }
    }

    /**
     * Initiates a batch AI pass. If cloud AI is active and the user hasn't opted to skip the
     * Privacy Preview dialog, presents [AiPrivacyPreview] first (Pass 3 Section H).
     */
    fun requestBatchByokAiPass() {
        val unreviewed = uiState.value.activePhotos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }
        if (unreviewed.isEmpty()) {
            _statusMessage.value = "No unreviewed items need AI inspection."
            return
        }
        val cfg = _aiConfig.value
        val hasKey = repository.hasSecretKeyFor(cfg.activeProvider) || cfg.customApiKey.isNotBlank()
        if (cfg.privacyMode != PrivacyMode.LOCAL_ONLY && hasKey && !cfg.skipPrivacyPreviewDialog) {
            _pendingPrivacyPreview.value = repository.aiAdapterLayer.buildPrivacyPreview(
                photos = unreviewed,
                config = cfg,
                maxBatchSize = 12
            )
        } else {
            runBatchByokAiPass()
        }
    }

    fun dismissPrivacyPreview() {
        _pendingPrivacyPreview.value = null
    }

    fun confirmPrivacyPreviewAndAnalyze(rememberConsent: Boolean = false) {
        _pendingPrivacyPreview.value = null
        if (rememberConsent) {
            val updatedCfg = _aiConfig.value.copy(skipPrivacyPreviewDialog = true)
            viewModelScope.launch {
                repository.saveAiConfig(updatedCfg)
                _aiConfig.value = updatedCfg
            }
        }
        runBatchByokAiPass()
    }

    fun runBatchByokAiPass() {
        viewModelScope.launch {
            val unreviewed = uiState.value.activePhotos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }
            if (unreviewed.isEmpty()) return@launch
            _isScanning.value = true
            _statusMessage.value = "Running AI analysis on ${minOf(12, unreviewed.size)} items..."
            val count = repository.runBatchAiInspection(unreviewed)
            _isScanning.value = false
            _statusMessage.value = "AI analysis complete for $count items."
        }
    }

    fun clearEncryptedCache() {
        viewModelScope.launch {
            repository.clearEncryptedAnalysisCache()
            _statusMessage.value = "Cleared analysis cache (API keys & automations preserved)."
        }
    }

    fun hasConfiguredKey(provider: AiProviderType): Boolean {
        return repository.hasSecretKeyFor(provider)
    }

    suspend fun fetchEndpointModels(
        rawEndpoint: String,
        provider: AiProviderType,
        apiKeyOverride: String,
        forceRefresh: Boolean = false
    ): com.example.domain.ai.EndpointModelCatalogResult {
        return repository.fetchAvailableModels(rawEndpoint, provider, apiKeyOverride, forceRefresh)
    }

    suspend fun testAiConnection(
        config: AiAdapterConfig,
        catalogOverride: com.example.domain.ai.EndpointModelCatalogResult? = null
    ): AiConnectionTestResult {
        return repository.testAiConnection(config, catalogOverride)
    }

    private fun buildSmartBatches(activePhotos: List<PhotoEntity>): List<SmartCleanupBatch> {
        val unreviewed = activePhotos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }

        val exactDuplicates = unreviewed.filter {
            it.duplicateClusterId != null &&
                !it.isBestShotInCluster &&
                it.clusterTypeEnum == MediaClusterType.EXACT_DUPLICATE
        }

        val similarPhotos = unreviewed.filter {
            it.duplicateClusterId != null &&
                !it.isBestShotInCluster &&
                it !in exactDuplicates
        }

        val allDuplicateExtras = unreviewed.filter {
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

        val dupGroupPhotos = if (exactDuplicates.isNotEmpty()) exactDuplicates else allDuplicateExtras

        return listOfNotNull(
            SmartCleanupBatch(
                id = "queue_duplicates",
                title = "Duplicates",
                subtitle = "${dupGroupPhotos.size} extra shots • Best shots safely kept",
                badgeText = formatBytes(dupGroupPhotos.sumOf { it.fileSizeBytes }),
                spineColorHex = 0xFF168BFF,
                photos = dupGroupPhotos,
                totalBytes = dupGroupPhotos.sumOf { it.fileSizeBytes },
                averageConfidence = 0.92f
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_old_screenshots",
                title = "Screenshots",
                subtitle = "${screenshots.size} screen captures ready to review",
                badgeText = formatBytes(screenshots.sumOf { it.fileSizeBytes }),
                spineColorHex = 0xFF38BDF8,
                photos = screenshots,
                totalBytes = screenshots.sumOf { it.fileSizeBytes },
                averageConfidence = 0.88f
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_similar",
                title = "Similar Photos",
                subtitle = "${similarPhotos.size} near-duplicate or burst variations",
                badgeText = formatBytes(similarPhotos.sumOf { it.fileSizeBytes }),
                spineColorHex = 0xFF2DD4BF,
                photos = similarPhotos,
                totalBytes = similarPhotos.sumOf { it.fileSizeBytes },
                averageConfidence = 0.86f
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_blurry",
                title = "Blurry",
                subtitle = "${blurryPhotos.size} low-sharpness or misfocused shots",
                badgeText = formatBytes(blurryPhotos.sumOf { it.fileSizeBytes }),
                spineColorHex = 0xFFE5484D,
                photos = blurryPhotos,
                totalBytes = blurryPhotos.sumOf { it.fileSizeBytes },
                averageConfidence = 0.94f
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_videos",
                title = "Large Videos",
                subtitle = "${videoItems.size} video clips sorted by size",
                badgeText = formatBytes(videoItems.sumOf { it.fileSizeBytes }),
                spineColorHex = 0xFF818CF8,
                photos = videoItems,
                totalBytes = videoItems.sumOf { it.fileSizeBytes },
                averageConfidence = 0.78f
            ).takeIf { it.photos.isNotEmpty() },
            SmartCleanupBatch(
                id = "queue_receipts_protected",
                title = "Documents",
                subtitle = "${downloadsAndDocs.size} receipts, documents & downloads",
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
                    compareByDescending<PhotoEntity> { if (it.userSelectedBestShot) 1 else 0 }
                        .thenByDescending { if (it.isBestShotInCluster) 1 else 0 }
                        .thenByDescending { it.overallQualityScore }
                        .thenByDescending { it.sharpnessScore }
                )
                val best = sorted.first()
                val redundant = sorted.drop(1)
                val clusterId = best.duplicateClusterId ?: "cluster"
                val classified = CandidateIndexEngine.classifyCluster(clusterId, sorted, best)
                val resolvedType = best.clusterTypeEnum ?: classified.type
                val resolvedConfidence = best.clusterConfidence.takeIf { it > 0f } ?: classified.confidence
                val resolvedReason = best.clusterReason.ifBlank { classified.reason }

                val descriptiveTitle = AiWorkflowsAndUsageEngine.generateDescriptiveClusterTitle(
                    members = sorted,
                    clusterType = resolvedType
                )
                val bestShotExplanations = AiWorkflowsAndUsageEngine.formatUserFacingBestShotExplanations(
                    bestShot = best,
                    clusterMembers = sorted
                )
                DuplicateClusterGroup(
                    clusterId = clusterId,
                    title = descriptiveTitle,
                    bestShot = best,
                    redundantVariants = redundant,
                    allMembers = sorted,
                    recoverableBytes = redundant.sumOf { it.fileSizeBytes },
                    type = resolvedType,
                    confidence = resolvedConfidence,
                    reason = resolvedReason,
                    bestShotExplanations = bestShotExplanations,
                    clusterGenerationVersion = best.clusterGenerationVersion
                )
            }
            .sortedByDescending { it.recoverableBytes }
    }

    companion object {
        private const val MAX_UNDO_HISTORY_SIZE = 50

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
