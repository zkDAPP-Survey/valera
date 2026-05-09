package ui.composables

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.content.ContentValues
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.asitplus.valera.resources.Res
import at.asitplus.valera.resources.button_label_choose_from_gallery
import at.asitplus.valera.resources.button_label_take_photo
import org.jetbrains.compose.resources.stringResource
import java.io.ByteArrayOutputStream

@Composable
actual fun FIIssuerAttachmentActions(
    onGalleryImagesSelected: (List<ByteArray>) -> Unit,
    onCameraImageSelected: (ByteArray?) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMedia(),
        onResult = { uris ->
            val bytesList = uris.mapNotNull { uri ->
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytesList.isNotEmpty()) onGalleryImagesSelected(bytesList)
        }
    )

    // Use TakePicture to get a full-resolution image saved to a content Uri (better quality)
    val currentPhotoUri = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
        onResult = { success: Boolean ->
            val uri = currentPhotoUri.value
            if (success && uri != null) {
                val maxDim = 1920 // max width/height to avoid huge uploads
                try {
                    // helper to apply EXIF orientation from the saved Uri
                    fun applyExifRotation(bitmap: Bitmap?, sourceUri: Uri?): Bitmap? {
                        if (bitmap == null || sourceUri == null) return bitmap
                        return try {
                            context.contentResolver.openInputStream(sourceUri)?.use { exifStream ->
                                val exif = ExifInterface(exifStream)
                                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                                val rotation = when (orientation) {
                                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                                    else -> 0
                                }
                                if (rotation == 0) bitmap else {
                                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                }
                            } ?: bitmap
                        } catch (_: Throwable) {
                            bitmap
                        }
                    }

                    // 1) decode bounds to compute sample size
                    val bounds = context.contentResolver.openInputStream(uri)?.use { input ->
                        val bopts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(input, null, bopts)
                        bopts
                    } ?: BitmapFactory.Options().apply { outWidth = 1; outHeight = 1 }

                    val outW = bounds.outWidth.takeIf { it > 0 } ?: 1
                    val outH = bounds.outHeight.takeIf { it > 0 } ?: 1
                    var inSampleSize = 1
                    val largest = maxOf(outW, outH)
                    if (largest > maxDim) {
                        while (largest / inSampleSize > maxDim) inSampleSize *= 2
                    }

                    // 2) decode actual bitmap with sampling
                    val opts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { input2 ->
                        BitmapFactory.decodeStream(input2, null, opts)
                    }

                    val oriented = applyExifRotation(bitmap, uri)

                    val bytes = oriented?.let {
                        val stream = ByteArrayOutputStream()
                        it.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        stream.toByteArray()
                    } ?: context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

                    // If bytes are present but we didn't apply rotation (e.g., bitmap null), attempt to apply orientation from raw bytes
                    val finalBytes = if (bytes != null && bitmap == null) {
                        // try decode from bytes and rotate
                        try {
                            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            val rotated = applyExifRotation(decoded, uri)
                            rotated?.let { r ->
                                val s = ByteArrayOutputStream()
                                r.compress(Bitmap.CompressFormat.JPEG, 90, s)
                                s.toByteArray()
                            } ?: bytes
                        } catch (_: Throwable) { bytes }
                    } else bytes

                    onCameraImageSelected(finalBytes)
                } catch (_: Throwable) {
                    // fallback: try to read raw bytes
                    val raw = try {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } catch (_: Throwable) {
                        null
                    }
                    onCameraImageSelected(raw)
                } finally {
                    currentPhotoUri.value = null
                }
            } else {
                // Delete the empty/unused uri if camera cancelled
                uri?.let { u ->
                    try {
                        context.contentResolver.delete(u, null, null)
                    } catch (_: Throwable) {}
                }
                currentPhotoUri.value = null
                if (!success) onCameraImageSelected(null)
            }
        }
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                )
                Text(" ")
                Text(stringResource(Res.string.button_label_choose_from_gallery))
            }
            OutlinedButton(
                onClick = {
                    // create a content Uri for the camera to write into
                    val filename = "valera_${System.currentTimeMillis()}.jpg"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Valera")
                        }
                    }
                    val uri = try {
                        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    } catch (_: Throwable) {
                        null
                    }
                    if (uri == null) {
                        // Could not create destination; abort and report null
                        onCameraImageSelected(null)
                        return@OutlinedButton
                    }
                    currentPhotoUri.value = uri
                    cameraLauncher.launch(uri)
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                )
                Text(" ")
                Text(stringResource(Res.string.button_label_take_photo))
            }
        }
    }
}





