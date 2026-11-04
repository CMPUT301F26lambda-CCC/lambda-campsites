/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Compose navigation code, syntax
 * and KDoc. Sashreek Addanki reviewed it and committed it.
 *
 * The owner campsites tab was first a hand-rolled back stack, then moved to
 * routes in this NavHost in November 2026, with Gemini 3.8 Flash (Google
 * DeepMind). Raghav Sethi reviewed and committed it.
 *
 * The status bar fix (each screen's app bar drawing up under the clock) was
 * added in November 2026 with GLM 5.3 Flash (Z.ai), through OpenCode, at
 * Raghav Sethi's request.
 */
package com.lambda.campsite.view

import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteQrCodeController
import com.lambda.campsite.controller.LotteryResultsController
import com.lambda.campsite.controller.MapLocationController
import com.lambda.campsite.controller.NewLotteryController
import com.lambda.campsite.controller.OwnerCampsiteAddController
import com.lambda.campsite.controller.OwnerCampsiteDetailsController
import com.lambda.campsite.controller.OwnerCampsiteEditController
import com.lambda.campsite.controller.OwnerCampsiteListController
import com.lambda.campsite.controller.OwnerLotteryDetailsController
import com.lambda.campsite.controller.OwnerLotteryListController
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.model.Role
import com.lambda.campsite.view.screens.CampsiteQrCodeScreen
import com.lambda.campsite.view.screens.LotteryResultsScreen
import com.lambda.campsite.view.screens.MapLocationScreen
import com.lambda.campsite.view.screens.NewLotteryScreen
import com.lambda.campsite.view.screens.OwnerCampsiteAddScreen
import com.lambda.campsite.view.screens.OwnerCampsiteDetailsScreen
import com.lambda.campsite.view.screens.OwnerCampsiteEditScreen
import com.lambda.campsite.view.screens.OwnerCampsiteListScreen
import com.lambda.campsite.view.screens.OwnerLotteryDetailsScreen
import com.lambda.campsite.view.screens.OwnerLotteryListScreen
import com.lambda.campsite.view.screens.PlaceholderScreen
import com.lambda.campsite.view.screens.ProfileScreen

/** Routes below a tab's route, keeping the nav bar's tab selected. */
private const val OWNER_ADD_ROUTE = "owner/campsites/add"
private const val OWNER_DETAILS_ROUTE = "owner/campsites/details/{campsiteId}"
private const val OWNER_EDIT_ROUTE = "owner/campsites/edit/{campsiteId}"
private const val OWNER_QR_ROUTE = "owner/campsites/qr/{campsiteId}"

/** The owner lotteries tab's screens: the wizard, a lottery's details and its results (US 02.04, 02.05). */
private const val OWNER_LOTTERY_NEW_ROUTE = "owner/lotteries/new"
private const val OWNER_LOTTERY_DETAILS_ROUTE = "owner/lotteries/details/{lotteryId}"
private const val OWNER_LOTTERY_RESULTS_ROUTE = "owner/lotteries/results/{lotteryId}"

/** Opens the map picker with exact initial coordinates, and returnTo = [OWNER_PICKER_RETURN_ADD]/[OWNER_PICKER_RETURN_EDIT]. */
private const val OWNER_MAP_PICKER_ROUTE =
    "owner/map-picker?returnTo={returnTo}&campsiteId={campsiteId}" +
        "&lat={lat}&lng={lng}&pinName={pinName}"

/** Where the map picker hands its confirmed location back: the register form. */
private const val OWNER_PICKER_RETURN_ADD = "ADD"

/** Where the map picker hands its confirmed location back: the edit form. */
private const val OWNER_PICKER_RETURN_EDIT = "EDIT"

/** Builds the details route for [campsiteId]. */
private fun ownerDetailsRoute(campsiteId: String) = "owner/campsites/details/$campsiteId"

/** Builds the edit route for [campsiteId]. */
private fun ownerEditRoute(campsiteId: String) = "owner/campsites/edit/$campsiteId"

/** Builds the QR code route for [campsiteId]. */
private fun ownerQrRoute(campsiteId: String) = "owner/campsites/qr/$campsiteId"

/** Builds the details route for [lotteryId]. */
private fun ownerLotteryDetailsRoute(lotteryId: String) = "owner/lotteries/details/$lotteryId"

/** Builds the results route for [lotteryId]. */
private fun ownerLotteryResultsRoute(lotteryId: String) = "owner/lotteries/results/$lotteryId"

/** Builds the map picker route with the pin's [initialLat]/[initialLng]/[initialPinName] already on the map. */
private fun ownerMapPickerRoute(
    returnTo: String,
    campsiteId: String,
    initialLat: Double?,
    initialLng: Double?,
    initialPinName: String?,
): String =
    "owner/map-picker?returnTo=$returnTo" +
        "&campsiteId=${Uri.encode(campsiteId)}" +
        "&lat=${initialLat?.toString().orEmpty()}" +
        "&lng=${initialLng?.toString().orEmpty()}" +
        "&pinName=${Uri.encode(initialPinName.orEmpty())}"

/**
 * The whole app: the nav bar for the active role and the screen for the
 * selected tab.
 *
 * Switching roles builds a fresh navigation stack, so the app lands on that
 * role's first tab. Owners and admins reach the profile screen from the
 * account button in the top bar, since their nav bars have no Profile tab.
 *
 * The owner campsites tab's six screens (list, register, map picker, details,
 * edit and QR code) are routes in the same [NavHost], so the system Back
 * gesture pops them one at a time and rotating the phone keeps the open screen.
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
            // The root inset lets each screen's app bar draw up under the clock;
            // screens without an app bar pad themselves with statusBarsPadding().
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = isSelectedTab(tab, currentRoute),
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
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                AppTab.entries.forEach { tab ->
                    composable(tab.route) {
                        when (tab) {
                            AppTab.PROFILE -> ProfileScreen(profileController)
                            AppTab.OWNER_CAMPSITES -> OwnerCampsitesList(navController)
                            AppTab.OWNER_LOTTERIES -> OwnerLotteriesList(navController)
                            else -> PlaceholderScreen(tab)
                        }
                    }
                }

                composable(OWNER_ADD_ROUTE) { entry ->
                    val addController: OwnerCampsiteAddController =
                        viewModel(factory = OwnerCampsiteAddController.Factory)
                    OwnerCampsiteAddScreen(
                        controller = addController,
                        onClose = { navController.popBackStack() },
                        onPickLocation = {
                            val form = addController.state.value.form
                            navController.navigate(
                                ownerMapPickerRoute(
                                    returnTo = OWNER_PICKER_RETURN_ADD,
                                    campsiteId = "",
                                    initialLat = form.latitude,
                                    initialLng = form.longitude,
                                    initialPinName = form.locationPinName,
                                ),
                            )
                        },
                        onSaved = { newCampsite ->
                            // The register form's screen leaves the back stack,
                            // so the next registration starts on a blank form.
                            // Popping back to the list keeps Back returning to it.
                            navController.navigate(ownerDetailsRoute(newCampsite.id)) {
                                popUpTo(AppTab.OWNER_CAMPSITES.route)
                            }
                        },
                    )
                }

                composable(
                    route = OWNER_MAP_PICKER_ROUTE,
                    arguments = listOf(
                        navArgument("returnTo") { type = NavType.StringType },
                        navArgument("campsiteId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("lat") { type = NavType.StringType; defaultValue = "" },
                        navArgument("lng") { type = NavType.StringType; defaultValue = "" },
                        navArgument("pinName") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val args = entry.arguments ?: Bundle.EMPTY
                    val returnTo = args.getString("returnTo").orEmpty()
                    val pickerCampsiteId = args.getString("campsiteId").orEmpty()
                    val initialLat = args.getString("lat")?.toDoubleOrNull()
                    val initialLng = args.getString("lng")?.toDoubleOrNull()
                    val initialPinName = args.getString("pinName")?.ifBlank { null }

                    val mapController: MapLocationController = viewModel(
                        factory = MapLocationController.createFactory(initialLat, initialLng, initialPinName),
                    )

                    // The controller the confirmed pin goes to. Getting it during
                    // composition, from the entry this picker was opened from, means
                    // the map picker passes the pin to the same register or edit form.
                    val confirmLocation: (Double, Double, String?) -> Unit = when (returnTo) {
                        OWNER_PICKER_RETURN_EDIT -> {
                            val editEntry = navController.getBackStackEntry(OWNER_EDIT_ROUTE)
                            val editController: OwnerCampsiteEditController = viewModel(
                                viewModelStoreOwner = editEntry,
                                key = "edit_${pickerCampsiteId}",
                                factory = OwnerCampsiteEditController.createFactory(pickerCampsiteId),
                            )
                            ({ lat, lng, name -> editController.onLocationPinSet(lat, lng, name) })
                        }
                        // Any other return target is the register form.
                        else -> {
                            val addEntry = navController.getBackStackEntry(OWNER_ADD_ROUTE)
                            val addController: OwnerCampsiteAddController = viewModel(
                                viewModelStoreOwner = addEntry,
                                factory = OwnerCampsiteAddController.Factory,
                            )
                            ({ lat, lng, name -> addController.onLocationPinSet(lat, lng, name) })
                        }
                    }

                    MapLocationScreen(
                        controller = mapController,
                        onClose = { navController.popBackStack() },
                        onLocationConfirmed = { lat, lng, name ->
                            confirmLocation(lat, lng, name)
                            navController.popBackStack()
                        },
                    )
                }

                composable(OWNER_DETAILS_ROUTE) { entry ->
                    val campsiteId = entry.arguments?.getString("campsiteId").orEmpty()
                    val detailsController: OwnerCampsiteDetailsController = viewModel(
                        key = "details_${campsiteId}",
                        factory = OwnerCampsiteDetailsController.createFactory(campsiteId),
                    )
                    OwnerCampsiteDetailsScreen(
                        controller = detailsController,
                        onBack = { navController.popBackStack() },
                        onEditClick = { navController.navigate(ownerEditRoute(campsiteId)) },
                        onBookedDatesClick = {
                            // Handled when BookedDates screen is implemented (US 02.06)
                        },
                        onQrCodeClick = { navController.navigate(ownerQrRoute(campsiteId)) },
                    )
                }

                composable(OWNER_EDIT_ROUTE) { entry ->
                    val campsiteId = entry.arguments?.getString("campsiteId").orEmpty()
                    val editController: OwnerCampsiteEditController = viewModel(
                        key = "edit_${campsiteId}",
                        factory = OwnerCampsiteEditController.createFactory(campsiteId),
                    )
                    OwnerCampsiteEditScreen(
                        controller = editController,
                        onClose = { navController.popBackStack() },
                        onPickLocation = {
                            val editState = editController.state.value
                            navController.navigate(
                                ownerMapPickerRoute(
                                    returnTo = OWNER_PICKER_RETURN_EDIT,
                                    campsiteId = campsiteId,
                                    initialLat = editState.latitude,
                                    initialLng = editState.longitude,
                                    initialPinName = editState.locationPinName,
                                ),
                            )
                        },
                        onSaved = { navController.popBackStack() },
                    )
                }

                composable(OWNER_QR_ROUTE) { entry ->
                    val campsiteId = entry.arguments?.getString("campsiteId").orEmpty()
                    val qrController: CampsiteQrCodeController = viewModel(
                        key = "qr_${campsiteId}",
                        factory = CampsiteQrCodeController.createFactory(campsiteId),
                    )
                    CampsiteQrCodeScreen(
                        controller = qrController,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(OWNER_LOTTERY_NEW_ROUTE) {
                    val newLotteryController: NewLotteryController =
                        viewModel(factory = NewLotteryController.Factory)
                    NewLotteryScreen(
                        controller = newLotteryController,
                        onClose = { navController.popBackStack() },
                        onCreated = { lottery ->
                            // The wizard's screen leaves the back stack, so the
                            // next lottery starts on a blank form. Popping back
                            // to the tab keeps Back returning to its list.
                            navController.navigate(ownerLotteryDetailsRoute(lottery.id)) {
                                popUpTo(AppTab.OWNER_LOTTERIES.route)
                            }
                        },
                    )
                }

                composable(OWNER_LOTTERY_DETAILS_ROUTE) { entry ->
                    val lotteryId = entry.arguments?.getString("lotteryId").orEmpty()
                    val detailsController: OwnerLotteryDetailsController = viewModel(
                        key = "lottery_details_${lotteryId}",
                        factory = OwnerLotteryDetailsController.createFactory(lotteryId),
                    )
                    OwnerLotteryDetailsScreen(
                        controller = detailsController,
                        onBack = { navController.popBackStack() },
                        onEditClick = {
                            // Handled when editing a lottery lands (US 02.04.06)
                        },
                        onWaitingListClick = {
                            // Handled when the waiting-list screen lands (US 02.09)
                        },
                        onMessageWaitingListClick = {
                            // Handled when messaging lands (US 02.10)
                        },
                        onSentMessagesClick = {
                            // Handled when sent messages land (US 02.10.02)
                        },
                        onResultsClick = { navController.navigate(ownerLotteryResultsRoute(lotteryId)) },
                    )
                }

                composable(OWNER_LOTTERY_RESULTS_ROUTE) { entry ->
                    val lotteryId = entry.arguments?.getString("lotteryId").orEmpty()
                    val resultsController: LotteryResultsController = viewModel(
                        key = "lottery_results_${lotteryId}",
                        factory = LotteryResultsController.createFactory(lotteryId),
                    )
                    LotteryResultsScreen(
                        controller = resultsController,
                        onBack = { navController.popBackStack() },
                        onWinnersClick = {
                            // Handled when the winner list lands (US 02.05.01)
                        },
                        onNotSelectedClick = {
                            // Handled when the not-selected list lands (US 02.05.01)
                        },
                    )
                }
            }
        }
    }
}

/**
 * The first screen of the owner campsites tab: the owner's campsites, leading
 * to the register form (US 02.01.01) or a campsite's details.
 */
@Composable
private fun OwnerCampsitesList(navController: NavHostController) {
    val listController: OwnerCampsiteListController =
        viewModel(factory = OwnerCampsiteListController.Factory)
    OwnerCampsiteListScreen(
        controller = listController,
        onCampsiteClick = { campsite ->
            navController.navigate(ownerDetailsRoute(campsite.id))
        },
        onRegisterClick = { navController.navigate(OWNER_ADD_ROUTE) },
    )
}

/**
 * The first screen of the owner lotteries tab: the owner's lotteries in their
 * Active, Upcoming and Completed buckets, leading to the "New lottery" wizard
 * (US 02.04.01) or a lottery's details (US 02.04.06).
 */
@Composable
private fun OwnerLotteriesList(navController: NavHostController) {
    val listController: OwnerLotteryListController =
        viewModel(factory = OwnerLotteryListController.Factory)
    OwnerLotteryListScreen(
        controller = listController,
        onLotteryClick = { lottery ->
            navController.navigate(ownerLotteryDetailsRoute(lottery.id))
        },
        onNewLotteryClick = { navController.navigate(OWNER_LOTTERY_NEW_ROUTE) },
    )
}

/** `true` when [currentRoute] is [tab] or one of its sub screens. */
private fun isSelectedTab(tab: AppTab, currentRoute: String?): Boolean {
    if (currentRoute == null) return false
    if (currentRoute.startsWith("${tab.route}/")) return true
    // The map picker is opened from the owner registrations and edits.
    if (tab == AppTab.OWNER_CAMPSITES && currentRoute.startsWith("owner/map-picker")) return true
    return currentRoute == tab.route
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
