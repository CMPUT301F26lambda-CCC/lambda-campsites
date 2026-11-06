/*
Purpose:
The purpose of this file is to test the EditProfileScreen and ensure
that users can successfully update their personal information.
The tests cover saving changes, discarding unsaved changes using
the Cancel and X buttons, and keeping changes when the user chooses
to continue editing.
They also verify that validation errors are displayed when the user
enters incomplete or invalid information and disappear once the
fields are corrected. Additional tests check that the profile avatar
initials update when the name changes and that an error message is
displayed when saving fails.(US 01.07.03)
These tests help ensure that the Edit Profile screen behaves as
expected before the changes are integrated into the application.
Outstanding Issue-None
*/
/*
*
* AI use: Claude Sonnet 5.5 (Anthropic), used through the Claude chat app,
* helped with the test cases, the Kotlin syntax and reviewing Kdoc on November 2026.
* Harmanjoth Kaur reviewed and ran them.
*/
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lambda.campsite.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [EditProfileScreen] covering profile editing and validation
 * (US 01.07.03 – Update personal information).
 * The tests verify that users can:
 * - Save updated profile information and retain unchanged fields.
 * - Discard changes using Cancel or the X button, or continue editing.
 * - See validation errors for invalid or incomplete information.
 * - Correct validation errors and successfully save their changes.
 * - See their avatar initials update when editing their name.
 * - See an error message when profile saving fails.
 *
 * Also verifies that saving without making changes preserves the
 * original profile information.
 */
