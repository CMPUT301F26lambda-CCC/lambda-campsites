/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in FakeNotificationRepository. Aarush Bhat reviewed and
 * committed it.
 */
package com.lambda.campsite.model.repository

import android.graphics.Bitmap
import java.io.IOException

/**
 * An [ImageRepository] for unit tests. It doesn't touch Storage or
 * Firestore: it just hands out IDs and can be told to fail like an offline
 * phone would.
 */
class FakeImageRepository : ImageRepository {

    private var nextId = 1

    /** Every photo passed to [upload], in order. */
    val uploaded = mutableListOf<Bitmap>()

    /** When `true`, [upload] throws as if the phone were offline. */
    var failUploads = false

    override suspend fun upload(photo: Bitmap): String {
        if (failUploads) throw IOException("Offline")
        uploaded += photo
        return "image-${nextId++}"
    }
}
