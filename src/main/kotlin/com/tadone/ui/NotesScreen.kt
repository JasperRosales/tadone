package com.tadone.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tadone.AppState
import com.tadone.ai.Quiz
import com.tadone.ai.QuizQuestion
import com.tadone.data.Note
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

private val Wrong = Color(0xFFB3401E)

@Composable
fun NotesScreen(s: AppState) {
    var query by remember { mutableStateOf("") }
    val shown = s.notes.filter { query.isBlank() || it.title.contains(query, true) || it.course.contains(query, true) || it.body.contains(query, true) }
    val selected = s.notes.firstOrNull { it.id == s.selectedNoteId }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(320.dp).fillMaxHeight().padding(start = 28.dp, top = 28.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notes", style = MaterialTheme.typography.displaySmall, color = Palette.Bark, modifier = Modifier.weight(1f))
                IconButton({ pickFiles().takeIf { it.isNotEmpty() }?.let(s::importFiles) }) {
                    Icon(Icons.Rounded.UploadFile, "Import PDF, PowerPoint, Word or Markdown", tint = Palette.Bark)
                }
                ClayButton("New", Icons.Rounded.Add) { s.newNote() }
            }
            Spacer(Modifier.height(14.dp))
            ClayField(query, { query = it }, "Search notes…", Modifier.fillMaxWidth(), leading = { Icon(Icons.Rounded.Search, null, tint = Palette.Olive) })
            Spacer(Modifier.height(14.dp))
            if (s.notes.isEmpty()) Column {
                Text("No notes yet. Import a PDF, PowerPoint, Word or Markdown file and Tadone turns it into a Markdown note on this computer, with no AI needed.",
                    color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                ClayButton("Import files", Icons.Rounded.UploadFile, Palette.Olive) { pickFiles().takeIf { it.isNotEmpty() }?.let(s::importFiles) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 30.dp)) {
                items(shown, key = { it.id }) { n -> NoteRow(n, n.id == s.selectedNoteId, Modifier.animateItem()) { s.selectedNoteId = n.id; s.noteTab = 0 } }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
            if (selected == null) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.EditNote, null, tint = Palette.Olive, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(8.dp))
                Text("Pick a note or start a new one", color = Palette.Bark, style = MaterialTheme.typography.titleMedium)
            } else NoteEditor(s, selected, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun NoteRow(n: Note, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxWidth().clay(if (selected) Palette.Amber else Palette.Paper, 20.dp, if (selected) 10.dp else 5.dp)
        .pressable(onClick).padding(14.dp)) {
        val fg = if (selected) Color.White else Palette.Bark
        Text(n.title.ifBlank { "Untitled note" }, color = fg, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (n.course.isNotBlank()) Text(n.course + " · ", color = fg.copy(.8f), style = MaterialTheme.typography.labelSmall)
            Text(SimpleDateFormat("MMM d").format(Date(n.updatedAt)), color = fg.copy(.7f), style = MaterialTheme.typography.labelSmall)
            if (n.quiz.isNotBlank()) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Quiz, "Has quiz", tint = fg.copy(.8f), modifier = Modifier.size(14.dp)) }
        }
        val preview = n.body.lines().map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith(">") && !it.startsWith("- Covers:") && !Regex("^- \\d+ sections?").containsMatchIn(it) }
            .joinToString(" ") { it.removePrefix("- ").replace("**", "") }.take(140)
        if (preview.isNotBlank()) Text(preview, color = fg.copy(.75f), style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NoteEditor(s: AppState, n: Note, modifier: Modifier) {
    // re-read when a background job (file import, restructure) rewrites this note
    val rev = s.noteRevisions[n.id] ?: 0
    var title by remember(n.id, rev) { mutableStateOf(n.title) }
    var course by remember(n.id, rev) { mutableStateOf(n.course) }
    var body by remember(n.id, rev) { mutableStateOf(n.body) }
    var count by remember { mutableStateOf(5) }
    val current = n.copy(title = title, course = course, body = body)

    // autosave shortly after typing stops
    LaunchedEffect(n.id, rev, title, course, body) {
        if (title != n.title || course != n.course || body != n.body) { delay(600); s.saveNote(current) }
    }

    Column(modifier.clay(Palette.Paper, 30.dp, 18.dp).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(420.dp).clay(Palette.CreamDeep, 50.dp, 2.dp).padding(4.dp)) {
                listOf("Note", "Source", "Practice quiz").forEachIndexed { i, label ->
                    val sel = s.noteTab == i
                    val bg by animateColorAsState(if (sel) Palette.Amber else Color.Transparent)
                    Text(label, color = if (sel) Color.White else Palette.Bark, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).background(bg, RoundedCornerShape(50)).pressable { s.noteTab = i }.padding(vertical = 8.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton({ s.deleteNote(n) }) { Icon(Icons.Rounded.DeleteOutline, "Delete note", tint = Palette.Bark) }
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(s.noteTab, transitionSpec = { fadeIn(tween(220)) + slideInVertically { 20 } togetherWith fadeOut(tween(120)) }) { which ->
            if (which == 0) Column(Modifier.fillMaxSize()) {
                Row {
                    Column(Modifier.weight(2f)) { SectionLabel("Title"); ClayField(title, { title = it }, "e.g. Cell respiration", Modifier.fillMaxWidth()) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { SectionLabel("Subject"); ClayField(course, { course = it }, "e.g. Biology", Modifier.fillMaxWidth()) }
                }
                Spacer(Modifier.height(12.dp))
                NoteTools(s, current)
                Spacer(Modifier.height(10.dp))
                SectionLabel("Notes")
                ClayField(body, { body = it }, "Type or paste your notes here, or use Add from file to build them from a PDF or slides…",
                    Modifier.fillMaxWidth().weight(1f), singleLine = false, minLines = 12)
                Spacer(Modifier.height(14.dp))
                QuizBar(s, n.id, count, { count = it },
                    onQuick = { s.saveNote(current); s.quickQuiz(current, count); s.noteTab = 2 },
                    onSmart = { s.saveNote(current); s.generateQuiz(current, count); s.noteTab = 2 })
            } else if (which == 1) SourcePanel(s, n)
            else QuizPanel(s, n, count, { count = it },
                onQuick = { s.saveNote(current); s.quickQuiz(current, count) },
                onSmart = { s.saveNote(current); s.generateQuiz(current, count) })
        }
    }
}

@Composable
private fun QuizBar(s: AppState, noteId: Long, count: Int, setCount: (Int) -> Unit, onQuick: () -> Unit, onSmart: () -> Unit) {
    val busy = noteId in s.quizzing
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClayButton("Quick quiz", Icons.Rounded.Bolt, Palette.Olive, onClick = onQuick)
        Spacer(Modifier.width(8.dp))
        ClayButton(if (busy) "Writing…" else "Smart quiz (Gemini)", Icons.Rounded.AutoAwesome, Palette.Amber, enabled = !busy, onClick = onSmart)
        Spacer(Modifier.width(12.dp))
        Text("Questions", color = Palette.Bark.copy(.7f), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(8.dp))
        listOf(5, 10, 15).forEach { c ->
            val on = c == count
            Text("$c", color = if (on) Color.White else Palette.Bark, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 6.dp).clay(if (on) Palette.Amber else Palette.Cream, 50.dp, if (on) 5.dp else 2.dp)
                    .pressable { setCount(c) }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun QuizPanel(s: AppState, n: Note, count: Int, setCount: (Int) -> Unit, onQuick: () -> Unit, onSmart: () -> Unit) {
    val questions = remember(n.quiz) { Quiz.parse(n.quiz) }
    var picks by remember(n.quiz) { mutableStateOf(mapOf<Int, Int>()) }
    var checked by remember(n.quiz) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        QuizBar(s, n.id, count, setCount, onQuick, onSmart)
        Spacer(Modifier.height(6.dp))
        Text("Quick quiz is built on this computer from your headings, bullets and definitions. Smart quiz asks Gemini (one request).",
            color = Palette.Bark.copy(.55f), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
        when {
            n.id in s.quizzing && questions.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                ThinkingDots(Palette.Amber); Spacer(Modifier.width(10.dp))
                Text("Tadone is reading your notes and writing questions…", color = Palette.Bark)
            }
            questions.isEmpty() -> Text("No quiz yet. Write or import notes, then pick Quick quiz or Smart quiz.", color = Palette.Bark.copy(.6f))
            else -> {
                if (checked) {
                    val score = questions.indices.count { picks[it] == questions[it].answer }
                    Row(Modifier.fillMaxWidth().clay(if (score * 2 >= questions.size) Palette.Olive else Palette.Amber, 18.dp, 6.dp).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Score: $score / ${questions.size}", color = Color.White, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        ClayButton("Retake", Icons.Rounded.Replay, Palette.Cream, Palette.Bark) { picks = emptyMap(); checked = false }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    questions.forEachIndexed { qi, q -> QuestionCard(qi, q, picks[qi], checked) { if (!checked) picks = picks + (qi to it) } }
                    Spacer(Modifier.height(8.dp))
                    if (!checked) ClayButton("Check answers (${picks.size}/${questions.size})", Icons.Rounded.Check,
                        enabled = picks.size == questions.size) { checked = true }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(index: Int, q: QuizQuestion, pick: Int?, checked: Boolean, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).clay(Palette.Cream.lighten(.3f), 20.dp, 4.dp).padding(16.dp)) {
        Text("${index + 1}. ${q.question}", color = Palette.Ink, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        q.choices.forEachIndexed { ci, choice ->
            val isPick = pick == ci
            val isAnswer = ci == q.answer
            val (bg, fg, edge) = when {
                checked && isAnswer -> Triple(Palette.Olive, Color.White, Palette.Olive)
                checked && isPick -> Triple(Wrong, Color.White, Wrong)
                isPick -> Triple(Palette.Amber, Color.White, Palette.Amber)
                else -> Triple(Palette.Paper, Palette.Bark, Palette.CreamDeep)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).background(bg, RoundedCornerShape(14.dp))
                .border(1.dp, edge, RoundedCornerShape(14.dp)).pressable { onPick(ci) }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${'A' + ci}", color = fg, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                Text(choice, color = fg, modifier = Modifier.weight(1f))
                if (checked && isAnswer) Icon(Icons.Rounded.Check, "Correct", tint = Color.White, modifier = Modifier.size(18.dp))
                if (checked && isPick && !isAnswer) Icon(Icons.Rounded.Close, "Your answer", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        if (checked && q.explanation.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(q.explanation, color = Palette.Bark.copy(.8f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Multi-select file dialog for study files. */
private fun pickFiles(): List<java.io.File> {
    val d = java.awt.FileDialog(null as java.awt.Frame?, "Import PDF, PowerPoint, Word or Markdown", java.awt.FileDialog.LOAD)
    d.isMultipleMode = true
    d.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in com.tadone.data.MarkdownImport.extensions }
    d.isVisible = true
    return d.files.orEmpty().toList()
}

/** Per-note actions: pull text from files, rebuild the structure locally, or let Gemini organise it. */
@Composable
private fun NoteTools(s: AppState, n: Note) {
    val reading = s.running("note-files:${n.id}")
    val aiBusy = s.running("ai-structure:${n.id}")
    Row(Modifier.fillMaxWidth().clay(Palette.Cream.lighten(.35f), 18.dp, 3.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ClayButton(if (reading) "Reading…" else "Add from file", Icons.Rounded.UploadFile, Palette.Olive, enabled = !reading) {
            pickFiles().takeIf { it.isNotEmpty() }?.let { s.saveNote(n); s.addFilesToNote(n, it) }
        }
        Spacer(Modifier.width(8.dp))
        ClayButton("Restructure", Icons.Rounded.AccountTree, Palette.Cream, Palette.Bark, enabled = !reading && !aiBusy) { s.saveNote(n); s.restructureNote(n) }
        Spacer(Modifier.width(8.dp))
        ClayButton(if (aiBusy) "Organising…" else "Structure with Gemini", Icons.Rounded.AutoAwesome, Palette.Cream, Palette.Bark, enabled = !reading && !aiBusy) {
            s.saveNote(n); s.structureWithAi(n)
        }
        Spacer(Modifier.weight(1f))
        val files = n.sourceFiles.lines().filter { it.isNotBlank() }
        Text(if (files.isEmpty()) "PDF, PowerPoint, Word or Markdown" else "From ${files.size} file${if (files.size > 1) "s" else ""}",
            color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelSmall)
    }
}

/** Read-only view of all text extracted from the note's files. */
@Composable
private fun SourcePanel(s: AppState, n: Note) {
    Column(Modifier.fillMaxSize()) {
        val files = n.sourceFiles.lines().filter { it.isNotBlank() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (files.isEmpty()) "No files yet" else files.joinToString(", "), color = Palette.Bark, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Everything extracted from your files, kept so the note can be restructured without reading the files again.",
                    color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelSmall)
            }
            ClayButton("Add from file", Icons.Rounded.UploadFile, Palette.Olive, enabled = !s.running("note-files:${n.id}")) {
                pickFiles().takeIf { it.isNotEmpty() }?.let { s.addFilesToNote(n, it) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxSize().clay(Palette.Cream.lighten(.4f), 18.dp, 3.dp).padding(16.dp)) {
            if (n.source.isBlank()) Text("Use Add from file to pull all the text out of a PDF, PowerPoint or Word file.", color = Palette.Bark.copy(.6f))
            else androidx.compose.foundation.text.selection.SelectionContainer {
                Text(n.source, color = Palette.Ink, style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.verticalScroll(rememberScrollState()))
            }
        }
    }
}
