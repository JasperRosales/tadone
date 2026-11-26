package com.tadone.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Soft "clay" surface: layered shadow + light-from-top gradient + rim highlight. */
fun Modifier.clay(color: Color, radius: Dp = 22.dp, elevation: Dp = 14.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(elevation, shape, ambientColor = Palette.Bark, spotColor = Palette.Bark)
        .background(Brush.linearGradient(listOf(color.lighten(.14f), color, color.darken(.06f)), Offset.Zero, Offset(0f, Float.POSITIVE_INFINITY)), shape)
        .border(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(.55f), Color.White.copy(0f), Palette.Bark.copy(.10f))), shape)
}

/** Card that tilts toward the cursor in 3D and lifts on hover. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Tilt3D(modifier: Modifier = Modifier, maxDeg: Float = 7f, content: @Composable BoxScope.() -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var tgt by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableStateOf(false) }
    val spec = spring<Float>(dampingRatio = .55f, stiffness = 180f)
    val rx by animateFloatAsState(-tgt.y * maxDeg, spec)
    val ry by animateFloatAsState(tgt.x * maxDeg, spec)
    val lift by animateFloatAsState(if (hover) 1.025f else 1f, spec)
    Box(
        modifier
            .onSizeChanged { size = it }
            .onPointerEvent(PointerEventType.Enter) { hover = true }
            .onPointerEvent(PointerEventType.Exit) { hover = false; tgt = Offset.Zero }
            .onPointerEvent(PointerEventType.Move) {
                val p = it.changes.first().position
                if (size.width > 0) tgt = Offset((p.x / size.width - .5f) * 2f, (p.y / size.height - .5f) * 2f)
            }
            .graphicsLayer {
                rotationX = rx; rotationY = ry; scaleX = lift; scaleY = lift
                cameraDistance = 14f * density
            },
        content = content,
    )
}

/** Squishy press feedback for any clickable. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) .94f else 1f, spring(.4f, 600f))
    return this.graphicsLayer { scaleX = s; scaleY = s }.clickable(src, null, onClick = onClick)
}

/** Slowly drifting soft spheres behind everything. */
@Composable
fun AmbientOrbs(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing)))
    Canvas(modifier) {
        val orbs = listOf(
            Triple(Palette.Amber, .18f, 0f), Triple(Palette.Olive, .14f, 2.1f),
            Triple(Palette.Bark, .10f, 4.2f), Triple(Palette.Amber.lighten(.4f), .2f, 5.3f),
        )
        orbs.forEachIndexed { i, (c, r, ph) ->
            val a = t * 2 * PI.toFloat() + ph
            val cx = size.width * (.5f + .38f * cos(a * (1 + i * .15f)))
            val cy = size.height * (.5f + .36f * sin(a * (1.2f - i * .1f)))
            val rad = size.minDimension * r * 1.6f
            drawCircle(Brush.radialGradient(listOf(c.copy(.22f), c.copy(0f)), Offset(cx, cy), rad), rad, Offset(cx, cy))
        }
    }
}

/** 3D sphere checkbox that pops and draws its tick. */
@Composable
fun MagicCheck(checked: Boolean, onToggle: () -> Unit, dim: Dp = 30.dp) {
    val p by animateFloatAsState(if (checked) 1f else 0f, spring(.45f, 300f))
    val pop by animateFloatAsState(if (checked) 1f else .92f, spring(.3f, 500f))
    Box(Modifier.size(dim).graphicsLayer { scaleX = pop; scaleY = pop }.pressable(onToggle)) {
        Canvas(Modifier.matchParentSize()) {
            val base = lerp(Palette.Cream.darken(.05f), Palette.Olive, p.coerceIn(0f, 1f))
            drawCircle(Palette.Bark.copy(.25f), size.minDimension / 2, center + Offset(0f, 2.5f))
            drawCircle(Brush.radialGradient(listOf(base.lighten(.45f), base, base.darken(.15f)), center - Offset(size.width * .18f, size.height * .2f), size.minDimension * .7f), size.minDimension / 2)
            if (p > .01f) {
                val path = Path().apply {
                    moveTo(size.width * .28f, size.height * .52f)
                    lineTo(size.width * .44f, size.height * .68f)
                    lineTo(size.width * .74f, size.height * .36f)
                }
                val m = PathMeasure().apply { setPath(path, false) }
                val seg = Path(); m.getSegment(0f, m.length * p.coerceIn(0f, 1f), seg, true)
                drawPath(seg, Palette.Cream, style = Stroke(size.minDimension * .12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Shimmering dots while the AI is thinking. */
@Composable
fun ThinkingDots(color: Color = Palette.Amber) {
    val t by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)))
    Canvas(Modifier.size(44.dp, 14.dp)) {
        repeat(3) { i ->
            val ph = ((t - i * .18f) % 1f + 1f) % 1f
            val k = sin(ph * PI.toFloat()).coerceAtLeast(0f)
            drawCircle(color.copy(.35f + .65f * k), 4.dp.toPx() * (.7f + .4f * k), Offset(size.width * (.18f + i * .32f), size.height / 2 - 4f * k))
        }
    }
}

fun Modifier.glowRing(color: Color, alpha: Float) = drawBehind {
    if (alpha > 0f) drawCircle(color.copy(alpha * .35f), size.maxDimension * .75f)
}
