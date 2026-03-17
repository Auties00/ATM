package it.atm.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import it.atm.app.data.local.TokenDataStore
import it.atm.app.ui.components.OfflineBanner
import it.atm.app.ui.home.HomeScreen
import it.atm.app.ui.home.HomeViewModel
import it.atm.app.ui.login.LoginScreen
import it.atm.app.ui.login.LoginViewModel
import it.atm.app.ui.onboarding.OnboardingScreen
import it.atm.app.ui.onboarding.OnboardingViewModel
import it.atm.app.util.ConnectivityObserver
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    tokenDataStore: TokenDataStore,
    connectivityObserver: ConnectivityObserver
) {
    var startDestination by remember { mutableStateOf<String?>(null) }
    val isOnline by connectivityObserver.isOnline.collectAsState(initial = true)

    LaunchedEffect(Unit) {
        val hasToken = tokenDataStore.getAccessToken() != null
        val onboardingDone = tokenDataStore.isOnboardingComplete()
        startDestination = when {
            !hasToken -> Screen.Login.route
            !onboardingDone -> Screen.Onboarding.route
            else -> Screen.Home.route
        }
    }

    val dest = startDestination ?: return

    Column {
        OfflineBanner(isOffline = !isOnline)

        NavHost(
            navController = navController,
            startDestination = dest,
            modifier = Modifier.weight(1f)
        ) {
            composable(Screen.Login.route) {
                val viewModel: LoginViewModel = hiltViewModel()
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Onboarding.route) {
                val viewModel: OnboardingViewModel = hiltViewModel()
                OnboardingScreen(
                    viewModel = viewModel,
                    onComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                val viewModel: HomeViewModel = hiltViewModel()
                HomeScreen(
                    viewModel = viewModel,
                    isOffline = !isOnline,
                    onImportFromDevice = {
                        navController.navigate(Screen.Onboarding.route)
                    },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
