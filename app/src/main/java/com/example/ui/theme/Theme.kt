package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
  primary = CobaltPrimary,
  onPrimary = Color.White,
  primaryContainer = CobaltContainer,
  onPrimaryContainer = OnCobaltContainer,
  secondary = CoralSecondary,
  onSecondary = Color.White,
  secondaryContainer = CoralContainer,
  onSecondaryContainer = OnCoralContainer,
  tertiary = MintTertiary,
  onTertiary = Color.White,
  tertiaryContainer = MintContainer,
  onTertiaryContainer = OnMintContainer,
  background = ChalkCream,
  onBackground = InkDark,
  surface = Color.White,
  onSurface = InkDark,
  surfaceVariant = ChalkSurfaceVariant,
  onSurfaceVariant = InkMuted,
  outline = ChalkOutline,
)

private val DarkColorScheme = darkColorScheme(
  primary = CobaltPrimaryDark,
  onPrimary = Color(0xFF0A144D),
  primaryContainer = Color(0xFF25358F),
  onPrimaryContainer = Color(0xFFE0E5FF),
  secondary = Color(0xFFFF8A7A),
  onSecondary = Color(0xFF4E0E06),
  secondaryContainer = Color(0xFF7D2418),
  onSecondaryContainer = Color(0xFFFFDAD4),
  tertiary = Color(0xFF5FE0C5),
  onTertiary = Color(0xFF00382C),
  tertiaryContainer = Color(0xFF0D5C4B),
  onTertiaryContainer = Color(0xFFC8F5EA),
  background = SlateDarkBg,
  onBackground = SlateTextLight,
  surface = SlateDarkSurface,
  onSurface = SlateTextLight,
  surfaceVariant = SlateDarkSurfaceVariant,
  onSurfaceVariant = Color(0xFFB4BCD6),
  outline = SlateDarkOutline,
)

private val AppShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(18.dp),
  large = RoundedCornerShape(24.dp),
)

@Composable
fun QuizClassTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    content = content,
  )
}
