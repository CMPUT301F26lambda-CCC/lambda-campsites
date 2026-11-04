/*
 * Feature owner: Sreeman Reddy, Flow 16 (#133), November 2026.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with the lifecycle-bound
 * CameraX preview and QR frame analysis. Human review is pending.
 */
package com.lambda.campsite.view.components

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lambda.campsite.model.QrFrameDecoder
import java.util.concurrent.Executors

/** Live back-camera preview and QR analysis, owned by the scanner's lifecycle. */
@Composable
fun QrCameraPreview(
    onCodeSeen: (String) -> Unit,
    onCodeLost: () -> Unit,
    onCameraBound: (Camera) -> Unit,
    onCameraError: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    DisposableEffect(lifecycleOwner, previewView) {
        val analyzerExecutor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        var blankFrames = 0

        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val ready = providerFuture.get()
                val previewUseCase = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysisUseCase = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageRotationEnabled(true)
                    .build()
                analysisUseCase.setAnalyzer(analyzerExecutor) { frame ->
                    val payload = try {
                        val plane = frame.planes[0]
                        QrFrameDecoder.decode(plane.buffer, frame.width, frame.height,
                            plane.rowStride, plane.pixelStride)
                    } finally {
                        frame.close()
                    }
                    if (payload != null) {
                        blankFrames = 0
                        mainExecutor.execute { if (!disposed) onCodeSeen(payload) }
                    } else if (++blankFrames == 6) {
                        mainExecutor.execute { if (!disposed) onCodeLost() }
                    }
                }
                ready.unbindAll()
                val camera = ready.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                    previewUseCase, analysisUseCase)
                provider = ready
                preview = previewUseCase
                analysis = analysisUseCase
                onCameraBound(camera)
            } catch (_: Exception) {
                if (!disposed) onCameraError()
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            val boundPreview = preview
            val boundAnalysis = analysis
            if (boundPreview != null && boundAnalysis != null) {
                provider?.unbind(boundPreview, boundAnalysis)
            }
            analyzerExecutor.shutdown()
        }
    }
}
