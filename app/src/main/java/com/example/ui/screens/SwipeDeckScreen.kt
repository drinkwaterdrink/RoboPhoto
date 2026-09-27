package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.domain.ai.AiWorkflowsAndUsageEngine
import com.example.ui.components.FullscreenZoomablePhotoDialog
import com.example.ui.components.IntegratedVideoPlayer
import com.example.ui.components.PhotoThumbnailView
import com.example.ui.components.SectionEmptyStateCard
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.DestructiveRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.KeepEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoboHaptics
import com.example.ui.theme.RoboMotion
import com.example.ui.theme.RoboPill
import com.example.ui.theme.RoboPrimaryButton
import com.example.ui.theme.RoboProgressNumber
import com.example.ui.theme.RoboRadius
import com.example.ui.theme.RoboSecondaryButton
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pass 4 Section D & E: Media-First Review Screen & Precision Swipe Physics.
 * - Dedicates ~70–75% of the useful visual space to the media viewport.
 * - Tightened header & footer with simplified controls:
 *   Header: Review • X left | +Y MB this session | Filter / Undo / History / Details
 *   Media Card: Large edge-to-edge feel with integrated "Why RoboPhoto flagged this" banner
 *   and instant tap-to-open Fullscreen Viewer (which ALSO supports swiping right to Keep & left to Review Bin).
 *   Bottom Bar: [ Review Bin ] [ Skip ] [ Keep ]
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwipeDeckScreen(
    uiState: LuminaUiState,
    onSwipeTriage: (PhotoEntity, TriageStatus) -> Unit,
    onSkipPhoto: (PhotoEntity) -> Unit,
    onResetSkipped: () -> Unit,
    onSelectQueue: (String?) -> Unit,
    onClearCustomReviewSet: () -> Unit,
    onUndoLast: () -> Unit,
    onOpenUndoHistory: () -> Unit,
    onOpenCleanupReceipt: () -> Unit,
    onInspectPhotoDetail: (PhotoEntity) -> Unit,
    onInspectWithAi: (PhotoEntity) -> Unit,
    onStopSprint: () -> Unit,
    onOpenTrashVault: () -> Unit,
    onSelectBestShot: (String, Long) -> Unit = { _, _ -> },
    onOpenFlickerCompare: (DuplicateClusterGroup) -> Unit = {}
) {
    val activeQueue = remember(uiState.smartBatches, uiState.activeQueueIdForSwipe) {
        uiState.smartBatches.find { it.id == uiState.activeQueueIdForSwipe }
    }

    val customSetIds = remember(uiState.customReviewSetPhotos) {
        uiState.customReviewSetPhotos?.map { it.id }?.toSet()
    }

    val baseDeckPhotos = remember(
        uiState.activePhotos,
        activeQueue,
        customSetIds
    ) {
        val raw = when {
            customSetIds != null -> {
                uiState.activePhotos.filter {
                    it.id in customSetIds && it.triageStatusEnum == TriageStatus.UNREVIEWED
                }
            }
            activeQueue != null -> {
                activeQueue.photos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }
            }
            else -> {
                uiState.activePhotos.filter { it.triageStatusEnum == TriageStatus.UNREVIEWED }
            }
        }
        raw.sortedWith(
            compareByDescending<PhotoEntity> {
                when {
                    it.duplicateClusterId != null && !it.isBestShotInCluster -> 3
                    it.categoryEnum == PhotoCategory.BLURRY -> 2
                    it.categoryEnum == PhotoCategory.SCREENSHOT || it.categoryEnum == PhotoCategory.MEME -> 1
                    else -> 0
                }
            }.thenByDescending { it.junkConfidence }
        )
    }

    val deckPhotos = remember(baseDeckPhotos, uiState.skippedPhotoIds) {
        val nonSkipped = baseDeckPhotos.filterNot { it.id in uiState.skippedPhotoIds }
        nonSkipped.ifEmpty { baseDeckPhotos }
    }

    val topPhoto = deckPhotos.firstOrNull()
    val secondPhoto = deckPhotos.getOrNull(1)
    val thirdPhoto = deckPhotos.getOrNull(2)

    val topPhotoCluster = remember(topPhoto?.duplicateClusterId, uiState.duplicateClusters) {
        val cid = topPhoto?.duplicateClusterId
        if (cid.isNullOrBlank()) null else uiState.duplicateClusters.find { it.clusterId == cid }
    }

    var fullscreenViewerOpen by remember { mutableStateOf(false) }
    var showQueueFilterRow by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("swipe_deck_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Calm 5-Minute Cleanup Banner (Pass 4 Section N)
            if (uiState.sprintState.isActive) {
                CalmFiveMinuteCleanupBar(
                    remainingSeconds = uiState.sprintState.remainingSeconds,
                    completedCount = uiState.sprintState.completedCount,
                    targetCount = uiState.sprintState.targetCount,
                    identifiedBytes = uiState.sprintState.recoveredBytesInSprint,
                    onStop = onStopSprint
                )
            }

            // 2. Tightened Primary Header (Pass 4 Section D):
            //    "Review   37 left"
            //    "+742 MB this session"
            //    Right side: [Filter] [Undo] [History] [Details]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Review",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = ElevatedSlate,
                            border = BorderStroke(1.dp, CardBorderSlate)
                        ) {
                            Text(
                                text = "${deckPhotos.size} left",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clickable(enabled = uiState.sessionReviewedCount > 0) {
                                onOpenCleanupReceipt()
                            }
                            .testTag("review_session_savings_row")
                    ) {
                        RoboProgressNumber(
                            targetValue = uiState.sessionSavedBytes,
                            formatter = { bytes -> "+${LuminaViewModel.formatBytes(bytes)} this session" },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.sessionSavedBytes > 0L) KeepEmerald else TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (uiState.customReviewSetTitle != null) {
                            Text(
                                text = "• ${uiState.customReviewSetTitle}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (activeQueue != null) {
                            Text(
                                text = "• ${activeQueue.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Compact Action Icons: Queue Filter, Quick Undo, Undo History, Photo Details
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showQueueFilterRow = !showQueueFilterRow },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (showQueueFilterRow || activeQueue != null || uiState.customReviewSetTitle != null) {
                                    ElectricBlue.copy(alpha = 0.18f)
                                } else {
                                    CharcoalSurface
                                }
                            )
                            .border(1.dp, CardBorderSlate, CircleShape)
                            .testTag("toggle_review_filter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Filter Review Queue",
                            tint = if (showQueueFilterRow || activeQueue != null) ElectricBlue else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (uiState.lastUndoRecord != null) {
                        IconButton(
                            onClick = {
                                RoboHaptics.explicitAction(haptic)
                                onUndoLast()
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ElectricBlue.copy(alpha = 0.16f))
                                .border(1.dp, ElectricBlue.copy(alpha = 0.45f), CircleShape)
                                .testTag("undo_last_swipe_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Undo,
                                contentDescription = "Undo last decision",
                                tint = ElectricBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenUndoHistory,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CharcoalSurface)
                            .border(1.dp, CardBorderSlate, CircleShape)
                            .testTag("open_undo_history_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = "Recent Decisions History (${uiState.undoHistory.size})",
                                tint = if (uiState.undoHistory.isNotEmpty()) TextPrimary else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (topPhoto != null) {
                        IconButton(
                            onClick = { onInspectPhotoDetail(topPhoto) },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CharcoalSurface)
                                .border(1.dp, CardBorderSlate, CircleShape)
                                .testTag("swipe_card_detail_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Details & RoboLab metrics",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Optional collapsible queue filter row (kept tucked away by default to maximize media space)
            AnimatedVisibility(visible = showQueueFilterRow || uiState.customReviewSetTitle != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (uiState.customReviewSetTitle != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ElectricBlue.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.40f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Custom Set: ${uiState.customReviewSetTitle} (${deckPhotos.size} left)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Show All",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { onClearCustomReviewSet() }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("clear_custom_review_set_button")
                                )
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = uiState.activeQueueIdForSwipe == null && uiState.customReviewSetTitle == null,
                                onClick = {
                                    onClearCustomReviewSet()
                                    onSelectQueue(null)
                                },
                                label = {
                                    Text(
                                        text = "All (${uiState.unreviewedCount})",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue.copy(alpha = 0.22f),
                                    selectedLabelColor = ElectricBlue
                                ),
                                modifier = Modifier.testTag("deck_filter_all")
                            )
                        }
                        items(uiState.smartBatches, key = { it.id }) { batch ->
                            val selected = uiState.activeQueueIdForSwipe == batch.id
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectQueue(if (selected) null else batch.id) },
                                label = {
                                    Text(
                                        text = "${batch.title} (${batch.photos.size})",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue.copy(alpha = 0.22f),
                                    selectedLabelColor = ElectricBlue
                                ),
                                modifier = Modifier.testTag("deck_filter_${batch.id}")
                            )
                        }
                    }
                }
            }

            // 3. MAIN MEDIA VIEWPORT (~72% of vertical space!) + Tight Bottom Controls
            if (topPhoto == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SectionEmptyStateCard(
                            title = "Cleanup Complete ✓",
                            message = "${uiState.sessionReviewedCount} reviewed • ${uiState.sessionKeptCount} kept • ${uiState.sessionMovedToBinCount} moved to Review Bin (${LuminaViewModel.formatBytes(uiState.vaultRecoverableBytes)} ready to recover).",
                            icon = Icons.Filled.DoneAll
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (uiState.sessionReviewedCount > 0) {
                                RoboSecondaryButton(
                                    text = "Session Receipt",
                                    icon = Icons.Filled.CheckCircle,
                                    iconTint = KeepEmerald,
                                    onClick = onOpenCleanupReceipt,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_cleanup_receipt_button")
                                )
                            }
                            if (uiState.vaultPhotos.isNotEmpty()) {
                                RoboPrimaryButton(
                                    text = "Review Bin (${uiState.vaultPhotos.size})",
                                    icon = Icons.Filled.DeleteOutline,
                                    containerColor = DestructiveRed,
                                    onClick = onOpenTrashVault,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("empty_deck_open_vault_button")
                                )
                            }
                        }
                    }
                }
            } else {
                // Swipe physics state (Pass 4 Section E)
                val offsetXAnim = remember(topPhoto.stableIdentityKey) { Animatable(0f) }
                var cardWidthPx by remember { mutableFloatStateOf(1000f) }
                var thresholdHapticFired by remember(topPhoto.stableIdentityKey) { mutableStateOf(false) }
                var isCommittingExit by remember(topPhoto.stableIdentityKey) { mutableStateOf(false) }

                val currentOffsetX = offsetXAnim.value
                val commitThresholdPx = (cardWidthPx * RoboMotion.SWIPE_COMMIT_DISTANCE_FRACTION).coerceAtLeast(200f)
                val dragProgress = (abs(currentOffsetX) / commitThresholdPx).coerceIn(0f, 1f)
                val isThresholdLocked = abs(currentOffsetX) >= commitThresholdPx

                // Next card subtly rises/scales as front card is dragged
                val nextCardScale by animateFloatAsState(
                    targetValue = (0.94f + 0.06f * dragProgress).coerceIn(0.94f, 1f),
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "nextCardScale"
                )
                val nextCardAlpha by animateFloatAsState(
                    targetValue = (0.52f + 0.48f * dragProgress).coerceIn(0.52f, 1f),
                    animationSpec = tween(120),
                    label = "nextCardAlpha"
                )

                fun triggerAnimatedDecision(targetStatus: TriageStatus) {
                    if (isCommittingExit) return
                    isCommittingExit = true
                    RoboHaptics.explicitAction(haptic)
                    val targetX = if (targetStatus == TriageStatus.KEEP) {
                        cardWidthPx * 1.25f
                    } else {
                        -cardWidthPx * 1.25f
                    }
                    coroutineScope.launch {
                        // Start identity-safe command immediately while card smoothly exits
                        onSwipeTriage(topPhoto, targetStatus)
                        offsetXAnim.animateTo(
                            targetValue = targetX,
                            animationSpec = tween(
                                durationMillis = RoboMotion.SWIPE_EXIT_MS,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                }

                // Media Stack Container — Takes all available weight (70–75% of screen height!)
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
                    // Third card subtle depth shadow
                    if (thirdPhoto != null) {
                        Surface(
                            shape = RoundedCornerShape(RoboRadius.Large),
                            color = CharcoalSurface.copy(alpha = 0.42f),
                            border = BorderStroke(1.dp, CardBorderSlate.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .graphicsLayer {
                                    scaleX = 0.90f
                                    scaleY = 0.90f
                                    translationY = 16f
                                }
                        ) {}
                    }

                    // Second card in stack (rises & scales smoothly as top card is dragged)
                    if (secondPhoto != null) {
                        Surface(
                            shape = RoundedCornerShape(RoboRadius.Large),
                            color = CharcoalSurface,
                            border = BorderStroke(1.dp, CardBorderSlate),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .graphicsLayer {
                                    scaleX = nextCardScale
                                    scaleY = nextCardScale
                                    alpha = nextCardAlpha
                                    translationY = (1f - dragProgress) * 10f
                                }
                        ) {
                            PhotoThumbnailView(
                                photo = secondPhoto,
                                showBadges = false,
                                contentScale = ContentScale.Crop,
                                cornerRadius = RoboRadius.Large,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Front interactive media card (1:1 horizontal follow, max ~7° rotation, edge tint)
                    val borderTint by animateColorAsState(
                        targetValue = when {
                            currentOffsetX > 45f -> KeepEmerald.copy(alpha = (0.35f + 0.65f * dragProgress).coerceIn(0f, 1f))
                            currentOffsetX < -45f -> DestructiveRed.copy(alpha = (0.35f + 0.65f * dragProgress).coerceIn(0f, 1f))
                            else -> CardBorderSlate
                        },
                        animationSpec = tween(90),
                        label = "swipeBorderTint"
                    )

                    val rotationDeg = ((currentOffsetX / (cardWidthPx.coerceAtLeast(1f) * 0.5f)) * RoboMotion.SWIPE_MAX_ROTATION_DEG)
                        .coerceIn(-RoboMotion.SWIPE_MAX_ROTATION_DEG, RoboMotion.SWIPE_MAX_ROTATION_DEG)

                    Surface(
                        shape = RoundedCornerShape(RoboRadius.Large),
                        color = CharcoalSurface,
                        border = BorderStroke(if (dragProgress > 0.15f) 2.dp else 1.dp, borderTint),
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(currentOffsetX.roundToInt(), 0) }
                            .graphicsLayer {
                                rotationZ = rotationDeg
                            }
                            .pointerInput(topPhoto.stableIdentityKey) {
                                val velocityTracker = VelocityTracker()
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        val velocityX = velocityTracker.calculateVelocity().x
                                        val finalOffset = offsetXAnim.value
                                        val crossedByDistance = abs(finalOffset) >= commitThresholdPx
                                        val crossedByVelocity = abs(velocityX) >= RoboMotion.SWIPE_COMMIT_VELOCITY_PX_PER_SEC &&
                                            abs(finalOffset) >= cardWidthPx * 0.14f &&
                                            (finalOffset * velocityX > 0f)

                                        when {
                                            (crossedByDistance || crossedByVelocity) && finalOffset > 0f -> {
                                                triggerAnimatedDecision(TriageStatus.KEEP)
                                            }
                                            (crossedByDistance || crossedByVelocity) && finalOffset < 0f -> {
                                                triggerAnimatedDecision(TriageStatus.TRASH_VAULT)
                                            }
                                            else -> {
                                                thresholdHapticFired = false
                                                coroutineScope.launch {
                                                    offsetXAnim.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = RoboMotion.SwipeRecenterSpring
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        thresholdHapticFired = false
                                        coroutineScope.launch {
                                            offsetXAnim.animateTo(
                                                targetValue = 0f,
                                                animationSpec = RoboMotion.SwipeRecenterSpring
                                            )
                                        }
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        val nextVal = offsetXAnim.value + dragAmount
                                        val wasPast = abs(offsetXAnim.value) >= commitThresholdPx
                                        val nowPast = abs(nextVal) >= commitThresholdPx
                                        if (!wasPast && nowPast && !thresholdHapticFired) {
                                            thresholdHapticFired = true
                                            RoboHaptics.swipeThresholdTick(haptic)
                                        } else if (wasPast && !nowPast) {
                                            thresholdHapticFired = false
                                        }
                                        coroutineScope.launch {
                                            offsetXAnim.snapTo(nextVal)
                                        }
                                    }
                                )
                            }
                            .testTag("swipe_active_card")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Main Media Viewer (Video Player with integrated Storyboard OR Large Image Viewer)
                            if (topPhoto.isVideo || topPhoto.categoryEnum == PhotoCategory.VIDEO) {
                                IntegratedVideoPlayer(
                                    photo = topPhoto,
                                    autoPlay = false,
                                    showStoryboard = true,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                // Use ContentScale.Fit inside dark theatre canvas so the user sees the entire uncropped photo large and crisp,
                                // with a subtle blurred/dark backdrop and instant tap to open Fullscreen Swipeable Viewer
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF0A0C10))
                                        .clickable { fullscreenViewerOpen = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    PhotoThumbnailView(
                                        photo = topPhoto,
                                        showBadges = false,
                                        contentScale = ContentScale.Fit,
                                        cornerRadius = 0.dp,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            // Progressive Edge Tint during swipe (Pass 4 Section E)
                            if (abs(currentOffsetX) > 24f) {
                                val edgeColor = if (currentOffsetX > 0f) KeepEmerald else DestructiveRed
                                val tintAlpha = (dragProgress * 0.28f).coerceIn(0f, 0.28f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = if (currentOffsetX > 0f) {
                                                    listOf(Color.Transparent, edgeColor.copy(alpha = tintAlpha))
                                                } else {
                                                    listOf(edgeColor.copy(alpha = tintAlpha), Color.Transparent)
                                                }
                                            )
                                        )
                                )
                            }

                            // Top overlay row: Category Pill + Fullscreen / Compare quick actions
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                ObsidianBg.copy(alpha = 0.78f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RoboPill(
                                        text = topPhoto.categoryEnum.displayName,
                                        accentColor = ElectricBlue,
                                        containerColor = ObsidianBg.copy(alpha = 0.82f)
                                    )
                                    RoboPill(
                                        text = LuminaViewModel.formatBytes(topPhoto.fileSizeBytes),
                                        accentColor = TextPrimary,
                                        containerColor = ObsidianBg.copy(alpha = 0.82f)
                                    )
                                    if (topPhoto.isBestShotInCluster) {
                                        RoboPill(
                                            text = "BEST SHOT",
                                            accentColor = KeepEmerald,
                                            containerColor = ObsidianBg.copy(alpha = 0.86f)
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (topPhotoCluster != null && topPhotoCluster.allMembers.size > 1) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = ObsidianBg.copy(alpha = 0.84f),
                                            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f)),
                                            modifier = Modifier
                                                .clickable { onOpenFlickerCompare(topPhotoCluster) }
                                                .testTag("swipe_card_compare_button")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Compare,
                                                    contentDescription = "Flicker Compare Cluster",
                                                    tint = ElectricBlue,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "Compare (${topPhotoCluster.allMembers.size})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ElectricBlue,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    if (!topPhoto.isVideo) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = ObsidianBg.copy(alpha = 0.84f),
                                            border = BorderStroke(1.dp, CardBorderSlate),
                                            modifier = Modifier
                                                .clickable { fullscreenViewerOpen = true }
                                                .testTag("swipe_card_zoom_button")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.ZoomIn,
                                                    contentDescription = "See full image view & swipe",
                                                    tint = TextPrimary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = "Full Image",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Progressive KEEP / REVIEW BIN Stamp (locks into stronger state past threshold)
                            if (abs(currentOffsetX) > 35f) {
                                val isKeep = currentOffsetX > 0f
                                val stampColor = if (isKeep) KeepEmerald else DestructiveRed
                                val stampScale by animateFloatAsState(
                                    targetValue = if (isThresholdLocked) 1.08f else (0.85f + 0.15f * dragProgress),
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                    label = "stampScale"
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ObsidianBg.copy(alpha = if (isThresholdLocked) 0.94f else 0.78f),
                                    border = BorderStroke(if (isThresholdLocked) 2.5.dp else 1.5.dp, stampColor),
                                    modifier = Modifier
                                        .align(if (isKeep) Alignment.TopStart else Alignment.TopEnd)
                                        .padding(top = 56.dp, start = 16.dp, end = 16.dp)
                                        .graphicsLayer {
                                            scaleX = stampScale
                                            scaleY = stampScale
                                            alpha = (dragProgress * 1.25f).coerceIn(0.25f, 1f)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isKeep) Icons.Filled.Check else Icons.Filled.DeleteOutline,
                                            contentDescription = null,
                                            tint = stampColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isKeep) "KEEP" else "REVIEW BIN",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = stampColor,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }

                            // Bottom concise overlay inside media card:
                            // "Why RoboPhoto flagged this"
                            // "Likely duplicate • Best shot already selected"
                            if (!topPhoto.isVideo) {
                                val conciseFlagSummary = remember(topPhoto) {
                                    buildConciseFlagReason(topPhoto)
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    ObsidianBg.copy(alpha = 0.86f),
                                                    ObsidianBg.copy(alpha = 0.96f)
                                                )
                                            )
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Optional cluster sibling strip when reviewing a duplicate
                                    if (topPhotoCluster != null && topPhotoCluster.allMembers.size > 1) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                items(
                                                    items = topPhotoCluster.allMembers,
                                                    key = { it.stableIdentityKey }
                                                ) { sibling ->
                                                    val isCurrent = sibling.id == topPhoto.id
                                                    val isBest = sibling.isBestShotInCluster
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .border(
                                                                width = if (isBest || isCurrent) 1.5.dp else 1.dp,
                                                                color = when {
                                                                    isBest -> KeepEmerald
                                                                    isCurrent -> ElectricBlue
                                                                    else -> CardBorderSlate
                                                                },
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                            .clickable {
                                                                RoboHaptics.bestShotSelection(haptic)
                                                                onSelectBestShot(topPhotoCluster.clusterId, sibling.id)
                                                            }
                                                    ) {
                                                        PhotoThumbnailView(
                                                            photo = sibling,
                                                            showBadges = false,
                                                            cornerRadius = 8.dp,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                        if (isBest) {
                                                            Surface(
                                                                shape = CircleShape,
                                                                color = KeepEmerald,
                                                                modifier = Modifier
                                                                    .align(Alignment.TopEnd)
                                                                    .padding(2.dp)
                                                                    .size(13.dp)
                                                            ) {
                                                                Box(contentAlignment = Alignment.Center) {
                                                                    Icon(
                                                                        imageVector = Icons.Filled.Star,
                                                                        contentDescription = "Best Shot",
                                                                        tint = Color(0xFF042016),
                                                                        modifier = Modifier.size(9.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Tap to set Best",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        val dateStamp = remember(topPhoto.dateTakenEpochMs) {
                                            SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US)
                                                .format(Date(topPhoto.dateTakenEpochMs))
                                        }
                                        val currentIdx = uiState.sessionReviewedCount + 1
                                        val totalInSession = (uiState.sessionReviewedCount + deckPhotos.size).coerceAtLeast(1)
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "$currentIdx / $totalInSession  •  Why RoboPhoto flagged this",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ElectricBlue,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = conciseFlagSummary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${topPhoto.title}  •  $dateStamp",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(Modifier.width(8.dp))

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { triggerAnimatedDecision(TriageStatus.KEEP) },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(ElevatedSlate.copy(alpha = 0.88f))
                                                    .border(1.dp, CardBorderSlate, CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.FavoriteBorder,
                                                    contentDescription = "Favorite / Keep",
                                                    tint = TextPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = ElevatedSlate.copy(alpha = 0.90f),
                                                border = BorderStroke(1.dp, CardBorderSlate),
                                                modifier = Modifier
                                                    .clickable { onInspectPhotoDetail(topPhoto) }
                                                    .testTag("swipe_card_robolab_details_chip")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Info,
                                                        contentDescription = null,
                                                        tint = TextSecondary,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "Details",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = TextSecondary,
                                                        fontWeight = FontWeight.SemiBold
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

                // Concept Filmstrip Scrubber Strip (center phone in UI concept image):
                // Shows current photo framed in 2.dp ElectricBlue alongside upcoming queue/cluster thumbnails
                if (deckPhotos.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("review_filmstrip_row"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        items(
                            items = deckPhotos.take(8),
                            key = { "filmstrip_${it.stableIdentityKey}" }
                        ) { itemPhoto ->
                            val isActive = itemPhoto.id == topPhoto.id
                            Box(
                                modifier = Modifier
                                    .size(width = 50.dp, height = 50.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isActive) 2.dp else 1.dp,
                                        color = if (isActive) ElectricBlue else CardBorderSlate,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        if (isActive) {
                                            fullscreenViewerOpen = true
                                        } else {
                                            onInspectPhotoDetail(itemPhoto)
                                        }
                                    }
                            ) {
                                PhotoThumbnailView(
                                    photo = itemPhoto,
                                    showBadges = false,
                                    cornerRadius = 10.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // For videos, put the concise "Why RoboPhoto flagged this" bar right below the video player so playback controls are never covered
                if (topPhoto.isVideo || topPhoto.categoryEnum == PhotoCategory.VIDEO) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CharcoalSurface,
                        border = BorderStroke(1.dp, CardBorderSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Why RoboPhoto flagged this",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = buildConciseFlagReason(topPhoto),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricBlue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { onInspectPhotoDetail(topPhoto) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // 4. Tightened Primary Decision Bar (Pass 4 Section D & F):
                //    [ Review Bin ]  [ Skip ]  [ Keep ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { triggerAnimatedDecision(TriageStatus.TRASH_VAULT) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DestructiveRed.copy(alpha = 0.16f),
                            contentColor = DestructiveRed
                        ),
                        border = BorderStroke(1.dp, DestructiveRed.copy(alpha = 0.50f)),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1.15f)
                            .height(50.dp)
                            .roboPressScale()
                            .testTag("swipe_left_vault_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Review Bin",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            RoboHaptics.swipeThresholdTick(haptic)
                            onSkipPhoto(topPhoto)
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardBorderSlate),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(0.75f)
                            .height(50.dp)
                            .roboPressScale()
                            .testTag("swipe_skip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = { triggerAnimatedDecision(TriageStatus.KEEP) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KeepEmerald,
                            contentColor = Color(0xFF042016)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1.15f)
                            .height(50.dp)
                            .roboPressScale()
                            .testTag("swipe_right_keep_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Keep",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    // Fullscreen Zoomable + Swipeable Photo Viewer
    // Allows swiping right to Keep, swiping left to Review Bin, or tapping Skip/Keep/Bin while in Full Image view!
    if (fullscreenViewerOpen && topPhoto != null && !topPhoto.isVideo) {
        FullscreenZoomablePhotoDialog(
            photo = topPhoto,
            onDismiss = { fullscreenViewerOpen = false },
            onSwipeKeep = { photo ->
                onSwipeTriage(photo, TriageStatus.KEEP)
            },
            onSwipeBin = { photo ->
                onSwipeTriage(photo, TriageStatus.TRASH_VAULT)
            },
            onSkip = { photo ->
                onSkipPhoto(photo)
            },
            remainingCount = deckPhotos.size
        )
    } else if (fullscreenViewerOpen && topPhoto == null) {
        fullscreenViewerOpen = false
    }
}

/**
 * Pass 4 Section D: Builds a calm, human-readable one-line summary for
 * "Why RoboPhoto flagged this" (e.g. "Likely duplicate • Best shot already selected").
 * Technical measurements remain behind Details / RoboLab.
 */
private fun buildConciseFlagReason(photo: PhotoEntity): String {
    val reasons = AiWorkflowsAndUsageEngine.formatUserFacingBestShotExplanations(photo)
    val primary = reasons.firstOrNull() ?: photo.humanFriendlyWhy
    return when {
        photo.duplicateClusterId != null && !photo.isBestShotInCluster ->
            "Likely duplicate • Best shot already selected"
        photo.duplicateClusterId != null && photo.isBestShotInCluster ->
            "Nominated Best Shot • Highest sharpness in group"
        photo.categoryEnum == PhotoCategory.BLURRY ->
            "Low sharpness detected • Motion blur or soft focus"
        photo.categoryEnum == PhotoCategory.SCREENSHOT ->
            "${photo.screenshotSubTypeEnum.displayName} • Safe to review"
        photo.isVideo ->
            "Large video (${photo.formattedDuration}) • ${LuminaViewModel.formatBytes(photo.fileSizeBytes)}"
        else -> primary
    }
}

/**
 * Pass 4 Section N: Calm 5-Minute Cleanup Header Bar.
 * Shows:
 *   5:00  •  12 / 25 reviewed  •  386 MB identified   [ Stop ]
 */
@Composable
private fun CalmFiveMinuteCleanupBar(
    remainingSeconds: Int,
    completedCount: Int,
    targetCount: Int,
    identifiedBytes: Long,
    onStop: () -> Unit
) {
    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val formattedClock = String.format(java.util.Locale.US, "%d:%02d", mins, secs)
    val progress = (completedCount.toFloat() / targetCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CharcoalSurface,
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.40f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sprint_banner_card")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = formattedClock,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "•",
                        color = TextMuted
                    )
                    Text(
                        text = "$completedCount / $targetCount reviewed",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "•",
                        color = TextMuted
                    )
                    Text(
                        text = "${LuminaViewModel.formatBytes(identifiedBytes)} identified",
                        style = MaterialTheme.typography.labelMedium,
                        color = KeepEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Stop",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onStop)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("stop_sprint_button")
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = ElectricBlue,
                trackColor = ElevatedSlate
            )
        }
    }
}
