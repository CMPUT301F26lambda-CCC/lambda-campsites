/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Campsite
import kotlinx.coroutines.flow.Flow

/**
 * Loads and saves campsites.
 *
 * Controllers depend on this interface, never on a particular version of it.
 * The app uses [FirestoreCampsiteRepository], and unit tests use
 * `FakeCampsiteRepository`.
 */
interface CampsiteRepository {

    /**
     * Every published campsite, in no particular order, sent again whenever one
     * changes. Search filters this list in the app, since Firestore can't search
     * text (US 01.01.01).
     */
    fun publishedCampsites(): Flow<List<Campsite>>

    /**
     * The campsites this device's profile owns, published or not, in no
     * particular order, sent again whenever one changes (US 02.03.01).
     */
    fun myCampsites(): Flow<List<Campsite>>

    /**
     * The campsite with this [id], sent again whenever it changes. Sends `null`
     * if there's no such campsite, like when a QR code for a deleted campsite is
     * scanned (US 01.12.01).
     */
    fun campsite(id: String): Flow<Campsite?>

    /** A new, unused ID for a campsite being registered (US 02.01.01). */
    fun newCampsiteId(): String

    /**
     * Saves [campsite], creating it or replacing its details. The stored rating
     * totals stay as they are.
     *
     * @throws Exception if the campsite can't be saved, for example when this
     *   device's profile isn't an owner.
     */
    suspend fun save(campsite: Campsite)
}