package com.rajasudhan.taskmind.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rajasudhan.taskmind.data.source.HealthCheck
import com.rajasudhan.taskmind.data.source.HealthStatus
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeCard

/**
 * The Reliability Doctor: an honest, at-a-glance diagnosis of whether TaskMind can actually deliver.
 * Surfaces the silent killers of a background app — revoked notification access, OEM battery-freezing,
 * a quieted channel, missing exact-alarm permission — each with a one-tap deep link to the fix, plus a
 * live test-alarm round-trip that proves the OS still wakes us on time.
 */
@Composable
fun ReliabilityScreen(
    viewModel: ReliabilityViewModel = hiltViewModel(),
) {
    val c = BoldTheme.colors
    val context = LocalContext.current
    val checks by viewModel.checks.collectAsState()
    val test by viewModel.test.collectAsState()

    // Re-run the diagnosis whenever we return to the screen (e.g. back from a system settings fix).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launch a fix screen and re-diagnose on return.
    val fixLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refresh()
    }

    val problems = checks.count { it.status != HealthStatus.OK }

    // The back arrow + "Reliability" title live in the Scaffold top bar (isDetail route in MainActivity).
    Column(Modifier.fillMaxSize().background(c.screen)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { ReliabilityHero(problems = problems) }
            items(checks, key = { it.id }) { check ->
                CheckRow(check = check, onFix = { intent -> runCatching { fixLauncher.launch(intent) } })
            }
            item { TestAlarmCard(state = test, onRun = viewModel::runTestAlarm) }
        }
    }
}

/** Summary card: all-clear (green) or a count of what needs attention (amber). */
@Composable
private fun ReliabilityHero(problems: Int) {
    val c = BoldTheme.colors
    val clean = problems == 0
    val tint = if (clean) c.accent else c.amber
    val soft = if (clean) c.accentGlow else c.amberSoft
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(soft)
            .border(1.dp, tint, RoundedCornerShape(20.dp)).padding(vertical = 24.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(tint.copy(alpha = 0.14f)))
            Icon(Icons.Outlined.Bolt, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (clean) "TaskMind is fully wired up" else "$problems thing${if (problems == 1) "" else "s"} to fix",
            style = BoldType.emptyTitle.copy(fontSize = 24.sp), color = c.ink, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (clean) "Every channel a reminder needs to reach you is open."
            else "Reminders and capture may be unreliable until these are resolved.",
            style = BoldType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
            color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp)
        )
    }
}

@Composable
private fun CheckRow(check: HealthCheck, onFix: (android.content.Intent) -> Unit) {
    val c = BoldTheme.colors
    val (dot, label) = when (check.status) {
        HealthStatus.OK -> c.accent to "OK"
        HealthStatus.WARN -> c.amber to "CHECK"
        HealthStatus.FAIL -> c.skip to "ACTION"
    }
    Column(
        Modifier.fillMaxWidth().clip(ShapeCard).background(c.surface).border(1.dp, c.line, ShapeCard)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Text(check.title, style = BoldType.sourceName.copy(fontSize = 14.5.sp), color = c.ink, modifier = Modifier.weight(1f))
            Text(label, style = BoldType.detailMeta.copy(fontSize = 10.sp), color = dot)
        }
        Spacer(Modifier.height(6.dp))
        Text(check.detail, style = BoldType.sourceMeta.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = c.muted)
        val fix = check.fix
        if (fix != null && check.fixLabel != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(c.bg2)
                    .clickable { onFix(fix) }.semantics { role = Role.Button }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) { Text(check.fixLabel, style = BoldType.detailMeta.copy(fontSize = 12.sp), color = c.ink) }
        }
    }
}

@Composable
private fun TestAlarmCard(state: TestAlarmState, onRun: () -> Unit) {
    val c = BoldTheme.colors
    val running = state == TestAlarmState.Running
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(ShapeCard).background(c.surface)
            .border(1.dp, c.line, ShapeCard).padding(16.dp)
    ) {
        Text("Test alarm", style = BoldType.sourceName.copy(fontSize = 14.5.sp), color = c.ink)
        Spacer(Modifier.height(4.dp))
        val (msg, tint) = when (state) {
            TestAlarmState.Idle -> "Fire a real alarm now to prove the OS wakes TaskMind on time." to c.muted
            TestAlarmState.Running -> "Waiting for the alarm to come back…" to c.muted
            is TestAlarmState.Delivered -> "Delivered in ${"%.1f".format(state.latencyMs / 1000.0)}s — alarms work." to c.accent
            TestAlarmState.Missed -> "No delivery — something is dropping alarms. Fix battery optimization above." to c.skip
            TestAlarmState.CannotSchedule -> "Exact alarms aren't permitted — allow them above, then retry." to c.amber
        }
        Text(msg, style = BoldType.sourceMeta.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = tint)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.clip(RoundedCornerShape(10.dp)).background(if (running) c.surface2 else c.accent)
                .clickable(enabled = !running, onClick = onRun).semantics { role = Role.Button }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (running) "TESTING…" else "RUN TEST",
                style = BoldType.detailMeta.copy(fontSize = 12.sp),
                color = if (running) c.ink2 else Color.Black
            )
        }
    }
}
