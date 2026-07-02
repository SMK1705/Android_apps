package com.rajasudhan.taskmind.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.ui.theme.BoldTheme
import com.rajasudhan.taskmind.ui.theme.BoldType
import com.rajasudhan.taskmind.ui.theme.ShapeCard

/**
 * "What TaskMind knows about me": the on-device rejection-learning memory made visible and reversible.
 * Every sender the user keeps dismissing is listed with how often, whether it's actively down-ranked,
 * and a one-tap Forget — turning an invisible penalty (previously only clearable by wiping all data)
 * into an auditable, editable privacy flex. Everything here is local; nothing ever left the device.
 */
@Composable
fun KnowsScreen(
    viewModel: KnowsViewModel = hiltViewModel(),
) {
    val c = BoldTheme.colors
    val senders by viewModel.senders.collectAsState()
    val loaded by viewModel.loaded.collectAsState()

    // The back arrow + "What TaskMind knows" title live in the Scaffold top bar (isDetail in MainActivity).
    Column(Modifier.fillMaxSize().background(c.screen)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { KnowsHero(count = senders.size) }
            items(senders, key = { it.kind + "|" + it.value }) { sender ->
                SenderRow(sender = sender, onForget = { viewModel.forget(sender) })
            }
            if (senders.isNotEmpty()) {
                item { ForgetAllButton(onClick = viewModel::forgetAll) }
            } else if (loaded) {
                item { EmptyNote() }
            }
        }
    }
}

/** Summary card: nothing learned (calm) or a count of down-ranked senders (amber). */
@Composable
private fun KnowsHero(count: Int) {
    val c = BoldTheme.colors
    val clean = count == 0
    val tint = if (clean) c.accent else c.amber
    val soft = if (clean) c.accentGlow else c.amberSoft
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(soft)
            .border(1.dp, tint, RoundedCornerShape(20.dp)).padding(vertical = 24.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(tint.copy(alpha = 0.14f)))
            Icon(Icons.Outlined.Memory, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (clean) "TaskMind hasn't tuned anyone out"
                else "$count learned sender${if (count == 1) "" else "s"}",
            style = BoldType.emptyTitle.copy(fontSize = 24.sp), color = c.ink, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (clean) "The only thing TaskMind learns is who you keep dismissing — and it hasn't had to. All on-device."
                else "Senders you repeatedly dismiss get quietly down-ranked. It's all on this device — forget any of them below.",
            style = BoldType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
            color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp)
        )
    }
}

@Composable
private fun SenderRow(sender: LearnedSender, onForget: () -> Unit) {
    val c = BoldTheme.colors
    val dot = if (sender.downRanked) c.amber else c.ink3
    Column(
        Modifier.fillMaxWidth().clip(ShapeCard).background(c.surface).border(1.dp, c.line, ShapeCard)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Text(sender.value, style = BoldType.sourceName.copy(fontSize = 14.5.sp), color = c.ink, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(c.bg2)
                    .clickable(onClick = onForget).semantics { role = Role.Button }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) { Text("FORGET", style = BoldType.detailMeta.copy(fontSize = 11.sp), color = c.ink) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            (if (sender.downRanked) "Down-ranked" else "Learning") +
                " · dismissed ${sender.count} time${if (sender.count == 1) "" else "s"}",
            style = BoldType.sourceMeta.copy(fontSize = 12.5.sp), color = c.muted
        )
    }
}

/** Shown once loaded confirms there's genuinely nothing learned, under the calm hero. */
@Composable
private fun EmptyNote() {
    val c = BoldTheme.colors
    Text(
        "When you dismiss suggestions from the same sender a few times, they'll show up here — always removable.",
        style = BoldType.sourceMeta.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
        color = c.muted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ForgetAllButton(onClick: () -> Unit) {
    val c = BoldTheme.colors
    Box(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp).height(48.dp)
            .clip(RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "FORGET EVERYTHING LEARNED",
            style = BoldType.detailMeta.copy(fontSize = 12.sp, letterSpacing = 0.5.sp), color = c.ink2
        )
    }
}
