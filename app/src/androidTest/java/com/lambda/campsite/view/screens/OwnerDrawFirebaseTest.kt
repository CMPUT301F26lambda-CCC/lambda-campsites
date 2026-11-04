/*
 * Purpose: real owner navigation and live automatic draw results from the local Python backend.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: the local harness must invoke draw_due_lotteries after FLOW5_FIXTURE_READY (#122).
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with emulator assertions; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.R
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.model.*
import com.lambda.campsite.model.repository.*
import com.lambda.campsite.testutil.*
import com.lambda.campsite.view.LambdaCampsiteApp
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Requires the local draw harness, and refuses production reads/writes. */
class OwnerDrawFirebaseTest {
    @get:Rule val compose=createComposeRule()
    private fun text(id:Int)=InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun awaitText(s:String,timeout:Long=20_000)=compose.waitUntil(timeout){compose.onAllNodesWithText(s,substring=true).fetchSemanticsNodes().isNotEmpty()}
    @Test fun ownerSeesActualBackendDrawAndSavedWinnerIds() {
        org.junit.Assume.assumeTrue("Opt-in local draw harness; pass -e runDrawHarness true", InstrumentationRegistry.getArguments().getString("runDrawHarness") == "true")
        check(Firebase.firestore.firestoreSettings.host=="10.0.2.2:8080")
        FirestoreEmulator.clear()
        val uid=runBlocking{withTimeout(15_000){signInAsNewUser()}}
        FirestoreEmulator.setIgnoringRules("profiles/$uid",ProfileFields.toMap(Profile(uid,"Demo Owner","owner@example.com",roles=setOf(Role.OWNER),policyVersionAgreed=1)))
        FirestoreEmulator.setIgnoringRules("campsites/flow5-demo",CampsiteFields.toMap(Campsite("flow5-demo",uid,"Flow 5 Demo Campsite","Demo location")))
        val lottery=Lottery("flow5-draw-review",uid,"Automatic Draw Demo",listOf("flow5-demo"),Timestamp(1,0),Timestamp(2,0),
            LocalDate.now().plusMonths(1),LocalDate.now().plusMonths(2),numberOfWinners=1,entryCount=2)
        FirestoreEmulator.setIgnoringRules("lotteries/${lottery.id}",LotteryFields.toMap(lottery)+("entryCount" to 2))
        for(id in listOf("demo-a","demo-b")) FirestoreEmulator.setIgnoringRules("lotteries/${lottery.id}/entries/$id",LotteryEntryFields.toMap(LotteryEntry(lottery.id,id,Timestamp(1,0))))
        val profile=ProfileController(FirestoreProfileRepository(Firebase.auth,Firebase.firestore))
        compose.setContent{LambdaCampsiteTheme{LambdaCampsiteApp(profile)}}
        compose.waitUntil(15_000){profile.state.value.profile!=null}
        compose.runOnIdle{profile.onSwitchRole(Role.OWNER)}
        awaitText(text(R.string.tab_lotteries))
        compose.onNodeWithText(text(R.string.tab_lotteries)).performClick()
        awaitText(text(R.string.flow5_view_results))
        compose.onNodeWithText(text(R.string.flow5_view_results)).performClick()
        awaitText(text(R.string.flow5_pending))
        android.util.Log.i("Flow5Review","FLOW5_FIXTURE_READY")
        awaitText("Drawn",90_000)
        val entries=runBlocking{withTimeout(15_000){Firebase.firestore.collection("lotteries/${lottery.id}/entries").get(Source.SERVER).await().documents}}
        val winner=entries.single{it.getString("status")=="WON"}.id
        val other=entries.single{it.getString("status")=="NOT_SELECTED"}.id
        compose.onNodeWithText(text(R.string.flow5_winners)).performScrollTo().performClick()
        compose.onNodeWithText("Entrant ID: $winner").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Entrant ID: $other").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.flow5_not_selected)).performScrollTo().performClick()
        compose.onNodeWithText("Entrant ID: $other").performScrollTo().assertIsDisplayed()
        val draw=runBlocking{withTimeout(15_000){Firebase.firestore.document("lotteries/${lottery.id}").get(Source.SERVER).await()}}
        assertEquals(1L,draw.getLong("drawnWinnerCount"))
        assertNotNull(draw.getTimestamp("drawnAt"))
    }
}
