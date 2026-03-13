package it.atm.app.domain.repository

import it.atm.app.data.local.db.SubscriptionEntity
import it.atm.app.util.AppResult
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    suspend fun transferSubscriptions(token: String, deviceUid: String): AppResult<List<SubscriptionEntity>>
    suspend fun syncSubscriptions(token: String, deviceUid: String): AppResult<List<SubscriptionEntity>>
    fun getCachedSubscriptions(): Flow<List<SubscriptionEntity>>
}
