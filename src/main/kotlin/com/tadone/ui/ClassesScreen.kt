package com.tadone.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tadone.AppState
import com.tadone.Screen
import com.tadone.data.Attachment
import com.tadone.data.Course
import com.tadone.data.CourseItem
import com.tadone.data.Db
import com.tadone.data.Keys

private fun open(url: String?) { if (!url.isNullOrBlank()) runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) } }

fun agoLabel(t: Long?): String {
    t ?: return "never"
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "just now"; m < 60 -> "${m}m ago"; m < 60 * 24 -> "${m / 60}h ago"; else -> "${m / (60 * 24)}d ago"
    }
}

@Composable
fun ClassesScreen(s: AppState) {
    // Lazy: only asks Composio for the course list if nothing is cached yet.
    LaunchedEffect(Unit) { s.loadCourses() }
    val selected = s.courses.firstOrNull { it.id == s.selectedCourseId }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(330.dp).fillMaxHeight().padding(start = 28.dp, top = 28.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Classes", style = MaterialTheme.typography.displaySmall, color = Palette.Bark, modifier = Modifier.weight(1f))
                if (s.loadingCourses) ThinkingDots(Palette.Amber)
                else IconButton({ s.loadCourses(force = true) }) { Icon(Icons.Rounded.Refresh, "Refresh class list", tint = Palette.Bark) }
            }
            Text("Saved on this computer. Refresh only when something changed.", color = Palette.Olive, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(14.dp))
            when {
                Db.get(Keys.COMPOSIO_KEY).isBlank() -> Text("Sign in and connect Google Classroom in Apps to see your classes.", color = Palette.Bark.copy(.7f))
                s.courses.isEmpty() && !s.loadingCourses -> Column {
                    Text("No classes loaded yet.", color = Palette.Bark.copy(.7f))
                    Spacer(Modifier.height(8.dp))
                    ClayButton("Load my classes", Icons.Rounded.School, Palette.Olive) { s.loadCourses(force = true) }
                    Spacer(Modifier.height(6.dp))
                    TextButton({ s.screen = Screen.Apps }) { Text("Connect Google Classroom", color = Palette.Amber) }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 30.dp)) {
                items(s.courses, key = { it.id }) { c -> CourseRow(c, c.id == s.selectedCourseId, c.id in s.loadingModules) { s.openCourse(c.id) } }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
            if (selected == null) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.School, null, tint = Palette.Olive, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(8.dp))
                Text("Pick a class to see its modules", color = Palette.Bark, style = MaterialTheme.typography.titleMedium)
                Text("Modules are fetched the first time you open a class, then kept offline.", color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelSmall)
            } else CourseModules(s, selected, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CourseRow(c: Course, selected: Boolean, loading: Boolean, onClick: () -> Unit) {
    val fg = if (selected) Color.White else Palette.Bark
    Column(Modifier.fillMaxWidth().clay(if (selected) Palette.Olive else Palette.Paper, 20.dp, if (selected) 10.dp else 5.dp).pressable(onClick).padding(14.dp)) {
        Text(c.name, color = fg, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val sub = listOf(c.section, c.room).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, color = fg.copy(.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
            when { loading -> "Loading modules…"; c.modulesFetchedAt == null -> "Not opened yet"; else -> "Modules saved ${agoLabel(c.modulesFetchedAt)}" },
            color = fg.copy(.65f), style = MaterialTheme.typography.labelSmall,
        )
    }
}

private data class Module(val id: String?, val name: String, val items: List<CourseItem>)

@Composable
private fun CourseModules(s: AppState, c: Course, modifier: Modifier) {
    val loading = c.id in s.loadingModules
    val modules = remember(s.courseTopics, s.courseItems) {
        val byTopic = s.courseItems.groupBy { it.topicId }
        s.courseTopics.map { Module(it.id, it.name, byTopic[it.id].orEmpty()) } +
            listOfNotNull(byTopic[null]?.let { Module(null, "Not in a module", it) }) +
            // items pointing at a topic we didn't get back
            byTopic.filterKeys { k -> k != null && s.courseTopics.none { it.id == k } }.values.flatten()
                .takeIf { it.isNotEmpty() }?.let { listOf(Module("?", "Other", it)) }.orEmpty()
    }
    var collapsed by remember(c.id) { mutableStateOf(setOf<String?>()) }
    var expandedItem by remember(c.id) { mutableStateOf<String?>(null) }

    Column(modifier.clay(Palette.Paper, 30.dp, 18.dp).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.headlineSmall, color = Palette.Bark, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (loading) "Fetching modules from Classroom…"
                    else "${modules.count { it.id != null && it.id != "?" }} modules · ${s.courseItems.size} items · updated ${agoLabel(c.modulesFetchedAt)}",
                    color = Palette.Olive, style = MaterialTheme.typography.labelMedium,
                )
            }
            if (c.link != null) IconButton({ open(c.link) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open in Classroom", tint = Palette.Bark) }
            ClayButton(if (loading) "Refreshing…" else "Refresh", Icons.Rounded.Sync, Palette.Cream, Palette.Bark, enabled = !loading) { s.openCourse(c.id, force = true) }
        }
        Spacer(Modifier.height(14.dp))
        if (loading && s.courseItems.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            ThinkingDots(Palette.Amber); Spacer(Modifier.width(10.dp)); Text("Reading topics, classwork and materials…", color = Palette.Bark)
        } else if (modules.isEmpty()) Text("This class has no classwork yet.", color = Palette.Bark.copy(.6f))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            modules.forEach { m ->
                val open = m.id !in collapsed
                item(key = "m-${m.id}") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).pressable { collapsed = if (open) collapsed + m.id else collapsed - m.id }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (open) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight, null, tint = Palette.Amber)
                        Spacer(Modifier.width(6.dp))
                        Text(m.name, color = Palette.Bark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${m.items.size}", color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (open) items(m.items, key = { "i-${it.id}" }) { i ->
                    ItemCard(s, i, expandedItem == i.id) { expandedItem = if (expandedItem == i.id) null else i.id }
                }
            }
        }
    }
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "material" -> Icons.Rounded.Book
    "question" -> Icons.Rounded.HelpOutline
    else -> Icons.AutoMirrored.Rounded.Assignment
}

