package com.personalrecords.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.personalrecords.app.core.database.PersonalRecordsDatabase
import com.personalrecords.app.core.datastore.AppPreferences
import com.personalrecords.app.core.theme.AppNightMode
import com.personalrecords.app.core.network.NetworkClient
import com.personalrecords.app.data.repository.PersonalRecordsRepository

class PersonalRecordsApp : Application(), ImageLoaderFactory {
    lateinit var preferences: AppPreferences
        private set

    lateinit var database: PersonalRecordsDatabase
        private set

    lateinit var repository: PersonalRecordsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = AppPreferences(this)
        // Sync the in-app theme to the system once (e.g. after an update), so launch screens match.
        AppNightMode.apply(this, preferences.readStartupPreferences().themeMode)
        database = PersonalRecordsDatabase.getInstance(this)
        val apiService = NetworkClient.createApiService(preferences)
        repository = PersonalRecordsRepository(apiService, database)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB
                    .build()
            }
            .build()
    }

    companion object {
        lateinit var instance: PersonalRecordsApp
            private set
    }
}
