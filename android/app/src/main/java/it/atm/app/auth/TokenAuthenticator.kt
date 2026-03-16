package it.atm.app.auth

import it.atm.app.data.local.TokenDataStore
import it.atm.app.domain.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import it.atm.app.util.AppLogger

class TokenAuthenticator(
    private val tokenDataStore: TokenDataStore,
    private val authRepository: () -> AuthRepository
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("X-Token-Retry") != null) return null
        val authHeader = response.request.header("Authorization") ?: return null
        if (!authHeader.startsWith("Bearer ")) return null

        AppLogger.d("AUTH","401 received, attempting token refresh")
        return runBlocking {
            try {
                authRepository().refreshAccessToken()
                val newToken = tokenDataStore.getAccessToken() ?: return@runBlocking null
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .header("X-Token-Retry", "1")
                    .build()
            } catch (_: Exception) {
                AppLogger.w("AUTH","Token refresh in authenticator failed, triggering logout")
                try { authRepository().logout() } catch (_: Exception) {}
                null
            }
        }
    }
}
