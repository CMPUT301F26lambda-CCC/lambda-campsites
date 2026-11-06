/*
 * Purpose: Verifies that owner campsite forms request an image from another app.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with the photo-picker
 * intent regression test. Human review is pending.
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
import androidx.test.espresso.intent.matcher.IntentMatchers.hasCategories
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.platform.app.InstrumentationRegistry
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteFormData
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Checks the external image selection handoff for owner registration and editing (US 02.01.02). */
class OwnerCampsitePhotoPickerIntentTest {
    @get:Rule val compose = createComposeRule()

    @Before fun startIntentRecording() {
        Intents.init()
        intending(hasAction(Intent.ACTION_GET_CONTENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
    }

    @After fun stopIntentRecording() {
        Intents.release()
    }

    @Test fun addPhotoOpensAnImageContentPicker() {
        assertImagePickerLaunches(CampsiteFormData(), R.string.add_photo_button)
    }

    @Test fun replacePhotoOpensAnImageContentPicker() {
        assertImagePickerLaunches(CampsiteFormData(imageId = "existing-photo"), R.string.replace_photo_button)
    }

    private fun assertImagePickerLaunches(form: CampsiteFormData, buttonLabel: Int) {
        compose.setContent {
            OwnerCampsiteFormScreen(
                titleRes = R.string.register_campsite_title,
                saveRes = R.string.register_campsite_button,
                form = form,
                selectedPhoto = null,
                existingPhotoUrl = null,
                actions = OwnerFormActions(
                    name = {}, location = {}, description = {}, occupancy = {},
                    season = { _, _ -> }, price = {}, rules = {}, addTag = {}, removeTag = {},
                    photo = {}, removePhoto = {}, pickLocation = {},
                ),
                isDirty = false,
                onClose = {},
                onSave = {},
            )
        }

        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(buttonLabel)
        compose.onNodeWithText(label).performScrollTo().performClick()

        intended(allOf(
            hasAction(Intent.ACTION_GET_CONTENT),
            hasType("image/*"),
            hasCategories(setOf(Intent.CATEGORY_OPENABLE)),
        ))
    }
}
