package org.trustweave.referencewallet.ui

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Live CameraX + ZXing QR viewfinder with corner reticle and flashlight torch control.
 * Requires zero Google Play Services and releases camera resources automatically on dispose.
 */
@Composable
fun QrCameraViewfinder(
    torchEnabled: Boolean,
    hintText: String,
    decodeSuccess: Boolean = false,
    onQrDecoded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(torchEnabled, boundCamera) {
        boundCamera?.cameraControl?.enableTorch(torchEnabled)
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
        val mainExecutor = ContextCompat.getMainExecutor(context)

        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    @Suppress("DEPRECATION")
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor) { imageProxy ->
                        try {
                            val plane = imageProxy.planes.firstOrNull()
                            if (plane != null) {
                                val buffer = plane.buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                val source = PlanarYUVLuminanceSource(
                                    bytes,
                                    plane.rowStride,
                                    imageProxy.height,
                                    0,
                                    0,
                                    imageProxy.width.coerceAtMost(plane.rowStride),
                                    imageProxy.height,
                                    false,
                                )
                                val bitmap = BinaryBitmap(HybridBinarizer(source))
                                val text = try {
                                    reader.decodeWithState(bitmap).text
                                } catch (_: Exception) {
                                    null
                                } finally {
                                    reader.reset()
                                }
                                if (!text.isNullOrBlank()) {
                                    mainExecutor.execute { onQrDecoded(text) }
                                }
                            }
                        } finally {
                            imageProxy.close()
                        }
                    }

                    provider.unbindAll()
                    boundCamera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    boundCamera?.cameraControl?.enableTorch(torchEnabled)
                } catch (_: Exception) {
                    // Camera unavailable on device/emulator; manual & NFC options remain usable.
                }
            },
            mainExecutor,
        )

        onDispose {
            try {
                providerFuture.get().unbindAll()
            } catch (_: Exception) {
                // ignore
            }
            executor.shutdown()
        }
    }

    Box(modifier = modifier.background(Color(0xFF0B1120))) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        // Centered corner reticle overlay
        val reticleColor = if (decodeSuccess) Color(0xFF34D399) else Color.White
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxSize = (minOf(size.width, size.height) * 0.62f).coerceAtMost(260.dp.toPx())
            val left = (size.width - boxSize) / 2f
            val top = (size.height - boxSize) / 2f
            val right = left + boxSize
            val bottom = top + boxSize
            val corner = 28.dp.toPx()
            val stroke = 4.dp.toPx()

            drawRect(color = Color(0x380B1120))

            // Top-left
            drawLine(reticleColor, Offset(left, top), Offset(left + corner, top), stroke, StrokeCap.Round)
            drawLine(reticleColor, Offset(left, top), Offset(left, top + corner), stroke, StrokeCap.Round)
            // Top-right
            drawLine(reticleColor, Offset(right - corner, top), Offset(right, top), stroke, StrokeCap.Round)
            drawLine(reticleColor, Offset(right, top), Offset(right, top + corner), stroke, StrokeCap.Round)
            // Bottom-left
            drawLine(reticleColor, Offset(left, bottom - corner), Offset(left, bottom), stroke, StrokeCap.Round)
            drawLine(reticleColor, Offset(left, bottom), Offset(left + corner, bottom), stroke, StrokeCap.Round)
            // Bottom-right
            drawLine(reticleColor, Offset(right - corner, bottom), Offset(right, bottom), stroke, StrokeCap.Round)
            drawLine(reticleColor, Offset(right, bottom - corner), Offset(right, bottom), stroke, StrokeCap.Round)
        }

        Surface(
            color = if (decodeSuccess) Color(0xE6059669) else Color(0xBA0B1120),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
        ) {
            Text(
                if (decodeSuccess) "Code recognized!" else hintText,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
