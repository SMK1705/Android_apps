package com.rajasudhan.taskmind.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The app's one premium card primitive: a neutral tonal surface with the rounded shared shape, a
 * hairline of elevation, an optional slim **accent bar** down the left edge (the only place a
 * category color appears on a card now), and — when clickable — a ripple plus a subtle press-scale.
 *
 * Callers fill [content] as a [RowScope] placed right after the accent bar, so they keep full control
 * of inner padding and layout (a single Column for the Inbox card; checkbox + column + delete for a
 * Notes row). Replaces the old fully color-tinted `Card`s.
 */
@Composable
fun TmCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    val interaction = remember { MutableInteractionSource() }
    val body: @Composable () -> Unit = {
        Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = verticalAlignment) {
            if (accent != null) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            }
            content()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().pressScale(interaction),
            shape = shape,
            color = color,
            shadowElevation = 1.dp,
            interactionSource = interaction,
            content = body
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            shadowElevation = 1.dp,
            content = body
        )
    }
}
