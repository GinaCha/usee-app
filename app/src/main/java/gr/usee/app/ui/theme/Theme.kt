package gr.usee.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlueLight,
    onPrimary = BrandText,
    secondary = BrandBlue,
    onSecondary = Color.White,
    tertiary = BrandBlueDark,
    background = BrandText,
    onBackground = Color.White,
    surface = BrandBlueDark,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandBlueDark,
    onSecondary = Color.White,
    tertiary = BrandBlueLight,
    background = BrandBackground,
    onBackground = BrandText,
    surface = BrandSurface,
    onSurface = BrandText,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF7A1C1C)
)

@Composable
fun UseeOfficialAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
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
        typography = Typography,
        content = content
    )
}