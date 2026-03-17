package it.atm.app.data.local

import it.atm.app.auth.AccountManager
import it.atm.app.util.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenDataStore @Inject constructor(
    private val accountManager: AccountManager
) {
    private var onboardingCompleteOverride: Boolean? = null

    suspend fun setOnboardingComplete() {
        onboardingCompleteOverride = true
        accountManager.updateActiveAccount { it.copy(onboardingComplete = true) }
    }

    fun isOnboardingComplete(): Boolean {
        return onboardingCompleteOverride
            ?: (accountManager.getActiveAccount()?.onboardingComplete ?: false)
    }

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        tokenType: String,
        expiresAt: Long
    ) {
        AppLogger.d("DATA","Saving tokens")
        accountManager.updateActiveAccount {
            it.copy(
                accessToken = accessToken,
                refreshToken = refreshToken ?: it.refreshToken,
                tokenType = tokenType,
                expiresAt = expiresAt
            )
        }
    }

    fun getAccessToken(): String? = accountManager.getActiveAccount()?.accessToken

    fun getRefreshToken(): String? = accountManager.getActiveAccount()?.refreshToken

    fun getTokenType(): String? = accountManager.getActiveAccount()?.tokenType

    fun getExpiresAt(): Long? = accountManager.getActiveAccount()?.expiresAt

    private val deviceUidMutex = Mutex()

    suspend fun getDeviceUid(): String = deviceUidMutex.withLock {
        val account = accountManager.getActiveAccount()
        val existing = account?.deviceUid?.takeIf { it.isNotBlank() }
        if (existing != null) return@withLock existing
        val generated = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        if (account != null) {
            accountManager.updateActiveAccount { it.copy(deviceUid = generated) }
        }
        generated
    }

    suspend fun saveDeviceUid(uid: String) {
        accountManager.updateActiveAccount { it.copy(deviceUid = uid) }
    }

    suspend fun clearAll() {
        AppLogger.d("DATA","Clearing all tokens")
        val activeId = accountManager.activeAccountId.value
        if (activeId != null) {
            accountManager.removeAccount(activeId)
        }
        onboardingCompleteOverride = null
    }
}
