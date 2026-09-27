package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.KeepEmerald
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
import kotlinx.coroutines.Dispatchers
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
 * Identity-verified thumbnail renderer for both Photos and Videos.
 * Strictly keyed on `photo.stableIdentityKey` (`id + uriString + fileSizeBytes + dateTakenEpochMs`)
 * so thumbnails NEVER bleed or repeat across different items in LazyLists, Grids, or Swipe Decks.
 */
@Composable
fun PhotoThumbnailView(
    photo: PhotoEntity,
    modifier: Modifier = Modifier,
    showBadges: Boolean = true
) {
    val identityKey = photo.stableIdentityKey

    key(identityKey) {
        val context = LocalContext.current
        var asyncImageFailed by remember(identityKey) { mutableStateOf(false) }

        val verifiedBitmap by produceState<Bitmap?>(
            initialValue = MediaThumbnailIdentityCache.get(identityKey),
            key1 = identityKey
        ) {
            if (value == null && (photo.isVideo || asyncImageFailed)) {
                value = loadVerifiedThumbnailBitmap(context, photo, targetPx = 480)
            }
        }

        val imageRequest = remember(identityKey) {
            ImageRequest.Builder(context)
                .data(Uri.parse(photo.uriString))
                .memoryCacheKey(identityKey)
                .diskCacheKey(identityKey)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(false)
                .build()
        }

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(ElevatedSlate)
                .border(1.dp, CardBorderSlate, RoundedCornerShape(12.dp))
        ) {
            if (photo.isVideo) {
                if (verifiedBitmap != null) {
                    Image(
                        bitmap = verifiedBitmap!!.asImageBitmap(),
                        contentDescription = photo.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CleanMediaFallbackPlaceholder(photo = photo)
                }
            } else {
                if (!asyncImageFailed) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = photo.title,
                        contentScale = ContentScale.Crop,
                        onError = { asyncImageFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (verifiedBitmap != null) {
                    Image(
                        bitmap = verifiedBitmap!!.asImageBitmap(),
                        contentDescription = photo.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
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
                // Top-left Best Shot or Protected pill
                if (photo.isBestShotInCluster) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = KeepEmerald.copy(alpha = 0.92f),
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
                } else if (photo.sentimentalProtected) {
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
