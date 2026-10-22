package net.iegy.hisn.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.StateFlow
import net.iegy.hisn.settings.Settings
import net.iegy.hisn.settings.ThemeMode

private val Forest = Color(0xFF0F3B28)
private val Emerald = Color(0xFF2EAD7B)
private val OffWhite = Color(0xFFF6F3EC)
private val Charcoal = Color(0xFF121413)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = OffWhite,
    primaryContainer = Color(0xFFCFEBDD),
    onPrimaryContainer = Color(0xFF062416),
    secondary = Color(0xFF1F8F63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEFE5),
    onSecondaryContainer = Color(0xFF0B3A27),
    tertiary = Emerald,
    background = OffWhite,
    onBackground = Color(0xFF16201B),
    surface = Color(0xFFFBF9F4),
    onSurface = Color(0xFF16201B),
    surfaceVariant = Color(0xFFE9E5DA),
    onSurfaceVariant = Color(0xFF55605A),
    surfaceContainer = Color(0xFFEFEBE0),
    surfaceContainerHigh = Color(0xFFE9E5DA),
    outline = Color(0xFF8A938E),
    outlineVariant = Color(0xFFD3D0C5),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF45D19B),
    onPrimary = Color(0xFF05241A),
    primaryContainer = Color(0xFF14513A),
    onPrimaryContainer = Color(0xFFCFEBDD),
    secondary = Emerald,
    onSecondary = Color(0xFF05241A),
    secondaryContainer = Color(0xFF1B3A2E),
    onSecondaryContainer = Color(0xFFCFEBDD),
    tertiary = Emerald,
    background = Charcoal,
    onBackground = Color(0xFFE4E7E4),
    surface = Color(0xFF1A1D1B),
    onSurface = Color(0xFFE4E7E4),
    surfaceVariant = Color(0xFF2A2F2C),
    onSurfaceVariant = Color(0xFFA7B0AA),
    surfaceContainer = Color(0xFF1F2321),
    surfaceContainerHigh = Color(0xFF262B28),
    outline = Color(0xFF6F7873),
    outlineVariant = Color(0xFF353B37),
    error = Color(0xFFFFB4AB),
)

/** Private windows get a clearly different violet-on-slate palette, so the mode is obvious at a glance. */
private val PrivateColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFBFA8FF),
    onPrimary = Color(0xFF22134D),
    primaryContainer = Color(0xFF3A2A73),
    onPrimaryContainer = Color(0xFFE6DDFF),
    secondary = Color(0xFF9D86E8),
    onSecondary = Color(0xFF22134D),
    secondaryContainer = Color(0xFF2E2552),
    onSecondaryContainer = Color(0xFFE6DDFF),
    tertiary = Color(0xFFBFA8FF),
    background = Color(0xFF110F1C),
    onBackground = Color(0xFFE8E4F5),
    surface = Color(0xFF1A1729),
    onSurface = Color(0xFFE8E4F5),
    surfaceVariant = Color(0xFF2B2640),
    onSurfaceVariant = Color(0xFFB4ADCB),
    surfaceContainer = Color(0xFF201C33),
    surfaceContainerHigh = Color(0xFF292443),
    outline = Color(0xFF7A7396),
    outlineVariant = Color(0xFF383254),
    error = Color(0xFFFFB4AB),
)

private val HisnShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun HisnTheme(
    settings: StateFlow<Settings>,
    isPrivate: Boolean,
    content: @Composable () -> Unit,
) {
    val current = settings.collectAsState().value
    val dark = when (current.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        isPrivate -> PrivateColors
        dark -> DarkColors
        else -> LightColors
    }
    val useDarkBars = isPrivate || dark

    val view = LocalView.current
    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !useDarkBars
            controller.isAppearanceLightNavigationBars = !useDarkBars
        }
    }

    MaterialTheme(colorScheme = colors, shapes = HisnShapes) {
        // Compose's default content colour is black. Without a Surface at the root, every Text that does not set its
        // own colour (shortcut names, list rows, titles...) stays black, which is unreadable on the dark private palette.
        Surface(color = colors.background, contentColor = colors.onBackground, content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
