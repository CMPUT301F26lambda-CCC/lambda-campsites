/*
 * Purpose: Checks the campsite QR screen's handoff to Android's share sheet.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with this QR sharing
 * intent test for the Part 3 checkpoint. Human review is pending.
 */
package com.lambda.campsite.view.screens

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.platform.app.InstrumentationRegistry
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteQrCodeController
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.QRCode
import com.lambda.campsite.testutil.ScreenFakeCampsites
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Verifies the external share intent produced by the owner QR screen (US 02.02.01). */
class CampsiteQrShareIntentTest {
    @get:Rule val compose = createComposeRule()

    @Before fun startIntentRecording() {
        Intents.init()
        intending(hasAction(Intent.ACTION_CHOOSER))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
    }

    @After fun stopIntentRecording() {
        Intents.release()
    }

    @Test fun shareOpensChooserWithCampsiteLink() {
        val campsite = Campsite("share-site", "owner", "Pine Cove", "Jasper")
        val controller = CampsiteQrCodeController(campsite.id, ScreenFakeCampsites(listOf(campsite)))
        compose.setContent { CampsiteQrCodeScreen(controller, onBack = {}) }
        compose.waitUntil(timeoutMillis = 10_000) { !controller.state.value.isLoading }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.action_share))
            .performScrollTo().performClick()

        intended(allOf(
            hasAction(Intent.ACTION_CHOOSER),
            hasExtra(Intent.EXTRA_TITLE, context.getString(R.string.qr_share_chooser_title)),
            hasExtra(Intent.EXTRA_INTENT, allOf(
                hasAction(Intent.ACTION_SEND),
                hasType("text/plain"),
                hasExtra(Intent.EXTRA_SUBJECT, campsite.name),
                hasExtra(
                    Intent.EXTRA_TEXT,
                    context.getString(
                        R.string.qr_share_text,
                        campsite.name,
                        "${QRCode.URI_PREFIX}${campsite.id}",
                    ),
                ),
            )),
        ))
    }
}
