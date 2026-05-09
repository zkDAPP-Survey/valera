package ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun FIIssuerAttachmentActions(
    onGalleryImagesSelected: (List<ByteArray>) -> Unit,
    onCameraImageSelected: (ByteArray?) -> Unit,
    modifier: Modifier,
)


