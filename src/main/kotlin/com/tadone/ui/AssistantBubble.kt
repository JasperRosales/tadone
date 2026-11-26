package com.tadone.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tadone.AppState

/**
 * "Ask Tadone" as a floating button in the bottom-right corner that expands into a mini chat.
 * It floats over every screen, so you can ask about a task, note or class without leaving it.
 */
@Composable
fun AssistantBubble(s: AppState, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { s.loadChat(null) }
    val thinking = s.isThinking(null)

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        val panelH = (maxHeight - 96.dp).coerceIn(300.dp, 560.dp)
        Column(horizontalAlignment = Alignment.End) {
            AnimatedVisibility(
                s.assistantOpen,
                enter = scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = .6f, transformOrigin = TransformOrigin(1f, 1f)) + fadeIn(tween(160)),
                exit = scaleOut(tween(160), targetScale = .6f, transformOrigin = TransformOrigin(1f, 1f)) + fadeOut(tween(120)),
            ) {
                Column(
                    Modifier.padding(bottom = 14.dp).width(380.dp).height(panelH)
                        .clay(Palette.Paper, 26.dp, 22.dp)
                        .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { s.assistantOpen = false; true } else false }
                ) {
                    // header
                    Row(Modifier.fillMaxWidth().background(Palette.Bark, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                        .padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clay(Palette.Amber, 10.dp, 4.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.Cream, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ask Tadone", color = Palette.Cream, fontWeight = FontWeight.Bold)
                            Text(if (thinking) "Thinking…" else "Knows your tasks", color = Palette.Cream.copy(.7f),
                                style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton({ s.clearChat(null) }) { Icon(Icons.Rounded.Refresh, "New chat", tint = Palette.Cream.copy(.85f), modifier = Modifier.size(20.dp)) }
                        IconButton({ s.assistantOpen = false }) { Icon(Icons.Rounded.KeyboardArrowDown, "Minimise", tint = Palette.Cream) }
                    }
                    ChatPanel(
                        s.chatFor(null), thinking, { s.ask(null, it) },
                        listOf("What should I do first?", "Plan my week", "What's overdue?", "Help me focus"),
                        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                        empty = "Hi, I'm Tadone. Ask me to prioritise your work, plan your week, or explain something tricky.",
                    )
                }
            }
            // the floating button
            val pulse by rememberInfiniteTransition().animateFloat(1f, if (thinking) 1.08f else 1f,
                infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse))
            val lift by animateFloatAsState(if (s.assistantOpen) 0.92f else 1f, spring(.5f, 400f))
            Box(
                Modifier.size(60.dp).graphicsLayer { scaleX = pulse * lift; scaleY = pulse * lift }
                    .clay(if (s.assistantOpen) Palette.Bark else Palette.Amber, 50.dp, 14.dp)
                    .pressable { s.assistantOpen = !s.assistantOpen; if (s.assistantOpen) s.assistantUnread = false },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(s.assistantOpen, transitionSpec = { (fadeIn() + scaleIn(initialScale = .6f)) togetherWith (fadeOut() + scaleOut(targetScale = .6f)) }) { open ->
                    Icon(if (open) Icons.Rounded.Close else Icons.Rounded.AutoAwesome, if (open) "Close Ask Tadone" else "Ask Tadone",
                        tint = Palette.Cream, modifier = Modifier.size(28.dp))
                }
                if (s.assistantUnread && !s.assistantOpen)
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp).background(Color(0xFFB3401E), RoundedCornerShape(50)))
            }
        }
    }
}
