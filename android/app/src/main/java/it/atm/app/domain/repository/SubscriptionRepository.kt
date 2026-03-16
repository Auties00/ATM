package it.atm.app.domain.repository

import it.atm.app.data.local.db.SubscriptionEntity

import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    suspend fun transferSubscriptions(token: String, deviceUid: String): Result<List<SubscriptionEntity>>
    suspend fun syncSubscriptions(token: String, deviceUid: String): Result<List<SubscriptionEntity>>
    fun getCachedSubscriptions(): Flow<List<SubscriptionEntity>>
}
