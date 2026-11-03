package id.nonton.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = CoralBright,
    onPrimary = Color(0xFF3D0701),
    primaryContainer = Color(0xFF5C1A12),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFF4B8A8),
    onSecondary = Color(0xFF2E150F),
    secondaryContainer = Color(0xFF442A24),
    onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = Mint,
    onTertiary = Color(0xFF00382A),
    background = Ink,
    onBackground = Mist,
    surface = InkSurface,
    onSurface = Mist,
    surfaceVariant = InkElevated,
    onSurfaceVariant = MistMuted,
    outline = InkOutline,
    outlineVariant = Color(0xFF262B34),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = CoralDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410000),
    secondary = Color(0xFF77574F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD4),
    onSecondaryContainer = Color(0xFF2C1510),
    tertiary = Color(0xFF006B57),
    onTertiary = Color.White,
    background = Paper,
    onBackground = Coal,
    surface = PaperSurface,
    onSurface = Coal,
    surfaceVariant = PaperElevated,
    onSurfaceVariant = Color(0xFF53433F),
    outline = PaperOutline,
    outlineVariant = Color(0xFFF0E2DE),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

@Composable
fun NontonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = NontonTypography,
        shapes = NontonShapes,
        content = content,
    )
}
