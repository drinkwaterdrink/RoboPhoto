package com.example.ui.theme

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui. composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * ============================================================================
 * PASS 4 SECTION A: CENTRALIZED ROBOPHOTO STEALTH GRAPHITE DESIGN SYSTEM
 * ============================================================================
 * Centralizes colors, spacing, typography, elevation, radius, motion durations,
 * spring specs, haptic conventions, icon sizing, and reusable tactile components.
 */

object RoboColors {
    val Background = ObsidianBg          // #08090B
    val Surface1 = CharcoalSurface       // #111316
    val Surface2 = ElevatedSlate         // #181B1F
    val Surface3 = SubtleSurface         // #1F242B
    val Border = CardBorderSlate         // #272B31
    val PrimaryText = TextPrimary        // #F3F4F6
    val SecondaryText = TextSecondary    // #9399A3
    val MutedText = TextMuted            // #646A75
    val ElectricBlue = com.example.ui.theme.ElectricBlue // #168BFF
    val Keep = KeepEmerald               // Restrained emerald/teal
    val DeleteBin = DestructiveRed       // Restrained red
    val WarningAmber = SpineAmber
    val AccentViolet = SpineViolet
    val AccentCyan = SpineCyan
}

object RoboSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}

object RoboRadius {
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 28.dp

    val Small: Dp = sm
    val Medium: Dp = md
    val Card: Dp = lg
    val Large: Dp = xl
    val ExtraLarge: Dp = xxl
}

object RoboElevation {
    val none: Dp = 0.dp
    val low: Dp = 2.dp
    val medium: Dp = 4.dp
    val high: Dp = 8.dp
}

object RoboIconSize {
    val xs: Dp = 14.dp
    val sm: Dp = 16.dp
    val md: Dp = 20.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}

object RoboMotion {
    const val PRESS_DURATION_MS = 95
    const val THUMBNAIL_FADE_MS = 160
    const val SWIPE_EXIT_MS = 210
    const val BEST_SHOT_BADGE_MS = 280
    const val CLUSTER_COLLAPSE_MS = 440
    const val NUMBER_COUNT_MS = 420

    const val PRESS_SCALE = 0.98f
    const val MAX_SWIPE_ROTATION_DEG = 7.0f
    const val SWIPE_MAX_ROTATION_DEG = 7.0f
    const val SWIPE_COMMIT_DISTANCE_FRACTION = 0.24f
    const val SWIPE_COMMIT_VELOCITY_PX_PER_SEC = 1050f

