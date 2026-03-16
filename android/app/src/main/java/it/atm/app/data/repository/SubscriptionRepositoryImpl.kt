package it.atm.app.data.repository

import it.atm.app.data.local.SubscriptionDataStore
import it.atm.app.data.local.db.SubscriptionEntity
import it.atm.app.data.mapper.SubscriptionMapper
import it.atm.app.data.remote.rest.AtmRestClient
import it.atm.app.data.remote.vts.VtsSoapClient
import it.atm.app.domain.model.QrConfig
import it.atm.app.domain.model.VToken
import it.atm.app.domain.repository.SubscriptionRepository
import it.atm.app.auth.AccountManager
import it.atm.app.util.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import it.atm.app.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val restClient: AtmRestClient,
    private val vtsSoapClient: VtsSoapClient,
    private val subscriptionDataStore: SubscriptionDataStore,
    private val accountManager: AccountManager
) : SubscriptionRepository {

    companion object {
        private const val CARRIER_CODE = "0723"
    }

    override suspend fun transferSubscriptions(token: String, deviceUid: String): AppResult<List<SubscriptionEntity>> {
        AppLogger.d("SYNC","Starting transfer flow")
        return try {
            vtsSetupSession(deviceUid)

            restClient.initiateMigration(token, deviceUid)

            val checksResult = restClient.fetchChecks(token, deviceUid)
            val checks = when (checksResult) {
                is AppResult.Success -> checksResult.data
                is AppResult.Error -> throw checksResult.exception
            }
            val carriers = checks.aepTicketsMigrationsCarriers.orEmpty()

            for (carrier in carriers) {
                restClient.executeAepMigration(token, deviceUid, carrier)
            }

            if (carriers.isNotEmpty()) {
                restClient.fetchChecks(token, deviceUid)
            }

            syncSubscriptions(token, deviceUid)
        } catch (e: Exception) {
            AppLogger.e("SYNC","Transfer failed: %s", e.message)
            AppResult.Error(e)
        }
    }

    override suspend fun syncSubscriptions(token: String, deviceUid: String): AppResult<List<SubscriptionEntity>> =
        coroutineScope {
            AppLogger.d("SYNC","Syncing subscriptions")
            try {
                val accountId = accountManager.activeAccountId.value ?: throw RuntimeException("No active account")

                val restDeferred = async(Dispatchers.IO) { collectCards(token, deviceUid) }
                val vtsDeferred = async(Dispatchers.IO) { fetchVTokensAndConfig(deviceUid) }

                val cards = restDeferred.await()
                val subs = SubscriptionMapper.cardsToSubscriptions(cards, accountId).toMutableList()
                val (vtokens, qrConfig) = vtsDeferred.await()

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

                subscriptionDataStore.saveSubscriptions(accountId, subs)
                subscriptionDataStore.saveQrConfig(qrConfig)
                AppLogger.d("SYNC","Sync complete: %d subscriptions", subs.size)
                AppResult.Success(subs.toList())
            } catch (e: Exception) {
                AppLogger.e("SYNC","Sync failed: %s", e.message)
                AppResult.Error(e)
            }
        }

    override fun getCachedSubscriptions(): Flow<List<SubscriptionEntity>> {
        return subscriptionDataStore.getSubscriptions()
    }

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
        } catch (e: Exception) {
            AppLogger.w("SYNC","VTS setup failed (non-fatal): %s", e.message)
        }
    }

    private suspend fun collectCards(
        token: String,
        deviceUid: String
    ) = withContext(Dispatchers.IO) {
        when (val result = restClient.fetchUserCards(token, deviceUid, CARRIER_CODE)) {
            is AppResult.Success -> result.data
            is AppResult.Error -> {
                AppLogger.w("SYNC","Failed to fetch cards: %s", result.exception.message)
                emptyList()
            }
        }
    }

    private suspend fun fetchVTokensAndConfig(deviceUid: String): Pair<List<VToken>, QrConfig> {
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

                for (i in vtokens.indices) {
                    try {
                        val dataB64 = vtsSoapClient.getVToken(sessionId, vtokens[i].uid)
                        if (dataB64 != null) {
                            vtokens[i] = vtokens[i].copy(dataOutBin = dataB64)
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
