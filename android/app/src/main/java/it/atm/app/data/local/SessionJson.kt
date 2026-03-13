package it.atm.app.data.local

import it.atm.app.data.local.db.AccountEntity
import it.atm.app.data.local.db.SubscriptionEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AuthBlock(
    @SerialName("access_token") val accessToken: String?,
    @SerialName("refresh_token") val refreshToken: String?,
    @SerialName("token_type") val tokenType: String?,
    @SerialName("expires_at") val expiresAt: Long?
)

@Serializable
data class SessionSubscription(
    @SerialName("card_code") val cardCode: String = "",
    @SerialName("card_number") val cardNumber: String = "",
    @SerialName("serial_number") val serialNumber: String = "",
    @SerialName("holder_id") val holderId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("subtitle") val subtitle: String = "",
    @SerialName("profile") val profile: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("start_validity") val startValidity: String = "",
    @SerialName("end_validity") val endValidity: String = "",
    @SerialName("carrier_code") val carrierCode: String = "",
    @SerialName("status") val status: Int = 0,
    @SerialName("cached_data_out_bin") val cachedDataOutBin: String? = null,
    @SerialName("vtoken_uid") val vtokenUid: String = "",
    @SerialName("signature_count") val signatureCount: Int = 1
)

@Serializable
data class SessionJson(
    @SerialName("version") val version: Int = 1,
    @SerialName("auth") val auth: AuthBlock?,
    @SerialName("device_uid") val deviceUid: String?,
    @SerialName("subscriptions") val subscriptions: List<SessionSubscription>,
    @SerialName("last_sync") val lastSync: String?
) {
    fun toJson(): String = Json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonStr: String): SessionJson = json.decodeFromString(serializer(), jsonStr)
    }
}
