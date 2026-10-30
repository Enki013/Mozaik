package com.personalrecords.app.ui.screens.catalog

import com.personalrecords.app.R
import com.personalrecords.app.ui.common.syncIntegration
import com.personalrecords.app.ui.common.UiMessage
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.datastore.AppPreferences
import com.personalrecords.app.core.integrations.IntegrationCategory
import com.personalrecords.app.core.integrations.IntegrationPlugin
import com.personalrecords.app.core.integrations.PluginRegistry
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Immutable
data class IntegrationsCatalogUiState(
    val plugins: List<IntegrationPlugin> = emptyList(),
    val enabledPluginIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val selectedCategory: IntegrationCategory = IntegrationCategory.ALL,
    val serverBaseUrl: String = "",
    val serverApiKey: String = "",
    val syncingPluginId: String? = null,
    val toastMessage: UiMessage? = null
)

class IntegrationsCatalogViewModel(
    private val repository: PersonalRecordsRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow(IntegrationCategory.ALL)
    private val _syncingPluginId = MutableStateFlow<String?>(null)
    private val _toastMessage = MutableStateFlow<UiMessage?>(null)

    private val _uiState = MutableStateFlow(IntegrationsCatalogUiState())
    val uiState: StateFlow<IntegrationsCatalogUiState> = _uiState.asStateFlow()

    init {
        val basePrefsFlow = combine(
            preferences.enabledIntegrationPlugins,
            preferences.serverBaseUrl,
            preferences.serverApiKey
        ) { enabledIds, serverUrl, apiKey ->
            Triple(enabledIds, serverUrl, apiKey)
        }

        viewModelScope.launch {
            combine(
                basePrefsFlow,
                _searchQuery,
                _selectedCategory,
                _syncingPluginId,
                _toastMessage
            ) { (enabledIds, serverUrl, apiKey), query, category, syncingId, toastMsg ->
                val filtered = PluginRegistry.defaultPlugins.filter { plugin ->
                    val matchesCategory = category == IntegrationCategory.ALL || plugin.category == category
                    val matchesSearch = if (query.isBlank()) {
                        true
                    } else {
                        val q = query.trim().lowercase()
                        plugin.name.lowercase().contains(q) ||
                                plugin.shortDescription.lowercase().contains(q) ||
                                plugin.tags.any { it.contains(q) }
                    }
                    matchesCategory && matchesSearch
                }.map { plugin ->
                    plugin.copy(
                        isEnabled = enabledIds.contains(plugin.id),
                        isConfigured = enabledIds.contains(plugin.id)
                    )
                }.sortedWith(
                    compareByDescending<IntegrationPlugin> { it.isEnabled }
                        .thenBy { it.name.lowercase() }
                )

                IntegrationsCatalogUiState(
                    plugins = filtered,
                    enabledPluginIds = enabledIds,
                    searchQuery = query,
                    selectedCategory = category,
                    serverBaseUrl = serverUrl,
                    serverApiKey = apiKey,
                    syncingPluginId = syncingId,
                    toastMessage = toastMsg
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: IntegrationCategory) {
        _selectedCategory.value = category
    }

    fun togglePlugin(pluginId: String, isEnabled: Boolean) {
        val plugin = PluginRegistry.defaultPlugins.firstOrNull { it.id == pluginId } ?: return
        if (plugin.isInDevelopment && isEnabled) return

        viewModelScope.launch {
            preferences.toggleIntegrationPlugin(pluginId, isEnabled)
            _toastMessage.value = if (isEnabled) {
                UiMessage.of(R.string.plugin_msg_enabled)
            } else {
                UiMessage.of(R.string.plugin_msg_disabled)
            }
            // Immediately inform server so webhooks and sync pause/resume
            repository.togglePluginOnServer(pluginId, isEnabled)
            if (pluginId == "web_scrobbler") {
                repository.saveMusicSettings(webScrobblerEnabled = isEnabled)
            }
        }
    }

    fun syncPlugin(plugin: IntegrationPlugin) {
        if (plugin.isInDevelopment) return
        val serviceKey = plugin.syncServiceKey ?: return
        viewModelScope.launch {
            _syncingPluginId.value = plugin.id
            try {
                _toastMessage.value = repository.syncIntegration(serviceKey, plugin.name)
                    ?: UiMessage.error(R.string.sync_msg_failed, plugin.name)
            } catch (e: Exception) {
                _toastMessage.value = UiMessage.error(R.string.settings_msg_error, e.message ?: plugin.name)
            } finally {
                _syncingPluginId.value = null
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
