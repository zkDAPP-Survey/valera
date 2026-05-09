package ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AttachmentPhotoGrid(
    attachments: List<ByteArray>,
    onRemoveAttachment: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = attachments.chunked(3)

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
}


