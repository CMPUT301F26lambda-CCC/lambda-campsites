/*
 * Feature owner: Sreeman Reddy, Flow 16 (#133), November 2026.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with scanner state tests.
 * Human review is pending.
 */
package com.lambda.campsite.controller

import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.QRCode
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Flow 16 acceptance: printed QR opens one published campsite; bad codes stay in the scanner. */
class QrScanControllerTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val campsite = Campsite("site-123", "owner", "Aspen Hollow", "Alberta")
    private val payload = "${QRCode.URI_PREFIX}${campsite.id}"

    @Test fun `Flow 3 QR opens published campsite once`() {
        val controller = QrScanController(FakeCampsiteRepository(initial = listOf(campsite)))
        controller.onCodeSeen(payload)
        assertEquals(campsite.id, controller.state.value.destinationId)
        assertFalse(controller.state.value.isResolving)
        controller.onDestinationOpened()
        controller.onCodeSeen(payload)
        assertNull(controller.state.value.destinationId)
        controller.onCodeLost()
        controller.onCodeSeen(payload)
        assertEquals(campsite.id, controller.state.value.destinationId)
    }

    @Test fun `foreign or malformed QR never navigates`() {
        val controller = QrScanController(FakeCampsiteRepository(initial = listOf(campsite)))
        listOf("https://example.com/site-123", "campsite:site-123/other", "campsite:site-123?x=1")
            .forEach { code ->
                controller.onCodeSeen(code)
                assertEquals(QrScanError.INVALID_CODE, controller.state.value.error)
                assertNull(controller.state.value.destinationId)
                controller.onErrorShown()
                controller.onCodeLost()
            }
    }

    @Test fun `missing or unpublished campsite stays in scanner`() {
        listOf(emptyList(), listOf(campsite.copy(published = false))).forEach { campsites ->
            val controller = QrScanController(FakeCampsiteRepository(initial = campsites))
            controller.onCodeSeen(payload)
            assertEquals(QrScanError.CAMPSITE_UNAVAILABLE, controller.state.value.error)
            assertNull(controller.state.value.destinationId)
            assertFalse(controller.state.value.isResolving)
        }
    }

    @Test fun `lookup failure offers another scan without crashing`() {
        val base = FakeCampsiteRepository(initial = listOf(campsite))
        val repository = object : CampsiteRepository by base {
            override fun campsite(id: String): Flow<Campsite?> = flow { throw IOException("Offline") }
        }
        val controller = QrScanController(repository)
        controller.onCodeSeen(payload)
        assertEquals(QrScanError.LOOKUP_FAILED, controller.state.value.error)
        assertNull(controller.state.value.destinationId)
        controller.onErrorShown()
        controller.onCodeLost()
        controller.onCodeSeen(payload)
        assertEquals(QrScanError.LOOKUP_FAILED, controller.state.value.error)
    }
}
