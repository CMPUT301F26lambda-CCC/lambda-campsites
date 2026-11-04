/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the in-memory fakes for
 * the lottery screens' UI tests, following the shape of the fake repositories
 * in the unit-test source set. Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.testutil

import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.LotteryRepository
import com.lambda.campsite.model.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A [CampsiteRepository] for the screens' UI tests. The unit-test source set
 * has its own [com.lambda.campsite.model.repository.FakeCampsiteRepository],
 * which androidTest can't reach, so the UI tests get one of their own.
 *
 * @param initial the campsites to start with.
 */
class ScreenFakeCampsites(initial: List<Campsite> = emptyList()) : CampsiteRepository {

    private val stored = MutableStateFlow(initial.associateBy { it.id })

    override fun publishedCampsites(): Flow<List<Campsite>> = stored.map { it.values.filter { c -> c.published } }

    override fun myCampsites(): Flow<List<Campsite>> = stored.map { it.values.toList() }

    override fun campsite(id: String): Flow<Campsite?> = stored.map { it[id] }

    override fun newCampsiteId(): String = "campsite-${stored.value.size + 1}"

    override suspend fun save(campsite: Campsite) {
        stored.update { it + (campsite.id to campsite) }
    }

    override fun availability(campsiteId: String): Flow<Availability?> = stored.map { null }

    override suspend fun setSeason(campsiteId: String, seasonStart: java.time.LocalDate, seasonEnd: java.time.LocalDate) {
        // No lottery screen needs a campsite's calendar. If one ever does,
        // keep the booked nights here the way the unit-test fake does.
    }
}

/**
 * A [LotteryRepository] for the screens' UI tests. It keeps lotteries in
 * memory and records every save; joining and leaving are left empty, since no
 * screen test exercises them.
 *
 * @param initial the lotteries to start with.
 * @param initialEntries the waiting-list entries to start with.
 */
class ScreenFakeLotteries(
    initial: List<Lottery> = emptyList(),
    initialEntries: List<LotteryEntry> = emptyList(),
) : LotteryRepository {

    private val stored = MutableStateFlow(initial.associateBy { it.id })
    private val entries = MutableStateFlow(initialEntries)
    private var nextId = 1

    /** Every lottery passed to [save], in order. */
    val saved = mutableListOf<Lottery>()

    override fun lottery(id: String): Flow<Lottery?> = stored.map { it[id] }

    override fun lotteriesFor(campsiteId: String): Flow<List<Lottery>> =
        stored.map { lotteries -> lotteries.values.filter { campsiteId in it.campsiteIds } }

    override fun myLotteries(): Flow<List<Lottery>> = stored.map { it.values.toList() }

    override fun newLotteryId(): String = "lottery-${nextId++}"

    override suspend fun save(lottery: Lottery) {
        saved += lottery
        stored.update { it + (lottery.id to lottery) }
    }

    override fun waitingList(lotteryId: String): Flow<List<LotteryEntry>> =
        entries.map { all -> all.filter { it.lotteryId == lotteryId }.sortedBy { it.joinedAt } }

    override fun myEntries(): Flow<List<LotteryEntry>> = entries.map { it }

    override suspend fun join(lotteryId: String) {
        // No lottery screen test joins a waiting list.
    }

    override suspend fun leave(lotteryId: String) {
        // No lottery screen test leaves a waiting list.
    }
}

/**
 * A [ProfileRepository] for the screens' UI tests. It hands every controller
 * the same owner profile, whose device ID is "user-1".
 *
 * @param initial the profile to hand out.
 */
class ScreenFakeProfile(
    initial: Profile = Profile(deviceId = "user-1", name = "Owner", email = "owner@example.com"),
) : ProfileRepository {

    private val stored = MutableStateFlow(initial)

    override fun currentProfile(): Flow<Profile> = stored

    override suspend fun save(profile: Profile) {
        stored.value = profile
    }

    override suspend fun savePushToken(token: String) {
        // No lottery screen test changes a push token; check the profile's
        // test fakes for how a real fake would store it.
    }
}
