package it.atm.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.atm.app.data.local.TokenDataStore
import it.atm.app.data.remote.rest.AtmRestClient
import it.atm.app.data.remote.rest.CardItem
import it.atm.app.domain.repository.SubscriptionRepository
import it.atm.app.util.AppResult
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class ImportPhase { Idle, Searching, Found, Empty, Transferring, Done }

data class OnboardingUiState(
    val phase: ImportPhase = ImportPhase.Idle,
    val cards: List<CardItem> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val tokenDataStore: TokenDataStore,
    private val restClient: AtmRestClient,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    companion object {
        private const val CARRIER_CODE = "0723"
    }

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
                when (val checksResult = restClient.fetchChecks(token, deviceUid)) {
                    is AppResult.Success -> {
                        val carriers = checksResult.data.aepTicketsMigrationsCarriers.orEmpty()
                        if (carriers.isEmpty()) {
                            minDelay.await()
                            _uiState.update { it.copy(phase = ImportPhase.Empty) }
                        } else {
                            val cards = when (val cardsResult = restClient.fetchUserCards(token, deviceUid, CARRIER_CODE)) {
                                is AppResult.Success -> cardsResult.data.filter { it.valid }
                                is AppResult.Error -> emptyList()
                            }
                            minDelay.await()
                            _uiState.update { it.copy(phase = ImportPhase.Found, cards = cards) }
                        }
                    }
                    is AppResult.Error -> {
                        minDelay.await()
                        _uiState.update { it.copy(phase = ImportPhase.Empty, error = checksResult.exception.message) }
                    }
                }
            } catch (e: Exception) {
                minDelay.await()
                _uiState.update { it.copy(phase = ImportPhase.Empty, error = e.message ?: "Failed to check subscriptions") }
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
                when (val result = subscriptionRepository.transferSubscriptions(token, deviceUid)) {
                    is AppResult.Success -> _uiState.update { it.copy(phase = ImportPhase.Done) }
                    is AppResult.Error -> _uiState.update { it.copy(phase = ImportPhase.Found, error = result.exception.message ?: "Migration failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(phase = ImportPhase.Found, error = e.message ?: "Migration failed") }
            }
        }
    }

    fun markComplete() {
        viewModelScope.launch { tokenDataStore.setOnboardingComplete() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
