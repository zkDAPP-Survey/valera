package ui.composables

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
                try {
                    // Simply read the file from MediaStore as-is (preserves EXIF data)
                    // EXIF orientation will be applied later in decodeImageBitmap.android.kt
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    onCameraImageSelected(bytes)
                } catch (_: Throwable) {
                    // fallback
                    onCameraImageSelected(null)
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





