package com.tadone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tadone.AppState
import com.tadone.data.InboxItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val Danger = Color(0xFFB3401E)

private fun kindStyle(kind: String): Triple<ImageVector, Color, String> = when (kind) {
    "deadline" -> Triple(Icons.AutoMirrored.Rounded.Assignment, Palette.Amber, "Deadline")
    "class" -> Triple(Icons.Rounded.CalendarMonth, Color(0xFF3F6E8C), "Class")
    "error" -> Triple(Icons.Rounded.ErrorOutline, Danger, "Problem")
    else -> Triple(Icons.Rounded.TaskAlt, Palette.Olive, "Activity")
}

/** Notifications tab: everything Tadone has told you, plus what's coming up next. */
@Composable
fun InboxScreen(s: AppState) {
    var filter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Unread", "Deadlines", "Classes", "Activity", "Problems")
    val shown = s.inbox.filter {
        when (filter) {
            "Unread" -> !it.read; "Deadlines" -> it.kind == "deadline"; "Classes" -> it.kind == "class"
            "Activity" -> it.kind == "activity"; "Problems" -> it.kind == "error"; else -> true
        }
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    fun day(i: InboxItem) = Instant.ofEpochMilli(i.createdAt).atZone(zone).toLocalDate()
    val groups = shown.groupBy { when (day(it)) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> "Earlier" } }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notifications", style = MaterialTheme.typography.displaySmall, color = Palette.Bark)
                    Text(if (s.unread == 0) "You're all caught up" else "${s.unread} unread", color = Palette.Olive)
                }
                ClayButton("Mark all read", Icons.Rounded.DoneAll, Palette.Cream, Palette.Bark, enabled = s.unread > 0) { s.markRead(null) }
                Spacer(Modifier.width(8.dp))
                ClayButton("Clear", Icons.Rounded.DeleteSweep, Palette.Cream, Palette.Bark, enabled = s.inbox.isNotEmpty()) { s.clearInbox() }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                filters.forEach { f ->
                    val on = f == filter
                    Text(f, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else Palette.Bark,
                        modifier = Modifier.clay(if (on) Palette.Amber else Palette.Cream, 50.dp, if (on) 6.dp else 3.dp)
                            .pressable { filter = f }.padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            if (shown.isEmpty()) Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.NotificationsNone, null, tint = Palette.Olive, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(8.dp))
                Text("Nothing here", color = Palette.Bark, style = MaterialTheme.typography.titleMedium)
                Text("Reminders, finished imports and syncs, and problems show up here.", color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelSmall)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 30.dp)) {
                listOf("Today", "Yesterday", "Earlier").forEach { g ->
                    val items = groups[g].orEmpty()
                    if (items.isNotEmpty()) {
                        item(key = "h-$g") { SectionLabel(g) }
                        items(items, key = { it.id }) { i -> InboxRow(s, i) }
                    }
                }
            }
        }
        UpcomingPanel(s, Modifier.width(330.dp).fillMaxHeight().padding(top = 20.dp, end = 20.dp, bottom = 20.dp))
    }
}

@Composable
private fun InboxRow(s: AppState, i: InboxItem) {
    val (icon, color, label) = kindStyle(i.kind)
    Row(Modifier.fillMaxWidth().clay(if (i.read) Palette.Paper else Palette.Paper.lighten(.4f), 18.dp, if (i.read) 3.dp else 7.dp)
        .pressable { s.markRead(i); s.openTarget(i.target) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(color, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i.title, color = Palette.Bark, fontWeight = if (i.read) FontWeight.SemiBold else FontWeight.Bold)
                Text((if (label.equals(i.title, true)) "" else "  ·  $label") + "  ·  ${agoLabel(i.createdAt)}", color = Palette.Bark.copy(.55f), style = MaterialTheme.typography.labelSmall)
            }
            Text(i.body, color = Palette.Ink.copy(if (i.read) .7f else 1f), style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        }
        if (!i.read) Box(Modifier.padding(horizontal = 8.dp).size(9.dp).background(Palette.Amber, RoundedCornerShape(50)))
        IconButton({ s.clearInbox(i) }) { Icon(Icons.Rounded.Close, "Dismiss", tint = Palette.Bark.copy(.5f), modifier = Modifier.size(18.dp)) }
    }
}

/** What will notify you next: open deadlines in the coming week and today's remaining classes. Local data only. */
@Composable
private fun UpcomingPanel(s: AppState, modifier: Modifier) {
    val now = System.currentTimeMillis()
    val week = now + 7 * 86_400_000L
    val due = s.tasks.filter { !it.done && it.dueAt != null && it.dueAt in now..week }.sortedBy { it.dueAt }
    val overdue = s.tasks.count { !it.done && it.dueAt != null && it.dueAt < now }
    val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
    val classes = s.events.filter { it.occursOn(LocalDate.now()) && it.endMin > nowMin }.sortedBy { it.startMin }

    Column(modifier.clay(Palette.Paper, 30.dp, 18.dp).padding(20.dp)) {
        Text("Coming up", style = MaterialTheme.typography.titleLarge, color = Palette.Bark)
        Text("You'll be reminded about these", color = Palette.Olive, style = MaterialTheme.typography.labelSmall)
        if (overdue > 0) {
            Spacer(Modifier.height(10.dp))
            Text("$overdue overdue task${if (overdue > 1) "s" else ""}", color = Danger, fontWeight = FontWeight.Bold,
                modifier = Modifier.pressable { s.filter = com.tadone.Filter.Open; s.screen = com.tadone.Screen.Tasks })
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Classes today")
        if (classes.isEmpty()) Text("No more classes today", color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.bodySmall)
        classes.take(4).forEach { e ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(EventColors[e.color.mod(EventColors.size)], RoundedCornerShape(50)))
                Spacer(Modifier.width(8.dp))
                Text(e.title, color = Palette.Bark, modifier = Modifier.weight(1f), maxLines = 1)
                Text(minutesLabel(e.startMin), color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Due this week")
        if (due.isEmpty()) Text("Nothing due in the next 7 days", color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(due, key = { it.id }) { t ->
                Column(Modifier.fillMaxWidth().clay(Palette.Cream.lighten(.3f), 14.dp, 2.dp)
                    .pressable { s.openTarget("task:${t.id}") }.padding(10.dp)) {
                    Text(t.title, color = Palette.Bark, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    dueLabel(t.dueAt)?.let { (txt, col) -> Text(txt, color = col, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}
