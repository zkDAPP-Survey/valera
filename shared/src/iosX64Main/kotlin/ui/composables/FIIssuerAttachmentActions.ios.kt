package ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun FIIssuerAttachmentActions(
    onGalleryImagesSelected: (List<ByteArray>) -> Unit,
    onCameraImageSelected: (ByteArray?) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Button(onClick = { /* noop */ }, enabled = false) {
            Text("Choose from gallery")
        }
        Button(onClick = { /* noop */ }, enabled = false) {
            Text("Take photo")
        }
    }
}



