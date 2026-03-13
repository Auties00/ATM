package it.atm.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.atm.app.data.local.db.AccountDao
import it.atm.app.data.local.db.AtmDatabase
import it.atm.app.data.local.db.SubscriptionDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AtmDatabase {
        return Room.databaseBuilder(context, AtmDatabase::class.java, "atm.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideAccountDao(database: AtmDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideSubscriptionDao(database: AtmDatabase): SubscriptionDao = database.subscriptionDao()
}
