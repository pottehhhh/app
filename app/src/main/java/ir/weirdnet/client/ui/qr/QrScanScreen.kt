package ir.weirdnet.client.ui.qr

import android.Manifest
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import ir.weirdnet.client.core.parser.ConfigParser
import ir.weirdnet.client.ui.theme.BrandCrimson
import ir.weirdnet.client.ui.theme.BrandIceBlue

/**
 * QR scanning requirement #15: camera preview, scanning frame, flash toggle,
 * cancel button, automatic detection, and immediate validation feedback.
 * Camera permission (requirement #11) is only requested when this screen opens,
 * never eagerly at app launch.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrScanScreen(
    onPayloadScanned: (String) -> Unit,
    onCancel: () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreviewWithScanning(onPayloadScanned = onPayloadScanned)
            ScannerOverlay()
        } else {
            PermissionDeniedContent(
                permanentlyDenied = !cameraPermissionState.status.shouldShowRationale,
                onCancel = onCancel
            )
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
        }
    }
}

@Composable
private fun CameraPreviewWithScanning(onPayloadScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var torchEnabled by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var hasHandledResult by remember { mutableStateOf(false) }
    // Hoisted so DisposableEffect below can release it when this screen is left --
    // bindToLifecycle() ties use-case teardown to the *Activity's* lifecycle, not
    // this composable's, so without this the camera and analyzer would otherwise
    // keep running (and the camera indicator light stay on) after navigating away.
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderRef?.unbindAll()
            scanner.close()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                cameraProviderRef = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && !hasHandledResult) {
                        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                val payload = barcodes.firstOrNull()?.rawValue
                                if (payload != null && !hasHandledResult) {
                                    if (ConfigParser.looksImportable(payload)) {
                                        hasHandledResult = true
                                        cameraProvider.unbindAll()
                                        onPayloadScanned(payload)
                                    } else {
                                        errorMessage = "This QR code isn't a supported VPN configuration."
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                    )
                } catch (e: Exception) {
                    errorMessage = "Could not start the camera: ${e.message}"
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )

    Column(Modifier.fillMaxSize().padding(bottom = 32.dp), verticalArrangement = Arrangement.Bottom) {
        errorMessage?.let {
            Text(
                it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandCrimson.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
        IconButton(
            onClick = {
                torchEnabled = !torchEnabled
                camera?.cameraControl?.enableTorch(torchEnabled)
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Icon(
                if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "Toggle flash",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ScannerOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(250.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Transparent)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Transparent)
            )
        }
        Box(
            modifier = Modifier
                .size(250.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = BrandIceBlue,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun PermissionDeniedContent(permanentlyDenied: Boolean, onCancel: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Camera access is needed to scan QR codes.", color = Color.White, style = MaterialTheme.typography.titleMedium)
        if (permanentlyDenied) {
            Spacer(Modifier.height(8.dp))
            Text(
                "You've previously denied this. Enable it from the app's system settings to use QR scanning.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Spacer(Modifier.height(16.dp))
        if (permanentlyDenied) {
            Button(onClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }) { Text("Open settings") }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onCancel) { Text("Go back") }
    }
}
