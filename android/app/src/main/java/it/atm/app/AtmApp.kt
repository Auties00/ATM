package it.atm.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import it.atm.app.auth.AuthConstants
import it.atm.app.data.local.TokenDataStore
import it.atm.app.service.AccountManager
import it.atm.app.util.AppLogger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class AtmApp : Application(), ImageLoaderFactory {

    @Inject lateinit var accountManager: AccountManager
    @Inject lateinit var tokenDataStore: TokenDataStore
    @Inject lateinit var httpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(BuildConfig.DEBUG)
        Timber.tag("APP").d("AtmApp onCreate")
        runBlocking { accountManager.load() }
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
                            builder.addHeader("client", "${AuthConstants.CLIENT_ID};${AuthConstants.APP_VERSION}")
                            builder.addHeader("Cache-Control", "no-cache, no-store")
                        }
                        chain.proceed(builder.build())
                    }
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
