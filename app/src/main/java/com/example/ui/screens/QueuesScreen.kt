package com.example.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.LensBlur
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
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
import com.example.ui.theme.SpineCyan
import com.example.ui.theme.SpineViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel
import com.example.ui.viewmodel.SmartCleanupBatch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueuesScreen(
    uiState: LuminaUiState,
    onStartSprint: () -> Unit,
    onOpenGoalPlanner: () -> Unit,
    onOpenByokSheet: () -> Unit,
    onReviewQueueInSwipe: (String?) -> Unit,
    onBatchVaultQueue: (List<PhotoEntity>, String) -> Unit,
    onInspectPhoto: (PhotoEntity) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCategoryFilterSelect: (PhotoCategory?) -> Unit,
    onTriggerRescan: () -> Unit,
    onPhotosPicked: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onPhotosPicked(uris)
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onTriggerRescan()
    }

    val requestFullMediaScan = {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        mediaPermissionLauncher.launch(permissions)
    }

    val filteredPhotos = remember(
        uiState.activePhotos,
        uiState.searchQuery,
        uiState.selectedCategoryFilter
    ) {
        uiState.activePhotos.filter { photo ->
            val matchesCategory = uiState.selectedCategoryFilter == null ||
                photo.categoryEnum == uiState.selectedCategoryFilter
            val q = uiState.searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                photo.title.lowercase().contains(q) ||
                photo.folderName.lowercase().contains(q) ||
                photo.ocrText.lowercase().contains(q) ||
                photo.aiDescription.lowercase().contains(q) ||
                photo.semanticTags.lowercase().contains(q) ||
                photo.categoryEnum.label.lowercase().contains(q)
            matchesCategory && matchesSearch
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Stealth Graphite Hero Cleanup Card
        item(key = "hero_cleanup_card") {
            StealthCleanupHeroCard(
                cleanableBytes = uiState.totalCleanableBytes,
                totalLibraryBytes = uiState.totalLibraryBytes,
                readyToReviewCount = uiState.unreviewedCount,
                reviewBinCount = uiState.vaultPhotos.size,
                reviewBinBytes = uiState.vaultRecoverableBytes,
                onStartCleanup = { onReviewQueueInSwipe(null) },
                onScanPhone = requestFullMediaScan,
                onPickGooglePhotos = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onStartSprint = onStartSprint,
                onOpenGoalPlanner = onOpenGoalPlanner
            )
        }

        // 2. Smart Cleanup Groups
        if (uiState.smartBatches.isNotEmpty()) {
            item(key = "smart_groups_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cleanup Groups",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${uiState.smartBatches.sumOf { it.photos.size }} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            items(
                items = uiState.smartBatches,
                key = { it.id }
            ) { batch ->
                StealthCleanupGroupCard(
                    batch = batch,
                    onReviewInSwipe = { onReviewQueueInSwipe(batch.id) },
                    onMoveExtrasToBin = {
                        onBatchVaultQueue(batch.photos, batch.title)
                    },
                    onInspectPhoto = onInspectPhoto
                )
            }
        }

        // 3. Search & Quick Category Filters
        item(key = "search_and_category_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Browse Library",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("semantic_search_input"),
                    placeholder = {
                        Text(
                            text = "Search photos, videos, folders, or text...",
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = TextSecondary
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear search",
                                    tint = TextSecondary
                                )
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
                    )
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        PhotoCategory.VIDEO,
                        PhotoCategory.SCREENSHOT,
                        PhotoCategory.BLURRY,
                        PhotoCategory.RECEIPT,
                        PhotoCategory.DOCUMENT,
                        PhotoCategory.LANDSCAPE
                    ).forEach { category ->
                        val selected = uiState.selectedCategoryFilter == category
                        FilterChip(
                            selected = selected,
                            onClick = { onCategoryFilterSelect(category) },
                            label = { Text(category.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue.copy(alpha = 0.18f),
                                selectedLabelColor = ElectricBlue,
                                containerColor = CharcoalSurface,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = CardBorderSlate,
                                selectedBorderColor = ElectricBlue.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // 4. Library Items or Clean Empty State
        if (filteredPhotos.isEmpty()) {
            item(key = "empty_library_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (uiState.allPhotos.isEmpty()) {
                                "Ready to Scan Your Library"
                            } else {
                                "No Matching Media Found"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.allPhotos.isEmpty()) {
                                "Scan your phone's photos and videos to find duplicates, blurry shots, old screenshots, and large videos."
                            } else {
                                "Try clearing your search or category filter to view all indexed items."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        if (uiState.allPhotos.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = requestFullMediaScan,
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("empty_scan_phone_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhoneAndroid,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Scan Phone", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CardBorderSlate),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("empty_google_photos_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Google Photos", color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            items(
                items = filteredPhotos,
                key = { it.stableIdentityKey }
            ) { photo ->
                CleanMediaRowCard(
                    photo = photo,
                    onClick = { onInspectPhoto(photo) }
                )
            }
        }
    }
}

@Composable
private fun StealthCleanupHeroCard(
    cleanableBytes: Long,
    totalLibraryBytes: Long,
    readyToReviewCount: Int,
    reviewBinCount: Int,
    reviewBinBytes: Long,
    onStartCleanup: () -> Unit,
    onScanPhone: () -> Unit,
    onPickGooglePhotos: () -> Unit,
    onStartSprint: () -> Unit,
    onOpenGoalPlanner: () -> Unit
) {
    val cleanableRatio = if (totalLibraryBytes > 0L) {
        (cleanableBytes.toFloat() / totalLibraryBytes.toFloat()).coerceIn(0.04f, 1f)
    } else {
        0.05f
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = BorderStroke(1.dp, CardBorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top storage headline matching the dark UI concept
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${LuminaViewModel.formatBytes(cleanableBytes)} can be cleaned",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (readyToReviewCount > 0) {
                            "$readyToReviewCount items ready to review • ${LuminaViewModel.formatBytes(totalLibraryBytes)} indexed"
                        } else {
                            "Library analyzed • ${LuminaViewModel.formatBytes(totalLibraryBytes)} indexed"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                IconButton(
                    onClick = onScanPhone,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ElevatedSlate)
                        .testTag("rescan_library_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Rescan library",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Subtle progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { cleanableRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = ElectricBlue,
                    trackColor = ElevatedSlate
                )
                if (reviewBinCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$reviewBinCount item(s) waiting in Review Bin",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = LuminaViewModel.formatBytes(reviewBinBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = DestructiveRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Primary Electric-Blue CTA: [ Start Cleanup ]
            Button(
                onClick = onStartCleanup,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_cleanup_primary_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Swipe,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Start Cleanup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Secondary Quick Actions Row: Scan Phone | Google Photos | 5-Min Sprint | Goal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecondaryActionPill(
                    icon = Icons.Filled.PhoneAndroid,
                    label = "Scan Phone",
                    onClick = onScanPhone,
                    testTag = "scan_full_phone_button",
                    modifier = Modifier.weight(1f)
                )
                SecondaryActionPill(
                    icon = Icons.Filled.AddPhotoAlternate,
                    label = "Google Photos",
                    onClick = onPickGooglePhotos,
                    testTag = "import_photos_button",
                    modifier = Modifier.weight(1f)
                )
                SecondaryActionPill(
                    icon = Icons.Filled.Bolt,
                    label = "Sprint",
                    onClick = onStartSprint,
                    testTag = "start_sprint_button",
                    modifier = Modifier.weight(0.8f)
                )
                SecondaryActionPill(
                    icon = Icons.Filled.AutoAwesome,
                    label = "Goal",
                    onClick = onOpenGoalPlanner,
                    testTag = "open_goal_planner_button",
                    modifier = Modifier.weight(0.75f)
                )
            }
        }
    }
}

@Composable
private fun SecondaryActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = ElevatedSlate,
        border = BorderStroke(1.dp, CardBorderSlate)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TextPrimary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StealthCleanupGroupCard(
    batch: SmartCleanupBatch,
    onReviewInSwipe: () -> Unit,
    onMoveExtrasToBin: () -> Unit,
    onInspectPhoto: (PhotoEntity) -> Unit
) {
    val groupIcon = when (batch.id) {
        "queue_duplicates" -> Icons.Filled.ContentCopy
        "queue_old_screenshots" -> Icons.Filled.ScreenshotMonitor
        "queue_videos" -> Icons.Filled.Videocam
        "queue_blurry" -> Icons.Filled.LensBlur
        else -> Icons.Filled.FolderSpecial
    }
    val accentColor = Color(batch.spineColorHex)
    val isProtectedGroup = batch.id == "queue_receipts_protected"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReviewInSwipe() }
            .testTag("smart_batch_card_${batch.id}"),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = groupIcon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = batch.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = batch.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElevatedSlate,
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Text(
                        text = batch.badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Identity-keyed horizontal thumbnail strip
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = batch.photos.take(10),
                    key = { it.stableIdentityKey }
                ) { photo ->
                    PhotoThumbnailView(
                        photo = photo,
                        modifier = Modifier
                            .size(width = 88.dp, height = 88.dp)
                            .clickable { onInspectPhoto(photo) }
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onReviewInSwipe,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElevatedSlate,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("review_batch_button_${batch.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Review (${batch.photos.size})", fontWeight = FontWeight.SemiBold)
                }

                if (!isProtectedGroup) {
                    OutlinedButton(
                        onClick = onMoveExtrasToBin,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorderSlate),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vault_batch_button_${batch.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = DestructiveRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Move All to Bin", color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanMediaRowCard(
    photo: PhotoEntity,
    onClick: () -> Unit
) {
    key(photo.stableIdentityKey) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag("forensic_photo_row_${photo.id}"),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = photo.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = LuminaViewModel.formatBytes(photo.fileSizeBytes),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = photo.humanFriendlyWhy,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ForensicPillBadge(
                            text = photo.categoryEnum.label,
                            accentColor = if (photo.isVideo) ElectricBlue else TextSecondary
                        )
                        if (photo.isBestShotInCluster) {
                            ForensicPillBadge(
                                text = "Best Shot",
                                accentColor = KeepEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}
