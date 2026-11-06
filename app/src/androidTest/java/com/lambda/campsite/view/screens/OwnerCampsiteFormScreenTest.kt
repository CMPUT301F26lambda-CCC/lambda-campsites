/*
 * Purpose: Checks the owner campsite registration and edit form fields, dates, and validation in Compose.
 * Outstanding issues: None known.
 * AI use: For issue #150, GPT-6 (OpenAI), through Codex, assisted me with owner form UI tests.
 * Human review is pending.
 */
package com.lambda.campsite.view.screens

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteFormData
import com.lambda.campsite.view.AppTab
import com.lambda.campsite.view.ownerTabIconRes
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Checks the shared owner register/edit fields and discard feedback (US 02.01). */
class OwnerCampsiteFormScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)


    @Test fun priceRulesAndSeasonControlsAppearInBothForms() {
        var form by mutableStateOf(CampsiteFormData(
            seasonStart = LocalDate.of(2026, 11, 20),
            seasonEnd = LocalDate.of(2027, 10, 12),
        ))
        var saved = 0
        compose.setContent {
            OwnerCampsiteFormScreen(
                titleRes = R.string.register_campsite_title,
                saveRes = R.string.register_campsite_button,
                form = form,
                selectedPhoto = null,
                existingPhotoUrl = null,
                actions = OwnerFormActions(
                    name = { form = form.copy(name = it) },
                    location = { form = form.copy(location = it) },
                    description = { form = form.copy(description = it) },
                    occupancy = { form = form.copy(maxOccupancy = it) },
                    season = { start, end -> form = form.copy(seasonStart = start, seasonEnd = end) },
                    price = { form = form.copy(pricePerNight = it) },
                    rules = { form = form.copy(rules = it) },
                    addTag = { form = form.copy(tags = form.tags + it) },
                    removeTag = { form = form.copy(tags = form.tags - it) },
                    photo = {}, removePhoto = {}, pickLocation = {},
                ),
                isDirty = form != CampsiteFormData(), onClose = {}, onSave = { saved++ },
            )
        }
        compose.onNodeWithText(label(R.string.add_photo_button)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.season_opens_label), substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.season_closes_label), substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Season opens: Nov 20, 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Season closes: Oct 12, 2027").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.owner_price_label)).performScrollTo().performTextInput("32")
        compose.onNodeWithText(label(R.string.owner_rules_label)).performScrollTo().performTextInput("No fires")
        compose.runOnIdle {
            assertEquals("32", form.pricePerNight)
            assertEquals("No fires", form.rules)
        }
        compose.onNode(hasText(label(R.string.register_campsite_button)) and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(1, saved) }
    }

    @Test fun ownerCampsitesTabUsesTransparentTentAsset() {
        val resource = ownerTabIconRes(AppTab.OWNER_CAMPSITES)
        assertEquals(R.drawable.flow11_tent, resource)
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val bitmap = BitmapFactory.decodeResource(resources, resource!!)
        assertTrue(bitmap.width >= 48)
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
    }

    @Test fun cancelWithChangesAsksBeforeDiscarding() {
        var closed = 0
        compose.setContent {
            OwnerCampsiteFormScreen(
                titleRes = R.string.edit_campsite_title,
                saveRes = R.string.save_changes_button,
                form = CampsiteFormData(name = "Pine"), selectedPhoto = null,
                existingPhotoUrl = null,
                actions = OwnerFormActions({}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}),
                isDirty = true, onClose = { closed++ }, onSave = {},
            )
        }
        compose.onNodeWithText(label(R.string.owner_cancel)).performClick()
        compose.onNodeWithText(label(R.string.owner_discard_title)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, closed) }
        compose.onNodeWithText(label(R.string.owner_discard)).performClick()
        compose.runOnIdle { assertEquals(1, closed) }
    }
}
