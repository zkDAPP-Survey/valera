package ui.views

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.aakira.napier.Napier
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val documentScannerExecutor = Executors.newSingleThreadExecutor()

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun DocumentScannerView(
    onScannedPhoto: (imageBytes: ByteArray) -> Unit,
    onScannedText: (text: String) -> Unit,
    captureTrigger: Int,
    modifier: Modifier,
) {
    val cameraPermissionState = rememberMultiplePermissionsState(listOf(android.Manifest.permission.CAMERA))
    if (cameraPermissionState.allPermissionsGranted) {
        DocumentScannerWithGrantedPermission(onScannedPhoto, onScannedText, captureTrigger, modifier)
    } else {
        LaunchedEffect(Unit) {
            cameraPermissionState.launchMultiplePermissionRequest()
        }
    }
}

@Composable
private fun DocumentScannerWithGrantedPermission(
    onScannedPhoto: (imageBytes: ByteArray) -> Unit,
    onScannedText: (text: String) -> Unit,
    captureTrigger: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val preview = remember { Preview.Builder().build() }
    val previewView = remember { PreviewView(context) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val cameraProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider.value?.unbindAll()
            recognizer.close()
        }
    }

    LaunchedEffect(Unit) {
        cameraProvider.value = suspendCoroutine<ProcessCameraProvider> { continuation ->
            ProcessCameraProvider.getInstance(context).also { provider ->
                provider.addListener({ continuation.resume(provider.get()) }, documentScannerExecutor)
            }
        }
        cameraProvider.value?.unbindAll()
        cameraProvider.value?.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture,
        )
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            { previewView },
            modifier = Modifier.fillMaxSize(),
        )
    }

    LaunchedEffect(captureTrigger) {
        if (captureTrigger <= 0) return@LaunchedEffect
        val outputFile = createTempCaptureFile(context)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            outputOptions,
            documentScannerExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    runCatching {
                        val bytes = outputFile.readBytes()
                        onScannedPhoto(bytes)
                        recognizer.process(InputImage.fromFilePath(context, Uri.fromFile(outputFile)))
                            .addOnSuccessListener { result ->
                                val scannedText = result.text.trim()
                                if (scannedText.isNotBlank()) {
                                    onScannedText(scannedText)
                                }
                            }
                            .addOnFailureListener { throwable ->
                                Napier.w("DocumentScannerView: OCR failed", throwable)
                            }
                    }.onFailure { throwable ->
                        Napier.w("DocumentScannerView: capture failed", throwable)
                    }.also {
                        runCatching { outputFile.delete() }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Napier.w("DocumentScannerView: capture error", throwable = exception)
                    runCatching { outputFile.delete() }
                }
            }
        )
    }
}

private fun createTempCaptureFile(context: Context): File =
    File.createTempFile("fiissuer_scan_", ".jpg", context.cacheDir)
