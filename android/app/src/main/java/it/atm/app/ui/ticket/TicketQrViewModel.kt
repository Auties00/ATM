package it.atm.app.ui.ticket

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.aztec.AztecWriter
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import it.atm.app.AtmApp
import it.atm.app.data.remote.rest.AtmRestClient
import it.atm.app.data.remote.rest.Ticket
import it.atm.app.data.remote.rest.TicketQrCodeResponse
import it.atm.app.data.remote.rest.TicketStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TicketQrViewModel(
    private val app: AtmApp
) : ViewModel() {

    private val restClient = AtmRestClient(app.httpClient)
    private val tokenDataStore = app.tokenDataStore

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

        // Can validate if status is PURCHASED and manual validation is allowed
        _canValidate.value = ticket.displayStatus == TicketStatus.PURCHASED

        if (ticket.hasQrCode) {
            fetchQrCode(ticket)
        }
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
                restClient.validateTicket(token, deviceUid, ticket.ticketId)
                // After validation, refresh the ticket to get updated status and QR
                _canValidate.value = false
                _statusLabel.value = "Active"
                _statusColor.value = Color(0xFF358551)
                // Re-fetch QR code with validation
                if (ticket.hasQrCode) {
                    fetchQrCode(ticket)
                }
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

            // Initial fetch + auto-refresh loop
            while (true) {
                _isLoadingQr.value = true
                _error.value = null
                try {
                    val token = tokenDataStore.getAccessToken()
                        ?: throw RuntimeException("Not authenticated")
                    val deviceUid = tokenDataStore.getDeviceUid()
                    val qr = restClient.fetchTicketQrCode(
                        token, deviceUid,
                        ticket.ticketId,
                        ticket.activeValidationId
                    )
                    renderQr(qr)
                    _qrMessage.value = qr.qrCodeDescription ?: qr.qrCodeInfoValidationMessage
                } catch (e: Exception) {
                    if (_qrBitmap.value == null) {
                        _error.value = "Failed to load QR code: ${e.message}"
                    }
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
                // Server sent a pre-rendered base64 PNG image
                try {
                    val bytes = Base64.decode(value, Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    _error.value = "Failed to decode QR image"
                    return
                }
            }
            type == "QRCODE" || type.contains("QR") -> {
                encodeBarcode(value, BarcodeFormat.QR_CODE, 1024, 1024)
            }
            type == "CODE_128" || type == "CODE128" || type == "BARCODE" -> {
                encodeBarcode(value, BarcodeFormat.CODE_128, 1024, 400)
            }
            type == "AZTEC" -> {
                encodeBarcode(value, BarcodeFormat.AZTEC, 1024, 1024)
            }
            else -> {
                // Default to QR code
                encodeBarcode(value, BarcodeFormat.QR_CODE, 1024, 1024)
            }
        }
        _qrBitmap.value = bitmap
    }

    private fun encodeBarcode(data: String, format: BarcodeFormat, width: Int, height: Int): Bitmap {
        val hints = mutableMapOf<EncodeHintType, Any>()
        if (format == BarcodeFormat.QR_CODE) {
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.L
            hints[EncodeHintType.CHARACTER_SET] = "ISO-8859-1"
        }

        val writer = when (format) {
            BarcodeFormat.QR_CODE -> QRCodeWriter()
            BarcodeFormat.CODE_128 -> Code128Writer()
            BarcodeFormat.AZTEC -> AztecWriter()
            else -> QRCodeWriter()
        }
        val bitMatrix = writer.encode(data, format, width, height, hints)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                )
            }
        }
        return bitmap
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}

class TicketQrViewModelFactory(
    private val app: AtmApp
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TicketQrViewModel::class.java)) {
            return TicketQrViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