    val SwipeRecenterSpring = spring<Float>(
        dampingRatio = 0.84f,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> restrainedSpring() = spring<T>(
        dampingRatio = 0.84f,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> gentleSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    @Composable
    fun isReducedMotionEnabled(): Boolean {
        val context = LocalContext.current
        return remember(context) {
            runCatching {
                val scale = Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
                )
                scale == 0f
            }.getOrDefault(false)
        }
    }
}

object RoboHaptics {
    fun swipeThresholdTick(haptic: HapticFeedback) {
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    fun explicitAction(haptic: HapticFeedback) {
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    fun bestShotSelection(haptic: HapticFeedback) {
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    fun completion(haptic: HapticFeedback) {
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}

/**
 * Subtle tactile press microinteraction (Pass 4 Section F):
 * Scales 1.0 -> 0.98 over ~95ms on press and springs back to 1.0 on release.
 */
fun Modifier.roboPressable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = RoboMotion.PRESS_SCALE
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = RoboMotion.isReducedMotionEnabled()
    val targetScale = if (enabled && isPressed && !reducedMotion) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (isPressed) {
            tween(durationMillis = RoboMotion.PRESS_DURATION_MS, easing = FastOutSlowInEasing)
        } else {
            RoboMotion.restrainedSpring()
        },
        label = "roboPressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun Modifier.roboPressScale(
    enabled: Boolean = true,
    pressedScale: Float = RoboMotion.PRESS_SCALE
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this.roboPressable(
        interactionSource = interactionSource,
        enabled = enabled,
        pressedScale = pressedScale
    )
}

/**
 * Reusable Media Card container with subtle Best Shot / Selected border highlighting and press scale.
 */
@Composable
fun RoboMediaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBestShot: Boolean = false,
    isSelected: Boolean = false,
    cornerRadius: Dp = RoboRadius.md,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val borderColor by animateColorAsState(
        targetValue = when {
            isBestShot -> RoboColors.Keep
            isSelected -> RoboColors.ElectricBlue
            else -> RoboColors.Border
        },
        animationSpec = tween(durationMillis = RoboMotion.BEST_SHOT_BADGE_MS),
        label = "roboMediaCardBorder"
    )
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = RoboColors.Surface1,
        border = BorderStroke(if (isBestShot || isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .roboPressable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/**
 * Reusable Stealth Graphite Card with optional tactile press microinteraction & surface brightening.
 */
@Composable
fun RoboCard(
    modifier: Modifier = Modifier,
    containerColor: Color = RoboColors.Surface1,
    borderColor: Color = RoboColors.Border,
    cornerRadius: Dp = RoboRadius.lg,
    contentPadding: PaddingValues = PaddingValues(RoboSpacing.md),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedContainerColor by animateColorAsState(
        targetValue = if (isPressed && onClick != null) RoboColors.Surface2 else containerColor,
        animationSpec = tween(durationMillis = RoboMotion.PRESS_DURATION_MS),
        label = "roboCardSurface"
    )

    val clickableModifier = if (onClick != null) {
        modifier
            .roboPressable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    } else {
        modifier
    }

    Card(
        modifier = clickableModifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = animatedContainerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = RoboElevation.none)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Reusable Primary Action Button with tactile press scale (1.0 -> 0.98), surface brightening,
 * and minimum 48.dp touch target for accessibility.
 */
@Composable
fun RoboPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    containerColor: Color = RoboColors.ElectricBlue,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    cornerRadius: Dp = RoboRadius.md
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val brightenedColor = remember(containerColor) {
        Color(
            red = (containerColor.red + 0.06f).coerceAtMost(1f),
            green = (containerColor.green + 0.06f).coerceAtMost(1f),
            blue = (containerColor.blue + 0.06f).coerceAtMost(1f),
            alpha = containerColor.alpha
        )
    }
    val activeColor by animateColorAsState(
        targetValue = if (isPressed && enabled) brightenedColor else containerColor,
        animationSpec = tween(durationMillis = RoboMotion.PRESS_DURATION_MS),
        label = "roboPrimaryBtnColor"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = activeColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(cornerRadius),
        contentPadding = PaddingValues(horizontal = RoboSpacing.md, vertical = RoboSpacing.sm),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .roboPressable(interactionSource = interactionSource, enabled = enabled)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(RoboIconSize.md)
            )
            Spacer(Modifier.width(RoboSpacing.xs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingIcon != null) {
            Spacer(Modifier.width(RoboSpacing.xs))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(RoboIconSize.sm)
            )
        }
    }
}

/**
 * Reusable Secondary Button with Stealth Graphite Surface 2 styling, 1dp border,
 * and press microinteraction.
 */
@Composable
fun RoboSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = RoboColors.PrimaryText,
    containerColor: Color = RoboColors.Surface2,
    contentColor: Color = RoboColors.PrimaryText,
    borderColor: Color = RoboColors.Border,
    enabled: Boolean = true,
    cornerRadius: Dp = RoboRadius.md
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val activeBg by animateColorAsState(
        targetValue = if (isPressed && enabled) RoboColors.Surface3 else containerColor,
        animationSpec = tween(durationMillis = RoboMotion.PRESS_DURATION_MS),
        label = "roboSecondaryBtnColor"
    )

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(cornerRadius),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = activeBg,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = RoboSpacing.sm, vertical = RoboSpacing.sm),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .roboPressable(interactionSource = interactionSource, enabled = enabled)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(RoboIconSize.sm)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Reusable Pill component for status badges, counts, and compact actions.
 */
@Composable
fun RoboPill(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = RoboColors.SecondaryText,
    containerColor: Color = accentColor.copy(alpha = 0.14f),
    borderColor: Color = accentColor.copy(alpha = 0.32f),
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) {
        modifier
            .roboPressable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    } else {
        modifier
    }

    Surface(
        modifier = clickModifier,
        shape = RoundedCornerShape(RoboRadius.sm),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = RoboSpacing.xs, vertical = RoboSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RoboSpacing.xxs)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(RoboIconSize.xs)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Reusable Section Header with title, optional subtitle, and optional trailing badge/action.
 */
@Composable
fun RoboSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = RoboColors.PrimaryText,
                fontWeight = FontWeight.Bold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RoboColors.SecondaryText
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        } else if (!trailingText.isNullOrBlank()) {
            RoboPill(
                text = trailingText,
                accentColor = if (onTrailingClick != null) RoboColors.ElectricBlue else RoboColors.SecondaryText,
                containerColor = RoboColors.Surface2,
                borderColor = RoboColors.Border,
                onClick = onTrailingClick
            )
        }
    }
}

/**
 * Animated counting number / byte formatter (Pass 4 Sections A, H, L):
 * Smoothly animates upward toward [targetBytes] or [targetCount] while respecting Reduced Motion.
 */
@Composable
fun RoboProgressNumber(
    targetValue: Long,
    formatter: (Long) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
    color: Color = RoboColors.PrimaryText,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val reducedMotion = RoboMotion.isReducedMotionEnabled()
    var displayedValue by remember { mutableLongStateOf(targetValue) }

    LaunchedEffect(targetValue, reducedMotion) {
        if (reducedMotion || displayedValue == targetValue) {
            displayedValue = targetValue
            return@LaunchedEffect
        }
        val startValue = displayedValue
        val diff = targetValue - startValue
        val steps = 14
        val stepDelay = (RoboMotion.NUMBER_COUNT_MS / steps).toLong().coerceAtLeast(12L)
        for (i in 1..steps) {
            val fraction = i.toFloat() / steps.toFloat()
            val eased = 1f - (1f - fraction) * (1f - fraction)
            displayedValue = startValue + (diff * eased).toLong()
            delay(stepDelay)
        }
        displayedValue = targetValue
    }

    Text(
        text = formatter(displayedValue),
        style = style,
        color = color,
        fontWeight = fontWeight,
        modifier = modifier.semantics {
            contentDescription = formatter(targetValue)
        }
    )
}

/**
 * Reusable Stealth Graphite Empty State Card.
 */
@Composable
fun RoboEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accentColor: Color = RoboColors.Keep,
    actionContent: (@Composable () -> Unit)? = null
) {
    RoboCard(
        modifier = modifier,
        cornerRadius = RoboRadius.xl,
        contentPadding = PaddingValues(RoboSpacing.lg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RoboSpacing.sm)
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f)),
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = RoboColors.PrimaryText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = RoboColors.SecondaryText,
                textAlign = TextAlign.Center
            )
            if (actionContent != null) {
                Spacer(Modifier.height(RoboSpacing.xs))
                actionContent()
            }
        }
    }
}

/**
 * Reusable Stealth Graphite Bottom Sheet container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoboBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = RoboColors.Background,
        scrimColor = Color.Black.copy(alpha = 0.74f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            content = content
        )
    }
}
