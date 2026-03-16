package it.atm.app.ui.login

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.atm.app.data.local.SessionJson
import it.atm.app.data.local.TokenDataStore
import it.atm.app.data.remote.rest.AtmRestClient
import it.atm.app.domain.model.AuthStatus
import it.atm.app.domain.repository.AuthRepository
import it.atm.app.auth.AccountManager
import it.atm.app.auth.DuplicateAccountException

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import it.atm.app.util.AppLogger
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenDataStore: TokenDataStore,
    private val accountManager: AccountManager,
    private val restClient: AtmRestClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authStatus.collect { status ->
                when (status) {
                    is AuthStatus.Authenticated -> {
                        _uiState.update { it.copy(isLoading = false, isAuthenticated = true, error = null) }
                    }
                    is AuthStatus.Error -> {
                        _uiState.update { it.copy(isLoading = false, error = status.message) }
                    }
                    is AuthStatus.Idle, is AuthStatus.NeedsLogin -> {
                        _uiState.update { it.copy(isLoading = false, isAuthenticated = false) }
                    }
                }
            }
        }
        viewModelScope.launch { authRepository.checkAuthState() }
    }

    fun initiateLogin(context: Context, launcher: ActivityResultLauncher<Intent>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val authRequest = authRepository.buildAuthRequest()
                val authService = AuthorizationService(context)
                val authIntent = authService.getAuthorizationRequestIntent(authRequest)
                launcher.launch(authIntent)
                authService.dispose()
            } catch (e: Exception) {
                AppLogger.e("NAV","Login initiation failed: %s", e.message)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Login failed") }
            }
        }
    }

    fun handleAuthResult(response: AuthorizationResponse?, exception: AuthorizationException?) {
        viewModelScope.launch {
            if (exception != null) {
                if (exception.type == AuthorizationException.TYPE_GENERAL_ERROR
                    && exception.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code) {
                    _uiState.update { it.copy(isLoading = false) }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = exception.errorDescription ?: "Authentication failed")
                    }
                }
                return@launch
            }
            if (response != null) {
                _uiState.update { it.copy(isLoading = true, error = null) }
                try {
                    authRepository.exchangeCode(response)
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Token exchange failed") }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun importSession(json: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val session = SessionJson.fromJson(json)
                if (accountManager.getActiveAccount() == null) {
                    accountManager.createPendingAccount()
                }
                session.auth?.let { a ->
                    if (a.accessToken != null) {
                        tokenDataStore.saveTokens(
                            accessToken = a.accessToken,
                            refreshToken = a.refreshToken,
                            tokenType = a.tokenType ?: "Bearer",
                            expiresAt = a.expiresAt?.toLong() ?: (System.currentTimeMillis() + 3_600_000L)
                        )
                    }
                }
                session.deviceUid?.let { tokenDataStore.saveDeviceUid(it) }
                val token = tokenDataStore.getAccessToken()
                if (token != null) {
                    val deviceUid = tokenDataStore.getDeviceUid()
                    restClient.fetchAccountProfile(token, deviceUid).getOrNull()?.let { profile ->
                        if (profile.email.isNotBlank()) {
                            accountManager.finalizePendingAccount(profile.email, profile.name, profile.surname)
                        }
                    }
                }
                authRepository.checkAuthState()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: DuplicateAccountException) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Account already exists") }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Invalid session file") }
            }
        }
    }

    fun onResumed() {
        if (_uiState.value.isLoading && !_uiState.value.isAuthenticated) {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
