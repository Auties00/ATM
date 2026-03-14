package it.atm.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.atm.app.data.local.SessionJson
import it.atm.app.data.local.AuthBlock
import it.atm.app.data.local.SessionSubscription
import it.atm.app.data.local.SubscriptionDataStore
import it.atm.app.data.local.TokenDataStore
import it.atm.app.data.local.db.SubscriptionEntity
import it.atm.app.data.remote.rest.AccountProfileResponse
import it.atm.app.data.remote.rest.AtmRestClient
import it.atm.app.data.remote.rest.Ticket
import it.atm.app.domain.model.AuthStatus
import it.atm.app.domain.model.UserProfile
import it.atm.app.domain.repository.AuthRepository
import it.atm.app.domain.repository.SubscriptionRepository
import it.atm.app.domain.repository.TicketRepository
import it.atm.app.service.AccountManager
import it.atm.app.service.DuplicateAccountException
import it.atm.app.util.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val ticketRepository: TicketRepository,
    private val tokenDataStore: TokenDataStore,
    private val subscriptionDataStore: SubscriptionDataStore,
    private val accountManager: AccountManager,
    private val restClient: AtmRestClient
) : ViewModel() {

    val accounts = accountManager.accounts
    val activeAccountId = accountManager.activeAccountId

    private val _subscriptions = MutableStateFlow<List<SubscriptionEntity>>(emptyList())
    val subscriptions: StateFlow<List<SubscriptionEntity>> = _subscriptions.asStateFlow()

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
                if (status is AuthStatus.NeedsLogin && !manualLogout && _subscriptions.value.isNotEmpty()) {
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
        restClient.syncAccount(token, deviceUid)
        when (val result = restClient.fetchAccountProfile(token, deviceUid)) {
            is AppResult.Success -> {
                val p = result.data
                _profile.value = UserProfile(
                    name = p.name,
                    surname = p.surname,
                    email = p.email,
                    confirmedEmail = p.confirmedEmail,
                    phone = p.phone,
                    phonePrefix = p.phonePrefix,
                    birthDate = p.birthDate,
                    imagePath = p.imagePath,
                )
                val profile = _profile.value
                if (accountManager.getActiveAccount()?.id == AccountManager.PENDING_ACCOUNT_ID
                    && profile.email.isNotBlank()) {
                    accountManager.finalizePendingAccount(profile.email, profile.name, profile.surname)
                } else {
                    accountManager.updateActiveAccount {
                        it.copy(name = profile.name, surname = profile.surname, email = profile.email)
                    }
                }
            }
            is AppResult.Error -> throw result.exception
        }
    }

    private fun loadProfile(showSyncing: Boolean = true) {
        viewModelScope.launch {
            if (showSyncing) _isProfileSyncing.value = true
            try {
                fetchAndApplyProfile()
            } catch (e: DuplicateAccountException) {
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
                when (val result = restClient.updateAccount(
                    token, deviceUid,
                    name = updated.name,
                    surname = updated.surname,
                    phone = updated.phone,
                    phonePrefix = updated.phonePrefix,
                    birthDate = updated.birthDate
                )) {
                    is AppResult.Success -> {
                        fetchAndApplyProfile()
                        val serverProfile = _profile.value
                        if (updated.name != serverProfile.name || updated.surname != serverProfile.surname ||
                            updated.phone != serverProfile.phone) {
                            _snackbar.value = SnackbarMessage("Some changes were not accepted by the server")
                        }
                    }
                    is AppResult.Error -> throw result.exception
                }
            } catch (e: Exception) {
                _profile.value = previous
                _snackbar.value = SnackbarMessage(e.message ?: "Update failed")
            } finally {
                _isProfileUpdating.value = false
            }
        }
    }

    fun refreshProfile() { loadProfile() }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _snackbar.value = null
            try {
                val token = tokenDataStore.getAccessToken()
                    ?: throw RuntimeException("Not authenticated")
                val deviceUid = tokenDataStore.getDeviceUid()
                when (val result = subscriptionRepository.syncSubscriptions(token, deviceUid)) {
                    is AppResult.Success -> _subscriptions.value = result.data
                    is AppResult.Error -> _snackbar.value = SnackbarMessage(result.exception.message ?: "Sync failed")
                }
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
                when (val result = ticketRepository.fetchTickets(token, deviceUid)) {
                    is AppResult.Success -> _tickets.value = result.data
                    is AppResult.Error -> Timber.tag("REST").w("Ticket fetch failed: %s", result.exception.message)
                }
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
                val subs = subscriptionDataStore.getSubscriptions().first()
                val session = SessionJson(
                    version = 1,
                    auth = AuthBlock(
                        accessToken = tokenDataStore.getAccessToken(),
                        refreshToken = tokenDataStore.getRefreshToken(),
                        tokenType = tokenDataStore.getTokenType(),
                        expiresAt = tokenDataStore.getExpiresAt()
                    ),
                    deviceUid = try { tokenDataStore.getDeviceUid() } catch (_: Exception) { null },
                    subscriptions = subs.map { s ->
                        SessionSubscription(
                            cardCode = s.cardCode, cardNumber = s.cardNumber, serialNumber = s.serialNumber,
                            holderId = s.holderId, title = s.title, subtitle = s.subtitle, profile = s.profile,
                            name = s.name, startValidity = s.startValidity, endValidity = s.endValidity,
                            carrierCode = s.carrierCode, status = s.status, cachedDataOutBin = s.cachedDataOutBin,
                            vtokenUid = s.vtokenUid, signatureCount = s.signatureCount
                        )
                    },
                    lastSync = subscriptionDataStore.getLastSync()
                )
                val json = session.toJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: throw RuntimeException("Cannot open output stream")
                _snackbar.value = SnackbarMessage("Session exported successfully", isError = false)
            } catch (e: Exception) {
                _snackbar.value = SnackbarMessage(e.message ?: "Export failed")
            } finally {
                _isLoading.value = false
            }
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
        viewModelScope.launch {
            accountManager.createPendingAccount()
            authRepository.invalidate()
        }
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

    fun clearSnackbar() { _snackbar.value = null }
}
