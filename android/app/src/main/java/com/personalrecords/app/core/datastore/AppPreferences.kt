package com.personalrecords.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context) {
    companion object {
        val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
        val CALORIE_TARGET = intPreferencesKey("calorie_target")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val ENABLED_INTEGRATION_PLUGINS = stringSetPreferencesKey("enabled_integration_plugins")
        val HIDDEN_STATS_CARDS = stringSetPreferencesKey("hidden_stats_cards")
        val DEFAULT_ENABLED_PLUGINS = setOf("fatsecret", "goodreads", "letterboxd", "lastfm")
        const val DEFAULT_BASE_URL = ""
    }

    val hiddenStatsCards: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[HIDDEN_STATS_CARDS] ?: emptySet()
    }

    suspend fun toggleStatCardVisibility(cardId: String, visible: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[HIDDEN_STATS_CARDS] ?: emptySet()
            val updated = if (visible) current - cardId else current + cardId
            prefs[HIDDEN_STATS_CARDS] = updated
        }
    }

    suspend fun hideStatCard(cardId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[HIDDEN_STATS_CARDS] ?: emptySet()
            prefs[HIDDEN_STATS_CARDS] = current + cardId
        }
    }

    suspend fun setStatsCardsHidden(cardIds: Collection<String>, hidden: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[HIDDEN_STATS_CARDS] ?: emptySet()
            val updated = if (hidden) current + cardIds else current - cardIds.toSet()
            prefs[HIDDEN_STATS_CARDS] = updated
        }
    }

    suspend fun resetHiddenStatsCards(cardIds: Collection<String>) {
        context.dataStore.edit { prefs ->
            val current = prefs[HIDDEN_STATS_CARDS] ?: emptySet()
            prefs[HIDDEN_STATS_CARDS] = current - cardIds.toSet()
        }
    }

    val enabledIntegrationPlugins: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[ENABLED_INTEGRATION_PLUGINS] ?: DEFAULT_ENABLED_PLUGINS
    }

    suspend fun toggleIntegrationPlugin(pluginId: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[ENABLED_INTEGRATION_PLUGINS] ?: DEFAULT_ENABLED_PLUGINS
            val updated = if (enabled) current + pluginId else current - pluginId
            prefs[ENABLED_INTEGRATION_PLUGINS] = updated
        }
    }

    val appLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[APP_LANGUAGE] ?: "system"
    }

    val serverBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_BASE_URL] ?: DEFAULT_BASE_URL
    }

    val calorieTarget: Flow<Int?> = context.dataStore.data.map { prefs ->
        prefs[CALORIE_TARGET]
    }

    suspend fun setAppLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[APP_LANGUAGE] = language
        }
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

    fun getStatsCardsOrder(screenKey: String, defaultOrder: List<String>): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[stringPreferencesKey("stats_order_$screenKey")]
            if (raw.isNullOrBlank()) {
                defaultOrder
            } else {
                val savedList = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                savedList.filter { it in defaultOrder } + defaultOrder.filter { it !in savedList }
            }
        }

    suspend fun setStatsCardsOrder(screenKey: String, order: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[stringPreferencesKey("stats_order_$screenKey")] = order.joinToString(",")
        }
    }

    suspend fun resetStatsCardsOrder(screenKey: String) {
        context.dataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey("stats_order_$screenKey"))
        }
    }
}
