/**
 * Purpose: Shared form data model and validation logic for creating and editing campsites
 * (US 02.01.01, US 02.01.03).
 * Outstanding issues: Photo uploading is deferred until ImageRepository is implemented.
 */
/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It unifies form state, field mutations, and validation rules between Add and Edit controllers.
 * Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.controller

import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.CampsiteRequiredField
import java.time.LocalDate

/** Why saving or validating a campsite form failed. */
enum class CampsiteFormError {
    /** Saving to Firestore failed (e.g. offline or permission issue). */
    SAVE_FAILED,
    /** Season close date is not strictly after season open date. */
    INVALID_SEASON_RANGE,
    /** Only one of start or end date was specified. */
    INCOMPLETE_SEASON,
}

/**
 * Shared state for campsite form inputs across both Add and Edit screens.
 *
 * @property name campsite name.
 * @property location description of location or park.
 * @property description amenities and details.
 * @property maxOccupancy maximum guests allowed (minimum 1).
 * @property latitude map pin latitude.
 * @property longitude map pin longitude.
 * @property locationPinName friendly name for the pinned location.
 * @property seasonStart opening date for campsite reservations.
 * @property seasonEnd closing date for campsite reservations.
 * @property tags discovery tags.
 * @property requiredFieldErrors missing required fields from [Campsite.validate].
 * @property seasonError specific error with the season dates, or null.
 * @property isSaving whether a save operation is in-flight.
 * @property formError high-level error why save failed, or null.
 */
data class CampsiteFormData(
    val name: String = "",
    val location: String = "",
    val description: String = "",
    val maxOccupancy: Int = 1,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationPinName: String? = null,
    val seasonStart: LocalDate? = null,
    val seasonEnd: LocalDate? = null,
    val tags: List<String> = emptyList(),
    val requiredFieldErrors: Set<CampsiteRequiredField> = emptySet(),
    val seasonError: CampsiteFormError? = null,
    val isSaving: Boolean = false,
    val formError: CampsiteFormError? = null,
) {
    /** Validates season consistency. */
    fun checkSeasonError(): CampsiteFormError? = when {
        seasonStart != null && seasonEnd != null && !seasonEnd.isAfter(seasonStart) ->
            CampsiteFormError.INVALID_SEASON_RANGE
        (seasonStart != null && seasonEnd == null) || (seasonStart == null && seasonEnd != null) ->
            CampsiteFormError.INCOMPLETE_SEASON
        else -> null
    }

    /** Returns a draft [Campsite] and its validation result. */
    fun toCampsiteDraft(id: String, ownerId: String): Pair<Campsite, Set<CampsiteRequiredField>> {
        val campsite = Campsite(
            id = id,
            ownerId = ownerId,
            name = name,
            location = location,
            description = description,
            maxOccupancy = maxOccupancy,
            latitude = latitude,
            longitude = longitude,
            tags = tags,
        )
        return campsite to campsite.validate()
    }
}
