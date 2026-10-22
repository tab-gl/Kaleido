package com.tabgl.kaleido.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
)

@Composable
fun KaleidoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MilkOrangeDark else MilkOrangeLight,
        typography = Typography(),
        content = content,
    )
}
