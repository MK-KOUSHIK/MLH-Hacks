package com.example.ui.theme

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

private val DarkColorScheme =
  darkColorScheme(
    primary = CyberPrimary,
    secondary = CyberSecondary,
    tertiary = CyberTertiary,
    background = DarkBg,
    surface = DarkSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CyberPrimary,
    secondary = CyberSecondary,
    tertiary = CyberTertiary,
    background = DarkBg,
    surface = DarkSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Set to false to prefer the crisp Clean light polish theme by default
  dynamicColor: Boolean = false, // Disable dynamic overrides to keep our precise branded palette
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
