package it.atm.app.domain.model

data class VToken(
    val uid: String,
    val signatureCount: Int,
    val dataOutBin: String? = null,
    val contractStartValidity: String? = null,
    val contractEndValidity: String? = null,
    val contractDescription: String? = null
)
