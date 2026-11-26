package com.tadone

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.tadone.data.Db
import com.tadone.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

fun main() = application {
    var ready by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(.1f) }
    val icon = painterResource("tadone.png")
    // Windows / macOS notifications go through a tray icon; Linux uses notify-send (see Notifier).
    val useTray = remember { !System.getProperty("os.name").lowercase().contains("linux") && java.awt.SystemTray.isSupported() }
    if (useTray) {
        val trayState = rememberTrayState()
        Tray(icon, state = trayState, tooltip = "Tadone")
        LaunchedEffect(trayState) { Notifier.tray = { t, b -> trayState.sendNotification(Notification(t, b, Notification.Type.Info)) } }
    }

    if (!ready) {
        Window(
            onCloseRequest = ::exitApplication, title = "Tadone", icon = icon,
            undecorated = true, transparent = true, resizable = false, alwaysOnTop = true,
            state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(440.dp, 460.dp)),
        ) {
            TadoneTheme { Splash(progress) }
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) { Db.init() }; progress = .55f
                delay(900); progress = .85f
                delay(900); progress = 1f
                delay(450); ready = true
            }
        }
    } else {
        Window(
            onCloseRequest = ::exitApplication, title = "Tadone", icon = icon,
            state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(1240.dp, 800.dp)),
        ) {
            window.minimumSize = java.awt.Dimension(980, 640)
            var focused by remember { mutableStateOf(true) }
            DisposableEffect(window) {
                val l = object : java.awt.event.WindowAdapter() {
                    override fun windowGainedFocus(e: java.awt.event.WindowEvent?) { focused = true }
                    override fun windowLostFocus(e: java.awt.event.WindowEvent?) { focused = false }
                }
                window.addWindowFocusListener(l)
                onDispose { window.removeWindowFocusListener(l) }
            }
            TadoneTheme { App(focused) }
        }
    }
}

@Composable
fun App(focused: Boolean = true) {
    val scope = rememberCoroutineScope()
    val s = remember { AppState(scope).apply { reload() } }
    LaunchedEffect(focused) { s.windowFocused = focused }
    LaunchedEffect(Unit) { s.startReminders() }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(.7f, 120f)) }
    LaunchedEffect(s.signedIn) { if (s.signedIn) s.refreshConnections() }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.Cream.lighten(.25f), Palette.Cream)))) {
        AmbientOrbs(Modifier.matchParentSize())
        if (!s.signedIn) SignInScreen(s) else Row(Modifier.fillMaxSize().graphicsLayer { alpha = enter.value; scaleX = .96f + .04f * enter.value; scaleY = scaleX }) {
            Sidebar(s)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(s.screen, transitionSpec = {
                    (fadeIn(tween(260)) + slideInVertically(tween(320)) { 40 }) togetherWith fadeOut(tween(140))
                }) { scr ->
                    when (scr) {
                        Screen.Tasks -> TasksScreen(s)
                        Screen.Classes -> ClassesScreen(s)
                        Screen.Schedule -> ScheduleScreen(s)
                        Screen.Notes -> NotesScreen(s)
                        Screen.Assistant -> AssistantScreen(s)
                        Screen.Apps -> AppsScreen(s)
                        Screen.Settings -> SettingsScreen(s)
                    }
                }
            }
        }
        ActivityPills(s.jobs.values.filter { it.isNotBlank() }, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
        Toast(s, Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp))
    }
}

@Composable
private fun Sidebar(s: AppState) {
    Column(
        Modifier.padding(16.dp).width(92.dp).fillMaxHeight().clay(Palette.Bark, 30.dp, 18.dp).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val spin by rememberInfiniteTransition().animateFloat(-8f, 8f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse))
        Box(Modifier.size(52.dp).graphicsLayer { rotationY = spin * 2; rotationX = spin; cameraDistance = 8f * density }.clay(Palette.Amber, 16.dp, 10.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Palette.Cream, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        NavItem(Icons.Rounded.TaskAlt, "Tasks", s.screen == Screen.Tasks) { s.screen = Screen.Tasks }
        NavItem(Icons.Rounded.School, "Classes", s.screen == Screen.Classes) { s.screen = Screen.Classes }
        NavItem(Icons.Rounded.CalendarMonth, "Schedule", s.screen == Screen.Schedule) { s.screen = Screen.Schedule }
        NavItem(Icons.Rounded.EditNote, "Notes", s.screen == Screen.Notes) { s.screen = Screen.Notes }
        NavItem(Icons.Rounded.AutoAwesome, "Ask", s.screen == Screen.Assistant) { s.screen = Screen.Assistant }
        NavItem(Icons.Rounded.Apps, "Apps", s.screen == Screen.Apps) { s.screen = Screen.Apps }
        Spacer(Modifier.weight(1f))
        NavItem(Icons.Rounded.Settings, "Settings", s.screen == Screen.Settings) { s.screen = Screen.Settings }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Palette.Amber else Color.Transparent)
    val fg by animateColorAsState(if (selected) Color.White else Palette.Cream.copy(.7f))
    val lift by animateFloatAsState(if (selected) 1.08f else 1f, spring(.45f, 300f))
    Column(
        Modifier.padding(vertical = 6.dp).graphicsLayer { scaleX = lift; scaleY = lift }
            .background(bg, RoundedCornerShape(18.dp)).pressable(onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = fg)
        Text(label, color = fg, style = MaterialTheme.typography.labelSmall)
    }
}

/** One pill per running job (up to 3), so parallel work stays visible. */
@Composable
private fun ActivityPills(labels: List<String>, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.take(3).forEach { text ->
            key(text) {
                val shown = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(shown, enter = slideInVertically { -it } + fadeIn()) {
                    Row(Modifier.clay(Palette.Bark, 50.dp, 12.dp).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ThinkingDots(Palette.Amber); Spacer(Modifier.width(8.dp))
                        Text(text, color = Palette.Cream, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (labels.size > 3) Text("+${labels.size - 3} more running", color = Palette.Bark, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clay(Palette.Cream, 50.dp, 4.dp).padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

@Composable
private fun Toast(s: AppState, modifier: Modifier) {
    val current = s.toasts.firstOrNull()
    LaunchedEffect(current?.first) { if (current != null) { delay(if (s.toasts.size > 1) 2200 else 3200); s.toast = null } }
    AnimatedVisibility(s.toast != null, modifier, enter = slideInVertically { it } + fadeIn() + scaleIn(initialScale = .8f), exit = fadeOut() + slideOutVertically { it }) {
        Text(s.toast ?: "", color = Palette.Cream, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(max = 600.dp).clay(Palette.Olive, 50.dp, 14.dp).padding(horizontal = 20.dp, vertical = 12.dp))
    }
}
