package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.PhotoEntity
import com.example.domain.scanner.VideoFrameSample
import com.example.domain.scanner.VideoSignatureAnalyzer
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.KeepEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LuminaViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Compact, downscaled storyboard frame cache keyed strictly by `PhotoEntity.stableIdentityKey`.
 * Stores only small 128px storyboard strips (never full-resolution bitmaps) to keep memory usage low.
 */
object VideoStoryboardCache {
    private val cache = object : LruCache<String, List<Bitmap>>(36) {}

    @Synchronized
    fun get(identityKey: String): List<Bitmap>? = cache.get(identityKey)

    @Synchronized
    fun put(identityKey: String, frames: List<Bitmap>) {
        if (frames.isNotEmpty()) {
            cache.put(identityKey, frames)
        }
    }

    @Synchronized
    fun evict(identityKey: String) {
        cache.remove(identityKey)
    }
}

/**
 * Extracts 5 compact downscaled storyboard frames (`10%, 30%, 50%, 70%, 90%`) for [photo]
 * on a background thread and caches them in [VideoStoryboardCache].
 */
suspend fun loadVideoStoryboardFrames(
    context: Context,
    photo: PhotoEntity,
    frameCount: Int = 5
): List<Bitmap> = withContext(Dispatchers.IO) {
    val key = photo.stableIdentityKey
    VideoStoryboardCache.get(key)?.let { return@withContext it }

    val uri = runCatching { Uri.parse(photo.uriString) }.getOrNull() ?: return@withContext emptyList()
    val retriever = MediaMetadataRetriever()
    val extracted = mutableListOf<Bitmap>()
    try {
        var dsSet = false
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                dsSet = true
            }
        }
        if (!dsSet) {
            retriever.setDataSource(context, uri)
        }

        val effectiveDurationMs = if (photo.durationMs > 0L) {
            photo.durationMs
        } else {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        }

        val fractions = if (frameCount <= 5) {
            VideoSignatureAnalyzer.STANDARD_SAMPLE_FRACTIONS
        } else {
            (1..frameCount).map { idx -> idx.toFloat() / (frameCount + 1).toFloat() }
        }

        for (fraction in fractions) {
            val seekUs = if (effectiveDurationMs > 0L) {
                (effectiveDurationMs * fraction * 1000f).toLong().coerceAtLeast(50_000L)
            } else {
                (fraction * 2_000_000f).toLong()
            }
            val frame = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        seekUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        128,
                        128
                    ) ?: retriever.getFrameAtTime(seekUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } else {
                    retriever.getFrameAtTime(seekUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            }.getOrNull()
            if (frame != null) {
                extracted += VideoSignatureAnalyzer.downscaleForStoryboard(frame, 128)
            }
        }
    } catch (_: Exception) {
        // Ignore and return whatever frames succeeded
    } finally {
        runCatching { retriever.release() }
    }

    if (extracted.isNotEmpty()) {
        VideoStoryboardCache.put(key, extracted)
    }
    extracted
}

/**
 * User-facing Video Storyboard Strip (Pass 2 Section E):
 * Represents a video in Review and Cluster Comparison with:
 * - Poster frame / 5–8 representative timeline frames (`10%, 30%, 50%, 70%, 90%`)
 * - Duration badge
 * - Play indicator
 * - File size badge
 * - Per-frame timestamp & sharpness cues so a user can understand a video without fully playing it.
 */
