package ui.composables

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview
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

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = TakePicturePreview(),
        onResult = { bitmap: Bitmap? ->
            val bytes = bitmap?.let {
                val stream = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                stream.toByteArray()
            }
            onCameraImageSelected(bytes)
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
                onClick = { cameraLauncher.launch(null) },
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





