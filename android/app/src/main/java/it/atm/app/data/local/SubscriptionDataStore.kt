package it.atm.app.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import it.atm.app.data.remote.rest.Subscription
import it.atm.app.data.remote.vts.QrConfig
import it.atm.app.service.AccountManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SubscriptionDataStore(private val accountManager: AccountManager) {

    private val gson = Gson()

    suspend fun saveSubscriptions(subscriptions: List<Subscription>) {
        val json = gson.toJson(subscriptions)
        val lastSync = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss",
            java.util.Locale.US
        ).format(java.util.Date())
        accountManager.updateActiveAccount {
            it.copy(subscriptionsJson = json, lastSync = lastSync)
        }
    }

    fun getSubscriptions(): Flow<List<Subscription>> {
        return combine(accountManager.accounts, accountManager.activeAccountId) { accounts, activeId ->
            val account = accounts.find { it.id == activeId } ?: accounts.firstOrNull()
            val json = account?.subscriptionsJson
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    val type = object : TypeToken<List<Subscription>>() {}.type
                    gson.fromJson(json, type)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    suspend fun getLastSync(): String? {
        return accountManager.getActiveAccount()?.lastSync
    }

    suspend fun getSubscriptionsJson(): String? {
        return accountManager.getActiveAccount()?.subscriptionsJson
    }

    suspend fun saveQrConfig(config: QrConfig) {
        accountManager.updateActiveAccount {
            it.copy(
                qrSigType = config.sigType,
                qrInitialKeyId = config.initialKeyId,
                qrCodeFormat = config.qrCodeFormat,
                qrSignatureKeysVTID = config.signatureKeysVTID
            )
        }
    }

    fun getQrConfig(): QrConfig {
        val account = accountManager.getActiveAccount()
        return QrConfig(
            sigType = account?.qrSigType ?: 0,
            initialKeyId = account?.qrInitialKeyId ?: 0,
            qrCodeFormat = account?.qrCodeFormat ?: 1,
            signatureKeysVTID = account?.qrSignatureKeysVTID ?: 0
        )
    }

    suspend fun setActiveNfcSubscriptionIndex(index: Int) {
        accountManager.updateActiveAccount {
            it.copy(activeNfcSubscriptionIndex = index)
        }
    }

    fun getActiveNfcSubscriptionIndex(): Int {
        return accountManager.getActiveAccount()?.activeNfcSubscriptionIndex ?: -1
    }

    suspend fun clearAll() {
        accountManager.updateActiveAccount {
            it.copy(subscriptionsJson = null, lastSync = null)
        }
    }
}
