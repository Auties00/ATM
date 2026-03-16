package it.atm.app.ui.home

import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import it.atm.app.data.remote.rest.Ticket
import it.atm.app.ui.home.tab.AccountTab
import it.atm.app.ui.home.tab.AccountSwitcherPage
import it.atm.app.ui.home.tab.SubscriptionsTab
import it.atm.app.ui.home.tab.TicketsTab
import it.atm.app.ui.login.LoginViewModel
import it.atm.app.ui.qrcode.SubscriptionPage
import it.atm.app.ui.qrcode.QrCodeViewModel
import it.atm.app.ui.ticket.TicketQrScreen
import it.atm.app.ui.ticket.TicketQrViewModel
import it.atm.app.auth.AccountManager
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

private const val BUY_TICKETS_URL = "https://giromilano.atm.it/#!/shopping"

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onImportFromDevice: () -> Unit,
    onLogout: () -> Unit
) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val tickets by viewModel.tickets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isTicketsLoading by viewModel.isTicketsLoading.collectAsState()
    val snackbarMessage by viewModel.snackbar.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val isProfileSyncing by viewModel.isProfileSyncing.collectAsState()
    val isProfileUpdating by viewModel.isProfileUpdating.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val activeAccountId by viewModel.activeAccountId.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAddAccountSheet by remember { mutableStateOf(false) }
    val addAccountReady by viewModel.addAccountReady.collectAsState()
    var showAccountSwitcher by remember { mutableStateOf(false) }

    LaunchedEffect(addAccountReady) {
        if (addAccountReady) showAddAccountSheet = true
    }
    var showQrForIndex by remember { mutableStateOf<Int?>(null) }
    var showTicketDetail by remember { mutableStateOf<Ticket?>(null) }

    val exportFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportSession(context, it) }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg.text)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(loggedOut) {
        if (loggedOut) onLogout()
    }

    var bottomBarPadding by remember { mutableStateOf(0.dp) }

    Box {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Icon(
                                if (selectedTab == 0) Icons.Rounded.ConfirmationNumber
                                else Icons.Outlined.ConfirmationNumber,
                                contentDescription = null
                            )
                        },
                        label = { Text("Subscriptions") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Icon(
                                if (selectedTab == 1) Icons.Rounded.DirectionsBus
                                else Icons.Outlined.DirectionsBus,
                                contentDescription = null
                            )
                        },
                        label = { Text("Tickets") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Icon(
                                if (selectedTab == 2) Icons.Rounded.Person
                                else Icons.Outlined.Person,
                                contentDescription = null
                            )
                        },
                        label = { Text("Account") }
                    )
                }
            },
            floatingActionButton = {
                when (selectedTab) {
                    0 -> ExtendedFloatingActionButton(
                        onClick = onImportFromDevice,
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text("Add Subscription") },
                        shape = MaterialTheme.shapes.extraLarge
                    )
                    1 -> ExtendedFloatingActionButton(
                        onClick = {
                            CustomTabsIntent.Builder().build()
                                .launchUrl(context, Uri.parse(BUY_TICKETS_URL))
                        },
                        icon = { Icon(Icons.Outlined.ShoppingCart, contentDescription = null) },
                        text = { Text("Buy Tickets") },
                        shape = MaterialTheme.shapes.extraLarge
                    )
                    2 -> ExtendedFloatingActionButton(
                        onClick = { exportFilePicker.launch("atm_session.json") },
                        icon = { Icon(Icons.Outlined.SaveAlt, contentDescription = null) },
                        text = { Text("Export Session") },
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }
            }
        ) { paddingValues ->
            bottomBarPadding = paddingValues.calculateBottomPadding()
            when (selectedTab) {
                0 -> SubscriptionsTab(
                    subscriptions = subscriptions,
                    isLoading = isLoading,
                    onRefresh = { viewModel.refresh() },
                    onSubscriptionClick = { index -> showQrForIndex = index },
                    modifier = Modifier.padding(paddingValues)
                )
                1 -> TicketsTab(
                    tickets = tickets,
                    isLoading = isTicketsLoading,
                    onRefresh = { viewModel.refreshTickets() },
                    onTicketClick = { ticket -> showTicketDetail = ticket },
                    modifier = Modifier.padding(paddingValues)
                )
                2 -> AccountTab(
                    profile = profile,
                    isSyncing = isProfileSyncing,
                    isUpdating = isProfileUpdating,
                    accounts = accounts,
                    activeAccountId = activeAccountId,
                    onUpdateProfile = { updated -> viewModel.updateProfile(updated) },
                    onLogout = { accountId -> viewModel.removeAccount(accountId) },
                    onSwitchAccount = { accountId -> viewModel.switchAccount(accountId) },
                    onAddAccount = { viewModel.prepareAddAccount() },
                    onShowAccountSwitcher = { showAccountSwitcher = true },
                    onRefresh = { viewModel.refreshProfile() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }

        val isErrorSnackbar = snackbarMessage?.isError != false
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = bottomBarPadding)
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = MaterialTheme.shapes.medium,
                containerColor = if (isErrorSnackbar) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isErrorSnackbar) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }

    if (showAddAccountSheet) {
        val addAccountLoginViewModel: LoginViewModel = hiltViewModel(key = "add_account_${viewModel.addAccountKey}")
        val addAccountUiState by addAccountLoginViewModel.uiState.collectAsState()

        LaunchedEffect(addAccountUiState.isAuthenticated) {
            if (addAccountUiState.isAuthenticated) {
                showAddAccountSheet = false
                showAccountSwitcher = false
                viewModel.addNewAccountCompleted()
            }
        }

        LaunchedEffect(addAccountUiState.error) {
            addAccountUiState.error?.let { error ->
                addAccountLoginViewModel.clearError()
                showAddAccountSheet = false
                showAccountSwitcher = false
                viewModel.cancelAddAccount()
                viewModel.showSnackbar(error)
            }
        }

        val addAccountAuthLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data = result.data
            if (data != null) {
                val response = AuthorizationResponse.fromIntent(data)
                val exception = AuthorizationException.fromIntent(data)
                addAccountLoginViewModel.handleAuthResult(response, exception)
            }
        }

        val addAccountFilePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                try {
                    val json = context.contentResolver.openInputStream(it)
                        ?.bufferedReader()?.use { r -> r.readText() }
                    if (json != null) addAccountLoginViewModel.importSession(json)
                } catch (_: Exception) {}
            }
        }

        AlertDialog(
            onDismissRequest = {
                showAddAccountSheet = false
                viewModel.cancelAddAccount()
            },
            title = {
                Text(
                    text = "Add account",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (addAccountUiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Button(
                            onClick = {
                                addAccountLoginViewModel.clearError()
                                addAccountLoginViewModel.initiateLogin(context, addAccountAuthLauncher)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Outlined.Person, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in with ATM account")
                        }

                        OutlinedButton(
                            onClick = {
                                addAccountLoginViewModel.clearError()
                                addAccountFilePicker.launch(arrayOf("application/json", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import session")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showAddAccountSheet = false
                    viewModel.cancelAddAccount()
                }) { Text("Cancel") }
            }
        )
    }

    showQrForIndex?.let { index ->
        val qrViewModel: QrCodeViewModel = hiltViewModel(key = "qr_$index")
        DisposableEffect(index) {
            qrViewModel.loadSubscription(index)
            onDispose { qrViewModel.stop() }
        }
        SubscriptionPage(
            viewModel = qrViewModel,
            onDismiss = { showQrForIndex = null }
        )
    }

    showTicketDetail?.let { ticket ->
        val ticketQrViewModel: TicketQrViewModel = hiltViewModel(key = "ticket_qr_${ticket.ticketId}")
        DisposableEffect(ticket.ticketId) {
            ticketQrViewModel.loadTicket(ticket)
            onDispose { ticketQrViewModel.stop() }
        }
        TicketQrScreen(
            viewModel = ticketQrViewModel,
            onDismiss = {
                showTicketDetail = null
                viewModel.refreshTickets()
            }
        )
    }

    if (showAccountSwitcher) {
        val switcherActiveId = if (activeAccountId == AccountManager.PENDING_ACCOUNT_ID)
            viewModel.previousAccountId else activeAccountId
        AccountSwitcherPage(
            accounts = accounts.filter { it.id != AccountManager.PENDING_ACCOUNT_ID },
            activeAccountId = switcherActiveId,
            onDismiss = { showAccountSwitcher = false },
            onLogout = { id -> showAccountSwitcher = false; viewModel.removeAccount(id) },
            onSwitchAccount = { id -> showAccountSwitcher = false; viewModel.switchAccount(id) },
            onAddAccount = { viewModel.prepareAddAccount() }
        )
    }
}
