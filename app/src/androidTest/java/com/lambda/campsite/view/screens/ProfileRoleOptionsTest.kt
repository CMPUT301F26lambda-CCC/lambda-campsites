/*
 * Purpose: profile role visibility and switching regressions (#176).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-04.
 * Outstanding issues: manual TalkBack review remains.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with fake-profile UI
 * tests for role options and live revocation; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.controller.ProfileError
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Role
import com.lambda.campsite.testutil.ScreenFakeProfile
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileRoleOptionsTest {
    @get:Rule val compose = createComposeRule()
    private fun profile(vararg roles: Role) = Profile("demo", "Demo User", "demo@example.com", roles = roles.toSet())
    private fun launch(value: Profile): Pair<ProfileController, ScreenFakeProfile> {
        val repo = ScreenFakeProfile(value)
        val controller = ProfileController(repo)
        compose.setContent { LambdaCampsiteTheme { ProfileScreen(controller) } }
        compose.waitUntil(5000) { controller.state.value.profile != null }
        return controller to repo
    }
    private fun choose(label: String) = compose.onNode(hasText(label) and hasClickAction()).performClick()

    @Test fun entrantOwnerProfileHidesAdminAndSwitchesBothHeldRoles() {
        val (controller, _) = launch(profile(Role.ENTRANT, Role.OWNER))
        compose.onNodeWithText("Admin").assertDoesNotExist()
        choose("Owner")
        compose.runOnIdle { assertEquals(Role.OWNER, controller.state.value.activeRole) }
        choose("Entrant")
        compose.runOnIdle { assertEquals(Role.ENTRANT, controller.state.value.activeRole) }
    }
    @Test fun assignedAdminSeesAndCanSelectAdmin() {
        val (controller, _) = launch(profile(Role.ENTRANT, Role.OWNER, Role.ADMIN))
        compose.onNodeWithText("Admin").assertIsDisplayed()
        choose("Admin")
        compose.runOnIdle { assertEquals(Role.ADMIN, controller.state.value.activeRole) }
    }
    @Test fun revokingAdminRemovesTheOptionAndReturnsToEntrant() {
        val original = profile(Role.ENTRANT, Role.OWNER, Role.ADMIN)
        val (controller, repo) = launch(original)
        choose("Admin")
        runBlocking { repo.save(original.copy(roles = setOf(Role.ENTRANT, Role.OWNER))) }
        compose.waitUntil(5000) { controller.state.value.activeRole == Role.ENTRANT }
        compose.onNodeWithText("Admin").assertDoesNotExist()
        choose("Owner")
        compose.runOnIdle { assertEquals(Role.OWNER, controller.state.value.activeRole) }
    }
    @Test fun entrantCanStillBecomeOwnerWithoutSeeingAdmin() {
        val (controller, _) = launch(profile(Role.ENTRANT))
        compose.onNodeWithText("Admin").assertDoesNotExist()
        choose("Owner")
        compose.waitUntil(5000) { controller.state.value.activeRole == Role.OWNER }
        compose.runOnIdle { assertTrue(controller.state.value.profile!!.hasRole(Role.OWNER)) }
        compose.onNodeWithText("Admin").assertDoesNotExist()
    }
    @Test fun bannedEntrantCannotBecomeOwnerAndHasNoAdminOption() {
        val (controller, _) = launch(profile(Role.ENTRANT).copy(ownerBanned = true))
        choose("Owner")
        compose.runOnIdle {
            assertEquals(Role.ENTRANT, controller.state.value.activeRole)
            assertEquals(ProfileError.OWNER_BANNED, controller.state.value.error)
        }
        compose.onNodeWithText("Admin").assertDoesNotExist()
    }
}
