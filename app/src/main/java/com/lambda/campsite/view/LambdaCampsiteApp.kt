/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Compose navigation code, syntax
 * and KDoc. Sashreek Addanki reviewed it and committed it.
 *
 * Owner campsites navigation was added in November 2026 with Gemini 3.8 Flash
 * (Google DeepMind). Raghav Sethi reviewed and committed it.
 */
package com.lambda.campsite.view

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteQrCodeController
import com.lambda.campsite.controller.MapLocationController
import com.lambda.campsite.controller.OwnerCampsiteAddController
import com.lambda.campsite.controller.OwnerCampsiteDetailsController
import com.lambda.campsite.controller.OwnerCampsiteEditController
import com.lambda.campsite.controller.OwnerCampsiteListController
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.model.Role
import com.lambda.campsite.view.screens.CampsiteQrCodeScreen
import com.lambda.campsite.view.screens.MapLocationScreen
import com.lambda.campsite.view.screens.OwnerCampsiteAddScreen
import com.lambda.campsite.view.screens.OwnerCampsiteDetailsScreen
import com.lambda.campsite.view.screens.OwnerCampsiteEditScreen
import com.lambda.campsite.view.screens.OwnerCampsiteListScreen
import com.lambda.campsite.view.screens.PlaceholderScreen
import com.lambda.campsite.view.screens.ProfileScreen

/**
 * The whole app: the nav bar for the active role and the screen for the
 * selected tab.
 *
 * Switching roles builds a fresh navigation stack, so the app lands on that
 * role's first tab. Owners and admins reach the profile screen from the
 * account button in the top bar, since their nav bars have no Profile tab.
 *
 * To add a feature's screen, replace its tab's `PlaceholderScreen` in the
 * `when` below.
 *
 * @param profileController supplies the active role. Created with its Factory by default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LambdaCampsiteApp(
    profileController: ProfileController = viewModel(factory = ProfileController.Factory),
) {
    val state by profileController.state.collectAsStateWithLifecycle()
    val tabs = AppTab.forRole(state.activeRole)

    key(state.activeRole) {
        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

        Scaffold(
            topBar = {
                if (state.activeRole != Role.ENTRANT) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        actions = {
                            IconButton(onClick = { navController.navigateToTab(AppTab.PROFILE) }) {
                                Icon(
                                    Icons.Filled.AccountCircle,
                                    contentDescription = stringResource(R.string.open_profile),
                                )
                            }
                        },
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.navigateToTab(tab) },
                            // The label below names the tab, so the icon needs no description.
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = tabs.first().route,
                modifier = Modifier.padding(innerPadding),
            ) {
                AppTab.entries.forEach { tab ->
                    composable(tab.route) {
                        when (tab) {
                            AppTab.PROFILE -> ProfileScreen(profileController)
                            AppTab.OWNER_CAMPSITES -> OwnerCampsitesFlow()
                            else -> PlaceholderScreen(tab)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Internal navigation backstack destination for the campsite owner tab (US 02.01 – 02.03).
 */
private sealed interface OwnerCampsiteScreenDest {
    data object List : OwnerCampsiteScreenDest
    data object Add : OwnerCampsiteScreenDest
    data class MapPicker(val returnTo: ReturnTarget, val initialLat: Double?, val initialLng: Double?, val initialName: String?) : OwnerCampsiteScreenDest {
        enum class ReturnTarget { ADD, EDIT }
    }
    data class Details(val campsiteId: String) : OwnerCampsiteScreenDest
    data class Edit(val campsiteId: String) : OwnerCampsiteScreenDest
    data class QrCode(val campsiteId: String) : OwnerCampsiteScreenDest
}

/**
 * Manages the screen transitions for the owner campsites tab across all 6 screens:
 * List, Register, Map Picker, Details, Edit, and QR Code.
 */
