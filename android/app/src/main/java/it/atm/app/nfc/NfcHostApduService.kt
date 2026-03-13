package it.atm.app.nfc

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class NfcHostApduService : HostApduService() {

    companion object {
        const val ACTION_EXCHANGE_COMPLETE = "it.atm.app.NFC_EXCHANGE_COMPLETE"
    }

    @Inject lateinit var tokenStore: NfcTokenStore

    private lateinit var protocol: ApduProtocol

    override fun onCreate() {
        super.onCreate()
        protocol = ApduProtocol(tokenStore)
        Timber.tag("NFC").d("NfcHostApduService created")
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val response = protocol.processCommand(commandApdu)
        if (protocol.exchangeCompleted) {
            Timber.tag("NFC").d("NFC exchange completed, broadcasting")
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
        Timber.tag("NFC").d("NFC service deactivated: %s", reasonStr)
        protocol.reset()
    }
}
