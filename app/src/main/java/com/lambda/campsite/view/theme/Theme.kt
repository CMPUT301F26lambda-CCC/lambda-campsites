/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped change
 * this file in October 2026 to use the team's brand colours and turn off
 * wallpaper-based colours. Sashreek Addanki reviewed and committed it.
 */
package com.lambda.campsite.view.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = DeepGreen,
    secondary = SageGreen,
    onSecondary = Color.White,
    secondaryContainer = Mint,
    onSecondaryContainer = DeepGreen,
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
