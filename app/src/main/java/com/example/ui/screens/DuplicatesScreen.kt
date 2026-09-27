package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.ui.components.BestShotAnimatedBadge
import com.example.ui.components.FlickerCompareDialog
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
import com.example.ui.theme.RoboMotion
import com.example.ui.theme.RoboPill
import com.example.ui.theme.RoboPrimaryButton
import com.example.ui.theme.RoboProgressNumber
import com.example.ui.theme.RoboRadius
import com.example.ui.theme.RoboSecondaryButton
import com.example.ui.theme.RoboSectionHeader
import com.example.ui.theme.SpineAmber
import com.example.ui.theme.SpineCyan
import com.example.ui.theme.SpineViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.roboPressScale
import com.example.ui.viewmodel.DuplicateClusterGroup
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CollectionsPrimaryTab(val label: String) {
    CLEANUP("Cleanup"),
    SMART_COLLECTIONS("Smart Collections")
}

private enum class CleanupCollectionFilter(val label: String) {
    ALL("All Cleanup"),
    EXACT_DUPLICATES("Exact Duplicates"),
    SIMILAR_PHOTOS("Similar Photos"),
    BURST_SEQUENCES("Burst Sequences"),
    SIMILAR_VIDEOS("Similar Videos"),
    SCREENSHOTS("Screenshots")
}

private enum class SmartSubjectFilter(val label: String, val icon: ImageVector, val accent: Color) {
    ALL("All Smart", Icons.Filled.Collections, ElectricBlue),
    PEOPLE("People", Icons.Filled.Person, ElectricBlue),
    PETS("Pets", Icons.Filled.Pets, SpineAmber),
    PLANTS("Plants", Icons.Filled.Spa, KeepEmerald),
    RECEIPTS("Receipts", Icons.Filled.Receipt, SpineCyan),
    DOCUMENTS("Documents", Icons.Filled.Description, SpineViolet),
    DOWNLOADS("Downloads", Icons.Filled.Download, ElectricBlue)
}

