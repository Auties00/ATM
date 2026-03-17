package it.atm.app.data.local

import it.atm.app.data.local.db.SubscriptionDao
import it.atm.app.data.local.db.SubscriptionEntity
import it.atm.app.domain.model.QrConfig
import it.atm.app.auth.AccountManager
import it.atm.app.util.DateFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import it.atm.app.util.AppLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionDataStore @Inject constructor(
    private val accountManager: AccountManager,
    private val subscriptionDao: SubscriptionDao
) {
    suspend fun saveSubscriptions(accountId: String, subscriptions: List<SubscriptionEntity>) {
        AppLogger.d("DATA","Saving %d subscriptions for account=%s", subscriptions.size, accountId)
        subscriptionDao.deleteByAccount(accountId)
        subscriptionDao.insertAll(subscriptions)
        accountManager.updateActiveAccount { it.copy(lastSync = DateFormatter.nowIso()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getSubscriptions(): Flow<List<SubscriptionEntity>> {
        return accountManager.activeAccountId.flatMapLatest { activeId ->
            if (activeId == null) flowOf(emptyList())
            else subscriptionDao.observeByAccount(activeId)
        }
    }

    suspend fun updateCachedData(accountId: String, vtokenUid: String, dataOutBin: String) {
        subscriptionDao.updateDataOutBin(accountId, vtokenUid, dataOutBin)
    }

    suspend fun getSubscriptionsForAccount(accountId: String): List<SubscriptionEntity> {
        return subscriptionDao.getByAccount(accountId)
    }

    fun getLastSync(): String? {
        return accountManager.getActiveAccount()?.lastSync
    }

    suspend fun saveQrConfig(config: QrConfig) {
        AppLogger.d("DATA","Saving QR config sigType=%d keyId=%d format=%d", config.sigType, config.initialKeyId, config.qrCodeFormat)
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
        AppLogger.d("DATA","Clearing subscription data")
        val activeId = accountManager.activeAccountId.value ?: return
        subscriptionDao.deleteByAccount(activeId)
        accountManager.updateActiveAccount { it.copy(lastSync = null) }
    }
}
