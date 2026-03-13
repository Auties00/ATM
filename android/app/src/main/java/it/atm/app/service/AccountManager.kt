package it.atm.app.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.accountsDataStore: DataStore<Preferences> by preferencesDataStore(name = "accounts")

data class StoredAccount(
    val id: String,
    val name: String = "",
    val surname: String = "",
    val email: String = "",
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresAt: Long = 0L,
    val deviceUid: String = "",
    val onboardingComplete: Boolean = false,
    val subscriptionsJson: String? = null,
    val lastSync: String? = null,
    val qrSigType: Int = 0,
    val qrInitialKeyId: Int = 0,
    val qrCodeFormat: Int = 1,
    val qrSignatureKeysVTID: Int = 0,
    val activeNfcSubscriptionIndex: Int = -1
) {
    val displayName: String get() = "$name $surname".trim()
    val initials: String get() = buildString {
        if (name.isNotBlank()) append(name.first().uppercase())
        if (surname.isNotBlank()) append(surname.first().uppercase())
    }.ifEmpty { email.firstOrNull()?.uppercase() ?: "?" }
}

class DuplicateAccountException(message: String) : RuntimeException(message)

class AccountManager(private val context: Context) {

    companion object {
        private val KEY_ACCOUNTS = stringPreferencesKey("accounts_json")
        private val KEY_ACTIVE_ID = stringPreferencesKey("active_account_id")
        const val PENDING_ACCOUNT_ID = "__pending__"
    }

    private val gson = Gson()

    private val _accounts = MutableStateFlow<List<StoredAccount>>(emptyList())
    val accounts: StateFlow<List<StoredAccount>> = _accounts.asStateFlow()

    private val _activeAccountId = MutableStateFlow<String?>(null)
    val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()

    suspend fun load() {
        val prefs = context.accountsDataStore.data.first()
        val json = prefs[KEY_ACCOUNTS]
        val loaded: List<StoredAccount> = if (json.isNullOrBlank()) emptyList()
        else try {
            val type = object : TypeToken<List<StoredAccount>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) { emptyList() }
        // Filter out any leftover pending accounts
        _accounts.value = loaded.filter { it.id != PENDING_ACCOUNT_ID }
        _activeAccountId.value = prefs[KEY_ACTIVE_ID]
    }

    fun getActiveAccount(): StoredAccount? {
        val id = _activeAccountId.value ?: return _accounts.value.firstOrNull()
        return _accounts.value.find { it.id == id } ?: _accounts.value.firstOrNull()
    }

    fun hasAccountWithEmail(email: String): Boolean {
        return _accounts.value.any { it.email.equals(email, ignoreCase = true) }
    }

    suspend fun addOrUpdateAccount(account: StoredAccount) {
        val list = _accounts.value.toMutableList()
        // Match by id first, then by email
        val idx = list.indexOfFirst { it.id == account.id }
        if (idx >= 0) {
            list[idx] = account
        } else {
            val emailIdx = list.indexOfFirst {
                it.email.isNotBlank() && it.email.equals(account.email, ignoreCase = true)
            }
            if (emailIdx >= 0 && account.email.isNotBlank()) {
                list[emailIdx] = account.copy(id = list[emailIdx].id)
            } else {
                list.add(account)
            }
        }
        // Remove pending accounts now that we have a real one
        if (account.id != PENDING_ACCOUNT_ID) {
            list.removeAll { it.id == PENDING_ACCOUNT_ID }
        }
        _accounts.value = list
        _activeAccountId.value = account.id
        persist()
    }

    /**
     * Finalize a pending account by replacing it with a real email-based ID.
     * Throws [DuplicateAccountException] if an account with that email already exists.
     */
    suspend fun finalizePendingAccount(email: String, name: String = "", surname: String = "") {
        val pending = _accounts.value.find { it.id == PENDING_ACCOUNT_ID } ?: return
        val existing = _accounts.value.find {
            it.id != PENDING_ACCOUNT_ID && it.email.equals(email, ignoreCase = true)
        }
        if (existing != null) {
            // Duplicate detected — cancel the pending account and reject
            cancelPendingAccount(existing.id)
            throw DuplicateAccountException("An account with email $email already exists")
        }
        val list = _accounts.value.toMutableList()
        list.removeAll { it.id == PENDING_ACCOUNT_ID }
        val finalized = pending.copy(id = email.lowercase(), email = email, name = name, surname = surname)
        list.add(finalized)
        _activeAccountId.value = finalized.id
        _accounts.value = list
        persist()
    }

    suspend fun createPendingAccount(): StoredAccount {
        val pending = StoredAccount(id = PENDING_ACCOUNT_ID)
        val list = _accounts.value.toMutableList()
        list.removeAll { it.id == PENDING_ACCOUNT_ID }
        list.add(pending)
        _accounts.value = list
        _activeAccountId.value = PENDING_ACCOUNT_ID
        persist()
        return pending
    }

    suspend fun cancelPendingAccount(previousActiveId: String?) {
        val list = _accounts.value.toMutableList()
        list.removeAll { it.id == PENDING_ACCOUNT_ID }
        _accounts.value = list
        _activeAccountId.value = previousActiveId ?: list.firstOrNull()?.id
        persist()
    }

    suspend fun removeAccount(accountId: String) {
        val list = _accounts.value.toMutableList()
        list.removeAll { it.id == accountId }
        _accounts.value = list
        if (_activeAccountId.value == accountId) {
            _activeAccountId.value = list.firstOrNull()?.id
        }
        persist()
    }

    suspend fun switchTo(accountId: String) {
        _activeAccountId.value = accountId
        persist()
    }

    suspend fun updateActiveAccount(transform: (StoredAccount) -> StoredAccount) {
        val active = getActiveAccount() ?: return
        val updated = transform(active)
        // If email changed from blank, use email as new id
        if (active.id == PENDING_ACCOUNT_ID && updated.email.isNotBlank()) {
            finalizePendingAccount(updated.email, updated.name, updated.surname)
            return
        }
        addOrUpdateAccount(updated)
    }

    private suspend fun persist() {
        context.accountsDataStore.edit { prefs ->
            prefs[KEY_ACCOUNTS] = gson.toJson(_accounts.value)
            _activeAccountId.value?.let { prefs[KEY_ACTIVE_ID] = it }
                ?: prefs.remove(KEY_ACTIVE_ID)
        }
    }
}
