package es.ual.notifier.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import es.ual.notifier.R

// Paleta azul inspirada en la Universidad de Almería
val UalBlue = Color(0xFF0055A5)
val UalBlueDark = Color(0xFF003B75)
val UalBlueLight = Color(0xFFD6E4FF)
val UalAccent = Color(0xFF0277BD)

/**
 * Pesos equilibrados de Catamaran:
 *  - Normal: 450 (lectura limpia, ni fina ni pesada)
 *  - Medium: 520
 *  - SemiBold: 600
 *  - Bold: 700
 *  - ExtraBold: 750
 */
@OptIn(ExperimentalTextApi::class)
val CatamaranFontFamily = FontFamily(
    Font(
        resId = R.font.catamaran,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(450))
    ),
    Font(
        resId = R.font.catamaran,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(520))
    ),
    Font(
        resId = R.font.catamaran,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        resId = R.font.catamaran,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
    Font(
        resId = R.font.catamaran,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(750))
    )
)

private val BaseTypography = Typography()

val AppTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.Bold),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.Bold),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.Bold),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.Bold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.Bold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.Bold),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.Normal),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.Normal),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.Normal),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.Medium),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = CatamaranFontFamily, fontWeight = FontWeight.Medium)
)

private val LightColors = lightColorScheme(
    primary = UalBlue,
    onPrimary = Color.White,
    primaryContainer = UalBlueLight,
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = UalAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE5FF),
    onSecondaryContainer = Color(0xFF001D34),
    tertiary = UalBlueDark,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3ECF7),
    onSurfaceVariant = Color(0xFF2B3A4E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = UalBlueDark,
    onPrimaryContainer = UalBlueLight,
    secondary = Color(0xFF8ACAFF),
    onSecondary = Color(0xFF003355),
    tertiary = Color(0xFFA8C8FF),
    background = Color(0xFF0F141C),
    surface = Color(0xFF171D26),
    surfaceVariant = Color(0xFF222C3A),
    onSurfaceVariant = Color(0xFFC5D2E3)
)

@Composable
fun UALNotifierTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
