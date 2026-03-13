package it.atm.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE accountId = :accountId")
    fun observeByAccount(accountId: String): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE accountId = :accountId")
    suspend fun getByAccount(accountId: String): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subscriptions: List<SubscriptionEntity>)

    @Query("DELETE FROM subscriptions WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String)

    @Query("UPDATE subscriptions SET cachedDataOutBin = :dataOutBin WHERE accountId = :accountId AND vtokenUid = :vtokenUid")
    suspend fun updateDataOutBin(accountId: String, vtokenUid: String, dataOutBin: String)
}
