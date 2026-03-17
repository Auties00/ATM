package it.atm.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class, SubscriptionEntity::class, TicketEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AtmDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun ticketDao(): TicketDao
}
