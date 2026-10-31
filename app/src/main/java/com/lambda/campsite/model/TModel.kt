/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026, following the TModel design from the CMPUT 301
 * MVC lecture. It helped with the Kotlin code, syntax and KDoc. Sashreek
 * Addanki reviewed it, ran the tests and committed it.
 */
package com.lambda.campsite.model

import com.lambda.campsite.view.TView

/**
 * Base class for every model in the app's MVC design.
 *
 * A model keeps a list of the views that show it. After every change to its
 * state it calls [notifyViews], and each registered view redraws itself in
 * [TView.update]. Based on `TModel` from the CMPUT 301 MVC lecture.
 *
 * @param M the concrete model type, so views receive that type in [TView.update].
 */
abstract class TModel<M> {

    private val views = mutableListOf<TView<M>>()

    /**
     * Registers [view] so it's updated after every change. Adding a view that
     * is already registered does nothing.
     */
    fun addView(view: TView<M>) {
        if (view !in views) {
            views.add(view)
        }
    }

    /** Stops updating [view]. Screens call this when they close. */
    fun deleteView(view: TView<M>) {
        views.remove(view)
    }

    /**
     * Tells every registered view that [model] changed. Subclasses call this,
     * passing `this`, at the end of every method that changes their state.
     */
    protected fun notifyViews(model: M) {
        // Copy the list first, so a view can call deleteView while it's being updated.
        views.toList().forEach { view -> view.update(model) }
    }
}
