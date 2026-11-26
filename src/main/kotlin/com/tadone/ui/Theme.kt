package com.tadone.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// colorhunt.co/palette/ce7f20eedfc7607938533416
object Palette {
    val Amber = Color(0xFFCE7F20)
    val Cream = Color(0xFFEEDFC7)
    val Olive = Color(0xFF607938)
    val Bark = Color(0xFF533416)

    val Paper = lerp(Cream, Color.White, 0.45f)
    val CreamDeep = lerp(Cream, Bark, 0.10f)
    val Ink = lerp(Bark, Color.Black, 0.25f)
}

fun Color.lighten(f: Float) = lerp(this, Color.White, f)
fun Color.darken(f: Float) = lerp(this, Color.Black, f)

private val scheme = lightColorScheme(
    primary = Palette.Amber, onPrimary = Color.White,
    secondary = Palette.Olive, onSecondary = Color.White,
    tertiary = Palette.Bark, onTertiary = Palette.Cream,
    background = Palette.Cream, onBackground = Palette.Ink,
    surface = Palette.Paper, onSurface = Palette.Ink,
    surfaceVariant = Palette.CreamDeep, onSurfaceVariant = Palette.Bark,
    outline = Palette.Bark.copy(alpha = .25f), error = Color(0xFFB3401E),
)

private val type = Typography().run {
    val f = FontFamily.SansSerif
    copy(
        displaySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-1).sp),
        headlineSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-.5).sp),
        titleMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = f, fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp),
    )
}

@Composable
fun TadoneTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, content = content)
