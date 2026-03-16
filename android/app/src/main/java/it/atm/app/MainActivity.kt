package it.atm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import it.atm.app.data.local.TokenDataStore
import it.atm.app.ui.navigation.NavGraph
import it.atm.app.ui.theme.AtmTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tokenDataStore: TokenDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtmTheme {
                NavGraph(tokenDataStore = tokenDataStore)
            }
        }
    }
}
