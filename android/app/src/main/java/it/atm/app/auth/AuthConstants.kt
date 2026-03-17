package it.atm.app.auth

object AuthConstants {
    const val AUTH_ISSUER = "https://be.atm.it/IdServer/"
    const val CLIENT_ID = "atmmi"
    const val REDIRECT_URI = "it.atm.appmobile.auth:/oauthredirect"
    const val SCOPE = "offline_access"

    const val TICKETING_BASE = "https://be.atm.it/app.gateway/api/transport.ticketing/v5"
    const val PASSES_BASE = "https://be.atm.it/app.gateway/api/transport.passes/"
    const val ACCOUNT_URL = "https://be.atm.it/app.gateway/api/userservice/v1/Account"
    const val ACCOUNT_SYNC_URL = "https://be.atm.it/app.gateway/api/userservice/v1/Account/Sync"

    const val VTS_URL = "https://api.atm.it/vts/v1"
    const val SYSTEM_TYPE = "3"
    const val SYSTEM_SUBTYPE = "1"
    const val APP_KEY = "f964f8521503d722556799c0a37d3a79"
    const val SDK_VERSION = "2.9.123"
    const val CERT_SUBJECT_DN = "CN=Roberto Carreri, OU=DSIT, O=ATM, L=Milano, ST=MI, C=IT"
    const val CERT_SHA256_B64 = "5mSEGV/qWj4WT1+xwZCFkAKAvLFe/Fti9VSrU2MmE0Y="
    const val CERT_SERIAL = "1417235406"

    const val APP_VERSION = "17.2.3"
    const val APP_BUILD = "17.2.3+2026010902"

    const val NS_SOAP = "http://www.w3.org/2003/05/soap-envelope"
    const val NS_ADDR = "http://www.w3.org/2005/08/addressing"
    const val NS_AEP = "http://www.aep-italia.it/vts/"
    const val NS_VTSR = "http://tempuri.org/"
    const val NS_VTSF = "http://schemas.datacontract.org/2004/07/VTS_Frontend"

    const val SOAP_INIT_SESSION = "http://tempuri.org/IServiceVtsFrontend/vts_InitSession"
    const val SOAP_CLOSE_SESSION = "http://tempuri.org/IServiceVtsFrontend/vts_CloseSession"
    const val SOAP_REQUEST_FUNCTION = "http://tempuri.org/IServiceVtsFrontend/vts_RequestFunction"

    val MIGRATION_ERRORS: Map<String, String> = mapOf(
        "AepMig_MaxDailyMigKo" to "Maximum daily migrations reached. Try again tomorrow.",
        "AepMig_MaxMonthlyMigKo" to "Maximum monthly migrations reached. Try again next month.",
        "AepMig_MaxMigKo" to "Maximum number of migrations reached.",
        "AepMig_NoMigAvailable" to "No migrations available.",
        "AepMig_MigAlreadyInProgress" to "A migration is already in progress.",
        "AepMig_GenericError" to "Generic migration error.",
        "AepMig_DeviceNotFound" to "Device not found.",
        "AepMig_InvalidCarrier" to "Invalid carrier code."
    )
}
