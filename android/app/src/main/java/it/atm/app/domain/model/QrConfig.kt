package it.atm.app.domain.model

data class QrConfig(
    val sigType: Int = 0,
    val initialKeyId: Int = 0,
    val qrCodeFormat: Int = 1,
    val signatureKeysVTID: Int = 0
)
