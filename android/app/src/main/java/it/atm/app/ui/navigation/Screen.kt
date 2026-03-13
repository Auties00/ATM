package it.atm.app.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    data class QrCode(val subscriptionIndex: Int) : Screen("qr/$subscriptionIndex") {
        companion object {
            const val ROUTE_PATTERN = "qr/{index}"
            const val ARG_INDEX = "index"
        }
    }
}
