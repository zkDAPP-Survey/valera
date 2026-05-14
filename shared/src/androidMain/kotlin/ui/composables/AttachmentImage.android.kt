package ui.composables

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

@Composable
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    return remember(bytes) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                // Try to read and apply EXIF orientation from bytes
                val rotated = try {
                    val exif = ExifInterface(ByteArrayInputStream(bytes))
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    val rotation = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                    if (rotation == 0) bitmap else {
                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    }
                } catch (_: Throwable) {
                    // If EXIF read fails, return bitmap as-is
                    bitmap
                }
                rotated.asImageBitmap()
            } else null
        } catch (_: Exception) {
            null
        }
    }
}

