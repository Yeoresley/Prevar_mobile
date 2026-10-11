package cu.assi.prevarmobile.data.remote

import cu.assi.prevarmobile.BuildConfig
import cu.assi.prevarmobile.security.PinnedCaTrust
import cu.assi.prevarmobile.security.SecureStore
import java.util.Base64
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiFactory(private val store: SecureStore) {
    fun create(baseUrl: String, authenticated: Boolean): PrevarApi {
        val url = baseUrl.trimEnd('/') + "/"
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionSpecs(
                if (url.startsWith("https://")) listOf(ConnectionSpec.MODERN_TLS)
                else listOf(ConnectionSpec.CLEARTEXT)
            )
            .apply {
                if (url.startsWith("https://")) {
                    store.serverCaDerBase64?.takeIf { it.isNotBlank() }?.let { encoded ->
                        val der = Base64.getDecoder().decode(encoded)
                        val tls = PinnedCaTrust.materialFromDer(der)
                        sslSocketFactory(tls.socketFactory, tls.trustManager)
                    }
                }
            }
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-store")
                if (authenticated) store.token?.let { request.header("Authorization", "Bearer $it") }
                chain.proceed(request.build())
            }

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        val client = builder.build()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PrevarApi::class.java)
    }
}
