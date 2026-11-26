package com.tadone.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tadone.AppState
import com.tadone.Filter
import com.tadone.data.Task
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date

@Composable
fun TasksScreen(s: AppState) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(28.dp)) {
            Header(s)
            Spacer(Modifier.height(18.dp))
            QuickAdd(s)
            Spacer(Modifier.height(16.dp))
            FilterBar(s)
            Spacer(Modifier.height(12.dp))
            val list = s.visible
            if (list.isEmpty()) EmptyState(s.filter)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp, 6.dp, 6.dp, 40.dp)) {
                items(list, key = { it.id }) { t ->
                    TaskCard(t, t.id == s.selectedId, Modifier.animateItem(), onToggle = { s.toggle(t) }) {
                        s.selectedId = if (s.selectedId == t.id) null else t.id
                    }
                }
            }
        }
        AnimatedContent(
            s.selected,
            transitionSpec = { (slideInHorizontally { it / 2 } + fadeIn()) togetherWith (slideOutHorizontally { it / 2 } + fadeOut()) },
            contentKey = { it?.id },
        ) { t ->
            if (t != null) TaskDetail(s, t, Modifier.width(440.dp).fillMaxHeight().padding(top = 20.dp, end = 20.dp, bottom = 20.dp))
        }
    }
}

@Composable
private fun Header(s: AppState) {
    val h = LocalTime.now().hour
    val hello = when { h < 12 -> "Good morning"; h < 18 -> "Good afternoon"; else -> "Good evening" }
    val open = s.tasks.count { !it.done }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("$hello ✨", style = MaterialTheme.typography.displaySmall, color = Palette.Bark)
            Text(
                if (open == 0) "Nothing pending. Enjoy the calm." else "$open task${if (open == 1) "" else "s"} waiting — let's make some magic.",
                color = Palette.Olive, style = MaterialTheme.typography.bodyMedium,
            )
        }
        ClayButton("Sync Classroom", Icons.Rounded.School, Palette.Olive, enabled = s.busy == null) { s.syncClassroom() }
    }
}

@Composable
private fun QuickAdd(s: AppState) {
    var text by remember { mutableStateOf("") }
    ClayField(
        text, { text = it }, "Tell Tadone what to do… e.g. \"physics lab report due friday 5pm\"",
        Modifier.fillMaxWidth(),
        onDone = { s.quickAdd(text); text = "" },
        leading = { Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.Amber) },
    )
}

@Composable
private fun FilterBar(s: AppState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Filter.entries.forEach { f ->
            val sel = f == s.filter
            val bg by animateColorAsState(if (sel) Palette.Bark else Palette.Paper)
            val fg by animateColorAsState(if (sel) Palette.Cream else Palette.Bark)
            val count = when (f) {
                Filter.Open -> s.tasks.count { !it.done }
                Filter.Classroom -> s.tasks.count { it.isClassroom }
                Filter.Done -> s.tasks.count { it.done }
                Filter.All -> s.tasks.size
                Filter.Today -> null
            }
            Text(
                f.name + (count?.let { "  $it" } ?: ""),
                color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clay(bg, 50.dp, if (sel) 8.dp else 2.dp).pressable { s.filter = f }.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(f: Filter) {
    val bob by rememberInfiniteTransition().animateFloat(-6f, 6f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).graphicsLayer { translationY = bob; rotationZ = bob }.clay(Palette.Amber, 26.dp, 16.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.Cream, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(if (f == Filter.Done) "Nothing finished yet" else "All clear!", style = MaterialTheme.typography.headlineSmall, color = Palette.Bark)
        Text("Add a task above or sync your Google Classroom.", color = Palette.Olive)
    }
}

@Composable
private fun TaskCard(t: Task, selected: Boolean, modifier: Modifier, onToggle: () -> Unit, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Palette.Cream.lighten(.2f) else Palette.Paper)
    val ring by animateDpAsState(if (selected) 18.dp else 8.dp)
    Tilt3D(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clay(bg, 22.dp, ring).pressable(onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MagicCheck(t.done, onToggle)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).alpha(if (t.done) .55f else 1f)) {
                Text(
                    t.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (t.isClassroom) Chip("CLASSROOM", Palette.Olive)
                    if (t.course.isNotBlank()) Chip(t.course.take(28), Palette.CreamDeep, Palette.Bark)
                    dueLabel(t.dueAt)?.let { (txt, c) -> if (!t.done) Text(txt, color = c, style = MaterialTheme.typography.labelSmall) }
                }
            }
            Box(Modifier.size(10.dp).background(listOf(Palette.Olive.lighten(.4f), Palette.Amber, Color(0xFFB3401E))[t.priority.coerceIn(0, 2)], CircleShape))
        }
    }
}

private val dueFmt = SimpleDateFormat("yyyy-MM-dd HH:mm")

