/*
 * Purpose: Checks that choosing an owner or entrant role changes the visible app navigation.
 * Outstanding issues: None known.
 * AI use: For issue #150, GPT-6 (OpenAI), through Codex, assisted me with the role switch UI regression test.
 */
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.lambda.campsite.R
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Role
import com.lambda.campsite.testutil.ScreenFakeProfile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Checks that the Profile role selector actually changes app navigation state. */
class ProfileRoleSwitchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingAdminChangesTheActiveRole() {
        val profile = Profile("user-1", "Admin User", "admin@example.com",
            roles = setOf(Role.ENTRANT, Role.OWNER, Role.ADMIN))
        val controller = ProfileController(ScreenFakeProfile(profile))
        compose.setContent { ProfileScreen(controller) }
        compose.waitUntil(5_000) { controller.state.value.profile != null }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.role_admin)
        compose.onNode(hasText(label) and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(Role.ADMIN, controller.state.value.activeRole) }
    }
}
