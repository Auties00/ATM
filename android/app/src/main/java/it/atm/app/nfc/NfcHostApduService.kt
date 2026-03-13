package it.atm.app.nfc

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import it.atm.app.AtmApp

/**
 * Android HCE service that handles NFC contactless validation for ATM subscriptions.
 *
 * Extends HostApduService and delegates all APDU processing to [ApduProtocol].
 * Registered in AndroidManifest with AID A0000007874145502E4E4643422E5654
 * (AEP NFC Virtual Ticket).
 *
 * The service is started by the system when an NFC reader selects the matching AID.
 * It works even with the screen locked (requireDeviceUnlock="false").
 */
class NfcHostApduService : HostApduService() {

    companion object {
        private const val TAG = "ATM_NFC"
        const val ACTION_EXCHANGE_COMPLETE = "it.atm.app.NFC_EXCHANGE_COMPLETE"
    }

    private lateinit var protocol: ApduProtocol

    override fun onCreate() {
        super.onCreate()
        val app = application as AtmApp
        val tokenStore = NfcTokenStore(app.accountManager)
        protocol = ApduProtocol(tokenStore)
        Log.d(TAG, "NfcHostApduService created")
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val response = protocol.processCommand(commandApdu)

        if (protocol.exchangeCompleted) {
            Log.d(TAG, "NFC exchange completed, broadcasting")
            sendBroadcast(Intent(ACTION_EXCHANGE_COMPLETE).setPackage(packageName))
        }

        return response
    }

    override fun onDeactivated(reason: Int) {
        val reasonStr = when (reason) {
            DEACTIVATION_LINK_LOSS -> "LINK_LOSS"
            DEACTIVATION_DESELECTED -> "DESELECTED"
            else -> "UNKNOWN($reason)"
        }
        Log.d(TAG, "NFC service deactivated: $reasonStr")
        protocol.reset()
    }
}
