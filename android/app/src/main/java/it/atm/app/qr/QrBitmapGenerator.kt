package it.atm.app.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generates a [Bitmap] QR code from raw byte data using ZXing.
 *
 * Matches BitmapUtils.generateQrCode in the original AEP VTS SDK:
 * - Encodes the data as an ISO-8859-1 string (preserving raw binary bytes)
 * - Uses error correction level L (Low)
 */
object QrBitmapGenerator {

    /**
     * Encodes [data] (raw bytes) into a QR code bitmap of the given [size].
     *
     * The bytes are converted to an ISO-8859-1 string before ZXing encoding,
     * matching the original SDK's `new String(bytes, Charset.forName("ISO-8859-1"))`.
     */
    fun generate(data: ByteArray, size: Int = 512, bgColor: Int = Color.TRANSPARENT): Bitmap {
        val qrString = String(data, Charsets.ISO_8859_1)
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
        )
        val bitMatrix = QRCodeWriter().encode(
            qrString,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints,
        )

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else bgColor)
            }
        }
        return bitmap
    }
}
