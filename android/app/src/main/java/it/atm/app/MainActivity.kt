package it.atm.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.atm.app.ui.navigation.NavGraph
import it.atm.app.ui.theme.AtmTheme
import kotlinx.coroutines.flow.MutableStateFlow
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

class MainActivity : ComponentActivity() {

    companion object {
        val pendingAuthResponse = MutableStateFlow<AuthorizationResponse?>(null)
        val pendingAuthException = MutableStateFlow<AuthorizationException?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthRedirect(intent)
        setContent {
            AtmTheme {
                NavGraph()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthRedirect(intent)
    }

    private fun handleAuthRedirect(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "it.atm.appmobile.auth") return
        pendingAuthResponse.value = AuthorizationResponse.fromIntent(intent)
        pendingAuthException.value = AuthorizationException.fromIntent(intent)
    }
}
