/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with QR bitmap rendering, Share and Save buttons, accessibility semantics,
 * string resources and KDoc. Raghav Sethi reviewed, tested, and committed it.
 *
 * The share sheet and the MediaStore save were moved here from the controller in
 * November 2026, so the ViewModel never holds an Activity context that can leak.
 * Saving writes into the Pictures folder, where Photos finds it.
 *
 * The status bar icon styling was added in November 2026 with GLM 5.3 Flash
 * (Z.ai), through OpenCode, at Raghav Sethi's request.
 */
package com.lambda.campsite.view.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteQrCodeController
import com.lambda.campsite.controller.CampsiteQrError
import com.lambda.campsite.view.theme.StatusBarIconStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Campsite QR code display screen (US 02.02.01).
 *
 * Displays the automatically generated QR code for a campsite, allowing owners
 * to share the link or save the QR image to their device.
 *
 * @param controller the [CampsiteQrCodeController] supplying the QR bitmap and share handlers.
 * @param onBack invoked when navigating back.
 * @param modifier optional layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampsiteQrCodeScreen(
    controller: CampsiteQrCodeController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The dark green app bar runs under the clock, so the clock draws white.
    StatusBarIconStyle(darkIcons = false)

    Scaffold(
        modifier = modifier,
        containerColor = CampGreenLight,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.campsite_qr_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val campsite = state.campsite
            val campsiteName = campsite?.name ?: "Campsite"
            val campsiteId = campsite?.id ?: ""

            // Center White Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = campsiteName,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.qr_scan_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))

                    // QR Code Image Matrix
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.qrBitmap != null) {
                            Image(
                                bitmap = state.qrBitmap!!.asImageBitmap(),
                                contentDescription = "QR code for $campsiteName",
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (state.isLoading) {
                            CircularProgressIndicator(color = CampGreenDark)
                        } else {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                tint = Color.LightGray,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.qr_auto_generated_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.qr_campsite_id_format, campsiteId),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.weight(1f, fill = false))

            // Load error message
            state.error?.let { error ->
                Text(
                    text = stringResource(
                        when (error) {
                            CampsiteQrError.LOAD_FAILED -> R.string.error_campsite_load_failed
                            CampsiteQrError.NOT_FOUND -> R.string.error_campsite_not_found
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            // Action Buttons
            Button(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, campsiteName)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            context.getString(R.string.qr_share_text, campsiteName, state.qrPayload),
                        )
                    }
                    val chooser = Intent.createChooser(
                        shareIntent,
                        context.getString(R.string.qr_share_chooser_title),
                    )
                    context.startActivity(chooser)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CampGreenDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
            ) {
                Text(stringResource(R.string.action_share))
            }

            OutlinedButton(
                onClick = {
                    val bitmap = state.qrBitmap ?: return@OutlinedButton
                    scope.launch {
                        val saved = saveQrImageToPictures(context, campsiteId.ifBlank { "unknown" }, bitmap)
                        Toast.makeText(
                            context,
                            if (saved) R.string.qr_image_saved_message else R.string.qr_image_save_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
            ) {
                Text(stringResource(R.string.action_save_image))
            }
        }
    }
}

/**
 * Saves [bitmap] into the phone's Pictures folder through MediaStore, so Photos
 * and other apps can show it (US 02.02.01). Returns `false` if the phone
 * refuses, like an older Android without the storage permission.
 */
private suspend fun saveQrImageToPictures(context: Context, campsiteId: String, bitmap: Bitmap): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "campsite-$campsiteId-qr.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Lambda Campsite")
                }
            }
            val uri = context.contentResolver
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            false
        }
    }
