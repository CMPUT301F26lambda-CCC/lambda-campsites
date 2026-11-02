/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with in-memory sample data matching the Figma mockups, reactive Flow setup,
 * campsite saving and season updating, syntax and KDoc. Raghav Sethi reviewed,
 * tested, and committed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * An in-memory [CampsiteRepository] populated with realistic sample data for testing
 * and local UI development before connecting to live Firestore (US 02.01 – 02.03).
 */
class InMemoryCampsiteRepository : CampsiteRepository {

    private val idCounter = AtomicInteger(3)

    private val initialCampsites = listOf(
        Campsite(
            id = "demo-aspen-hollow-001",
            ownerId = "owner-demo",
            name = "Aspen Hollow Campsite",
            location = "Hinton – Grande Cache, AB",
            description = "A quiet forest site with an outdoor cookhouse and an on-site tennis court. Every site has a fire pit and a picnic table.",
            amenities = listOf("Cookhouse", "Tennis court", "Forest", "Fire pit"),
            maxOccupancy = 8,
            latitude = 53.4116,
            longitude = -117.5739,
            tags = listOf("Cookhouse", "Tennis court", "Forest", "Fire pit"),
            pricePerNight = 45.0,
            rules = "Quiet hours after 11 PM. Keep fires inside designated pits.",
            published = true,
            ratingCount = 12,
            ratingSum = 55, // 55 / 12 = ~4.6 average rating
        ),
        Campsite(
            id = "demo-cedar-point-002",
            ownerId = "owner-demo",
            name = "Cedar Point Campsite",
            location = "Jasper National Park, AB",
            description = "Lakeside camping with boat launch and scenic mountain trail access.",
            amenities = listOf("Boat launch", "Showers", "Fire pit"),
            maxOccupancy = 6,
            latitude = 52.8734,
            longitude = -118.0814,
            tags = listOf("lakeside", "jasper", "boating"),
            pricePerNight = 50.0,
            rules = "Pack in, pack out. Store all food in bear-proof bins.",
            published = true,
            ratingCount = 0,
            ratingSum = 0,
        ),
    )

    private val initialCalendars = mapOf(
        "demo-aspen-hollow-001" to Availability(
            seasonStart = LocalDate.of(2027, 5, 1),
            seasonEnd = LocalDate.of(2027, 10, 12),
        ),
        "demo-cedar-point-002" to Availability(
            seasonStart = LocalDate.of(2027, 6, 1),
            seasonEnd = LocalDate.of(2027, 9, 30),
        ),
    )

    private val campsitesFlow = MutableStateFlow(initialCampsites.associateBy { it.id })
    private val calendarsFlow = MutableStateFlow(initialCalendars)

    override fun publishedCampsites(): Flow<List<Campsite>> =
        campsitesFlow.map { it.values.filter { campsite -> campsite.published } }

    override fun myCampsites(): Flow<List<Campsite>> =
        campsitesFlow.map { it.values.toList() }

    override fun campsite(id: String): Flow<Campsite?> =
        campsitesFlow.map { it[id] }

    override fun newCampsiteId(): String = "demo-campsite-${idCounter.getAndIncrement()}"

    override suspend fun save(campsite: Campsite) {
        campsitesFlow.update { current ->
            current + (campsite.id to campsite)
        }
    }

    override fun availability(campsiteId: String): Flow<Availability?> =
        calendarsFlow.map { it[campsiteId] }

    override suspend fun setSeason(campsiteId: String, seasonStart: LocalDate, seasonEnd: LocalDate) {
        require(seasonEnd.isAfter(seasonStart)) { "The season has to close after it opens." }
        calendarsFlow.update { current ->
            val booked = current[campsiteId]?.bookedNights.orEmpty()
            current + (campsiteId to Availability(seasonStart, seasonEnd, booked))
        }
    }
}
