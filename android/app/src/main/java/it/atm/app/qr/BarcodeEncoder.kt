package it.atm.app.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.aztec.AztecWriter
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import androidx.core.graphics.createBitmap
import com.google.zxing.common.BitMatrix

object BarcodeEncoder {

    fun generateQr(data: ByteArray, size: Int = 512, bgColor: Int = Color.TRANSPARENT): Bitmap {
        val qrString = String(data, Charsets.ISO_8859_1)
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
        )
        val bitMatrix = QRCodeWriter().encode(qrString, BarcodeFormat.QR_CODE, size, size, hints)
        return renderBitMatrix(bitMatrix, size, size, bgColor)
    }

    fun encode(data: String, format: BarcodeFormat, width: Int, height: Int, bgColor: Int = Color.WHITE): Bitmap {
        val hints = mutableMapOf<EncodeHintType, Any>()
        if (format == BarcodeFormat.QR_CODE) {
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.L
            hints[EncodeHintType.CHARACTER_SET] = "ISO-8859-1"
        }

        val writer = when (format) {
            BarcodeFormat.QR_CODE -> QRCodeWriter()
            BarcodeFormat.CODE_128 -> Code128Writer()
            BarcodeFormat.AZTEC -> AztecWriter()
            else -> QRCodeWriter()
        }
        val bitMatrix = writer.encode(data, format, width, height, hints)
        return renderBitMatrix(bitMatrix, width, height, bgColor)
    }

    private fun renderBitMatrix(
        bitMatrix: BitMatrix,
        width: Int,
        height: Int,
        bgColor: Int
    ): Bitmap {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                pixels[rowOffset + x] = if (bitMatrix.get(x, y)) Color.BLACK else bgColor
            }
        }
        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
