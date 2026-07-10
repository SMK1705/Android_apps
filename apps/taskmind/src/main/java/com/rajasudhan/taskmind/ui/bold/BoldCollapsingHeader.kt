package com.rajasudhan.taskmind.ui.bold

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import kotlin.math.roundToInt

/**
 * Header collapse progress: 0f fully expanded, 1f fully collapsed. [offsetPx] is the (negative) scroll the
 * header has absorbed; the collapsible region shrinks from its measured [fullPx] down to the pinned bar
 * ([pinnedPx]). Pure, so the mapping is unit-testable without Compose.
 */
internal fun collapseFraction(offsetPx: Float, fullPx: Float, pinnedPx: Float): Float {
    val max = (fullPx - pinnedPx).coerceAtLeast(0f)
    return if (max > 0f) (-offsetPx / max).coerceIn(0f, 1f) else 0f
}

// The pinned bar's footprint, shared by the pinned Row's layout and the collapse floor (pinnedBarPx) so
// they can't drift. Height stays >= 48dp so the header buttons keep the accessibility-minimum tap target.
private val PinnedBarTopInset = 14.dp
private val PinnedBarHeight = 48.dp

/**
 * The shared collapsing screen header used by the tab screens, so they all behave identically. Expanded, it
 * shows a big serif [title] + [subtitle] (and any [collapsible] content, e.g. a search field) with a row of
 * trailing controls. As [content]'s list scrolls up, the title/subtitle/collapsible region shrinks into a
 * slim pinned bar carrying a compact title + the same controls; the [pinned] slot (e.g. filter chips) stays
 * put. The trailing row is, left→right: the screen's own [actions], the help "?" ([onOpenGuide]), the app
 * lock (when [onLock] is non-null), then the theme toggle — folded in here so no separate top app bar is
 * needed.
 *
 * [content] hosts the scrollable list and fills the space under the header; give it the same [listState]
 * passed here so the header can settle open when the list is empty or shorter than the screen. Change
 * [resetKey] (e.g. on a filter/segment change) to re-expand and re-anchor to the top.
 *
 * Collapse state is read only inside layout / graphicsLayer / drawBehind (deferred phases), so a scroll
 * frame updates the layer without recomposing the screen.
 */
