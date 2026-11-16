package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = MintContainer,
    onPrimaryContainer = OnMintContainer,
    secondary = ForestSecondary,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = AmberGold,
    onTertiary = Color(0xFF231900),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = AlabasterBg,
    onBackground = Color(0xFF111C16),
    surface = CrispSurface,
    onSurface = Color(0xFF111C16),
    surfaceVariant = SoftMintSurfaceVariant,
    onSurfaceVariant = Color(0xFF405248),
    error = CoralEnergy,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldBright,
    onPrimary = Color(0xFF003820),
    primaryContainer = Color(0xFF005230),
    onPrimaryContainer = MintContainer,
    secondary = Color(0xFFA0D5BC),
    onSecondary = Color(0xFF093826),
    secondaryContainer = Color(0xFF1E4F3C),
    onSecondaryContainer = SageContainer,
    tertiary = Color(0xFFFFD54F),
    onTertiary = Color(0xFF332100),
    tertiaryContainer = Color(0xFF523900),
    onTertiaryContainer = AmberContainer,
    background = CarbonBg,
    onBackground = Color(0xFFE3EFE8),
    surface = CarbonSurface,
    onSurface = Color(0xFFE3EFE8),
    surfaceVariant = CarbonSurfaceVariant,
    onSurfaceVariant = Color(0xFFB3C7BC),
    error = Color(0xFFFF8A65),
    onError = Color(0xFF4A1200),
)

val VeloQuestShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun VeloQuestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VeloQuestTypography,
        shapes = VeloQuestShapes,
        content = content,
    )
}
