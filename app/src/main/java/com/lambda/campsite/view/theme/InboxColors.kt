/* OpenAI GPT-6 (OpenAI), through Codex, assisted with mapping the Figma inbox tokens. */
// Flow 8 update: Lalith Valluru, 2026-11-03.
// Outstanding issues: manual TalkBack and deployed push delivery checks remain.
// Purpose: Define the shared colors used by the notification inbox.
package com.lambda.campsite.view.theme

import androidx.compose.ui.graphics.Color

/** Figma Schemes/Surface Container Low for unread inbox rows. */
val InboxUnreadSurface = Color(0xFFDDF0E4)
/** Figma Schemes/Surface Container Highest for neutral status and icon surfaces. */
val InboxNeutralSurface = Color(0xFFEEF8F1)
/** Figma Schemes/Tertiary Container for a winning notice icon. */
val InboxWinIconSurface = Color(0xFFFFDBCD)
/** Figma Schemes/On Surface for inbox content. */
val InboxText = Color(0xFF12241D)
/** Figma Schemes/On Surface Variant for supporting text. */
val InboxSecondaryText = Color(0xFF2F4A40)
/** Figma Schemes/Outline Variant for dividers and neutral chips. */
val InboxOutline = Color(0xFF9EC2AE)