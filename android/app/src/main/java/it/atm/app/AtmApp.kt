package it.atm.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import it.atm.app.auth.AuthRepository
import it.atm.app.auth.TokenAuthenticator
import it.atm.app.data.local.SubscriptionDataStore
import it.atm.app.data.local.TokenDataStore
import it.atm.app.data.remote.rest.AtmRestClient
import it.atm.app.data.remote.vts.VtsSoapClient
import it.atm.app.data.repository.SubscriptionRepository
import it.atm.app.service.AccountManager
import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AtmApp : Application(), ImageLoaderFactory {

    val accountManager: AccountManager by lazy { AccountManager(this) }
    val tokenDataStore: TokenDataStore by lazy { TokenDataStore(accountManager) }
    val subscriptionDataStore: SubscriptionDataStore by lazy { SubscriptionDataStore(accountManager) }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            context = this,
            tokenDataStore = tokenDataStore,
            accountManager = accountManager
        )
    }

    val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .authenticator(TokenAuthenticator(tokenDataStore) { authRepository })
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                okhttp3.logging.HttpLoggingInterceptor { message ->
                    Log.d("ATM_HTTP", message)
                }.apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
                }
            )
        }
        builder.build()
    }

    val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepository(
            restClient = AtmRestClient(httpClient),
            vtsSoapClient = VtsSoapClient(httpClient),
            subscriptionDataStore = subscriptionDataStore
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                httpClient.newBuilder()
                    .addInterceptor { chain ->
                        val token = runBlocking { tokenDataStore.getAccessToken() }
                        val builder = chain.request().newBuilder()
                        if (token != null) {
                            builder.addHeader("Authorization", "Bearer $token")
                            builder.addHeader("client", "${it.atm.app.auth.AuthConstants.CLIENT_ID};${it.atm.app.auth.AuthConstants.APP_VERSION}")
                            builder.addHeader("Cache-Control", "no-cache, no-store")
                        }
                        chain.proceed(builder.build())
                    }
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        runBlocking { accountManager.load() }
    }
}
