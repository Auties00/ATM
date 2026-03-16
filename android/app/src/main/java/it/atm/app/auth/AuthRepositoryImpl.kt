package it.atm.app.auth

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.atm.app.data.local.TokenDataStore
import it.atm.app.domain.model.AuthStatus
import it.atm.app.domain.repository.AuthRepository
import it.atm.app.util.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import it.atm.app.util.AppLogger
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenDataStore: TokenDataStore,
    private val accountManager: AccountManager
) : AuthRepository {

    private val _authStatus = MutableStateFlow<AuthStatus>(AuthStatus.Idle)
    override val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    private var serviceConfig: AuthorizationServiceConfiguration? = null

    private suspend fun discoverConfiguration(): AuthorizationServiceConfiguration =
        withContext(Dispatchers.IO) {
            serviceConfig?.let { return@withContext it }
            AppLogger.d("AUTH","Discovering OIDC configuration")
            val issuerUri = Uri.parse(AuthConstants.AUTH_ISSUER)
            val config = suspendCoroutine<AuthorizationServiceConfiguration> { cont ->
                AuthorizationServiceConfiguration.fetchFromIssuer(issuerUri) { config, ex ->
                    if (config != null) cont.resume(config)
                    else cont.resumeWithException(ex ?: RuntimeException("Failed to fetch OIDC configuration"))
                }
            }
            serviceConfig = config
            config
        }

    override suspend fun buildAuthRequest(): AuthorizationRequest {
        val config = discoverConfiguration()
        val nonce = generateNonce()
        AppLogger.d("AUTH","Building auth request")
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

    override suspend fun exchangeCode(authResponse: AuthorizationResponse) {
        withContext(Dispatchers.IO) {
            AppLogger.d("AUTH","Exchanging authorization code")
            val authService = AuthorizationService(context)
            try {
                val tokenRequest = authResponse.createTokenExchangeRequest()
                val tokenResponse = suspendCoroutine<TokenResponse> { cont ->
                    authService.performTokenRequest(tokenRequest) { response, ex ->
                        if (response != null) cont.resume(response)
                        else cont.resumeWithException(ex ?: RuntimeException("Token exchange failed"))
                    }
                }
                val accessToken = tokenResponse.accessToken
                    ?: throw RuntimeException("No access token in response")
                val refreshToken = tokenResponse.refreshToken
                val tokenType = tokenResponse.tokenType ?: "Bearer"
                val expiresAt = tokenResponse.accessTokenExpirationTime
                    ?: (System.currentTimeMillis() + 3_600_000L)

                if (accountManager.getActiveAccount() == null) {
                    accountManager.createPendingAccount()
                }

                tokenDataStore.saveTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    tokenType = tokenType,
                    expiresAt = expiresAt
                )
                AppLogger.d("AUTH","Token exchange successful")
                _authStatus.value = AuthStatus.Authenticated(accessToken)
            } finally {
                authService.dispose()
            }
        }
    }

    override suspend fun refreshAccessToken() {
        withContext(Dispatchers.IO) {
            AppLogger.d("AUTH","Refreshing access token")
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
                        if (response != null) cont.resume(response)
                        else cont.resumeWithException(ex ?: RuntimeException("Token refresh failed"))
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
                AppLogger.d("AUTH","Token refresh successful")
                _authStatus.value = AuthStatus.Authenticated(accessToken)
            } catch (e: Exception) {
                AppLogger.w("AUTH","Token refresh failed: %s", e.message)
                _authStatus.value = AuthStatus.NeedsLogin
                throw e
            } finally {
                authService.dispose()
            }
        }
    }

    override suspend fun checkAuthState() {
        val accessToken = tokenDataStore.getAccessToken()
        if (accessToken == null) {
            _authStatus.value = AuthStatus.NeedsLogin
            return
        }
        val expiresAt = tokenDataStore.getExpiresAt()
        if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
            try {
                refreshAccessToken()
            } catch (_: Exception) {
                _authStatus.value = AuthStatus.NeedsLogin
            }
        } else {
            _authStatus.value = AuthStatus.Authenticated(accessToken)
        }
    }

    override fun invalidate() {
        _authStatus.value = AuthStatus.NeedsLogin
    }

    override suspend fun logout() {
        AppLogger.d("AUTH","Logging out")
        _authStatus.value = AuthStatus.NeedsLogin
        tokenDataStore.clearAll()
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }
}
