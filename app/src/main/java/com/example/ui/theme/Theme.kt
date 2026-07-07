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
    primary = Color.White,
    secondary = NaturalDarkMutedText,
    tertiary = NaturalDarkMutedText,
    background = NaturalDarkBg,
    surface = NaturalDarkSurface,
    surfaceVariant = NaturalDarkCard,
    outline = NaturalDarkBorder,
    inverseSurface = NaturalDarkUserBubble,
    onBackground = Color(0xFFE5E5E5),
    onSurface = Color(0xFFE5E5E5),
    onPrimary = NaturalDarkBg
  )

private val LightColorScheme =
  lightColorScheme(
    primary = NaturalCharcoal,
    secondary = NaturalMutedText,
    tertiary = NaturalAccent,
    background = NaturalCream,
    surface = Color.White,
    surfaceVariant = NaturalThinkingBg,
    outline = NaturalBorder,
    inverseSurface = NaturalUserBubble,
    onBackground = NaturalCharcoal,
    onSurface = NaturalCharcoal,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onSurfaceVariant = NaturalCharcoal,
    inverseOnSurface = NaturalCharcoal
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Set default to false to ensure the "Natural Tones" theme is consistently displayed
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
