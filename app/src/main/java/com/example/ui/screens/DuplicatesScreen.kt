package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.SpineCyan
import com.example.ui.theme.SpineEmerald
import com.example.ui.theme.SpineViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DuplicateClusterGroup
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel

@Composable
fun DuplicatesScreen(
    uiState: LuminaUiState,
    onStageClusterRedundants: (DuplicateClusterGroup) -> Unit,
    onStageAllClustersRedundants: (List<PhotoEntity>) -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val allRedundantPhotos = uiState.duplicateClusters.flatMap { it.redundantVariants }
    val totalClusterSavings = allRedundantPhotos.sumOf { it.fileSizeBytes }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("duplicates_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Summary Header
        item {
            SpineCard(
                spineColor = SpineAmber,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Perceptual Deduplication & Best-Shot",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "64-bit dHash/pHash Hamming clustering + Laplacian sharpness scoring",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        TelemetryPill(
                            text = "${uiState.duplicateClusters.size} Clusters",
                            accentColor = SpineAmber
                        )
                    }

                    if (allRedundantPhotos.isNotEmpty()) {
                        Button(
                            onClick = { onStageAllClustersRedundants(allRedundantPhotos) },
                            colors = ButtonDefaults.buttonColors(containerColor = SpineEmerald),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("clean_all_duplicates_button")
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF041E15),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Keep All Best Shots & Vault ${allRedundantPhotos.size} Extras (${LuminaViewModel.formatBytes(totalClusterSavings)})",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF041E15),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (uiState.duplicateClusters.isEmpty()) {
            item {
                SpineCard(spineColor = SpineEmerald) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = SpineEmerald,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            text = "Zero Duplicate Clusters Remaining",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "All redundant burst shots have been moved to your Quarantine Vault while keeping the sharpest Best-Shot safe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(uiState.duplicateClusters, key = { it.clusterId }) { cluster ->
                DuplicateClusterCard(
                    cluster = cluster,
                    onStageRedundants = { onStageClusterRedundants(cluster) },
                    onPhotoClick = onPhotoClick
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DuplicateClusterCard(
    cluster: DuplicateClusterGroup,
    onStageRedundants: () -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit
) {
    SpineCard(
        spineColor = SpineEmerald,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cluster_card_${cluster.clusterId}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cluster.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "dHash Prefix: ${cluster.bestShot.dHash.take(8)} • Recoverable: ${LuminaViewModel.formatBytes(cluster.recoverableBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                TelemetryPill(
                    text = "Best Shot Locked",
                    accentColor = SpineEmerald
                )
            }

            // Horizontal Comparison Carousel of All Variants in Cluster
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                cluster.allMembers.forEach { member ->
                    val isWinner = member.isBestShotInCluster
                    val borderColor = if (isWinner) SpineEmerald else SpineCoral.copy(alpha = 0.7f)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ElevatedSlate,
                        border = BorderStroke(if (isWinner) 2.dp else 1.dp, borderColor),
                        modifier = Modifier
                            .width(168.dp)
                            .clickable { onPhotoClick(member) }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                PhotoThumbnailView(
                                    photo = member,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(106.dp),
                                    cornerRadius = 8.dp
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isWinner) SpineEmerald else SpineCoral,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        if (isWinner) {
                                            Icon(
                                                Icons.Filled.Star,
                                                contentDescription = null,
                                                tint = Color(0xFF041E15),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        Text(
                                            text = if (isWinner) "BEST SHOT" else "REDUNDANT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isWinner) Color(0xFF041E15) else Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = member.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TelemetryPill(
                                    text = "Sharp ${member.sharpnessScore}",
                                    accentColor = if (isWinner) SpineEmerald else SpineCoral
                                )
                                TelemetryPill(
                                    text = "Exp ${member.exposureScore}",
                                    accentColor = SpineCyan
                                )
                            }

                            Text(
                                text = "${LuminaViewModel.formatBytes(member.fileSizeBytes)} • dHash ${member.dHash.take(6)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onStageRedundants,
                colors = ButtonDefaults.buttonColors(containerColor = SpineCoral),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("stage_cluster_${cluster.clusterId}")
            ) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Keep Best Shot & Vault ${cluster.redundantVariants.size} Redundant (${LuminaViewModel.formatBytes(cluster.recoverableBytes)})",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