@Composable
fun VideoStoryboardStrip(
    photo: PhotoEntity,
    modifier: Modifier = Modifier,
    onFrameClickFraction: ((Float) -> Unit)? = null
) {
    if (!photo.isVideo) return
    val identityKey = photo.stableIdentityKey
    val context = LocalContext.current

    val decodedSignatureSamples = remember(identityKey, photo.videoSignatureHashes) {
        val parsed = VideoSignatureAnalyzer.decodeFrameSamples(photo.videoSignatureHashes)
        if (parsed.size >= 5) {
            parsed
        } else {
            VideoSignatureAnalyzer.STANDARD_SAMPLE_FRACTIONS.mapIndexed { index, frac ->
                parsed.getOrNull(index) ?: VideoFrameSample(
                    positionFraction = frac,
                    dHash = photo.dHash,
                    pHash = photo.pHash,
                    sharpnessScore = photo.sharpnessScore,
                    exposureScore = photo.exposureScore,
                    framingScore = photo.framingScore,
                    laplacianVariance = photo.laplacianVariance,
                    setBitCount = 32
                )
            }
        }
    }

    val cachedFrames by produceState(
        initialValue = VideoStoryboardCache.get(identityKey).orEmpty(),
        key1 = identityKey
    ) {
        if (value.isEmpty()) {
            value = loadVideoStoryboardFrames(context, photo, frameCount = decodedSignatureSamples.size.coerceIn(5, 8))
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_storyboard_${photo.id}"),
        shape = RoundedCornerShape(12.dp),
        color = ElevatedSlate,
        border = BorderStroke(1.dp, CardBorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Storyboard Header: Play Indicator + Duration + File Size + Stability
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ElectricBlue.copy(alpha = 0.20f),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Video Storyboard",
                                tint = ElectricBlue,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Text(
                        text = "Storyboard (${decodedSignatureSamples.size} frames)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CharcoalSurface,
                        border = BorderStroke(1.dp, CardBorderSlate)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = photo.formattedDuration,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CharcoalSurface,
                        border = BorderStroke(1.dp, CardBorderSlate)
                    ) {
                        Text(
                            text = LuminaViewModel.formatBytes(photo.fileSizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 5-8 Representative Thumbnail Frames Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                decodedSignatureSamples.forEachIndexed { idx, sample ->
                    val frameBmp = cachedFrames.getOrNull(idx)
                    val frameTimeLabel = if (photo.durationMs > 0L) {
                        formatMillisToClock((photo.durationMs * sample.positionFraction).toLong())
                    } else {
                        "${(sample.positionFraction * 100).toInt()}%"
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CharcoalSurface)
                            .border(
                                width = if (idx == decodedSignatureSamples.size / 2) 1.dp else 0.5.dp,
                                color = if (idx == decodedSignatureSamples.size / 2) {
                                    ElectricBlue.copy(alpha = 0.65f)
                                } else {
                                    CardBorderSlate
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = onFrameClickFraction != null) {
                                onFrameClickFraction?.invoke(sample.positionFraction)
                            }
                            .testTag("storyboard_frame_${photo.id}_$idx")
                    ) {
                        if (frameBmp != null) {
                            Image(
                                bitmap = frameBmp.asImageBitmap(),
                                contentDescription = "Frame $frameTimeLabel",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Deterministic visual frame cell derived from hash entropy & sharpness
                            val seed = (sample.dHash.take(6).toIntOrNull(16) ?: (idx * 40))
                            val tintAlpha = (0.12f + (idx * 0.04f)).coerceIn(0.10f, 0.32f)
                            val cellAccent = if (sample.isLowEntropy) {
                                TextMuted
                            } else if (seed % 2 == 0) {
                                ElectricBlue
                            } else {
                                KeepEmerald
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                CharcoalSurface,
                                                cellAccent.copy(alpha = tintAlpha),
                                                ObsidianBg
                                            )
                                        )
                                    )
                            )
                        }

                        // Timestamp label at bottom of frame cell
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.68f))
                                .padding(vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = frameTimeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Integrated Video Preview, Storyboard & Lifecycle-Safe Player (Pass 2 Sections E & F).
 * Supports:
 * - Lifecycle-aware playback (auto-pauses on ON_PAUSE/ON_STOP and releases on ON_DESTROY)
 * - Uncropped poster frame (`ContentScale.Fit`) before playback starts
 * - Interactive 5-frame storyboard strip (tap any frame to seek directly)
 * - Tap to Play / Pause, Scrubbing timeline slider, Mute / Unmute, Replay, and Fullscreen mode
 */
@Composable
fun IntegratedVideoPlayer(
    photo: PhotoEntity,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    showStoryboard: Boolean = true
) {
    val identityKey = photo.stableIdentityKey
    var isFullscreen by remember(identityKey) { mutableStateOf(false) }

    key(identityKey) {
        VideoPlayerSurface(
            photo = photo,
            modifier = modifier,
            autoPlay = autoPlay,
            isFullscreen = false,
            showStoryboard = showStoryboard,
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
                        showStoryboard = true,
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
    showStoryboard: Boolean,
    onToggleFullscreen: () -> Unit
) {
    val identityKey = photo.stableIdentityKey
    val lifecycleOwner = LocalLifecycleOwner.current

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

    // Lifecycle observer: pause cleanly when app goes to background or screen stops
    DisposableEffect(identityKey, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    runCatching {
                        if (videoViewRef?.isPlaying == true) {
                            videoViewRef?.pause()
                        }
                        isPlaying = false
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    runCatching {
                        videoViewRef?.stopPlayback()
                    }
                    isPlaying = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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

    fun seekToFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        val seekTarget = (clamped * durationMs).toInt().coerceIn(0, durationMs)
        scrubSliderValue = clamped
        currentPosMs = seekTarget
        runCatching {
            hasStartedRendering = true
            videoViewRef?.seekTo(seekTarget)
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

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 16.dp))
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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

            // Uncropped Poster thumbnail (`ContentScale.Fit`) shown before playback begins
            if (!hasStartedRendering) {
                PhotoThumbnailView(
                    photo = photo,
                    showBadges = false,
                    contentScale = ContentScale.Fit,
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
                    .padding(horizontal = 12.dp, vertical = 6.dp)
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
                            seekToFraction(scrubSliderValue)
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

        if (showStoryboard) {
            VideoStoryboardStrip(
                photo = photo,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                onFrameClickFraction = { fraction ->
                    seekToFraction(fraction)
                }
            )
        }
    }
}

private fun formatMillisToClock(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
