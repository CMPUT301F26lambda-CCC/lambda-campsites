/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with the controller integration, TalkBack accessibility semantics,
 * empty state, strings extraction and KDoc. Raghav Sethi reviewed, tested,
 * and committed it.
 *
 * The status bar icon styling was added in November 2026 with GLM 5.3 Flash
 * (Z.ai), through OpenCode, at Raghav Sethi's request.
 */

/**
 * Purpose: Displays the owner's campsite list, including empty states and registration access.
 * Feature owner: Raghav Sethi
 * Outstanding issues: None
 */

package com.lambda.campsite.view.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteListError
import com.lambda.campsite.controller.OwnerCampsiteListController
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.view.theme.StatusBarIconStyle

internal val CampGreenDark = Color(0xFF2F5D50)
internal val CampGreenLight = Color(0xFFC9E5D3)
internal val CampGreenField = Color(0xFFEAF5EE)

/**
 * The owner's campsites tab screen (US 02.03.01).
 *
 * It connects to [OwnerCampsiteListController], collects its state, and renders
 * the owner's list of registered campsites along with a floating button to
 * register a new campsite.
 *
 * @param controller the ViewModel controller providing campsite list state.
 * @param onCampsiteClick invoked when the owner selects a campsite to view details.
 * @param onRegisterClick invoked when the owner taps the button to register a new campsite.
 * @param modifier optional modifier for the screen layout.
 * @param hasPhoto optional lambda determining if a campsite has an associated photo.
 */
@Composable
fun OwnerCampsiteListScreen(
    controller: OwnerCampsiteListController,
    onCampsiteClick: (Campsite) -> Unit,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasPhoto: (Campsite) -> Boolean = { false },
) {
    val state by controller.state.collectAsStateWithLifecycle()

    OwnerCampsiteListContent(
        campsites = state.campsites,
        isLoading = state.isLoading,
        error = state.error,
        onCampsiteClick = onCampsiteClick,
        onRegisterClick = onRegisterClick,
        modifier = modifier,
        hasPhoto = hasPhoto,
    )
}

/**
 * Stateless content composable for the owner's campsites list (US 02.03.01).
 *
 * @param campsites the list of campsites to display.
 * @param onCampsiteClick callback when a campsite is clicked.
 * @param onRegisterClick callback when "Register campsite" is clicked.
 * @param modifier optional layout modifier.
 * @param hasPhoto optional checker for campsite photos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerCampsiteListScreen(
    campsites: List<Campsite>,
    onCampsiteClick: (Campsite) -> Unit,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasPhoto: (Campsite) -> Boolean = { false },
) {
    OwnerCampsiteListContent(
        campsites = campsites,
        isLoading = false,
        error = null,
        onCampsiteClick = onCampsiteClick,
        onRegisterClick = onRegisterClick,
        modifier = modifier,
        hasPhoto = hasPhoto,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerCampsiteListContent(
    campsites: List<Campsite>,
    isLoading: Boolean,
    error: CampsiteListError?,
    onCampsiteClick: (Campsite) -> Unit,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasPhoto: (Campsite) -> Boolean = { false },
) {
    // The dark green app bar runs under the clock, so the clock draws white.
    StatusBarIconStyle(darkIcons = false)

    Scaffold(
        modifier = modifier,
        containerColor = CampGreenLight,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.my_campsites_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CampGreenDark,
                    titleContentColor = Color.White,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRegisterClick,
                containerColor = CampGreenDark,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.register_campsite_button)) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                isLoading && campsites.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CampGreenDark)
                    }
                }
                error != null && campsites.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.error_load_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                campsites.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.no_campsites_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    val countText = if (campsites.size == 1) {
                        stringResource(R.string.campsite_count_one)
                    } else {
                        stringResource(R.string.campsite_count_many, campsites.size)
                    }

                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .padding(16.dp)
                            .semantics { heading() },
                    )
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        items(campsites, key = { it.id }) { camp ->
                            CampsiteRow(camp, hasPhoto(camp)) { onCampsiteClick(camp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CampsiteRow(camp: Campsite, hasPhoto: Boolean, onClick: () -> Unit) {
    val photoNotice = if (hasPhoto) "" else " · " + stringResource(R.string.no_photo_yet)
    val subtitle = camp.location + photoNotice
    val rowDescription = "${camp.name}, $subtitle"

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CampGreenField),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(camp.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
