/*
 * Feature owner: Sreeman Reddy, Flow 16 (#133), November 2026.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with the Figma-aligned
 * scanner and camera-permission screens. Human review is pending.
 */
package com.lambda.campsite.view.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.QrScanController
import com.lambda.campsite.controller.QrScanError
import com.lambda.campsite.controller.QrScanUiState
import com.lambda.campsite.view.components.QrCameraPreview
import com.lambda.campsite.view.theme.CampOnSurface
import com.lambda.campsite.view.theme.CampPositive
import com.lambda.campsite.view.theme.CampSecondaryText
import com.lambda.campsite.view.theme.ForestGreen
import com.lambda.campsite.view.theme.Mint
import com.lambda.campsite.view.theme.StatusBarIconStyle

/** Requests camera access, scans Flow 3 QR codes, and opens validated campsite details. */
@Composable
fun QrScannerScreen(controller: QrScanController, onBack: () -> Unit, onCampsiteFound: (String) -> Unit) {
    val context = LocalContext.current
    val state by controller.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var permissionGranted by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var cameraUnavailable by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    LaunchedEffect(permissionGranted, requested) {
        if (!permissionGranted && !requested) {
            requested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(state.destinationId) {
        state.destinationId?.let { id ->
            controller.onDestinationOpened()
            onCampsiteFound(id)
        }
    }
    val errorMessage = state.error?.let { error -> stringResource(when (error) {
        QrScanError.INVALID_CODE, QrScanError.CAMPSITE_UNAVAILABLE -> R.string.flow16_invalid_code
        QrScanError.LOOKUP_FAILED -> R.string.flow16_lookup_failed
    }) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbar.showSnackbar(errorMessage)
            controller.onErrorShown()
        }
    }
    QrScannerContent(state, permissionGranted, cameraUnavailable,
        torchAvailable = camera?.cameraInfo?.hasFlashUnit() == true,
        torchEnabled = torchEnabled,
        onBack = onBack,
        onOpenSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")))
        },
        onTorchToggle = {
            val next = !torchEnabled
            camera?.cameraControl?.enableTorch(next)
            torchEnabled = next
        },
        snackbar = snackbar,
        cameraPreview = {
            QrCameraPreview(controller::onCodeSeen, controller::onCodeLost,
                onCameraBound = { camera = it; cameraUnavailable = false; torchEnabled = false },
                onCameraError = { cameraUnavailable = true })
        })
}

/** Full-screen Figma scanner, with a live preview replacing its fake QR. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerContent(
    state: QrScanUiState,
    permissionGranted: Boolean,
    cameraUnavailable: Boolean,
    torchAvailable: Boolean,
    torchEnabled: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onTorchToggle: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    cameraPreview: @Composable () -> Unit = {},
) {
    StatusBarIconStyle(darkIcons = false)
    if (!permissionGranted || cameraUnavailable) {
        Scaffold(containerColor = ForestGreen,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = { ScannerTopBar(onBack, ForestGreen) }) { padding ->
            Surface(Modifier.fillMaxSize().padding(padding),
                color = Mint, contentColor = CampOnSurface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Column(Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Surface(shape = CircleShape, color = CampPositive, modifier = Modifier.size(96.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.flow16_camera), null, Modifier.size(40.dp))
                        }
                    }
                    Text(stringResource(if (cameraUnavailable) R.string.flow16_camera_unavailable
                        else R.string.flow16_camera_needed),
                        modifier = Modifier.padding(top = 16.dp).semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center)
                    if (!cameraUnavailable) Text(stringResource(R.string.flow16_camera_explanation),
                        modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                        color = CampSecondaryText, style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center)
                    if (!cameraUnavailable) Button(onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)) {
                        Text(stringResource(R.string.flow16_open_settings))
                    }
                    TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.flow16_not_now))
                    }
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFF2B3833), Color(0xFF0D1412))))) {
        cameraPreview()
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0xC02B3833), Color(0x660D1412), Color(0xB30D1412)))))
        Scaffold(containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = { ScannerTopBar(onBack, Color.Transparent) },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.TopCenter) {
                    Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(56.dp)) {
                        IconButton(onClick = onTorchToggle, enabled = torchAvailable) {
                            Icon(if (torchEnabled) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                                stringResource(if (torchEnabled) R.string.flow16_torch_off
                                else R.string.flow16_torch_on), tint = Color(0xFF16241F))
                        }
                    }
                }
            }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Image(painterResource(R.drawable.flow16_brackets), null, Modifier.size(263.dp))
                if (state.isResolving) {
                    CircularProgressIndicator(Modifier.padding(top = 20.dp).size(24.dp), color = Mint)
                    Text(stringResource(R.string.flow16_checking),
                        modifier = Modifier.padding(top = 12.dp), color = Mint,
                        style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.flow16_scan_hint),
                        modifier = Modifier.padding(top = 28.dp).width(280.dp),
                        color = Color.White, style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Dark scanner app bar, matching the Figma back action and title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerTopBar(onBack: () -> Unit, containerColor: Color) {
    TopAppBar(title = { Text(stringResource(R.string.flow16_scan_title)) },
        navigationIcon = { IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_button))
        } }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor, titleContentColor = Color.White,
            navigationIconContentColor = Color.White))
}
