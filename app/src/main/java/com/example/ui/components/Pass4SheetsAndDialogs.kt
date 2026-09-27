package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.PhotoEntity
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
import com.example.ui.theme.RoboPrimaryButton
import com.example.ui.theme.RoboProgressNumber
import com.example.ui.theme.RoboSecondaryButton
import com.example.ui.theme.SpineAmber
import com.example.ui.theme.SpineCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DuplicateClusterGroup
import com.example.ui.viewmodel.LuminaViewModel
import com.example.ui.viewmodel.UndoTriageRecord
import kotlinx.coroutines.delay

/**
 * ============================================================================
 * PASS 4 SECTION J: PHOTOGRAPHY-GRADE FLICKER COMPARE DIALOG
 * ============================================================================
 * Features:
 * - Synchronized crop (zoom + pan shared across Photo A and Photo B)
 * - Tap A / B pills for instant manual toggle
 * - Hold for rapid alternation (or toggle auto-flicker)
 * - Visible A / B labels with Best Shot status & quality metrics
 * - Auto-pause flicker while user is actively zooming/panning
 * - 3 / 4 / 5 Hz selector under advanced controls
 * - [ Set as Best Shot ] action with Best Shot animation & haptic feedback
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlickerCompareDialog(
    cluster: DuplicateClusterGroup,
    initialCandidateId: Long? = null,
    onSelectBestShot: (String, Long) -> Unit,
    onMoveExtraToBin: (PhotoEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val members = cluster.allMembers
    if (members.isEmpty()) {
        onDismiss()
        return
    }

    var currentBestId by remember(cluster.clusterId, cluster.bestShot.id) {
        mutableLongStateOf(cluster.bestShot.id)
    }

    val photoA = remember(members, currentBestId) {
        members.find { it.id == currentBestId } ?: cluster.bestShot
    }

    val candidatesB = remember(members, photoA.id) {
        members.filter { it.id != photoA.id }.ifEmpty { listOf(photoA) }
    }

    var selectedCandidateId by remember(cluster.clusterId, initialCandidateId, candidatesB) {
        mutableLongStateOf(
            initialCandidateId?.takeIf { id -> candidatesB.any { it.id == id } }
                ?: candidatesB.first().id
        )
    }

    val photoB = remember(candidatesB, selectedCandidateId) {
        candidatesB.find { it.id == selectedCandidateId } ?: candidatesB.first()
    }

    // Active slot: false = A (Best Shot), true = B (Candidate)
    var showingB by remember { mutableStateOf(false) }

    // Synchronized crop state (shared across A & B!)
    var syncScale by remember { mutableFloatStateOf(1f) }
    var syncOffsetX by remember { mutableFloatStateOf(0f) }
    var syncOffsetY by remember { mutableFloatStateOf(0f) }

    // Flicker controls
    var flickerHz by remember { mutableIntStateOf(4) }
    var showAdvancedControls by remember { mutableStateOf(false) }
    var autoFlickerEnabled by remember { mutableStateOf(false) }
    var isUserZooming by remember { mutableStateOf(false) }
    var lastZoomTimestampMs by remember { mutableLongStateOf(0L) }

    val holdInteractionSource = remember { MutableInteractionSource() }
    val isHoldingFlicker by holdInteractionSource.collectIsPressedAsState()

    // Pause flicker automatically while user zooms (for 700ms after transform gesture)
    LaunchedEffect(lastZoomTimestampMs) {
        if (lastZoomTimestampMs > 0L) {
            isUserZooming = true
            delay(700L)
            isUserZooming = false
        }
    }

    // Rapid alternation loop when holding or when auto-flicker is enabled (unless user is zooming)
    LaunchedEffect(isHoldingFlicker, autoFlickerEnabled, flickerHz, isUserZooming) {
        val shouldFlicker = (isHoldingFlicker || autoFlickerEnabled) && !isUserZooming
        if (!shouldFlicker) return@LaunchedEffect
        val intervalMs = (1000L / flickerHz.coerceIn(2, 6)).coerceAtLeast(160L)
        while (true) {
            showingB = !showingB
            RoboHaptics.swipeThresholdTick(haptic)
            delay(intervalMs)
        }
    }

    val displayedPhoto = if (showingB) photoB else photoA
    val isDisplayedBest = displayedPhoto.id == currentBestId

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .testTag("flicker_compare_dialog")
        ) {
            // Top Bar: Cluster Title + Visible A/B Indicator + Advanced Hz Toggle + Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Compare,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Flicker Compare",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        RoboPill(
                            text = if (showingB) "VIEWING B" else "VIEWING A (BEST)",
                            accentColor = if (showingB) SpineAmber else KeepEmerald
                        )
                    }
                    Text(
                        text = if (syncScale > 1.02f) {
                            "Synchronized crop (${String.format(java.util.Locale.US, "%.1fx", syncScale)}) • Tap A/B or hold Flicker"
                        } else {
                            "Pinch to zoom synchronized crop • Tap A/B or hold Flicker"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (syncScale > 1.02f) {
                        IconButton(
                            onClick = {
                                syncScale = 1f
                                syncOffsetX = 0f
                                syncOffsetY = 0f
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ElevatedSlate)
                                .testTag("flicker_reset_zoom_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ZoomOutMap,
                                contentDescription = "Reset synchronized zoom",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { showAdvancedControls = !showAdvancedControls },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (showAdvancedControls) ElectricBlue.copy(alpha = 0.22f) else ElevatedSlate)
                            .testTag("flicker_advanced_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Flicker speed settings",
                            tint = if (showAdvancedControls) ElectricBlue else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ElevatedSlate)
                            .testTag("flicker_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close Flicker Compare",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Optional 3 / 4 / 5 Hz selector under advanced controls
            AnimatedVisibility(visible = showAdvancedControls) {
                Surface(
                    color = ElevatedSlate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Alternation Frequency",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 4, 5).forEach { hz ->
                                FilterChip(
                                    selected = flickerHz == hz,
                                    onClick = { flickerHz = hz },
                                    label = { Text("$hz Hz", style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricBlue.copy(alpha = 0.22f),
                                        selectedLabelColor = ElectricBlue
                                    ),
                                    modifier = Modifier.testTag("flicker_hz_$hz")
                                )
                            }
                        }
                    }
                }
            }

            // Main Synchronized Crop Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(ObsidianBg)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showingB = !showingB
                                RoboHaptics.swipeThresholdTick(haptic)
                            },
                            onDoubleTap = {
                                if (syncScale > 1.05f) {
                                    syncScale = 1f
                                    syncOffsetX = 0f
                                    syncOffsetY = 0f
                                } else {
                                    syncScale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (zoom != 1f || pan != androidx.compose.ui.geometry.Offset.Zero) {
                                lastZoomTimestampMs = System.currentTimeMillis()
                            }
                            val newScale = (syncScale * zoom).coerceIn(1f, 5f)
                            syncScale = newScale
                            if (newScale > 1.01f) {
                                val maxPan = 900f * (newScale - 1f)
                                syncOffsetX = (syncOffsetX + pan.x).coerceIn(-maxPan, maxPan)
                                syncOffsetY = (syncOffsetY + pan.y).coerceIn(-maxPan, maxPan)
                            } else {
                                syncOffsetX = 0f
                                syncOffsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = syncScale
                            scaleY = syncScale
                            translationX = syncOffsetX
                            translationY = syncOffsetY
                        }
                ) {
                    PhotoThumbnailView(
                        photo = displayedPhoto,
                        showBadges = false,
                        contentScale = ContentScale.Fit,
                        cornerRadius = 0.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top-Start Prominent A / B Badge Overlay
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianBg.copy(alpha = 0.86f),
                    border = BorderStroke(
                        1.5.dp,
                        if (showingB) SpineAmber else KeepEmerald
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .testTag("flicker_active_slot_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (showingB) SpineAmber else KeepEmerald,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (showingB) "B" else "A",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF042016),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (showingB) {
                                    "Candidate B • ${displayedPhoto.title}"
                                } else {
                                    "Best Shot A • ${displayedPhoto.title}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sharpness ${displayedPhoto.sharpnessScore} • Quality ${displayedPhoto.overallQualityScore} • ${LuminaViewModel.formatBytes(displayedPhoto.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                if (isUserZooming && (isHoldingFlicker || autoFlickerEnabled)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianBg.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, SpineCyan),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Flicker paused while zooming",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpineCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Bottom Control Panel: Candidate B Strip (if >2 items), Tap A/B, Hold to Flicker, and Set as Best Shot
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (candidatesB.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(candidatesB, key = { it.stableIdentityKey }) { cand ->
                            val selected = cand.id == photoB.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) SpineAmber.copy(alpha = 0.20f) else ElevatedSlate,
                                border = BorderStroke(1.dp, if (selected) SpineAmber else CardBorderSlate),
                                modifier = Modifier.clickable {
                                    selectedCandidateId = cand.id
                                    showingB = true
                                }
                            ) {
                                Text(
                                    text = "B: ${cand.title} (Sharp ${cand.sharpnessScore})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) SpineAmber else TextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Tap [ A ] | [ B ] | [ Hold to Flicker ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!showingB) KeepEmerald.copy(alpha = 0.20f) else ElevatedSlate,
                        border = BorderStroke(1.5.dp, if (!showingB) KeepEmerald else CardBorderSlate),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showingB = false
                                autoFlickerEnabled = false
                                RoboHaptics.swipeThresholdTick(haptic)
                            }
                            .testTag("flicker_select_a_button")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "A • Best Shot",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (!showingB) KeepEmerald else TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sharp ${photoA.sharpnessScore}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (showingB) SpineAmber.copy(alpha = 0.20f) else ElevatedSlate,
                        border = BorderStroke(1.5.dp, if (showingB) SpineAmber else CardBorderSlate),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showingB = true
                                autoFlickerEnabled = false
                                RoboHaptics.swipeThresholdTick(haptic)
                            }
                            .testTag("flicker_select_b_button")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "B • Candidate",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (showingB) SpineAmber else TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sharp ${photoB.sharpnessScore}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    // Hold for rapid alternation (or tap to toggle continuous flicker)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isHoldingFlicker || autoFlickerEnabled) {
                            ElectricBlue.copy(alpha = 0.24f)
                        } else {
                            ElevatedSlate
                        },
                        border = BorderStroke(
                            1.5.dp,
                            if (isHoldingFlicker || autoFlickerEnabled) ElectricBlue else CardBorderSlate
                        ),
                        modifier = Modifier
                            .weight(1.1f)
                            .clickable(
                                interactionSource = holdInteractionSource,
                                indication = null
                            ) {
                                autoFlickerEnabled = !autoFlickerEnabled
                            }
                            .testTag("flicker_hold_button")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Flip,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (isHoldingFlicker || autoFlickerEnabled) "Flickering" else "Hold Flicker",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "$flickerHz Hz A↔B",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Bottom Decision Row: [ Set as Best Shot ] | [ Move B to Review Bin ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val targetBest = displayedPhoto
                            RoboHaptics.bestShotSelection(haptic)
                            currentBestId = targetBest.id
                            showingB = false
                            onSelectBestShot(cluster.clusterId, targetBest.id)
                        },
                        enabled = !isDisplayedBest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KeepEmerald,
                            contentColor = Color(0xFF042016)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("flicker_set_best_shot_button")
                    ) {
                        Icon(
                            imageVector = if (isDisplayedBest) Icons.Filled.Check else Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isDisplayedBest) "Current Best Shot" else "Set as Best Shot",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            RoboHaptics.explicitAction(haptic)
                            onMoveExtraToBin(photoB)
                            if (candidatesB.size <= 1) {
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DestructiveRed.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("flicker_bin_candidate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = DestructiveRed,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Bin Candidate B",
                            color = DestructiveRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * ============================================================================
 * PASS 4 SECTION K: MULTI-LEVEL UNDO HISTORY SHEET
 * ============================================================================
 * Displays the session-local undo stack of the last 20–50 reversible decisions:
 * - Keep — IMG_...
 * - Review Bin — VID_...
 * - Cluster cleanup — X extras
 * - Best Shot — IMG_...
 * Never includes confirmed permanent source deletions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UndoHistorySheet(
    undoHistory: List<UndoTriageRecord>,
    onUndoAt: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBg,
        scrimColor = Color.Black.copy(alpha = 0.74f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("undo_history_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Recent Decisions",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${undoHistory.size} reversible decision(s) this session",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ElevatedSlate,
                border = BorderStroke(1.dp, CardBorderSlate)
            ) {
                Text(
                    text = "Reversible session actions include Keep, Review Bin, cluster batch cleanup, and manual Best Shot selections. Confirmed permanent deletions cannot be undone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(10.dp)
                )
            }

            if (undoHistory.isEmpty()) {
                RoboCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No Recent Decisions Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Decisions you make in Review or Collections will appear here so you can reverse them anytime during your session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = undoHistory,
                        key = { idx, item -> "${idx}_${item.timestampEpochMs}_${item.description}" }
                    ) { index, record ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CharcoalSurface,
                            border = BorderStroke(1.dp, CardBorderSlate),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("undo_history_row_$index")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = record.description,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val detailText = buildString {
                                        append(record.actionCategory)
                                        if (record.bytesAffected > 0L) {
                                            append(" • ")
                                            append(LuminaViewModel.formatBytes(record.bytesAffected))
                                        }
                                    }
                                    Text(
                                        text = detailText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }

                                Spacer(Modifier.width(10.dp))

                                OutlinedButton(
                                    onClick = {
                                        RoboHaptics.explicitAction(haptic)
                                        onUndoAt(index)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("undo_history_item_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Undo,
                                        contentDescription = "Undo ${record.description}",
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Undo",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * ============================================================================
 * PASS 4 SECTION L: CLEANUP RECEIPT SHEET
 * ============================================================================
 * Displays verified cleanup session results or OS-confirmed permanent deletion receipts:
 * - Cleanup complete ✓
 * - 126 reviewed
 * - 84 kept
 * - 42 moved to Review Bin
 * - 1.32 GB ready to recover (or "1.21 GB actually recovered" after permanent delete)
 * - Animated upward counting storage metric + subtle completion haptic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupReceiptSheet(
    reviewedCount: Int,
    keptCount: Int,
    movedToBinCount: Int,
    readyToRecoverBytes: Long,
    verifiedPermanentlyDeletedBytes: Long = 0L,
    verifiedPermanentlyDeletedCount: Int = 0,
    isFiveMinuteSprintCompletion: Boolean = false,
    onOpenReviewBin: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        RoboHaptics.completion(haptic)
    }

    val isPermanentDeleteReceipt = verifiedPermanentlyDeletedCount > 0 && verifiedPermanentlyDeletedBytes > 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBg,
        scrimColor = Color.Black.copy(alpha = 0.76f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 12.dp)
                .testTag("cleanup_receipt_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with subtle check badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = KeepEmerald.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, KeepEmerald.copy(alpha = 0.45f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = KeepEmerald,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = when {
                                isPermanentDeleteReceipt -> "Space recovered ✓"
                                isFiveMinuteSprintCompletion -> "Nice work ✓"
                                else -> "Cleanup complete ✓"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                isPermanentDeleteReceipt -> "Verified OS storage deletion complete"
                                isFiveMinuteSprintCompletion -> "$reviewedCount reviewed • ${LuminaViewModel.formatBytes(readyToRecoverBytes)} cleaned"
                                else -> "Your library decisions are safely saved"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            // Primary Animated Storage Number Card
            RoboCard(
                containerColor = CharcoalSurface,
                borderColor = KeepEmerald.copy(alpha = 0.45f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val targetBytes = if (isPermanentDeleteReceipt) {
                        verifiedPermanentlyDeletedBytes
                    } else {
                        readyToRecoverBytes
                    }
                    RoboProgressNumber(
                        targetValue = targetBytes,
                        formatter = { bytes ->
                            if (isPermanentDeleteReceipt) {
                                "${LuminaViewModel.formatBytes(bytes)} actually recovered"
                            } else {
                                "${LuminaViewModel.formatBytes(bytes)} ready to recover"
                            }
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = KeepEmerald,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag("receipt_animated_bytes_text")
                    )
                    Text(
                        text = if (isPermanentDeleteReceipt) {
                            "$verifiedPermanentlyDeletedCount item(s) permanently removed from device storage."
                        } else {
                            "Staged safely in Review Bin. Nothing is permanently deleted until you confirm."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Session Breakdown Rows
            RoboCard(containerColor = CharcoalSurface) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReceiptStatRow(
                        label = "Reviewed this session",
                        value = "$reviewedCount reviewed",
                        valueColor = TextPrimary
                    )
                    HorizontalDivider(color = CardBorderSlate)
                    ReceiptStatRow(
                        label = "Kept safe",
                        value = "$keptCount kept",
                        valueColor = KeepEmerald
                    )
                    HorizontalDivider(color = CardBorderSlate)
                    ReceiptStatRow(
                        label = "Moved to Review Bin",
                        value = "$movedToBinCount moved to Review Bin",
                        valueColor = ElectricBlue
                    )
                }
            }

            // Actions: [ Review Bin ] | [ Done ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isPermanentDeleteReceipt) {
                    RoboSecondaryButton(
                        text = "Review Bin",
                        icon = Icons.Filled.DeleteOutline,
                        iconTint = DestructiveRed,
                        onClick = {
                            onDismiss()
                            onOpenReviewBin()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("receipt_open_bin_button")
                    )
                }
                RoboPrimaryButton(
                    text = "Done",
                    icon = Icons.Filled.Check,
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("receipt_done_button")
                )
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ReceiptStatRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}
