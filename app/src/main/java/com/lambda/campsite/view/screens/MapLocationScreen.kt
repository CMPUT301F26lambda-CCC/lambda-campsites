/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with the interactive stylized map canvas, draggable pin gesture handling,
 * search suggestions dropdown, string resources and KDoc. Raghav Sethi reviewed,
 * tested, and committed it.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.MapLocationController

/**
 * Map location picker screen (US 02.01.02).
 *
 * Allows owners to search an address/park or drag the pin on the map to set
 * exact coordinates for a campsite.
 *
 * @param controller the [MapLocationController] managing coordinates and search.
 * @param onClose invoked when closing or cancelling the picker.
 * @param onLocationConfirmed invoked with (latitude, longitude, locationName) when chosen.
 * @param modifier optional layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLocationScreen(
    controller: MapLocationController,
    onClose: () -> Unit,
    onLocationConfirmed: (latitude: Double, longitude: Double, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = CampGreenLight,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.set_map_location_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CampGreenDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // Interactive stylized map background
            StylizedMapCanvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // Convert screen pixels to coordinate deltas (~0.0008 deg per pixel)
                            val deltaLat = -dragAmount.y * 0.0008
                            val deltaLng = dragAmount.x * 0.0008
                            controller.onPinDragged(deltaLat, deltaLng)
                        }
                    },
            )

            // Centered Red Location Pin & Drag Callout
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Map pin",
                        tint = Color(0xFFC8382B),
                        modifier = Modifier.size(48.dp),
                    )
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(Color.White, CircleShape),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xCC1A382F),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.drag_map_hint),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // Top Search Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = controller::onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_location_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { controller.onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = CampGreenDark,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Search suggestions dropdown
                if (state.suggestions.isNotEmpty() && state.searchQuery.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    ) {
                        LazyColumn(modifier = Modifier.height((state.suggestions.size * 48).coerceAtMost(200).dp)) {
                            items(state.suggestions) { place ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { controller.onPlaceSelected(place) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = CampGreenDark, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(place.name, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Confirmation Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CampGreenField),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.pin_location_heading),
                        style = MaterialTheme.typography.labelSmall,
                        color = CampGreenDark,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.pinLocationName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onLocationConfirmed(state.latitude, state.longitude, state.pinLocationName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CampGreenDark),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.use_this_location_button))
                    }
                }
            }
        }
    }
}

/**
 * Draws soft organic park areas and cream roads matching the Figma map style.
 */
@Composable
private fun StylizedMapCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Background soft mint
        drawRect(color = Color(0xFFCDE8D5))

        // Organic green park shapes
        drawCircle(color = Color(0xFFA1D6B6), radius = w * 0.35f, center = Offset(w * 0.15f, h * 0.25f))
        drawCircle(color = Color(0xFFA6DBBB), radius = w * 0.40f, center = Offset(w * 0.85f, h * 0.30f))
        drawCircle(color = Color(0xFFA2D7B7), radius = w * 0.38f, center = Offset(w * 0.20f, h * 0.70f))
        drawCircle(color = Color(0xFFA9DCBD), radius = w * 0.32f, center = Offset(w * 0.88f, h * 0.78f))

        // Curved organic roads (cream lines)
        val roadColor = Color(0xFFF9FAF4)
        val roadStroke = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)

        val mainRoad = Path().apply {
            moveTo(0f, h * 0.45f)
            cubicTo(w * 0.3f, h * 0.40f, w * 0.6f, h * 0.55f, w, h * 0.50f)
        }
        drawPath(mainRoad, color = roadColor, style = roadStroke)

        val crossingRoad = Path().apply {
            moveTo(w * 0.45f, 0f)
            cubicTo(w * 0.48f, h * 0.3f, w * 0.52f, h * 0.7f, w * 0.55f, h)
        }
        drawPath(crossingRoad, color = roadColor, style = roadStroke)

        val branchRoad = Path().apply {
            moveTo(w * 0.2f, 0f)
            cubicTo(w * 0.25f, h * 0.25f, w * 0.4f, h * 0.4f, w * 0.5f, h * 0.48f)
        }
        drawPath(branchRoad, color = roadColor, style = roadStroke)
    }
}
