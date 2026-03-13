package it.atm.app.ui.login

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.atm.app.AtmApp
import it.atm.app.auth.AuthStatus
import it.atm.app.data.local.SessionJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false
)

class LoginViewModel(
    private val app: AtmApp
) : ViewModel() {

    private val authRepository = app.authRepository
    private val tokenDataStore = app.tokenDataStore

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

        viewModelScope.launch {
            authRepository.checkAuthState()
        }
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
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Login failed") }
            }
        }
    }

    fun handleAuthResult(response: AuthorizationResponse?, exception: AuthorizationException?) {
        viewModelScope.launch {
            if (exception != null) {
                // Don't show error for user-initiated cancellation
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
                session.importInto(tokenDataStore, app.accountManager)
                // Fetch profile to discover email and check for duplicate accounts
                val token = tokenDataStore.getAccessToken()
                if (token != null) {
                    val deviceUid = tokenDataStore.getDeviceUid()
                    val restClient = it.atm.app.data.remote.rest.AtmRestClient(app.httpClient)
                    val profile = restClient.fetchAccountProfile(token, deviceUid)
                    val email = profile.get("email")?.asString ?: ""
                    if (email.isNotBlank()) {
                        // finalizePendingAccount throws DuplicateAccountException if email exists
                        app.accountManager.finalizePendingAccount(email,
                            profile.get("name")?.asString ?: "",
                            profile.get("surname")?.asString ?: "")
                    }
                }
                authRepository.checkAuthState()
            } catch (e: it.atm.app.service.DuplicateAccountException) {
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

class LoginViewModelFactory(
    private val app: AtmApp
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            return LoginViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
