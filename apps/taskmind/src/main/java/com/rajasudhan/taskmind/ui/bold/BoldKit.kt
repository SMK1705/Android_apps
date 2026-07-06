package com.rajasudhan.taskmind.ui.bold

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajasudhan.taskmind.ui.common.sourceVisual
import com.rajasudhan.taskmind.ui.theme.BoldOnAccent
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeCard
import com.rajasudhan.taskmind.ui.theme.ShapeChip
import com.rajasudhan.taskmind.ui.theme.ShapePill
import kotlin.math.roundToInt

// ── Kinds ────────────────────────────────────────────────────────────────────
enum class BoldKind(val label: String) { TASK("Task"), EVENT("Event"), REMINDER("Reminder"), NOTE("Note"), WAITING("Waiting on") }

@Composable fun BoldKind.color(): Color = with(BoldTheme.colors) {
    when (this@color) { BoldKind.TASK -> task; BoldKind.EVENT -> event; BoldKind.REMINDER -> reminder; BoldKind.NOTE -> idea; BoldKind.WAITING -> amber }
}

@Composable fun BoldKind.soft(): Color = with(BoldTheme.colors) {
    when (this@soft) { BoldKind.TASK -> taskSoft; BoldKind.EVENT -> eventSoft; BoldKind.REMINDER -> reminderSoft; BoldKind.NOTE -> ideaSoft; BoldKind.WAITING -> amberSoft }
}

fun BoldKind.icon(): ImageVector = when (this) {
    BoldKind.TASK -> Icons.Outlined.CheckCircle
    BoldKind.EVENT -> Icons.Outlined.CalendarToday
    BoldKind.REMINDER -> Icons.Outlined.Schedule
    BoldKind.NOTE -> Icons.Outlined.Lightbulb
    BoldKind.WAITING -> Icons.Outlined.HourglassEmpty
}

/** Bridge the app's suggestion types onto the design's kinds (a dated reminder reads as an Event). */
fun boldKindFor(type: String, hasDate: Boolean): BoldKind = when (type.lowercase()) {
    "todo" -> BoldKind.TASK
    "note" -> BoldKind.NOTE
    "waiting_on" -> BoldKind.WAITING
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
            .clip(RoundedCornerShape(6.dp))
            .background(kind.soft())
            .padding(horizontal = 8.dp, vertical = 3.dp)
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
            .semantics { role = Role.Button; this.selected = selected }
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
    ASK("ask", "Ask", Icons.Outlined.AutoAwesome),
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

// ── Redesigned page header ─────────────────────────────────────────────────────
/** 38dp visual chip inside a 48dp touch target — the design's rounded square header buttons. */
@Composable
fun BoldHeaderIconButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = BoldTheme.colors
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
                .border(1.dp, c.line, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** Sun/moon toggle that flips the app between an explicit Light and Dark theme. */
@Composable
fun BoldThemeToggle(isDark: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    val c = BoldTheme.colors
    BoldHeaderIconButton(
        onClick = onToggleTheme,
        label = if (isDark) "Switch to light theme" else "Switch to dark theme",
        modifier = modifier,
    ) {
        Icon(
            if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The redesigned per-screen header: serif title + a mono subtitle on the left, with the theme
 * toggle (and any [trailing] actions before it) on the right. Used by Notes / Sources / Privacy /
 * Settings; the Inbox keeps its own header (count subtitle + scan + overflow).
 */
@Composable
fun BoldPageHeader(
    title: String,
    subtitle: String,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = BoldTheme.colors
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = BoldType.screenTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(7.dp))
            Text(
                subtitle,
                style = BoldType.srcLabel.copy(fontSize = 11.5.sp, letterSpacing = 0.3.sp),
                color = c.muted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp), verticalAlignment = Alignment.CenterVertically) {
            trailing()
            BoldThemeToggle(isDark, onToggleTheme)
        }
    }
}

/**
 * The handoff's bottom sheet: a screen-coloured modal with a drag handle, a serif title and an
 * optional muted subtitle, then [content]. Used for the snooze / reminder / calendar pickers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoldBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = BoldTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.screen,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(38.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line2))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp)) {
            Text(title, style = BoldType.emptyTitle.copy(fontSize = 26.sp), color = c.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = BoldType.body.copy(fontSize = 13.sp, lineHeight = 19.sp), color = c.muted)
            }
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

/** The handoff's pill toggle: a 46×28 track (accent on / line2 off) with a 22dp knob, inside a
 *  48dp-tall touch target with proper Switch semantics. */
@Composable
fun BoldSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = BoldTheme.colors
    Box(
        modifier
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(width = 46.dp, height = 28.dp).clip(RoundedCornerShape(14.dp))
                .background(if (checked) c.accent else c.line2).padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier.size(22.dp).shadow(1.dp, CircleShape).clip(CircleShape)
                    .background(if (checked) BoldOnAccent else c.surface)
            )
        }
    }
}
