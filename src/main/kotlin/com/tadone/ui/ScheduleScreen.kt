package com.tadone.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tadone.AppState
import com.tadone.data.ScheduleEvent
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

val EventColors = listOf(Palette.Amber, Palette.Olive, Color(0xFF3F6E8C), Color(0xFF8C3F5E), Color(0xFF6B4FA0), Palette.Bark)
private const val FIRST_HOUR = 6
private const val LAST_HOUR = 22
private val HOUR_H = 56.dp
private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun minutesLabel(m: Int): String = LocalTime.of((m / 60).coerceIn(0, 23), m % 60).format(DateTimeFormatter.ofPattern("h:mm a"))

/** Parses "9", "9:30", "14:00", "2pm", "2:15 PM" into minutes after midnight. */
fun parseTime(text: String): Int? {
    val t = text.trim().lowercase().replace(" ", "")
    val m = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm)?$").find(t) ?: return null
    var h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    when (m.groupValues[3]) { "pm" -> if (h < 12) h += 12; "am" -> if (h == 12) h = 0 }
    return if (h in 0..23 && min in 0..59) h * 60 + min else null
}

@Composable
fun ScheduleScreen(s: AppState) {
    var weekStart by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
    var editing by remember { mutableStateOf<ScheduleEvent?>(null) }
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val zone = ZoneId.systemDefault()

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Schedule", style = MaterialTheme.typography.displaySmall, color = Palette.Bark)
                    val fmt = DateTimeFormatter.ofPattern("MMM d")
                    Text("${weekStart.format(fmt)} to ${weekStart.plusDays(6).format(fmt)}, ${weekStart.year}", color = Palette.Olive)
                }
                IconButton({ weekStart = weekStart.minusWeeks(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous week", tint = Palette.Bark) }
                ClayButton("This week", null, Palette.Cream, Palette.Bark) { weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
                IconButton({ weekStart = weekStart.plusWeeks(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next week", tint = Palette.Bark) }
                Spacer(Modifier.width(10.dp))
                ClayButton("Add class", Icons.Rounded.Add) {
                    val today = LocalDate.now().dayOfWeek.value - 1
                    editing = ScheduleEvent(title = "", days = 1 shl today)
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxSize().clay(Palette.Paper.copy(alpha = .9f), 26.dp, 10.dp).padding(14.dp)) {
                // day headers + due tasks
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(56.dp))
                    days.forEach { d ->
                        val today = d == LocalDate.now()
                        val due = s.tasks.filter { t ->
                            !t.done && t.dueAt != null && Instant.ofEpochMilli(t.dueAt).atZone(zone).toLocalDate() == d
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(dayNames[d.dayOfWeek.value - 1], color = if (today) Palette.Amber else Palette.Olive,
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Text("${d.dayOfMonth}", color = if (today) Color.White else Palette.Bark, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.background(if (today) Palette.Amber else Color.Transparent, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            due.take(3).forEach { t ->
                                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.Bark,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(bottom = 3.dp).fillMaxWidth()
                                        .border(1.dp, Palette.Amber, RoundedCornerShape(8.dp)).pressable {
                                            s.selectedId = t.id; s.screen = com.tadone.Screen.Tasks
                                        }.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            if (due.size > 3) Text("+${due.size - 3} more due", color = Palette.Amber, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Palette.CreamDeep)
                // timeline
                Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState(with(LocalDensity.current) { (HOUR_H * (8 - FIRST_HOUR) - 8.dp).roundToPx() }))) {
                    Column(Modifier.width(56.dp)) {
                        (FIRST_HOUR until LAST_HOUR).forEach { h ->
                            Text(minutesLabel(h * 60).replace(":00", ""), color = Palette.Bark.copy(.55f),
                                style = MaterialTheme.typography.labelSmall, modifier = Modifier.height(HOUR_H).padding(top = 2.dp))
                        }
                    }
                    days.forEach { d ->
                        Box(Modifier.weight(1f).height(HOUR_H * (LAST_HOUR - FIRST_HOUR)).padding(horizontal = 3.dp)
                            .background(if (d == LocalDate.now()) Palette.Amber.copy(.06f) else Color.Transparent, RoundedCornerShape(10.dp))) {
                            (FIRST_HOUR until LAST_HOUR).forEach { h ->
                                HorizontalDivider(Modifier.offset(y = HOUR_H * (h - FIRST_HOUR)), color = Palette.CreamDeep.copy(.7f))
                            }
                            if (d == LocalDate.now()) {
                                val now = LocalTime.now().let { it.hour * 60 + it.minute }
                                if (now in FIRST_HOUR * 60 until LAST_HOUR * 60)
                                    Box(Modifier.offset(y = HOUR_H * ((now - FIRST_HOUR * 60) / 60f)).fillMaxWidth().height(2.dp).background(Palette.Amber))
                            }
                            s.events.filter { it.occursOn(d) }.forEach { e ->
                                val top = ((e.startMin - FIRST_HOUR * 60).coerceAtLeast(0)) / 60f
                                val len = ((e.endMin - e.startMin).coerceAtLeast(20)) / 60f
                                val c = EventColors[e.color.mod(EventColors.size)]
                                Column(Modifier.offset(y = HOUR_H * top).fillMaxWidth().height(HOUR_H * len - 2.dp)
                                    .clay(c, 10.dp, 4.dp).pressable { editing = e }.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                    Text(e.title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(minutesLabel(e.startMin) + if (e.location.isNotBlank()) " · ${e.location}" else "",
                                        color = Color.White.copy(.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(editing != null, enter = slideInHorizontally { it / 2 } + fadeIn(), exit = slideOutHorizontally { it / 2 } + fadeOut()) {
            editing?.let { e ->
                EventEditor(e, weekStart, Modifier.width(380.dp).fillMaxHeight().padding(top = 20.dp, end = 20.dp, bottom = 20.dp),
                    onSave = { s.saveEvent(it); editing = null },
                    onDelete = { s.deleteEvent(e); editing = null },
                    onClose = { editing = null })
            }
        }
    }
}

@Composable
private fun EventEditor(
    e: ScheduleEvent, weekStart: LocalDate, modifier: Modifier,
    onSave: (ScheduleEvent) -> Unit, onDelete: () -> Unit, onClose: () -> Unit,
) {
    var title by remember(e) { mutableStateOf(e.title) }
    var location by remember(e) { mutableStateOf(e.location) }
    var weekly by remember(e) { mutableStateOf(e.days != 0 || e.date == null) }
    var days by remember(e) { mutableStateOf(e.days) }
    var date by remember(e) { mutableStateOf(LocalDate.ofEpochDay(e.date ?: weekStart.toEpochDay()).toString()) }
    var start by remember(e) { mutableStateOf(minutesLabel(e.startMin)) }
    var end by remember(e) { mutableStateOf(minutesLabel(e.endMin)) }
    var color by remember(e) { mutableStateOf(e.color) }
    var error by remember(e) { mutableStateOf<String?>(null) }

    Column(modifier.clay(Palette.Paper, 30.dp, 20.dp).padding(22.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (e.id == 0L) "New class" else "Edit class", style = MaterialTheme.typography.titleLarge, color = Palette.Bark, modifier = Modifier.weight(1f))
            IconButton(onClose) { Icon(Icons.Rounded.Close, "Close", tint = Palette.Bark) }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("Title"); ClayField(title, { title = it }, "e.g. Biology lecture", Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        SectionLabel("Room or link"); ClayField(location, { location = it }, "e.g. Room 204", Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        SectionLabel("Repeats")
        Row(Modifier.clay(Palette.CreamDeep, 50.dp, 2.dp).padding(4.dp)) {
            listOf("Every week" to true, "One time" to false).forEach { (label, v) ->
                Text(label, color = if (weekly == v) Color.White else Palette.Bark, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(if (weekly == v) Palette.Amber else Color.Transparent, RoundedCornerShape(50))
                        .pressable { weekly = v }.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        if (weekly) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            dayNames.forEachIndexed { i, n ->
                val on = days and (1 shl i) != 0
                Text(n.take(2), color = if (on) Color.White else Palette.Bark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clay(if (on) Palette.Olive else Palette.Cream, 50.dp, if (on) 5.dp else 2.dp)
                        .pressable { days = days xor (1 shl i) }.padding(horizontal = 9.dp, vertical = 7.dp))
            }
        } else ClayField(date, { date = it }, "YYYY-MM-DD", Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row {
            Column(Modifier.weight(1f)) { SectionLabel("Starts"); ClayField(start, { start = it }, "9:00 AM", Modifier.fillMaxWidth()) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { SectionLabel("Ends"); ClayField(end, { end = it }, "10:30 AM", Modifier.fillMaxWidth()) }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("Color")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EventColors.forEachIndexed { i, c ->
                Box(Modifier.size(30.dp).clay(c, 50.dp, if (color == i) 6.dp else 2.dp).pressable { color = i }, contentAlignment = Alignment.Center) {
                    if (color == i) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Color(0xFFB3401E), style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClayButton("Save", Icons.Rounded.Check) {
                val sm = parseTime(start); val em = parseTime(end)
                val d = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
                error = when {
                    title.isBlank() -> "Give it a title."
                    sm == null || em == null -> "Times look like 9:00 AM or 14:30."
                    em <= sm -> "The end time must be after the start."
                    weekly && days == 0 -> "Pick at least one day."
                    !weekly && d == null -> "Dates look like 2026-12-01."
                    else -> null
                }
                if (error == null) onSave(e.copy(title = title.trim(), location = location.trim(),
                    days = if (weekly) days else 0, date = if (weekly) null else d!!.toEpochDay(),
                    startMin = sm!!, endMin = em!!, color = color))
            }
            Spacer(Modifier.weight(1f))
            if (e.id != 0L) TextButton(onDelete) { Text("Delete", color = Color(0xFFB3401E)) }
        }
    }
}
