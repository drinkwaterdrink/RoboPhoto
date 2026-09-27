package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.ui.components.PhotoThumbnailView
import com.example.ui.components.SpineCard
import com.example.ui.components.TelemetryPill
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpineAmber
import com.example.ui.theme.SpineBlue
import com.example.ui.theme.SpineCoral
import com.example.ui.theme.SpineCyan
import com.example.ui.theme.SpineEmerald
import com.example.ui.theme.SpineViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SwipeDeckScreen(
    uiState: LuminaUiState,
    onSelectSwipeQueue: (String?) -> Unit,
    onSwipeDecision: (PhotoEntity, TriageStatus) -> Unit,
    onUndoLast: () -> Unit,
    onInspectPhotoDetail: (PhotoEntity) -> Unit,
    onStartSprint: () -> Unit,
    onStopSprint: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine deck candidates based on active queue filter or unreviewed status
    val deckCandidates = remember(
        uiState.activePhotos,
        uiState.activeQueueIdForSwipe,
        uiState.smartBatches
    ) {
        val batchMatch = uiState.smartBatches.firstOrNull { it.id == uiState.activeQueueIdForSwipe }
        if (batchMatch != null) {
            batchMatch.photos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }
        } else {
            uiState.activePhotos
                .filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }
                .sortedByDescending { it.junkConfidence }
        }
    }

    val topPhoto = deckCandidates.firstOrNull()
    val nextPhoto = deckCandidates.getOrNull(1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("swipe_deck_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Gamified 5-Minute Sprint Banner or Queue Selector Header
        if (uiState.sprintState.isActive) {
            val minutes = uiState.sprintState.remainingSeconds / 60
            val seconds = uiState.sprintState.remainingSeconds % 60
            val progress = (uiState.sprintState.completedCount.toFloat() /
                uiState.sprintState.targetCount.coerceAtLeast(1)).coerceIn(0f, 1f)

            SpineCard(
                spineColor = SpineAmber,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                tint = SpineAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = String.format(Locale.US, "5-MIN SPRINT • %02d:%02d", minutes, seconds),
                                style = MaterialTheme.typography.titleSmall,
                                color = SpineAmber
                            )
                        }
                        TelemetryPill(
                            text = "${uiState.sprintState.completedCount}/${uiState.sprintState.targetCount} • Freed ${LuminaViewModel.formatBytes(uiState.sprintState.recoveredBytesInSprint)}",
                            accentColor = SpineEmerald
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        color = SpineAmber,
                        trackColor = ElevatedSlate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.activeQueueIdForSwipe == null,
                        onClick = { onSelectSwipeQueue(null) },
                        label = { Text("All Unreviewed (${deckCandidates.size})") },
                        leadingIcon = {
                            Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    if (uiState.activeQueueIdForSwipe != null) {
                        FilterChip(
                            selected = true,
                            onClick = { onSelectSwipeQueue(null) },
                            label = { Text("Clear Batch Filter") }
                        )
                    }
                }

                Button(
                    onClick = onStartSprint,
                    colors = ButtonDefaults.buttonColors(containerColor = SpineAmber),
                    modifier = Modifier.testTag("start_sprint_from_swipe")
                ) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        tint = Color(0xFF1F1300),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "5m Sprint",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF1F1300)
                    )
                }
            }
        }

        // 2. Swipe Gesture Instructions & Undo Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Left: Vault  •  ↑ Up: Archive  •  → Right: Keep",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            if (uiState.lastUndoRecord != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ElevatedSlate,
                    border = BorderStroke(1.dp, SpineAmber.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("swipe_undo_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onUndoLast,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo last decision",
                                tint = SpineAmber,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Undo",
                            style = MaterialTheme.typography.labelMedium,
                            color = SpineAmber
                        )
                    }
                }
            }
        }

        // 3. Main Interactive Card Stack
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (topPhoto == null) {
                EmptySwipeDeckState(
                    onResetFilter = { onSelectSwipeQueue(null) },
                    onUndoLast = onUndoLast,
                    hasUndo = uiState.lastUndoRecord != null
                )
            } else {
                // Background Next Card Preview for depth
                if (nextPhoto != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .graphicsLayer {
                                scaleX = 0.94f
                                scaleY = 0.94f
                                alpha = 0.55f
                            },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                        border = BorderStroke(1.dp, CardBorderSlate)
                    ) {
                        PhotoThumbnailView(
                            photo = nextPhoto,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 22.dp
                        )
                    }
                }

                // Active Foreground Draggable Card
                DraggableTriageCard(
                    keyId = topPhoto.id,
                    photo = topPhoto,
                    onDecision = { status -> onSwipeDecision(topPhoto, status) },
                    onInspectClick = { onInspectPhotoDetail(topPhoto) }
                )
            }
        }

        // 4. Tactile Bottom Control Dock (48dp+ Accessibility Targets)
        if (topPhoto != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trash Vault Button (Left)
                Button(
                    onClick = { onSwipeDecision(topPhoto, TriageStatus.TRASH_VAULT) },
                    colors = ButtonDefaults.buttonColors(containerColor = SpineCoral),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .weight(1f)
                        .testTag("swipe_btn_vault")
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Move to Vault", tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Vault", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Favorite / Archive Button (Up)
                Button(
                    onClick = { onSwipeDecision(topPhoto, TriageStatus.FAVORITE_ARCHIVE) },
                    colors = ButtonDefaults.buttonColors(containerColor = SpineBlue),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .weight(0.95f)
                        .testTag("swipe_btn_archive")
                ) {
                    Icon(Icons.Filled.BookmarkAdded, contentDescription = "Archive", tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Archive", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Keep Button (Right)
                Button(
                    onClick = { onSwipeDecision(topPhoto, TriageStatus.KEEP) },
                    colors = ButtonDefaults.buttonColors(containerColor = SpineEmerald),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .weight(1f)
                        .testTag("swipe_btn_keep")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Keep Safe", tint = Color(0xFF041E15))
                    Spacer(Modifier.width(6.dp))
                    Text("Keep", color = Color(0xFF041E15), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DraggableTriageCard(
    keyId: Long,
    photo: PhotoEntity,
    onDecision: (TriageStatus) -> Unit,
    onInspectClick: () -> Unit
) {
    var offsetX by remember(keyId) { mutableFloatStateOf(0f) }
    var offsetY by remember(keyId) { mutableFloatStateOf(0f) }

    val animatedX by animateFloatAsState(targetValue = offsetX, label = "card_x")
    val animatedY by animateFloatAsState(targetValue = offsetY, label = "card_y")

    val swipeThreshold = 210f

    Card(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
            .graphicsLayer {
                rotationZ = (animatedX / 28f).coerceIn(-14f, 14f)
            }
            .pointerInput(keyId) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            offsetX < -swipeThreshold -> {
                                onDecision(TriageStatus.TRASH_VAULT)
                                offsetX = 0f
                                offsetY = 0f
                            }
                            offsetX > swipeThreshold -> {
                                onDecision(TriageStatus.KEEP)
                                offsetX = 0f
                                offsetY = 0f
                            }
                            offsetY < -swipeThreshold -> {
                                onDecision(TriageStatus.FAVORITE_ARCHIVE)
                                offsetX = 0f
                                offsetY = 0f
                            }
                            else -> {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                    onDragCancel = {
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
            .testTag("active_swipe_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = BorderStroke(
            width = 2.dp,
            color = when {
                offsetX < -80f -> SpineCoral
                offsetX > 80f -> SpineEmerald
                offsetY < -80f -> SpineBlue
                photo.junkConfidence >= 0.75f -> SpineCoral.copy(alpha = 0.65f)
                else -> CardBorderSlate
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PhotoThumbnailView(
                photo = photo,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 22.dp
            )

            // Top & Bottom Gradient Scrims for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to ObsidianBg.copy(alpha = 0.82f),
                            0.28f to Color.Transparent,
                            0.58f to Color.Transparent,
                            1.0f to ObsidianBg.copy(alpha = 0.96f)
                        )
                    )
            )

            // Top Forensics Header Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TelemetryPill(
                        text = photo.categoryEnum.label,
                        accentColor = Color(photo.categoryEnum.spineHex)
                    )
                    TelemetryPill(
                        text = "${(photo.junkConfidence * 100).toInt()}% Junk Conf",
                        accentColor = if (photo.junkConfidence >= 0.7f) SpineCoral else SpineEmerald
                    )
                }

                IconButton(
                    onClick = onInspectClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ObsidianBg.copy(alpha = 0.75f))
                        .testTag("inspect_card_forensics_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "Inspect Full Forensics",
                        tint = SpineViolet,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Dynamic Gesture Stamp Indicator
            when {
                offsetX < -75f -> {
                    StampOverlayBadge(
                        text = "QUARANTINE VAULT",
                        color = SpineCoral,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                offsetX > 75f -> {
                    StampOverlayBadge(
                        text = "KEEP SAFE",
                        color = SpineEmerald,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                offsetY < -75f -> {
                    StampOverlayBadge(
                        text = "ARCHIVE / FAVORITE",
                        color = SpineBlue,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Bottom Detailed Telemetry & AI Recommendation Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (photo.isBestShotInCluster) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = SpineEmerald)
                    } else if (photo.sentimentalProtected) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = SpineCyan)
                    }
                    Text(
                        text = photo.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = photo.aiDescription.ifBlank { photo.junkReason },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (photo.ocrText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SpineCyan.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, SpineCyan.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "OCR: ${photo.ocrText}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpineCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Telemetry Metrics Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TelemetryPill(
                        text = LuminaViewModel.formatBytes(photo.fileSizeBytes),
                        accentColor = TextPrimary
                    )
                    TelemetryPill(
                        text = "Sharp ${photo.sharpnessScore}/100",
                        accentColor = if (photo.sharpnessScore >= 70) SpineEmerald else SpineCoral
                    )
                    TelemetryPill(
                        text = "Exp ${photo.exposureScore}/100",
                        accentColor = SpineAmber
                    )
                    TelemetryPill(
                        text = "dHash ${photo.dHash.take(6)}",
                        accentColor = SpineViolet
                    )
                }

                Text(
                    text = "AI Recommendation: ${photo.junkReason}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (photo.junkConfidence >= 0.7f) SpineCoral else SpineEmerald
                )
            }
        }
    }
}

@Composable
private fun StampOverlayBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = ObsidianBg.copy(alpha = 0.88f),
        border = BorderStroke(3.dp, color)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun EmptySwipeDeckState(
    onResetFilter: () -> Unit,
    onUndoLast: () -> Unit,
    hasUndo: Boolean
) {
    SpineCard(
        spineColor = SpineEmerald,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = SpineEmerald,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Queue Fully Triaged!",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "Every photo in this batch has been reviewed. Non-destructive deletions are safely waiting in your Quarantine Vault.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onResetFilter,
                    colors = ButtonDefaults.buttonColors(containerColor = SpineEmerald)
                ) {
                    Text("View All Queues", color = Color(0xFF041E15), fontWeight = FontWeight.Bold)
                }
                if (hasUndo) {
                    Button(
                        onClick = onUndoLast,
                        colors = ButtonDefaults.buttonColors(containerColor = ElevatedSlate)
                    ) {
                        Text("Undo Last", color = TextPrimary)
                    }
                }
            }
        }
    }
}
