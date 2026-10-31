/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Compose navigation code, syntax
 * and KDoc. Sashreek Addanki reviewed it and committed it.
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
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.model.Role
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
                            else -> PlaceholderScreen(tab)
                        }
                    }
                }
            }
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
