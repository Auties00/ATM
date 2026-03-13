package it.atm.app.data.local

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import it.atm.app.data.remote.rest.Subscription
import kotlinx.coroutines.flow.first

data class AuthBlock(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("expires_at") val expiresAt: Long?
)

data class SessionJson(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("auth") val auth: AuthBlock?,
    @SerializedName("device_uid") val deviceUid: String?,
    @SerializedName("subscriptions") val subscriptions: List<Subscription>,
    @SerializedName("last_sync") val lastSync: String?
) {

    fun toJson(): String {
        return Gson().toJson(this)
    }

    suspend fun importInto(
        tokenDataStore: TokenDataStore,
        accountManager: it.atm.app.service.AccountManager
    ) {
        // Create a pending account if none exists
        if (accountManager.getActiveAccount() == null) {
            accountManager.createPendingAccount()
        }
        auth?.let { a ->
            if (a.accessToken != null) {
                tokenDataStore.saveTokens(
                    accessToken = a.accessToken,
                    refreshToken = a.refreshToken,
                    tokenType = a.tokenType ?: "Bearer",
                    expiresAt = a.expiresAt ?: (System.currentTimeMillis() + 3_600_000L)
                )
            }
        }
        deviceUid?.let { uid ->
            tokenDataStore.saveDeviceUid(uid)
        }
    }

    companion object {

        fun fromJson(json: String): SessionJson {
            return Gson().fromJson(json, SessionJson::class.java)
        }

        suspend fun exportFrom(
            tokenDataStore: TokenDataStore,
            subscriptionDataStore: SubscriptionDataStore
        ): SessionJson {
            val accessToken = tokenDataStore.getAccessToken()
            val refreshToken = tokenDataStore.getRefreshToken()
            val tokenType = tokenDataStore.getTokenType()
            val expiresAt = tokenDataStore.getExpiresAt()
            val deviceUid = try {
                tokenDataStore.getDeviceUid()
            } catch (e: Exception) {
                null
            }
            val subscriptions = subscriptionDataStore.getSubscriptions().first()
            val lastSync = subscriptionDataStore.getLastSync()

            val authBlock = if (accessToken != null) {
                AuthBlock(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    tokenType = tokenType,
                    expiresAt = expiresAt
                )
            } else {
                null
            }

            return SessionJson(
                version = 1,
                auth = authBlock,
                deviceUid = deviceUid,
                subscriptions = subscriptions,
                lastSync = lastSync
            )
        }
    }
}
