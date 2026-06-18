package com.rajasudhan.taskmind.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Subtly scales a clickable down while pressed — the tactile "give" premium cards have. Pass the
 * same [interactionSource] you give to `clickable`/`combinedClickable`.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * An animated diagonal sheen for skeleton placeholders. Apply to a shaped box's `drawWithContent`
 * via [shimmer]; the colors come from the caller so it reads correctly in light and dark.
 */
@Composable
fun Modifier.shimmer(base: androidx.compose.ui.graphics.Color, highlight: androidx.compose.ui.graphics.Color): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSweep"
    )
    return this.drawWithContent {
        val width = size.width
        val sweep = width * 1.6f
        val start = -sweep + progress * (width + sweep)
        val brush = Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(start, 0f),
            end = Offset(start + sweep, size.height)
        )
        drawRect(brush)
        // Draw the node's own content over the sheen (skeleton boxes have none, so this is a no-op
        // there) — without it, a `.shimmer()` on any composable with children would hide them.
        drawContent()
    }
}
