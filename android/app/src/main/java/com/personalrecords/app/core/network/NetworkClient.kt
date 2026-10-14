package com.personalrecords.app.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.personalrecords.app.BuildConfig
import com.personalrecords.app.core.datastore.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DynamicHostInterceptor(
    private val preferences: AppPreferences,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : Interceptor {
    private val cachedBaseUrl = AtomicReference<String>("")

    init {
        coroutineScope.launch {
            preferences.serverBaseUrl.collect { url ->
                cachedBaseUrl.set(url)
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val currentBaseUrl = cachedBaseUrl.get().ifBlank {
            runBlocking { preferences.serverBaseUrl.first() }.also { cachedBaseUrl.set(it) }
        }
        val newHttpUrl = currentBaseUrl.toHttpUrlOrNull()
        if (newHttpUrl != null) {
            val newRequestUrl = request.url.newBuilder()
                .scheme(newHttpUrl.scheme)
                .host(newHttpUrl.host)
                .port(newHttpUrl.port)
                .build()
            request = request.newBuilder().url(newRequestUrl).build()
        }
        return chain.proceed(request)
    }
}

object NetworkClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun createApiService(preferences: AppPreferences): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(DynamicHostInterceptor(preferences))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8787/") // Dummy base, overridden by DynamicHostInterceptor
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)
    }
}