@Composable
private fun TaskDetail(s: AppState, t: Task, modifier: Modifier) {
    var title by remember(t.id) { mutableStateOf(t.title) }
    var course by remember(t.id) { mutableStateOf(t.course) }
    var notes by remember(t.id) { mutableStateOf(t.notes) }
    var due by remember(t.id) { mutableStateOf(t.dueAt?.let { dueFmt.format(Date(it)) } ?: "") }
    var prio by remember(t.id) { mutableStateOf(t.priority) }
    var tab by remember(t.id) { mutableStateOf(0) }
    var link by remember(t.id) { mutableStateOf("") }
    LaunchedEffect(t.id) { s.loadChat(t.id) }

    fun edited() = t.copy(title = title.ifBlank { t.title }, course = course, notes = notes, priority = prio,
        dueAt = due.takeIf { it.isNotBlank() }?.let { runCatching { dueFmt.parse(it).time }.getOrNull() } ?: if (due.isBlank()) null else t.dueAt)

    Column(modifier.clay(Palette.Paper, 30.dp, 20.dp).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clay(Palette.CreamDeep, 50.dp, 2.dp).padding(4.dp)) {
                listOf("Details", "Ask Tadone").forEachIndexed { i, label ->
                    val sel = tab == i
                    val bg by animateColorAsState(if (sel) Palette.Amber else Color.Transparent)
                    Text(label, color = if (sel) Color.White else Palette.Bark, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).background(bg, RoundedCornerShape(50)).pressable { tab = i }.padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton({ s.selectedId = null }) { Icon(Icons.Rounded.Close, "Close", tint = Palette.Bark) }
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) + slideInVertically { 20 } togetherWith fadeOut(tween(120)) }) { which ->
            if (which == 1) {
                ChatPanel(
                    s.chat, s.thinking, { s.ask(t.id, it) },
                    listOf("Break it down", "Explain simply", "Plan my time", "Check my notes"),
                    Modifier.fillMaxSize(),
                    empty = "I know this task. Ask me to break it down, explain the topic, or plan your time until it's due.",
                )
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SectionLabel("Title"); ClayField(title, { title = it }, "Task title", Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SectionLabel("Subject"); ClayField(course, { course = it }, "e.g. Biology", Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SectionLabel("Due (yyyy-MM-dd HH:mm)"); ClayField(due, { due = it }, "2026-12-01 17:00", Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SectionLabel("Priority")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Low", "Normal", "High").forEachIndexed { i, l ->
                        val sel = prio == i
                        Text(l, color = if (sel) Color.White else Palette.Bark, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clay(if (sel) Palette.Olive else Palette.Cream, 50.dp, if (sel) 6.dp else 2.dp).pressable { prio = i }.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionLabel("Notes / instructions")
                ClayField(notes, { notes = it }, "Anything to remember…", Modifier.fillMaxWidth(), singleLine = false, minLines = 5)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClayButton("Save", Icons.Rounded.Check) { s.save(edited()); s.toast = "Saved" }
                    ClayButton("Delete", Icons.Rounded.Delete, Palette.Cream, Palette.Bark) { s.delete(t) }
                }

                if (t.isClassroom) {
                    Spacer(Modifier.height(22.dp))
                    HorizontalDivider(color = Palette.Bark.copy(.12f))
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Google Classroom")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Chip(t.submissionState ?: "NOT SYNCED", if (t.done) Palette.Olive else Palette.Amber)
                        Spacer(Modifier.weight(1f))
                        t.link?.let { l -> TextButton({ runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(l)) } }) { Text("Open in Classroom ↗", color = Palette.Olive) } }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClayButton("Upload file", Icons.Rounded.UploadFile, Palette.Bark, Palette.Cream, enabled = s.busy == null) {
                            pickFile()?.let { s.uploadFile(t, it) }
                        }
                        ClayButton(if (t.done) "Unsubmit" else "Turn in", Icons.Rounded.TaskAlt, Palette.Olive, enabled = s.busy == null) { s.toggle(t) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClayField(link, { link = it }, "Attach a link (https://…)", Modifier.weight(1f), onDone = { if (link.isNotBlank()) { s.attachLink(t, link); link = "" } })
                        Spacer(Modifier.width(8.dp))
                        IconButton({ if (link.isNotBlank()) { s.attachLink(t, link); link = "" } }) { Icon(Icons.Rounded.Link, "Attach", tint = Palette.Amber) }
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton({ s.save(edited()); s.pushEdits(edited()) }) { Text("Push title & notes to Classroom (teachers)", color = Palette.Bark.copy(.7f)) }
                }
            }
        }
    }
}

private fun pickFile(): File? {
    val d = FileDialog(null as Frame?, "Choose a file to submit", FileDialog.LOAD)
    d.isVisible = true
    return d.file?.let { File(d.directory, it) }
}
