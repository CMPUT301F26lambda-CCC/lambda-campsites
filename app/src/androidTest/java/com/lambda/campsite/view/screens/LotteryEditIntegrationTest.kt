/*
 * Purpose: regression coverage for PR 169's root snackbar and admin-only app bar.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-04 (#172).
 * Outstanding issues: manual TalkBack review remains.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with emulator navigation,
 * confirmation assertions and fixture isolation; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.Source
import com.lambda.campsite.R
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.model.*
import com.lambda.campsite.model.repository.*
import com.lambda.campsite.testutil.*
import com.lambda.campsite.view.LambdaCampsiteApp
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LotteryEditIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun awaitText(value: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
    }
    private fun launch(role: Role): ProfileController {
        check(Firebase.firestore.firestoreSettings.host == "10.0.2.2:8080")
        FirestoreEmulator.clear()
        val uid = runBlocking { withTimeout(15_000) { signInAsNewUser() } }
        FirestoreEmulator.setIgnoringRules("profiles/$uid", ProfileFields.toMap(
            Profile(uid, "Demo Owner", "owner@example.com", roles = setOf(Role.ENTRANT, role), policyVersionAgreed = 1)))
        FirestoreEmulator.setIgnoringRules("campsites/edit-demo", CampsiteFields.toMap(
            Campsite("edit-demo", uid, "Snackbar Demo", "Alberta", rules = "Demo rules")))
        val profile = ProfileController(FirestoreProfileRepository(Firebase.auth, Firebase.firestore))
        compose.setContent { LambdaCampsiteTheme { LambdaCampsiteApp(profile) } }
        compose.waitUntil(15_000) { profile.state.value.profile != null }
        compose.runOnIdle { profile.onSwitchRole(role) }
        return profile
    }
    @Test fun savedCampsiteEditShowsRootSnackbarAndOwnerHasNoExtraProfileBar() {
        launch(Role.OWNER)
        awaitText(text(R.string.tab_campsites))
        compose.onNodeWithContentDescription(text(R.string.open_profile)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tab_campsites)).performClick()
        awaitText("Snackbar Demo")
        compose.onNodeWithText("Snackbar Demo").performClick()
        awaitText(text(R.string.action_edit_details))
        compose.onNodeWithText(text(R.string.action_edit_details)).performScrollTo().performClick()
        awaitText(text(R.string.edit_campsite_title))
        compose.onNodeWithText(text(R.string.label_name_required)).performScrollTo().performTextReplacement("Saved Demo")
        compose.onNodeWithText(text(R.string.save_changes_button)).performClick()
        awaitText(text(R.string.owner_changes_saved))
        compose.onNodeWithText(text(R.string.owner_changes_saved)).assertIsDisplayed()
        val saved = runBlocking { Firebase.firestore.document("campsites/edit-demo").get(Source.SERVER).await() }
        assertEquals("Saved Demo", saved.getString("name"))
    }
    @Test fun adminTabRetainsProfileTopBar() {
        launch(Role.ADMIN)
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription(text(R.string.open_profile)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(text(R.string.open_profile)).assertIsDisplayed().performClick()
        awaitText("Profile")
    }
}
