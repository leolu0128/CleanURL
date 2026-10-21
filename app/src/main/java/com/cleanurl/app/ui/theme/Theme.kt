package com.cleanurl.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Monochrome fallback palette taken from the launcher icon (white / grey chain on near-black).
private val LightColors = lightColorScheme(
    primary = Color(0xFF1C1C1A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E3DF),
    onPrimaryContainer = Color(0xFF1C1C1A),
    secondary = Color(0xFF5E5E5A),
    secondaryContainer = Color(0xFFE8E7E2),
    onSecondaryContainer = Color(0xFF1C1C1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F1EC),
    onPrimary = Color(0xFF10100D),
    primaryContainer = Color(0xFF3A3A36),
    onPrimaryContainer = Color(0xFFF2F1EC),
    secondary = Color(0xFFA3A39E),
    secondaryContainer = Color(0xFF2E2E2B),
    onSecondaryContainer = Color(0xFFE4E3DF),
    background = Color(0xFF10100D),
    surface = Color(0xFF10100D),
)

@Composable
fun CleanUrlTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
