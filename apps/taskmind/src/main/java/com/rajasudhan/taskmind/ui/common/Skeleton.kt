package com.rajasudhan.taskmind.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Placeholder cards with a soft sheen, shown while the first data load is in flight — so loading
 *  reads as "working", distinct from a genuine empty state. */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 5,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(count) { SkeletonCard() }
    }
}

@Composable
private fun SkeletonCard() {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShimmerBar(0.35f, 12.dp, base, highlight)
            ShimmerBar(0.85f, 18.dp, base, highlight)
            ShimmerBar(0.95f, 12.dp, base, highlight)
            ShimmerBar(0.55f, 12.dp, base, highlight)
        }
    }
}

@Composable
private fun ShimmerBar(fraction: Float, height: Dp, base: Color, highlight: Color) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .shimmer(base, highlight)
    )
}
