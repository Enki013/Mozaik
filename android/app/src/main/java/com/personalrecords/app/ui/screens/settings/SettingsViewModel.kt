package com.personalrecords.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.datastore.AppPreferences
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = "",
    val isTestingConnection: Boolean = false,
    val connectionSuccess: Boolean? = null,
    val isSyncing: String? = null,
    val syncStatusMessage: String? = null,
    // FatSecret BYOK
    val consumerKey: String = "",
    val consumerSecret: String = "",
    val hasKeys: Boolean = false,
    val isConnected: Boolean = false,
    val maskedKey: String = "",
    val isLoadingFsSettings: Boolean = false,
    val isSavingKeys: Boolean = false,
    val keySaveMessage: String? = null
)

class SettingsViewModel(
    private val repository: PersonalRecordsRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val url = preferences.serverBaseUrl.first()
            _uiState.value = _uiState.value.copy(serverUrl = url)
            loadFatSecretSettings()
        }
    }

    fun loadFatSecretSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFsSettings = true)
            val result = repository.getFatSecretSettings()
            when (result) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingFsSettings = false,
                        hasKeys = result.data.has_keys,
                        isConnected = result.data.is_connected,
                        maskedKey = result.data.consumer_key
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoadingFsSettings = false)
                }
                else -> _uiState.value = _uiState.value.copy(isLoadingFsSettings = false)
            }
        }
    }

    fun updateConsumerKey(key: String) {
        _uiState.value = _uiState.value.copy(consumerKey = key, keySaveMessage = null)
    }

    fun updateConsumerSecret(secret: String) {
        _uiState.value = _uiState.value.copy(consumerSecret = secret, keySaveMessage = null)
    }

    fun saveFatSecretKeys() {
        val key = _uiState.value.consumerKey.trim()
        val secret = _uiState.value.consumerSecret.trim()
        if (key.isBlank() || secret.isBlank()) {
            _uiState.value = _uiState.value.copy(keySaveMessage = "Consumer Key ve Secret boş olamaz.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingKeys = true, keySaveMessage = null)
            val result = repository.saveFatSecretKeys(key, secret)
            when (result) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSavingKeys = false,
                        keySaveMessage = result.data.message.ifBlank { "Anahtarlar kaydedildi!" },
                        consumerKey = "",
                        consumerSecret = ""
                    )
                    loadFatSecretSettings()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSavingKeys = false,
                        keySaveMessage = "Hata: ${result.message}"
                    )
                }
                else -> _uiState.value = _uiState.value.copy(isSavingKeys = false)
            }
        }
    }

    fun updateServerUrlInput(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, connectionSuccess = null)
    }

    fun saveServerUrl() {
        viewModelScope.launch {
            preferences.setServerBaseUrl(_uiState.value.serverUrl)
            testConnection()
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingConnection = true, connectionSuccess = null)
            val ok = repository.checkHealth()
            _uiState.value = _uiState.value.copy(
                isTestingConnection = false,
                connectionSuccess = ok
            )
        }
    }

    fun syncFatSecret(days: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = "fatsecret_$days", syncStatusMessage = null)
            val result = repository.syncFatSecret(days)
            when (result) {
                is Resource.Success -> {
                    val serverMsg = result.data.message.takeIf { it.isNotBlank() }
                    _uiState.value = _uiState.value.copy(
                        isSyncing = null,
                        syncStatusMessage = serverMsg ?: "FatSecret başarıyla eşitlendi ($days gün)!"
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = null,
                        syncStatusMessage = "Hata: ${result.message}"
                    )
                }
                else -> _uiState.value = _uiState.value.copy(isSyncing = null)
            }
        }
    }

    fun syncGoodreads() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = "goodreads", syncStatusMessage = null)
            val result = repository.syncGoodreads()
            when (result) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = null,
                        syncStatusMessage = "Goodreads RSS eşitlendi!"
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = null,
                        syncStatusMessage = "Hata: ${result.message}"
                    )
                }
                else -> _uiState.value = _uiState.value.copy(isSyncing = null)
            }
        }
    }

    fun syncLetterboxd() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = "letterboxd", syncStatusMessage = null)
            val result = repository.syncLetterboxd()
            when (result) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = null,
                        syncStatusMessage = "Letterboxd RSS eşitlendi!"
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = null,
                        syncStatusMessage = "Hata: ${result.message}"
                    )
                }
                else -> _uiState.value = _uiState.value.copy(isSyncing = null)
            }
        }
    }
}
