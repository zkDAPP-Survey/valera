package ui.views

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun DocumentScannerView(
    onScannedPhoto: (imageBytes: ByteArray) -> Unit,
    onScannedText: (text: String) -> Unit,
    captureTrigger: Int = 0,
    modifier: Modifier = Modifier,
)