@RunWith(AndroidJUnit4::class)
class EditProfileScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // JUnit makes a new instance per test, so these start fresh every time.
    private var cancelCount = 0
    private var saveCount = 0
    private var savedName: String? = null
    private var savedEmail: String? = null
    private var savedPhone: String? = null

    // ---------- helpers ----------

    private fun str(id: Int): String = context.getString(id)

    private fun launch(
        name: String = "Jane Doe",
        email: String = "jane@example.com",
        phone: String? = "780 555 0123",
        saveFailed: Boolean = false,
    ) {
        rule.setContent {
            EditProfileScreen(
                name = name,
                email = email,
                phone = phone,
                onCancel = { cancelCount++ },
                onSave = { n, e, p ->
                    saveCount++
                    savedName = n
                    savedEmail = e
                    savedPhone = p
                },
                saveFailed = saveFailed,
            )
        }
    }

    /** The text fields' labels have a "*" added in the screen. */
    private fun field(labelRes: Int) = rule.onNodeWithText(str(labelRes) + "*")

    private fun fill(labelRes: Int, value: String) {
        field(labelRes).performTextClearance()
        field(labelRes).performTextInput(value)
    }

    private fun fillAll(name: String, email: String, phone: String) {
        fill(R.string.profile_name, name)
        fill(R.string.profile_email, email)
        fill(R.string.profile_phone, phone)
    }

    private fun clickSave() = rule.onNodeWithText(str(R.string.profile_save_changes)).performClick()
    private fun clickCancel() = rule.onNodeWithText(str(R.string.profile_cancel)).performClick()
    private fun clickX() = rule.onNodeWithContentDescription("Close edit profile").performClick()
    private fun clickDiscard() = rule.onNodeWithText(str(R.string.profile_discard)).performClick()
    private fun clickKeepEditing() = rule.onNodeWithText(str(R.string.profile_keep_editing)).performClick()

    private fun assertDialogShown() =
        rule.onNodeWithText(str(R.string.profile_discard_title)).assertIsDisplayed()

    private fun assertDialogGone() =
        rule.onNodeWithText(str(R.string.profile_discard_title)).assertDoesNotExist()

    // ---------- Scenario 1: edit, then Cancel -> Discard ----------

    @Test
    fun cancel_thenDiscard_leavesScreenWithoutSaving() {
        launch()
        fill(R.string.profile_name, "Changed Name")

        clickCancel()
        assertDialogShown()
        clickDiscard()

        assertEquals(1, cancelCount)
        assertEquals(0, saveCount)
        assertDialogGone()
    }

    @Test
    fun cancel_thenKeepEditing_staysOnScreenAndKeepsEdits() {
        launch()
        fill(R.string.profile_name, "Changed Name")

        clickCancel()
        assertDialogShown()
        clickKeepEditing()

        assertDialogGone()
        assertEquals(0, cancelCount)
        assertEquals(0, saveCount)
        field(R.string.profile_name).assertTextContains("Changed Name")
    }

    @Test
    fun cancel_withNoEdits_stillAsksBeforeLeaving() {
        launch()

        clickCancel()

        assertDialogShown()
        assertEquals(0, cancelCount)
    }

    // ---------- Scenario 2: edit, then Save ----------

    @Test
    fun editAllFields_thenSave_callsOnSaveWithNewValues() {
        launch()

        fillAll("John Smith", "john@example.com", "5875550199")
        clickSave()

        assertEquals(1, saveCount)
        assertEquals("John Smith", savedName)
        assertEquals("john@example.com", savedEmail)
        assertEquals("5875550199", savedPhone)
        assertEquals(0, cancelCount)
    }

    @Test
    fun editOneField_thenSave_keepsTheOtherFieldsUnchanged() {
        launch()

        fill(R.string.profile_name, "Only Name Changed")
        clickSave()

        assertEquals(1, saveCount)
        assertEquals("Only Name Changed", savedName)
        assertEquals("jane@example.com", savedEmail)
        assertEquals("780 555 0123", savedPhone)
    }

    @Test
    fun save_trimsSpacesAroundValues() {
        launch()

        fillAll("  John Smith  ", "  john@example.com  ", "  7805550123  ")
        clickSave()

        assertEquals(1, saveCount)
        assertEquals("John Smith", savedName)
        assertEquals("john@example.com", savedEmail)
        assertEquals("7805550123", savedPhone)
    }

    // ---------- Scenario 3: X button ----------

    @Test
    fun clickX_thenDiscard_leavesScreenWithoutSaving() {
        launch()
        fill(R.string.profile_email, "other@example.com")

        clickX()
        assertDialogShown()
        clickDiscard()

        assertEquals(1, cancelCount)
        assertEquals(0, saveCount)
    }

    @Test
    fun clickX_thenKeepEditing_staysOnScreen() {
        launch()
        fill(R.string.profile_email, "other@example.com")

        clickX()
        assertDialogShown()
        clickKeepEditing()

        assertDialogGone()
        assertEquals(0, cancelCount)
        field(R.string.profile_email).assertTextContains("other@example.com")
    }

    // ---------- Scenario 4: incomplete -> errors -> fix -> save ----------

    @Test
    fun emptyFields_save_showsAllErrorsAndDoesNotSave() {
        launch(name = "", email = "", phone = null)

        clickSave()

        rule.onNodeWithText(str(R.string.error_name_required)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.error_email_invalid)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.error_phone_invalid)).assertIsDisplayed()
        assertEquals(0, saveCount)
    }

    @Test
    fun errorsShown_thenFillEverything_errorsClearAndSaveWorks() {
        launch(name = "", email = "", phone = null)

        // First try: nothing entered
        clickSave()
        rule.onNodeWithText(str(R.string.error_name_required)).assertIsDisplayed()
        assertEquals(0, saveCount)

        // Second try: everything entered
        fillAll("Jane Doe", "jane@example.com", "7805550123")

        rule.onNodeWithText(str(R.string.error_name_required)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.error_email_invalid)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.error_phone_invalid)).assertDoesNotExist()

        clickSave()

        assertEquals(1, saveCount)
        assertEquals("Jane Doe", savedName)
        assertEquals("jane@example.com", savedEmail)
        assertEquals("7805550123", savedPhone)
    }

    @Test
    fun onlyNameEntered_save_showsEmailAndPhoneErrorsOnly() {
        launch(name = "", email = "", phone = null)

        fill(R.string.profile_name, "Jane Doe")
        clickSave()

        rule.onNodeWithText(str(R.string.error_name_required)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.error_email_invalid)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.error_phone_invalid)).assertIsDisplayed()
        assertEquals(0, saveCount)
    }

    @Test
    fun blankName_onExistingProfile_showsNameError() {
        launch()

        fill(R.string.profile_name, "   ")
        clickSave()

        rule.onNodeWithText(str(R.string.error_name_required)).assertIsDisplayed()
        assertEquals(0, saveCount)
    }

    // ---------- Scenario 5: invalid formats ----------

    @Test
    fun invalidEmail_save_showsEmailErrorAndDoesNotSave() {
        launch()

        fill(R.string.profile_email, "not-an-email")
        clickSave()

        rule.onNodeWithText(str(R.string.error_email_invalid)).assertIsDisplayed()
        assertEquals(0, saveCount)
    }

    @Test
    fun invalidPhone_letters_showsPhoneError() {
        launch()

        fill(R.string.profile_phone, "abc123")
        clickSave()

        rule.onNodeWithText(str(R.string.error_phone_invalid)).assertIsDisplayed()
        assertEquals(0, saveCount)
    }

    @Test
    fun invalidPhone_tooShort_showsPhoneError() {
        launch()

        fill(R.string.profile_phone, "12345")
        clickSave()

        rule.onNodeWithText(str(R.string.error_phone_invalid)).assertIsDisplayed()
        assertEquals(0, saveCount)
    }

    @Test
    fun invalidPhone_tooLong_showsPhoneError() {
        launch()

        fill(R.string.profile_phone, "1234567890123456")
        clickSave()

        rule.onNodeWithText(str(R.string.error_phone_invalid)).assertIsDisplayed()
        assertEquals(0, saveCount)
    }

    @Test
    fun invalidEmail_thenCorrected_saveWorks() {
        launch()

        fill(R.string.profile_email, "bad")
        clickSave()
        rule.onNodeWithText(str(R.string.error_email_invalid)).assertIsDisplayed()

        fill(R.string.profile_email, "good@example.com")
        rule.onNodeWithText(str(R.string.error_email_invalid)).assertDoesNotExist()
        clickSave()

        assertEquals(1, saveCount)
        assertEquals("good@example.com", savedEmail)
    }

    // ---------- Scenario 6: just open and click Save ----------

    @Test
    fun noChanges_save_callsOnSaveWithOriginalValues() {
        launch()

        clickSave()

        assertEquals(1, saveCount)
        assertEquals("Jane Doe", savedName)
        assertEquals("jane@example.com", savedEmail)
        assertEquals("780 555 0123", savedPhone)
    }

    // ---------- Other screen behaviour ----------

    @Test
    fun screenOpens_withoutDiscardDialogAndWithoutErrors() {
        launch()

        assertDialogGone()
        rule.onNodeWithText(str(R.string.error_name_required)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.error_email_invalid)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.error_phone_invalid)).assertDoesNotExist()
        assertEquals(0, saveCount)
        assertNull(savedName)
    }

    @Test
    fun initials_updateAsTheNameIsEdited() {
        launch()

        fill(R.string.profile_name, "Alice Brown")

        rule.onNodeWithText("AB").assertIsDisplayed()
    }

    @Test
    fun initials_showQuestionMarkWhenNameIsBlank() {
        launch()

        fill(R.string.profile_name, "")

        rule.onNodeWithText("?").assertIsDisplayed()
    }

    @Test
    fun saveFailed_showsSaveErrorMessage() {
        launch(saveFailed = true)

        rule.onNodeWithText(str(R.string.error_save_failed)).assertIsDisplayed()
    }
}