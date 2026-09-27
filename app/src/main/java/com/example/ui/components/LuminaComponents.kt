package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.DestructiveRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.KeepEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoboHaptics
import com.example.ui.theme.RoboMotion
import com.example.ui.theme.RoboRadius
import com.example.ui.theme.SpineAmber
import com.example.ui.theme.SpineBlue
import com.example.ui.theme.SpineCoral
import com.example.ui.theme.SpineCyan
import com.example.ui.theme.SpineEmerald
import com.example.ui.theme.SpineViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LuminaViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Thread-safe LRU cache keyed strictly by `PhotoEntity.stableIdentityKey`
 * (`"${id}_${uriString}_${fileSizeBytes}_${dateTakenEpochMs}"`).
 * Prevents any cross-item thumbnail collisions across lists, clusters, or swipe decks.
 */
object MediaThumbnailIdentityCache {
    private val cache = object : LruCache<String, Bitmap>(96) {}

    @Synchronized
    fun get(identityKey: String): Bitmap? = cache.get(identityKey)

    @Synchronized
    fun put(identityKey: String, bitmap: Bitmap) {
        cache.put(identityKey, bitmap)
    }

    @Synchronized
    fun evict(identityKey: String) {
        cache.remove(identityKey)
    }
}

/**
 * Extracts or decodes an exact thumbnail for [photo] and stores it in [MediaThumbnailIdentityCache]
 * under [PhotoEntity.stableIdentityKey].
 */
suspend fun loadVerifiedThumbnailBitmap(context: Context, photo: PhotoEntity, targetPx: Int = 420): Bitmap? {
    val cacheKey = photo.stableIdentityKey
    MediaThumbnailIdentityCache.get(cacheKey)?.let { return it }

    return withContext(Dispatchers.IO) {
        MediaThumbnailIdentityCache.get(cacheKey)?.let { return@withContext it }

        val uri = runCatching { Uri.parse(photo.uriString) }.getOrNull() ?: return@withContext null
        var loaded: Bitmap? = null

        if (photo.isVideo) {
            // 1. Primary: FileDescriptor-backed MediaMetadataRetriever for exact per-video frame accuracy
            val retriever = MediaMetadataRetriever()
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
                val seekUs = if (photo.durationMs > 2000L) {
                    minOf(1_500_000L, photo.durationMs * 250L)
                } else {
                    300_000L
                }
                loaded = retriever.getFrameAtTime(seekUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
            } catch (_: Exception) {
                // Fallback below
            } finally {
                runCatching { retriever.release() }
            }

            // 2. Fallback: ContentResolver.loadThumbnail
            if (loaded == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    loaded = context.contentResolver.loadThumbnail(uri, Size(targetPx, targetPx), null)
                }
            }
        } else {
            // Still Image: ContentResolver.loadThumbnail on Android 10+ or sampled stream decode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
                runCatching {
                    loaded = context.contentResolver.loadThumbnail(uri, Size(targetPx, targetPx), null)
                }
            }
            if (loaded == null) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, bounds)
                    }
                    var sample = 1
                    while ((bounds.outWidth / sample) > targetPx * 2 && (bounds.outHeight / sample) > targetPx * 2) {
                        sample *= 2
                    }
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sample.coerceAtLeast(1)
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        loaded = BitmapFactory.decodeStream(stream, null, opts)
                    }
                }
            }
        }

        if (loaded != null) {
            MediaThumbnailIdentityCache.put(cacheKey, loaded!!)
        }
        loaded
    }
}

/**
 * Preloads the next 2–3 media items into both [MediaThumbnailIdentityCache] and Coil's memory cache
 * so swipe transitions and cluster comparisons feel instantaneous.
 */