@Composable
private fun OwnerCampsitesFlow() {
    var backStack by remember { mutableStateOf(listOf<OwnerCampsiteScreenDest>(OwnerCampsiteScreenDest.List)) }

    val currentDest = backStack.lastOrNull() ?: OwnerCampsiteScreenDest.List

    // Keep controllers in scope across child screen transitions
    val listController: OwnerCampsiteListController = viewModel(factory = OwnerCampsiteListController.Factory)
    val addController: OwnerCampsiteAddController = viewModel(factory = OwnerCampsiteAddController.Factory)

    when (val dest = currentDest) {
        OwnerCampsiteScreenDest.List -> {
            OwnerCampsiteListScreen(
                controller = listController,
                onCampsiteClick = { camp ->
                    backStack = backStack + OwnerCampsiteScreenDest.Details(camp.id)
                },
                onRegisterClick = {
                    backStack = backStack + OwnerCampsiteScreenDest.Add
                },
            )
        }
        OwnerCampsiteScreenDest.Add -> {
            OwnerCampsiteAddScreen(
                controller = addController,
                onClose = {
                    if (backStack.size > 1) backStack = backStack.dropLast(1)
                },
                onPickLocation = {
                    val addState = addController.state.value
                    backStack = backStack + OwnerCampsiteScreenDest.MapPicker(
                        returnTo = OwnerCampsiteScreenDest.MapPicker.ReturnTarget.ADD,
                        initialLat = addState.latitude,
                        initialLng = addState.longitude,
                        initialName = addState.locationPinName,
                    )
                },
                onSaved = { newCampsite ->
                    backStack = listOf(OwnerCampsiteScreenDest.List, OwnerCampsiteScreenDest.Details(newCampsite.id))
                },
            )
        }
        is OwnerCampsiteScreenDest.MapPicker -> {
            val mapController: MapLocationController = viewModel(
                key = "map_${dest.initialLat}_${dest.initialLng}",
                factory = MapLocationController.createFactory(dest.initialLat, dest.initialLng, dest.initialName),
            )
            MapLocationScreen(
                controller = mapController,
                onClose = {
                    if (backStack.size > 1) backStack = backStack.dropLast(1)
                },
                onLocationConfirmed = { lat, lng, name ->
                    when (dest.returnTo) {
                        OwnerCampsiteScreenDest.MapPicker.ReturnTarget.ADD -> {
                            addController.onLocationPinSet(lat, lng, name)
                        }
                        OwnerCampsiteScreenDest.MapPicker.ReturnTarget.EDIT -> {
                            // Target edit controller will receive update via state or callback
                        }
                    }
                    if (backStack.size > 1) backStack = backStack.dropLast(1)
                },
            )
        }
        is OwnerCampsiteScreenDest.Details -> {
            val detailsController: OwnerCampsiteDetailsController = viewModel(
                key = "details_${dest.campsiteId}",
                factory = OwnerCampsiteDetailsController.createFactory(dest.campsiteId),
            )
            OwnerCampsiteDetailsScreen(
                controller = detailsController,
                onBack = {
                    if (backStack.size > 1) backStack = backStack.dropLast(1)
                },
                onEditClick = {
                    backStack = backStack + OwnerCampsiteScreenDest.Edit(dest.campsiteId)
                },
                onBookedDatesClick = {
                    // Handled when BookedDates screen is implemented (US 02.06)
                },
                onQrCodeClick = {
                    backStack = backStack + OwnerCampsiteScreenDest.QrCode(dest.campsiteId)
                },
            )
        }
        is OwnerCampsiteScreenDest.Edit -> {
            val editController: OwnerCampsiteEditController = viewModel(
                key = "edit_${dest.campsiteId}",
                factory = OwnerCampsiteEditController.createFactory(dest.campsiteId),
            )
            OwnerCampsiteEditScreen(
                controller = editController,
                onClose = {
                    if (backStack.size > 1) backStack = backStack.dropLast(1)
                },
                onPickLocation = {
                    val editState = editController.state.value
                    backStack = backStack + OwnerCampsiteScreenDest.MapPicker(
                        returnTo = OwnerCampsiteScreenDest.MapPicker.ReturnTarget.EDIT,
                        initialLat = editState.latitude,
                        initialLng = editState.longitude,
                        initialName = editState.locationPinName,
                    )
                },
                onSaved = {
                    if (backStack.size > 1) backStack = backStack.dropLast(1)
                },
            )
        }
        is OwnerCampsiteScreenDest.QrCode -> {
            val qrController: CampsiteQrCodeController = viewModel(
                key = "qr_${dest.campsiteId}",
                factory = CampsiteQrCodeController.createFactory(dest.campsiteId),
            )
            CampsiteQrCodeScreen(
                controller = qrController,
                onBack = {
                    if (backStack.size > 1) backStack = backStack.dropLast(1)
                },
            )
        }
    }
}

/**
 * Opens [tab] the way a nav bar should: going back to the first tab instead of
 * piling up screens, and keeping each tab's scroll position.
 */
private fun NavHostController.navigateToTab(tab: AppTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
