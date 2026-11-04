/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write these tests in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the test cases for the
 * wizard's campsites, steps, date validation and saving, and the coroutines
 * rule, following the OwnerCampsiteListControllerTest pattern.
 * Raghav Sethi reviewed, ran, and committed them.
 */
package com.lambda.campsite.controller

import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.model.repository.FakeLotteryRepository
import com.lambda.campsite.model.repository.FakeProfileRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests for [NewLotteryController]: the four-step "New lottery" wizard (US 02.04.01 to 02.04.07). */
class NewLotteryControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val aspen = Campsite(id = "c1", ownerId = "user-1", name = "Aspen Hollow", location = "Hinton, AB")
    private val cedar = Campsite(id = "c2", ownerId = "user-1", name = "Cedar Point", location = "Jasper, AB")

    /** Builds a controller whose owner's profile is "user-1" and whose campsites are [campsites]. */
    private fun newController(vararg campsites: Campsite): Pair<NewLotteryController, FakeLotteryRepository> {
        val lotteryRepository = FakeLotteryRepository(myUserId = "user-1")
        val controller = NewLotteryController(
            lotteryRepository = lotteryRepository,
            campsiteRepository = FakeCampsiteRepository(myUserId = "user-1", initial = campsites.toList()),
            profileRepository = FakeProfileRepository(
                Profile(deviceId = "user-1", name = "Owner", email = "owner@example.com"),
            ),
        )
        return controller to lotteryRepository
    }

    /** Selects the first campsite and moves to the dates step. */
    private fun toDatesStep(controller: NewLotteryController) {
        controller.onCampsiteToggled("c1")
        controller.onNext()
    }

    /** Sets the registration and booking periods step 2 asks for. */
    private fun setValidDates(controller: NewLotteryController) {
        controller.onRegistrationOpensChange(LocalDate.of(2026, 10, 6))
        controller.onRegistrationClosesChange(LocalDate.of(2026, 10, 18))
        controller.onStayStartChange(LocalDate.of(2027, 5, 15))
        controller.onStayEndChange(LocalDate.of(2027, 9, 30))
    }

    /** Steps 1 to 3 filled in, reaching the review step. */
    private fun toReviewStep(controller: NewLotteryController) {
        toDatesStep(controller)
        setValidDates(controller)
        controller.onNext()
        controller.onNameChange("Aspen Hollow summer lottery")
        controller.onNext()
    }

    @Test
    fun `the owner's campsites are the choices on step 1`() {
        val (controller, _) = newController(aspen, cedar)

        assertEquals(listOf(aspen, cedar), controller.state.value.campsites)
        assertFalse(controller.state.value.isLoadingCampsites)
    }

    @Test
    fun `toggling a campsite selects and unselects it`() {
        val (controller, _) = newController(aspen, cedar)

        controller.onCampsiteToggled("c1")
        assertEquals(listOf("c1"), controller.state.value.selectedCampsiteIds)
        controller.onCampsiteToggled("c1")
        assertTrue(controller.state.value.selectedCampsiteIds.isEmpty())
    }

    @Test
    fun `a sixth campsite is refused`() {
        val numbered = (1..6).map { n ->
            Campsite(id = "c$n", ownerId = "user-1", name = "Campsite $n", location = "loc")
        }
        val (controller, _) = newController(*numbered.toTypedArray())
        numbered.take(5).forEach { controller.onCampsiteToggled(it.id) }

        controller.onCampsiteToggled("c6")

        assertEquals(NewLotteryError.TOO_MANY_CAMPSITES, controller.state.value.error)
        assertEquals(5, controller.state.value.selectedCampsiteIds.size)
    }

    @Test
    fun `continue is refused without a selected campsite`() {
        val (controller, _) = newController(aspen)

        controller.onNext()

        assertEquals(1, controller.state.value.step)
        assertEquals(NewLotteryError.NO_CAMPSITES_SELECTED, controller.state.value.error)
    }

    @Test
    fun `continue with a campsite moves to the dates step`() {
        val (controller, _) = newController(aspen, cedar)

        controller.onCampsiteToggled("c1")
        controller.onNext()

        assertEquals(2, controller.state.value.step)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `continue from dates is refused while dates are missing`() {
        val (controller, _) = newController(aspen)
        toDatesStep(controller)

        controller.onNext()

        assertEquals(2, controller.state.value.step)
        assertEquals(NewLotteryError.MISSING_DATES, controller.state.value.error)
    }

    @Test
    fun `continue with every date moves to the settings step`() {
        val (controller, _) = newController(aspen)
        toDatesStep(controller)
        setValidDates(controller)

        controller.onNext()

        assertEquals(3, controller.state.value.step)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `continue without a name is refused`() {
        val (controller, _) = newController(aspen)
        toDatesStep(controller)
        setValidDates(controller)
        controller.onNext()

        controller.onNext()

        assertEquals(3, controller.state.value.step)
        assertEquals(NewLotteryError.MISSING_NAME, controller.state.value.error)
    }

    @Test
    fun `continue with a name moves to the review step`() {
        val (controller, _) = newController(aspen)
        toDatesStep(controller)
        setValidDates(controller)
        controller.onNext()
        controller.onNameChange("Aspen Hollow summer lottery")

        controller.onNext()

        assertEquals(4, controller.state.value.step)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `the Edit buttons on review return to earlier steps`() {
        val (controller, _) = newController(aspen)
        toReviewStep(controller)

        controller.onStepSelected(2)

        assertEquals(2, controller.state.value.step)
    }

    @Test
    fun `back moves one step and never past the first`() {
        val (controller, _) = newController(aspen)
        toDatesStep(controller)

        controller.onBack()
        assertEquals(1, controller.state.value.step)
        controller.onBack()
        assertEquals(1, controller.state.value.step)
    }

    @Test
    fun `creating the lottery saves it for the owner with a waiting-list cap`() {
        val (controller, repository) = newController(aspen, cedar)
        toReviewStep(controller)
        var created: Lottery? = null

        controller.onCreateLottery { created = it }

        val saved = repository.saved.single()
        assertEquals("user-1", saved.ownerId)
        assertEquals("Aspen Hollow summer lottery", saved.name)
        assertEquals(listOf("c1"), saved.campsiteIds)
        assertEquals(LotteryStatus.OPEN, saved.status)
        assertEquals(20, saved.numberOfWinners)
        assertEquals(150, saved.capacity)
        assertEquals(created?.id, saved.id)
    }

    @Test
    fun `creating with the limit off saves a lottery without a capacity`() {
        val (controller, repository) = newController(aspen)
        toReviewStep(controller)

        controller.onLimitWaitingListChange(false)
        controller.onCreateLottery { }

        assertNull(repository.saved.single().capacity)
    }

    @Test
    fun `creating is refused when the dates don't fit`() {
        val (controller, repository) = newController(aspen)
        toDatesStep(controller)
        // Registration closes before it opens.
        controller.onRegistrationOpensChange(LocalDate.of(2026, 10, 18))
        controller.onRegistrationClosesChange(LocalDate.of(2026, 10, 6))
        controller.onStayStartChange(LocalDate.of(2027, 5, 15))
        controller.onStayEndChange(LocalDate.of(2027, 9, 30))
        controller.onNext()
        controller.onNameChange("Aspen Hollow summer lottery")
        controller.onNext()

        controller.onCreateLottery { }

        assertEquals(NewLotteryError.INVALID_DATES, controller.state.value.error)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `a failed save shows the create failed error`() {
        val (controller, repository) = newController(aspen)
        toReviewStep(controller)
        repository.failSaves = true

        controller.onCreateLottery { }

        assertTrue(controller.state.value.saveFailed)
        assertFalse(controller.state.value.isSaving)
    }
}
