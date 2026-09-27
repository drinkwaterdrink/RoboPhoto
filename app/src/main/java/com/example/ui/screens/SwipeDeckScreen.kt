package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.ui.components.ForensicPillBadge
import com.example.ui.components.IntegratedVideoPlayer
import com.example.ui.components.PhotoThumbnailView
import com.example.ui.components.preloadUpcomingMediaItems
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
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwipeDeckScreen(
    uiState: LuminaUiState,
    onSelectQueueFilter: (String?) -> Unit,
    onSwipeDecision: (PhotoEntity, TriageStatus) -> Unit,
    onSkipPhoto: (PhotoEntity) -> Unit,
    onResetSkipped: () -> Unit,
    onUndoLast: () -> Unit,
    onStopSprint: () -> Unit,
    onInspectPhoto: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val baseDeckPhotos = remember(
        uiState.activePhotos,
        uiState.smartBatches,
        uiState.activeQueueIdForSwipe
    ) {
        val unreviewed = uiState.activePhotos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }
        val activeQueue = uiState.smartBatches.find { it.id == uiState.activeQueueIdForSwipe }
        if (activeQueue != null) {
            activeQueue.photos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }
        } else {
            unreviewed.sortedWith(
                compareByDescending<PhotoEntity> { it.junkConfidence }
                    .thenByDescending { it.fileSizeBytes }
            )
        }
    }

    val deckPhotos = remember(baseDeckPhotos, uiState.skippedPhotoIds) {
        val filtered = baseDeckPhotos.filterNot { it.id in uiState.skippedPhotoIds }
        if (filtered.isEmpty() && baseDeckPhotos.isNotEmpty() && uiState.skippedPhotoIds.isNotEmpty()) {
            baseDeckPhotos
        } else {
            filtered
        }
    }

    val topPhoto = deckPhotos.firstOrNull()
    val nextPhoto = deckPhotos.getOrNull(1)
    val thirdPhoto = deckPhotos.getOrNull(2)

    // Preload next 3 media items whenever topPhoto changes
    LaunchedEffect(topPhoto?.stableIdentityKey) {
        if (deckPhotos.size > 1) {
            preloadUpcomingMediaItems(context, deckPhotos.drop(1).take(3))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Sprint Mode Banner (if active)
        AnimatedVisibility(
            visible = uiState.sprintState.isActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SprintActiveHeaderCard(
                remainingSeconds = uiState.sprintState.remainingSeconds,
                completed = uiState.sprintState.completedCount,
                target = uiState.sprintState.targetCount,
                recoveredBytes = uiState.sprintState.recoveredBytesInSprint,
                onStop = onStopSprint
            )
        }

        // Top Review Header + Session Dopamine Pill + Undo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Review",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ElevatedSlate,
                        border = BorderStroke(1.dp, CardBorderSlate)
                    ) {
                        Text(
                            text = "${deckPhotos.size} left",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (uiState.sessionSavedBytes > 0L) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = KeepEmerald.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, KeepEmerald.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "+${LuminaViewModel.formatBytes(uiState.sessionSavedBytes)} cleaned",
                                style = MaterialTheme.typography.labelSmall,
                                color = KeepEmerald,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (uiState.lastUndoRecord != null) {
                OutlinedButton(
                    onClick = onUndoLast,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CardBorderSlate),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("swipe_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Undo,
                        contentDescription = "Undo",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Undo", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                }
            }
        }

        // Queue Filter Pills
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = uiState.activeQueueIdForSwipe == null,
                onClick = { onSelectQueueFilter(null) },
                label = { Text("All Ready (${uiState.unreviewedCount})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricBlue.copy(alpha = 0.18f),
                    selectedLabelColor = ElectricBlue
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = uiState.activeQueueIdForSwipe == null,
                    borderColor = CardBorderSlate,
                    selectedBorderColor = ElectricBlue.copy(alpha = 0.5f)
                ),
                modifier = Modifier.testTag("deck_filter_all")
            )
            uiState.smartBatches.forEach { batch ->
                val selected = uiState.activeQueueIdForSwipe == batch.id
                FilterChip(
                    selected = selected,
                    onClick = { onSelectQueueFilter(if (selected) null else batch.id) },
                    label = {
                        Text(
                            text = "${batch.title} (${batch.photos.size})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricBlue.copy(alpha = 0.18f),
                        selectedLabelColor = ElectricBlue
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = CardBorderSlate,
                        selectedBorderColor = ElectricBlue.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("deck_filter_${batch.id}")
                )
            }
        }

        // Main Interactive Card Stack
        if (topPhoto == null) {
            EmptySwipeDeckCard(
                hasSkipped = uiState.skippedPhotoIds.isNotEmpty(),
                onResetSkipped = onResetSkipped,
                onResetFilter = { onSelectQueueFilter(null) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            key(topPhoto.stableIdentityKey) {
                val offsetX = remember { Animatable(0f) }
                var cardWidthPx by remember { mutableFloatStateOf(1000f) }
                var hasCrossedThresholdHaptic by remember { mutableStateOf(false) }

                val commitThresholdPx = (cardWidthPx * 0.24f).coerceIn(140f, 260f)
                val dragFraction = (offsetX.value / commitThresholdPx).coerceIn(-1.5f, 1.5f)
                val absProgress = abs(dragFraction).coerceIn(0f, 1f)

                fun animateAndCommit(status: TriageStatus) {
                    coroutineScope.launch {
                        val targetX = if (status == TriageStatus.TRASH_VAULT) {
                            -cardWidthPx * 1.25f
                        } else {
                            cardWidthPx * 1.25f
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        offsetX.animateTo(
                            targetValue = targetX,
                            animationSpec = tween(durationMillis = 210)
                        )
                        onSwipeDecision(topPhoto, status)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (size.width > 0) {
                                cardWidthPx = size.width.toFloat()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Third card subtle depth preview
                    if (thirdPhoto != null) {
                        key(thirdPhoto.stableIdentityKey) {
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                                    .scale(0.90f),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = CharcoalSurface.copy(alpha = 0.6f)),
                                border = BorderStroke(1.dp, CardBorderSlate.copy(alpha = 0.5f))
                            ) {}
                        }
                    }

                    // Second card depth preview (scales up smoothly as top card is dragged)
                    if (nextPhoto != null) {
                        key(nextPhoto.stableIdentityKey) {
                            val nextScale = 0.94f + (0.06f * absProgress)
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .scale(nextScale),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                                border = BorderStroke(1.dp, CardBorderSlate)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    PhotoThumbnailView(
                                        photo = nextPhoto,
                                        showBadges = false,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = (0.45f * (1f - absProgress)).coerceIn(0.1f, 0.45f)))
                                    )
                                }
                            }
                        }
                    }

                    // Active Top Swipe Card
                    val borderColor = when {
                        dragFraction > 0.20f -> KeepEmerald.copy(alpha = (0.4f + 0.6f * absProgress).coerceIn(0f, 1f))
                        dragFraction < -0.20f -> DestructiveRed.copy(alpha = (0.4f + 0.6f * absProgress).coerceIn(0f, 1f))
                        else -> CardBorderSlate
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                            .graphicsLayer {
                                rotationZ = (offsetX.value / cardWidthPx.coerceAtLeast(1f)) * 8.5f
                            }
                            .pointerInput(topPhoto.stableIdentityKey) {
                                val velocityTracker = VelocityTracker()
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        velocityTracker.resetTracking()
                                        hasCrossedThresholdHaptic = false
                                    },
                                    onDragEnd = {
                                        val velocityX = velocityTracker.calculateVelocity().x
                                        val currentX = offsetX.value
                                        val crossedRight = currentX > commitThresholdPx ||
                                            (velocityX > 950f && currentX > cardWidthPx * 0.10f)
                                        val crossedLeft = currentX < -commitThresholdPx ||
                                            (velocityX < -950f && currentX < -cardWidthPx * 0.10f)

                                        when {
                                            crossedRight -> animateAndCommit(TriageStatus.KEEP)
                                            crossedLeft -> animateAndCommit(TriageStatus.TRASH_VAULT)
                                            else -> {
                                                coroutineScope.launch {
                                                    offsetX.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            offsetX.animateTo(0f, spring())
                                        }
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        val nextVal = offsetX.value + dragAmount
                                        val crossedNow = abs(nextVal) >= commitThresholdPx
                                        if (crossedNow && !hasCrossedThresholdHaptic) {
                                            hasCrossedThresholdHaptic = true
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } else if (!crossedNow && hasCrossedThresholdHaptic) {
                                            hasCrossedThresholdHaptic = false
                                        }
                                        coroutineScope.launch {
                                            offsetX.snapTo(nextVal)
                                        }
                                    }
                                )
                            }
                            .testTag("swipe_top_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                        border = BorderStroke(1.5.dp, borderColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Visual Media Area (Integrated Video Player for Videos, High-Res Thumbnail for Photos)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                if (topPhoto.isVideo) {
                                    IntegratedVideoPlayer(
                                        photo = topPhoto,
                                        modifier = Modifier.fillMaxSize(),
                                        autoPlay = false
                                    )
                                } else {
                                    PhotoThumbnailView(
                                        photo = topPhoto,
                                        showBadges = false,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { onInspectPhoto(topPhoto) }
                                    )
                                }

                                // Top-Start Metadata & Category Pills
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ObsidianBg.copy(alpha = 0.82f),
                                        border = BorderStroke(1.dp, CardBorderSlate)
                                    ) {
                                        Text(
                                            text = topPhoto.categoryEnum.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ObsidianBg.copy(alpha = 0.82f),
                                        border = BorderStroke(1.dp, CardBorderSlate)
                                    ) {
                                        Text(
                                            text = LuminaViewModel.formatBytes(topPhoto.fileSizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricBlue,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                // Dynamic Swipe Stamp Overlays (KEEP / REVIEW BIN)
                                if (dragFraction > 0.12f) {
                                    val stampScale = (0.85f + 0.25f * absProgress).coerceIn(0.85f, 1.12f)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = KeepEmerald.copy(alpha = (0.20f + 0.72f * absProgress).coerceIn(0.2f, 0.92f)),
                                        border = BorderStroke(2.dp, KeepEmerald),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(top = 54.dp, start = 16.dp)
                                            .scale(stampScale)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "KEEP",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                } else if (dragFraction < -0.12f) {
                                    val stampScale = (0.85f + 0.25f * absProgress).coerceIn(0.85f, 1.12f)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = DestructiveRed.copy(alpha = (0.20f + 0.72f * absProgress).coerceIn(0.2f, 0.92f)),
                                        border = BorderStroke(2.dp, DestructiveRed),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 54.dp, end = 16.dp)
                                            .scale(stampScale)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "REVIEW BIN",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }

                            // Clean, Non-Technical Info Footer + Action Dock
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CharcoalSurface)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = topPhoto.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "${topPhoto.folderName} • ${topPhoto.formattedCaptureDate}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Subtle "Why / Details" pill button
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ElevatedSlate,
                                        border = BorderStroke(1.dp, CardBorderSlate),
                                        modifier = Modifier
                                            .clickable { onInspectPhoto(topPhoto) }
                                            .testTag("swipe_details_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Info,
                                                contentDescription = "Details",
                                                tint = ElectricBlue,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Details",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Human-Friendly Reason Bar (no raw hex hashes)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = ElevatedSlate,
                                    border = BorderStroke(1.dp, CardBorderSlate),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = topPhoto.humanFriendlyWhy,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        ForensicPillBadge(
                                            text = "Quality ${topPhoto.overallQualityScore}",
                                            accentColor = if (topPhoto.overallQualityScore >= 70) KeepEmerald else SpineAmber
                                        )
                                    }
                                }

                                // 4-Action Tactile Control Bar: Bin | Skip | Favorite | Keep
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Move to Review Bin (Left Swipe)
                                    Button(
                                        onClick = { animateAndCommit(TriageStatus.TRASH_VAULT) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DestructiveRed.copy(alpha = 0.16f),
                                            contentColor = DestructiveRed
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, DestructiveRed.copy(alpha = 0.45f)),
                                        contentPadding = PaddingValues(vertical = 12.dp),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .testTag("swipe_vault_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Move to Review Bin",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Bin",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 2. Skip for now
                                    OutlinedButton(
                                        onClick = { onSkipPhoto(topPhoto) },
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, CardBorderSlate),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = ElevatedSlate,
                                            contentColor = TextSecondary
                                        ),
                                        contentPadding = PaddingValues(vertical = 12.dp),
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .testTag("swipe_skip_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SkipNext,
                                            contentDescription = "Skip item",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Skip",
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // 3. Favorite / Archive
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSwipeDecision(topPhoto, TriageStatus.FAVORITE_ARCHIVE)
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(ElevatedSlate)
                                            .testTag("swipe_archive_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.BookmarkAdded,
                                            contentDescription = "Save to Favorites",
                                            tint = ElectricBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // 4. Keep (Right Swipe)
                                    Button(
                                        onClick = { animateAndCommit(TriageStatus.KEEP) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = KeepEmerald,
                                            contentColor = Color(0xFF042016)
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(vertical = 12.dp),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .testTag("swipe_keep_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Keep item",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Keep",
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
    }
}

@Composable
private fun SprintActiveHeaderCard(
    remainingSeconds: Int,
    completed: Int,
    target: Int,
    recoveredBytes: Long,
    onStop: () -> Unit
) {
    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val timeText = String.format(java.util.Locale.US, "%d:%02d", mins, secs)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CharcoalSurface,
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Cleanup Sprint • $timeText",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "$completed/$target • ${LuminaViewModel.formatBytes(recoveredBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = KeepEmerald,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (completed.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape),
                    color = ElectricBlue,
                    trackColor = ElevatedSlate
                )
                Text(
                    text = "End",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier
                        .clickable { onStop() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptySwipeDeckCard(
    hasSkipped: Boolean,
    onResetSkipped: () -> Unit,
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = BorderStroke(1.dp, CardBorderSlate)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = KeepEmerald.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, KeepEmerald.copy(alpha = 0.4f)),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = KeepEmerald,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "All Caught Up",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "You've reviewed every item in this queue. Items moved to the Review Bin can be restored or permanently deleted anytime.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasSkipped) {
                    OutlinedButton(
                        onClick = onResetSkipped,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorderSlate)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Review Skipped Items")
                    }
                }
                Button(
                    onClick = onResetFilter,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Show All Queues", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
