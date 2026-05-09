package ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

@Composable
fun AttachmentPhotoGrid(
    attachments: List<ByteArray>,
    onRemoveAttachment: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = attachments.chunked(3)
    val selected = remember { mutableStateOf<ByteArray?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEachIndexed { columnIndex, bytes ->
                    val globalIndex = rowIndex * 3 + columnIndex
                    AttachmentThumbnail(
                        imageBytes = bytes,
                        onRemove = { onRemoveAttachment(globalIndex) },
                        onClick = { selected.value = bytes },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
                if (row.size < 3) {
                    repeat(3 - row.size) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        ) {}
                    }
                }
            }
        }
    }

    // Fullscreen preview dialog
    val selectedBytes = selected.value
    if (selectedBytes != null) {
        Dialog(onDismissRequest = { selected.value = null }) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                val imageBitmap = decodeImageBitmap(selectedBytes)
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                IconButton(
                    onClick = { selected.value = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(40.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}


