package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.PhotoEntity
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Integrated Video Preview & Player for Review Deck, Cluster Comparison, and Media Detail Sheet.
 * Supports:
 * - Tap to Play / Pause
 * - Scrubbing timeline slider
 * - Live position / total duration display
 * - Mute / Unmute toggle
 * - Restart / Replay
 * - Fullscreen modal mode
 * - Identity-verified poster thumbnail before playback starts
 */
@Composable
fun IntegratedVideoPlayer(
    photo: PhotoEntity,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false
) {
    val identityKey = photo.stableIdentityKey
    var isFullscreen by remember(identityKey) { mutableStateOf(false) }

    key(identityKey) {
        VideoPlayerSurface(
            photo = photo,
            modifier = modifier,
            autoPlay = autoPlay,
            isFullscreen = false,
            onToggleFullscreen = { isFullscreen = true }
        )

        if (isFullscreen) {
            Dialog(
                onDismissRequest = { isFullscreen = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBg)
                ) {
                    VideoPlayerSurface(
                        photo = photo,
                        modifier = Modifier.fillMaxSize(),
                        autoPlay = true,
                        isFullscreen = true,
                        onToggleFullscreen = { isFullscreen = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerSurface(
    photo: PhotoEntity,
    modifier: Modifier = Modifier,
    autoPlay: Boolean,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit
) {
    val identityKey = photo.stableIdentityKey
    var isPlaying by remember(identityKey) { mutableStateOf(autoPlay) }
    var isPrepared by remember(identityKey) { mutableStateOf(false) }
    var hasStartedRendering by remember(identityKey) { mutableStateOf(false) }
    var isMuted by remember(identityKey) { mutableStateOf(false) }
    var durationMs by remember(identityKey) {
        mutableIntStateOf(photo.durationMs.toInt().coerceAtLeast(1000))
    }
    var currentPosMs by remember(identityKey) { mutableIntStateOf(0) }
    var isUserScrubbing by remember(identityKey) { mutableStateOf(false) }
    var scrubSliderValue by remember(identityKey) { mutableFloatStateOf(0f) }

    var videoViewRef by remember(identityKey) { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember(identityKey) { mutableStateOf<MediaPlayer?>(null) }

    // Update progress timeline every 200ms while playing
    LaunchedEffect(identityKey, isPlaying, isPrepared, isUserScrubbing) {
        while (isPlaying && isPrepared) {
            val vv = videoViewRef
            if (vv != null && !isUserScrubbing) {
                runCatching {
                    if (vv.isPlaying) {
                        currentPosMs = vv.currentPosition.coerceAtLeast(0)
                        val total = durationMs.coerceAtLeast(1)
                        scrubSliderValue = (currentPosMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    }
                }
            }
            delay(200L)
        }
    }

    DisposableEffect(identityKey) {
        onDispose {
            runCatching {
                videoViewRef?.stopPlayback()
            }
            mediaPlayerRef = null
            videoViewRef = null
        }
    }

    fun applyMuteState(mp: MediaPlayer?, muted: Boolean) {
        runCatching {
            val vol = if (muted) 0f else 1f
            mp?.setVolume(vol, vol)
        }
    }

    fun togglePlayPause() {
        val vv = videoViewRef ?: return
        runCatching {
            if (isPlaying) {
                vv.pause()
                isPlaying = false
            } else {
                hasStartedRendering = true
                vv.start()
                isPlaying = true
            }
        }
    }

    fun restartVideo() {
        val vv = videoViewRef ?: return
        runCatching {
            vv.seekTo(0)
            currentPosMs = 0
            scrubSliderValue = 0f
            hasStartedRendering = true
            vv.start()
            isPlaying = true
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 16.dp))
            .background(Color.Black)
    ) {
        // Underlying Android VideoView
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    videoViewRef = this
                    setVideoURI(Uri.parse(photo.uriString))
                    setOnPreparedListener { mp ->
                        mediaPlayerRef = mp
                        isPrepared = true
                        val reportedDuration = mp.duration
                        if (reportedDuration > 0) {
                            durationMs = reportedDuration
                        }
                        applyMuteState(mp, isMuted)
                        mp.setOnInfoListener { _, what, _ ->
                            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                hasStartedRendering = true
                            }
                            false
                        }
                        if (autoPlay) {
                            hasStartedRendering = true
                            start()
                            isPlaying = true
                        } else {
                            // Preload first frame
                            seekTo(1)
                        }
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPosMs = durationMs
                        scrubSliderValue = 1f
                    }
                    setOnErrorListener { _, _, _ ->
                        isPlaying = false
                        true
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    togglePlayPause()
                }
        )

        // Poster thumbnail shown smoothly before playback begins
        if (!hasStartedRendering) {
            PhotoThumbnailView(
                photo = photo,
                showBadges = false,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { togglePlayPause() }
            )
        }

        // Center Play / Pause floating button when paused
        if (!isPlaying) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.68f),
                border = BorderStroke(1.5.dp, ElectricBlue.copy(alpha = 0.85f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clickable { togglePlayPause() }
                    .testTag("video_play_pause_center_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Top-right quick controls (Mute/Unmute, Replay, Fullscreen)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.68f),
                border = BorderStroke(1.dp, CardBorderSlate),
                modifier = Modifier.size(38.dp)
            ) {
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        applyMuteState(mediaPlayerRef, isMuted)
                    },
                    modifier = Modifier.testTag("video_mute_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute video" else "Mute video",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.68f),
                border = BorderStroke(1.dp, CardBorderSlate),
                modifier = Modifier.size(38.dp)
            ) {
                IconButton(
                    onClick = { restartVideo() },
                    modifier = Modifier.testTag("video_replay_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay,
                        contentDescription = "Restart video",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.68f),
                border = BorderStroke(1.dp, CardBorderSlate),
                modifier = Modifier.size(38.dp)
            ) {
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.testTag("video_fullscreen_button")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen video",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bottom Scrubbing Timeline & Duration Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.86f)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { togglePlayPause() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("video_play_pause_bar_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = formatMillisToClock(currentPosMs.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Slider(
                    value = scrubSliderValue,
                    onValueChange = { fraction ->
                        isUserScrubbing = true
                        scrubSliderValue = fraction
                        val seekTarget = (fraction * durationMs).toInt().coerceIn(0, durationMs)
                        currentPosMs = seekTarget
                    },
                    onValueChangeFinished = {
                        val seekTarget = (scrubSliderValue * durationMs).toInt().coerceIn(0, durationMs)
                        runCatching {
                            hasStartedRendering = true
                            videoViewRef?.seekTo(seekTarget)
                        }
                        isUserScrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlue,
                        activeTrackColor = ElectricBlue,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .testTag("video_scrub_slider")
                )

                Text(
                    text = formatMillisToClock(durationMs.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatMillisToClock(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