/**
 * Pass 4 Sections H, I, J, P:
 * - Information Architecture split into two clean tabs:
 *   1. Cleanup (Exact Duplicates, Similar Photos, Burst Sequences, Similar Videos, Screenshots)
 *   2. Smart Collections (People, Pets, Plants, Receipts, Documents, Downloads)
 * - Cluster Collapse Effect (Section H):
 *   1. Selected Best Shot remains
 *   2. Redundant thumbnails gently move/stack toward it
 *   3. Resolved card compresses
 *   4. Reclaimed storage count animates upward
 *   5. Card transitions into completed state then disappears (~520 ms)
 * - Best Shot Animation (Section I) & Flicker Compare Dialog (Section J).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuplicatesScreen(
    uiState: LuminaUiState,
    onTrashRedundantForCluster: (DuplicateClusterGroup) -> Unit,
    onTrashAllRedundantDuplicates: (List<PhotoEntity>) -> Unit,
    onInspectPhotoDetail: (PhotoEntity) -> Unit,
    onRenameCluster: (String, String) -> Unit,
    onMarkNotDuplicate: (PhotoEntity) -> Unit,
    onSelectBestShot: (String, Long) -> Unit,
    onSelectCategoryFilter: (PhotoCategory?) -> Unit,
    onOpenQueueInSwipeDeck: (String?) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(CollectionsPrimaryTab.CLEANUP) }
    var selectedCleanupFilter by remember { mutableStateOf(CleanupCollectionFilter.ALL) }
    var selectedSmartFilter by remember { mutableStateOf(SmartSubjectFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    // Flicker compare dialog state (Pass 4 Section J)
    var activeFlickerClusterId by remember { mutableStateOf<String?>(null) }
    val activeFlickerCluster = remember(activeFlickerClusterId, uiState.duplicateClusters) {
        activeFlickerClusterId?.let { cid -> uiState.duplicateClusters.find { it.clusterId == cid } }
    }

    // Cluster Collapse Animation state (Pass 4 Section H):
    // Maps clusterId -> collapsing stage (true while animating collapse before committing batch bin)
    val collapsingClusters = remember { mutableStateMapOf<String, Boolean>() }
    var bonusAnimatingSavedBytes by remember { mutableStateOf(0L) }

    val totalDuplicateSavingsBytes = remember(uiState.duplicateClusters, bonusAnimatingSavedBytes) {
        uiState.duplicateClusters.sumOf { it.recoverableBytes }
    }

    val filteredClusters = remember(
        uiState.duplicateClusters,
        selectedCleanupFilter,
        searchQuery
    ) {
        uiState.duplicateClusters.filter { cluster ->
            val matchesType = when (selectedCleanupFilter) {
                CleanupCollectionFilter.ALL -> true
                CleanupCollectionFilter.EXACT_DUPLICATES -> cluster.type == MediaClusterType.EXACT_DUPLICATE
                CleanupCollectionFilter.SIMILAR_PHOTOS -> cluster.type == MediaClusterType.NEAR_DUPLICATE
                CleanupCollectionFilter.BURST_SEQUENCES -> cluster.type == MediaClusterType.BURST_SEQUENCE
                CleanupCollectionFilter.SIMILAR_VIDEOS -> cluster.type == MediaClusterType.SIMILAR_VIDEO
                CleanupCollectionFilter.SCREENSHOTS -> cluster.type == MediaClusterType.SCREENSHOT_VARIANT ||
                    cluster.allMembers.any { it.categoryEnum == PhotoCategory.SCREENSHOT }
            }
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                cluster.title.lowercase().contains(q) ||
                cluster.reason.lowercase().contains(q) ||
                cluster.allMembers.any { it.title.lowercase().contains(q) || it.semanticLabels.lowercase().contains(q) }
            matchesType && matchesSearch
        }
    }

    val screenshotCandidates = remember(uiState.activePhotos, searchQuery) {
        uiState.activePhotos.filter {
            it.categoryEnum == PhotoCategory.SCREENSHOT &&
                (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.ocrSnippet.contains(searchQuery, ignoreCase = true))
        }
    }

    val smartCollectionGroups = remember(uiState.activePhotos) {
        buildSmartSubjectMap(uiState.activePhotos)
    }

    val displayedSmartPhotos = remember(smartCollectionGroups, selectedSmartFilter, searchQuery, uiState.activePhotos) {
        val base = when (selectedSmartFilter) {
            SmartSubjectFilter.ALL -> uiState.activePhotos
            else -> smartCollectionGroups[selectedSmartFilter].orEmpty()
        }
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            base
        } else {
            base.filter {
                it.title.lowercase().contains(q) ||
                    it.semanticLabels.lowercase().contains(q) ||
                    it.ocrSnippet.lowercase().contains(q) ||
                    it.categoryEnum.displayName.lowercase().contains(q)
            }
        }
    }

    fun triggerClusterCollapseAndResolve(cluster: DuplicateClusterGroup) {
        if (collapsingClusters[cluster.clusterId] == true) return
        collapsingClusters[cluster.clusterId] = true
        RoboHaptics.completion(haptic)
        bonusAnimatingSavedBytes += cluster.recoverableBytes
        coroutineScope.launch {
            // Allow 480ms for redundant thumbnails to stack into Best Shot & card to compress
            delay(RoboMotion.CLUSTER_COLLAPSE_MS.toLong())
            onTrashRedundantForCluster(cluster)
            delay(120L)
            collapsingClusters.remove(cluster.clusterId)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("duplicates_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header & Segmented Primary Tabs: [ Cleanup ] | [ Smart Collections ] (Pass 4 Section P)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Collections",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            RoboProgressNumber(
                                targetValue = totalDuplicateSavingsBytes,
                                formatter = { bytes ->
                                    "${uiState.duplicateClusters.size} cleanup groups • ${LuminaViewModel.formatBytes(bytes)} recoverable"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = KeepEmerald,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (activeTab == CollectionsPrimaryTab.CLEANUP && uiState.duplicateClusters.isNotEmpty()) {
                            val allRedundant = remember(uiState.duplicateClusters) {
                                uiState.duplicateClusters.flatMap { it.redundantVariants }
                            }
                            OutlinedButton(
                                onClick = {
                                    RoboHaptics.completion(haptic)
                                    onTrashAllRedundantDuplicates(allRedundant)
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .roboPressScale()
                                    .testTag("clean_all_duplicates_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Clean All (${allRedundant.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Primary Two-Tab Switcher: Cleanup | Smart Collections
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CharcoalSurface,
                        border = BorderStroke(1.dp, CardBorderSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CollectionsPrimaryTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) ElectricBlue else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            RoboHaptics.swipeThresholdTick(haptic)
                                            activeTab = tab
                                        }
                                        .testTag("collections_tab_${tab.name}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) TextPrimary else TextSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = if (activeTab == CollectionsPrimaryTab.CLEANUP) {
                                    "Filter duplicate groups, bursts, or screenshots..."
                                } else {
                                    "Search People, Pets, Plants, Receipts, Documents..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CharcoalSurface,
                            unfocusedContainerColor = CharcoalSurface,
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = CardBorderSlate
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("collections_search_input")
                    )
                }
            }

            // 2A. CLEANUP TAB: Exact Duplicates, Similar Photos, Burst Sequences, Similar Videos, Screenshots
            if (activeTab == CollectionsPrimaryTab.CLEANUP) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(CleanupCollectionFilter.entries.toList(), key = { it.name }) { filter ->
                            val selected = selectedCleanupFilter == filter
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    RoboHaptics.swipeThresholdTick(haptic)
                                    selectedCleanupFilter = filter
                                },
                                label = { Text(filter.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue.copy(alpha = 0.22f),
                                    selectedLabelColor = ElectricBlue
                                ),
                                modifier = Modifier.testTag("cleanup_collection_filter_${filter.name}")
                            )
                        }
                    }
                }

                if (selectedCleanupFilter == CleanupCollectionFilter.SCREENSHOTS && filteredClusters.isEmpty()) {
                    // Show screenshot collection cards directly if no multi-screenshot clusters
                    item {
                        ScreenshotCleanupCollectionCard(
                            screenshots = screenshotCandidates,
                            onReviewScreenshots = { onOpenQueueInSwipeDeck("queue_old_screenshots") },
                            onInspectPhotoDetail = onInspectPhotoDetail
                        )
                    }
                } else if (filteredClusters.isEmpty()) {
                    item {
                        SectionEmptyStateCard(
                            title = "No Groups in ${selectedCleanupFilter.label}",
                            message = "Your library has no remaining redundant items in this cleanup category.",
                            icon = Icons.Filled.CheckCircle
                        )
                    }
                } else {
                    items(
                        items = filteredClusters,
                        key = { it.clusterId }
                    ) { cluster ->
                        val isCollapsing = collapsingClusters[cluster.clusterId] == true
                        ClusterInteractiveComparisonCard(
                            cluster = cluster,
                            isCollapsing = isCollapsing,
                            onResolveCluster = { triggerClusterCollapseAndResolve(cluster) },
                            onOpenFlickerCompare = { activeFlickerClusterId = cluster.clusterId },
                            onInspectPhotoDetail = onInspectPhotoDetail,
                            onSelectBestShot = { photoId ->
                                RoboHaptics.bestShotSelection(haptic)
                                onSelectBestShot(cluster.clusterId, photoId)
                            },
                            onRenameCluster = { newTitle ->
                                onRenameCluster(cluster.clusterId, newTitle)
                            },
                            onMarkNotDuplicate = onMarkNotDuplicate
                        )
                    }
                }
            } else {
                // 2B. SMART COLLECTIONS TAB: People, Pets, Plants, Receipts, Documents, Downloads (Concept Horizontal Cover-Photo Rows + Grid)
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(SmartSubjectFilter.entries.toList(), key = { it.name }) { filter ->
                            val selected = selectedSmartFilter == filter
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) ElectricBlue else ElevatedSlate,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selected) ElectricBlue else CardBorderSlate
                                ),
                                modifier = Modifier
                                    .roboPressScale()
                                    .clickable {
                                        RoboHaptics.swipeThresholdTick(haptic)
                                        selectedSmartFilter = filter
                                    }
                                    .testTag("smart_collection_pill_${filter.name}")
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) TextPrimary else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val subjectFilters = remember {
                            SmartSubjectFilter.entries.filter { it != SmartSubjectFilter.ALL }
                        }
                        subjectFilters.forEach { subject ->
                            val subjectPhotos = smartCollectionGroups[subject].orEmpty()
                            val coverPhoto = subjectPhotos.firstOrNull() ?: uiState.activePhotos.firstOrNull()
                            val folderCount = remember(subjectPhotos) {
                                subjectPhotos.map { it.folderName.ifBlank { "Camera" } }.distinct().size.coerceAtLeast(1)
                            }
                            val isSelected = selectedSmartFilter == subject
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = CharcoalSurface,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) ElectricBlue else CardBorderSlate
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .roboPressScale()
                                    .clickable {
                                        RoboHaptics.swipeThresholdTick(haptic)
                                        selectedSmartFilter = if (isSelected) SmartSubjectFilter.ALL else subject
                                    }
                                    .testTag("smart_collection_tile_${subject.name}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Cover photo thumbnail on left (like right phone in UI concept image)
                                    if (coverPhoto != null) {
                                        PhotoThumbnailView(
                                            photo = coverPhoto,
                                            showBadges = false,
                                            cornerRadius = 12.dp,
                                            modifier = Modifier.size(64.dp)
                                        )
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = ElevatedSlate,
                                            border = BorderStroke(1.dp, CardBorderSlate),
                                            modifier = Modifier.size(64.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = subject.icon,
                                                    contentDescription = null,
                                                    tint = subject.accent,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Title + photo count + sub-collection count in center
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = subject.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${subjectPhotos.size} photos",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = "$folderCount collections • ${LuminaViewModel.formatBytes(subjectPhotos.sumOf { it.fileSizeBytes })}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }

                                    // Category icon + Chevron on right
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = subject.icon,
                                            contentDescription = null,
                                            tint = subject.accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.ChevronRight,
                                            contentDescription = "Open ${subject.label}",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    RoboSectionHeader(
                        title = selectedSmartFilter.label,
                        subtitle = "${displayedSmartPhotos.size} items organized automatically"
                    )
                }

                if (displayedSmartPhotos.isEmpty()) {
                    item {
                        SectionEmptyStateCard(
                            title = "No Items in ${selectedSmartFilter.label}",
                            message = "Try selecting another Smart Collection or scanning more photos.",
                            icon = Icons.Filled.FolderOpen
                        )
                    }
                } else {
                    item {
                        val rows = remember(displayedSmartPhotos) { displayedSmartPhotos.chunked(3) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rows.forEach { rowPhotos ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowPhotos.forEach { photo ->
                                        RoboMediaCard(
                                            onClick = { onInspectPhotoDetail(photo) },
                                            isBestShot = photo.isBestShotInCluster,
                                            isSelected = photo.triageStatusEnum == TriageStatus.KEEP,
                                            cornerRadius = 12.dp,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(118.dp)
                                                .testTag("smart_collection_photo_${photo.id}")
                                        ) {
                                            PhotoThumbnailView(
                                                photo = photo,
                                                showBadges = true,
                                                cornerRadius = 12.dp,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    repeat(3 - rowPhotos.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Photography-grade Flicker Compare Dialog (Pass 4 Section J)
    if (activeFlickerCluster != null) {
        FlickerCompareDialog(
            cluster = activeFlickerCluster,
            onSelectBestShot = { cid, pid ->
                onSelectBestShot(cid, pid)
            },
            onMoveExtraToBin = { extraPhoto ->
                onTrashAllRedundantDuplicates(listOf(extraPhoto))
            },
            onDismiss = { activeFlickerClusterId = null }
        )
    }
}

/**
 * Pass 4 Sections H & I:
 * Cluster Card with:
 * - Best Shot animated badge & shimmer border when nominated or manually chosen
 * - Cluster Collapse Effect when resolved:
 *   1. Selected Best Shot remains prominent
 *   2. Redundant thumbnails gently move/stack toward it (animated horizontal offset & scale)
 *   3. Resolved card compresses into a sleek "Resolved ✓ +X MB" bar before disappearing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClusterInteractiveComparisonCard(
    cluster: DuplicateClusterGroup,
    isCollapsing: Boolean,
    onResolveCluster: () -> Unit,
    onOpenFlickerCompare: () -> Unit,
    onInspectPhotoDetail: (PhotoEntity) -> Unit,
    onSelectBestShot: (Long) -> Unit,
    onRenameCluster: (String) -> Unit,
    onMarkNotDuplicate: (PhotoEntity) -> Unit
) {
    var isEditingTitle by remember { mutableStateOf(false) }
    var draftTitle by remember(cluster.title) { mutableStateOf(cluster.title) }

    val cardScale by animateFloatAsState(
        targetValue = if (isCollapsing) 0.96f else 1f,
        animationSpec = tween(
            durationMillis = RoboMotion.CLUSTER_COLLAPSE_MS,
            easing = FastOutSlowInEasing
        ),
        label = "clusterCollapseScale"
    )

    val redundantStackOffsetDp by animateDpAsState(
        targetValue = if (isCollapsing) (-72).dp else 0.dp,
        animationSpec = tween(
            durationMillis = (RoboMotion.CLUSTER_COLLAPSE_MS * 0.65f).toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "redundantStackOffset"
    )

    val redundantAlpha by animateFloatAsState(
        targetValue = if (isCollapsing) 0f else 1f,
        animationSpec = tween(
            durationMillis = (RoboMotion.CLUSTER_COLLAPSE_MS * 0.70f).toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "redundantAlpha"
    )

    Surface(
        shape = RoundedCornerShape(RoboRadius.Card),
        color = CharcoalSurface,
        border = BorderStroke(
            width = if (isCollapsing) 1.5.dp else 1.dp,
            color = if (isCollapsing) KeepEmerald else CardBorderSlate
        ),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .testTag("cluster_card_${cluster.clusterId}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title + Cluster Type Pill + Recoverable Bytes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoboPill(
                            text = cluster.type.displayName,
                            accentColor = ElectricBlue
                        )
                        RoboPill(
                            text = "${cluster.allMembers.size} items",
                            accentColor = TextSecondary
                        )
                    }

                    if (isEditingTitle) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = draftTitle,
                                onValueChange = { draftTitle = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (draftTitle.isNotBlank()) {
                                        onRenameCluster(draftTitle)
                                    }
                                    isEditingTitle = false
                                }
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "Save title", tint = KeepEmerald)
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = cluster.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Rename cluster",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { isEditingTitle = true }
                            )
                        }
                    }
                }

                RoboPill(
                    text = if (isCollapsing) {
                        "✓ +${LuminaViewModel.formatBytes(cluster.recoverableBytes)}"
                    } else {
                        "Save ${LuminaViewModel.formatBytes(cluster.recoverableBytes)}"
                    },
                    accentColor = KeepEmerald
                )
            }

            // Collapsed Completed Banner during final stage of Section H animation
            AnimatedVisibility(
                visible = isCollapsing,
                enter = fadeIn(tween(150)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KeepEmerald.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, KeepEmerald.copy(alpha = 0.50f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = KeepEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Best Shot kept • ${cluster.redundantVariants.size} extras moved to Review Bin (+${LuminaViewModel.formatBytes(cluster.recoverableBytes)})",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Member Thumbnails Strip: Best Shot remains anchored while redundant thumbnails gently stack toward it on resolve!
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(
                    items = cluster.allMembers,
                    key = { it.stableIdentityKey }
                ) { member ->
                    val isBest = member.id == cluster.bestShot.id
                    Box(
                        modifier = Modifier
                            .width(136.dp)
                            .offset(x = if (isBest) 0.dp else redundantStackOffsetDp)
                            .graphicsLayer {
                                alpha = if (isBest) 1f else redundantAlpha
                                scaleX = if (!isBest && isCollapsing) 0.78f else 1f
                                scaleY = if (!isBest && isCollapsing) 0.78f else 1f
                            }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(136.dp)
                            ) {
                                RoboMediaCard(
                                    onClick = { onInspectPhotoDetail(member) },
                                    isBestShot = isBest,
                                    cornerRadius = 12.dp,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("cluster_member_${member.id}")
                                ) {
                                    PhotoThumbnailView(
                                        photo = member,
                                        showBadges = false,
                                        cornerRadius = 12.dp,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Best Shot Animated Badge (Pass 4 Section I)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                ) {
                                    BestShotAnimatedBadge(
                                        isBestShot = isBest,
                                        isUserSelected = member.userSelectedBestShot
                                    )
                                }

                                // Tap to nominate as Best Shot button on non-best items
                                if (!isBest) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ObsidianBg.copy(alpha = 0.84f),
                                        border = BorderStroke(1.dp, CardBorderSlate),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                            .clickable { onSelectBestShot(member.id) }
                                            .testTag("select_best_shot_${member.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Star,
                                                contentDescription = "Set as Best Shot",
                                                tint = KeepEmerald,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "Set Best",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    // Remove from cluster (Mark Not Duplicate)
                                    Surface(
                                        shape = CircleShape,
                                        color = ObsidianBg.copy(alpha = 0.82f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(24.dp)
                                            .clickable { onMarkNotDuplicate(member) }
                                            .testTag("mark_not_duplicate_${member.id}")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Keep separate (not a duplicate)",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = member.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isBest) KeepEmerald else TextPrimary,
                                fontWeight = if (isBest) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Sharp ${member.sharpnessScore} • ${LuminaViewModel.formatBytes(member.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Why Best Shot was nominated (concise pills)
            if (cluster.bestShotExplanations.isNotEmpty() && !isCollapsing) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    cluster.bestShotExplanations.take(3).forEach { reason ->
                        RoboPill(
                            text = "Best Shot: $reason",
                            accentColor = KeepEmerald
                        )
                    }
                }
            }

            // Action Buttons: [ Flicker Compare ] | [ Keep Best & Bin Extras ]
            if (!isCollapsing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoboSecondaryButton(
                        text = "Flicker Compare",
                        icon = Icons.Filled.Compare,
                        iconTint = ElectricBlue,
                        onClick = onOpenFlickerCompare,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("flicker_compare_cluster_${cluster.clusterId}")
                    )

                    RoboPrimaryButton(
                        text = "Bin ${cluster.redundantVariants.size} Extra(s)",
                        icon = Icons.Filled.DeleteSweep,
                        containerColor = ElectricBlue,
                        onClick = onResolveCluster,
                        modifier = Modifier
                            .weight(1.15f)
                            .testTag("resolve_cluster_${cluster.clusterId}")
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenshotCleanupCollectionCard(
    screenshots: List<PhotoEntity>,
    onReviewScreenshots: () -> Unit,
    onInspectPhotoDetail: (PhotoEntity) -> Unit
) {
    RoboCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Screenshots Collection",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${screenshots.size} screenshots • ${LuminaViewModel.formatBytes(screenshots.sumOf { it.fileSizeBytes })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                RoboPrimaryButton(
                    text = "Review All",
                    onClick = onReviewScreenshots
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(screenshots.take(12), key = { it.stableIdentityKey }) { shot ->
                    PhotoThumbnailView(
                        photo = shot,
                        showBadges = true,
                        cornerRadius = 10.dp,
                        modifier = Modifier
                            .size(96.dp)
                            .clickable { onInspectPhotoDetail(shot) }
                    )
                }
            }
        }
    }
}

/**
 * Organizes library items into user-friendly Smart Collections (Pass 4 Section P):
 * - People
 * - Pets
 * - Plants
 * - Receipts
 * - Documents
 * - Downloads
 */
