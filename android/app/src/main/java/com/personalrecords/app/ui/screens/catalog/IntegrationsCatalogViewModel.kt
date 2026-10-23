package com.personalrecords.app.ui.screens.catalog

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.datastore.AppPreferences
import com.personalrecords.app.core.integrations.IntegrationCategory
import com.personalrecords.app.core.integrations.IntegrationPlugin
import com.personalrecords.app.core.integrations.PluginRegistry
import com.personalrecords.app.core.network.model.SyncResultDto
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
    val syncingPluginId: String? = null,
    val toastMessage: String? = null
)

class IntegrationsCatalogViewModel(
    private val repository: PersonalRecordsRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow(IntegrationCategory.ALL)
    private val _syncingPluginId = MutableStateFlow<String?>(null)
    private val _toastMessage = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(IntegrationsCatalogUiState())
    val uiState: StateFlow<IntegrationsCatalogUiState> = _uiState.asStateFlow()

    init {
        val basePrefsFlow = combine(
            preferences.enabledIntegrationPlugins,
            preferences.serverBaseUrl
        ) { enabledIds, serverUrl ->
            enabledIds to serverUrl
        }

        viewModelScope.launch {
            combine(
                basePrefsFlow,
                _searchQuery,
                _selectedCategory,
                _syncingPluginId,
                _toastMessage
            ) { (enabledIds, serverUrl), query, category, syncingId, toastMsg ->
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
                }

                IntegrationsCatalogUiState(
                    plugins = filtered,
                    enabledPluginIds = enabledIds,
                    searchQuery = query,
                    selectedCategory = category,
                    serverBaseUrl = serverUrl,
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
        viewModelScope.launch {
            preferences.toggleIntegrationPlugin(pluginId, isEnabled)
            _toastMessage.value = if (isEnabled) {
                "Eklenti etkinleştirildi"
            } else {
                "Eklenti devre dışı bırakıldı"
            }
        }
    }

    fun syncPlugin(plugin: IntegrationPlugin) {
        val serviceKey = plugin.syncServiceKey ?: return
        viewModelScope.launch {
            _syncingPluginId.value = plugin.id
            try {
                val result: Resource<SyncResultDto> = when (serviceKey) {
                    "fatsecret" -> repository.syncFatSecret(30)
                    "goodreads" -> repository.syncGoodreads()
                    "letterboxd" -> repository.syncLetterboxd()
                    "lastfm" -> {
                        when (val lfm = repository.syncLastFm()) {
                            is Resource.Success -> {
                                Resource.Success(
                                    SyncResultDto(
                                        success = true,
                                        message = lfm.data.message ?: "${plugin.name} eşitlendi"
                                    )
                                )
                            }
                            is Resource.Error -> {
                                Resource.Error(lfm.message ?: "Last.fm eşitlenemedi")
                            }
                            else -> Resource.Error("Last.fm eşitlenemedi")
                        }
                    }
                    "listenbrainz" -> {
                        when (val lb = repository.syncListenBrainz()) {
                            is Resource.Success -> {
                                val count = lb.data.inserted + lb.data.updated
                                val defaultMsg = if (count > 0) "ListenBrainz eşitlendi ($count yeni/güncellenen)" else "ListenBrainz dinlemeleri güncel"
                                Resource.Success(
                                    SyncResultDto(
                                        success = true,
                                        message = lb.data.message.ifBlank { defaultMsg }
                                    )
                                )
                            }
                            is Resource.Error -> {
                                Resource.Error(lb.message ?: "ListenBrainz eşitlenemedi")
                            }
                            else -> Resource.Error("ListenBrainz eşitlenemedi")
                        }
                    }
                    "statsfm" -> {
                        when (val sfm = repository.syncStatsFm()) {
                            is Resource.Success -> {
                                val count = sfm.data.inserted + sfm.data.updated
                                val defaultMsg = if (count > 0) "Stats.fm eşitlendi ($count yeni/güncellenen)" else "Stats.fm dinlemeleri güncel"
                                Resource.Success(
                                    SyncResultDto(
                                        success = true,
                                        message = sfm.data.message.ifBlank { defaultMsg }
                                    )
                                )
                            }
                            is Resource.Error -> {
                                Resource.Error(sfm.message ?: "Stats.fm eşitlenemedi")
                            }
                            else -> Resource.Error("Stats.fm eşitlenemedi")
                        }
                    }
                    else -> Resource.Error("Bilinmeyen servis: $serviceKey")
                }

                when (result) {
                    is Resource.Success -> {
                        val msg = result.data.message.takeIf { it.isNotBlank() } ?: "${plugin.name} eşitlendi"
                        _toastMessage.value = msg
                    }
                    is Resource.Error -> {
                        _toastMessage.value = "Hata: ${result.message}"
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _toastMessage.value = "Eşitleme hatası: ${e.message}"
            } finally {
                _syncingPluginId.value = null
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
