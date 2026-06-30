package com.rajasudhan.taskmind.ui.bold

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rajasudhan.taskmind.ui.common.sourceVisual
import com.rajasudhan.taskmind.ui.theme.BoldOnAccent
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeCard
import com.rajasudhan.taskmind.ui.theme.ShapeChip
import com.rajasudhan.taskmind.ui.theme.ShapePill
import kotlin.math.roundToInt

// ── Kinds ────────────────────────────────────────────────────────────────────
enum class BoldKind(val label: String) { TASK("Task"), EVENT("Event"), REMINDER("Reminder"), NOTE("Note") }

@Composable fun BoldKind.color(): Color = with(BoldTheme.colors) {
    when (this@color) { BoldKind.TASK -> task; BoldKind.EVENT -> event; BoldKind.REMINDER -> reminder; BoldKind.NOTE -> idea }
}

@Composable fun BoldKind.soft(): Color = with(BoldTheme.colors) {
    when (this@soft) { BoldKind.TASK -> taskSoft; BoldKind.EVENT -> eventSoft; BoldKind.REMINDER -> reminderSoft; BoldKind.NOTE -> ideaSoft }
}

fun BoldKind.icon(): ImageVector = when (this) {
    BoldKind.TASK -> Icons.Outlined.CheckCircle
    BoldKind.EVENT -> Icons.Outlined.CalendarToday
    BoldKind.REMINDER -> Icons.Outlined.Schedule
    BoldKind.NOTE -> Icons.Outlined.Lightbulb
}

/** Bridge the app's 3 suggestion types onto the design's 4 kinds (a dated reminder reads as an Event). */
fun boldKindFor(type: String, hasDate: Boolean): BoldKind = when (type.lowercase()) {
    "todo" -> BoldKind.TASK
    "note" -> BoldKind.NOTE
    "reminder" -> if (hasDate) BoldKind.EVENT else BoldKind.REMINDER
    else -> BoldKind.NOTE
}

// ── Text bits ────────────────────────────────────────────────────────────────
@Composable
fun BoldEyebrow(text: String, modifier: Modifier = Modifier, color: Color = BoldTheme.colors.accent) {
    Text(text.uppercase(), style = BoldType.eyebrow, color = color, modifier = modifier)
}

/** Editorial screen header: accent eyebrow + serif title, with an optional right-aligned block. */
@Composable
fun BoldScreenHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            BoldEyebrow(eyebrow)
            Text(title, style = BoldType.screenTitle, color = BoldTheme.colors.ink, modifier = Modifier.semantics { heading() })
        }
        trailing?.invoke()
    }
}

// ── Containers ───────────────────────────────────────────────────────────────
/** Flat editorial card: surface fill + 1dp hairline border (no elevation). */
@Composable
fun BoldCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = ShapeCard,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = BoldTheme.colors
    var m = modifier.clip(shape).background(c.surface).border(1.dp, c.line, shape)
    if (onClick != null) m = m.clickable { onClick() }
    Column(m, content = content)
}

// ── Chips / dots ─────────────────────────────────────────────────────────────
@Composable
fun BoldKindChip(kind: BoldKind, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(kind.soft())
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) { Text(kind.label, style = BoldType.kindChip, color = kind.color()) }
}

@Composable
fun BoldKindDot(kind: BoldKind, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(2.dp)).background(kind.color()))
}

/** Confidence read-out: a percentage, or an amber "likely noise" pill below 50%. */
@Composable
fun BoldConfidenceChip(confidence: Double, modifier: Modifier = Modifier) {
    val c = BoldTheme.colors
    val pct = (confidence.coerceIn(0.0, 1.0) * 100).roundToInt()
    if (pct < 50) {
        Box(
            modifier
                .clip(CircleShape)
                .background(c.amberSoft)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) { Text("likely noise", style = BoldType.confBadge, color = c.amber) }
    } else {
        Text("$pct%", style = BoldType.confBadge, color = c.ink3, modifier = modifier)
    }
}

/** Source badge: channel icon in a small well + the sender label. */
@Composable
fun BoldSourcePill(source: String, modifier: Modifier = Modifier) {
    val c = BoldTheme.colors
    val v = sourceVisual(source)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(c.surface2),
            contentAlignment = Alignment.Center,
        ) { Icon(v.icon, contentDescription = null, tint = c.ink2, modifier = Modifier.size(13.dp)) }
        Text(source, style = BoldType.srcLabel, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Segment/filter chip with an optional count, used by the Notes filters. */
@Composable
fun BoldFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, count: Int? = null) {
    val c = BoldTheme.colors
    val fg = if (selected) BoldOnAccent else c.ink2
    Row(
        modifier
            .clip(ShapeChip)
            .background(if (selected) c.accent else c.surface2)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, style = BoldType.filterChip, color = fg)
        if (count != null) Text("$count", style = BoldType.detailMeta, color = fg.copy(alpha = 0.6f))
    }
}

// ── Buttons ──────────────────────────────────────────────────────────────────
@Composable
fun BoldPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = true,
) {
    val c = BoldTheme.colors
    val bg = if (filled) c.accent else c.surface2
    val fg = if (filled) BoldOnAccent else c.ink
    Row(
        modifier.clip(ShapePill).background(bg).clickable { onClick() }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp)) }
        Text(text, style = BoldType.button, color = fg)
    }
}

// ── Bottom navigation ────────────────────────────────────────────────────────
enum class BoldTab(val route: String, val label: String, val icon: ImageVector) {
    INBOX("inbox", "Inbox", Icons.Outlined.Inbox),
    NOTES("notes", "Notes", Icons.Outlined.Layers),
    SOURCES("sources", "Sources", Icons.Outlined.Tune),
    PRIVACY("settings", "Privacy", Icons.Outlined.Shield),
}

@Composable
fun BoldBottomNav(currentRoute: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = BoldTheme.colors
    Column(modifier.fillMaxWidth().background(c.screen)) {
        HorizontalDivider(color = c.line, thickness = 1.dp)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(74.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoldTab.values().forEach { tab ->
                val active = currentRoute == tab.route
                BoldNavItem(tab = tab, active = active, onClick = { onSelect(tab.route) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.BoldNavItem(tab: BoldTab, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BoldTheme.colors
    val tint = if (active) c.accent else c.ink3
    Column(
        modifier
            // Fill the 74dp nav row so the whole cell is a ≥48dp touch target, and expose the
            // selected state + Tab role so TalkBack announces "selected" for the active tab.
            .fillMaxHeight()
            .semantics { selected = active; role = Role.Tab }
            .clickable(interactionSource = rememberMutableInteraction(), indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .width(46.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (active) c.surface2 else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(tab.label.uppercase(), style = BoldType.tabLabel, color = tint)
    }
}

@Composable
private fun rememberMutableInteraction(): MutableInteractionSource {
    return androidx.compose.runtime.remember { MutableInteractionSource() }
}
