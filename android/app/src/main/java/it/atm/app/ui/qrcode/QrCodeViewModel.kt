package it.atm.app.ui.qrcode

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.atm.app.AtmApp
import it.atm.app.data.remote.rest.Subscription
import it.atm.app.qr.QrBitmapGenerator
import it.atm.app.qr.QrPayloadBuilder
import it.atm.app.data.remote.vts.VtsSoapClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class QrCodeViewModel(
    private val app: AtmApp
) : ViewModel() {

    private val subscriptionDataStore = app.subscriptionDataStore
    private val vtsSoapClient: VtsSoapClient = VtsSoapClient(app.httpClient)

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _validUntil = MutableStateFlow("")
    val validUntil: StateFlow<String> = _validUntil.asStateFlow()

    private val _secondsRemaining = MutableStateFlow(25)
    val secondsRemaining: StateFlow<Int> = _secondsRemaining.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var ticketData: ByteArray? = null
    private var sigType: Int = 0
    private var keyId: Int = 0
    private var qrCodeFormat: Int = 1
    private var timerJob: Job? = null
    private var syncJob: Job? = null

    /**
     * Called every time the QR screen is shown. Always does a fresh VTS sync
     * (matching the original app's forceSynchronization on every card screen open).
     */
    fun loadSubscription(index: Int) {
        // Cancel any previous sync/timer — fresh start every time
        syncJob?.cancel()
        timerJob?.cancel()
        ticketData = null
        _qrBitmap.value = null
        _error.value = null

        syncJob = viewModelScope.launch {
            val subscriptions = subscriptionDataStore.getSubscriptions().first()
            if (index in subscriptions.indices) {
                val subscription = subscriptions[index]
                _title.value = subscription.title.ifBlank { "Subscription" }
                _validUntil.value = formatValidity(subscription)
                forceSyncAndShow(subscription, index)
            } else {
                _error.value = "Subscription not found"
            }
        }
    }

    /** Cancels background work when the QR screen is dismissed. */
    fun stop() {
        syncJob?.cancel()
        timerJob?.cancel()
        ticketData = null
        _qrBitmap.value = null
        _error.value = null
    }

    /**
     * Mirrors the original app's card screen flow:
     * 1. Load cached QR config as fallback (for offline mode)
     * 2. Open full VTS session (SetClientInfo, GetServerInfo, GetClientInfo)
     * 3. Update QR config (sigType/keyId/format) from server
     * 4. Check requiresGeneration flag (bit 4 of flags at byte offset 14 in DataOutBin)
     *    — if set, call GenerateVToken + GetVToken to refresh DataOutBin
     * 5. ChangeVTokenStatus to ACTIVE
     * 6. Start QR loop with the (possibly refreshed) data
     */
    private suspend fun forceSyncAndShow(subscription: Subscription, index: Int) {
        val account = app.accountManager.getActiveAccount()
        val deviceUid = account?.deviceUid
        val vtokenUid = subscription.vtokenUid
        var dataB64 = subscription.cachedDataOutBin

        if (deviceUid.isNullOrBlank() || vtokenUid.isBlank()) {
            _error.value = "No subscription data available"
            return
        }

        if (dataB64.isNullOrBlank()) {
            _error.value = "No ticket data. Sync your subscriptions first."
            return
        }

        var tokenData = try {
            Base64.decode(dataB64, Base64.DEFAULT)
        } catch (_: Exception) {
            _error.value = "Corrupted ticket data. Re-sync your subscriptions."
            return
        }

        // Initialize QR config from cache (fallback for offline mode)
        val cachedConfig = subscriptionDataStore.getQrConfig()
        sigType = cachedConfig.sigType
        keyId = cachedConfig.initialKeyId
        qrCodeFormat = cachedConfig.qrCodeFormat

        // Sync + activate via VTS session
        try {
            val sessionId = vtsSoapClient.initSession(deviceUid)
            try {
                vtsSoapClient.setClientInfo(sessionId)
                val freshQrConfig = vtsSoapClient.getServerInfo(sessionId)
                vtsSoapClient.getClientInfo(sessionId)

                // Update QR signing config from server
                sigType = freshQrConfig.sigType
                keyId = freshQrConfig.initialKeyId
                qrCodeFormat = freshQrConfig.qrCodeFormat
                subscriptionDataStore.saveQrConfig(freshQrConfig)

                // Check requiresGeneration flag from binary token header.
                if (tokenRequiresGeneration(tokenData)) {
                    android.util.Log.d("ATM_QR", "Token requiresGeneration=true, regenerating...")
                    try {
                        vtsSoapClient.generateVToken(
                            sessionId, vtokenUid,
                            subscription.signatureCount.coerceAtLeast(1)
                        )
                    } catch (_: Exception) {}

                    // Fetch fresh DataOutBin after generation
                    val freshB64 = vtsSoapClient.getVToken(sessionId, vtokenUid)
                    if (!freshB64.isNullOrBlank()) {
                        tokenData = Base64.decode(freshB64, Base64.DEFAULT)
                        dataB64 = freshB64

                        // Persist fresh data
                        val subs = subscriptionDataStore.getSubscriptions().first().toMutableList()
                        if (index in subs.indices) {
                            subs[index] = subs[index].copy(cachedDataOutBin = freshB64)
                            subscriptionDataStore.saveSubscriptions(subs)
                        }
                    }
                }

                // Activate the token (set status to ACTIVE=0)
                vtsSoapClient.changeVTokenStatus(sessionId, vtokenUid, status = 0)

                // Set this subscription as the NFC-active one for contactless validation
                subscriptionDataStore.setActiveNfcSubscriptionIndex(index)
            } finally {
                vtsSoapClient.closeSession(sessionId)
            }
        } catch (e: Exception) {
            android.util.Log.w("ATM_QR", "VTS sync/activation failed: ${e.message}")
            // Continue anyway — use cached config and data for offline mode
        }

        ticketData = tokenData
        startQrLoop()
    }

    /**
     * Checks the requiresGeneration flag using the VToken header parser.
     * Matches VtsVTokenByteParser flag parsing in the original SDK.
     */
    private fun tokenRequiresGeneration(data: ByteArray): Boolean {
        if (data.size < 57) return false
        return QrPayloadBuilder.parseHeader(data).requiresGeneration
    }

    /**
     * Periodically regenerates the QR code.
     *
     * The original app uses a fixed signing key from server config (no rotation).
     * For format 4, each regeneration produces a different QR code because
     * the VTSQ message includes the current timestamp. For format 1, the QR
     * code stays identical across refreshes.
     */
    private fun startQrLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                generateQr()
                for (remaining in 25 downTo 1) {
                    _secondsRemaining.value = remaining
                    delay(1000L)
                }
                // No key rotation — the original SDK uses a fixed key from server config.
                // The QR refresh is meaningful for format 4 (timestamp changes) and
                // serves as a UI heartbeat for format 1.
            }
        }
    }

    /**
     * Generates the QR code bitmap matching the original VTS SDK pipeline:
     * 1. Parse VToken header, extract payload at offset 57
     * 2. Build QR data according to QRCodeFormat (1-4)
     * 3. Optionally sign with AES-derived 4-byte checksum
     * 4. Encode raw bytes as ISO-8859-1 string into QR code via ZXing
     */
    private fun generateQr() {
        val data = ticketData ?: return
        try {
            val qrBytes = QrPayloadBuilder.buildQrData(
                data = data,
                qrCodeFormat = qrCodeFormat,
                sigType = sigType,
                sigKey = keyId,
            )
            _qrBitmap.value = QrBitmapGenerator.generate(qrBytes, 1024)
        } catch (e: Exception) {
            android.util.Log.e("ATM_QR", "QR generation failed: ${e.message}")
            _error.value = "QR generation failed: ${e.message}"
        }
    }

    private fun formatValidity(subscription: Subscription): String {
        return buildString {
            if (subscription.startValidity.isNotBlank()) {
                append("Valid: ${formatDate(subscription.startValidity)}")
            }
            if (subscription.endValidity.isNotBlank()) {
                append(" - ${formatDate(subscription.endValidity)}")
            }
        }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            val outputFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            val date = inputFormat.parse(dateStr)
            if (date != null) outputFormat.format(date) else dateStr
        } catch (e: Exception) {
            try {
                val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val outputFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                val date = inputFormat.parse(dateStr)
                if (date != null) outputFormat.format(date) else dateStr
            } catch (e2: Exception) {
                dateStr
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

class QrCodeViewModelFactory(
    private val app: AtmApp
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QrCodeViewModel::class.java)) {
            return QrCodeViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
