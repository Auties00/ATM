package it.atm.app.nfc

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import it.atm.app.data.remote.rest.Subscription
import it.atm.app.qr.QrPayloadBuilder
import it.atm.app.service.AccountManager

/**
 * Synchronous data bridge between the HCE service and persisted subscription data.
 *
 * The HCE service runs on the main thread and cannot do async I/O,
 * so this reads from AccountManager's in-memory StateFlow.
 */
class NfcTokenStore(private val accountManager: AccountManager) {

    private val gson = Gson()

    /**
     * Returns the full VToken binary data for the currently NFC-active subscription,
     * or null if none is active.
     */
    fun loadActiveVToken(): ByteArray? {
        val account = accountManager.getActiveAccount() ?: return null
        val index = account.activeNfcSubscriptionIndex
        if (index < 0) return null
        val json = account.subscriptionsJson ?: return null
        val subs: List<Subscription> = try {
            val type = object : TypeToken<List<Subscription>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            return null
        }
        if (index !in subs.indices) return null
        val b64 = subs[index].cachedDataOutBin ?: return null
        return try {
            Base64.decode(b64, Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }

    /** Returns the device UID for this account. */
    fun getDeviceUid(): String {
        return accountManager.getActiveAccount()?.deviceUid ?: ""
    }
}
