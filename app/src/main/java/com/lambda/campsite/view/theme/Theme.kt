/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped change
 * this file in October 2026 to use the team's brand colours and turn off
 * wallpaper-based colours. Sashreek Addanki reviewed and committed it.
 *
 * StatusBarIconStyle was added in November 2026 with GLM 5.3 Flash (Z.ai),
 * through OpenCode, so the status bar's clock and battery icons stay readable
 * over each screen's own header colours. Raghav Sethi asked for it.
 *
 * Last updated: 2026-11-03 (#149), Lalith Valluru.
 * Outstanding issues: manual TalkBack and physical-device review remain pending.
 * OpenAI GPT-6 (OpenAI), through Codex, assisted with retaining the active
 * screen's icon style while navigation transitions dispose the previous screen.
 * Follow-up human review remains pending.
  * Shared surface scheme follow-up (#154), Lalith Valluru, 2026-11-03:
 * OpenAI GPT-6 (OpenAI), through Codex, assisted with ui-guide color mapping.
 */
package com.lambda.campsite.view.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import java.util.WeakHashMap

private val statusBarStyles = WeakHashMap<View, MutableList<Pair<Any, Boolean>>>()

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = CampPrimaryContainer,
    onPrimaryContainer = DeepGreen,
    secondary = SageGreen,
    onSecondary = Color.White,
    secondaryContainer = CampPositive,
    onSecondaryContainer = CampOnPositive,
    background = Mint,
    onBackground = CampOnSurface,
    surface = Mint,
    onSurface = CampOnSurface,
    surfaceVariant = CampField,
    onSurfaceVariant = CampSecondaryText,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = CampSurfaceLow,
    surfaceContainer = CampSurfaceContainer,
    surfaceContainerHigh = CampSurfaceHigh,
    surfaceContainerHighest = CampField,
    outline = CampOutlineStrong,
    outlineVariant = CampOutline,
    tertiary = CampEmber,
    inverseSurface = CampInverseSurface,
    inverseOnSurface = CampInverseText,
)

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenDark,
    onPrimary = DeepGreen,
    primaryContainer = MintDark,
    onPrimaryContainer = Mint,
    secondary = ForestGreenDark,
    onSecondary = DeepGreen,
    secondaryContainer = MintDark,
    onSecondaryContainer = Mint,
    background = NightGreen,
    surface = NightGreenSurface,
    onSurface = Mint,
    onSurfaceVariant = ForestGreenDark,
    surfaceContainerLow = NightGreenSurface,
    surfaceContainer = NightGreenSurface,
    surfaceContainerHigh = NightGreenSurface,
    surfaceContainerHighest = MintDark,
    outlineVariant = SageGreen,
)

/**
 * The app's theme: the brand colours from Figma, light or dark to match the
 * phone's setting.
 *
 * Android 12 and later can colour apps from the wallpaper. We don't use that,
 * so the app always shows the brand greens.
 */
@Composable
fun LambdaCampsiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}

/**
 * Makes the phone's status bar icons (clock, Wi-Fi, battery) readable on top of
 * the screen's colours, while keeping the clock, notifications and battery
 * visible.
 *
 * Screens with a dark colour behind the status bar, like the dark green app
 * bars, call it with `darkIcons = false` so the icons draw white. Screens whose
 * top area is the plain background don't need it: otherwise the app follows the
 * phone's light or dark mode.
 *
 * Each screen calls it where it composes. When the app navigates away from the
 * screen, the look goes back to the system default, so the next screen decides
 * its own.
 */
@Composable
internal fun StatusBarIconStyle(darkIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, darkIcons) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            val owner = Any()
            val styles = statusBarStyles.getOrPut(view) { mutableListOf() }
            styles.add(owner to darkIcons)
            controller.isAppearanceLightStatusBars = darkIcons
            onDispose {
                // An outgoing screen can dispose after the incoming screen's effect ran.
                // Restore the remaining screen's choice instead of overwriting it with defaults.
                styles.removeAll { it.first === owner }
                controller.isAppearanceLightStatusBars = styles.lastOrNull()?.second ?: !view.isSystemDarkMode()
                if (styles.isEmpty()) statusBarStyles.remove(view)
            }
        } else {
            // Compose previews don't run in an activity, so there is nothing to set.
            onDispose { }
        }
    }
}

/** The [Activity] hosting [this] view, unwrapping the theme wrapper Compose adds. */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** `true` when the phone is set to dark mode. */
private fun View.isSystemDarkMode(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
