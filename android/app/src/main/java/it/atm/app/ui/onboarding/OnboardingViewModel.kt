package it.atm.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.atm.app.AtmApp
import it.atm.app.data.remote.rest.AtmRestClient
import it.atm.app.data.remote.rest.CardItem
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImportPhase { Idle, Searching, Found, Empty, Transferring, Done }

data class OnboardingUiState(
    val phase: ImportPhase = ImportPhase.Idle,
    val cards: List<CardItem> = emptyList(),
    val error: String? = null
)

class OnboardingViewModel(
    private val app: AtmApp
) : ViewModel() {

    companion object {
        private const val CARRIER_CODE = "0723"
    }

    private val tokenDataStore = app.tokenDataStore

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun startSearch() {
        _uiState.update { it.copy(phase = ImportPhase.Searching, error = null) }
        viewModelScope.launch {
            val minDelay = async { delay(1500L) }
            try {
                val token = tokenDataStore.getAccessToken()
                    ?: throw RuntimeException("Not authenticated")
                val deviceUid = tokenDataStore.getDeviceUid()
                val restClient = AtmRestClient(app.httpClient)
                val checks = restClient.fetchChecks(token, deviceUid)
                val carriers = checks.aepTicketsMigrationsCarriers.orEmpty()
                if (carriers.isEmpty()) {
                    minDelay.await()
                    _uiState.update { it.copy(phase = ImportPhase.Empty) }
                } else {
                    // Fetch card previews so we can show them
                    val cards = try {
                        restClient.fetchUserCards(token, deviceUid, CARRIER_CODE)
                            .filter { it.valid }
                    } catch (_: Exception) { emptyList() }
                    minDelay.await()
                    _uiState.update { it.copy(
                        phase = ImportPhase.Found,
                        cards = cards
                    ) }
                }
            } catch (e: Exception) {
                minDelay.await()
                _uiState.update { it.copy(
                    phase = ImportPhase.Empty,
                    error = e.message ?: "Failed to check subscriptions"
                ) }
            }
        }
    }

    fun confirmImport() {
        _uiState.update { it.copy(phase = ImportPhase.Transferring, error = null) }
        viewModelScope.launch {
            try {
                val token = tokenDataStore.getAccessToken()
                    ?: throw RuntimeException("Not authenticated")
                val deviceUid = tokenDataStore.getDeviceUid()
                app.subscriptionRepository.transferSubscriptions(token, deviceUid)
                _uiState.update { it.copy(phase = ImportPhase.Done) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    phase = ImportPhase.Found,
                    error = e.message ?: "Migration failed"
                ) }
            }
        }
    }

    fun markComplete() {
        viewModelScope.launch {
            tokenDataStore.setOnboardingComplete()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

class OnboardingViewModelFactory(
    private val app: AtmApp
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            return OnboardingViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
