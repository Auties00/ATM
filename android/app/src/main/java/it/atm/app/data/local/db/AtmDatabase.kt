package it.atm.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class, SubscriptionEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AtmDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun subscriptionDao(): SubscriptionDao
}
