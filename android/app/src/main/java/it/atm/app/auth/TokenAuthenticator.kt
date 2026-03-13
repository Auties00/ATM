package it.atm.app.auth

import it.atm.app.data.local.TokenDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenDataStore: TokenDataStore,
    private val authRepository: () -> AuthRepository
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry if we already retried (avoid infinite loops)
        if (response.request.header("X-Token-Retry") != null) {
            return null
        }

        // Don't try to refresh for non-Bearer auth
        val authHeader = response.request.header("Authorization") ?: return null
        if (!authHeader.startsWith("Bearer ")) return null

        return runBlocking {
            try {
                authRepository().refreshAccessToken()
                val newToken = tokenDataStore.getAccessToken() ?: return@runBlocking null
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .header("X-Token-Retry", "1")
                    .build()
            } catch (_: Exception) {
                // Refresh failed — trigger logout
                try {
                    authRepository().logout()
                } catch (_: Exception) {}
                null
            }
        }
    }
}