private fun attachmentIcon(a: Attachment): ImageVector = when (a.kind) {
    "video" -> Icons.Rounded.PlayCircle; "form" -> Icons.Rounded.Checklist; "drive" -> Icons.Rounded.Description; else -> Icons.Rounded.Link
}

@Composable
private fun ItemCard(s: AppState, i: CourseItem, expanded: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp).clay(Palette.Cream.lighten(.3f), 18.dp, 3.dp).pressable(onToggle).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(if (i.kind == "material") Palette.Olive else Palette.Amber, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(kindIcon(i.kind), null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(i.title, color = Palette.Ink, fontWeight = FontWeight.SemiBold, maxLines = if (expanded) 4 else 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (i.kind) { "material" -> "Material"; "question" -> "Question"; else -> "Assignment" },
                        color = Palette.Olive, style = MaterialTheme.typography.labelSmall)
                    dueLabel(i.dueAt)?.let { (txt, col) -> Text("  ·  $txt", color = col, style = MaterialTheme.typography.labelSmall) }
                    if (i.materials.isNotEmpty()) Text("  ·  ${i.materials.size} attachment${if (i.materials.size > 1) "s" else ""}",
                        color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelSmall)
                }
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Palette.Bark.copy(.5f))
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 12.dp)) {
                if (i.description.isNotBlank()) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(i.description, color = Palette.Bark, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                i.materials.forEach { a ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).clay(Palette.Paper, 12.dp, 2.dp).pressable { open(a.url) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(attachmentIcon(a), null, tint = Palette.Amber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(a.title.ifBlank { a.url }, color = Palette.Bark, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Palette.Bark.copy(.5f), modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (i.link != null) ClayButton("Open in Classroom", Icons.AutoMirrored.Rounded.OpenInNew, Palette.Olive) { open(i.link) }
                    ClayButton("Save to Notes", Icons.Rounded.EditNote, Palette.Cream, Palette.Bark) { s.itemToNote(i) }
                }
            }
        }
    }
}
