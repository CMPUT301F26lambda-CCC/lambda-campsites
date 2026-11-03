/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write these tests in November 2026.
 * It helped with test cases for required field validation, updating form fields,
 * saving with the signed-in user as owner, saving seasons, duplicate-save and
 * retry protection, and offline failure handling. Raghav Sethi reviewed, ran,
 * and committed them.
 */
package com.lambda.campsite.controller

import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.CampsiteRequiredField
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Role
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.model.repository.FakeProfileRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests for [OwnerCampsiteAddController]: validation and registering a campsite (US 02.01.01, US 02.01.02). */
class OwnerCampsiteAddControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** The signed-in profile. Its deviceId is what `request.auth.uid` matches in the rules. */
    private val profile = Profile(
        deviceId = "owner-1",
        name = "Owner One",
        email = "owner-1@example.com",
        roles = setOf(Role.ENTRANT, Role.OWNER),
    )

    /** A [FakeCampsiteRepository] whose [save] waits until the test passes the gate. */
    private class GatedSaveRepository(private val delegate: FakeCampsiteRepository) :
        CampsiteRepository by delegate {

        val gate = CompletableDeferred<Unit>()

        override suspend fun save(campsite: Campsite) {
            gate.await()
            delegate.save(campsite)
        }
    }

    /** A [FakeCampsiteRepository] whose first [setSeason] fails, like an offline phone. */
    private class FirstSeasonFailsRepository(private val delegate: FakeCampsiteRepository) :
        CampsiteRepository by delegate {

        private var failedOnce = false

        override suspend fun setSeason(campsiteId: String, seasonStart: LocalDate, seasonEnd: LocalDate) {
            if (!failedOnce) {
                failedOnce = true
                throw IOException("Offline")
            }
            delegate.setSeason(campsiteId, seasonStart, seasonEnd)
        }
    }

    /** Fills the register form with valid details, ready to save. */
    private fun fillValidDraft(controller: OwnerCampsiteAddController) {
        controller.onNameChange("Pine Ridge Campsite")
        controller.onLocationChange("Kananaskis Country, AB")
        controller.onDescriptionChange("Quiet lakeside setting.")
        controller.onOccupancyChange(4)
        controller.onLocationPinSet(51.0, -115.0, "Kananaskis")
        controller.onSeasonChange(LocalDate.of(2027, 5, 1), LocalDate.of(2027, 10, 15))
        controller.onAddTag("lakeside")
    }

    @Test
    fun `initial state has a blank form that is not saving`() {
        val repository = FakeCampsiteRepository()
        val controller = OwnerCampsiteAddController(repository, FakeProfileRepository(profile))

        val form = controller.state.value.form
        assertEquals("", form.name)
        assertEquals("", form.location)
        assertEquals(1, form.maxOccupancy)
        assertTrue(form.requiredFieldErrors.isEmpty())
        assertFalse(form.isSaving)
        assertNull(form.formError)
        assertNull(controller.state.value.generatedId)
    }

    @Test
    fun `saving a blank campsite populates required field errors and halts save`() {
        val repository = FakeCampsiteRepository()
        val controller = OwnerCampsiteAddController(repository, FakeProfileRepository(profile))

        var savedCampsite: Campsite? = null
        controller.onSave { savedCampsite = it }

        assertNull(savedCampsite)
        assertTrue(repository.saved.isEmpty())
        val form = controller.state.value.form
        assertTrue(CampsiteRequiredField.NAME in form.requiredFieldErrors)
        assertTrue(CampsiteRequiredField.LOCATION in form.requiredFieldErrors)
    }

    @Test
    fun `saving sets ownerId to the signed-in user so it shows in My campsites`() {
        val repository = FakeCampsiteRepository()
        val controller = OwnerCampsiteAddController(repository, FakeProfileRepository(profile))
        fillValidDraft(controller)

        var savedCampsite: Campsite? = null
        controller.onSave { savedCampsite = it }

        assertNotNull(savedCampsite)
        val stored = repository.saved.last()
        assertEquals("owner-1", stored.ownerId)
        assertEquals("owner-1", savedCampsite!!.ownerId)
        // The owner's My campsites query matches campsites with this ownerId.
        val mine = runBlocking { repository.myCampsites().first() }
        assertEquals(listOf(stored), mine)
    }

    @Test
    fun `saving a valid campsite stores its details, generated ID and season`() {
        val repository = FakeCampsiteRepository()
        val controller = OwnerCampsiteAddController(repository, FakeProfileRepository(profile))
        fillValidDraft(controller)

        var savedCampsite: Campsite? = null
        controller.onSave { savedCampsite = it }

        assertNotNull(savedCampsite)
        assertEquals(1, repository.saved.size)
        val stored = repository.saved.first()
        assertEquals("Pine Ridge Campsite", stored.name)
        assertEquals("Kananaskis Country, AB", stored.location)
        assertEquals(4, stored.maxOccupancy)
        assertEquals(listOf("lakeside"), stored.tags)
        assertEquals(51.0, stored.latitude!!, 0.0)
        assertEquals("campsite-1", stored.id)
        val calendar = runBlocking { repository.availability(stored.id).first() }
        assertNotNull(calendar)
        assertEquals(LocalDate.of(2027, 5, 1), calendar!!.seasonStart)
        assertEquals(LocalDate.of(2027, 10, 15), calendar.seasonEnd)
        assertFalse(controller.state.value.form.isSaving)
    }

    @Test
    fun `the register form is empty again after a successful save`() {
        val repository = FakeCampsiteRepository()
        val controller = OwnerCampsiteAddController(repository, FakeProfileRepository(profile))
        fillValidDraft(controller)

        controller.onSave { }

        val form = controller.state.value.form
        assertEquals("", form.name)
        assertEquals("", form.location)
        assertEquals("", form.description)
        assertEquals(1, form.maxOccupancy)
        assertNull(form.latitude)
        assertNull(form.longitude)
        assertNull(form.locationPinName)
        assertTrue(form.tags.isEmpty())
        assertTrue(form.requiredFieldErrors.isEmpty())
        assertNull(form.seasonError)
        assertNull(form.formError)
        assertFalse(form.isSaving)
        assertNull(controller.state.value.generatedId)
    }

    @Test
    fun `a double tap while saving registers only one campsite`() {
        val fake = FakeCampsiteRepository()
        val repository = GatedSaveRepository(fake)
        val controller = OwnerCampsiteAddController(repository, FakeProfileRepository(profile))
        fillValidDraft(controller)

        controller.onSave { }
        // The first save is still waiting at the gate, so this tap must do nothing.
        controller.onSave { }

        assertFalse(repository.gate.isCompleted)
        assertTrue(controller.state.value.form.isSaving)
        assertEquals(0, fake.saved.size)

        repository.gate.complete(Unit)
        assertEquals(1, fake.saved.size)
    }

    @Test
    fun `a retry after a failed season save reuses the same campsite ID`() {
        val fake = FakeCampsiteRepository()
        val repository = FirstSeasonFailsRepository(fake)
        val controller = OwnerCampsiteAddController(repository, FakeProfileRepository(profile))
        fillValidDraft(controller)

        controller.onSave { }
        assertEquals(
            CampsiteFormError.SAVE_FAILED,
            controller.state.value.form.formError,
        )
        assertEquals(1, fake.saved.size)
        val firstId = fake.saved.single().id

        // Save again: same campsite, kept ID, season saved this time.
        controller.onSave { }
        assertEquals(2, fake.saved.size)
        assertEquals(firstId, fake.saved.last().id)
        assertNull(controller.state.value.form.formError)
        assertFalse(controller.state.value.form.isSaving)
    }

    @Test
    fun `an offline repository records SAVE_FAILED and keeps the generated ID`() {
        val fake = FakeCampsiteRepository().apply { failSaves = true }
        val controller = OwnerCampsiteAddController(fake, FakeProfileRepository(profile))
        fillValidDraft(controller)

        var savedCampsite: Campsite? = null
        controller.onSave { savedCampsite = it }

        assertNull(savedCampsite)
        assertEquals(CampsiteFormError.SAVE_FAILED, controller.state.value.form.formError)
        assertFalse(controller.state.value.form.isSaving)
        // The next Save retries the same campsite instead of making a second one.
        assertNotNull(controller.state.value.generatedId)
    }

    @Test
    fun `tag addition and removal update draft state correctly`() {
        val repository = FakeCampsiteRepository()
        val controller = OwnerCampsiteAddController(repository, FakeProfileRepository(profile))

        controller.onAddTag("forest")
        controller.onAddTag("lake")
        assertEquals(listOf("forest", "lake"), controller.state.value.form.tags)

        controller.onRemoveTag("forest")
        assertEquals(listOf("lake"), controller.state.value.form.tags)
    }
}
