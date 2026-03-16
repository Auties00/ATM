package it.atm.app.nfc

import android.util.Base64
import it.atm.app.data.local.db.SubscriptionDao
import it.atm.app.auth.AccountManager
import kotlinx.coroutines.runBlocking
import it.atm.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NfcTokenStore @Inject constructor(
    private val accountManager: AccountManager,
    private val subscriptionDao: SubscriptionDao
) {
    fun loadActiveVToken(): ByteArray? {
        val account = accountManager.getActiveAccount() ?: return null
        val index = account.activeNfcSubscriptionIndex
        if (index < 0) return null
        val subs = runBlocking { subscriptionDao.getByAccount(account.id) }
        if (index !in subs.indices) return null
        val b64 = subs[index].cachedDataOutBin ?: return null
        return try {
            Base64.decode(b64, Base64.DEFAULT)
        } catch (_: Exception) {
            AppLogger.w("NFC","Failed to decode VToken data")
            null
        }
    }

    fun getDeviceUid(): String {
        return accountManager.getActiveAccount()?.deviceUid ?: ""
    }

    fun saveValidationStamp(stamp: ByteArray) {
        val account = accountManager.getActiveAccount() ?: return
        val index = account.activeNfcSubscriptionIndex
        if (index < 0) return
        val subs = runBlocking { subscriptionDao.getByAccount(account.id) }
        if (index !in subs.indices) return
        val sub = subs[index]
        if (sub.vtokenUid.isBlank()) return
        val b64 = Base64.encodeToString(stamp, Base64.NO_WRAP)
        runBlocking { subscriptionDao.updateDataOutBin(account.id, sub.vtokenUid, b64) }
    }
}
