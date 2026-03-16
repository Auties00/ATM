package it.atm.app.ui.qrcode

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.atm.app.data.local.SubscriptionDataStore
import it.atm.app.data.local.db.SubscriptionEntity
import it.atm.app.data.remote.vts.VtsSoapClient
import it.atm.app.qr.BarcodeEncoder
import it.atm.app.qr.QrPayloadBuilder
import it.atm.app.auth.AccountManager
import it.atm.app.util.DateFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import it.atm.app.util.AppLogger
import javax.inject.Inject

@HiltViewModel
class QrCodeViewModel @Inject constructor(
    private val subscriptionDataStore: SubscriptionDataStore,
    private val vtsSoapClient: VtsSoapClient,
    private val accountManager: AccountManager
) : ViewModel() {

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

    fun loadSubscription(index: Int) {
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

    fun stop() {
        syncJob?.cancel()
        timerJob?.cancel()
        ticketData = null
        _qrBitmap.value = null
        _error.value = null
    }

    private suspend fun forceSyncAndShow(subscription: SubscriptionEntity, index: Int) {
        val account = accountManager.getActiveAccount()
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

        val cachedConfig = subscriptionDataStore.getQrConfig()
        sigType = cachedConfig.sigType
        keyId = cachedConfig.initialKeyId
        qrCodeFormat = cachedConfig.qrCodeFormat

        try {
            val sessionId = vtsSoapClient.initSession(deviceUid)
            try {
                vtsSoapClient.setClientInfo(sessionId)
                val freshQrConfig = vtsSoapClient.getServerInfo(sessionId)
                vtsSoapClient.getClientInfo(sessionId)

                sigType = freshQrConfig.sigType
                keyId = freshQrConfig.initialKeyId
                qrCodeFormat = freshQrConfig.qrCodeFormat
                subscriptionDataStore.saveQrConfig(freshQrConfig)

                if (tokenRequiresGeneration(tokenData)) {
                    AppLogger.d("QR","Token requiresGeneration=true, regenerating...")
                    try {
                        vtsSoapClient.generateVToken(sessionId, vtokenUid, subscription.signatureCount.coerceAtLeast(1))
                    } catch (_: Exception) {}

                    val freshB64 = vtsSoapClient.getVToken(sessionId, vtokenUid)
                    if (!freshB64.isNullOrBlank()) {
                        tokenData = Base64.decode(freshB64, Base64.DEFAULT)
                        dataB64 = freshB64
                        account?.id?.let { accountId ->
                            subscriptionDataStore.updateCachedData(accountId, vtokenUid, freshB64)
                        }
                    }
                }

                vtsSoapClient.changeVTokenStatus(sessionId, vtokenUid, status = 0)
                subscriptionDataStore.setActiveNfcSubscriptionIndex(index)
            } finally {
                vtsSoapClient.closeSession(sessionId)
            }
        } catch (e: Exception) {
            AppLogger.w("QR","VTS sync/activation failed: %s", e.message)
        }

        ticketData = tokenData
        startQrLoop()
    }

    private fun tokenRequiresGeneration(data: ByteArray): Boolean {
        if (data.size < 57) return false
        return QrPayloadBuilder.parseHeader(data).requiresGeneration
    }

    private fun startQrLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                generateQr()
                for (remaining in 25 downTo 1) {
                    _secondsRemaining.value = remaining
                    delay(1000L)
                }
            }
        }
    }

    private fun generateQr() {
        val data = ticketData ?: return
        try {
            val qrBytes = QrPayloadBuilder.buildQrData(
                data = data,
                qrCodeFormat = qrCodeFormat,
                sigType = sigType,
                sigKey = keyId,
            )
            _qrBitmap.value = BarcodeEncoder.generateQr(qrBytes, 1024)
        } catch (e: Exception) {
            AppLogger.e("QR","QR generation failed: %s", e.message)
            _error.value = "QR generation failed: ${e.message}"
        }
    }

    private fun formatValidity(subscription: SubscriptionEntity): String {
        return buildString {
            if (subscription.startValidity.isNotBlank()) {
                append("Valid: ${DateFormatter.formatDate(subscription.startValidity)}")
            }
            if (subscription.endValidity.isNotBlank()) {
                append(" - ${DateFormatter.formatDate(subscription.endValidity)}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        timerJob?.cancel()
        _qrBitmap.value?.recycle()
        _qrBitmap.value = null
    }
}
