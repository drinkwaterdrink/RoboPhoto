package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.example.ui.theme.RoboCard
import com.example.ui.theme.RoboHaptics
import com.example.ui.theme.RoboPill
import com.example.ui.theme.RoboProgressNumber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.roboPressScale
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel

/**
 * Pass 4 Section Q & Section L: Reassuring Review Bin UX.
 * - Header:
 *   Review Bin
 *   37 items • 948 MB
 *   "Nothing here is permanently deleted until you confirm."
 * - Actions:
 *   [ Restore ]  [ Delete Permanently ]
 * - Displays verified OS-confirmed permanent deletion receipt ("1.21 GB actually recovered")
 *   with animated upward counter and NO Undo button for permanent deletions.
 */
@Composable
fun VaultScreen(
    uiState: LuminaUiState,
    onRestorePhotos: (List<PhotoEntity>) -> Unit,
    onRequestPermanentDeleteModal: () -> Unit,
    onInspectPhoto: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val binPhotos = uiState.vaultPhotos
    val recoverableBytes = uiState.vaultRecoverableBytes
    val lastDeletion = uiState.lastDeletionResult
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Verified OS-Confirmed Recovery Receipt Banner (Pass 4 Section L & Q)
            if (lastDeletion != null && lastDeletion.confirmedDeletedCount > 0) {
                item(key = "verified_permanent_delete_receipt") {
                    RoboCard(
                        containerColor = CharcoalSurface,
                        borderColor = KeepEmerald.copy(alpha = 0.55f),
                        modifier = Modifier.testTag("verified_deletion_receipt_card")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = KeepEmerald.copy(alpha = 0.16f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = KeepEmerald,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                RoboProgressNumber(
                                    targetValue = lastDeletion.confirmedFreedBytes,
                                    formatter = { bytes -> "${LuminaViewModel.formatBytes(bytes)} actually recovered" },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = KeepEmerald,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${lastDeletion.confirmedDeletedCount} item(s) permanently deleted from device storage.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            item(key = "review_bin_header") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    border = BorderStroke(1.dp, CardBorderSlate)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Review Bin",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${binPhotos.size} items • ${LuminaViewModel.formatBytes(recoverableBytes)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            RoboPill(
                                text = "Staged Safely",
                                accentColor = KeepEmerald
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ElevatedSlate,
                            border = BorderStroke(1.dp, CardBorderSlate)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Nothing here is permanently deleted until you confirm.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (binPhotos.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        RoboHaptics.explicitAction(haptic)
                                        onRestorePhotos(binPhotos)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CardBorderSlate),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .roboPressScale()
                                        .testTag("vault_restore_all_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.RestoreFromTrash,
                                        contentDescription = null,
                                        tint = KeepEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Restore",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        RoboHaptics.explicitAction(haptic)
                                        onRequestPermanentDeleteModal()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DestructiveRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.35f)
                                        .height(48.dp)
                                        .roboPressScale()
                                        .testTag("vault_empty_permanent_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteForever,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Delete Permanently",
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (binPhotos.isEmpty()) {
                item(key = "empty_bin_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                        border = BorderStroke(1.dp, CardBorderSlate)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
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
                                text = "Review Bin is Empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Items you move to the Review Bin wait here safely until you confirm permanent deletion.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(
                    items = binPhotos,
                    key = { it.stableIdentityKey }
                ) { photo ->
                    key(photo.stableIdentityKey) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onInspectPhoto(photo) }
                                .testTag("vault_item_${photo.id}"),
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
                                    showBadges = false,
                                    modifier = Modifier.size(68.dp)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = photo.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
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

                                OutlinedButton(
                                    onClick = {
                                        RoboHaptics.explicitAction(haptic)
                                        onRestorePhotos(listOf(photo))
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CardBorderSlate),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("restore_single_${photo.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.RestoreFromTrash,
                                        contentDescription = "Restore",
                                        tint = KeepEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Restore", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
