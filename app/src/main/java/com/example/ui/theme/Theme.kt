package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = NeonPink,
  secondary = NeonCyan,
  tertiary = NeonPurple,
  background = CyberBackground,
  surface = CyberSurface,
  onBackground = TextPrimary,
  onSurface = TextPrimary,
  onPrimary = Color.Black,
  onSecondary = Color.Black,
  onTertiary = Color.White,
  surfaceVariant = CyberSurfaceVariant,
  onSurfaceVariant = TextSecondary
)

// Fallback light theme, but we prioritize the dark aesthetic
private val LightColorScheme = lightColorScheme(
  primary = NeonPink,
  secondary = NeonCyan,
  tertiary = NeonPurple,
  background = Color(0xFFF9F7FF),
  surface = Color(0xFFEEEEF6),
  onBackground = CyberBackground,
  onSurface = CyberBackground,
  onPrimary = Color.White,
  onSecondary = Color.White,
  onTertiary = Color.White,
  surfaceVariant = Color(0xFFE2E0EE),
  onSurfaceVariant = Color(0xFF4C4A5E)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for the epic music visualizer experience
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
