/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026, following the main dispatcher rule from the
 * Android testing docs. It helped with the coroutines test code, syntax and
 * KDoc. Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Lets unit tests run controllers, which are ViewModels.
 *
 * A ViewModel runs its work on Android's main thread, which plain unit tests
 * don't have. This rule swaps in a test dispatcher before each test and puts
 * things back afterwards. With the default dispatcher, a controller's work
 * finishes before its method returns, so tests can check the state straight
 * away.
 *
 * Add it to a controller test with `@get:Rule val mainDispatcherRule = MainDispatcherRule()`.
 *
 * @property testDispatcher the dispatcher used as the main thread during the test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
