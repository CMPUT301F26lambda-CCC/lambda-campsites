/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in October 2026. It helped with the test cases and the JUnit and
 * Kotlin syntax. Sashreek Addanki reviewed them, ran them and committed them.
 */
package com.lambda.campsite.model

import com.lambda.campsite.view.TView
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for [TModel]: registering, removing and updating views. */
class TModelTest {

    /** A small model with one counter, used only by these tests. */
    private class CounterModel : TModel<CounterModel>() {
        var count = 0
            private set

        fun increment() {
            count++
            notifyViews(this)
        }
    }

    /** A view that records every count it was shown. */
    private class RecordingView : TView<CounterModel> {
        val seen = mutableListOf<Int>()

        override fun update(model: CounterModel) {
            seen.add(model.count)
        }
    }

    @Test
    fun `registered view is updated after a change`() {
        val model = CounterModel()
        val view = RecordingView()
        model.addView(view)

        model.increment()

        assertEquals(listOf(1), view.seen)
    }

    @Test
    fun `every registered view is updated`() {
        val model = CounterModel()
        val first = RecordingView()
        val second = RecordingView()
        model.addView(first)
        model.addView(second)

        model.increment()

        assertEquals(listOf(1), first.seen)
        assertEquals(listOf(1), second.seen)
    }

    @Test
    fun `adding the same view twice updates it once`() {
        val model = CounterModel()
        val view = RecordingView()
        model.addView(view)
        model.addView(view)

        model.increment()

        assertEquals(listOf(1), view.seen)
    }

    @Test
    fun `deleted view is no longer updated`() {
        val model = CounterModel()
        val view = RecordingView()
        model.addView(view)
        model.increment()

        model.deleteView(view)
        model.increment()

        assertEquals(listOf(1), view.seen)
    }

    @Test
    fun `view can remove itself while being updated`() {
        val model = CounterModel()
        val view = object : TView<CounterModel> {
            var updates = 0

            override fun update(model: CounterModel) {
                updates++
                model.deleteView(this)
            }
        }
        model.addView(view)

        model.increment()
        model.increment()

        assertEquals(1, view.updates)
    }
}
