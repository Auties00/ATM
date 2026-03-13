package it.atm.app.data.remote.vts

data class VToken(
    val uid: String,
    val signatureCount: Int,
    val dataOutBin: String? = null,
    val contractStartValidity: String? = null,
    val contractEndValidity: String? = null,
    val contractDescription: String? = null
)

data class QrConfig(
    val sigType: Int = 0,
    val initialKeyId: Int = 0,
    val qrCodeFormat: Int = 1,
    val signatureKeysVTID: Int = 0
)
