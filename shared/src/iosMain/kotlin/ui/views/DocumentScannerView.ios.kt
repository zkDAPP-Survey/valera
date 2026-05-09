package ui.views

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun DocumentScannerView(
    onScannedPhoto: (imageBytes: ByteArray) -> Unit,
    onScannedText: (text: String) -> Unit,
    captureTrigger: Int,
    modifier: Modifier,
) {
    Text(
        text = "Document OCR scanning is not implemented on iOS yet.",
        modifier = modifier,
    )
}
