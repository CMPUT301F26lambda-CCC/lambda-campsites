/*
 * Last updated: 2026-11-03 (Flow 6/7 follow-up #149; Lalith Valluru).
 * Outstanding issues: Flow 12 shared-project navigation verification remains pending (#129).
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
 *
 * OpenAI GPT-6 (OpenAI), through Codex, assisted with Flow 6/7 entrant search,
 * selected-lottery routing and shared parent ViewModels, and Flow 12 owner
 * waiting-list navigation. Follow-up human review remains pending.
 * Layout follow-up (#154): Lalith Valluru, 2026-11-03. OpenAI GPT-6 (OpenAI),
 * through Codex, assisted with Figma layout/status/navigation-color integration
 * or its regression checks. Follow-up human review remains pending.
 */
// Flow 5: Lalith Valluru, 2026-11-03. OpenAI GPT-6 (OpenAI), through Codex,
// assisted with read-only draw metadata/results integration; human review pending.
// Flow 11 feature owner: Sreeman Reddy. GPT-6 (OpenAI), through Codex,
// also helped route the owner reservation lists
// and booked-date calendar for Flow 11 (US 02.06.01 and 02.07.01 to 02.07.03).
// Human review is pending.
// Flow 10 feature owner: Sreeman Reddy. GPT-6 (OpenAI), through Codex,
// assisted me with entrant reservation list and detail navigation.
// Flow 16 feature owner: Sreeman Reddy. GPT-6 (OpenAI), through Codex,
// assisted me with the Home scan button and full-screen scanner route.
package com.lambda.campsite.view

import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.lambda.campsite.controller.MapLocationController
import com.lambda.campsite.controller.OwnerCampsiteAddController
import com.lambda.campsite.controller.OwnerCampsiteDetailsController
import com.lambda.campsite.controller.OwnerCampsiteEditController
import com.lambda.campsite.controller.OwnerCampsiteListController
import com.lambda.campsite.controller.OwnerBookedDatesController
import com.lambda.campsite.controller.OwnerReservationsController
import com.lambda.campsite.controller.OwnerReservationTab
import com.lambda.campsite.controller.EntrantReservationsController
import com.lambda.campsite.controller.QrScanController
import com.lambda.campsite.controller.SearchController
import com.lambda.campsite.controller.CampsiteController
import com.lambda.campsite.controller.LotteryController
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.controller.OwnerWaitingListController
import com.lambda.campsite.view.screens.OwnerWaitingListScreen
import com.lambda.campsite.model.Role
import com.lambda.campsite.view.screens.CampsiteQrCodeScreen
import com.lambda.campsite.view.screens.MapLocationScreen
import com.lambda.campsite.view.screens.OwnerCampsiteAddScreen
import com.lambda.campsite.view.screens.OwnerCampsiteDetailsScreen
import com.lambda.campsite.view.screens.OwnerCampsiteEditScreen
import com.lambda.campsite.view.screens.OwnerCampsiteListScreen
import com.lambda.campsite.view.screens.OwnerBookedDatesScreen
import com.lambda.campsite.view.screens.OwnerReservationsScreen
import com.lambda.campsite.view.screens.OwnerHomeScreen
import com.lambda.campsite.view.screens.PlaceholderScreen
import com.lambda.campsite.view.screens.SearchScreen
import com.lambda.campsite.view.screens.CampsiteScreen
import com.lambda.campsite.view.screens.EntrantLotteryDetailsScreen
import com.lambda.campsite.view.screens.EntrantReservationsScreen
import com.lambda.campsite.view.screens.EntrantReservationDetailsScreen
import com.lambda.campsite.view.screens.QrScannerScreen
import com.lambda.campsite.view.screens.ProfileScreen
import com.lambda.campsite.view.screens.OwnerLotteryResultsScreen
import com.lambda.campsite.controller.ProfileError
import com.lambda.campsite.view.screens.SettingScreen
import com.lambda.campsite.view.screens.EditProfileScreen
import com.lambda.campsite.view.screens.FirstLaunchScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton

/** Entrant pushed routes retain Home selection and the selected campsite's parent state. */
private const val ENTRANT_DETAILS_ROUTE = "entrant/home/details/{campsiteId}"
private const val OWNER_RESULTS_ROUTE = "owner/lotteries/results/{lotteryId}"
private const val ENTRANT_LOTTERY_ROUTE = "entrant/home/details/{campsiteId}/lottery/{lotteryId}"
private const val ENTRANT_RESERVATION_DETAILS_ROUTE = "entrant/reservations/details/{reservationId}"
private const val ENTRANT_SCAN_ROUTE = "entrant/home/scan"

