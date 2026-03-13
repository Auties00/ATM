package it.atm.app.domain.repository

import it.atm.app.domain.model.AuthStatus
import kotlinx.coroutines.flow.StateFlow
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse

interface AuthRepository {
    val authStatus: StateFlow<AuthStatus>
    suspend fun buildAuthRequest(): AuthorizationRequest
    suspend fun exchangeCode(authResponse: AuthorizationResponse)
    suspend fun refreshAccessToken()
    suspend fun checkAuthState()
    fun invalidate()
    suspend fun logout()
}
