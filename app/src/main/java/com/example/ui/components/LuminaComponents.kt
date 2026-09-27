package com.example.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ElevatedSlate
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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val videoThumbMemoryCache = ConcurrentHashMap<String, Bitmap>()

/**
 * Tactile dark-charcoal card with a purposeful left colored spine inspired by ColorNote productivity layouts.
 */
@Composable
fun SpineCard(
    spineColor: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = CharcoalSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, CardBorderSlate)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(spineColor)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun TelemetryPill(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PhotoThumbnailView(
    photo: PhotoEntity,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    val context = LocalContext.current
    val videoBitmap by produceState<Bitmap?>(initialValue = videoThumbMemoryCache[photo.uriString], key1 = photo.uriString) {
        if (photo.isVideo && value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(photo.uriString)
                    val loaded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
                        runCatching {
                            context.contentResolver.loadThumbnail(uri, Size(360, 360), null)
                        }.getOrNull()
                    } else null
                    val finalBmp = loaded ?: run {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, uri)
                        val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                        retriever.release()
                        frame
                    }
                    if (finalBmp != null) {
                        videoThumbMemoryCache[photo.uriString] = finalBmp
                    }
                    finalBmp
                }.getOrNull()
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(ElevatedSlate)
    ) {
        if (photo.isVideo && videoBitmap != null) {
            Image(
                bitmap = videoBitmap!!.asImageBitmap(),
                contentDescription = photo.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(Uri.parse(photo.uriString))
                    .crossfade(true)
                    .build(),
                contentDescription = photo.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (photo.isVideo) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Video",
                        tint = SpineCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = photo.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ForensicPhotoListItem(
    photo: PhotoEntity,
    onPhotoClick: (PhotoEntity) -> Unit,
    onQuickVaultClick: (PhotoEntity) -> Unit,
    onQuickKeepClick: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val spineColor = when {
        photo.isBestShotInCluster -> SpineEmerald
        photo.sentimentalProtected -> SpineBlue
        photo.junkConfidence >= 0.78f -> SpineCoral
        photo.junkConfidence >= 0.45f -> SpineAmber
        else -> Color(photo.categoryEnum.spineHex)
    }

    SpineCard(
        spineColor = spineColor,
        modifier = modifier
            .fillMaxWidth()
            .testTag("photo_card_${photo.id}"),
        onClick = { onPhotoClick(photo) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PhotoThumbnailView(
                    photo = photo,
                    modifier = Modifier
                        .size(74.dp)
                        .border(1.dp, CardBorderSlate, RoundedCornerShape(10.dp)),
                    cornerRadius = 10.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = photo.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (photo.isBestShotInCluster) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "Best Shot Winner",
                                tint = SpineEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                        } else if (photo.sentimentalProtected) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Protected Record",
                                tint = SpineCyan,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Text(
                        text = photo.aiDescription.ifBlank { photo.junkReason },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (photo.isVideo) {
                            TelemetryPill(
                                text = "VIDEO • ${photo.formattedDuration}",
                                accentColor = SpineCyan
                            )
                        }
                        TelemetryPill(
                            text = LuminaViewModel.formatBytes(photo.fileSizeBytes),
                            accentColor = TextSecondary
                        )
                        TelemetryPill(
                            text = "Sharp ${photo.sharpnessScore}",
                            accentColor = if (photo.sharpnessScore >= 70) SpineEmerald else SpineCoral
                        )
                        TelemetryPill(
                            text = "${photo.ageInDays}d old",
                            accentColor = SpineAmber
                        )
                    }
                }
            }

            // Bottom status & 1-tap action strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(spineColor)
                    )
                    Text(
                        text = photo.junkReason.ifBlank { photo.categoryEnum.label },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (photo.triageStatusEnum != TriageStatus.KEEP) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SpineEmerald.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, SpineEmerald.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clickable { onQuickKeepClick(photo) }
                                .testTag("quick_keep_${photo.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Keep media",
                                    tint = SpineEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Keep",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SpineEmerald
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SpineCoral.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, SpineCoral.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clickable { onQuickVaultClick(photo) }
                            .testTag("quick_vault_${photo.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Move to Quarantine Vault",
                                tint = SpineCoral,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Vault",
                                style = MaterialTheme.typography.labelMedium,
                                color = SpineCoral
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EncryptedCacheBadgeStrip(
    entriesCount: Int,
    hitRatePercent: Int,
    privacyTitle: String,
    activeProviderLabel: String,
    onOpenAiHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAiHub)
            .testTag("encrypted_cache_status_strip"),
        shape = RoundedCornerShape(12.dp),
        color = ElevatedSlate,
        border = BorderStroke(1.dp, CardBorderSlate)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "AES-256 Encrypted Cache",
                    tint = SpineEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "AES-256 Cache: $entriesCount hashes ($hitRatePercent% hit)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "BYOK Provider",
                    tint = SpineViolet,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$activeProviderLabel • $privacyTitle",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpineViolet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
