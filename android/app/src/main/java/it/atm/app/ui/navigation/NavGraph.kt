package it.atm.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import it.atm.app.AtmApp
import it.atm.app.ui.home.HomeScreen
import it.atm.app.ui.home.HomeViewModel
import it.atm.app.ui.home.HomeViewModelFactory
import it.atm.app.ui.login.LoginScreen
import it.atm.app.ui.login.LoginViewModel
import it.atm.app.ui.login.LoginViewModelFactory
import it.atm.app.ui.onboarding.OnboardingScreen
import it.atm.app.ui.onboarding.OnboardingViewModel
import it.atm.app.ui.onboarding.OnboardingViewModelFactory

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val app = context.applicationContext as AtmApp

    // Resolve start destination once, asynchronously
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val hasToken = app.tokenDataStore.getAccessToken() != null
        val onboardingDone = app.tokenDataStore.isOnboardingComplete()
        startDestination = when {
            !hasToken -> Screen.Login.route
            !onboardingDone -> Screen.Onboarding.route
            else -> Screen.Home.route
        }
    }

    val dest = startDestination ?: return // Don't render until resolved

    NavHost(
        navController = navController,
        startDestination = dest
    ) {
        composable(Screen.Login.route) {
            val viewModel: LoginViewModel = viewModel(
                factory = LoginViewModelFactory(app)
            )
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
            val viewModel: OnboardingViewModel = viewModel(
                factory = OnboardingViewModelFactory(app)
            )
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
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModelFactory(app)
            )
            HomeScreen(
                viewModel = viewModel,
                loginViewModelFactory = LoginViewModelFactory(app),
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
