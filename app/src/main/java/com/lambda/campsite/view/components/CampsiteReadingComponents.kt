/*
 * Purpose: reusable Figma campsite reading components for entrant search/details (#154).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: rating submission belongs to its own user story.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with layout and semantics;
 * human review pending.
 */
package com.lambda.campsite.view.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import com.lambda.campsite.view.theme.*

/** Noninteractive textual status: color is supplemental, never the only signal. */
@Composable
fun CampsiteStatusChip(label: String, positive: Boolean = false) {
    Surface(color = if (positive) CampPositive else CampField, shape = RoundedCornerShape(8.dp)) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge, color = CampOnPositive)
    }
}

/** Icon and readable value. Icons are decorative because the adjacent text names the value. */
@Composable
fun CampsiteInfoRow(icon: ImageVector, value: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = CampSecondaryText)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
    }
}

/** Compact rating with a full spoken equivalent and correct singular/plural count. */
@Composable
fun CampsiteRating(average: Double?, count: Int, compact: Boolean = false) {
    val spoken = if (average == null) stringResource(R.string.no_ratings_yet)
        else pluralStringResource(R.plurals.campsite_rating_count, count, average, count)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        if (compact || average == null) Icon(if (average == null) Icons.Outlined.StarBorder else Icons.Outlined.Star,
            null, Modifier.size(if (compact) 16.dp else 20.dp), tint = if (average == null) CampSecondaryText else CampGold)
        else Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { index ->
                val star = when {
                    average >= index + 1 -> Icons.Outlined.Star
                    average >= index + 0.5 -> Icons.Outlined.StarHalf
                    else -> Icons.Outlined.StarBorder
                }
                Icon(star, null, Modifier.size(18.dp), tint = CampGold)
            }
        }
        Text(if (average == null) spoken else if (compact) stringResource(R.string.campsite_rating_compact, average, count)
            else pluralStringResource(R.plurals.campsite_rating_summary, count, average, count),
            style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
    }
}

/** Figma Campsite card: 72dp placeholder, wrapped metadata, optional live status and explicit details. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampsiteSummaryCard(name: String, location: String, maxGuests: Int, rating: Double?, ratingCount: Int,
    onDetails: () -> Unit, status: @Composable () -> Unit = {}) {
    Card(onClick = onDetails, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CampSurfaceHigh, contentColor = CampOnSurface)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = CampField, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(72.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Photo, null, tint = CampSecondaryText) }
                }
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    CampsiteInfoRow(Icons.Outlined.LocationOn, location)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CampsiteInfoRow(Icons.Outlined.Person, stringResource(R.string.campsite_guest_summary, maxGuests))
                        CampsiteRating(rating, ratingCount, compact = true)
                    }
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(vertical = 4.dp)) { status() }
                TextButton(onClick = onDetails, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.entrant_campsite_view_details))
                }
            }
        }
    }
}

/** Figma section heading; plain values/content keep it usable by both owner and entrant screens. */
@Composable
fun CampsiteReadingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = ForestGreen,
            modifier = Modifier.semantics { heading() })
        content()
    }
}

/** Noninteractive outlined tags; unlike AssistChip these do not imply an empty tap action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampsiteReadingTags(values: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> Surface(color = Mint, contentColor = CampSecondaryText,
            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CampOutline)) {
            Text(value, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
        } }
    }
}

/** Exact Figma map illustration, clearly described as a placeholder rather than a geographic map. */
@Composable
fun CampsiteMapPlaceholder() {
    Image(painterResource(R.drawable.campsite_map_placeholder),
        contentDescription = stringResource(R.string.campsite_map_placeholder_description),
        modifier = Modifier.fillMaxWidth().aspectRatio(364f / 180f), contentScale = ContentScale.Fit)
    Text(stringResource(R.string.campsite_map_caption), style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
}
