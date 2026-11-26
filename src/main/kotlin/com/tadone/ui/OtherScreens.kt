package com.tadone.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tadone.AppState
import com.tadone.ai.DEFAULT_MODEL
import com.tadone.data.Db
import com.tadone.data.Keys

@Composable
fun AssistantScreen(s: AppState) {
    LaunchedEffect(Unit) { s.loadChat(null) }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ask Tadone", style = MaterialTheme.typography.displaySmall, color = Palette.Bark)
                Text("Your study sidekick knows every task on your list.", color = Palette.Olive)
            }
            ClayButton("New chat", Icons.Rounded.Refresh, Palette.Cream, Palette.Bark) { s.clearChat(null) }
        }
        Spacer(Modifier.height(16.dp))
        ChatPanel(
            s.chat, s.thinking, { s.ask(null, it) },
            listOf("What should I do first?", "Plan my week", "What's overdue?", "Help me focus"),
            Modifier.fillMaxSize().clay(Palette.Paper.copy(alpha = .85f), 30.dp, 14.dp).padding(20.dp),
            empty = "Hi! I'm Tadone ✨  Ask me to prioritise your work, plan your week, or explain something tricky.",
        )
    }
}

@Composable
fun SettingsScreen(s: AppState) {
    var aKey by remember { mutableStateOf(Db.get(Keys.ANTHROPIC_KEY)) }
    var model by remember { mutableStateOf(Db.get(Keys.MODEL, DEFAULT_MODEL)) }
    var cKey by remember { mutableStateOf(Db.get(Keys.COMPOSIO_KEY)) }
    var cUser by remember { mutableStateOf(Db.get(Keys.COMPOSIO_USER, "default")) }
    var classAuth by remember { mutableStateOf(Db.get(Keys.CLASSROOM_AUTH_CONFIG)) }
    var driveAuth by remember { mutableStateOf(Db.get(Keys.DRIVE_AUTH_CONFIG)) }
    var connected by remember { mutableStateOf<Set<String>?>(null) }

    fun persist() {
        Db.set(Keys.ANTHROPIC_KEY, aKey.trim()); Db.set(Keys.MODEL, model.trim().ifBlank { DEFAULT_MODEL })
        Db.set(Keys.COMPOSIO_KEY, cKey.trim()); Db.set(Keys.COMPOSIO_USER, cUser.trim().ifBlank { "default" })
        Db.set(Keys.CLASSROOM_AUTH_CONFIG, classAuth.trim()); Db.set(Keys.DRIVE_AUTH_CONFIG, driveAuth.trim())
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).widthIn(max = 760.dp)) {
        Text("Settings", style = MaterialTheme.typography.displaySmall, color = Palette.Bark)
        Text("Everything stays on this computer in a local SQLite database.", color = Palette.Olive)
        Spacer(Modifier.height(22.dp))

        Card("AI (Claude)", Icons.Rounded.AutoAwesome) {
            SectionLabel("Anthropic API key"); ClayField(aKey, { aKey = it }, "sk-ant-…", Modifier.fillMaxWidth(), secret = true)
            Spacer(Modifier.height(10.dp))
            SectionLabel("Model"); ClayField(model, { model = it }, DEFAULT_MODEL, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(18.dp))
        Card("Google Classroom via Composio", Icons.Rounded.School) {
            SectionLabel("Composio API key"); ClayField(cKey, { cKey = it }, "From dashboard.composio.dev → Settings", Modifier.fillMaxWidth(), secret = true)
            Spacer(Modifier.height(10.dp))
            SectionLabel("Composio user id"); ClayField(cUser, { cUser = it }, "default", Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            SectionLabel("Classroom auth config id"); ClayField(classAuth, { classAuth = it }, "ac_…", Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            SectionLabel("Google Drive auth config id (for uploads)"); ClayField(driveAuth, { driveAuth = it }, "ac_…", Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClayButton("Connect Classroom", Icons.Rounded.Link, Palette.Olive, enabled = classAuth.isNotBlank()) { persist(); s.connect(classAuth.trim()) }
                ClayButton("Connect Drive", Icons.Rounded.Link, Palette.Olive, enabled = driveAuth.isNotBlank()) { persist(); s.connect(driveAuth.trim()) }
                ClayButton("Check", Icons.Rounded.Sync, Palette.Cream, Palette.Bark) { persist(); s.checkConnections { connected = it } }
            }
            connected?.let {
                Spacer(Modifier.height(10.dp))
                Text(if (it.isEmpty()) "No active connections for this user yet." else "Connected: ${it.joinToString()}", color = Palette.Bark)
            }
        }
        Spacer(Modifier.height(22.dp))
        ClayButton("Save settings", Icons.Rounded.Check) { persist(); s.toast = "Settings saved" }
        Spacer(Modifier.height(14.dp))
        Text("Data folder: ${Db.dir.absolutePath}", color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Card(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Tilt3D(maxDeg = 2.5f) {
        Column(Modifier.fillMaxWidth().clay(Palette.Paper, 26.dp, 12.dp).padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Palette.Amber); Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Bark)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
