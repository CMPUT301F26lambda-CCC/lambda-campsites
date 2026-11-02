/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026, following the Campsite class in the team's class
 * diagram. It helped with the Kotlin code, syntax and KDoc. Sashreek Addanki
 * reviewed it and committed it.
 *
 * The imageId field was added by Aarush Bhat with the assistance of Claude
 * Sonnet 5 (Anthropic), through Claude Code, in November 2026. Aarush Bhat
 * reviewed and committed it.
 */
package com.lambda.campsite.model

/**
 * A campsite an owner lists in the app (US 02.01.01, 02.01.02).
 *
 * Like [Profile], a campsite never changes in place. Make an edited copy with
 * `copy()` and save it through the repository.
 *
 * The class diagram's `availability` attribute arrives with the Availability
 * class. Its `owner: Profile` is [ownerId] here, because profiles are
 * private: the security rules don't let other users load the owner's
 * profile.
 *
 * @property id the campsite's document ID. It never changes, so a printed QR
 *   code keeps working after the owner edits the campsite (US 02.01.03).
 * @property ownerId the Firebase Auth user ID of the owner who listed it.
 * @property name the campsite's name. Required.
 * @property location where it is, in the owner's words, like "Elk Island
 *   National Park, AB". Required.
 * @property description what the owner wrote about it.
 * @property amenities what it has, like "Fire pit" or "Showers".
 * @property maxOccupancy the most guests allowed at once. A whole number above 0.
 * @property latitude the map pin's latitude, or `null` if the owner didn't drop a pin.
 * @property longitude the map pin's longitude, or `null` if the owner didn't drop a pin.
 * @property tags words that help entrants find it, like "lakeside".
 * @property pricePerNight the price of one night in dollars, or `null` if the
 *   owner didn't give one. It's only shown, since the app takes no payments.
 * @property rules the campsite's rules, including its cancellation policy
 *   (US 02.11.01).
 * @property published whether entrants can find it. An admin unpublishes the
 *   campsites of an owner they remove (US 03.07.01).
 * @property ratingCount how many entrants have rated it. Only a Cloud Function
 *   changes it.
 * @property ratingSum all its star ratings added together. Only a Cloud
 *   Function changes it.
 * @property imageId the ID of its photo, or `null` if the owner didn't upload
 *   one (US 02.01.02).
 */
data class Campsite(
    val id: String,
    val ownerId: String,
    val name: String,
    val location: String,
    val description: String = "",
    val amenities: List<String> = emptyList(),
    val maxOccupancy: Int = 1,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val tags: List<String> = emptyList(),
    val pricePerNight: Double? = null,
    val rules: String = "",
    val published: Boolean = true,
    val ratingCount: Int = 0,
    val ratingSum: Int = 0,
    override val imageId: String? = null,
) : ImageOwner {

    /** The average star rating, or `null` if nobody has rated it yet (US 01.01.04, 02.01.04). */
    val averageRating: Double?
        get() = if (ratingCount == 0) null else ratingSum.toDouble() / ratingCount
}