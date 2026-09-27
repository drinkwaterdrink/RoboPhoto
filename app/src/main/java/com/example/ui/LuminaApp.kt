package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.PhotoEntity
import com.example.ui.components.AiPrivacyPreviewDialog
import com.example.ui.components.ByokAiHubSheet
import com.example.ui.components.CleanupReceiptSheet
import com.example.ui.components.FlickerCompareDialog
import com.example.ui.components.GoalPlannerSheet
import com.example.ui.components.NaturalLanguageCleanupPlanSheet
import com.example.ui.components.PhotoForensicsDetailSheet
import com.example.ui.components.TwoStepPermanentDeleteDialog
import com.example.ui.components.UndoHistorySheet
import com.example.ui.screens.DuplicatesScreen
import com.example.ui.screens.QueuesScreen
import com.example.ui.screens.RulesScreen
import com.example.ui.screens.SwipeDeckScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.DestructiveRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.KeepEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppDestination
import com.example.ui.viewmodel.DuplicateClusterGroup
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel

@Composable
fun LuminaApp(
    viewModel: LuminaViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()

    var showByokSheet by remember { mutableStateOf(false) }
    var showGoalPlannerSheet by remember { mutableStateOf(false) }
    var showTwoStepDeleteDialog by remember { mutableStateOf(false) }
    var showUndoHistorySheet by remember { mutableStateOf(false) }
    var activeFlickerClusterId by remember { mutableStateOf<String?>(null) }
    var inspectedPhotoId by remember { mutableStateOf<Long?>(null) }

    val inspectedPhoto: PhotoEntity? = remember(uiState.allPhotos, inspectedPhotoId) {
        inspectedPhotoId?.let { id -> uiState.allPhotos.find { it.id == id } }
    }

    val activeFlickerCluster: DuplicateClusterGroup? = remember(uiState.duplicateClusters, activeFlickerClusterId) {
        activeFlickerClusterId?.let { cid -> uiState.duplicateClusters.find { it.clusterId == cid } }
    }

    val requiredMediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    var hasMediaPerm by remember {
        mutableStateOf(
            requiredMediaPermissions.any { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        hasMediaPerm = grantMap.values.any { it }
        viewModel.triggerLibraryRescan()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onPhotosPicked(uris)
        }
    }

    val systemDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onSystemDeleteDialogResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.pendingSystemDeleteRequest) {
        val pendingRequest = uiState.pendingSystemDeleteRequest ?: return@LaunchedEffect
        try {
            val request = IntentSenderRequest.Builder(pendingRequest.intentSender).build()
            systemDeleteLauncher.launch(request)
        } catch (_: Exception) {
            viewModel.onSystemDeleteDialogResult(false)
        }
    }

    if (currentDestination != AppDestination.QUEUES) {
        BackHandler {
            viewModel.navigateTo(AppDestination.QUEUES)
        }
    }

    // Pass 4 Section R: Bottom Navigation maintains Home, Review, Collections, Automations
    val primaryNavDestinations = remember {
        listOf(
            AppDestination.QUEUES,
            AppDestination.SWIPE_DECK,
            AppDestination.CLUSTERS,
            AppDestination.AI_RULES
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                StealthTopHeaderBar(
                    uiState = uiState,
                    isCompactMode = currentDestination == AppDestination.SWIPE_DECK,
                    isReviewBinActive = currentDestination == AppDestination.TRASH_VAULT,
                    onOpenReviewBin = { viewModel.navigateTo(AppDestination.TRASH_VAULT) },
                    onOpenUndoHistory = { showUndoHistorySheet = true },
                    onOpenSettings = { showByokSheet = true }
                )

                AnimatedVisibility(
                    visible = uiState.statusBannerMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    uiState.statusBannerMessage?.let { message ->
                        StealthStatusBanner(
                            message = message,
                            isScanning = uiState.isScanning,
                            hasUndo = uiState.lastUndoRecord != null,
                            onUndo = { viewModel.undoLastTriage() },
                            onDismiss = { viewModel.dismissBanner() }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 1.dp, color = CardBorderSlate)
                NavigationBar(
                    containerColor = CharcoalSurface,
                    tonalElevation = 0.dp
                ) {
                    primaryNavDestinations.forEach { destination ->
                        val selected = currentDestination == destination
                        val badgeCount = when (destination) {
                            AppDestination.QUEUES -> 0
                            AppDestination.SWIPE_DECK -> uiState.unreviewedCount
                            AppDestination.CLUSTERS -> uiState.duplicateClusters.size
                            AppDestination.AI_RULES -> 0
                            AppDestination.TRASH_VAULT -> uiState.vaultPhotos.size
                        }

                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.navigateTo(destination) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (badgeCount > 0) {
                                            Badge(
                                                containerColor = if (selected) ElectricBlue else ElevatedSlate,
                                                contentColor = TextPrimary
                                            ) {
                                                Text(badgeCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    val icon = when (destination) {
                                        AppDestination.QUEUES -> Icons.Filled.Home
                                        AppDestination.SWIPE_DECK -> Icons.Filled.Swipe
                                        AppDestination.CLUSTERS -> Icons.Filled.CollectionsBookmark
                                        AppDestination.AI_RULES -> Icons.Filled.AutoAwesome
                                        AppDestination.TRASH_VAULT -> Icons.Filled.DeleteOutline
                                    }
                                    Icon(imageVector = icon, contentDescription = destination.label)
                                }
                            },
                            label = {
                                Text(
                                    text = destination.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricBlue,
                                selectedTextColor = ElectricBlue,
                                indicatorColor = ElectricBlue.copy(alpha = 0.14f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_tab_${destination.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                AppDestination.QUEUES -> {
                    QueuesScreen(
                        uiState = uiState,
                        hasMediaPermission = hasMediaPerm,
                        onRequestMediaPermissionAndScan = {
                            if (hasMediaPerm) {
                                viewModel.triggerLibraryRescan()
                            } else {
                                mediaPermissionLauncher.launch(requiredMediaPermissions)
                            }
                        },
                        onLaunchPhotoPicker = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        onPauseScan = viewModel::pauseLibraryScan,
                        onResumeScan = viewModel::resumeLibraryScan,
                        onCancelScan = viewModel::cancelLibraryScan,
                        onOpenQueueInDeck = viewModel::openQueueInSwipeDeck,
                        onQuickReviewCleanupBatch = { batch ->
                            viewModel.openCustomReviewSet(batch.title, batch.photos)
                        },
                        onSelectCategory = viewModel::selectCategoryFilter,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onInspectPhotoDetail = { inspectedPhotoId = it.id },
                        onStartSprint = viewModel::startFiveMinuteSprint,
                        onOpenGoalPlanner = { showGoalPlannerSheet = true },
                        onOpenAiSuggestion = viewModel::openHomeSuggestionInReview,
                        onPreviewNaturalCleanupPlan = viewModel::previewNaturalLanguageCleanupPlan,
                        onOpenTrashVault = { viewModel.navigateTo(AppDestination.TRASH_VAULT) }
                    )
                }

                AppDestination.SWIPE_DECK -> {
                    SwipeDeckScreen(
                        uiState = uiState,
                        onSwipeTriage = { photo, status ->
                            viewModel.swipeTriagePhoto(photo, status)
                        },
                        onSkipPhoto = { photo ->
                            viewModel.skipPhotoInReview(photo)
                        },
                        onResetSkipped = {
                            viewModel.clearSkippedPhotos()
                        },
                        onSelectQueue = { queueId ->
                            viewModel.openQueueInSwipeDeck(queueId)
                        },
                        onClearCustomReviewSet = {
                            viewModel.clearCustomReviewSet()
                        },
                        onUndoLast = {
                            viewModel.undoLastTriage()
                        },
                        onOpenUndoHistory = {
                            showUndoHistorySheet = true
                        },
                        onOpenCleanupReceipt = {
                            viewModel.openCleanupReceipt()
                        },
                        onInspectPhotoDetail = { photo ->
                            inspectedPhotoId = photo.id
                        },
                        onInspectWithAi = { photo ->
                            viewModel.inspectSinglePhotoWithAi(photo)
                        },
                        onStopSprint = {
                            viewModel.stopSprintSession()
                        },
                        onOpenTrashVault = {
                            viewModel.navigateTo(AppDestination.TRASH_VAULT)
                        },
                        onSelectBestShot = { clusterId, photoId ->
                            viewModel.selectBestShotInCluster(clusterId, photoId)
                        },
                        onOpenFlickerCompare = { cluster ->
                            activeFlickerClusterId = cluster.clusterId
                        }
                    )
                }

                AppDestination.CLUSTERS -> {
                    DuplicatesScreen(
                        uiState = uiState,
                        onTrashRedundantForCluster = { cluster ->
                            viewModel.batchMoveToVault(cluster.redundantVariants, "Cluster ${cluster.title}")
                        },
                        onTrashAllRedundantDuplicates = { allExtras ->
                            viewModel.batchMoveToVault(allExtras, "Cluster cleanup")
                        },
                        onInspectPhotoDetail = { inspectedPhotoId = it.id },
                        onRenameCluster = { clusterId, newTitle ->
                            viewModel.renameDuplicateCluster(clusterId, newTitle)
                        },
                        onMarkNotDuplicate = { photo ->
                            viewModel.markPhotoNotDuplicate(photo)
                        },
                        onSelectBestShot = { clusterId, photoId ->
                            viewModel.selectBestShotInCluster(clusterId, photoId)
                        },
                        onSelectCategoryFilter = { category ->
                            viewModel.selectCategoryFilter(category)
                        },
                        onOpenQueueInSwipeDeck = { queueId ->
                            viewModel.openQueueInSwipeDeck(queueId)
                        }
                    )
                }

                AppDestination.AI_RULES -> {
                    RulesScreen(
                        uiState = uiState,
                        onCreateRuleFromPrompt = { viewModel.createNaturalLanguageRule(it) },
                        onPreviewCleanupPlan = { viewModel.previewNaturalLanguageCleanupPlan(it) },
                        onToggleRule = { viewModel.toggleCleanupRule(it) },
                        onExecuteRule = { rule ->
                            val eval = uiState.ruleEvaluations.find { it.rule.id == rule.id }
                            if (eval != null && eval.matchingPhotos.isNotEmpty()) {
                                viewModel.batchMoveToVault(eval.matchingPhotos, rule.title)
                            }
                        },
                        onDeleteRule = { viewModel.deleteCleanupRule(it.id) },
                        onOpenMatchesInReview = { rule ->
                            val eval = uiState.ruleEvaluations.find { it.rule.id == rule.id }
                            if (eval != null && eval.matchingPhotos.isNotEmpty()) {
                                viewModel.openCustomReviewSet(rule.title, eval.matchingPhotos)
                            }
                        },
                        onInspectPhoto = { inspectedPhotoId = it.id }
                    )
                }

                AppDestination.TRASH_VAULT -> {
                    VaultScreen(
                        uiState = uiState,
                        onRestorePhotos = { viewModel.restoreFromVault(it) },
                        onRequestPermanentDeleteModal = { showTwoStepDeleteDialog = true },
                        onInspectPhoto = { inspectedPhotoId = it.id }
                    )
                }
            }
        }
    }

    if (showByokSheet) {
        ByokAiHubSheet(
            currentConfig = uiState.aiConfig,
            todayUsageSummary = uiState.todayUsageSummary,
            sevenDayUsageSummary = uiState.sevenDayUsageSummary,
            thirtyDayUsageSummary = uiState.thirtyDayUsageSummary,
            totalProviderTokens = uiState.totalProviderTokens,
            securityStorageDescription = uiState.securityStorageDescription,
            encryptedCacheEntries = uiState.encryptedCacheEntries,
            encryptedCacheHitRate = uiState.encryptedCacheHitRate,
            auditLogs = uiState.aiAuditLogs,
            hasKeyForProvider = { viewModel.hasConfiguredKey(it) },
            onFetchEndpointModels = { endpoint, provider, apiKey, forceRefresh ->
                viewModel.fetchEndpointModels(endpoint, provider, apiKey, forceRefresh)
            },
            onTestConnection = { draftConfig, catalogOverride ->
                viewModel.testAiConnection(draftConfig, catalogOverride)
            },
            onSaveConfig = { viewModel.updateAiAdapterConfig(it) },
            onRunBatchAiPass = { viewModel.requestBatchByokAiPass() },
            onClearEncryptedCache = { viewModel.clearEncryptedCache() },
            onDismiss = { showByokSheet = false }
        )
    }

    uiState.pendingPrivacyPreview?.let { privacyPreview ->
        AiPrivacyPreviewDialog(
            preview = privacyPreview,
            onConfirmAnalyze = { rememberPreference ->
                viewModel.confirmPrivacyPreviewAndAnalyze(rememberPreference)
            },
            onDismiss = { viewModel.dismissPrivacyPreview() }
        )
    }

    uiState.activeNaturalCleanupPlan?.let { cleanupPlan ->
        NaturalLanguageCleanupPlanSheet(
            plan = cleanupPlan,
            onReviewCandidates = { plan ->
                viewModel.reviewNaturalCleanupPlan(plan)
            },
            onSaveAutomation = { plan ->
                viewModel.saveCleanupPlanAsAutomation(plan)
            },
            onDismiss = { viewModel.dismissNaturalCleanupPlan() }
        )
    }

    if (showGoalPlannerSheet) {
        GoalPlannerSheet(
            goalTargetMb = uiState.goalTargetMegabytes,
            goalPlan = uiState.currentGoalPlan,
            onUpdateTargetMb = { viewModel.updateGoalTargetMb(it) },
            onExecutePlanToVault = { photos ->
                viewModel.batchMoveToVault(photos, "Goal Plan (${uiState.goalTargetMegabytes} MB)")
            },
            onReviewSelectedPhotos = { photos ->
                viewModel.openCustomReviewSet("Space Goal Plan", photos)
            },
            onDismiss = { showGoalPlannerSheet = false }
        )
    }

    if (showTwoStepDeleteDialog) {
        TwoStepPermanentDeleteDialog(
            vaultPhotos = uiState.vaultPhotos,
            totalRecoverableBytes = uiState.vaultRecoverableBytes,
            onConfirmPermanentDelete = {
                viewModel.confirmTwoStepPermanentDelete(uiState.vaultPhotos)
                showTwoStepDeleteDialog = false
            },
            onDismiss = { showTwoStepDeleteDialog = false }
        )
    }

    if (showUndoHistorySheet) {
        UndoHistorySheet(
            undoHistory = uiState.undoHistory,
            onUndoAt = { idx ->
                viewModel.undoDecisionAt(idx)
            },
            onDismiss = { showUndoHistorySheet = false }
        )
    }

    if (uiState.showCleanupReceipt) {
        CleanupReceiptSheet(
            reviewedCount = uiState.sessionReviewedCount,
            keptCount = uiState.sessionKeptCount,
            movedToBinCount = uiState.sessionMovedToBinCount,
            readyToRecoverBytes = uiState.vaultRecoverableBytes.coerceAtLeast(uiState.sessionSavedBytes),
            verifiedPermanentlyDeletedBytes = uiState.lastDeletionResult?.confirmedFreedBytes ?: 0L,
            verifiedPermanentlyDeletedCount = uiState.lastDeletionResult?.confirmedDeletedCount ?: 0,
            isFiveMinuteSprintCompletion = uiState.isSprintCompletionReceipt,
            onOpenReviewBin = { viewModel.navigateTo(AppDestination.TRASH_VAULT) },
            onDismiss = { viewModel.dismissCleanupReceipt() }
        )
    }

    if (activeFlickerCluster != null) {
        FlickerCompareDialog(
            cluster = activeFlickerCluster,
            onSelectBestShot = { cid, pid ->
                viewModel.selectBestShotInCluster(cid, pid)
            },
            onMoveExtraToBin = { extraPhoto ->
                viewModel.swipeTriagePhoto(extraPhoto, com.example.data.local.TriageStatus.TRASH_VAULT)
            },
            onDismiss = { activeFlickerClusterId = null }
        )
    }

    if (inspectedPhoto != null) {
        PhotoForensicsDetailSheet(
            photo = inspectedPhoto,
            onTriage = { targetStatus ->
                viewModel.swipeTriagePhoto(inspectedPhoto, targetStatus)
            },
            onInspectWithAi = { viewModel.inspectSinglePhotoWithAi(it) },
            onDismiss = { inspectedPhotoId = null }
        )
    }
}

@Composable
private fun StealthTopHeaderBar(
    uiState: LuminaUiState,
    isCompactMode: Boolean,
    isReviewBinActive: Boolean,
    onOpenReviewBin: () -> Unit,
    onOpenUndoHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = ObsidianBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = if (isCompactMode) 6.dp else 10.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = if (isCompactMode) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.isScanning) {
                        CircularProgressIndicator(
                            color = ElectricBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Undo History icon button
                    if (uiState.undoHistory.isNotEmpty()) {
                        IconButton(
                            onClick = onOpenUndoHistory,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CharcoalSurface)
                                .testTag("top_bar_undo_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = "Recent Decisions History",
                                tint = ElectricBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Review Bin pill button
                    val binCount = uiState.vaultPhotos.size
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isReviewBinActive) {
                            ElectricBlue.copy(alpha = 0.18f)
                        } else if (binCount > 0) {
                            DestructiveRed.copy(alpha = 0.14f)
                        } else {
                            CharcoalSurface
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isReviewBinActive -> ElectricBlue
                                binCount > 0 -> DestructiveRed.copy(alpha = 0.45f)
                                else -> CardBorderSlate
                            }
                        ),
                        modifier = Modifier
                            .clickable(onClick = onOpenReviewBin)
                            .testTag("nav_tab_trash_vault")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Review Bin",
                                tint = if (binCount > 0) DestructiveRed else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (binCount > 0) {
                                    "Bin ($binCount)"
                                } else {
                                    "Bin"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (binCount > 0) TextPrimary else TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Settings / AI Hub icon button
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CharcoalSurface)
                            .testTag("top_bar_byok_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings & AI Profiles",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = CardBorderSlate)
        }
    }
}

@Composable
private fun StealthStatusBanner(
    message: String,
    isScanning: Boolean,
    hasUndo: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = CharcoalSurface,
        border = BorderStroke(1.dp, CardBorderSlate),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        color = ElectricBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasUndo && !isScanning) {
                    Text(
                        text = "UNDO",
                        style = MaterialTheme.typography.labelSmall,
                        color = KeepEmerald,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onUndo)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("banner_undo_button")
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
