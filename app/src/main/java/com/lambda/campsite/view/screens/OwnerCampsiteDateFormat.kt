// Issue #150 review: GPT-6 (OpenAI), through Codex, assisted me with shared owner date formatting.
package com.lambda.campsite.view.screens

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Displays a season date consistently in owner forms and details. */
internal fun formatOwnerCampsiteDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
