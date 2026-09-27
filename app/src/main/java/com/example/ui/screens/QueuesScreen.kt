package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.domain.rules.NaturalLanguageRuleEngine
import com.example.ui.components.EncryptedCacheBadgeStrip
import com.example.ui.components.ForensicPhotoListItem
import com.example.ui.components.PhotoThumbnailView
import com.example.ui.components.SpineCard
import com.example.ui.components.TelemetryPill
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpineAmber
import com.example.ui.theme.SpineCoral
import com.example.ui.theme.SpineCyan
import com.example.ui.theme.SpineEmerald
import com.example.ui.theme.SpineViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel
import com.example.ui.viewmodel.SmartCleanupBatch

@Composable
fun QueuesScreen(
    uiState: LuminaUiState,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelect: (PhotoCategory?) -> Unit,
    onOpenQueueInSwipe: (String?) -> Unit,
    onBatchMoveToVault: (List<PhotoEntity>, String) -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit,
    onQuickKeep: (PhotoEntity) -> Unit,
    onQuickVault: (PhotoEntity) -> Unit,
    onImportPhotosClick: () -> Unit,
    onStartFiveMinSprint: () -> Unit,
    onOpenGoalPlanner: () -> Unit,
    onOpenByokAiHub: () -> Unit,
    onTriggerRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredPhotos = remember(
        uiState.activePhotos,
        uiState.searchQuery,
        uiState.selectedCategoryFilter
    ) {
        val searched = if (uiState.searchQuery.isNotBlank()) {
            NaturalLanguageRuleEngine.semanticSearch(uiState.searchQuery, uiState.activePhotos)
        } else {
            uiState.activePhotos
        }
        if (uiState.selectedCategoryFilter != null) {
            searched.filter { it.categoryEnum == uiState.selectedCategoryFilter }
        } else {
            searched
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("queues_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Storage Impact Forensics Hero Card
        item {
            StorageForensicsHeroBanner(
                totalLibraryBytes = uiState.totalLibraryBytes,
                recoverableQueueBytes = uiState.potentialQueueSavingsBytes,
                vaultStagedBytes = uiState.vaultRecoverableBytes,
                activePhotoCount = uiState.activePhotos.size,
                onRescanClick = onTriggerRescan
            )
        }

        // 2. Hardware AES-256 Encrypted Cache & BYOK Status Strip
        item {
            EncryptedCacheBadgeStrip(
                entriesCount = uiState.encryptedCacheEntries,
                hitRatePercent = uiState.encryptedCacheHitRate,
                privacyTitle = uiState.aiConfig.privacyMode.title,
                activeProviderLabel = uiState.aiConfig.activeProvider.displayName,
                onOpenAiHub = onOpenByokAiHub
            )
        }

        // 3. Tactical 4-Button Action Bar (Import, 5-Min Sprint, Goal Planner, BYOK AI)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionTile(
                    icon = Icons.Filled.AddPhotoAlternate,
                    title = "Google Photos",
                    subtitle = "Photos & Videos",
                    accentColor = SpineCyan,
                    onClick = onImportPhotosClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_import_photos")
                )
                QuickActionTile(
                    icon = Icons.Filled.Timer,
                    title = "5m Sprint",
                    subtitle = "Fast Triage",
                    accentColor = SpineAmber,
                    onClick = onStartFiveMinSprint,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_five_min_sprint")
                )
                QuickActionTile(
                    icon = Icons.Filled.Storage,
                    title = "Goal Plan",
                    subtitle = "Free ${uiState.goalTargetMegabytes}MB",
                    accentColor = SpineEmerald,
                    onClick = onOpenGoalPlanner,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_goal_planner")
                )
                QuickActionTile(
                    icon = Icons.Filled.AutoAwesome,
                    title = "BYOK AI",
                    subtitle = uiState.aiConfig.activeProvider.id,
                    accentColor = SpineViolet,
                    onClick = onOpenByokAiHub,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_byok_ai_hub")
                )
            }
        }

        // 4. Semantic & Natural-Language Search Bar + Preset Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "Semantic & OCR Search (e.g. 'dog outside', 'serial numbers', 'receipts 2023')",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = SpineCyan)
                    },
                    trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = TextSecondary)
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpineCyan,
                        unfocusedBorderColor = CardBorderSlate,
                        focusedContainerColor = CharcoalSurface,
                        unfocusedContainerColor = CharcoalSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("semantic_search_input")
                )

                // Quick semantic query chips
                val sampleQueries = listOf(
                    "videos",
                    "screenshots",
                    "blurry photos",
                    "duplicate burst",
                    "receipts"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sampleQueries.forEach { preset ->
                        val active = uiState.searchQuery.equals(preset, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) SpineCyan.copy(alpha = 0.22f) else CharcoalSurface,
                            border = BorderStroke(1.dp, if (active) SpineCyan else CardBorderSlate),
                            modifier = Modifier.clickable {
                                onSearchQueryChange(if (active) "" else preset)
                            }
                        ) {
                            Text(
                                text = "\"$preset\"",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) SpineCyan else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. AI Cleanup Queue Batches (shown when not actively filtering by search text)
        if (uiState.searchQuery.isBlank() && uiState.smartBatches.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI Cleanup Queues",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "${uiState.smartBatches.size} intelligent batches",
                        style = MaterialTheme.typography.labelMedium,
                        color = SpineAmber
                    )
                }
            }

            items(uiState.smartBatches, key = { it.id }) { batch ->
                SmartBatchCard(
                    batch = batch,
                    onReviewInSwipe = { onOpenQueueInSwipe(batch.id) },
                    onStageAllToVault = {
                        onBatchMoveToVault(batch.photos, batch.title)
                    }
                )
            }
        }

        // 6. Smart Collections Category Filter Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smart Collections & Forensics Index",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "${filteredPhotos.size} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedCategoryFilter == null,
                        onClick = { onCategorySelect(null) },
                        label = { Text("All (${uiState.activePhotos.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpineEmerald.copy(alpha = 0.22f),
                            selectedLabelColor = SpineEmerald
                        )
                    )
                    PhotoCategory.entries.forEach { category ->
                        val count = uiState.activePhotos.count { it.categoryEnum == category }
                        if (count > 0) {
                            val selected = uiState.selectedCategoryFilter == category
                            val catColor = Color(category.spineHex)
                            FilterChip(
                                selected = selected,
                                onClick = { onCategorySelect(category) },
                                label = { Text("${category.label} ($count)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = catColor.copy(alpha = 0.22f),
                                    selectedLabelColor = catColor
                                ),
                                modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // 7. Photo & Video Forensics Feed (or Clean Real-Library Onboarding Card when empty)
        if (uiState.activePhotos.isEmpty()) {
            item {
                SpineCard(
                    spineColor = SpineCyan,
                    containerColor = CharcoalSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_library_scan_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Ready to Scan Your Real Photos & Videos",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "All sample demo images have been removed. Tap 'Scan Full Phone' to index your device camera roll (photos & videos), or tap 'Google Photos Picker' to select cloud/local media.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onTriggerRescan,
                                colors = ButtonDefaults.buttonColors(containerColor = SpineEmerald),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("empty_state_scan_phone_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFF041E15),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Scan Full Phone",
                                    color = Color(0xFF041E15),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            OutlinedButton(
                                onClick = onImportPhotosClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("empty_state_google_photos_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = SpineCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Google Photos",
                                    color = SpineCyan,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredPhotos, key = { it.id }) { photo ->
                ForensicPhotoListItem(
                    photo = photo,
                    onPhotoClick = onPhotoClick,
                    onQuickVaultClick = { onQuickVault(photo) },
                    onQuickKeepClick = { onQuickKeep(photo) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StorageForensicsHeroBanner(
    totalLibraryBytes: Long,
    recoverableQueueBytes: Long,
    vaultStagedBytes: Long,
    activePhotoCount: Int,
    onRescanClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_vault_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianBg.copy(alpha = 0.78f),
                                ObsidianBg.copy(alpha = 0.94f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "STORAGE IMPACT FORENSICS",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpineEmerald
                        )
                        Text(
                            text = "Recoverable: ${LuminaViewModel.formatBytes(recoverableQueueBytes + vaultStagedBytes)}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextPrimary
                        )
                    }
                    OutlinedButton(
                        onClick = onRescanClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("rescan_library_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Scan Phone Library",
                            tint = SpineEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Scan Phone",
                            style = MaterialTheme.typography.labelLarge,
                            color = SpineEmerald
                        )
                    }
                }

                // Multi-segment storage forensic bar
                val totalSafe = totalLibraryBytes.coerceAtLeast(1L).toFloat()
                val queueRatio = (recoverableQueueBytes.toFloat() / totalSafe).coerceIn(0.08f, 0.55f)
                val vaultRatio = (vaultStagedBytes.toFloat() / totalSafe).coerceIn(0.02f, 0.35f)
                val keepRatio = (1f - queueRatio - vaultRatio).coerceAtLeast(0.15f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(ElevatedSlate)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(queueRatio)
                            .height(10.dp)
                            .background(SpineAmber)
                    )
                    if (vaultStagedBytes > 0L) {
                        Box(
                            modifier = Modifier
                                .weight(vaultRatio)
                                .height(10.dp)
                                .background(SpineCoral)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(keepRatio)
                            .height(10.dp)
                            .background(SpineEmerald)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryPill(
                        text = "AI Queues: ${LuminaViewModel.formatBytes(recoverableQueueBytes)}",
                        accentColor = SpineAmber
                    )
                    TelemetryPill(
                        text = "In Vault: ${LuminaViewModel.formatBytes(vaultStagedBytes)}",
                        accentColor = SpineCoral
                    )
                    TelemetryPill(
                        text = "$activePhotoCount Indexed",
                        accentColor = SpineEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = CharcoalSurface,
        border = BorderStroke(1.dp, CardBorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SmartBatchCard(
    batch: SmartCleanupBatch,
    onReviewInSwipe: () -> Unit,
    onStageAllToVault: () -> Unit
) {
    val spineColor = Color(batch.spineColorHex)
    val isProtectedBatch = batch.id == "queue_receipts_protected"

    SpineCard(
        spineColor = spineColor,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_batch_${batch.id}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = batch.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = batch.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TelemetryPill(
                    text = batch.badgeText,
                    accentColor = spineColor
                )
            }

            // Thumbnail strip preview
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                batch.photos.take(4).forEach { photo ->
                    PhotoThumbnailView(
                        photo = photo,
                        modifier = Modifier.size(54.dp),
                        cornerRadius = 8.dp
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Impact: ${LuminaViewModel.formatBytes(batch.totalBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = spineColor
                    )
                    Text(
                        text = "${batch.photos.size} items ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReviewInSwipe,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("swipe_batch_${batch.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Swipe Review")
                }

                if (!isProtectedBatch) {
                    Button(
                        onClick = onStageAllToVault,
                        colors = ButtonDefaults.buttonColors(containerColor = spineColor),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stage_batch_${batch.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            tint = Color(0xFF090D16),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Vault (${LuminaViewModel.formatBytes(batch.totalBytes)})",
                            color = Color(0xFF090D16),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
