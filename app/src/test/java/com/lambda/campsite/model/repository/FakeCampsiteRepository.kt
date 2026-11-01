/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * It also helped add the availability calendar in November 2026. Sashreek
 * Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A [CampsiteRepository] for unit tests. It keeps campsites and calendars in
 * memory, records every save, and can be told to fail like an offline phone
 * would.
 *
 * @param myUserId the user ID [myCampsites] treats as this device's.
 * @param initial the campsites to start with.
 * @param initialCalendars the calendars to start with, by campsite ID.
 */
class FakeCampsiteRepository(
    private val myUserId: String = "owner-1",
    initial: List<Campsite> = emptyList(),
    initialCalendars: Map<String, Availability> = emptyMap(),
) : CampsiteRepository {

    private val stored = MutableStateFlow(initial.associateBy { it.id })
    private val calendars = MutableStateFlow(initialCalendars)
    private var nextId = 1

    /** Every campsite passed to [save], in order. */
    val saved = mutableListOf<Campsite>()

    /** When `true`, [save] and [setSeason] throw as if the phone were offline. */
    var failSaves = false

    override fun publishedCampsites(): Flow<List<Campsite>> =
        stored.map { campsites -> campsites.values.filter { it.published } }

    override fun myCampsites(): Flow<List<Campsite>> =
        stored.map { campsites -> campsites.values.filter { it.ownerId == myUserId } }

    override fun campsite(id: String): Flow<Campsite?> = stored.map { it[id] }

    override fun newCampsiteId(): String = "campsite-${nextId++}"

    override suspend fun save(campsite: Campsite) {
        if (failSaves) throw IOException("Offline")
        saved += campsite
        stored.update { it + (campsite.id to campsite) }
    }

    override fun availability(campsiteId: String): Flow<Availability?> = calendars.map { it[campsiteId] }

    override suspend fun setSeason(campsiteId: String, seasonStart: LocalDate, seasonEnd: LocalDate) {
        require(seasonEnd.isAfter(seasonStart)) { "The season has to close after it opens." }
        if (failSaves) throw IOException("Offline")
        calendars.update { current ->
            val booked = current[campsiteId]?.bookedNights.orEmpty()
            current + (campsiteId to Availability(seasonStart, seasonEnd, booked))
        }
    }

    /** Changes or adds a campsite without a save, like its owner editing it on another phone. */
    fun changeElsewhere(campsite: Campsite) {
        stored.update { it + (campsite.id to campsite) }
    }

    /** Changes or adds a calendar without a save, like a guest booking nights on another phone. */
    fun changeCalendarElsewhere(campsiteId: String, availability: Availability) {
        calendars.update { it + (campsiteId to availability) }
    }
}
