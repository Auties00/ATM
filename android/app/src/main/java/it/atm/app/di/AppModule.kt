package it.atm.app.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.atm.app.BuildConfig
import it.atm.app.auth.AuthConstants
import it.atm.app.auth.AuthRepositoryImpl
import it.atm.app.auth.TokenAuthenticator
import it.atm.app.data.local.TokenDataStore
import it.atm.app.data.repository.SubscriptionRepositoryImpl
import it.atm.app.data.repository.TicketRepositoryImpl
import it.atm.app.domain.repository.AuthRepository
import it.atm.app.domain.repository.SubscriptionRepository
import it.atm.app.domain.repository.TicketRepository
import okhttp3.OkHttpClient
import it.atm.app.util.AppLogger
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {
    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindSubscriptionRepository(impl: SubscriptionRepositoryImpl): SubscriptionRepository

    @Binds @Singleton
    abstract fun bindTicketRepository(impl: TicketRepositoryImpl): TicketRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideOkHttpClient(
        tokenDataStore: TokenDataStore,
        authRepository: dagger.Lazy<AuthRepository>
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .authenticator(TokenAuthenticator(tokenDataStore) { authRepository.get() })
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                okhttp3.logging.HttpLoggingInterceptor { message ->
                    AppLogger.d("HTTP", message)
                }.apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
                }
            )
        }
        return builder.build()
    }
}
