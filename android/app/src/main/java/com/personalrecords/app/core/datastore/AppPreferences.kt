package com.personalrecords.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context) {
    companion object {
        val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
        val CALORIE_TARGET = intPreferencesKey("calorie_target")
        const val DEFAULT_BASE_URL = "https://personal-records-api.enessalman1300.workers.dev"

    }

    val serverBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_BASE_URL] ?: DEFAULT_BASE_URL
    }

    val calorieTarget: Flow<Int?> = context.dataStore.data.map { prefs ->
        prefs[CALORIE_TARGET]
    }

    suspend fun setServerBaseUrl(url: String) {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "http://$cleanUrl"
        }
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.dropLast(1)
        }
        context.dataStore.edit { prefs ->
            prefs[SERVER_BASE_URL] = cleanUrl
        }
    }

    suspend fun setCalorieTarget(target: Int) {
        context.dataStore.edit { prefs ->
            prefs[CALORIE_TARGET] = target
        }
    }
}
