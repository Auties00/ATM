package it.atm.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import it.atm.app.AtmApp
import it.atm.app.data.local.SessionJson
import it.atm.app.data.local.SubscriptionDataStore
import it.atm.app.data.local.TokenDataStore
import it.atm.app.data.remote.rest.AtmRestClient
import it.atm.app.data.remote.rest.Subscription
import it.atm.app.data.remote.rest.Ticket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class UserProfile(
    val name: String = "",
    val surname: String = "",
    val email: String = "",
    val confirmedEmail: Boolean = false,
    val phone: String = "",
    val phonePrefix: String = "",
    val birthDate: String? = null,
    val imagePath: String? = null,
) {
    val displayName: String get() = "$name $surname".trim()
    val initials: String get() = buildString {
        if (name.isNotBlank()) append(name.first().uppercase())
        if (surname.isNotBlank()) append(surname.first().uppercase())
    }.ifEmpty { "?" }
    val profileImageUrl: String? get() = imagePath?.let { path ->
        when {
            path.startsWith("http") -> path
            path.startsWith("/") -> "https://be.atm.it$path"
            else -> "https://be.atm.it/$path"
        }
    }
}

class HomeViewModel(
    private val app: AtmApp
) : ViewModel() {

    private val authRepository = app.authRepository
    private val subscriptionRepository = app.subscriptionRepository
    private val tokenDataStore = app.tokenDataStore
    private val subscriptionDataStore = app.subscriptionDataStore
    private val accountManager = app.accountManager
    private val restClient = AtmRestClient(app.httpClient)

    val accounts = accountManager.accounts
    val activeAccountId = accountManager.activeAccountId

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _tickets = MutableStateFlow<List<Ticket>>(emptyList())
    val tickets: StateFlow<List<Ticket>> = _tickets.asStateFlow()

    private val _isTicketsLoading = MutableStateFlow(false)
    val isTicketsLoading: StateFlow<Boolean> = _isTicketsLoading.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    data class SnackbarMessage(val text: String, val isError: Boolean = true, val id: Long = System.nanoTime())

    private val _snackbar = MutableStateFlow<SnackbarMessage?>(null)
    val snackbar: StateFlow<SnackbarMessage?> = _snackbar.asStateFlow()

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    private var manualLogout = false

    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _isProfileSyncing = MutableStateFlow(true)
    val isProfileSyncing: StateFlow<Boolean> = _isProfileSyncing.asStateFlow()

    private val _isProfileUpdating = MutableStateFlow(false)
    val isProfileUpdating: StateFlow<Boolean> = _isProfileUpdating.asStateFlow()

    init {
        loadCachedSubscriptions()
        loadProfile()
        refreshTickets()

        viewModelScope.launch {
            authRepository.authStatus.collect { status ->
                if (status is it.atm.app.auth.AuthStatus.NeedsLogin && !manualLogout && _subscriptions.value.isNotEmpty()) {
                    _snackbar.value = SnackbarMessage("Session expired. Please sign in again.")
                    _loggedOut.value = true
                }
            }
        }
    }

    private fun loadCachedSubscriptions() {
        viewModelScope.launch {
            try {
                subscriptionDataStore.getSubscriptions().collect { subs ->
                    _subscriptions.value = subs
                }
            } catch (e: Exception) {
                _snackbar.value = SnackbarMessage(e.message ?: "Failed to load subscriptions")
            }
        }
    }

    private suspend fun fetchAndApplyProfile() {
        val token = tokenDataStore.getAccessToken() ?: return
        val deviceUid = tokenDataStore.getDeviceUid()
        try { restClient.syncAccount(token, deviceUid) } catch (_: Exception) {}
        val json = restClient.fetchAccountProfile(token, deviceUid)
        _profile.value = UserProfile(
            name = json.get("name")?.asString ?: "",
            surname = json.get("surname")?.asString ?: "",
            email = json.get("email")?.asString ?: "",
            confirmedEmail = json.get("confirmedEmail")?.asBoolean ?: false,
            phone = json.get("phone")?.asString ?: "",
            phonePrefix = json.get("phonePrefix")?.asString ?: "",
            birthDate = json.get("birthDate")?.takeIf { !it.isJsonNull }?.asString,
            imagePath = json.get("imagePath")?.takeIf { !it.isJsonNull }?.asString,
        )
        val p = _profile.value
        if (accountManager.getActiveAccount()?.id == it.atm.app.service.AccountManager.PENDING_ACCOUNT_ID
            && p.email.isNotBlank()) {
            accountManager.finalizePendingAccount(p.email, p.name, p.surname)
        } else {
            accountManager.updateActiveAccount {
                it.copy(name = p.name, surname = p.surname, email = p.email)
            }
        }
    }

    private fun loadProfile(showSyncing: Boolean = true) {
        viewModelScope.launch {
            if (showSyncing) _isProfileSyncing.value = true
            try {
                fetchAndApplyProfile()
            } catch (e: it.atm.app.service.DuplicateAccountException) {
                _snackbar.value = SnackbarMessage(e.message ?: "Account already exists")
                authRepository.checkAuthState()
                loadCachedSubscriptions()
            } catch (_: Exception) {
            } finally {
                if (showSyncing) _isProfileSyncing.value = false
            }
        }
    }

    fun updateProfile(updated: UserProfile) {
        val previous = _profile.value
        _profile.value = updated
        viewModelScope.launch {
            _isProfileUpdating.value = true
            try {
                val token = tokenDataStore.getAccessToken()
                    ?: throw RuntimeException("Not authenticated")
                val deviceUid = tokenDataStore.getDeviceUid()
                restClient.updateAccount(
                    token, deviceUid,
                    name = updated.name,
                    surname = updated.surname,
                    phone = updated.phone,
                    phonePrefix = updated.phonePrefix,
                    birthDate = updated.birthDate
                )
                fetchAndApplyProfile()
                val serverProfile = _profile.value
                if (updated.name != serverProfile.name || updated.surname != serverProfile.surname ||
                    updated.phone != serverProfile.phone) {
                    _snackbar.value = SnackbarMessage("Some changes were not accepted by the server")
                }
            } catch (e: Exception) {
                _profile.value = previous
                _snackbar.value = SnackbarMessage(e.message ?: "Update failed")
            } finally {
                _isProfileUpdating.value = false
            }
        }
    }

    fun refreshProfile() {
        loadProfile()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _snackbar.value = null
            try {
                val token = tokenDataStore.getAccessToken()
                    ?: throw RuntimeException("Not authenticated")
                val deviceUid = tokenDataStore.getDeviceUid()
                subscriptionRepository.syncSubscriptions(token, deviceUid)
                val updated = subscriptionDataStore.getSubscriptions().first()
                _subscriptions.value = updated
            } catch (e: Exception) {
                _snackbar.value = SnackbarMessage(e.message ?: "Sync failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshTickets() {
        viewModelScope.launch {
            _isTicketsLoading.value = true
            try {
                val token = tokenDataStore.getAccessToken() ?: return@launch
                val deviceUid = tokenDataStore.getDeviceUid()
                val response = restClient.fetchTickets(token, deviceUid)
                val all = mutableListOf<Ticket>()
                response.ticketsTPL?.let { all.addAll(it) }
                response.integratedTicketsTPL?.let { all.addAll(it) }
                response.subscriptions?.let { all.addAll(it) }
                response.ticketsTI?.let { all.addAll(it) }
                response.ticketsItabus?.let { all.addAll(it) }
                response.ticketsGT?.let { all.addAll(it) }
                response.ticketsItalo?.let { all.addAll(it) }
                _tickets.value = all
            } catch (_: Exception) {
            } finally {
                _isTicketsLoading.value = false
            }
        }
    }

    fun exportSession(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _snackbar.value = null
            try {
                val session = SessionJson.exportFrom(tokenDataStore, subscriptionDataStore)
                val json = session.toJson()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw RuntimeException("Cannot open output stream")
                _snackbar.value = SnackbarMessage("Session exported successfully", isError = false)
            } catch (e: Exception) {
                _snackbar.value = SnackbarMessage(e.message ?: "Export failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importSession(json: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _snackbar.value = null
            try {
                val session = SessionJson.fromJson(json)
                session.importInto(tokenDataStore, accountManager)
                authRepository.checkAuthState()
                fetchAndApplyProfile()
                val token = tokenDataStore.getAccessToken()
                    ?: throw RuntimeException("Not authenticated")
                val deviceUid = tokenDataStore.getDeviceUid()
                val subs = subscriptionRepository.syncSubscriptions(token, deviceUid)
                _subscriptions.value = subs
            } catch (e: it.atm.app.service.DuplicateAccountException) {
                authRepository.checkAuthState()
                loadCachedSubscriptions()
                _snackbar.value = SnackbarMessage(e.message ?: "Account already exists")
            } catch (_: Exception) {
                _snackbar.value = SnackbarMessage("Invalid session file")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun invalidateAndLogout() {
        manualLogout = true
        authRepository.invalidate()
        _subscriptions.value = emptyList()
        // Clean up data in background
        viewModelScope.launch {
            try {
                tokenDataStore.clearAll()
                subscriptionDataStore.clearAll()
            } catch (_: Exception) {}
        }
    }

    fun removeAccount(accountId: String) {
        val isActive = accountManager.activeAccountId.value == accountId
        manualLogout = true
        viewModelScope.launch {
            accountManager.removeAccount(accountId)
            if (isActive) {
                val remaining = accountManager.accounts.value
                if (remaining.isEmpty()) {
                    authRepository.invalidate()
                    _subscriptions.value = emptyList()
                    subscriptionDataStore.clearAll()
                    _loggedOut.value = true
                } else {
                    // Switch to first remaining account
                    accountManager.switchTo(remaining.first().id)
                    authRepository.checkAuthState()
                    loadCachedSubscriptions()
                    loadProfile()
                }
            }
        }
    }

    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            accountManager.switchTo(accountId)
            authRepository.checkAuthState()
            loadCachedSubscriptions()
            loadProfile()
        }
    }

    private var previousAccountId: String? = null

    fun prepareAddAccount() {
        previousAccountId = accountManager.activeAccountId.value
        manualLogout = true
        kotlinx.coroutines.runBlocking {
            accountManager.createPendingAccount()
        }
        authRepository.invalidate()
    }

    fun cancelAddAccount() {
        viewModelScope.launch {
            accountManager.cancelPendingAccount(previousAccountId)
            authRepository.checkAuthState()
            loadCachedSubscriptions()
            loadProfile()
        }
    }

    fun addNewAccountCompleted() {
        viewModelScope.launch {
            loadProfile()
            loadCachedSubscriptions()
        }
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }
}

class HomeViewModelFactory(
    private val app: AtmApp
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
