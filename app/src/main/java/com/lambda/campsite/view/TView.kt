/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026, following the TView design from the CMPUT 301
 * MVC lecture. It helped with the Kotlin code, syntax and KDoc. Sashreek
 * Addanki reviewed it and committed it.
 */
package com.lambda.campsite.view

import com.lambda.campsite.model.TModel

/**
 * A view that shows a model and redraws when the model changes.
 *
 * Each screen implements this for the model it shows and registers itself
 * with [TModel.addView]. Based on `TView` from the CMPUT 301 MVC lecture.
 *
 * @param M the type of model this view shows.
 */
interface TView<M> {

    /**
     * Called by the model after it changes. Copy what the screen shows from
     * [model] into the screen's own Compose state, and Compose redraws the
     * parts that changed.
     */
    fun update(model: M)
}
