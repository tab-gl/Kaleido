package com.tabgl.kaleido.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tabgl.kaleido.data.ThemeMode
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MilkOrangeLight = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = OrangeSoft,
    onPrimaryContainer = OrangeDeep,
    secondary = EspressoSoft,
    onSecondary = Color.White,
    secondaryContainer = MilkDeep,
    onSecondaryContainer = Espresso,
    tertiary = OrangeBright,
    onTertiary = OrangeDeep,
    background = Milk,
    onBackground = Espresso,
    surface = Milk,
    onSurface = Espresso,
    surfaceVariant = MilkDeep,
    onSurfaceVariant = EspressoSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = MilkSoft,
    surfaceContainer = MilkSoft,
    surfaceContainerHigh = MilkDeep,
    surfaceContainerHighest = MilkEdge,
    outline = Color(0xFF8C7360),
    outlineVariant = MilkEdge,
)

private val MilkOrangeDark = darkColorScheme(
    primary = OrangeLight,
    onPrimary = OrangeDeep,
    primaryContainer = OrangeDarkContainer,
    onPrimaryContainer = OrangeSoft,
    secondary = MilkEdge,
    onSecondary = Espresso,
    secondaryContainer = NightHigh,
    onSecondaryContainer = MilkDeep,
    tertiary = OrangeBright,
    onTertiary = OrangeDeep,
    background = NightBrown,
    onBackground = MilkSoft,
    surface = NightBrown,
    onSurface = MilkSoft,
    surfaceVariant = NightHigh,
    onSurfaceVariant = MilkEdge,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = Color(0xFF4A3322),
    outline = Color(0xFF9C8068),
    outlineVariant = NightHigh,
    surfaceDim = NightBrown,
    surfaceBright = Color(0xFF4A3322),
    surfaceContainerLowest = Color(0xFF170E07),
    surfaceTint = OrangeLight,
    inverseSurface = MilkSoft,
    inverseOnSurface = Espresso,
    inversePrimary = OrangeDeep,
)

@Composable
fun KaleidoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        // Monet: colours taken from the wallpaper. Needs Android 12 or newer.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> MilkOrangeDark
        else -> MilkOrangeLight
    }

    // Keep status and navigation bar icons readable for the theme that is actually shown.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        typography = Typography(),
        content = content,
    )
}
