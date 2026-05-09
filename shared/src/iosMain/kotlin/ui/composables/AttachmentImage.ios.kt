package ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    // iOS image decoding not implemented yet
    return null
}

