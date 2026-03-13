package it.atm.app.auth

import android.content.Context
import android.net.Uri
import it.atm.app.data.local.TokenDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

sealed class AuthStatus {
    object Idle : AuthStatus()
    data class Authenticated(val token: String) : AuthStatus()
    object NeedsLogin : AuthStatus()
    data class Error(val message: String) : AuthStatus()
}

class AuthRepository(
    private val context: Context,
    private val tokenDataStore: TokenDataStore,
    private val accountManager: it.atm.app.service.AccountManager
) {

    private val _authStatus = MutableStateFlow<AuthStatus>(AuthStatus.Idle)
    val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    private var serviceConfig: AuthorizationServiceConfiguration? = null

    suspend fun discoverConfiguration(): AuthorizationServiceConfiguration =
        withContext(Dispatchers.IO) {
            serviceConfig?.let { return@withContext it }
            val issuerUri = Uri.parse(AuthConstants.AUTH_ISSUER)
            val config = suspendCoroutine<AuthorizationServiceConfiguration> { cont ->
                AuthorizationServiceConfiguration.fetchFromIssuer(issuerUri) { config, ex ->
                    if (config != null) {
                        cont.resume(config)
                    } else {
                        cont.resumeWithException(
                            ex ?: RuntimeException("Failed to fetch OIDC configuration")
                        )
                    }
                }
            }
            serviceConfig = config
            config
        }

    suspend fun buildAuthRequest(): AuthorizationRequest {
        val config = discoverConfiguration()
        val nonce = generateNonce()
        return AuthorizationRequest.Builder(
            config,
            AuthConstants.CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(AuthConstants.REDIRECT_URI)
        )
            .setScope(AuthConstants.SCOPE)
            .setNonce(nonce)
            .setPrompt("login")
            .setUiLocales("en")
            .build()
    }

    suspend fun exchangeCode(authResponse: AuthorizationResponse) {
        withContext(Dispatchers.IO) {
            val authService = AuthorizationService(context)
            try {
                val tokenRequest = authResponse.createTokenExchangeRequest()
                val tokenResponse = suspendCoroutine<TokenResponse> { cont ->
                    authService.performTokenRequest(tokenRequest) { response, ex ->
                        if (response != null) {
                            cont.resume(response)
                        } else {
                            cont.resumeWithException(
                                ex ?: RuntimeException("Token exchange failed")
                            )
                        }
                    }
                }
                val accessToken = tokenResponse.accessToken
                    ?: throw RuntimeException("No access token in response")
                val refreshToken = tokenResponse.refreshToken
                val tokenType = tokenResponse.tokenType ?: "Bearer"
                val expiresAt = tokenResponse.accessTokenExpirationTime
                    ?: (System.currentTimeMillis() + 3_600_000L)

                // Ensure an account exists for this session
                if (accountManager.getActiveAccount() == null) {
                    accountManager.createPendingAccount()
                }

                tokenDataStore.saveTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    tokenType = tokenType,
                    expiresAt = expiresAt
                )

                _authStatus.value = AuthStatus.Authenticated(accessToken)
            } finally {
                authService.dispose()
            }
        }
    }

    suspend fun refreshAccessToken() {
        withContext(Dispatchers.IO) {
            val refreshToken = tokenDataStore.getRefreshToken()
                ?: throw RuntimeException("No refresh token available")
            val config = discoverConfiguration()
            val authService = AuthorizationService(context)
            try {
                val tokenRequest = TokenRequest.Builder(config, AuthConstants.CLIENT_ID)
                    .setGrantType("refresh_token")
                    .setRefreshToken(refreshToken)
                    .build()
                val tokenResponse = suspendCoroutine<TokenResponse> { cont ->
                    authService.performTokenRequest(tokenRequest) { response, ex ->
                        if (response != null) {
                            cont.resume(response)
                        } else {
                            cont.resumeWithException(
                                ex ?: RuntimeException("Token refresh failed")
                            )
                        }
                    }
                }
                val accessToken = tokenResponse.accessToken
                    ?: throw RuntimeException("No access token in refresh response")
                val newRefreshToken = tokenResponse.refreshToken ?: refreshToken
                val tokenType = tokenResponse.tokenType ?: "Bearer"
                val expiresAt = tokenResponse.accessTokenExpirationTime
                    ?: (System.currentTimeMillis() + 3_600_000L)

                tokenDataStore.saveTokens(
                    accessToken = accessToken,
                    refreshToken = newRefreshToken,
                    tokenType = tokenType,
                    expiresAt = expiresAt
                )

                _authStatus.value = AuthStatus.Authenticated(accessToken)
            } catch (e: Exception) {
                _authStatus.value = AuthStatus.NeedsLogin
                throw e
            } finally {
                authService.dispose()
            }
        }
    }

    suspend fun checkAuthState() {
        val accessToken = tokenDataStore.getAccessToken()
        if (accessToken == null) {
            _authStatus.value = AuthStatus.NeedsLogin
            return
        }
        val expiresAt = tokenDataStore.getExpiresAt()
        if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
            try {
                refreshAccessToken()
            } catch (e: Exception) {
                _authStatus.value = AuthStatus.NeedsLogin
            }
        } else {
            _authStatus.value = AuthStatus.Authenticated(accessToken)
        }
    }

    fun invalidate() {
        _authStatus.value = AuthStatus.NeedsLogin
    }

    suspend fun logout() {
        _authStatus.value = AuthStatus.NeedsLogin
        tokenDataStore.clearAll()
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
