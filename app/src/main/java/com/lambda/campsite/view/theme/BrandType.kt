/*
 * Purpose: Figma's Fraunces brand-name style, separate from Roboto reading text (#154).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: none known. Font attribution is in res/raw/fraunces_ofl.txt.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with font setup; human review pending.
 */
package com.lambda.campsite.view.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import com.lambda.campsite.R

/** Home brand name from Figma: Fraunces SemiBold, 22/28; no other reading text uses this font. */
@OptIn(ExperimentalTextApi::class)
val CampBrandName = TextStyle(
    fontFamily = FontFamily(Font(R.font.fraunces, weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.opticalSizing(22.sp)))),
    fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.22).sp,
)
