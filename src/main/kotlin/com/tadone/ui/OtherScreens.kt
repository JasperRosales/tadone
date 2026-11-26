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
import com.tadone.Screen
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.ui.text.font.FontWeight
import com.tadone.ai.DEFAULT_MODEL
import com.tadone.data.Db
import com.tadone.data.Keys

@Composable
fun SettingsScreen(s: AppState) {
    var aKey by remember { mutableStateOf(Db.get(Keys.GEMINI_KEY)) }
    var model by remember { mutableStateOf(Db.get(Keys.MODEL, DEFAULT_MODEL)) }
    var cKey by remember { mutableStateOf(Db.get(Keys.COMPOSIO_KEY)) }
    var cUser by remember { mutableStateOf(Db.get(Keys.COMPOSIO_USER, "default")) }
    var advanced by remember { mutableStateOf(false) }

    fun persist() {
        Db.set(Keys.GEMINI_KEY, aKey.trim()); Db.set(Keys.MODEL, model.trim().ifBlank { DEFAULT_MODEL })
        Db.set(Keys.COMPOSIO_KEY, cKey.trim()); Db.set(Keys.COMPOSIO_USER, cUser.trim().ifBlank { "default" })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).widthIn(max = 760.dp)) {
        Text("Settings", style = MaterialTheme.typography.displaySmall, color = Palette.Bark)
        Text("Everything stays on this computer in a local SQLite database.", color = Palette.Olive)
        Spacer(Modifier.height(22.dp))

        Card("Account", Icons.Rounded.AccountCircle) {
            if (cKey.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.account.ifBlank { "Signed in with an API key" }, color = Palette.Bark, style = MaterialTheme.typography.titleSmall)
                        Text("Connected apps: ${s.connections?.size ?: "…"}", color = Palette.Olive, style = MaterialTheme.typography.labelSmall)
                    }
                    ClayButton("Manage apps", Icons.Rounded.Apps, Palette.Olive) { s.screen = Screen.Apps }
                    Spacer(Modifier.width(8.dp))
                    ClayButton("Sign out", Icons.AutoMirrored.Rounded.Logout, Palette.Cream, Palette.Bark) { s.signOut() }
                }
            } else {
                Text("You're using Tadone offline. Sign in to sync Classroom and connect apps.", color = Palette.Bark)
                Spacer(Modifier.height(10.dp))
                ClayButton("Sign in", Icons.AutoMirrored.Rounded.Login) { s.signOut() }
            }
        }
        Spacer(Modifier.height(18.dp))
        Card("AI (Gemini)", Icons.Rounded.AutoAwesome) {
            SectionLabel("Gemini API key")
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClayField(aKey, { aKey = it }, "From aistudio.google.com/apikey", Modifier.weight(1f), secret = true)
                Spacer(Modifier.width(10.dp))
                if (!s.gettingGeminiKey) ClayButton(if (aKey.isBlank()) "Get a key from Gemini" else "Get a new key", Icons.Rounded.Key) {
                    s.getGeminiKey { k, m -> aKey = k; model = m }
                }
            }
            if (s.gettingGeminiKey) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clay(Palette.Cream, 18.dp, 3.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ThinkingDots(Palette.Amber); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("In AI Studio, click “Create API key”, then Copy.", color = Palette.Bark, fontWeight = FontWeight.SemiBold)
                        Text("Tadone grabs the key from your clipboard automatically.", color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Cancel", color = Palette.Amber, fontWeight = FontWeight.Bold, modifier = Modifier.pressable { s.cancelGeminiKey() }.padding(6.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            SectionLabel("Model"); ClayField(model, { model = it }, DEFAULT_MODEL, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(18.dp))
        Card("Notifications", Icons.Rounded.Notifications) {
            var deadlines by remember { mutableStateOf(Db.get(Keys.NOTIFY_DEADLINES, "1") == "1") }
            var classes by remember { mutableStateOf(Db.get(Keys.NOTIFY_CLASSES, "1") == "1") }
            var jobsDone by remember { mutableStateOf(Db.get(Keys.NOTIFY_JOBS, "1") == "1") }
            var sound by remember { mutableStateOf(Db.get(Keys.NOTIFY_SOUND, "1") == "1") }
            @Composable fun toggle(label: String, hint: String, on: Boolean, key: String, set: (Boolean) -> Unit) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, color = Palette.Bark, fontWeight = FontWeight.SemiBold)
                        Text(hint, color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(on, { set(it); Db.set(key, if (it) "1" else "0") },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Olive, uncheckedTrackColor = Palette.CreamDeep))
                }
            }
            toggle("Deadlines", "24 hours and 1 hour before a task is due", deadlines, Keys.NOTIFY_DEADLINES) { deadlines = it }
            toggle("Classes", "10 minutes before a class on your Schedule", classes, Keys.NOTIFY_CLASSES) { classes = it }
            toggle("Background work", "When a sync, import or quiz finishes while Tadone is in the background", jobsDone, Keys.NOTIFY_JOBS) { jobsDone = it }
            toggle("Sound", "A different sound for deadlines, classes, finished work and problems", sound, Keys.NOTIFY_SOUND) { sound = it }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClayButton("Send a test notification", Icons.Rounded.NotificationsActive, Palette.Olive) { s.testNotification() }
                Spacer(Modifier.width(12.dp))
                Text("Preview sounds:", color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelSmall)
                listOf("Deadline" to "deadline", "Class" to "class", "Done" to "activity", "Problem" to "error").forEach { (label, kind) ->
                    TextButton({ Thread { com.tadone.Sounds.play(kind, force = true) }.start() }) { Text(label, color = Palette.Amber) }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(if (advanced) "▾ Advanced" else "▸ Advanced", color = Palette.Olive, fontWeight = FontWeight.Bold,
            modifier = Modifier.pressable { advanced = !advanced }.padding(vertical = 6.dp))
        if (advanced) Card("Composio (manual)", Icons.Rounded.Key) {
            SectionLabel("Composio API key"); ClayField(cKey, { cKey = it }, "Filled in by sign-in, or a project key from dashboard.composio.dev", Modifier.fillMaxWidth(), secret = true)
            Spacer(Modifier.height(10.dp))
            SectionLabel("Composio user id"); ClayField(cUser, { cUser = it }, "default", Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(22.dp))
        ClayButton("Save settings", Icons.Rounded.Check) { persist(); s.toast = "Settings saved"; s.refreshConnections() }
        Spacer(Modifier.height(14.dp))
        Text("Data folder: ${Db.dir.absolutePath}", color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun Card(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
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
