package it.atm.app.domain.model

sealed class AuthStatus {
    object Idle : AuthStatus()
    data class Authenticated(val token: String) : AuthStatus()
    object NeedsLogin : AuthStatus()
    data class Error(val message: String) : AuthStatus()
}
