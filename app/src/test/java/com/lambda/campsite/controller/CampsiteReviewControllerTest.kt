/*
 * Purpose: Tests review eligibility, posting, replacement and failure recovery.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, assisted Sreeman Reddy with these
 * Flow 17 controller tests. Human review is pending.
 */
package com.lambda.campsite.controller

import com.google.firebase.Timestamp
import com.lambda.campsite.model.CampsiteReview
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.model.repository.FakeCampsiteReviewRepository
import com.lambda.campsite.model.repository.FakeReservationRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Controller tests for US 01.13.02 with fake repositories, as in the testing guide. */
class CampsiteReviewControllerTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val today = LocalDate.of(2027, 7, 5)

    private fun stay(status: ReservationStatus = ReservationStatus.CONFIRMED,
        checkout: LocalDate = today, campsiteId: String = "site-1") = Reservation(
        id = "stay-1", guestId = "entrant-1", guestName = "Jordan Lee",
        ownerId = "owner-1", campsiteId = campsiteId, campsiteName = "Aspen Hollow",
        lotteryId = "lottery-1", checkIn = checkout.minusDays(3), checkOut = checkout,
        guests = 2, rulesAgreedAt = Timestamp(1, 0), createdAt = Timestamp(1, 0),
        status = status,
    )

    private fun controller(stays: List<Reservation>, reviews: FakeCampsiteReviewRepository) =
        CampsiteReviewController("site-1", reviews,
            FakeReservationRepository(myUserId = "entrant-1", initial = stays)) { today }

    @Test fun `completed stay can post and review appears in live list`() {
        val reviews = FakeCampsiteReviewRepository()
        val control = controller(listOf(stay()), reviews)
        assertEquals("stay-1", control.state.value.eligibleReservationId)
        control.onReviewRequested()
        control.onDraftChanged("Quiet campsite and clean facilities.")
        control.onSubmit()

        assertFalse(control.state.value.sheetOpen)
        assertTrue(control.state.value.saved)
        assertEquals("Quiet campsite and clean facilities.", control.state.value.reviews.single().text)
    }

    @Test fun `future cancelled and unrelated stays cannot post`() {
        val reviews = FakeCampsiteReviewRepository()
        for (stay in listOf(stay(checkout = today.plusDays(2)),
            stay(status = ReservationStatus.CANCELLED), stay(campsiteId = "site-2"))) {
            val control = controller(listOf(stay), reviews)
            assertNull(control.state.value.eligibleReservationId)
            control.onReviewRequested()
            assertFalse(control.state.value.sheetOpen)
        }
    }

    @Test fun `empty and long reviews are refused before saving`() {
        val control = controller(listOf(stay()), FakeCampsiteReviewRepository())
        control.onReviewRequested()
        control.onSubmit()
        assertEquals(ReviewTextError.EMPTY, control.state.value.textError)
        control.onDraftChanged("x".repeat(1001))
        control.onSubmit()
        assertEquals(ReviewTextError.TOO_LONG, control.state.value.textError)
        assertTrue(control.state.value.reviews.isEmpty())
    }

    @Test fun `failed post keeps draft and retry succeeds once`() {
        val reviews = FakeCampsiteReviewRepository().apply { failSaves = true }
        val control = controller(listOf(stay()), reviews)
        control.onReviewRequested()
        control.onDraftChanged("Great hiking trails")
        control.onSubmit()
        assertTrue(control.state.value.sheetOpen)
        assertTrue(control.state.value.saveFailed)
        assertEquals("Great hiking trails", control.state.value.draft)

        reviews.failSaves = false
        control.onSubmit()
        assertEquals(1, control.state.value.reviews.size)
        assertFalse(control.state.value.saveFailed)
    }

    @Test fun `posting again replaces this entrants earlier review`() {
        val reviews = FakeCampsiteReviewRepository()
        val control = controller(listOf(stay()), reviews)
        control.onReviewRequested()
        control.onDraftChanged("First visit")
        control.onSubmit()
        control.onReviewRequested()
        assertEquals("First visit", control.state.value.draft)
        control.onDraftChanged("Second visit was even better")
        control.onSubmit()
        assertEquals(1, control.state.value.reviews.size)
        assertEquals("Second visit was even better", control.state.value.myReview?.text)
    }

    @Test fun `another entrants review appears without reopening the screen`() {
        val reviews = FakeCampsiteReviewRepository()
        val control = controller(emptyList(), reviews)
        assertTrue(control.state.value.reviews.isEmpty())
        reviews.changeElsewhere("site-1", CampsiteReview("entrant-2", "stay-2",
            "Clear night sky", Timestamp(2, 0)))
        assertEquals("Clear night sky", control.state.value.reviews.single().text)
    }
}
