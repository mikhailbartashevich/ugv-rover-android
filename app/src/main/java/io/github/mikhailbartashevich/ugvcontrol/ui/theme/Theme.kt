package io.github.mikhailbartashevich.ugvcontrol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RoverColors {
    val Accent = Color(0xFF4DD0E1)
    val Ok = Color(0xFF66BB6A)
    val Warn = Color(0xFFFFB74D)
    val Danger = Color(0xFFEF5350)
    val Glass = Color(0x99101418)
    val GlassStrong = Color(0xCC101418)
    val Camera = Color(0xFFFFD54F)
    val DialogGlass = Color(0xEE141A1F)
}

private val scheme = darkColorScheme(
    primary = RoverColors.Accent,
    onPrimary = Color(0xFF00363D),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFFFD54F),
    error = RoverColors.Danger,
    background = Color.Black,
    surface = Color(0xFF151A1E),
    surfaceVariant = Color(0xFF242B30),
)

@Composable
fun UgvTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
