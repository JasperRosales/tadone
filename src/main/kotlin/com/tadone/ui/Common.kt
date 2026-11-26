package com.tadone.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tadone.data.ChatMsg
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pill button with clay depth + squish. */
@Composable
fun ClayButton(text: String, icon: ImageVector? = null, color: Color = Palette.Amber, fg: Color = Color.White, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.alpha(if (enabled) 1f else .5f).clay(color, 50.dp, if (enabled) 6.dp else 1.dp)
            .pressable { if (enabled) onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Clay text input. */
@Composable
fun ClayField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    singleLine: Boolean = true, secret: Boolean = false, minLines: Int = 1, onDone: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(modifier.clay(Palette.Paper, 18.dp, 4.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        leading?.let { it(); Spacer(Modifier.width(10.dp)) }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Palette.Bark.copy(.45f), style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value, onChange,
                singleLine = singleLine, minLines = minLines,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Ink),
                cursorBrush = SolidColor(Palette.Amber),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                    if (onDone != null && it.type == KeyEventType.KeyDown && it.key == Key.Enter && !it.isShiftPressed) { onDone(); true } else false
                },
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Olive, modifier = Modifier.padding(bottom = 6.dp))

@Composable
fun Chip(text: String, color: Color, fg: Color = Color.White) =
    Text(text, color = fg, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp))

fun dueLabel(due: Long?): Pair<String, Color>? {
    due ?: return null
    val now = Instant.now(); val at = Instant.ofEpochMilli(due)
    val d = Duration.between(now, at)
    val fmt = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())
    return when {
        d.isNegative -> "Overdue · ${fmt.format(at)}" to Color(0xFFB3401E)
        d.toHours() < 24 -> "Due in ${d.toHours()}h ${d.toMinutesPart()}m" to Palette.Amber
        d.toDays() < 4 -> "Due in ${d.toDays()}d · ${fmt.format(at)}" to Palette.Amber.darken(.2f)
        else -> "Due ${fmt.format(at)}" to Palette.Olive
    }
}

/** Chat thread + composer, used both inline on a task and on the Assistant screen. */
@Composable
fun ChatPanel(
    messages: List<ChatMsg>, thinking: Boolean, onSend: (String) -> Unit,
    suggestions: List<String>, modifier: Modifier = Modifier, empty: String,
) {
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(messages.size, thinking) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1 + (if (thinking) 1 else 0)) }
    Column(modifier) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (messages.isEmpty() && !thinking) item {
                Text(empty, color = Palette.Bark.copy(.6f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            }
            items(messages, key = { it.id }) { m -> Bubble(m) }
            if (thinking) item { Box(Modifier.clay(Palette.Paper, 18.dp, 3.dp).padding(14.dp)) { ThinkingDots() } }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.forEach { s ->
                Text(s, style = MaterialTheme.typography.labelSmall, color = Palette.Bark,
                    modifier = Modifier.clay(Palette.Cream, 50.dp, 3.dp).pressable { onSend(s) }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClayField(input, { input = it }, "Ask Tadone anything…", Modifier.weight(1f), onDone = { onSend(input); input = "" })
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(46.dp).clay(Palette.Amber, 50.dp, 6.dp).pressable { onSend(input); input = "" }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    val mine = m.role == "user"
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { it / 3 } + scaleIn(initialScale = .92f)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
            SelectionWrap {
                Text(
                    m.content,
                    color = if (mine) Color.White else Palette.Ink,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.widthIn(max = 520.dp).clay(if (mine) Palette.Amber else Palette.Paper, 18.dp, 3.dp).padding(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectionWrap(content: @Composable () -> Unit) = androidx.compose.foundation.text.selection.SelectionContainer { content() }
