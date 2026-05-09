package ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FIIssuerAttachmentInputSection(
    label: String,
    description: String,
    attachments: List<ByteArray>,
    onGalleryImagesSelected: (List<ByteArray>) -> Unit,
    onCameraImageSelected: (ByteArray?) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        AttachmentPhotoGrid(
            attachments = attachments,
            onRemoveAttachment = onRemoveAttachment,
            modifier = Modifier.padding(top = 12.dp),
        )
        FIIssuerAttachmentActions(
            onGalleryImagesSelected = onGalleryImagesSelected,
            onCameraImageSelected = onCameraImageSelected,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}