suspend fun preloadUpcomingMediaItems(context: Context, upcoming: List<PhotoEntity>) {
    withContext(Dispatchers.IO) {
        val loader = ImageLoader(context)
        for (item in upcoming.take(3)) {
            runCatching {
                loadVerifiedThumbnailBitmap(context, item, targetPx = 640)
                if (!item.isVideo) {
                    val req = ImageRequest.Builder(context)
                        .data(Uri.parse(item.uriString))
                        .memoryCacheKey(item.stableIdentityKey)
                        .diskCacheKey(item.stableIdentityKey)
                        .build()
                    loader.enqueue(req)
                }
            }
        }
    }
}

/**
 * Stealth / Graphite Dark card with restrained 1dp border and subtle left accent bar.
 */
@Composable
fun SpineCard(
    spineColor: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = CharcoalSurface,
    cornerRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, CardBorderSlate),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(spineColor.copy(alpha = 0.85f))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun ForensicPillBadge(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.32f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TelemetryPill(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    ForensicPillBadge(
        text = text,
        accentColor = accentColor,
        modifier = modifier
    )
}

/**
 * Pass 4 Section I: Best Shot Animated Badge (250–350 ms scale & fade).
 */
@Composable
fun BestShotAnimatedBadge(
    isBestShot: Boolean,
    isUserSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isBestShot,
        enter = scaleIn(animationSpec = tween(RoboMotion.BEST_SHOT_BADGE_MS)) +
            fadeIn(animationSpec = tween(RoboMotion.BEST_SHOT_BADGE_MS)),
        exit = scaleOut(animationSpec = tween(200)) +
            fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = KeepEmerald.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = if (isUserSelected) Icons.Filled.Star else Icons.Filled.CheckCircle,
                    contentDescription = "Best Shot",
                    tint = Color(0xFF042016),
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = if (isUserSelected) "BEST (YOU)" else "BEST SHOT",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF042016),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Reusable Empty State Card for sections across Home, Review, and Collections.
 */
@Composable
fun SectionEmptyStateCard(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CharcoalSurface,
        border = BorderStroke(1.dp, CardBorderSlate),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = KeepEmerald.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, KeepEmerald.copy(alpha = 0.35f)),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = KeepEmerald,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun QualityMeterBar(
    label: String,
    score: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = "$score/100",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0.05f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape),
            color = accentColor,
            trackColor = ElevatedSlate
        )
    }
}

/**
 * Reusable RoboMediaCard (Pass 4 Section A) wrapping a thumbnail + title/size caption.
 */
