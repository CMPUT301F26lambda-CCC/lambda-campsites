
/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Campsite

/**
 * How a [Campsite] is stored in its Firestore document, `campsites/{id}`.
 *
 * The ID is the document's ID, so it isn't stored as a field. When a field is
 * missing or has the wrong type, [fromMap] uses the [Campsite] default, except
 * for [PUBLISHED].
 *
 * It's separate from [FirestoreCampsiteRepository] so unit tests can check it
 * without Firebase. The field names must match `firestore.rules`.
 */
object CampsiteFields {

    const val OWNER_ID = "ownerId"
    const val NAME = "name"
    const val LOCATION = "location"
    const val DESCRIPTION = "description"
    const val AMENITIES = "amenities"
    const val MAX_OCCUPANCY = "maxOccupancy"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val TAGS = "tags"
    const val PRICE_PER_NIGHT = "pricePerNight"
    const val RULES = "rules"
    const val PUBLISHED = "published"
    const val RATING_COUNT = "ratingCount"
    const val RATING_SUM = "ratingSum"

    /**
     * The document fields for [campsite]. It leaves out [RATING_COUNT] and
     * [RATING_SUM], because only the `updateRatingStats` Cloud Function writes
     * those.
     */
    fun toMap(campsite: Campsite): Map<String, Any?> = mapOf(
        OWNER_ID to campsite.ownerId,
        NAME to campsite.name,
        LOCATION to campsite.location,
        DESCRIPTION to campsite.description,
        AMENITIES to campsite.amenities,
        MAX_OCCUPANCY to campsite.maxOccupancy,
        LATITUDE to campsite.latitude,
        LONGITUDE to campsite.longitude,
        TAGS to campsite.tags,
        PRICE_PER_NIGHT to campsite.pricePerNight,
        RULES to campsite.rules,
        PUBLISHED to campsite.published,
    )

    /**
     * The campsite stored in the document with ID [id] and these [fields].
     * List items that aren't strings are skipped.
     */
    fun fromMap(id: String, fields: Map<String, Any?>): Campsite {
        val defaults = Campsite(id = id, ownerId = "", name = "", location = "")
        return Campsite(
            id = id,
            ownerId = fields[OWNER_ID] as? String ?: defaults.ownerId,
            name = fields[NAME] as? String ?: defaults.name,
            location = fields[LOCATION] as? String ?: defaults.location,
            description = fields[DESCRIPTION] as? String ?: defaults.description,
            amenities = stringList(fields[AMENITIES]),
            // A number comes back as a Long or a Double, depending on how it
            // was written. A whole number written from Kotlin is always a Long.
            maxOccupancy = (fields[MAX_OCCUPANCY] as? Number)?.toInt() ?: defaults.maxOccupancy,
            latitude = (fields[LATITUDE] as? Number)?.toDouble(),
            longitude = (fields[LONGITUDE] as? Number)?.toDouble(),
            tags = stringList(fields[TAGS]),
            pricePerNight = (fields[PRICE_PER_NIGHT] as? Number)?.toDouble(),
            rules = fields[RULES] as? String ?: defaults.rules,
            // firestore.rules hides a campsite with no published field, so the
            // app treats it as unpublished too.
            published = fields[PUBLISHED] as? Boolean ?: false,
            ratingCount = (fields[RATING_COUNT] as? Number)?.toInt() ?: defaults.ratingCount,
            ratingSum = (fields[RATING_SUM] as? Number)?.toInt() ?: defaults.ratingSum,
        )
    }

    private fun stringList(value: Any?): List<String> =
        (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
}