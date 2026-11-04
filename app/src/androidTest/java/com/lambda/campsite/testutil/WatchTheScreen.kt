/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It wrote the pause helper and KDoc.
 * Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.testutil

import android.os.SystemClock
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * How long every [watch] call holds the screen still. Half a second keeps a
 * full suite watchable while it runs; raise it (for example to 2_000L) when
 * demoing the screens on an emulator.
 */
var watchMillis: Long = 1_000L

/**
 * Holds the screen under test still for [millis] so a human watching the
 * emulator can see each step the test just took: the step the wizard moved
 * to, the row that was tapped, the error that showed up.
 *
 * It only pauses the test thread; assertions still run normally, so the tests
 * stay as strict as before.
 */
fun ComposeTestRule.watch(millis: Long = watchMillis) {
    SystemClock.sleep(millis)
}
