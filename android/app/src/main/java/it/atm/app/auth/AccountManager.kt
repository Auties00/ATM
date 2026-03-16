package it.atm.app.auth

import it.atm.app.data.local.db.AccountDao
import it.atm.app.data.local.db.AccountEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import it.atm.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

class DuplicateAccountException(message: String) : RuntimeException(message)

@Singleton
class AccountManager @Inject constructor(
    private val accountDao: AccountDao
) {
    companion object {
        const val PENDING_ACCOUNT_ID = "__pending__"
    }

    private val _accounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    val accounts: StateFlow<List<AccountEntity>> = _accounts.asStateFlow()

    private val _activeAccountId = MutableStateFlow<String?>(null)
    val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()

    suspend fun load() {
        AppLogger.d("ACCOUNT","Loading accounts")
        val loaded = accountDao.getAll().filter { it.id != PENDING_ACCOUNT_ID }
        _accounts.value = loaded
        if (_activeAccountId.value == null) {
            _activeAccountId.value = loaded.firstOrNull()?.id
        }
    }

    fun getActiveAccount(): AccountEntity? {
        val id = _activeAccountId.value ?: return _accounts.value.firstOrNull()
        return _accounts.value.find { it.id == id } ?: _accounts.value.firstOrNull()
    }

    suspend fun addOrUpdateAccount(account: AccountEntity) {
        AppLogger.d("ACCOUNT","Upsert account id=%s", account.id)
        accountDao.upsert(account)
        if (account.id != PENDING_ACCOUNT_ID) {
            accountDao.deleteById(PENDING_ACCOUNT_ID)
        }
        _activeAccountId.value = account.id
        refreshCache()
    }

    suspend fun finalizePendingAccount(email: String, name: String = "", surname: String = "") {
        val pending = accountDao.getById(PENDING_ACCOUNT_ID) ?: return
        val count = accountDao.countByEmail(email, PENDING_ACCOUNT_ID)
        if (count > 0) {
            accountDao.deleteById(PENDING_ACCOUNT_ID)
            refreshCache()
            throw DuplicateAccountException("An account with email $email already exists")
        }
        val finalized = pending.copy(id = email.lowercase(), email = email, name = name, surname = surname)
        accountDao.deleteById(PENDING_ACCOUNT_ID)
        accountDao.upsert(finalized)
        _activeAccountId.value = finalized.id
        AppLogger.d("ACCOUNT","Finalized pending account as %s", finalized.id)
        refreshCache()
    }

    suspend fun createPendingAccount(): AccountEntity {
        AppLogger.d("ACCOUNT","Creating pending account")
        accountDao.deleteById(PENDING_ACCOUNT_ID)
        val pending = AccountEntity(id = PENDING_ACCOUNT_ID)
        accountDao.upsert(pending)
        _activeAccountId.value = PENDING_ACCOUNT_ID
        refreshCache()
        return pending
    }

    suspend fun cancelPendingAccount(previousActiveId: String?) {
        accountDao.deleteById(PENDING_ACCOUNT_ID)
        _activeAccountId.value = previousActiveId ?: accountDao.getAll().firstOrNull()?.id
        refreshCache()
    }

    suspend fun removeAccount(accountId: String) {
        AppLogger.d("ACCOUNT","Removing account id=%s", accountId)
        accountDao.deleteById(accountId)
        if (_activeAccountId.value == accountId) {
            _activeAccountId.value = accountDao.getAll().firstOrNull()?.id
        }
        refreshCache()
    }

    suspend fun switchTo(accountId: String) {
        AppLogger.d("ACCOUNT","Switching to account id=%s", accountId)
        _activeAccountId.value = accountId
    }

    suspend fun updateActiveAccount(transform: (AccountEntity) -> AccountEntity) {
        val active = getActiveAccount() ?: return
        val updated = transform(active)
        if (active.id == PENDING_ACCOUNT_ID && updated.email.isNotBlank()) {
            finalizePendingAccount(updated.email, updated.name, updated.surname)
            return
        }
        addOrUpdateAccount(updated)
    }

    private suspend fun refreshCache() {
        _accounts.value = accountDao.getAll().filter { it.id != PENDING_ACCOUNT_ID }
    }
}
