package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MediaClusterType
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.domain.ai.AiHomeSuggestion
import com.example.domain.scanner.ScanProgress
import com.example.ui.components.PhotoThumbnailView
import com.example.ui.components.SectionEmptyStateCard
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.DestructiveRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.KeepEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoboCard
import com.example.ui.theme.RoboHaptics
import com.example.ui.theme.RoboMediaCard
import com.example.ui.theme.RoboPill
import com.example.ui.theme.RoboPrimaryButton
import com.example.ui.theme.RoboProgressNumber
import com.example.ui.theme.RoboRadius
import com.example.ui.theme.RoboSectionHeader
import com.example.ui.theme.SpineAmber
import com.example.ui.theme.SpineCyan
import com.example.ui.theme.SpineViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.roboPressScale
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel
import com.example.ui.viewmodel.SmartCleanupBatch
import java.text.NumberFormat
import java.util.Locale

/**
 * Pass 4 Section B & C: Refined Home Screen.
 * - Immediately answers: "How much can RoboPhoto help me clean?"
 *   - Primary Hero:
 *     `12.6 GB can be cleaned`
 *     `1,842 items ready to review`
 *     `[ Start Cleanup ]`
 * - Quick Actions 2x2 Grid (no truncated labels!):
 *   - `Scan Library` | `Add from Photos`
 *   - `5-Min Cleanup` | `Free Up Space Goal`
 * - Cleanup Groups (Duplicates, Screenshots, Similar Photos, Blurry, Large Videos, Documents)
 * - AI Suggestions
 * - Browse / Search
 * - Zero technical noise (no raw hash data, cache hit percentages, or algorithm names on Home).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueuesScreen(
    uiState: LuminaUiState,
    hasMediaPermission: Boolean,
    onRequestMediaPermissionAndScan: () -> Unit,
    onLaunchPhotoPicker: () -> Unit,
    onPauseScan: () -> Unit,
    onResumeScan: () -> Unit,
    onCancelScan: () -> Unit,
    onOpenQueueInDeck: (String?) -> Unit,
    onQuickReviewCleanupBatch: (SmartCleanupBatch) -> Unit,
    onSelectCategory: (PhotoCategory?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onInspectPhotoDetail: (PhotoEntity) -> Unit,
    onStartSprint: () -> Unit,
    onOpenGoalPlanner: () -> Unit,
    onOpenAiSuggestion: (AiHomeSuggestion) -> Unit,
    onPreviewNaturalCleanupPlan: (String) -> Unit,
    onOpenTrashVault: () -> Unit
) {
    val filteredPhotos = remember(
        uiState.activePhotos,
        uiState.selectedCategoryFilter,
        uiState.searchQuery
    ) {
        uiState.activePhotos.filter { photo ->
            val matchesCategory = uiState.selectedCategoryFilter == null ||
                photo.categoryEnum == uiState.selectedCategoryFilter
            val q = uiState.searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                photo.title.lowercase().contains(q) ||
                photo.flaggedReason.lowercase().contains(q) ||
                photo.ocrSnippet.lowercase().contains(q) ||
                photo.semanticLabels.lowercase().contains(q) ||
                photo.categoryEnum.displayName.lowercase().contains(q)
            matchesCategory && matchesQuery
        }
    }

    val cleanupGroupCards = remember(uiState.activePhotos, uiState.smartBatches) {
        buildHomeCleanupGroups(uiState.activePhotos, uiState.smartBatches)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("queues_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Primary Hero Card — "How much can RoboPhoto help me clean?" + "Your Memories Organized by AI" (Pass 4 Section B + Concept UI)
            item {
                HomeCleanupHeroCard(
                    allPhotos = uiState.activePhotos,
                    cleanableBytes = uiState.totalCleanableBytes,
                    readyToReviewCount = uiState.unreviewedCount,
                    vaultCount = uiState.vaultPhotos.size,
                    vaultBytes = uiState.vaultRecoverableBytes,
                    hasMediaPermission = hasMediaPermission,
                    isScanning = uiState.isScanning,
                    scanProgress = uiState.scanProgress,
                    onStartCleanup = { onOpenQueueInDeck(null) },
                    onScanLibrary = onRequestMediaPermissionAndScan,
                    onAddFromPhotos = onLaunchPhotoPicker,
                    onStartFiveMinCleanup = onStartSprint,
                    onOpenGoalPlanner = onOpenGoalPlanner,
                    onOpenReviewBin = onOpenTrashVault,
                    onPauseScan = onPauseScan,
                    onResumeScan = onResumeScan,
                    onCancelScan = onCancelScan
                )
            }

            // 1B. Storage Health Ring Card & Recent Moments Strip (from RoboPhoto UI Concept)
            if (uiState.activePhotos.isNotEmpty()) {
                item {
                    StorageHealthRingCard(
                        allPhotos = uiState.activePhotos,
                        cleanableBytes = uiState.totalCleanableBytes,
                        onClick = onOpenGoalPlanner
                    )
                }

                item {
                    RecentMomentsPortraitStrip(
                        allPhotos = uiState.activePhotos,
                        onInspectPhotoDetail = onInspectPhotoDetail,
                        onOpenAllInReview = { onOpenQueueInDeck(null) }
                    )
                }
            }

            // 2. Cleanup Groups (Duplicates, Screenshots, Similar Photos, Blurry, Large Videos, Documents)
            item {
                RoboSectionHeader(
                    title = "Cleanup Groups",
                    subtitle = "Tap any group to start reviewing",
                    trailingContent = {
                        if (uiState.unreviewedCount > 0) {
                            Text(
                                text = "Review All",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricBlue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { onOpenQueueInDeck(null) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("review_all_queues_link")
                            )
                        }
                    }
                )
            }

            if (uiState.smartBatches.isEmpty()) {
                item {
                    SectionEmptyStateCard(
                        title = "All Cleanup Groups Clear",
                        message = "You've reviewed all flagged items. Scan your library or add photos to check for new duplicates and clutter.",
                        icon = Icons.Filled.Shield
                    )
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        cleanupGroupCards.chunked(2).forEach { rowPair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowPair.forEach { groupItem ->
                                    CleanupGroupCompactCard(
                                        group = groupItem,
                                        onClick = {
                                            if (groupItem.batch != null && groupItem.batch.photos.isNotEmpty()) {
                                                onQuickReviewCleanupBatch(groupItem.batch)
                                            } else if (groupItem.photos.isNotEmpty()) {
                                                onQuickReviewCleanupBatch(
                                                    SmartCleanupBatch(
                                                        id = groupItem.id,
                                                        title = groupItem.title,
                                                        subtitle = groupItem.subtitle,
                                                        badgeText = LuminaViewModel.formatBytes(groupItem.totalBytes),
                                                        spineColorHex = groupItem.accentColorHex,
                                                        photos = groupItem.photos,
                                                        totalBytes = groupItem.totalBytes,
                                                        averageConfidence = 0.88f
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowPair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 3. AI Suggestions (Pass 4 Section B)
            if (uiState.homeSuggestions.isNotEmpty()) {
                item {
                    AiHomeSuggestionsStrip(
                        suggestions = uiState.homeSuggestions,
                        onOpenSuggestion = onOpenAiSuggestion,
                        onPreviewNaturalPlan = onPreviewNaturalCleanupPlan
                    )
                }
            }

            // 4. Browse & Search Library (Pass 4 Section B)
            item {
                BrowseAndSearchSection(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    selectedCategory = uiState.selectedCategoryFilter,
                    onSelectCategory = onSelectCategory,
                    filteredPhotos = filteredPhotos,
                    onInspectPhotoDetail = onInspectPhotoDetail
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private data class HomeCleanupGroupSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val accentColorHex: Long,
    val photos: List<PhotoEntity>,
    val totalBytes: Long,
    val batch: SmartCleanupBatch?
)

private fun buildHomeCleanupGroups(
    activePhotos: List<PhotoEntity>,
    smartBatches: List<SmartCleanupBatch>
): List<HomeCleanupGroupSpec> {
    val unreviewed = activePhotos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }

    val exactDups = unreviewed.filter {
        it.duplicateClusterId != null &&
            !it.isBestShotInCluster &&
            it.clusterTypeEnum == MediaClusterType.EXACT_DUPLICATE
    }
    val allDups = unreviewed.filter { it.duplicateClusterId != null && !it.isBestShotInCluster }
    val duplicatesList = if (exactDups.isNotEmpty()) exactDups else allDups

    val screenshotsList = unreviewed.filter {
        (it.categoryEnum == PhotoCategory.SCREENSHOT || it.categoryEnum == PhotoCategory.MEME) &&
            !it.screenshotSubTypeEnum.isImportantDefault
    }

    val similarList = unreviewed.filter {
        it.duplicateClusterId != null && !it.isBestShotInCluster && it !in exactDups
    }.ifEmpty { allDups }

    val blurryList = unreviewed.filter {
        !it.isVideo && (it.categoryEnum == PhotoCategory.BLURRY || it.sharpnessScore < 42)
    }

    val videosList = unreviewed.filter {
        it.isVideo || it.categoryEnum == PhotoCategory.VIDEO
    }.sortedByDescending { it.fileSizeBytes }

    val docsList = activePhotos.filter {
        it.categoryEnum == PhotoCategory.DOWNLOAD ||
            it.categoryEnum == PhotoCategory.RECEIPT ||
            it.categoryEnum == PhotoCategory.DOCUMENT ||
            it.screenshotSubTypeEnum.isImportantDefault
    }

    return listOf(
        HomeCleanupGroupSpec(
            id = "queue_duplicates",
            title = "Duplicates",
            subtitle = "${duplicatesList.size} items",
            icon = Icons.Filled.ContentCopy,
            accentColor = ElectricBlue,
            accentColorHex = 0xFF168BFF,
            photos = duplicatesList,
            totalBytes = duplicatesList.sumOf { it.fileSizeBytes },
            batch = smartBatches.find { it.id == "queue_duplicates" }
        ),
        HomeCleanupGroupSpec(
            id = "queue_old_screenshots",
            title = "Screenshots",
            subtitle = "${screenshotsList.size} items",
            icon = Icons.Filled.Screenshot,
            accentColor = SpineCyan,
            accentColorHex = 0xFF38BDF8,
            photos = screenshotsList,
            totalBytes = screenshotsList.sumOf { it.fileSizeBytes },
            batch = smartBatches.find { it.id == "queue_old_screenshots" }
        ),
        HomeCleanupGroupSpec(
            id = "queue_similar",
            title = "Similar Photos",
            subtitle = "${similarList.size} items",
            icon = Icons.Filled.BurstMode,
            accentColor = KeepEmerald,
            accentColorHex = 0xFF10B981,
            photos = similarList,
            totalBytes = similarList.sumOf { it.fileSizeBytes },
            batch = smartBatches.find { it.id == "queue_similar" }
        ),
        HomeCleanupGroupSpec(
            id = "queue_blurry",
            title = "Blurry",
            subtitle = "${blurryList.size} items",
            icon = Icons.Filled.BlurOn,
            accentColor = DestructiveRed,
            accentColorHex = 0xFFE5484D,
            photos = blurryList,
            totalBytes = blurryList.sumOf { it.fileSizeBytes },
            batch = smartBatches.find { it.id == "queue_blurry" }
        ),
        HomeCleanupGroupSpec(
            id = "queue_videos",
            title = "Large Videos",
            subtitle = "${videosList.size} items",
            icon = Icons.Filled.Movie,
            accentColor = SpineViolet,
            accentColorHex = 0xFF818CF8,
            photos = videosList,
            totalBytes = videosList.sumOf { it.fileSizeBytes },
            batch = smartBatches.find { it.id == "queue_videos" }
        ),
        HomeCleanupGroupSpec(
            id = "queue_receipts_protected",
            title = "Documents",
            subtitle = "${docsList.size} items",
            icon = Icons.Filled.Description,
            accentColor = SpineAmber,
            accentColorHex = 0xFFF59E0B,
            photos = docsList,
            totalBytes = docsList.sumOf { it.fileSizeBytes },
            batch = smartBatches.find { it.id == "queue_receipts_protected" }
        )
    )
}

/**
 * Pass 4 Section B & C:
 * Hero Card with:
 * - "12.6 GB can be cleaned"
 * - "1,842 items ready to review"
 * - [ Start Cleanup ]
 * - Non-truncated 2x2 Quick Action Grid:
 *   [ Scan Library ]     [ Add from Photos ]
 *   [ 5-Min Cleanup ]    [ Free Up Space Goal ]
 */