@Composable
fun RoboMediaCard(
    photo: PhotoEntity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(RoboRadius.md),
        color = CharcoalSurface,
        border = BorderStroke(1.dp, CardBorderSlate)
    ) {
        Column {
            PhotoThumbnailView(
                photo = photo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = photo.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = LuminaViewModel.formatBytes(photo.fileSizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Identity-verified thumbnail renderer for both Photos and Videos (Pass 4 Section G & Section I):
 * - Eliminates blank states via immediate cached micro-preview / toned backdrop + ~160ms smooth fade/scale reveal.
 * - Strictly keyed on `photo.stableIdentityKey` (`id + uriString + fileSizeBytes + dateTakenEpochMs`)
 *   so thumbnails NEVER bleed or repeat across different items in LazyLists, Grids, or Swipe Decks.
 * - Animates Best Shot badge into place (`280ms` scale + fade).
 */
@Composable
fun PhotoThumbnailView(
    photo: PhotoEntity,
    modifier: Modifier = Modifier,
    showBadges: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 12.dp
) {
    val identityKey = photo.stableIdentityKey

    key(identityKey) {
        val context = LocalContext.current
        val immediateCached = remember(identityKey) { MediaThumbnailIdentityCache.get(identityKey) }
        var asyncImageFailed by remember(identityKey) { mutableStateOf(false) }
        var imageLoaded by remember(identityKey) { mutableStateOf(immediateCached != null) }

        val verifiedBitmap by produceState<Bitmap?>(
            initialValue = immediateCached,
            key1 = identityKey
        ) {
            if (value == null && (photo.isVideo || asyncImageFailed)) {
                val loaded = loadVerifiedThumbnailBitmap(context, photo, targetPx = 720)
                value = loaded
                if (loaded != null) {
                    imageLoaded = true
                }
            }
        }

        val imageRequest = remember(identityKey) {
            ImageRequest.Builder(context)
                .data(Uri.parse(photo.uriString))
                .memoryCacheKey(identityKey)
                .diskCacheKey(identityKey)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(RoboMotion.THUMBNAIL_FADE_MS)
                .build()
        }

        // Smooth 160ms perceived thumbnail transition (Pass 4 Section G)
        val sharpAlpha by animateFloatAsState(
            targetValue = if (imageLoaded || verifiedBitmap != null) 1f else 0.85f,
            animationSpec = tween(durationMillis = RoboMotion.THUMBNAIL_FADE_MS),
            label = "thumbnailSharpAlpha"
        )

        val containerBg = if (contentScale == ContentScale.Fit) ObsidianBg else ElevatedSlate

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(containerBg)
                .border(1.dp, CardBorderSlate, RoundedCornerShape(cornerRadius)),
            contentAlignment = Alignment.Center
        ) {
            // Immediate low-res / toned micro-backdrop so there is never an ugly blank flash
            CategoryMicroPreviewBackdrop(photo = photo)

            if (photo.isVideo) {
                if (verifiedBitmap != null) {
                    Image(
                        bitmap = verifiedBitmap!!.asImageBitmap(),
                        contentDescription = photo.title,
                        contentScale = contentScale,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = sharpAlpha }
                    )
                } else {
                    CleanMediaFallbackPlaceholder(photo = photo)
                }
            } else {
                if (!asyncImageFailed) {
                    if (immediateCached != null) {
                        Image(
                            bitmap = immediateCached.asImageBitmap(),
                            contentDescription = photo.title,
                            contentScale = contentScale,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = photo.title,
                        contentScale = contentScale,
                        onSuccess = { imageLoaded = true },
                        onError = { asyncImageFailed = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = sharpAlpha }
                    )
                } else if (verifiedBitmap != null) {
                    Image(
                        bitmap = verifiedBitmap!!.asImageBitmap(),
                        contentDescription = photo.title,
                        contentScale = contentScale,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = sharpAlpha }
                    )
                } else {
                    CleanMediaFallbackPlaceholder(photo = photo)
                }
            }

            // Center Play indicator for Videos
            if (photo.isVideo && showBadges) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.62f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (showBadges) {
                // Pass 4 Section I: Best Shot Animated Badge (250–350 ms scale & fade)
                AnimatedVisibility(
                    visible = photo.isBestShotInCluster,
                    enter = scaleIn(animationSpec = tween(RoboMotion.BEST_SHOT_BADGE_MS)) +
                        fadeIn(animationSpec = tween(RoboMotion.BEST_SHOT_BADGE_MS)),
                    exit = scaleOut(animationSpec = tween(200)) +
                        fadeOut(animationSpec = tween(200)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = KeepEmerald.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Best Shot",
                                tint = Color(0xFF042016),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "BEST",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF042016),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (!photo.isBestShotInCluster && photo.sentimentalProtected) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SpineBlue.copy(alpha = 0.90f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Protected",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "SAFE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Bottom-start Video Duration pill
                if (photo.isVideo) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = photo.formattedDuration,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryMicroPreviewBackdrop(photo: PhotoEntity) {
    val accent = Color(photo.categoryEnum.spineHex)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        CharcoalSurface,
                        ElevatedSlate,
                        accent.copy(alpha = 0.12f)
                    )
                )
            )
    )
}

@Composable
private fun CleanMediaFallbackPlaceholder(photo: PhotoEntity) {
    val accent = Color(photo.categoryEnum.spineHex)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        CharcoalSurface,
                        ElevatedSlate,
                        accent.copy(alpha = 0.16f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                imageVector = if (photo.isVideo) Icons.Filled.Videocam else Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = photo.title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CategoryFallbackCanvas(category: PhotoCategory, title: String) {
    val baseColor = when (category) {
        PhotoCategory.VIDEO -> SpineBlue
        PhotoCategory.PLANT -> SpineEmerald
        PhotoCategory.PET -> SpineAmber
        PhotoCategory.LANDSCAPE -> SpineBlue
        PhotoCategory.SCREENSHOT -> SpineCyan
        PhotoCategory.RECEIPT -> SpineEmerald
        PhotoCategory.BLURRY -> SpineCoral
        PhotoCategory.DOCUMENT -> SpineViolet
        PhotoCategory.DOWNLOAD -> SpineAmber
        PhotoCategory.MEME -> SpineViolet
        PhotoCategory.PEOPLE -> SpineCyan
        PhotoCategory.UNKNOWN -> TextMuted
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    CharcoalSurface,
                    baseColor.copy(alpha = 0.25f),
                    ObsidianBg
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
    }
}

/**
 * Full-screen uncropped photo viewer with:
 * 1. Pinch-to-zoom (`1x..5x`), pan when zoomed in, and double-tap to zoom (`1x <-> 2.5x`).
 * 2. Full aspect ratio display (`ContentScale.Fit`) so no part of any image is ever cut off.
 * 3. **Full-Screen Swiping & Triage** (when `scale <= 1.05f`):
 *    - When opened from Review mode (`onSwipeDecision != null`), swiping right = KEEP and swiping left = REVIEW BIN
 *      with 1:1 finger tracking, max ~6.5° rotation, progressive KEEP / REVIEW BIN stamps, threshold haptic tick,
 *      and automatic advance to the next item in [deckPhotos] without leaving Full Image View!
 *    - Also provides a sleek bottom triage dock (`[ Review Bin ]` | `[ Skip ]` | `[ Keep ]` + `[ Undo ]`)
 *      so users can review their queue in 100% full-bleed mode.
 */
@Composable
fun FullscreenZoomablePhotoDialog(
    photo: PhotoEntity,
    onDismiss: () -> Unit,
    onInspectDetails: (() -> Unit)? = null,
    deckPhotos: List<PhotoEntity> = emptyList(),
    onSwipeDecision: ((PhotoEntity, TriageStatus) -> Unit)? = null,
    onSkipPhoto: ((PhotoEntity) -> Unit)? = null,
    onSwipeKeep: ((PhotoEntity) -> Unit)? = null,
    onSwipeBin: ((PhotoEntity) -> Unit)? = null,
    onSkip: ((PhotoEntity) -> Unit)? = null,
    remainingCount: Int = 0,
    onUndoLast: (() -> Unit)? = null,
    canUndo: Boolean = false
) {
    val effectiveOnSwipeDecision: ((PhotoEntity, TriageStatus) -> Unit)? = onSwipeDecision
        ?: if (onSwipeKeep != null || onSwipeBin != null) {
            { p, status ->
                if (status == TriageStatus.KEEP) {
                    onSwipeKeep?.invoke(p)
                } else if (status == TriageStatus.TRASH_VAULT) {
                    onSwipeBin?.invoke(p)
                }
            }
        } else {
            null
        }
    val effectiveOnSkipPhoto: ((PhotoEntity) -> Unit)? = onSkipPhoto ?: onSkip
    val effectiveRemainingCount = if (remainingCount > 0) remainingCount else deckPhotos.size
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Track current active photo when browsing/triaging inside Full Image View
    var browseIndex by remember(photo.stableIdentityKey) {
        val initialIdx = deckPhotos.indexOfFirst { it.stableIdentityKey == photo.stableIdentityKey }
        mutableIntStateOf(if (initialIdx >= 0) initialIdx else 0)
    }

    val activePhoto: PhotoEntity? = remember(photo, deckPhotos, browseIndex) {
        if (deckPhotos.isNotEmpty()) {
            deckPhotos.find { it.stableIdentityKey == photo.stableIdentityKey }
                ?: deckPhotos.getOrNull(browseIndex.coerceIn(0, (deckPhotos.size - 1).coerceAtLeast(0)))
        } else {
            photo
        }
    }

    // If all photos in the deck have been triaged while in Full Image View, dismiss cleanly
    LaunchedEffect(deckPhotos.size, activePhoto) {
        if (deckPhotos.isEmpty() && onSwipeDecision != null && activePhoto == null) {
            onDismiss()
        }
    }

    val currentPhoto = activePhoto ?: photo
    val nextPhotoInDeck = remember(deckPhotos, currentPhoto.stableIdentityKey) {
        val idx = deckPhotos.indexOfFirst { it.stableIdentityKey == currentPhoto.stableIdentityKey }
        if (idx >= 0 && idx + 1 < deckPhotos.size) deckPhotos[idx + 1] else null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        key(currentPhoto.stableIdentityKey) {
            var scale by remember { mutableFloatStateOf(1f) }
            var panX by remember { mutableFloatStateOf(0f) }
            var panY by remember { mutableFloatStateOf(0f) }

            val swipeOffsetX = remember { Animatable(0f) }
            var viewportWidthPx by remember { mutableFloatStateOf(1080f) }
            var hasCrossedThresholdHaptic by remember { mutableStateOf(false) }

            val commitThresholdPx = (viewportWidthPx * 0.22f).coerceIn(130f, 250f)
            val dragFraction = (swipeOffsetX.value / commitThresholdPx).coerceIn(-1.5f, 1.5f)
            val absProgress = abs(dragFraction).coerceIn(0f, 1f)
            val isCrossed = abs(swipeOffsetX.value) >= commitThresholdPx

            fun commitFullscreenSwipe(status: TriageStatus) {
                if (effectiveOnSwipeDecision == null) return
                coroutineScope.launch {
                    val targetX = if (status == TriageStatus.TRASH_VAULT) {
                        -viewportWidthPx * 1.25f
                    } else {
                        viewportWidthPx * 1.25f
                    }
                    RoboHaptics.explicitAction(haptic)
                    swipeOffsetX.animateTo(
                        targetValue = targetX,
                        animationSpec = tween(durationMillis = RoboMotion.SWIPE_EXIT_MS)
                    )
                    effectiveOnSwipeDecision(currentPhoto, status)
                    if (effectiveRemainingCount <= 1 && deckPhotos.size <= 1) {
                        onDismiss()
                    }
                }
            }

            val edgeTintColor = when {
                dragFraction > 0.15f -> KeepEmerald.copy(alpha = (0.28f * absProgress).coerceIn(0f, 0.32f))
                dragFraction < -0.15f -> DestructiveRed.copy(alpha = (0.28f * absProgress).coerceIn(0f, 0.32f))
                else -> Color.Transparent
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg)
                    .onSizeChanged { size ->
                        if (size.width > 0) viewportWidthPx = size.width.toFloat()
                    }
                    .testTag("fullscreen_photo_viewer")
            ) {
                // Next photo subtle preview underneath when dragging in Full Image View
                if (nextPhotoInDeck != null && absProgress > 0.04f) {
                    val nextScale = 0.94f + (0.06f * absProgress)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(nextScale)
                            .graphicsLayer { alpha = (0.35f + 0.65f * absProgress).coerceIn(0.35f, 1f) },
                        contentAlignment = Alignment.Center
                    ) {
                        PhotoThumbnailView(
                            photo = nextPhotoInDeck,
                            showBadges = false,
                            contentScale = ContentScale.Fit,
                            cornerRadius = 0.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Active Fullscreen Zoomable + Swipeable Media Surface
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = panX
                            translationY = panY
                            rotationZ = if (scale <= 1.05f) {
                                ((swipeOffsetX.value / viewportWidthPx.coerceAtLeast(1f)) * RoboMotion.MAX_SWIPE_ROTATION_DEG)
                                    .coerceIn(-RoboMotion.MAX_SWIPE_ROTATION_DEG, RoboMotion.MAX_SWIPE_ROTATION_DEG)
                            } else {
                                0f
                            }
                        }
                        .pointerInput(currentPhoto.stableIdentityKey) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.05f) {
                                        scale = 1f
                                        panX = 0f
                                        panY = 0f
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                        .pointerInput(currentPhoto.stableIdentityKey, scale) {
                            if (scale <= 1.05f && (effectiveOnSwipeDecision != null || deckPhotos.size > 1)) {
                                val velocityTracker = VelocityTracker()
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        velocityTracker.resetTracking()
                                        hasCrossedThresholdHaptic = false
                                    },
                                    onDragEnd = {
                                        val velocityX = velocityTracker.calculateVelocity().x
                                        val currentX = swipeOffsetX.value
                                        val crossedRight = currentX > commitThresholdPx ||
                                            (velocityX > 1050f && currentX > viewportWidthPx * 0.12f)
                                        val crossedLeft = currentX < -commitThresholdPx ||
                                            (velocityX < -1050f && currentX < -viewportWidthPx * 0.12f)

                                        if (effectiveOnSwipeDecision != null) {
                                            when {
                                                crossedRight -> commitFullscreenSwipe(TriageStatus.KEEP)
                                                crossedLeft -> commitFullscreenSwipe(TriageStatus.TRASH_VAULT)
                                                else -> {
                                                    coroutineScope.launch {
                                                        swipeOffsetX.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = RoboMotion.restrainedSpring()
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            // Gallery browse swipe when opened outside Review decision context
                                            when {
                                                crossedLeft && browseIndex + 1 < deckPhotos.size -> {
                                                    browseIndex += 1
                                                }
                                                crossedRight && browseIndex > 0 -> {
                                                    browseIndex -= 1
                                                }
                                                else -> {
                                                    coroutineScope.launch {
                                                        swipeOffsetX.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = RoboMotion.restrainedSpring()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            swipeOffsetX.animateTo(0f, RoboMotion.restrainedSpring())
                                        }
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        val nextVal = swipeOffsetX.value + dragAmount
                                        val crossedNow = abs(nextVal) >= commitThresholdPx
                                        if (crossedNow && !hasCrossedThresholdHaptic) {
                                            hasCrossedThresholdHaptic = true
                                            RoboHaptics.swipeThresholdTick(haptic)
                                        } else if (!crossedNow && hasCrossedThresholdHaptic) {
                                            hasCrossedThresholdHaptic = false
                                        }
                                        coroutineScope.launch {
                                            swipeOffsetX.snapTo(nextVal)
                                        }
                                    }
                                )
                            } else {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    scale = newScale
                                    if (newScale > 1.01f) {
                                        val maxPan = 900f * (newScale - 1f)
                                        panX = (panX + pan.x).coerceIn(-maxPan, maxPan)
                                        panY = (panY + pan.y).coerceIn(-maxPan, maxPan)
                                    } else {
                                        panX = 0f
                                        panY = 0f
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    PhotoThumbnailView(
                        photo = currentPhoto,
                        showBadges = false,
                        contentScale = ContentScale.Fit,
                        cornerRadius = 0.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 2.dp)
                    )

                    // Gradual edge tint while swiping in Full Image View
                    if (edgeTintColor != Color.Transparent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(
                                    width = if (isCrossed) 3.dp else 1.5.dp,
                                    color = if (dragFraction > 0f) KeepEmerald else DestructiveRed
                                )
                                .background(edgeTintColor)
                        )
                    }

                    // Progressive KEEP / REVIEW BIN stamps in Full Image View
                    if (effectiveOnSwipeDecision != null && dragFraction > 0.12f) {
                        val stampScale = if (isCrossed) 1.08f else (0.88f + 0.18f * absProgress)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = KeepEmerald.copy(alpha = (0.25f + 0.70f * absProgress).coerceIn(0.25f, 0.95f)),
                            border = BorderStroke(if (isCrossed) 2.5.dp else 1.5.dp, KeepEmerald),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 82.dp, start = 20.dp)
                                .scale(stampScale)
                                .testTag("fullscreen_keep_stamp")
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
                    } else if (effectiveOnSwipeDecision != null && dragFraction < -0.12f) {
                        val stampScale = if (isCrossed) 1.08f else (0.88f + 0.18f * absProgress)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DestructiveRed.copy(alpha = (0.25f + 0.70f * absProgress).coerceIn(0.25f, 0.95f)),
                            border = BorderStroke(if (isCrossed) 2.5.dp else 1.5.dp, DestructiveRed),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 82.dp, end = 20.dp)
                                .scale(stampScale)
                                .testTag("fullscreen_bin_stamp")
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

                // Top bar with filename, queue count, zoom toggle, details button, and close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.84f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = currentPhoto.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (effectiveRemainingCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ElevatedSlate.copy(alpha = 0.9f),
                                    border = BorderStroke(1.dp, CardBorderSlate)
                                ) {
                                    Text(
                                        text = "$effectiveRemainingCount left",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricBlue,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (effectiveOnSwipeDecision != null && scale <= 1.05f) {
                                "${LuminaViewModel.formatBytes(currentPhoto.fileSizeBytes)} • Swipe left to Bin, right to Keep • Double-tap to zoom"
                            } else {
                                "${currentPhoto.width}×${currentPhoto.height} • ${currentPhoto.folderName} • Double-tap to reset zoom"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canUndo && onUndoLast != null) {
                            IconButton(
                                onClick = onUndoLast,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(CharcoalSurface.copy(alpha = 0.88f))
                                    .testTag("fullscreen_photo_undo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Undo,
                                    contentDescription = "Undo last decision",
                                    tint = KeepEmerald,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (scale > 1.05f) {
                                    scale = 1f
                                    panX = 0f
                                    panY = 0f
                                } else {
                                    scale = 2.2f
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CharcoalSurface.copy(alpha = 0.88f))
                                .testTag("fullscreen_photo_zoom_toggle")
                        ) {
                            Icon(
                                imageVector = if (scale > 1.05f) Icons.Filled.ZoomOutMap else Icons.Filled.ZoomIn,
                                contentDescription = if (scale > 1.05f) "Reset Zoom" else "Zoom In",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (onInspectDetails != null) {
                            IconButton(
                                onClick = {
                                    onDismiss()
                                    onInspectDetails()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(CharcoalSurface.copy(alpha = 0.88f))
                                    .testTag("fullscreen_photo_details_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Inspect Details",
                                    tint = SpineBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CharcoalSurface.copy(alpha = 0.88f))
                                .testTag("fullscreen_photo_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Fullscreen Photo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Bottom Fullscreen Swipe Triage Dock (when opened from Review mode)
                if (effectiveOnSwipeDecision != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, ObsidianBg.copy(alpha = 0.94f))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = currentPhoto.humanFriendlyWhy,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { commitFullscreenSwipe(TriageStatus.TRASH_VAULT) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DestructiveRed.copy(alpha = 0.22f),
                                    contentColor = DestructiveRed
                                ),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, DestructiveRed.copy(alpha = 0.55f)),
                                contentPadding = PaddingValues(vertical = 11.dp),
                                modifier = Modifier
                                    .weight(1.15f)
                                    .testTag("fullscreen_swipe_bin_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Review Bin",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Review Bin", fontWeight = FontWeight.Bold)
                            }

                            if (effectiveOnSkipPhoto != null) {
                                OutlinedButton(
                                    onClick = { effectiveOnSkipPhoto(currentPhoto) },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, CardBorderSlate),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = CharcoalSurface.copy(alpha = 0.90f),
                                        contentColor = TextPrimary
                                    ),
                                    contentPadding = PaddingValues(vertical = 11.dp),
                                    modifier = Modifier
                                        .weight(0.85f)
                                        .testTag("fullscreen_swipe_skip_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipNext,
                                        contentDescription = "Skip",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Skip", fontWeight = FontWeight.Medium)
                                }
                            }

                            Button(
                                onClick = { commitFullscreenSwipe(TriageStatus.KEEP) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KeepEmerald,
                                    contentColor = Color(0xFF042016)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(vertical = 11.dp),
                                modifier = Modifier
                                    .weight(1.15f)
                                    .testTag("fullscreen_swipe_keep_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Keep",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Keep", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
