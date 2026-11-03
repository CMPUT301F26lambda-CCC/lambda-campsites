/*
 * Purpose: the approved Figma win/confirmation artwork, with replaceable exported assets.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: none known; manual visual review remains part of #126.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with asset placement; human review pending.
 */
package com.lambda.campsite.view.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R

/** Original Figma confetti, glow and win/check medallions at their reference geometry. */
@Composable
fun LotteryCelebrationArtwork(confirmed: Boolean = false) {
    // Original Figma artwork exports, positioned relative to the 412dp reference hero.
    BoxWithConstraints(Modifier.fillMaxWidth().height(164.dp)) {
        val scale = maxWidth.value / 412f
        Image(painterResource(R.drawable.flow8_art_2702), null,
            Modifier.offset((81f * scale).dp, (-59f * scale).dp).size((250f * scale).dp))
        Image(painterResource(if (confirmed) R.drawable.flow9_confirmed_medallion else R.drawable.flow8_art_2691), null,
            Modifier.offset((148f * scale).dp, (8f * scale).dp).size((116f * scale).dp, (122f * scale).dp))
        val pieces = listOf(
            Triple(R.drawable.flow8_art_2703, 44f, 18f), Triple(R.drawable.flow8_art_2704, 86f, 54f),
            Triple(R.drawable.flow8_art_2705, 62f, 104f), Triple(R.drawable.flow8_art_2706, 30f, 150f),
            Triple(R.drawable.flow8_art_2707, 120f, 12f), Triple(R.drawable.flow8_art_2708, 104f, 136f),
            Triple(R.drawable.flow8_art_2709, 140f, 70f), Triple(R.drawable.flow8_art_2710, 368f, 22f),
            Triple(R.drawable.flow8_art_2711, 326f, 60f), Triple(R.drawable.flow8_art_2712, 350f, 108f),
            Triple(R.drawable.flow8_art_2713, 380f, 150f), Triple(R.drawable.flow8_art_2714, 292f, 14f),
            Triple(R.drawable.flow8_art_2715, 306f, 132f), Triple(R.drawable.flow8_art_2716, 274f, 76f),
            Triple(R.drawable.flow8_art_2717, 20f, 76f), Triple(R.drawable.flow8_art_2718, 392f, 80f))
        val renderBounds = listOf(floatArrayOf(39.775f,18.563f,9.301f,14.082f), floatArrayOf(86f,54f,7f,7f), floatArrayOf(62f,104f,14f,14f), floatArrayOf(30.785f,147.344f,11.374f,13.339f), floatArrayOf(120f,12f,14f,14f), floatArrayOf(94.093f,136.817f,12.946f,11.960f), floatArrayOf(140f,70f,7f,7f), floatArrayOf(368.657f,20.122f,10.040f,13.909f), floatArrayOf(326f,60f,7f,7f), floatArrayOf(350f,108f,14f,14f), floatArrayOf(372.755f,150.785f,11.374f,13.339f), floatArrayOf(292f,14f,14f,14f), floatArrayOf(306.732f,127.536f,13.661f,10.732f), floatArrayOf(274f,76f,7f,7f), floatArrayOf(20f,76f,7f,7f), floatArrayOf(392f,80f,7f,7f))
        pieces.forEachIndexed { index, (art, _, _) ->
            val (x, y, w, h) = renderBounds[index]
            Image(painterResource(art), null, Modifier.offset((x * scale).dp, (y * scale).dp)
                .size((w * scale).dp, (h * scale).dp), contentScale = ContentScale.Fit)
        }
    }
}

