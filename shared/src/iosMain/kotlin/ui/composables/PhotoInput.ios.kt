package ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PhotoInput(
    label: String,
    imageBytes: ByteArray?,
    onImageSelected: (ByteArray?) -> Unit,
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Photo capture/pick not implemented on iOS yet.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = { /* noop */ }, enabled = false, modifier = Modifier) {
            Text("Open camera/gallery")
        }
    }
}
