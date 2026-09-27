package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.ui.components.ForensicPillBadge
import com.example.ui.components.PhotoThumbnailView
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.DestructiveRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.KeepEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpineAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DuplicateClusterGroup
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuplicatesScreen(
    uiState: LuminaUiState,
    onVaultRedundantInCluster: (List<PhotoEntity>, String) -> Unit,
    onVaultAllRedundant: (List<PhotoEntity>) -> Unit,
    onInspectPhoto: (PhotoEntity) -> Unit,
    onRenameCluster: (String, String) -> Unit = { _, _ -> },
    onMarkNotDuplicate: (PhotoEntity) -> Unit = {},
    onSelectBestShot: (String, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val clusters = uiState.duplicateClusters
    val allRedundant = remember(clusters) { clusters.flatMap { it.redundantVariants } }
    val totalRecoverable = remember(allRedundant) { allRedundant.sumOf { it.fileSizeBytes } }

    var showCollectionsView by remember { mutableStateOf(false) }
    var selectedCollectionCategory by remember { mutableStateOf<PhotoCategory?>(null) }
    var renamingCluster by remember { mutableStateOf<DuplicateClusterGroup?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    val collectionItems = remember(uiState.activePhotos, selectedCollectionCategory) {
        if (selectedCollectionCategory == null) {
            uiState.activePhotos
        } else {
            uiState.activePhotos.filter { it.categoryEnum == selectedCollectionCategory }
        }
    }

    if (renamingCluster != null) {
        val targetCluster = renamingCluster!!
        AlertDialog(
            onDismissRequest = { renamingCluster = null },
            containerColor = CharcoalSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Rename Group") },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    label = { Text("Group Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameCluster(targetCluster.clusterId, renameInputText)
                        renamingCluster = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingCluster = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Mode Switcher (Duplicates & Similar vs Smart Collections)
        item(key = "collections_top_header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Collections",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${clusters.size} duplicate groups • ${uiState.activePhotos.size} total items",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Segmented Sub-Tab Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CharcoalSurface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val dupSelected = !showCollectionsView
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (dupSelected) ElectricBlue else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showCollectionsView = false }
                            .testTag("tab_duplicate_groups")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "Duplicates & Similar (${clusters.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (dupSelected) Color.White else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val colSelected = showCollectionsView
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (colSelected) ElectricBlue else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showCollectionsView = true }
                            .testTag("tab_media_collections")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "Categories (${PhotoCategory.entries.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (colSelected) Color.White else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (!showCollectionsView) {
            // Summary Card for Duplicate Groups
            item(key = "duplicate_summary_banner") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${LuminaViewModel.formatBytes(totalRecoverable)} in duplicate extras",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Best shots are automatically selected and kept safe. Tap any item to preview or mark 'Not Duplicate'.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        if (allRedundant.isNotEmpty()) {
                            Button(
                                onClick = { onVaultAllRedundant(allRedundant) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricBlue,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vault_all_duplicates_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Keep All Best Shots • Move ${allRedundant.size} Extras to Bin",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (clusters.isEmpty()) {
                item(key = "no_duplicates_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                        border = BorderStroke(1.dp, CardBorderSlate)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = KeepEmerald,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No Duplicate Groups Found",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Your library has no remaining near-duplicate bursts or repeated video clips.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(
                    items = clusters,
                    key = { it.clusterId }
                ) { cluster ->
                    DuplicateClusterCard(
                        cluster = cluster,
                        onVaultRedundant = { selectedToBin ->
                            onVaultRedundantInCluster(selectedToBin, cluster.title)
                        },
                        onRenameClick = {
                            renameInputText = cluster.title
                            renamingCluster = cluster
                        },
                        onMarkNotDuplicate = onMarkNotDuplicate,
                        onSelectBestShot = { photoId ->
                            onSelectBestShot(cluster.clusterId, photoId)
                        },
                        onInspectPhoto = onInspectPhoto
                    )
                }
            }
        } else {
            // Smart Collections View
            item(key = "category_grid_selector") {
                val categoryGroups = remember(uiState.activePhotos) {
                    PhotoCategory.entries.mapNotNull { cat ->
                        val items = uiState.activePhotos.filter { it.categoryEnum == cat }
                        if (items.isNotEmpty()) cat to items else null
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedCollectionCategory == null,
                            onClick = { selectedCollectionCategory = null },
                            label = { Text("All (${uiState.activePhotos.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue.copy(alpha = 0.18f),
                                selectedLabelColor = ElectricBlue
                            )
                        )
                        categoryGroups.forEach { (cat, list) ->
                            val isSel = selectedCollectionCategory == cat
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedCollectionCategory = if (isSel) null else cat },
                                label = { Text("${cat.label} (${list.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue.copy(alpha = 0.18f),
                                    selectedLabelColor = ElectricBlue
                                )
                            )
                        }
                    }
                }
            }

            items(
                items = collectionItems,
                key = { it.stableIdentityKey }
            ) { photo ->
                CollectionItemRow(
                    photo = photo,
                    onInspect = { onInspectPhoto(photo) },
                    onMoveToBin = {
                        onVaultRedundantInCluster(listOf(photo), photo.categoryEnum.label)
                    }
                )
            }
        }
    }
}

@Composable
private fun DuplicateClusterCard(
    cluster: DuplicateClusterGroup,
    onVaultRedundant: (List<PhotoEntity>) -> Unit,
    onRenameClick: () -> Unit,
    onMarkNotDuplicate: (PhotoEntity) -> Unit,
    onSelectBestShot: (Long) -> Unit,
    onInspectPhoto: (PhotoEntity) -> Unit
) {
    // Track which redundant items are selected for moving to Review Bin (default: all non-best shots)
    var unselectedIds by remember(cluster.clusterId, cluster.allMembers.map { it.id }) {
        mutableStateOf(emptySet<Long>())
    }

    val selectedExtras = cluster.redundantVariants.filterNot { it.id in unselectedIds }
    val selectedRecoverableBytes = selectedExtras.sumOf { it.fileSizeBytes }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cluster_card_${cluster.clusterId}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = BorderStroke(1.dp, CardBorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cluster Header with Rename button & recoverable size badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                            IconButton(
                                onClick = onRenameClick,
                                modifier = Modifier
                                    .size(26.dp)
                                    .testTag("rename_cluster_${cluster.clusterId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Rename group",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Text(
                            text = "Best shot kept • ${cluster.redundantVariants.size} similar extra(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                ForensicPillBadge(
                    text = "Save ${LuminaViewModel.formatBytes(selectedRecoverableBytes)}",
                    accentColor = KeepEmerald
                )
            }

            // Horizontal Comparison Strip of Cluster Members (strictly keyed by stableIdentityKey)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = cluster.allMembers,
                    key = { it.stableIdentityKey }
                ) { member ->
                    val isBest = member.id == cluster.bestShot.id
                    val isSelectedForBin = !isBest && (member.id !in unselectedIds)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ElevatedSlate,
                        border = BorderStroke(
                            width = if (isBest) 1.5.dp else 1.dp,
                            color = when {
                                isBest -> KeepEmerald
                                isSelectedForBin -> DestructiveRed.copy(alpha = 0.65f)
                                else -> CardBorderSlate
                            }
                        ),
                        modifier = Modifier.width(172.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(128.dp)
                                    .clickable { onInspectPhoto(member) }
                            ) {
                                PhotoThumbnailView(
                                    photo = member,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Text(
                                text = member.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = LuminaViewModel.formatBytes(member.fileSizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Sharp ${member.sharpnessScore}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBest) KeepEmerald else TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            HorizontalDivider(color = CardBorderSlate)

                            // Per-item Controls: Preview/Play, Set Best / Toggle Bin, Not Duplicate
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isBest) {
                                    ForensicPillBadge(
                                        text = "Kept Best",
                                        accentColor = KeepEmerald
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelectedForBin) {
                                            DestructiveRed.copy(alpha = 0.16f)
                                        } else {
                                            CharcoalSurface
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelectedForBin) DestructiveRed.copy(alpha = 0.5f) else CardBorderSlate
                                        ),
                                        modifier = Modifier.clickable {
                                            unselectedIds = if (member.id in unselectedIds) {
                                                unselectedIds - member.id
                                            } else {
                                                unselectedIds + member.id
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = if (isSelectedForBin) "In Bin Queue" else "Keep Also",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelectedForBin) DestructiveRed else TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (!isBest) {
                                        IconButton(
                                            onClick = { onSelectBestShot(member.id) },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("set_best_shot_${member.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Star,
                                                contentDescription = "Make Best Shot",
                                                tint = SpineAmber,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onMarkNotDuplicate(member) },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("not_duplicate_${member.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.LinkOff,
                                            contentDescription = "Not a duplicate",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Cluster Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onInspectPhoto(cluster.bestShot) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorderSlate),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (cluster.bestShot.isVideo) Icons.Filled.PlayArrow else Icons.Filled.Compare,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (cluster.bestShot.isVideo) "Preview Video" else "Compare",
                        color = TextPrimary
                    )
                }

                Button(
                    onClick = { onVaultRedundant(selectedExtras) },
                    enabled = selectedExtras.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("cluster_vault_extras_${cluster.clusterId}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Bin ${selectedExtras.size} Extra(s)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionItemRow(
    photo: PhotoEntity,
    onInspect: () -> Unit,
    onMoveToBin: () -> Unit
) {
    key(photo.stableIdentityKey) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onInspect),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            border = BorderStroke(1.dp, CardBorderSlate)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PhotoThumbnailView(
                    photo = photo,
                    modifier = Modifier.size(64.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = photo.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${photo.folderName} • ${LuminaViewModel.formatBytes(photo.fileSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = photo.humanFriendlyWhy,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onMoveToBin,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ElevatedSlate)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Move to Review Bin",
                        tint = DestructiveRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
