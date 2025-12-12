package ui.composables

import androidx.compose.runtime.Composable

@Composable
expect fun PhotoInput(
    label: String,
    imageBytes: ByteArray?,
    onImageSelected: (ByteArray?) -> Unit,
)
