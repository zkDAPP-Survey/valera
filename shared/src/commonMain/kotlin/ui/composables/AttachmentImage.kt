package ui.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

@Composable
fun AttachmentThumbnail(
    imageBytes: ByteArray,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = decodeImageBitmap(imageBytes)

    if (imageBitmap != null) {
        Box(
            modifier = Modifier
                .then(modifier)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Attachment",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp)
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        }
    }
}


