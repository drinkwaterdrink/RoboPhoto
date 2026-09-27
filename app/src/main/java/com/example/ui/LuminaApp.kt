package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.PhotoEntity
import com.example.ui.components.ByokAiHubSheet
import com.example.ui.components.GoalPlannerSheet
import com.example.ui.components.PhotoForensicsDetailSheet
import com.example.ui.components.TwoStepPermanentDeleteDialog
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
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel

@Composable
fun LuminaApp(
    viewModel: LuminaViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()

    var showByokSheet by remember { mutableStateOf(false) }
    var showGoalPlannerSheet by remember { mutableStateOf(false) }
    var showTwoStepDeleteDialog by remember { mutableStateOf(false) }
    var inspectedPhotoId by remember { mutableStateOf<Long?>(null) }

    val inspectedPhoto: PhotoEntity? = remember(uiState.allPhotos, inspectedPhotoId) {
        inspectedPhotoId?.let { id -> uiState.allPhotos.find { it.id == id } }
    }

    if (currentDestination != AppDestination.QUEUES) {
        BackHandler {
            viewModel.navigateTo(AppDestination.QUEUES)
        }
    }

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
                    isReviewBinActive = currentDestination == AppDestination.TRASH_VAULT,
                    onOpenReviewBin = { viewModel.navigateTo(AppDestination.TRASH_VAULT) },
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
                        onStartSprint = { viewModel.startFiveMinuteSprint() },
                        onOpenGoalPlanner = { showGoalPlannerSheet = true },
                        onOpenByokSheet = { showByokSheet = true },
                        onReviewQueueInSwipe = { queueId -> viewModel.openQueueInSwipeDeck(queueId) },
                        onBatchVaultQueue = { photos, label ->
                            viewModel.batchMoveToVault(photos, label)
                        },
                        onInspectPhoto = { inspectedPhotoId = it.id },
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onCategoryFilterSelect = { viewModel.selectCategoryFilter(it) },
                        onTriggerRescan = { viewModel.triggerLibraryRescan() },
                        onPhotosPicked = { viewModel.onPhotosPicked(it) }
                    )
                }

                AppDestination.SWIPE_DECK -> {
                    SwipeDeckScreen(
                        uiState = uiState,
                        onSelectQueueFilter = { viewModel.openQueueInSwipeDeck(it) },
                        onSwipeDecision = { photo, status ->
                            viewModel.swipeTriagePhoto(photo, status)
                        },
                        onSkipPhoto = { photo ->
                            viewModel.skipPhotoInReview(photo)
                        },
                        onResetSkipped = {
                            viewModel.clearSkippedPhotos()
                        },
                        onUndoLast = { viewModel.undoLastTriage() },
                        onStopSprint = { viewModel.stopSprintSession() },
                        onInspectPhoto = { inspectedPhotoId = it.id }
                    )
                }

                AppDestination.CLUSTERS -> {
                    DuplicatesScreen(
                        uiState = uiState,
                        onVaultRedundantInCluster = { photos, label ->
                            viewModel.batchMoveToVault(photos, label)
                        },
                        onVaultAllRedundant = { allExtras ->
                            viewModel.batchMoveToVault(allExtras, "All Duplicate Extras")
                        },
                        onInspectPhoto = { inspectedPhotoId = it.id },
                        onRenameCluster = { clusterId, newTitle ->
                            viewModel.renameDuplicateCluster(clusterId, newTitle)
                        },
                        onMarkNotDuplicate = { photo ->
                            viewModel.markPhotoNotDuplicate(photo)
                        },
                        onSelectBestShot = { clusterId, photoId ->
                            viewModel.selectBestShotInCluster(clusterId, photoId)
                        }
                    )
                }

                AppDestination.AI_RULES -> {
                    RulesScreen(
                        uiState = uiState,
                        onCreateRule = { viewModel.createNaturalLanguageRule(it) },
                        onToggleRule = { viewModel.toggleCleanupRule(it) },
                        onDeleteRule = { viewModel.deleteCleanupRule(it) },
                        onExecuteRulePreview = { preview ->
                            viewModel.batchMoveToVault(preview.matchingPhotos, preview.rule.title)
                        },
                        onOpenGoalPlanner = { showGoalPlannerSheet = true },
                        onOpenByokSheet = { showByokSheet = true },
                        onInspectPhoto = { inspectedPhotoId = it.id }
                    )
                }

                AppDestination.TRASH_VAULT -> {
                    VaultScreen(
                        uiState = uiState,
                        onRestoreIds = { viewModel.restoreFromVault(it) },
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
            totalSpendUsd = uiState.totalApiSpendUsd,
            encryptedCacheEntries = uiState.encryptedCacheEntries,
            encryptedCacheHitRate = uiState.encryptedCacheHitRate,
            auditLogs = uiState.aiAuditLogs,
            hasKeyForProvider = { viewModel.hasConfiguredKey(it) },
            onFetchEndpointModels = { endpoint, provider, apiKey ->
                viewModel.fetchEndpointModels(endpoint, provider, apiKey)
            },
            onSaveConfig = { viewModel.updateAiAdapterConfig(it) },
            onRunBatchAiPass = { viewModel.runBatchByokAiPass() },
            onClearEncryptedCache = { viewModel.clearEncryptedCache() },
            onDismiss = { showByokSheet = false }
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
    isReviewBinActive: Boolean,
    onOpenReviewBin: () -> Unit,
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CharcoalSurface)
                            .testTag("top_bar_byok_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings & AI Profiles",
                            tint = TextSecondary,
                            modifier = Modifier.size(19.dp)
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
