package com.tadone.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Animated launch screen: a floating 3D tile assembles, ticks itself and the name rises in. */
@Composable
fun Splash(progress: Float) {
    val intro = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, spring(.5f, 90f))
        tick.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    val float by rememberInfiniteTransition().animateFloat(-1f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    val bar by animateFloatAsState(progress, tween(400))

    Box(
        Modifier.fillMaxSize().padding(18.dp).clay(Palette.Cream, 40.dp, 22.dp).clip(RoundedCornerShape(40.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AmbientOrbs(Modifier.matchParentSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Layered 3D tile
            Box(
                Modifier.size(150.dp).graphicsLayer {
                    val k = intro.value
                    rotationX = 28f * (1 - k) + 10f + float * 4f
                    rotationY = -40f * (1 - k) - 14f + float * 6f
                    translationY = float * 6.dp.toPx()
                    scaleX = .4f + .6f * k; scaleY = .4f + .6f * k
                    alpha = k.coerceIn(0f, 1f)
                    cameraDistance = 10f * density
                },
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val r = CornerRadius(size.width * .24f)
                    val depth = 14.dp.toPx() * intro.value
                    // extrusion layers
                    for (i in 6 downTo 1) {
                        val d = depth * i / 6
                        drawRoundRect(Palette.Bark.darken(.05f * i / 6), Offset(d * .5f, d), size, r)
                    }
                    drawRoundRect(
                        Brush.linearGradient(listOf(Palette.Amber.lighten(.18f), Palette.Amber, Palette.Amber.darken(.12f)), Offset.Zero, Offset(size.width, size.height)),
                        size = size, cornerRadius = r,
                    )
                    drawRoundRect(Color.White.copy(.18f), Offset(size.width * .12f, size.height * .07f), Size(size.width * .76f, size.height * .3f), CornerRadius(size.width * .16f))
                    val path = Path().apply {
                        moveTo(size.width * .27f, size.height * .52f)
                        lineTo(size.width * .43f, size.height * .68f)
                        lineTo(size.width * .74f, size.height * .34f)
                    }
                    val pm = PathMeasure().apply { setPath(path, false) }
                    val seg = Path(); pm.getSegment(0f, pm.length * tick.value, seg, true)
                    drawPath(seg, Palette.Cream, style = Stroke(size.width * .11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Spacer(Modifier.height(34.dp))
            Row {
                "Tadone".forEachIndexed { i, ch ->
                    val a = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { delay(380L + i * 70L); a.animateTo(1f, spring(.5f, 220f)) }
                    Text(
                        ch.toString(),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp),
                        color = Palette.Bark,
                        modifier = Modifier.graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1 - a.value) * 30f; rotationX = (1 - a.value) * 70f },
                    )
                }
            }
            Text("your magic task helper", color = Palette.Olive, style = MaterialTheme.typography.labelSmall, modifier = Modifier.graphicsLayer { alpha = tick.value })
            Spacer(Modifier.height(26.dp))
            Box(Modifier.width(170.dp).height(8.dp).shadow(2.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).drawWithBar(bar))
        }
    }
}

private fun Modifier.drawWithBar(p: Float) = this.drawBehind {
    drawRect(Palette.CreamDeep)
    drawRect(Brush.horizontalGradient(listOf(Palette.Olive, Palette.Amber)), size = Size(size.width * p, size.height))
}
