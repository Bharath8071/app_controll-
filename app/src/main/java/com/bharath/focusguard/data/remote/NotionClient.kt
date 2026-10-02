package com.bharath.focusguard.data.remote

import com.bharath.focusguard.BuildConfig
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NotionClient {
    const val BASE_URL = "https://api.notion.com/"
    val gson: Gson = Gson()

    fun create(token: String): NotionApiService {
        // Auth interceptor: only sets Authorization header.
        // BUG-020 fix: Notion-Version is declared in @Headers on each endpoint; don't duplicate it here.
        val auth = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(request)
        }
        val clientBuilder = OkHttpClient.Builder().addInterceptor(auth)
        // BUG-008 fix: Only log in debug builds. Bearer token visible in BASIC logs — never log in release.
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            clientBuilder.addInterceptor(logging)
        }
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(NotionApiService::class.java)
    }
}
