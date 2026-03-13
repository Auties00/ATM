package it.atm.app.data.local

import it.atm.app.service.AccountManager
import java.util.UUID

class TokenDataStore(private val accountManager: AccountManager) {

    private var onboardingCompleteOverride: Boolean? = null

    suspend fun setOnboardingComplete() {
        onboardingCompleteOverride = true
        accountManager.updateActiveAccount { it.copy(onboardingComplete = true) }
    }

    suspend fun isOnboardingComplete(): Boolean {
        return onboardingCompleteOverride
            ?: (accountManager.getActiveAccount()?.onboardingComplete ?: false)
    }

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        tokenType: String,
        expiresAt: Long
    ) {
        accountManager.updateActiveAccount {
            it.copy(
                accessToken = accessToken,
                refreshToken = refreshToken ?: it.refreshToken,
                tokenType = tokenType,
                expiresAt = expiresAt
            )
        }
    }

    suspend fun getAccessToken(): String? = accountManager.getActiveAccount()?.accessToken

    suspend fun getRefreshToken(): String? = accountManager.getActiveAccount()?.refreshToken

    suspend fun getTokenType(): String? = accountManager.getActiveAccount()?.tokenType

    suspend fun getExpiresAt(): Long? = accountManager.getActiveAccount()?.expiresAt

    suspend fun getDeviceUid(): String {
        val account = accountManager.getActiveAccount()
        val existing = account?.deviceUid?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        if (account != null) {
            accountManager.updateActiveAccount { it.copy(deviceUid = generated) }
        }
        return generated
    }

    suspend fun saveDeviceUid(uid: String) {
        accountManager.updateActiveAccount { it.copy(deviceUid = uid) }
    }

    suspend fun clearAll() {
        val activeId = accountManager.activeAccountId.value
        if (activeId != null) {
            accountManager.removeAccount(activeId)
        }
        onboardingCompleteOverride = null
    }
}
