package it.atm.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao {
    @Query("SELECT * FROM tickets WHERE accountId = :accountId")
    fun observeByAccount(accountId: String): Flow<List<TicketEntity>>

    @Query("SELECT * FROM tickets WHERE accountId = :accountId")
    suspend fun getByAccount(accountId: String): List<TicketEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tickets: List<TicketEntity>)

    @Query("DELETE FROM tickets WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String)
}