@Composable
fun BoldCollapsingHeader(
    title: String,
    subtitle: String,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenGuide: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    resetKey: Any? = Unit,
    onLock: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    collapsible: @Composable ColumnScope.() -> Unit = {},
    pinned: @Composable ColumnScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val c = BoldTheme.colors
    val density = LocalDensity.current
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }     // collapse offset, coerced to [-max, 0]
    var collapsibleFullPx by remember { mutableFloatStateOf(0f) }  // measured natural height of the region
    // Collapsed height the region floors at = the pinned bar's footprint, kept in lockstep with the Row below.
    val pinnedBarPx = with(density) { (PinnedBarTopInset + PinnedBarHeight).toPx() }

    val headerNsc = remember {
        object : NestedScrollConnection {
            private fun drag(dy: Float): Offset {
                val max = (collapsibleFullPx - pinnedBarPx).coerceAtLeast(0f)
                if (max <= 0f) return Offset.Zero
                // Only COLLAPSE (dy < 0) when the list can actually scroll down — a short/empty list has
                // nothing to scroll, so collapsing would leave the header stuck. Expanding (dy > 0) is always
                // allowed so a collapsed header can re-open.
                if (dy < 0f && !listState.canScrollForward) return Offset.Zero
                val prev = headerOffsetPx
                headerOffsetPx = (prev + dy).coerceIn(-max, 0f)
                return Offset(0f, headerOffsetPx - prev)
            }
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0f) drag(available.y) else Offset.Zero      // finger up → collapse first
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y > 0f) drag(available.y) else Offset.Zero      // list already at top → re-expand
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val max = (collapsibleFullPx - pinnedBarPx).coerceAtLeast(0f)
                if (max > 0f && headerOffsetPx != 0f && headerOffsetPx != -max) {
                    // Only settle to fully-collapsed when the list can still scroll down; otherwise the fling
                    // would over-collapse a list that now fits, and the watcher below would bounce it back open.
                    val target = if (listState.canScrollForward && -headerOffsetPx > max / 2f) -max else 0f
                    animate(headerOffsetPx, target, available.y, spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> headerOffsetPx = v }
                }
                return Velocity.Zero
            }
        }
    }

    // Re-expand + re-anchor to the top whenever the caller's content identity changes, so a fresh list starts
    // expanded rather than inheriting a stale collapsed offset.
    LaunchedEffect(resetKey) {
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) listState.scrollToItem(0)
        animate(headerOffsetPx, 0f) { v, _ -> headerOffsetPx = v }
    }
    // The header can only STAY collapsed while the list actually has room to scroll: whenever the list fits the
    // viewport (nothing to scroll in either direction), settle it back open. Derived from listState directly, so
    // it can never get stuck collapsed regardless of how the caller counts its content.
    LaunchedEffect(Unit) {
        snapshotFlow { listState.canScrollForward || listState.canScrollBackward }
            .collect { scrollable -> if (!scrollable && headerOffsetPx != 0f) animate(headerOffsetPx, 0f) { v, _ -> headerOffsetPx = v } }
    }

    Box(modifier.fillMaxSize().background(c.screen)) {
        Column(Modifier.fillMaxSize().nestedScroll(headerNsc)) {
            // --- Collapsing region (title + subtitle + optional collapsible content) with the pinned bar on top ---
            Box(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth()
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val full = placeable.height
                            if (collapsibleFullPx != full.toFloat()) collapsibleFullPx = full.toFloat()
                            val collapse = (-headerOffsetPx).roundToInt().coerceIn(0, full)
                            layout(placeable.width, full - collapse) { placeable.place(0, -collapse) }
                        }
                        .padding(start = 22.dp, end = 22.dp, top = PinnedBarTopInset)
                ) {
                    Text(
                        title,
                        style = BoldType.screenTitle,
                        color = c.ink,
                        // Big and compact titles cross-fade over the same [0.15, 0.85] window so their alphas are
                        // complements (sum to 1) — no mid-collapse dip where both are near-invisible.
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .graphicsLayer { alpha = 1f - ((collapseFraction(headerOffsetPx, collapsibleFullPx, pinnedBarPx) - 0.15f) / 0.7f).coerceIn(0f, 1f) }
                            .semantics { heading() }
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        subtitle,
                        style = BoldType.srcLabel.copy(fontSize = 11.5.sp, letterSpacing = 0.3.sp),
                        color = c.muted,
                        modifier = Modifier.graphicsLayer { alpha = 1f - collapseFraction(headerOffsetPx, collapsibleFullPx, pinnedBarPx) }
                    )
                    collapsible()
                }
                // Pinned bar: an opaque scrim (fades in on collapse to hide the shrinking region) carrying the
                // compact title (fades in) plus the trailing controls (always present, so they work in both states).
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .drawBehind { drawRect(color = c.screen, alpha = collapseFraction(headerOffsetPx, collapsibleFullPx, pinnedBarPx)) }
                        .padding(start = 22.dp, end = 18.dp, top = PinnedBarTopInset)
                        .height(PinnedBarHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        title,
                        style = BoldType.screenTitle.copy(fontSize = 22.sp, lineHeight = 24.sp),
                        color = c.ink,
                        // Visual duplicate of the big title (which carries the heading semantic) — keep it out
                        // of the semantics tree so screen readers and tests see a single title node.
                        modifier = Modifier
                            .graphicsLayer { alpha = ((collapseFraction(headerOffsetPx, collapsibleFullPx, pinnedBarPx) - 0.15f) / 0.7f).coerceIn(0f, 1f) }
                            .clearAndSetSemantics {}
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-4).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        actions()
                        BoldHeaderIconButton(onClick = onOpenGuide, label = "How to use TaskMind") {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = c.ink, modifier = Modifier.height(18.dp))
                        }
                        if (onLock != null) {
                            BoldHeaderIconButton(onClick = onLock, label = "Lock app") {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = c.ink, modifier = Modifier.height(18.dp))
                            }
                        }
                        BoldThemeToggle(isDark, onToggleTheme)
                    }
                }
            }
            // --- Pinned slot (e.g. filter chips): stays put while the content scrolls under it ---
            pinned()
            Spacer(Modifier.height(14.dp))
            // --- The scrollable content fills the rest ---
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}
