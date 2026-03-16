package it.atm.app.ui.ticket

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import it.atm.app.data.local.TokenDataStore
import it.atm.app.data.remote.rest.Ticket
import it.atm.app.data.remote.rest.TicketQrCodeResponse
import it.atm.app.data.remote.rest.TicketStatus
import it.atm.app.domain.repository.TicketRepository
import it.atm.app.qr.BarcodeEncoder

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TicketQrViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val tokenDataStore: TokenDataStore
) : ViewModel() {

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _subtitle = MutableStateFlow("")
    val subtitle: StateFlow<String> = _subtitle.asStateFlow()

    private val _statusLabel = MutableStateFlow("")
    val statusLabel: StateFlow<String> = _statusLabel.asStateFlow()

    private val _statusColor = MutableStateFlow(Color.Gray)
    val statusColor: StateFlow<Color> = _statusColor.asStateFlow()

    private val _qrMessage = MutableStateFlow<String?>(null)
    val qrMessage: StateFlow<String?> = _qrMessage.asStateFlow()

    private val _isLoadingQr = MutableStateFlow(false)
    val isLoadingQr: StateFlow<Boolean> = _isLoadingQr.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _canValidate = MutableStateFlow(false)
    val canValidate: StateFlow<Boolean> = _canValidate.asStateFlow()

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    private var currentTicket: Ticket? = null
    private var refreshJob: Job? = null

    fun loadTicket(ticket: Ticket) {
        refreshJob?.cancel()
        currentTicket = ticket
        _qrBitmap.value = null
        _error.value = null
        _qrMessage.value = null

        _title.value = ticket.description.ifBlank { "Ticket" }
        _subtitle.value = buildString {
            if (!ticket.route.isNullOrBlank()) append(ticket.route)
            if (ticket.showAmount && ticket.amount > 0) {
                if (isNotBlank()) append(" \u2022 ")
                append("%.2f\u00A0\u20AC".format(ticket.amount))
            }
        }
        _statusLabel.value = when (ticket.displayStatus) {
            TicketStatus.PURCHASED -> "Purchased"
            TicketStatus.VALIDATED -> "Active"
            TicketStatus.EXPIRED -> "Expired"
            TicketStatus.UNKNOWN -> ""
        }
        _statusColor.value = when (ticket.displayStatus) {
            TicketStatus.PURCHASED -> Color(0xFFFFC627)
            TicketStatus.VALIDATED -> Color(0xFF358551)
            TicketStatus.EXPIRED -> Color(0xFFDD0000)
            TicketStatus.UNKNOWN -> Color.Gray
        }
        _canValidate.value = ticket.displayStatus == TicketStatus.PURCHASED

        if (ticket.hasQrCode) fetchQrCode(ticket)
    }

    fun stop() {
        refreshJob?.cancel()
        _qrBitmap.value = null
    }

    fun validate() {
        val ticket = currentTicket ?: return
        viewModelScope.launch {
            _isValidating.value = true
            try {
                val token = tokenDataStore.getAccessToken()
                    ?: throw RuntimeException("Not authenticated")
                val deviceUid = tokenDataStore.getDeviceUid()
                ticketRepository.validateTicket(token, deviceUid, ticket.ticketId).fold(
                    onSuccess = {
                        _canValidate.value = false
                        _statusLabel.value = "Active"
                        _statusColor.value = Color(0xFF358551)
                        if (ticket.hasQrCode) fetchQrCode(ticket)
                    },
                    onFailure = { e -> _error.value = "Validation failed: ${e.message}" }
                )
            } catch (e: Exception) {
                _error.value = "Validation failed: ${e.message}"
            } finally {
                _isValidating.value = false
            }
        }
    }

    private fun fetchQrCode(ticket: Ticket) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val settings = ticket.qrCodeSettings
            val autoRefresh = settings?.hasAutoRefresh == true
            val refreshInterval = settings?.refreshInterval ?: 30_000L

            while (true) {
                _isLoadingQr.value = true
                _error.value = null
                try {
                    val token = tokenDataStore.getAccessToken()
                        ?: throw RuntimeException("Not authenticated")
                    val deviceUid = tokenDataStore.getDeviceUid()
                    ticketRepository.fetchTicketQrCode(token, deviceUid, ticket.ticketId, ticket.activeValidationId).fold(
                        onSuccess = { data ->
                            renderQr(data)
                            _qrMessage.value = data.qrCodeDescription ?: data.qrCodeInfoValidationMessage
                        },
                        onFailure = { e ->
                            if (_qrBitmap.value == null) _error.value = "Failed to load QR code: ${e.message}"
                        }
                    )
                } catch (e: Exception) {
                    if (_qrBitmap.value == null) _error.value = "Failed to load QR code: ${e.message}"
                } finally {
                    _isLoadingQr.value = false
                }

                if (!autoRefresh) break
                delay(refreshInterval.coerceAtLeast(5_000L))
            }
        }
    }

    private fun renderQr(qr: TicketQrCodeResponse) {
        val value = qr.qrCodeValue
        if (value.isBlank()) {
            _error.value = "Server returned empty QR code"
            return
        }
        val type = qr.qrCodeType.uppercase()
        val bitmap = when {
            type == "QRCODE_BASE64_IMG" -> {
                try {
                    val bytes = Base64.decode(value, Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) {
                    _error.value = "Failed to decode QR image"
                    return
                }
            }
            type == "QRCODE" || type.contains("QR") -> BarcodeEncoder.encode(value, BarcodeFormat.QR_CODE, 1024, 1024)
            type == "CODE_128" || type == "CODE128" || type == "BARCODE" -> BarcodeEncoder.encode(value, BarcodeFormat.CODE_128, 1024, 400)
            type == "AZTEC" -> BarcodeEncoder.encode(value, BarcodeFormat.AZTEC, 1024, 1024)
            else -> BarcodeEncoder.encode(value, BarcodeFormat.QR_CODE, 1024, 1024)
        }
        _qrBitmap.value = bitmap
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
        _qrBitmap.value?.recycle()
        _qrBitmap.value = null
    }
}
