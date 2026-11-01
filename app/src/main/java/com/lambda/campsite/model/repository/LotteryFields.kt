/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in November 2026, following the pattern in CampsiteFields. It
 * helped with the Kotlin code, syntax and KDoc. Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryStatus
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * How a [Lottery] is stored in its Firestore document, `lotteries/{id}`.
 *
 * The ID is the document's ID, so it isn't stored as a field. The stay dates
 * are stored as text like "2027-07-01", the same as the availability
 * calendar, and the registration times as Firestore timestamps.
 *
 * It's separate from the repository so unit tests can check it without
 * Firebase. The field names must match `firestore.rules`.
 */
object LotteryFields {

    const val OWNER_ID = "ownerId"
    const val NAME = "name"
    const val CAMPSITE_IDS = "campsiteIds"
    const val REGISTRATION_OPENS = "registrationOpens"
    const val REGISTRATION_CLOSES = "registrationCloses"
    const val STAY_START = "stayStart"
    const val STAY_END = "stayEnd"
    const val NUMBER_OF_WINNERS = "numberOfWinners"
    const val CAPACITY = "capacity"
    const val STATUS = "status"
    const val ENTRY_COUNT = "entryCount"

    /**
     * The document fields for [lottery]. It leaves out [ENTRY_COUNT], because
     * only the join and leave transactions change it.
     */
    fun toMap(lottery: Lottery): Map<String, Any?> = mapOf(
        OWNER_ID to lottery.ownerId,
        NAME to lottery.name,
        CAMPSITE_IDS to lottery.campsiteIds,
        REGISTRATION_OPENS to lottery.registrationOpens,
        REGISTRATION_CLOSES to lottery.registrationCloses,
        STAY_START to lottery.stayStart.toString(),
        STAY_END to lottery.stayEnd.toString(),
        NUMBER_OF_WINNERS to lottery.numberOfWinners,
        CAPACITY to lottery.capacity,
        STATUS to lottery.status.name,
    )

    /**
     * The lottery stored in the document with ID [id] and these [fields], or
     * `null` if its registration times, its stay dates or its [STATUS] are
     * missing or can't be read. Without them the app can't tell whether
     * anyone can join. Other missing fields read as the [Lottery] defaults.
     */
    fun fromMap(id: String, fields: Map<String, Any?>): Lottery? {
        val registrationOpens = fields[REGISTRATION_OPENS] as? Timestamp ?: return null
        val registrationCloses = fields[REGISTRATION_CLOSES] as? Timestamp ?: return null
        val stayStart = date(fields[STAY_START]) ?: return null
        val stayEnd = date(fields[STAY_END]) ?: return null
        val status = (fields[STATUS] as? String)?.let(::statusOrNull) ?: return null
        val defaults = Lottery(
            id = id,
            ownerId = "",
            name = "",
            campsiteIds = emptyList(),
            registrationOpens = registrationOpens,
            registrationCloses = registrationCloses,
            stayStart = stayStart,
            stayEnd = stayEnd,
        )
        return defaults.copy(
            ownerId = fields[OWNER_ID] as? String ?: defaults.ownerId,
            name = fields[NAME] as? String ?: defaults.name,
            campsiteIds = (fields[CAMPSITE_IDS] as? List<*>)?.filterIsInstance<String>() ?: defaults.campsiteIds,
            numberOfWinners = (fields[NUMBER_OF_WINNERS] as? Number)?.toInt() ?: defaults.numberOfWinners,
            capacity = (fields[CAPACITY] as? Number)?.toInt(),
            status = status,
            entryCount = (fields[ENTRY_COUNT] as? Number)?.toInt() ?: defaults.entryCount,
        )
    }

    private fun date(value: Any?): LocalDate? =
        try {
            (value as? String)?.let(LocalDate::parse)
        } catch (e: DateTimeParseException) {
            null
        }

    private fun statusOrNull(name: String): LotteryStatus? =
        LotteryStatus.entries.firstOrNull { it.name == name }
}
