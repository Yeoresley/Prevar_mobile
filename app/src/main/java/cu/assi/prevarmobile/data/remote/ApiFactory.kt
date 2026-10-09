package cu.assi.prevarmobile.data.remote

import cu.assi.prevarmobile.security.SecureStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiFactory(private val store: SecureStore) {
    fun create(baseUrl: String, authenticated: Boolean): PrevarApi {
        val url = baseUrl.trimEnd('/') + "/"
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder().header("Accept", "application/json")
                if (authenticated) store.token?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PrevarApi::class.java)
    }
}