@Composable
private fun HomeCleanupHeroCard(
    allPhotos: List<PhotoEntity>,
    cleanableBytes: Long,
    readyToReviewCount: Int,
    vaultCount: Int,
    vaultBytes: Long,
    hasMediaPermission: Boolean,
    isScanning: Boolean,
    scanProgress: ScanProgress,
    onStartCleanup: () -> Unit,
    onScanLibrary: () -> Unit,
    onAddFromPhotos: () -> Unit,
    onStartFiveMinCleanup: () -> Unit,
    onOpenGoalPlanner: () -> Unit,
    onOpenReviewBin: () -> Unit,
    onPauseScan: () -> Unit,
    onResumeScan: () -> Unit,
    onCancelScan: () -> Unit
) {
    val formattedCount = remember(readyToReviewCount) {
        NumberFormat.getIntegerInstance(Locale.US).format(readyToReviewCount)
    }
    val photoCount = remember(allPhotos) { allPhotos.count { !it.isVideo } }
    val videoCount = remember(allPhotos) { allPhotos.count { it.isVideo } }
    val placesOrFoldersCount = remember(allPhotos) {
        allPhotos.map { it.folderName.ifBlank { "Camera" } }.distinct().size.coerceAtLeast(1)
    }
    val peopleCount = remember(allPhotos) {
        allPhotos.count {
            it.faceCount > 0 ||
                it.semanticLabels.contains("person", ignoreCase = true) ||
                it.semanticLabels.contains("portrait", ignoreCase = true) ||
                it.sceneDescription.contains("person", ignoreCase = true)
        }
    }

    RoboCard(
        containerColor = CharcoalSurface,
        borderColor = CardBorderSlate,
        cornerRadius = RoboRadius.Large,
        contentPadding = PaddingValues(18.dp),
        modifier = Modifier.testTag("storage_hero_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Concept Header Row: "Your Memories • Organized by AI" + Mini Electric-Blue Bar Chart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElevatedSlate,
                        border = BorderStroke(1.dp, CardBorderSlate),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Your Memories",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Organized by AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                // Mini 5-bar Electric Blue Equalizer/Histogram graphic from concept image
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(24.dp)
                ) {
                    listOf(12.dp, 22.dp, 16.dp, 24.dp, 14.dp).forEach { barHeight ->
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(99.dp))
                                .background(ElectricBlue)
                        )
                    }
                }
            }

            // 4-Column Library Stat Strip from Concept Image: Photos | Videos | Places | People
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianBg.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, CardBorderSlate.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroStatColumn(value = NumberFormat.getIntegerInstance(Locale.US).format(photoCount), label = "Photos")
                    HeroStatColumn(value = NumberFormat.getIntegerInstance(Locale.US).format(videoCount), label = "Videos")
                    HeroStatColumn(value = NumberFormat.getIntegerInstance(Locale.US).format(placesOrFoldersCount), label = "Places")
                    HeroStatColumn(value = NumberFormat.getIntegerInstance(Locale.US).format(peopleCount), label = "People")
                }
            }

            // Top headline: "12.6 GB can be cleaned" + "1,842 items ready to review"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RoboProgressNumber(
                        targetValue = cleanableBytes,
                        formatter = { bytes -> "${LuminaViewModel.formatBytes(bytes)} can be cleaned" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "$formattedCount items ready to review",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Review Bin quick status pill if items are staged
                if (vaultCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DestructiveRed.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, DestructiveRed.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .clickable(onClick = onOpenReviewBin)
                            .testTag("hero_open_review_bin_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Open Review Bin",
                                tint = DestructiveRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Bin ($vaultCount • ${LuminaViewModel.formatBytes(vaultBytes)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = DestructiveRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Primary CTA: [ Start Cleanup ]
            RoboPrimaryButton(
                text = "Start Cleanup",
                icon = Icons.Filled.PlayArrow,
                onClick = onStartCleanup,
                enabled = readyToReviewCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_start_cleanup_button")
            )

            // Active or paused scan progress bar (if scanning)
            if (isScanning || scanProgress.isActive || scanProgress.isPaused) {
                IncrementalScanProgressPanel(
                    progress = scanProgress,
                    onPause = onPauseScan,
                    onResume = onResumeScan,
                    onCancel = onCancelScan
                )
            }

            // Pass 4 Section C: 2x2 Quick Action Grid — Every label is readable on narrow & wide phones!
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionTileButton(
                        title = if (hasMediaPermission) "Scan Library" else "Grant & Scan",
                        icon = Icons.Filled.Refresh,
                        accentColor = ElectricBlue,
                        onClick = onScanLibrary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scan_phone_button")
                    )
                    QuickActionTileButton(
                        title = "Add from Photos",
                        icon = Icons.Filled.AddPhotoAlternate,
                        accentColor = SpineCyan,
                        onClick = onAddFromPhotos,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_google_photos_button")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionTileButton(
                        title = "5-Min Cleanup",
                        icon = Icons.Filled.Timer,
                        accentColor = KeepEmerald,
                        onClick = onStartFiveMinCleanup,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_sprint_button")
                    )
                    QuickActionTileButton(
                        title = "Free Up Space Goal",
                        icon = Icons.Filled.FilterCenterFocus,
                        accentColor = SpineAmber,
                        onClick = onOpenGoalPlanner,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_goal_planner_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionTileButton(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ElevatedSlate,
        border = BorderStroke(1.dp, CardBorderSlate),
        modifier = modifier
            .height(48.dp)
            .roboPressScale()
            .clickable {
                RoboHaptics.swipeThresholdTick(haptic)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact 2-column Cleanup Group Card for Home (Pass 4 Section B):
 * Displays group icon, title, recoverable size pill, item count, and preview thumbnail strip.
 */
@Composable
private fun CleanupGroupCompactCard(
    group: HomeCleanupGroupSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val hasItems = group.photos.isNotEmpty()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CharcoalSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (hasItems) CardBorderSlate else CardBorderSlate.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .roboPressScale(enabled = hasItems)
            .clickable(enabled = hasItems) {
                RoboHaptics.explicitAction(haptic)
                onClick()
            }
            .testTag("smart_batch_card_${group.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = group.accentColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = group.icon,
                            contentDescription = null,
                            tint = group.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                RoboPill(
                    text = LuminaViewModel.formatBytes(group.totalBytes),
                    accentColor = if (hasItems) group.accentColor else TextMuted
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (hasItems) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (hasItems) "${group.photos.size} items ready" else "All clean ✓",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }

            if (group.photos.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    group.photos.take(3).forEach { photo ->
                        PhotoThumbnailView(
                            photo = photo,
                            showBadges = false,
                            cornerRadius = 8.dp,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    if (group.photos.size > 3) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElevatedSlate,
                            border = BorderStroke(1.dp, CardBorderSlate),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "+${group.photos.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncrementalScanProgressPanel(
    progress: ScanProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ElevatedSlate,
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("incremental_scan_progress_panel")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (progress.isPaused) "Scan Paused" else "Scanning Library",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = progress.homeStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Text(
                    text = "${progress.analyzed} / ${progress.total.coerceAtLeast(progress.analyzed)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            LinearProgressIndicator(
                progress = { progress.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = ElectricBlue,
                trackColor = CharcoalSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (progress.isPaused) {
                        OutlinedButton(
                            onClick = onResume,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("resume_scan_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("pause_scan_button")
                        ) {
                            Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, DestructiveRed.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("cancel_scan_button")
                    ) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = null,
                            tint = DestructiveRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel", style = MaterialTheme.typography.labelSmall, color = DestructiveRed)
                    }
                }
            }
        }
    }
}

/**
 * AI Suggestions section on Home (Pass 4 Section B).
 */
@Composable
private fun AiHomeSuggestionsStrip(
    suggestions: List<AiHomeSuggestion>,
    onOpenSuggestion: (AiHomeSuggestion) -> Unit,
    onPreviewNaturalPlan: (String) -> Unit
) {
    var naturalQueryInput by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.testTag("ai_home_suggestions_section")
    ) {
        RoboSectionHeader(
            title = "AI Suggestions",
            subtitle = "Smart cleanup recommendations ready for review"
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(suggestions, key = { it.id }) { suggestion ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CharcoalSurface,
                    border = BorderStroke(1.dp, Color(suggestion.accentColorHex).copy(alpha = 0.45f)),
                    modifier = Modifier
                        .width(248.dp)
                        .roboPressScale()
                        .clickable { onOpenSuggestion(suggestion) }
                        .testTag("ai_home_suggestion_${suggestion.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RoboPill(
                                text = "${suggestion.candidatePhotos.size} items",
                                accentColor = Color(suggestion.accentColorHex)
                            )
                            Text(
                                text = LuminaViewModel.formatBytes(suggestion.recoverableBytes),
                                style = MaterialTheme.typography.labelMedium,
                                color = KeepEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = suggestion.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = suggestion.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (suggestion.candidatePhotos.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    suggestion.candidatePhotos.take(5).forEach { photo ->
                                        PhotoThumbnailView(
                                            photo = photo,
                                            showBadges = false,
                                            cornerRadius = 6.dp,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Natural-Language Cleanup Plan Bar
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CharcoalSurface,
            border = BorderStroke(1.dp, CardBorderSlate),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(18.dp)
                )
                OutlinedTextField(
                    value = naturalQueryInput,
                    onValueChange = { naturalQueryInput = it },
                    placeholder = {
                        Text(
                            text = "Ask e.g. \"Clean screenshots older than 30 days\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = ElevatedSlate,
                        unfocusedContainerColor = ElevatedSlate
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_natural_cleanup_input")
                )
                Button(
                    onClick = {
                        val q = naturalQueryInput.trim().ifBlank { "Clean screenshots older than 30 days" }
                        onPreviewNaturalPlan(q)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("home_preview_cleanup_plan_button")
                ) {
                    Text(
                        text = "Plan",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Browse & Search section on Home (Pass 4 Section B).
 */
@Composable
private fun BrowseAndSearchSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: PhotoCategory?,
    onSelectCategory: (PhotoCategory?) -> Unit,
    filteredPhotos: List<PhotoEntity>,
    onInspectPhotoDetail: (PhotoEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RoboSectionHeader(
            title = "Browse & Search",
            subtitle = "${filteredPhotos.size} items in library"
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    "Search titles, text in screenshots, or labels...",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextSecondary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CharcoalSurface,
                unfocusedContainerColor = CharcoalSurface,
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = CardBorderSlate,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input")
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricBlue.copy(alpha = 0.22f),
                        selectedLabelColor = ElectricBlue
                    ),
                    modifier = Modifier.testTag("filter_chip_ALL")
                )
            }
            items(PhotoCategory.entries.toList(), key = { it.name }) { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCategory(category) },
                    label = { Text(category.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricBlue.copy(alpha = 0.22f),
                        selectedLabelColor = ElectricBlue
                    ),
                    modifier = Modifier.testTag("filter_chip_${category.name}")
                )
            }
        }

        if (filteredPhotos.isEmpty()) {
            SectionEmptyStateCard(
                title = "No Matching Media",
                message = "Try clearing your search or selecting another category filter.",
                icon = Icons.Filled.FolderOpen
            )
        } else {
            val chunkedRows = remember(filteredPhotos) { filteredPhotos.take(24).chunked(3) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chunkedRows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { photo ->
                            RoboMediaCard(
                                onClick = { onInspectPhotoDetail(photo) },
                                isBestShot = photo.isBestShotInCluster,
                                isSelected = photo.triageStatusEnum == TriageStatus.KEEP,
                                cornerRadius = 12.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(116.dp)
                                    .testTag("grid_photo_${photo.id}")
                            ) {
                                PhotoThumbnailView(
                                    photo = photo,
                                    showBadges = true,
                                    cornerRadius = 12.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatColumn(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

/**
 * Storage Health Card with Circular Electric-Blue Progress Ring (from RoboPhoto UI Concept).
 */
@Composable
private fun StorageHealthRingCard(
    allPhotos: List<PhotoEntity>,
    cleanableBytes: Long,
    onClick: () -> Unit
) {
    val totalLibraryBytes = remember(allPhotos) {
        allPhotos.sumOf { it.fileSizeBytes }.coerceAtLeast(1L)
    }
    val cleanRatio = remember(totalLibraryBytes, cleanableBytes) {
        ((totalLibraryBytes - cleanableBytes).toFloat() / totalLibraryBytes.toFloat()).coerceIn(0.15f, 0.98f)
    }
    val healthPercent = (cleanRatio * 100f).toInt()
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(RoboRadius.Large),
        color = CharcoalSurface,
        border = BorderStroke(1.dp, CardBorderSlate),
        modifier = Modifier
            .fillMaxWidth()
            .roboPressScale()
            .clickable {
                RoboHaptics.swipeThresholdTick(haptic)
                onClick()
            }
            .testTag("storage_health_ring_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Circular Electric-Blue Ring Gauge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(60.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 6.dp.toPx()
                    drawArc(
                        color = ElevatedSlate,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ElectricBlue,
                        startAngle = -90f,
                        sweepAngle = 360f * cleanRatio,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "$healthPercent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Storage Health",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Open Free Up Space Goal",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "${LuminaViewModel.formatBytes(cleanableBytes)} reclaimable of ${LuminaViewModel.formatBytes(totalLibraryBytes)} indexed",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                LinearProgressIndicator(
                    progress = { cleanRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = ElectricBlue,
                    trackColor = ElevatedSlate
                )
            }
        }
    }
}

/**
 * Recent Moments 3-Column Portrait Card Strip (from RoboPhoto UI Concept).
 */
@Composable
private fun RecentMomentsPortraitStrip(
    allPhotos: List<PhotoEntity>,
    onInspectPhotoDetail: (PhotoEntity) -> Unit,
    onOpenAllInReview: () -> Unit
) {
    val moments = remember(allPhotos) {
        val grouped = allPhotos
            .filter { !it.isVideo }
            .groupBy { it.folderName.ifBlank { it.categoryEnum.displayName } }
            .values
            .mapNotNull { list -> list.maxByOrNull { it.overallQualityScore } }
            .take(3)
        if (grouped.size >= 3) {
            grouped
        } else {
            allPhotos.sortedByDescending { it.overallQualityScore }.take(3)
        }
    }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }

    if (moments.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(RoboRadius.Large),
        color = CharcoalSurface,
        border = BorderStroke(1.dp, CardBorderSlate),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recent_moments_strip_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElevatedSlate,
                        border = BorderStroke(1.dp, CardBorderSlate),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Collections,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Recent Moments",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "From your library",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = ElevatedSlate,
                    border = BorderStroke(1.dp, CardBorderSlate),
                    modifier = Modifier
                        .size(30.dp)
                        .clickable { onOpenAllInReview() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Review Moments",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                moments.forEach { photo ->
                    val momentTitle = remember(photo.folderName, photo.title) {
                        photo.folderName.takeIf { it.isNotBlank() && !it.equals("Camera", ignoreCase = true) }
                            ?: photo.categoryEnum.displayName
                    }
                    val dateText = remember(photo.dateTakenEpochMs) {
                        dateFormat.format(Date(photo.dateTakenEpochMs))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .roboPressScale()
                            .clickable { onInspectPhotoDetail(photo) },
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PhotoThumbnailView(
                            photo = photo,
                            showBadges = false,
                            cornerRadius = 10.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(106.dp)
                        )
                        Text(
                            text = momentTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }
                repeat((3 - moments.size).coerceAtLeast(0)) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
