package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.BurstMode
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.ui.components.ByokAiHubSheet
import com.example.ui.components.GoalPlannerSheet
import com.example.ui.components.PhotoForensicsDetailSheet
import com.example.ui.screens.DuplicatesScreen
import com.example.ui.screens.QueuesScreen
import com.example.ui.screens.RulesScreen
import com.example.ui.screens.SwipeDeckScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpineAmber
import com.example.ui.theme.SpineCoral
import com.example.ui.theme.SpineEmerald
import com.example.ui.theme.SpineViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppDestination
import com.example.ui.viewmodel.LuminaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuminaApp(
    viewModel: LuminaViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()

    var showByokAiSheet by remember { mutableStateOf(false) }
    var showGoalPlannerSheet by remember { mutableStateOf(false) }
    var selectedPhotoForDetail by remember { mutableStateOf<PhotoEntity?>(null) }

    // Android Photo & Video Picker (supports both local gallery & cloud Google Photos)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onPhotosPicked(uris)
        }
    }

    // Full Phone MediaStore Scanner runtime permission launcher (Photos + Videos)
    val mediaPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.triggerLibraryRescan()
    }

    val requestFullPhoneScan: () -> Unit = {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        mediaPermissionsLauncher.launch(permissions)
    }

    // BackHandler for secondary tabs returning to primary Queues tab
    BackHandler(enabled = currentDestination != AppDestination.QUEUES) {
        viewModel.navigateTo(AppDestination.QUEUES)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val isExpandedScreen = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = ObsidianBg,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ObsidianBg,
                        titleContentColor = TextPrimary
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_robophoto_icon),
                                contentDescription = "RoboPhoto Icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Column {
                                Text(
                                    text = "RoboPhoto",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Photo & Video AI Forensics • BYOK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    },
                    actions = {
                        if (uiState.isScanning) {
                            CircularProgressIndicator(
                                color = SpineEmerald,
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(20.dp)
                            )
                        }
                        if (uiState.lastUndoRecord != null) {
                            IconButton(
                                onClick = { viewModel.undoLastTriage() },
                                modifier = Modifier.testTag("top_bar_undo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo last triage",
                                    tint = SpineAmber
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ElevatedSlate,
                            border = BorderStroke(1.dp, SpineViolet.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { showByokAiSheet = true }
                                .testTag("top_bar_byok_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(SpineEmerald)
                                )
                                Text(
                                    text = uiState.aiConfig.activeProfileName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    LuminaBottomNavigationBar(
                        currentDestination = currentDestination,
                        unreviewedCount = uiState.activePhotos.count { it.triageStatusEnum == TriageStatus.UNREVIEWED },
                        clusterCount = uiState.duplicateClusters.size,
                        vaultCount = uiState.vaultPhotos.size,
                        onNavigate = { viewModel.navigateTo(it) }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    LuminaSideNavigationRail(
                        currentDestination = currentDestination,
                        unreviewedCount = uiState.activePhotos.count { it.triageStatusEnum == TriageStatus.UNREVIEWED },
                        clusterCount = uiState.duplicateClusters.size,
                        vaultCount = uiState.vaultPhotos.size,
                        onNavigate = { viewModel.navigateTo(it) }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Live Status / Undo Banner
                    AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                        uiState.statusBannerMessage?.let { msg ->
                            Surface(
                                color = ElevatedSlate,
                                border = BorderStroke(1.dp, SpineEmerald.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .testTag("status_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.dismissBanner() },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Dismiss notification",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Active Screen Content
                    Box(modifier = Modifier.weight(1f)) {
                        when (currentDestination) {
                            AppDestination.QUEUES -> QueuesScreen(
                                uiState = uiState,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onCategorySelect = viewModel::selectCategoryFilter,
                                onOpenQueueInSwipe = viewModel::openQueueInSwipeDeck,
                                onBatchMoveToVault = viewModel::batchMoveToVault,
                                onPhotoClick = { selectedPhotoForDetail = it },
                                onQuickKeep = { viewModel.swipeTriagePhoto(it, TriageStatus.KEEP) },
                                onQuickVault = { viewModel.swipeTriagePhoto(it, TriageStatus.TRASH_VAULT) },
                                onImportPhotosClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                onStartFiveMinSprint = viewModel::startFiveMinuteSprint,
                                onOpenGoalPlanner = { showGoalPlannerSheet = true },
                                onOpenByokAiHub = { showByokAiSheet = true },
                                onTriggerRescan = requestFullPhoneScan
                            )

                            AppDestination.SWIPE_DECK -> SwipeDeckScreen(
                                uiState = uiState,
                                onSelectSwipeQueue = viewModel::openQueueInSwipeDeck,
                                onSwipeDecision = viewModel::swipeTriagePhoto,
                                onUndoLast = viewModel::undoLastTriage,
                                onInspectPhotoDetail = { selectedPhotoForDetail = it },
                                onStartSprint = viewModel::startFiveMinuteSprint,
                                onStopSprint = viewModel::stopSprintSession
                            )

                            AppDestination.CLUSTERS -> DuplicatesScreen(
                                uiState = uiState,
                                onStageClusterRedundants = { cluster ->
                                    viewModel.batchMoveToVault(
                                        cluster.redundantVariants,
                                        "Cluster cleanup (${cluster.title})"
                                    )
                                },
                                onStageAllClustersRedundants = { allRedundant ->
                                    viewModel.batchMoveToVault(
                                        allRedundant,
                                        "All duplicate burst extras"
                                    )
                                },
                                onPhotoClick = { selectedPhotoForDetail = it }
                            )

                            AppDestination.AI_RULES -> RulesScreen(
                                uiState = uiState,
                                onCreateRule = viewModel::createNaturalLanguageRule,
                                onToggleRule = viewModel::toggleCleanupRule,
                                onDeleteRule = viewModel::deleteCleanupRule,
                                onApproveAndExecuteRule = viewModel::batchMoveToVault,
                                onPhotoClick = { selectedPhotoForDetail = it }
                            )

                            AppDestination.TRASH_VAULT -> VaultScreen(
                                uiState = uiState,
                                onRestorePhotoIds = viewModel::restoreFromVault,
                                onConfirmPermanentDelete = viewModel::confirmTwoStepPermanentDelete,
                                onPhotoClick = { selectedPhotoForDetail = it }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modals & Bottom Sheets
    if (showByokAiSheet) {
        ByokAiHubSheet(
            currentConfig = uiState.aiConfig,
            totalSpendUsd = uiState.totalApiSpendUsd,
            encryptedCacheEntries = uiState.encryptedCacheEntries,
            encryptedCacheHitRate = uiState.encryptedCacheHitRate,
            auditLogs = uiState.aiAuditLogs,
            hasKeyForProvider = viewModel::hasConfiguredKey,
            onFetchEndpointModels = viewModel::fetchEndpointModels,
            onSaveConfig = viewModel::updateAiAdapterConfig,
            onRunBatchAiPass = viewModel::runBatchByokAiPass,
            onClearEncryptedCache = viewModel::clearEncryptedCache,
            onDismiss = { showByokAiSheet = false }
        )
    }

    if (showGoalPlannerSheet) {
        GoalPlannerSheet(
            goalTargetMb = uiState.goalTargetMegabytes,
            goalPlan = uiState.currentGoalPlan,
            onUpdateTargetMb = viewModel::updateGoalTargetMb,
            onExecutePlanToVault = { photos ->
                viewModel.batchMoveToVault(photos, "Goal Planner (${uiState.goalTargetMegabytes}MB Target)")
            },
            onDismiss = { showGoalPlannerSheet = false }
        )
    }

    selectedPhotoForDetail?.let { photo ->
        PhotoForensicsDetailSheet(
            photo = photo,
            onTriage = { status -> viewModel.swipeTriagePhoto(photo, status) },
            onInspectWithAi = { viewModel.inspectSinglePhotoWithAi(it) },
            onDismiss = { selectedPhotoForDetail = null }
        )
    }
}

private data class NavTabSpec(
    val destination: AppDestination,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0,
    val badgeColor: Color = SpineEmerald
)

@Composable
private fun LuminaBottomNavigationBar(
    currentDestination: AppDestination,
    unreviewedCount: Int,
    clusterCount: Int,
    vaultCount: Int,
    onNavigate: (AppDestination) -> Unit
) {
    val tabs = listOf(
        NavTabSpec(
            AppDestination.QUEUES,
            Icons.Filled.DashboardCustomize,
            Icons.Outlined.DashboardCustomize
        ),
        NavTabSpec(
            AppDestination.SWIPE_DECK,
            Icons.Filled.Style,
            Icons.Outlined.Style,
            badgeCount = unreviewedCount,
            badgeColor = SpineAmber
        ),
        NavTabSpec(
            AppDestination.CLUSTERS,
            Icons.Filled.BurstMode,
            Icons.Outlined.BurstMode,
            badgeCount = clusterCount,
            badgeColor = SpineEmerald
        ),
        NavTabSpec(
            AppDestination.AI_RULES,
            Icons.AutoMirrored.Filled.Rule,
            Icons.AutoMirrored.Outlined.Rule
        ),
        NavTabSpec(
            AppDestination.TRASH_VAULT,
            Icons.Filled.DeleteSweep,
            Icons.Outlined.DeleteSweep,
            badgeCount = vaultCount,
            badgeColor = SpineCoral
        )
    )

    NavigationBar(
        containerColor = CharcoalSurface,
        tonalElevation = 8.dp
    ) {
        tabs.forEach { tab ->
            val selected = currentDestination == tab.destination
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(tab.destination) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tab.badgeCount > 0) {
                                Badge(
                                    containerColor = tab.badgeColor,
                                    contentColor = Color(0xFF06130E)
                                ) {
                                    Text(tab.badgeCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.destination.label
                        )
                    }
                },
                label = {
                    Text(
                        text = tab.destination.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SpineEmerald,
                    selectedTextColor = SpineEmerald,
                    indicatorColor = SpineEmerald.copy(alpha = 0.16f),
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                ),
                modifier = Modifier.testTag("nav_tab_${tab.destination.route}")
            )
        }
    }
}

@Composable
private fun LuminaSideNavigationRail(
    currentDestination: AppDestination,
    unreviewedCount: Int,
    clusterCount: Int,
    vaultCount: Int,
    onNavigate: (AppDestination) -> Unit
) {
    val tabs = listOf(
        NavTabSpec(
            AppDestination.QUEUES,
            Icons.Filled.DashboardCustomize,
            Icons.Outlined.DashboardCustomize
        ),
        NavTabSpec(
            AppDestination.SWIPE_DECK,
            Icons.Filled.Style,
            Icons.Outlined.Style,
            badgeCount = unreviewedCount,
            badgeColor = SpineAmber
        ),
        NavTabSpec(
            AppDestination.CLUSTERS,
            Icons.Filled.BurstMode,
            Icons.Outlined.BurstMode,
            badgeCount = clusterCount,
            badgeColor = SpineEmerald
        ),
        NavTabSpec(
            AppDestination.AI_RULES,
            Icons.AutoMirrored.Filled.Rule,
            Icons.AutoMirrored.Outlined.Rule
        ),
        NavTabSpec(
            AppDestination.TRASH_VAULT,
            Icons.Filled.DeleteSweep,
            Icons.Outlined.DeleteSweep,
            badgeCount = vaultCount,
            badgeColor = SpineCoral
        )
    )

    NavigationRail(
        containerColor = CharcoalSurface,
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(modifier = Modifier.weight(1f))
        tabs.forEach { tab ->
            val selected = currentDestination == tab.destination
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(tab.destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.destination.label
                    )
                },
                label = { Text(tab.destination.label) },
                modifier = Modifier.testTag("rail_tab_${tab.destination.route}")
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}
