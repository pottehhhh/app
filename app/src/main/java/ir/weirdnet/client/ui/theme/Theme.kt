package ir.weirdnet.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette (mirrors res/values/colors.xml so Compose and XML never drift)
val BrandBlack = Color(0xFF0A0A0C)
val BrandSurface = Color(0xFF121216)
val BrandSurfaceAlt = Color(0xFF1A1B20)
val BrandIceBlue = Color(0xFF5FD0F0)
val BrandIceBlueDim = Color(0xFF2E8FB0)
val BrandSteel = Color(0xFFC7CCD1)
val BrandSteelDim = Color(0xFF7B838C)
val BrandCrimson = Color(0xFFFF3B4E)
val BrandCrimsonDim = Color(0xFFB0202F)
val BrandSuccess = Color(0xFF3DDC97)
val BrandWarning = Color(0xFFFFB454)

private val WeirdNetDarkColors = darkColorScheme(
    primary = BrandIceBlue,
    onPrimary = BrandBlack,
    secondary = BrandCrimson,
    onSecondary = Color.White,
    tertiary = BrandSteel,
    background = BrandBlack,
    onBackground = BrandSteel,
    surface = BrandSurface,
    onSurface = BrandSteel,
    surfaceVariant = BrandSurfaceAlt,
    onSurfaceVariant = BrandSteelDim,
    error = BrandCrimson,
    outline = BrandSteelDim
)

// WEIRDNET is dark-first by design (requirement #17), but a light variant is
// provided so the app still honors the system light-mode toggle rather than
// forcing dark mode on users who've asked for light everywhere.
private val WeirdNetLightColors = lightColorScheme(
    primary = BrandIceBlueDim,
    onPrimary = Color.White,
    secondary = BrandCrimsonDim,
    onSecondary = Color.White,
    tertiary = BrandSteelDim,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1B20),
    surface = Color.White,
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE7E9EC),
    onSurfaceVariant = Color(0xFF43474E),
    error = BrandCrimsonDim,
    outline = Color(0xFFB0B4BA)
)

enum class ThemePreference { SYSTEM, LIGHT, DARK }

@Composable
fun WeirdNetTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val colorScheme = if (useDark) WeirdNetDarkColors else WeirdNetLightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WeirdNetTypography,
        shapes = WeirdNetShapes,
        content = content
    )
}
