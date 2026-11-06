/*
 * Purpose: the brand colours from the team's Figma file, for light and dark
 * mode.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped change
 * this file in October 2026, replacing the template's purple with the team's
 * Figma colour tokens. Sashreek Addanki reviewed and committed it.
  * Shared palette follow-up (#154), Lalith Valluru, 2026-11-03: OpenAI GPT-6
 * (OpenAI), through Codex, assisted with remaining Figma scheme tokens.
 * Outstanding issues: dark-mode visual review and follow-up human review remain pending.
 */
package com.lambda.campsite.view.theme

import androidx.compose.ui.graphics.Color

// Brand colours from the "Lambda Campsite Theme" tokens in the team's Figma file.

/** Brand/Header and primary: headers, buttons, the selected nav tab. White text sits on it. */
val ForestGreen = Color(0xFF2F5D50)

/** Light green surface and primary container. Dark green text sits on it, since white fails contrast. */
val Mint = Color(0xFFCDE8D5)

/** Text and icons on [Mint]. */
val DeepGreen = Color(0xFF0E2B22)

/** Muted green for secondary elements, like chips and supporting text. */
val SageGreen = Color(0xFF4A6158)

/** Primary colour in dark mode, light enough to read on a dark background. */
val ForestGreenDark = Color(0xFF8FCFB0)

/** Primary container in dark mode. */
val MintDark = Color(0xFF22453A)

/** App background in dark mode. */
val NightGreen = Color(0xFF0F1A16)

/** Cards and sheets in dark mode. */
val NightGreenSurface = Color(0xFF16241F)

/** Fill for the selected segment in the "Use the app as" selector. Darker than [Mint]. */
val SelectedMint = Color(0xFFA9CDB5)

val LightSageGreen = Color(0xFFCDE8D5)
val DarkText = Color(0xFF263D35)
val PureWhite = Color(0xFFFFFFFF)

// Flow 6/7 Figma reading surfaces. Lalith Valluru, 2026-11-03 (#149).
// OpenAI GPT-6 through Codex assisted with token mapping; human review pending.
/** Schemes/On Surface. */
val CampOnSurface = Color(0xFF12241D)
/** Schemes/On Surface Variant. */
val CampSecondaryText = Color(0xFF2F4A40)
/** Schemes/Surface Container High: cards and dialogs. */
val CampSurfaceHigh = Color(0xFFE6F4EB)
/** Schemes/Surface Container Highest: inputs and neutral chips. */
val CampField = Color(0xFFEEF8F1)
/** Schemes/Outline Variant. */
val CampOutline = Color(0xFF9EC2AE)
/** Schemes/Secondary Container: positive status and tonal actions. */
val CampPositive = Color(0xFFA8D5BA)
/** Schemes/On Secondary Container. */
val CampOnPositive = Color(0xFF12301F)
/** Brand/Gold: rating stars. */
val CampGold = Color(0xFFE9A23B)

// Remaining Lambda Light scheme tokens from ui-guide 3.2 (#154).
/** Low-emphasis primary container. */
val CampPrimaryContainer = Color(0xFFB5DFC8)
/** Subtle reading surface. */
val CampSurfaceLow = Color(0xFFDDF0E4)
/** Standard container. */
val CampSurfaceContainer = Color(0xFFD6ECDD)
/** Strong outline. */
val CampOutlineStrong = Color(0xFF5C7A6D)
/** Ember accent. */
val CampEmber = Color(0xFFC0582E)
/** Inverse snackbar surface. */
val CampInverseSurface = Color(0xFF2E3430)
/** Inverse snackbar text. */
val CampInverseText = Color(0xFFEEF2EF)