private fun buildSmartSubjectMap(activePhotos: List<PhotoEntity>): Map<SmartSubjectFilter, List<PhotoEntity>> {
    val people = activePhotos.filter { photo ->
        val labels = "${photo.semanticLabels} ${photo.title} ${photo.sceneDescription}".lowercase()
        photo.faceCount > 0 ||
            labels.contains("portrait") ||
            labels.contains("person") ||
            labels.contains("people") ||
            labels.contains("selfie") ||
            labels.contains("family") ||
            labels.contains("friends") ||
            photo.categoryEnum == PhotoCategory.PEOPLE
    }

    val pets = activePhotos.filter { photo ->
        val labels = "${photo.semanticLabels} ${photo.title} ${photo.sceneDescription}".lowercase()
        labels.contains("dog") ||
            labels.contains("cat") ||
            labels.contains("pet") ||
            labels.contains("puppy") ||
            labels.contains("kitten") ||
            labels.contains("golden retriever") ||
            labels.contains("animal")
    }

    val plants = activePhotos.filter { photo ->
        val labels = "${photo.semanticLabels} ${photo.title} ${photo.sceneDescription}".lowercase()
        labels.contains("plant") ||
            labels.contains("flower") ||
            labels.contains("garden") ||
            labels.contains("tree") ||
            labels.contains("leaf") ||
            labels.contains("botanical") ||
            labels.contains("nature") ||
            labels.contains("park")
    }

    val receipts = activePhotos.filter { photo ->
        val labels = "${photo.semanticLabels} ${photo.title} ${photo.ocrSnippet}".lowercase()
        photo.categoryEnum == PhotoCategory.RECEIPT ||
            labels.contains("receipt") ||
            labels.contains("invoice") ||
            labels.contains("total") ||
            labels.contains("order")
    }

    val documents = activePhotos.filter { photo ->
        val labels = "${photo.semanticLabels} ${photo.title} ${photo.ocrSnippet}".lowercase()
        photo.categoryEnum == PhotoCategory.DOCUMENT ||
            photo.screenshotSubTypeEnum.isImportantDefault ||
            labels.contains("document") ||
            labels.contains("notes") ||
            labels.contains("whiteboard") ||
            labels.contains("ticket") ||
            labels.contains("boarding")
    }

    val downloads = activePhotos.filter { photo ->
        photo.categoryEnum == PhotoCategory.DOWNLOAD ||
            photo.categoryEnum == PhotoCategory.MEME ||
            photo.bucketName.contains("Download", ignoreCase = true)
    }

    return mapOf(
        SmartSubjectFilter.PEOPLE to people,
        SmartSubjectFilter.PETS to pets,
        SmartSubjectFilter.PLANTS to plants,
        SmartSubjectFilter.RECEIPTS to receipts,
        SmartSubjectFilter.DOCUMENTS to documents,
        SmartSubjectFilter.DOWNLOADS to downloads
    )
}
