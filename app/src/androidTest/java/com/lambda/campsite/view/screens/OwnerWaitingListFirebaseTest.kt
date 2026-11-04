/*
 * Last updated: 2026-11-03 (Flow 12).
 * Outstanding issues: None known for the Flow 12 changes.
 * Feature owner: Lalith Valluru. Purpose: Flow 12 / US 02.09.01 full navigation and security tests.
 * AI use: OpenAI GPT-6 through Codex assisted with emulator fixtures and assertions.
 * Human review pending. These tests refuse production Firestore hosts.
 */
package com.lambda.campsite.view.screens

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.app
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.model.*
import com.lambda.campsite.model.repository.*
import com.lambda.campsite.testutil.*
import com.lambda.campsite.view.LambdaCampsiteApp
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Actual app navigation, live Firestore list updates and owner/privacy rules for #129. */
class OwnerWaitingListFirebaseTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var uid: String
    private lateinit var first: Lottery
    private lateinit var empty: Lottery
    private lateinit var foreign: Lottery
    private val repository get() = FirestoreLotteryRepository(Firebase.auth, Firebase.firestore)

    @Before fun seed() {
        check(Firebase.firestore.firestoreSettings.host == "10.0.2.2:8080") { "Local emulator required" }
        FirestoreEmulator.clear()
        uid = runBlocking { withTimeout(15000) { signInAsNewUser() } }
        val owner = Profile(uid, "Demo Owner", "owner@example.com", roles = setOf(Role.ENTRANT, Role.OWNER))
        FirestoreEmulator.setIgnoringRules("profiles/$uid", ProfileFields.toMap(owner))
        val suffix = UUID.randomUUID().toString()
        fun lottery(id: String, name: String, ownerId: String = uid) = Lottery("$id-$suffix", ownerId, name,
            listOf("camp-$suffix"), Timestamp(1792000000, 0), Timestamp(1793000000, 0),
            LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 8))
        first = lottery("first", "October Camping Lottery")
        empty = lottery("empty", "November Camping Lottery")
        foreign = lottery("foreign", "Private Other Owner Lottery", "another-owner")
        listOf(first, empty, foreign).forEach {
            FirestoreEmulator.setIgnoringRules("lotteries/${it.id}", LotteryFields.toMap(it) + ("entryCount" to 0))
        }
        seedEntry(first.id, "sample-entrant-002", 1792000200)
        seedEntry(first.id, "sample-entrant-001", 1792000100)
        FirestoreEmulator.setIgnoringRules("profiles/sample-entrant-001",
            ProfileFields.toMap(Profile("sample-entrant-001", "Alex Demo", "alex@example.com", phone = "780-555-0101")))
        FirestoreEmulator.setIgnoringRules("profiles/sample-entrant-002",
            ProfileFields.toMap(Profile("sample-entrant-002", "Jordan Demo", "jordan@example.com")))
        seedEntry(foreign.id, "private-entrant", 1792000000)
        FirestoreEmulator.setIgnoringRules("profiles/private-entrant",
            ProfileFields.toMap(Profile("private-entrant", "Private Name", "private@example.com")))
    }

    private fun seedEntry(lotteryId: String, id: String, seconds: Long) = FirestoreEmulator.setIgnoringRules(
        "lotteries/$lotteryId/entries/$id", LotteryEntryFields.toMap(LotteryEntry(lotteryId, id, Timestamp(seconds, 0))))

    @Test fun ownerNavigatesSelectsSeesLiveOrderedEntriesAndReturnsHome() {
        lateinit var profile: ProfileController
        compose.runOnUiThread { profile = ProfileController(FirestoreProfileRepository(Firebase.auth, Firebase.firestore)) }
        compose.waitUntil(15000) { profile.state.value.profile?.deviceId == uid }
        compose.runOnUiThread { profile.onSwitchRole(Role.OWNER) }
        compose.setContent { LambdaCampsiteTheme { LambdaCampsiteApp(profile) } }
        compose.onNodeWithText("Lotteries").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("November Camping Lottery").fetchSemanticsNodes().isNotEmpty() }
        // Alphabetical first choice is November; select October through the actual dropdown.
        compose.onNodeWithText("November Camping Lottery").performClick()
        compose.onNodeWithText("Private Other Owner Lottery").assertDoesNotExist()
        compose.onNodeWithText(first.name).performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("2 entrants").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(20000) { compose.onAllNodesWithText("Alex Demo").fetchSemanticsNodes().isNotEmpty() }
        val firstRow = compose.onNodeWithText("Alex Demo")
        val secondRow = compose.onNodeWithText("Jordan Demo")
        assertTrue(firstRow.fetchSemanticsNode().boundsInRoot.top < secondRow.fetchSemanticsNode().boundsInRoot.top)
        capture("waiting-list.png")
        firstRow.performClick()
        compose.onNodeWithText("Email: alex@example.com").assertIsDisplayed()
        compose.onNodeWithText("Phone: 780-555-0101").assertIsDisplayed()
        compose.onNodeWithText("Private Name").assertDoesNotExist()
        capture("entrant-details.png")
        compose.onNodeWithContentDescription("Close entrant details").performClick()
        seedEntry(first.id, "sample-entrant-003", 1792000300)
        compose.waitUntil(15000) { compose.onAllNodesWithText("3 entrants").fetchSemanticsNodes().isNotEmpty() }
        deleteEntry(first.id, "sample-entrant-003")
        compose.waitUntil(15000) { compose.onAllNodesWithText("2 entrants").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(first.name).performClick()
        compose.onNodeWithText(empty.name).performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("No entrants on the waiting list.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("0 entrants").assertIsDisplayed()
        capture("empty-list.png")
        compose.onNodeWithContentDescription("Back to Owner Home").performClick()
        compose.onNodeWithText("Waiting list").assertDoesNotExist()
        compose.onNodeWithText("Lotteries").assertIsDisplayed()
    }

    @Test fun serverAllowsOwnListButDeniesAnotherOwnersListAndPrivateProfiles() {
        runBlocking { withTimeout(15000) {
            val entries = repository.waitingList(first.id).first { it.size == 2 }
            assertEquals(listOf("sample-entrant-001", "sample-entrant-002"), entries.map { it.entrantId })
        } }
        val listError = firestoreErrorFrom { withTimeout(15000) {
            Firebase.firestore.collection("lotteries/${foreign.id}/entries").get(Source.SERVER).await()
        } }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, listError?.code)
        val profileError = firestoreErrorFrom { withTimeout(15000) {
            Firebase.firestore.document("profiles/private-entrant").get(Source.SERVER).await()
        } }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, profileError?.code)
        val callable = CallableWaitingListContactRepository(com.google.firebase.functions.FirebaseFunctions.getInstance())
        runBlocking { withTimeout(20000) {
            val contacts = callable.contacts(first.id, listOf("sample-entrant-001", "private-entrant"))
            assertEquals(setOf("sample-entrant-001"), contacts.keys)
            assertEquals("Alex Demo", contacts.getValue("sample-entrant-001").name)
            assertEquals("780-555-0101", contacts.getValue("sample-entrant-001").phone)
            try {
                callable.contacts(foreign.id, listOf("private-entrant"))
                fail("Foreign owner contact access must fail")
            } catch (error: com.google.firebase.functions.FirebaseFunctionsException) {
                assertEquals(com.google.firebase.functions.FirebaseFunctionsException.Code.PERMISSION_DENIED, error.code)
            }
            Firebase.auth.signOut()
            try {
                callable.contacts(first.id, listOf("sample-entrant-001"))
                fail("Signed-out contact access must fail")
            } catch (error: com.google.firebase.functions.FirebaseFunctionsException) {
                assertEquals(com.google.firebase.functions.FirebaseFunctionsException.Code.UNAUTHENTICATED, error.code)
            }
        } }
    }

    private fun deleteEntry(lotteryId: String, id: String) {
        val project = Firebase.app.options.projectId
        val connection = URL("http://10.0.2.2:8080/v1/projects/$project/databases/(default)/documents/lotteries/$lotteryId/entries/$id")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer owner")
            check(connection.responseCode in 200..299)
        } finally { connection.disconnect() }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "flow12-review").apply { mkdirs() }
        compose.waitForIdle()
        // A modal is a second Compose root; capture the actual display including its scrim/navigation.
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