/** Owner campsite routes keep the Campsites tab selected. */
private const val OWNER_ADD_ROUTE = "owner/campsites/add"
private const val OWNER_DETAILS_ROUTE = "owner/campsites/details/{campsiteId}"
private const val OWNER_EDIT_ROUTE = "owner/campsites/edit/{campsiteId}"
private const val OWNER_QR_ROUTE = "owner/campsites/qr/{campsiteId}"
private const val OWNER_BOOKED_DATES_ROUTE = "owner/campsites/booked/{campsiteId}"
private const val OWNER_HOME_BOOKED_DATES_ROUTE = "owner/home/booked"
private const val OWNER_RESERVATIONS_ROUTE = "owner/home/reservations/{tab}"

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

/** Builds the owner's calendar route for [campsiteId]. */
private fun ownerBookedDatesRoute(campsiteId: String) = "owner/campsites/booked/${Uri.encode(campsiteId)}"

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
    val SETTINGS_ROUTE = "settings"
    val EDIT_PROFILE_ROUTE = "edit_profile"
    val FIRST_LAUNCH_ROUTE = "first_launch"

    key(state.activeRole) {
        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

        Scaffold(
            // The root inset lets each screen's app bar draw up under the clock;
            // screens without an app bar pad themselves with statusBarsPadding().
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (state.activeRole != Role.ENTRANT && currentRoute != FIRST_LAUNCH_ROUTE) {
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
                if (currentRoute != FIRST_LAUNCH_ROUTE && currentRoute != ENTRANT_SCAN_ROUTE) {
                    NavigationBar(containerColor = com.lambda.campsite.view.theme.CampSurfaceHigh) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = isSelectedTab(tab, currentRoute),
                                onClick = { navController.navigateToTab(tab) },
                                // The label below names the tab, so the icon needs no description.
                                icon = {
                                    val entrantIcon = if (state.activeRole == Role.ENTRANT) when (tab) {
                                        AppTab.ENTRANT_HOME -> R.drawable.flow10_nav_home
                                        AppTab.ENTRANT_RESERVATIONS -> R.drawable.flow11_home_active
                                        AppTab.PROFILE -> R.drawable.flow10_person
                                        else -> null
                                    } else null
                                    if (entrantIcon == null) Icon(tab.icon, contentDescription = null)
                                    else Icon(painterResource(entrantIcon), contentDescription = null)
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = com.lambda.campsite.view.theme.CampPositive,
                                    selectedIconColor = com.lambda.campsite.view.theme.ForestGreen,
                                    selectedTextColor = com.lambda.campsite.view.theme.ForestGreen,
                                    unselectedIconColor = com.lambda.campsite.view.theme.CampSecondaryText,
                                    unselectedTextColor = com.lambda.campsite.view.theme.CampSecondaryText,
                                ),
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (state.activeRole == Role.ENTRANT && currentRoute == AppTab.ENTRANT_HOME.route) {
                    ExtendedFloatingActionButton(
                        onClick = { navController.navigate(ENTRANT_SCAN_ROUTE) },
                        icon = { Icon(painterResource(R.drawable.flow16_scan), null, Modifier.size(20.dp)) },
                        text = { Text(stringResource(R.string.flow16_scan_title)) },
                        containerColor = com.lambda.campsite.view.theme.ForestGreen,
                        contentColor = Color.White,
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
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
                            AppTab.ENTRANT_HOME -> {
                                val searchController: SearchController = viewModel(factory = SearchController.Factory)
                                SearchScreen(searchController, profileName = state.profile?.name, onCampsiteClick = { campsiteId ->
                                    navController.navigate("entrant/home/details/${Uri.encode(campsiteId)}")
                                })
                            }
                            AppTab.PROFILE -> ProfileScreen(
                                controller = profileController,
                                onEditProfile = { navController.navigate(EDIT_PROFILE_ROUTE) },
                                onSettings = { navController.navigate(SETTINGS_ROUTE) },
                                onBack = { navController.navigateToTab(tabs.first()) },
                            )
                            AppTab.ENTRANT_RESERVATIONS -> {
                                val reservationsController: EntrantReservationsController =
                                    viewModel(factory = EntrantReservationsController.Factory)
                                EntrantReservationsScreen(reservationsController,
                                    onReservationClick = { id -> navController.navigate(
                                        "entrant/reservations/details/${Uri.encode(id)}") },
                                    onBrowseCampsites = { navController.navigateToTab(AppTab.ENTRANT_HOME) })
                            }
                            AppTab.OWNER_HOME -> {
                                val reservationsController: OwnerReservationsController =
                                    viewModel(factory = OwnerReservationsController.Factory)
                                OwnerHomeScreen(reservationsController, state.profile?.name.orEmpty(),
                                    onReservations = { tab -> navController.navigate("owner/home/reservations/${tab.name.lowercase()}") },
                                    onBookedDates = { navController.navigate(OWNER_HOME_BOOKED_DATES_ROUTE) })
                            }
                            AppTab.OWNER_CAMPSITES -> OwnerCampsitesList(navController)
                            AppTab.OWNER_LOTTERIES -> {
                                val waitingListController: OwnerWaitingListController = viewModel(
                                    factory = OwnerWaitingListController.createFactory(),
                                )
                                OwnerWaitingListScreen(waitingListController,
                                    onBack = { navController.navigateToTab(AppTab.OWNER_HOME) },
                                    onResults = { id -> navController.navigate("owner/lotteries/results/${Uri.encode(id)}") })
                            }
                            else -> PlaceholderScreen(tab)
                        }
                    }
                }

                composable(OWNER_RESULTS_ROUTE,
                    arguments = listOf(navArgument("lotteryId") { type = NavType.StringType })) { entry ->
                    val id = requireNotNull(entry.arguments?.getString("lotteryId"))
                    val results: OwnerWaitingListController = viewModel(factory = OwnerWaitingListController.createFactory(id, includeContacts = false))
                    OwnerLotteryResultsScreen(results, onBack = { navController.popBackStack() })
                }

                composable(OWNER_RESERVATIONS_ROUTE,
                    arguments = listOf(navArgument("tab") { type = NavType.StringType })) { entry ->
                    val tabName = entry.arguments?.getString("tab").orEmpty()
                    val tab = OwnerReservationTab.entries.firstOrNull { it.name.equals(tabName, ignoreCase = true) }
                        ?: OwnerReservationTab.ACTIVE
                    val homeEntry = navController.getBackStackEntry(AppTab.OWNER_HOME.route)
                    val reservationsController: OwnerReservationsController = viewModel(
                        viewModelStoreOwner = homeEntry, factory = OwnerReservationsController.Factory)
                    OwnerReservationsScreen(reservationsController,
                        onBack = { navController.popBackStack() }, initialTab = tab)
                }

                composable(ENTRANT_RESERVATION_DETAILS_ROUTE,
                    arguments = listOf(navArgument("reservationId") { type = NavType.StringType })) { entry ->
                    val reservationId = requireNotNull(entry.arguments?.getString("reservationId"))
                    val parent = navController.getBackStackEntry(AppTab.ENTRANT_RESERVATIONS.route)
                    val reservationsController: EntrantReservationsController = viewModel(
                        viewModelStoreOwner = parent, factory = EntrantReservationsController.Factory)
                    val reservationsState by reservationsController.state.collectAsStateWithLifecycle()
                    val campsiteId = reservationsState.reservation(reservationId)?.campsiteId
                    val campsiteController: CampsiteController? = campsiteId?.let { id ->
                        viewModel(key = "reservation-site-$id", factory = CampsiteController.createFactory(id))
                    }
                    EntrantReservationDetailsScreen(reservationId, reservationsController,
                        campsiteController, onBack = { navController.popBackStack() })
                }

                composable(ENTRANT_SCAN_ROUTE) {
                    val scanController: QrScanController = viewModel(factory = QrScanController.Factory)
                    QrScannerScreen(scanController, onBack = { navController.popBackStack() },
                        onCampsiteFound = { campsiteId ->
                            navController.popBackStack()
                            navController.navigate("entrant/home/details/${Uri.encode(campsiteId)}")
                        })
                }

                composable(OWNER_HOME_BOOKED_DATES_ROUTE) {
                    val bookedDatesController: OwnerBookedDatesController = viewModel(
                        factory = OwnerBookedDatesController.createFactory())
                    OwnerBookedDatesScreen(bookedDatesController, onBack = { navController.popBackStack() })
                }

                composable(
                    ENTRANT_DETAILS_ROUTE,
                    arguments = listOf(navArgument("campsiteId") { type = NavType.StringType }),
                ) { entry ->
                    val campsiteId = requireNotNull(entry.arguments?.getString("campsiteId"))
                    val campsiteController: CampsiteController = viewModel(
                        key = "entrant-details-$campsiteId",
                        factory = CampsiteController.createFactory(campsiteId),
                    )
                    val lotteryController: LotteryController = viewModel(
                        key = "entrant-lottery-$campsiteId",
                        factory = LotteryController.createFactory(campsiteId),
                    )
                    CampsiteScreen(campsiteController, lotteryController, onBack = { navController.popBackStack() },
                        onLotteryClick = { lotteryId -> navController.navigate(
                            "entrant/home/details/${Uri.encode(campsiteId)}/lottery/${Uri.encode(lotteryId)}") })
                }

                composable(ENTRANT_LOTTERY_ROUTE, arguments = listOf(
                    navArgument("campsiteId") { type = NavType.StringType },
                    navArgument("lotteryId") { type = NavType.StringType },
                )) { entry ->
                    val campsiteId = requireNotNull(entry.arguments?.getString("campsiteId"))
                    val lotteryId = requireNotNull(entry.arguments?.getString("lotteryId"))
                    val parent = remember(entry) {
                        // A future direct entry has no campsite parent; own its controllers safely.
                        try { navController.getBackStackEntry(ENTRANT_DETAILS_ROUTE) }
                        catch (_: IllegalArgumentException) { entry }
                    }
                    val campsiteController: CampsiteController = viewModel(viewModelStoreOwner = parent,
                        key = "entrant-details-$campsiteId", factory = CampsiteController.createFactory(campsiteId))
                    val lotteryController: LotteryController = viewModel(viewModelStoreOwner = parent,
                        key = "entrant-lottery-$campsiteId", factory = LotteryController.createFactory(campsiteId))
                    EntrantLotteryDetailsScreen(lotteryController, campsiteController, lotteryId,
                        onBack = { navController.popBackStack() })
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
                            navController.navigate(ownerBookedDatesRoute(campsiteId))
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

                composable(OWNER_BOOKED_DATES_ROUTE) { entry ->
                    val campsiteId = entry.arguments?.getString("campsiteId").orEmpty()
                    val bookedDatesController: OwnerBookedDatesController = viewModel(
                        key = "booked_${campsiteId}",
                        factory = OwnerBookedDatesController.createFactory(campsiteId),
                    )
                    OwnerBookedDatesScreen(bookedDatesController, onBack = { navController.popBackStack() })
                }

                composable(SETTINGS_ROUTE) {
                    SettingScreen(
                        name = state.profile?.name.orEmpty(),
                        notificationsOn = state.profile?.notificationsOn ?: true,
                        onNotificationsChange = profileController::onSetNotificationsOn,
                        onBack = { navController.popBackStack() },
                        saveFailed = state.error == ProfileError.SAVE_FAILED,
                        onDeleteProfile = {
                            // TODO (backend): call profileController.onDeleteProfile() here
                            // to delete the profile before navigating.
                            navController.navigate(FIRST_LAUNCH_ROUTE) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            }
                        },
                    )
                }

                composable(EDIT_PROFILE_ROUTE) {
                    val profile = state.profile
                    if (profile != null) {
                        EditProfileScreen(
                            name = profile.name,
                            email = profile.email,
                            phone = profile.phone,
                            saveFailed = state.error == ProfileError.SAVE_FAILED,
                            onCancel = {
                                profileController.onEditProfileClosed()
                                navController.popBackStack()
                            },
                            onSave = { name, email, phone ->
                                profileController.onSaveProfile(name, email, phone) {
                                    navController.popBackStack()
                                }
                            },
                        )
                    }
                }

                composable(FIRST_LAUNCH_ROUTE) {
                    FirstLaunchScreen(
                        onGetStarted = { name ->
                            profileController.onFinishFirstLaunch(name)
                            navController.navigate(tabs.first().route) {
                                popUpTo(FIRST_LAUNCH_ROUTE) { inclusive = true }
                            }
                        },
                        saveFailed = state.error == ProfileError.SAVE_FAILED,
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
