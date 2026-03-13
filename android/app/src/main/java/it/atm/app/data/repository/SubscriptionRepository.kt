package it.atm.app.data.repository

import it.atm.app.data.local.SubscriptionDataStore
import it.atm.app.data.remote.rest.AtmRestClient
import it.atm.app.data.remote.rest.CardItem
import it.atm.app.data.remote.rest.Subscription
import it.atm.app.data.remote.vts.QrConfig
import it.atm.app.data.remote.vts.VToken
import it.atm.app.data.remote.vts.VtsSoapClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SubscriptionRepository(
    private val restClient: AtmRestClient,
    private val vtsSoapClient: VtsSoapClient,
    private val subscriptionDataStore: SubscriptionDataStore
) {
    companion object {
        // 0723 is ATM Milano's transit operator identifier in the MyCicero/AEP ticketing system.
        // It's a static app configuration constant, not fetched from any API.
        private const val CARRIER_CODE = "0723"
    }

    /**
     * Transfers subscriptions to the current device, following the real app flow:
     *
     * 1. VTS setup session (SetClientInfo, GetServerInfo, GetClientInfo)
     * 2. POST Tickets/Migration (initiate general migration)
     * 3. GET Checks (discover carriers needing AEP migration)
     * 4. For each carrier: GET Migration/ExecuteAepMigrations/{carrier}
     * 5. GET Checks (verify migration completed)
     * 6. Sync subscriptions (REST cards + VTS vtokens)
     */
    suspend fun transferSubscriptions(token: String, deviceUid: String): List<Subscription> {
        // Step 1: VTS setup session
        vtsSetupSession(deviceUid)

        // Step 2: Initiate migration
        restClient.initiateMigration(token, deviceUid)

        // Step 3: Check which carriers need AEP migration
        val checks = restClient.fetchChecks(token, deviceUid)
        val carriers = checks.aepTicketsMigrationsCarriers.orEmpty()

        // Step 4: Execute AEP migration for each carrier
        for (carrier in carriers) {
            restClient.executeAepMigration(token, deviceUid, carrier)
        }

        // Step 5: Verify migration completed
        if (carriers.isNotEmpty()) {
            restClient.fetchChecks(token, deviceUid)
        }

        // Step 6: Sync subscriptions (fetch cards + vtokens)
        return syncSubscriptions(token, deviceUid)
    }

    suspend fun syncSubscriptions(token: String, deviceUid: String): List<Subscription> =
        coroutineScope {
            val restDeferred = async(Dispatchers.IO) {
                collectCards(token, deviceUid)
            }
            val vtsDeferred = async(Dispatchers.IO) {
                fetchVTokensAndConfig(deviceUid)
            }

            val cards = restDeferred.await()
            val subs = cardsToSubscriptions(cards).toMutableList()
            val (vtokens, qrConfig) = vtsDeferred.await()

            // Merge VToken data into subscriptions (1:1 by position)
            val vtokensWithData = vtokens.filter { it.dataOutBin != null }
            for (i in subs.indices) {
                if (i >= vtokensWithData.size) break
                val vt = vtokensWithData[i]
                subs[i] = subs[i].copy(
                    vtokenUid = vt.uid,
                    signatureCount = vt.signatureCount,
                    cachedDataOutBin = vt.dataOutBin,
                    title = vt.contractDescription ?: subs[i].title,
                    startValidity = vt.contractStartValidity?.take(10) ?: subs[i].startValidity,
                    endValidity = vt.contractEndValidity?.take(10) ?: subs[i].endValidity
                )
            }

            subscriptionDataStore.saveSubscriptions(subs)
            subscriptionDataStore.saveQrConfig(qrConfig)
            subs
        }

    fun getCachedSubscriptions(): Flow<List<Subscription>> {
        return subscriptionDataStore.getSubscriptions()
    }

    /**
     * VTS setup session matching the HAR flow:
     * InitSession -> SetClientInfo -> GetServerInfo -> GetClientInfo -> CloseSession
     */
    private suspend fun vtsSetupSession(deviceUid: String) {
        try {
            val sessionId = vtsSoapClient.initSession(deviceUid)
            try {
                vtsSoapClient.setClientInfo(sessionId)
                vtsSoapClient.getServerInfo(sessionId)
                vtsSoapClient.getClientInfo(sessionId)
            } finally {
                vtsSoapClient.closeSession(sessionId)
            }
        } catch (_: Exception) {
            // VTS setup failure is non-fatal for migration
        }
    }

    private suspend fun collectCards(
        token: String,
        deviceUid: String
    ): List<CardItem> = withContext(Dispatchers.IO) {
        try {
            restClient.fetchUserCards(token, deviceUid, CARRIER_CODE)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun cardsToSubscriptions(cards: List<CardItem>): List<Subscription> {
        return cards.filter { it.valid }.map { card ->
            val name = listOf(card.name, card.surname)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            val title = card.lastRenewalObj?.serviceTypeDescription
                ?: card.profileType.ifBlank { "Subscription" }
            val startValidity = (card.startValidityDate ?: "").take(10)
            val endValidity = (card.expiredDate ?: "").take(10)

            Subscription(
                cardCode = card.cardCode,
                cardNumber = card.cardNumber,
                serialNumber = card.serialNumber,
                holderId = card.holderId,
                title = title,
                subtitle = card.cardNumber,
                profile = card.profileType,
                name = name,
                startValidity = startValidity,
                endValidity = endValidity,
                carrierCode = card.carrierCode,
                status = if (card.valid) 0 else 1,
                cachedDataOutBin = null,
                vtokenUid = "",
                signatureCount = 1
            )
        }
    }

    private suspend fun fetchVTokensAndConfig(deviceUid: String): Pair<List<VToken>, QrConfig> {
        // First session: setup + get server info + list vtokens
        return try {
            val sessionId = vtsSoapClient.initSession(deviceUid)
            try {
                vtsSoapClient.setClientInfo(sessionId)

                val qrConfig = try {
                    vtsSoapClient.getServerInfo(sessionId)
                } catch (_: Exception) {
                    QrConfig()
                }

                vtsSoapClient.getClientInfo(sessionId)

                val vtokens = vtsSoapClient.getVTokenList(sessionId, deviceUid).toMutableList()

                // Fetch details for each vtoken and contract info
                for (i in vtokens.indices) {
                    try {
                        val dataB64 = vtsSoapClient.getVToken(sessionId, vtokens[i].uid)
                        if (dataB64 != null) {
                            vtokens[i] = vtokens[i].copy(dataOutBin = dataB64)
                            // Get contract validity via getInfoCard
                            try {
                                val contractInfo = vtsSoapClient.getInfoCard(sessionId, dataB64)
                                if (contractInfo != null) {
                                    vtokens[i] = vtokens[i].copy(
                                        contractStartValidity = contractInfo.contractStartValidity,
                                        contractEndValidity = contractInfo.contractEndValidity,
                                        contractDescription = contractInfo.contractDescription
                                    )
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {
                        continue
                    }
                }
                Pair(vtokens, qrConfig)
            } finally {
                vtsSoapClient.closeSession(sessionId)
            }
        } catch (_: Exception) {
            Pair(emptyList(), QrConfig())
        }
    }
}
