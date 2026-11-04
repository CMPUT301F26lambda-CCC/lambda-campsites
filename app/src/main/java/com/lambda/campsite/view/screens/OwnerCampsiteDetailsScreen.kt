// Issue #150: GPT-6 (OpenAI), through Codex, assisted me with this follow-up.
/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with controller integration, TalkBack accessibility, formatted availability
 * date ranges, map preview canvas, string extraction and KDoc. Raghav Sethi reviewed,
 * tested, and committed it.
 *
 * The status bar icon styling was added in November 2026 with GLM 5.3 Flash
 * (Z.ai), through OpenCode, at Raghav Sethi's request.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerCampsiteDetailsController
import com.lambda.campsite.controller.OwnerDetailsError
import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryStatus
import com.google.firebase.Timestamp
import android.graphics.Bitmap
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Everything about one campsite for its owner (US 02.01.04).
 *
 * Takes [controller] to display live campsite details, ratings, season calendar,
 * and navigation actions to edit details, view booked dates, or view QR code.
 *
 * @param controller the [OwnerCampsiteDetailsController] managing details state.
 * @param onBack invoked when user navigates back.
 * @param onEditClick invoked to edit campsite details.
 * @param onBookedDatesClick invoked to view the calendar of booked dates.
 * @param onQrCodeClick invoked to display the campsite's QR code.
 * @param modifier optional layout modifier.
 */
