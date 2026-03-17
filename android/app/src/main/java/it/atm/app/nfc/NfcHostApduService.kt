package it.atm.app.nfc

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Base64
import dagger.hilt.android.AndroidEntryPoint
import it.atm.app.data.remote.vts.VtsSoapClient
import it.atm.app.qr.QrPayloadBuilder
import it.atm.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NfcHostApduService : HostApduService() {

    companion object {
        const val ACTION_EXCHANGE_COMPLETE = "it.aep_italia.vts.nfc.EXCHANGE_COMPLETE"
        const val ACTION_EXCHANGE_ERROR = "it.aep_italia.vts.nfc.EXCHANGE_ERROR"
    }

    @Inject lateinit var tokenStore: NfcTokenStore
    @Inject lateinit var vtsSoapClient: VtsSoapClient

    private lateinit var protocol: ApduProtocol
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        protocol = ApduProtocol(tokenStore)
        AppLogger.d("NFC","NfcHostApduService created")
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val response = protocol.processCommand(commandApdu)
        if (protocol.exchangeCompleted) {
            val fullToken = protocol.updatedFullToken
            if (fullToken != null) {
                tokenStore.saveValidationStamp(fullToken)
                triggerPostExchangeSync(fullToken)
            }
            AppLogger.d("NFC","NFC exchange completed, broadcasting")
            sendBroadcast(Intent(ACTION_EXCHANGE_COMPLETE))
        }
        return response
    }

    override fun onDeactivated(reason: Int) {
        val reasonStr = when (reason) {
            DEACTIVATION_LINK_LOSS -> "LINK_LOSS"
            DEACTIVATION_DESELECTED -> "DESELECTED"
            else -> "UNKNOWN($reason)"
        }
        AppLogger.d("NFC","NFC service deactivated: %s", reasonStr)
        protocol.reset()
    }

    private fun triggerPostExchangeSync(updatedToken: ByteArray) {
        val deviceUid = tokenStore.getDeviceUid()
        if (deviceUid.isBlank()) return

        val header = try {
            QrPayloadBuilder.parseHeader(updatedToken)
        } catch (_: Exception) {
            AppLogger.w("NFC", "Could not parse VToken header for sync")
            return
        }

        val vtokenUid = String.format("%016X", header.uid)
        val signatureCount = tokenStore.getSignatureCount()
        val dataB64 = Base64.encodeToString(updatedToken, Base64.NO_WRAP)

        serviceScope.launch {
            try {
                AppLogger.d("NFC", "Post-exchange sync: uploading VToken %s", vtokenUid)
                val sessionId = vtsSoapClient.initSession(deviceUid)
                try {
                    vtsSoapClient.putVToken(sessionId, vtokenUid, signatureCount, dataB64)
                    AppLogger.d("NFC", "Post-exchange sync: upload complete")
                } finally {
                    vtsSoapClient.closeSession(sessionId)
                }
            } catch (e: Exception) {
                AppLogger.w("NFC", "Post-exchange sync failed (non-fatal): %s", e.message)
            }
        }
    }
}
