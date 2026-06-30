package com.rajasudhan.taskmind.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.ui.bold.BoldHeaderIconButton
import com.rajasudhan.taskmind.ui.bold.BoldPageHeader
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeCard

/**
 * The Privacy tab from the handoff: an auditable, at-a-glance status board — the egress hero, the
 * always-on guarantees, and the destructive "delete everything" action. The configurable knobs live
 * one tap away in [SettingsScreen], reached via the header gear.
 */
@Composable
fun PrivacyScreen(
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val c = BoldTheme.colors
    val egressEvents by viewModel.egressEvents.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val useOnDeviceLlm by viewModel.useOnDeviceLlm.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.screen)) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 8.dp)) {
            BoldPageHeader(
                title = "Privacy",
                subtitle = "Auditable · on-device by default",
                isDark = isDark,
                onToggleTheme = onToggleTheme,
                trailing = {
                    BoldHeaderIconButton(onClick = onOpenSettings, label = "Settings") {
                        Icon(Icons.Outlined.Settings, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { EgressHero(entries = egressEvents.size) }

            item {
                PrivacyStatusRow(
                    icon = Icons.Outlined.Lock,
                    title = "App lock",
                    subtitle = "Biometric on launch & every return",
                    badge = if (appLockEnabled) "ON" else "OFF",
                    badgeColor = if (appLockEnabled) c.accent else c.ink2,
                    badgeSoft = if (appLockEnabled) c.accentGlow else c.surface2
                )
            }
            item {
                PrivacyStatusRow(
                    icon = Icons.Outlined.Shield,
                    title = "Encryption at rest",
                    subtitle = "SQLCipher · AES-256-GCM",
                    badge = "ON", badgeColor = c.accent, badgeSoft = c.accentGlow
                )
            }
            item {
                PrivacyStatusRow(
                    icon = Icons.Outlined.Memory,
                    title = "Understanding engine",
                    subtitle = if (useOnDeviceLlm) "On-device Gemma · local" else "Cloud LLM · calls are logged",
                    badge = if (useOnDeviceLlm) "LOCAL" else "CLOUD",
                    badgeColor = if (useOnDeviceLlm) c.event else c.amber,
                    badgeSoft = if (useOnDeviceLlm) c.eventSoft else c.amberSoft
                )
            }
            item {
                PrivacyStatusRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "No telemetry",
                    subtitle = "Zero analytics · zero tracking",
                    badge = "✓", badgeColor = c.accent, badgeSoft = c.accentGlow
                )
            }

            item { DeleteAllButton(onClick = { showDeleteDialog = true }) }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "This permanently erases all approved notes, pending suggestions, source toggles, " +
                        "and saved keys/settings. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAllData(); showDeleteDialog = false }) {
                    Text("Delete", color = c.skip)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

/** The reassuring "No data has left this device" card — accent-glow fill, shield-check, egress count. */
@Composable
private fun EgressHero(entries: Int) {
    val c = BoldTheme.colors
    val clean = entries == 0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.accentGlow)
            .border(1.dp, c.accent, RoundedCornerShape(20.dp)).padding(vertical = 24.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(c.accent.copy(alpha = 0.14f)))
            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = c.accent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (clean) "No data has left this device"
                else "$entries egress event${if (entries == 1) "" else "s"} logged",
            style = BoldType.emptyTitle.copy(fontSize = 25.sp), color = c.ink, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (clean) "Every byte of understanding ran locally. The egress log is empty."
                else "Metadata only — never content. Review the full log in Settings.",
            style = BoldType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
            color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 260.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "EGRESS LOG · $entries ENTRIES",
            style = BoldType.detailMeta.copy(fontSize = 10.5.sp, letterSpacing = 1.sp), color = c.accent
        )
    }
}

@Composable
private fun PrivacyStatusRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    badgeSoft: Color,
) {
    val c = BoldTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(ShapeCard).background(c.surface).border(1.dp, c.line, ShapeCard)
            .padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(c.bg2),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = BoldType.sourceName.copy(fontSize = 14.5.sp), color = c.ink)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = BoldType.sourceMeta.copy(fontSize = 12.5.sp), color = c.muted)
        }
        Box(
            Modifier.clip(RoundedCornerShape(7.dp)).background(badgeSoft).padding(horizontal = 9.dp, vertical = 4.dp)
        ) { Text(badge, style = BoldType.detailMeta.copy(fontSize = 10.sp), color = badgeColor) }
    }
}

@Composable
private fun DeleteAllButton(onClick: () -> Unit) {
    val c = BoldTheme.colors
    Box(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp).height(48.dp)
            .clip(RoundedCornerShape(14.dp)).border(1.dp, c.skip, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Delete all private data"; role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "DELETE ALL PRIVATE DATA",
            style = BoldType.detailMeta.copy(fontSize = 12.sp, letterSpacing = 0.5.sp), color = c.skip
        )
    }
}