@Composable
fun OwnerCampsiteDetailsScreen(
    controller: OwnerCampsiteDetailsController,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onBookedDatesClick: () -> Unit,
    onQrCodeClick: () -> Unit,
    onLotteryClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()

    // While loading there's no dark green app bar yet, so the clock stays dark.
    StatusBarIconStyle(darkIcons = state.isLoading)

    when {
        state.isLoading -> {
            Scaffold(containerColor = CampGreenLight) { padding ->
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CampGreenDark)
                }
            }
        }
        state.campsite == null -> {
            Scaffold(
                containerColor = CampGreenLight,
                topBar = {
                    @OptIn(ExperimentalMaterial3Api::class)
                    TopAppBar(
                        title = { Text(stringResource(R.string.campsite_details_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = CampGreenDark,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                        ),
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(if (state.error == OwnerDetailsError.LOAD_FAILED)
                            R.string.error_campsite_load_failed else R.string.error_campsite_not_found),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
        else -> {
            val availabilityText = state.availability?.let {
                stringResource(R.string.campsite_available_dates, formatAvailability(it))
            }
            OwnerCampsiteDetailsContent(
                campsite = state.campsite!!,
                availabilityText = availabilityText,
                lotteries = state.lotteries,
                photoUrl = state.photoUrl,
                qrBitmap = state.qrBitmap,
                onBack = onBack,
                onEditClick = onEditClick,
                onBookedDatesClick = onBookedDatesClick,
                onQrCodeClick = onQrCodeClick,
                onLotteryClick = onLotteryClick,
                modifier = modifier,
            )
        }
    }
}

/**
 * Backward-compatible stateless overload for [OwnerCampsiteDetailsScreen].
 */
@Composable
fun OwnerCampsiteDetailsScreen(
    campsite: Campsite,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onBookedDatesClick: () -> Unit,
    onQrCodeClick: () -> Unit,
    onLotteryClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    availabilityText: String? = null,
) {
    OwnerCampsiteDetailsContent(
        campsite = campsite,
        availabilityText = availabilityText,
        onBack = onBack,
        onEditClick = onEditClick,
        onBookedDatesClick = onBookedDatesClick,
        onQrCodeClick = onQrCodeClick,
        onLotteryClick = onLotteryClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerCampsiteDetailsContent(
    campsite: Campsite,
    availabilityText: String?,
    lotteries: List<Lottery> = emptyList(),
    photoUrl: String? = null,
    qrBitmap: Bitmap? = null,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onBookedDatesClick: () -> Unit,
    onQrCodeClick: () -> Unit,
    onLotteryClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The dark green app bar runs under the clock, so the clock draws white.
    StatusBarIconStyle(darkIcons = false)

    Scaffold(
        modifier = modifier,
        containerColor = CampGreenLight,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.campsite_details_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CampGreenDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Hero campfire/forest illustration banner
            if (photoUrl != null) {
                AsyncImage(photoUrl, contentDescription = stringResource(R.string.owner_campsite_photo, campsite.name),
                    modifier = Modifier.fillMaxWidth().height(232.dp), contentScale = ContentScale.Crop)
            } else {
                CampsiteHeroBanner(modifier = Modifier.fillMaxWidth().height(232.dp))
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(CampGreenLight).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = campsite.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, Modifier.size(16.dp), tint = CampGreenDark)
                    Spacer(Modifier.width(4.dp))
                    Text(campsite.location, style = MaterialTheme.typography.bodySmall)
                }

                RatingRow(campsite.averageRating, campsite.ratingCount)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_edit_details))
                    }
                    FilledTonalButton(onClick = onBookedDatesClick) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_booked_dates))
                    }
                }
                HorizontalDivider()

                if (campsite.description.isNotBlank()) {
                    Text(
                        stringResource(R.string.heading_about),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(campsite.description, style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, Modifier.size(16.dp), tint = CampGreenDark)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.guests_format, campsite.maxOccupancy),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (campsite.amenities.isNotEmpty()) {
                    Text(
                        stringResource(R.string.heading_amenities),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    ChipRow(campsite.amenities)
                }

                if (campsite.tags.isNotEmpty()) {
                    Text(
                        stringResource(R.string.section_tags),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    ChipRow(campsite.tags)
                }

                if (availabilityText != null) {
                    Text(
                        stringResource(R.string.section_availability),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, Modifier.size(16.dp), tint = CampGreenDark)
                        Spacer(Modifier.width(4.dp))
                        Text(availabilityText, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        stringResource(R.string.season_range_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (campsite.pricePerNight != null || campsite.rules.isNotBlank()) {
                    Text(stringResource(R.string.campsite_price_rules_heading),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() })
                    campsite.pricePerNight?.let {
                        Text(stringResource(R.string.price_per_night_format, it),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (campsite.rules.isNotBlank()) Text(campsite.rules, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()

                Text(stringResource(R.string.owner_lotteries_heading),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() })
                if (lotteries.isEmpty()) Text(stringResource(R.string.owner_no_lotteries),
                    style = MaterialTheme.typography.bodySmall)
                lotteries.forEach { lottery ->
                    Surface(shape = RoundedCornerShape(12.dp), color = CampGreenField,
                        modifier = Modifier.fillMaxWidth().clickable { onLotteryClick(lottery.id) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(lottery.name, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(when (lottery.statusAt(Timestamp.now())) {
                                LotteryStatus.OPEN -> R.string.lottery_status_open
                                LotteryStatus.UPCOMING -> R.string.lottery_status_upcoming
                                LotteryStatus.DRAWN -> R.string.lottery_status_drawn
                                LotteryStatus.ENDED -> R.string.lottery_status_ended
                            }),
                                style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.campsite_date_range, lottery.stayStart.toString(), lottery.stayEnd.toString()),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider()

                Text(
                    stringResource(R.string.heading_location),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CampGreenField),
                    contentAlignment = Alignment.Center,
                ) {
                    val hasPin = campsite.latitude != null && campsite.longitude != null
                    if (hasPin) {
                        MapPreviewMini(
                            latitude = campsite.latitude!!,
                            longitude = campsite.longitude!!,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            stringResource(R.string.no_map_pin_set),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(stringResource(R.string.campsite_map_caption), style = MaterialTheme.typography.bodySmall)

                Text(stringResource(R.string.campsite_qr_title), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (qrBitmap != null) Image(qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.owner_qr_preview, campsite.name),
                        modifier = Modifier.size(120.dp))
                    else Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(80.dp))
                    Text(stringResource(R.string.qr_scan_prompt), style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onQrCodeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CampGreenDark),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_view_qr_code))
                }
            }
        }
    }
}

@Composable
private fun RatingRow(average: Double?, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (average == null) {
            Text(stringResource(R.string.no_ratings_yet), style = MaterialTheme.typography.bodySmall)
        } else {
            val filled = average.roundToInt()
            repeat(5) { i ->
                Icon(
                    if (i < filled) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = Color(0xFFE0A030),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.ratings_count_format, average, count),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(items: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
    }
}

@Composable
private fun CampsiteHeroBanner(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Night sky backdrop
        drawRect(color = Color(0xFF1B2E28))

        // Mountain/tree silhouettes
        val pinePath = Path().apply {
            moveTo(0f, h)
            lineTo(w * 0.15f, h * 0.4f)
            lineTo(w * 0.3f, h)
            lineTo(w * 0.45f, h * 0.35f)
            lineTo(w * 0.65f, h)
            lineTo(w * 0.85f, h * 0.38f)
            lineTo(w, h)
            close()
        }
        drawPath(pinePath, color = Color(0xFF12221D))

        // Warm campfire glow
        drawCircle(
            color = Color(0x66FF8C00),
            radius = 35.dp.toPx(),
            center = Offset(w * 0.5f, h * 0.75f),
        )
        drawCircle(
            color = Color(0xFFFFD700),
            radius = 12.dp.toPx(),
            center = Offset(w * 0.5f, h * 0.77f),
        )
    }
}

@Composable
internal fun MapPreviewMini(latitude: Double, longitude: Double, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(color = Color(0xFFCDE8D5))
            drawCircle(color = Color(0xFFA1D6B6), radius = w * 0.4f, center = Offset(w * 0.3f, h * 0.4f))
            drawCircle(color = Color(0xFFA6DBBB), radius = w * 0.45f, center = Offset(w * 0.8f, h * 0.6f))
            val road = Path().apply {
                moveTo(0f, h * 0.5f)
                cubicTo(w * 0.3f, h * 0.4f, w * 0.7f, h * 0.6f, w, h * 0.5f)
            }
            drawPath(road, color = Color.White, style = Stroke(width = 16f, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.owner_pinned_location),
                tint = Color(0xFFC8382B), modifier = Modifier.size(32.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xCC1A382F),
            ) {
                Text(
                    text = stringResource(R.string.owner_map_coordinates,
                        abs(latitude), stringResource(if (latitude >= 0) R.string.owner_north else R.string.owner_south),
                        abs(longitude), stringResource(if (longitude >= 0) R.string.owner_east else R.string.owner_west)),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private fun formatAvailability(availability: Availability): String {
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    return "${availability.seasonStart.format(fmt)} – ${availability.seasonEnd.format(fmt)}"
}
