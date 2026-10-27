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
    primary = SaffronLight,
    onPrimary = Color.Black,
    primaryContainer = SaffronPrimary,
    onPrimaryContainer = Color.White,
    secondary = AmberSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF422800),
    onSecondaryContainer = Color(0xFFFFDFA0),
    tertiary = GoldTertiary,
    background = DarkBackground,
    onBackground = Color(0xFFF9EDE1),
    surface = DarkSurface,
    onSurface = Color(0xFFF9EDE1),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFDCC8B8),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SaffronPrimary,
    onPrimary = Color.White,
    primaryContainer = SaffronLight,
    onPrimaryContainer = Color.White,
    secondary = AmberSecondary,
    onSecondary = Color.Black,
    secondaryContainer = CreamSurfaceVariant,
    onSecondaryContainer = Color(0xFF4E2B00),
    tertiary = GoldTertiary,
    background = CreamBackground,
    onBackground = Color(0xFF26190D),
    surface = CreamSurface,
    onSurface = Color(0xFF26190D),
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = Color(0xFF5D4837),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Set to false to retain our custom devotional theme aesthetic
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
