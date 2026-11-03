package io.github.s1ddhants1.unhinge.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val DefaultThemeColor = Color(0xFFED5564)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainerHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceContainerHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF080808),
        surfaceContainer = Color(0xFF101010),
        surfaceContainerHigh = Color(0xFF181818),
        surfaceContainerHighest = Color(0xFF222222)
    ) else this

@Composable
fun UnhingeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    setSystemBars: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useSystemDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && themeColor == DefaultThemeColor

    val baseColorScheme = remember(darkTheme, useSystemDynamic, themeColor) {
        if (useSystemDynamic) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) {
                if (themeColor == DefaultThemeColor) {
                    DarkColorScheme
                } else {
                    DarkColorScheme.copy(
                        primary = themeColor,
                        primaryContainer = themeColor.copy(alpha = 0.28f),
                        onPrimaryContainer = Color.White
                    )
                }
            } else {
                if (themeColor == DefaultThemeColor) {
                    LightColorScheme
                } else {
                    LightColorScheme.copy(
                        primary = themeColor,
                        primaryContainer = themeColor.copy(alpha = 0.15f),
                        onPrimaryContainer = themeColor
                    )
                }
            }
        }
    }

    val finalColorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) {
            baseColorScheme.pureBlack(true)
        } else {
            baseColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode && setSystemBars && view.context.packageName == "io.github.s1ddhants1.unhinge") {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = UnhingeTypography,
        shapes = UnhingeShapes,
        content = content
    )
}

@Composable
fun Theme(
    themeMode: io.github.s1ddhants1.unhinge.util.ThemeMode = io.github.s1ddhants1.unhinge.util.ThemeMode.SYSTEM,
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    setSystemBars: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        io.github.s1ddhants1.unhinge.util.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        io.github.s1ddhants1.unhinge.util.ThemeMode.LIGHT -> false
        io.github.s1ddhants1.unhinge.util.ThemeMode.DARK -> true
    }
    UnhingeTheme(
        darkTheme = darkTheme,
        pureBlack = pureBlack,
        themeColor = themeColor,
        setSystemBars = setSystemBars,
        content = content
    )
}
