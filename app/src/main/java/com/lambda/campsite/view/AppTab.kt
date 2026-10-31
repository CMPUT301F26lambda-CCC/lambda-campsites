/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026, following the nav bar component in the team's
 * Figma file. It helped with the Kotlin code, syntax and KDoc. Sashreek Addanki
 * reviewed it and committed it.
 */
package com.lambda.campsite.view

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.lambda.campsite.R
import com.lambda.campsite.model.Role

/**
 * Every top-level destination in the app: the nav bar tabs for each role, plus
 * the profile screen.
 *
 * Each feature's screen replaces its tab's placeholder. [plannedWork] says which
 * stories go where, and is only shown on the placeholder, so it isn't a string
 * resource.
 *
 * @property route the navigation route.
 * @property labelRes the tab's label.
 * @property icon the tab's icon. Swap for the Figma icons when the theme work lands.
 * @property plannedWork which stories and teammate build this screen.
 */
enum class AppTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val plannedWork: String,
) {
    ENTRANT_HOME(
        "entrant/home", R.string.tab_home, Icons.Filled.Home,
        "Search, filters, campsite details and joining lotteries (US 01.01 to 01.03, Harman). " +
            "Scanning a QR code (US 01.12, Sashreek).",
    ),
    ENTRANT_RESERVATIONS(
        "entrant/reservations", R.string.tab_reservations, Icons.Filled.DateRange,
        "Winning, booking, confirmations and your reservations (US 01.04 to 01.07, Raghav).",
    ),
    PROFILE(
        "profile", R.string.tab_profile, Icons.Filled.Person,
        "Profile, settings and the app policy (US 01.08 to 01.11, Sashreek).",
    ),
    OWNER_HOME(
        "owner/home", R.string.tab_home, Icons.Filled.Home,
        "Booked dates, reservations, waiting lists, notifications and messages " +
            "(US 02.06 to 02.12, Sreeman).",
    ),
    OWNER_CAMPSITES(
        "owner/campsites", R.string.tab_campsites, Icons.Filled.Place,
        "My campsites, registering and editing a campsite (US 02.01, Sashreek) and its QR code " +
            "(US 02.02 to 02.03, Lalith).",
    ),
    OWNER_LOTTERIES(
        "owner/lotteries", R.string.tab_lotteries, Icons.Filled.Star,
        "Creating and editing lotteries, and the automatic draw (US 02.04 to 02.05, Lalith).",
    ),
    ADMIN_CAMPSITES(
        "admin/campsites", R.string.tab_campsites, Icons.Filled.Place,
        "Browsing and removing campsites (US 03.01 and 03.04, Aarush).",
    ),
    ADMIN_PROFILES(
        "admin/profiles", R.string.tab_profiles, Icons.Filled.Person,
        "Browsing and removing profiles and owners (US 03.02, 03.05 and 03.07, Aarush).",
    ),
    ADMIN_IMAGES(
        "admin/images", R.string.tab_images, Icons.Filled.Face,
        "Browsing and removing images (US 03.03 and 03.06, Aarush).",
    ),
    ADMIN_LOGS(
        "admin/logs", R.string.tab_logs, Icons.AutoMirrored.Filled.List,
        "Notification and message logs (US 03.08 and 03.12, Aarush).",
    );

    companion object {
        /** The nav bar tabs for [role], in order. Matches the Nav bar component in Figma. */
        fun forRole(role: Role): List<AppTab> = when (role) {
            Role.ENTRANT -> listOf(ENTRANT_HOME, ENTRANT_RESERVATIONS, PROFILE)
            Role.OWNER -> listOf(OWNER_HOME, OWNER_CAMPSITES, OWNER_LOTTERIES)
            Role.ADMIN -> listOf(ADMIN_CAMPSITES, ADMIN_PROFILES, ADMIN_IMAGES, ADMIN_LOGS)
        }
    }
}
