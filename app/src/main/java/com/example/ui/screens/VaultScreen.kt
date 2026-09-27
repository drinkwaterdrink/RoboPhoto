package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.PhotoEntity
import com.example.ui.components.PhotoThumbnailView
import com.example.ui.components.SpineCard
import com.example.ui.components.TelemetryPill
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpineAmber
import com.example.ui.theme.SpineCoral
import com.example.ui.theme.SpineEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel

@Composable
fun VaultScreen(
    uiState: LuminaUiState,
    onRestorePhotoIds: (List<Long>) -> Unit,
    onConfirmPermanentDelete: (List<PhotoEntity>) -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showTwoStepConfirmDialog by remember { mutableStateOf(false) }
    var twoStepCheckboxChecked by remember { mutableStateOf(false) }

    val vaultPhotos = uiState.vaultPhotos
    val totalVaultBytes = uiState.vaultRecoverableBytes

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("vault_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Quarantine Vault Header & Storage Impact Preview
        item {
            SpineCard(
                spineColor = SpineCoral,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LockClock,
                                contentDescription = null,
                                tint = SpineCoral,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Soft-Delete Quarantine Vault",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "30-day reversible recovery buffer • Original files untouched until verified",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElevatedSlate,
                        border = BorderStroke(1.dp, CardBorderSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Deleting these ${vaultPhotos.size} items will recover:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = LuminaViewModel.formatBytes(totalVaultBytes),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = SpineCoral,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TelemetryPill(
                                text = "2-Step Protected",
                                accentColor = SpineEmerald
                            )
                        }
                    }

                    if (vaultPhotos.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onRestorePhotoIds(vaultPhotos.map { it.id }) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("restore_all_vault_button")
                            ) {
                                Icon(
                                    Icons.Filled.RestoreFromTrash,
                                    contentDescription = null,
                                    tint = SpineEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Restore All", color = SpineEmerald)
                            }

                            Button(
                                onClick = {
                                    twoStepCheckboxChecked = false
                                    showTwoStepConfirmDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SpineCoral),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("open_two_step_purge_button")
                            ) {
                                Icon(
                                    Icons.Filled.DeleteForever,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Purge (${LuminaViewModel.formatBytes(totalVaultBytes)})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (vaultPhotos.isEmpty()) {
            item {
                SpineCard(spineColor = SpineEmerald) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = SpineEmerald,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Quarantine Vault is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Items swiped left or staged by AI Cleanup Queues enter this reversible vault first before any platform-level deletion.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(vaultPhotos, key = { it.id }) { photo ->
                SpineCard(
                    spineColor = SpineCoral,
                    onClick = { onPhotoClick(photo) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_item_${photo.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PhotoThumbnailView(
                            photo = photo,
                            modifier = Modifier.size(68.dp),
                            cornerRadius = 10.dp
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = photo.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = photo.junkReason.ifBlank { photo.aiDescription },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TelemetryPill(
                                    text = LuminaViewModel.formatBytes(photo.fileSizeBytes),
                                    accentColor = SpineCoral
                                )
                                TelemetryPill(
                                    text = "Auto-Purge: 30d",
                                    accentColor = SpineAmber
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { onRestorePhotoIds(listOf(photo.id)) },
                            modifier = Modifier.testTag("restore_item_${photo.id}")
                        ) {
                            Text("Restore", color = SpineEmerald, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Two-Step Permanent Deletion Verification Dialog
    if (showTwoStepConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showTwoStepConfirmDialog = false },
            containerColor = CharcoalSurface,
            icon = {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = SpineCoral,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Two-Step Permanent Deletion",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "You are about to permanently remove ${vaultPhotos.size} item(s) and recover ${LuminaViewModel.formatBytes(totalVaultBytes)} of storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElevatedSlate,
                        border = BorderStroke(1.dp, SpineCoral.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { twoStepCheckboxChecked = !twoStepCheckboxChecked }
                            .testTag("two_step_verification_checkbox")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = twoStepCheckboxChecked,
                                onCheckedChange = { twoStepCheckboxChecked = it },
                                colors = CheckboxDefaults.colors(checkedColor = SpineCoral)
                            )
                            Text(
                                text = "I have reviewed these ${vaultPhotos.size} quarantined items and approve irreversible deletion.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmPermanentDelete(vaultPhotos)
                        showTwoStepConfirmDialog = false
                    },
                    enabled = twoStepCheckboxChecked,
                    colors = ButtonDefaults.buttonColors(containerColor = SpineCoral),
                    modifier = Modifier.testTag("confirm_permanent_purge_button")
                ) {
                    Text(
                        text = "Permanently Delete (${LuminaViewModel.formatBytes(totalVaultBytes)})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTwoStepConfirmDialog = false }) {
                    Text("Keep in Vault", color = TextSecondary)
                }
            }
        )
    }
}
