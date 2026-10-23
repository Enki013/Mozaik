package com.personalrecords.app.ui.screens.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.datastore.AppPreferences
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
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
    val keySaveMessage: String? = null,
    // Letterboxd
    val letterboxdUrl: String = "",
    val isLoadingLetterboxd: Boolean = false,
    val isSavingLetterboxd: Boolean = false,
    val letterboxdSaveMessage: String? = null,
    // Goodreads
    val goodreadsUrl: String = "",
    val isLoadingGoodreads: Boolean = false,
    val isSavingGoodreads: Boolean = false,
    val goodreadsSaveMessage: String? = null,
    // Music & Scrobbler
    val lastfmUsername: String = "",
    val lastfmApiKey: String = "",
    val lastfmApiKeySet: Boolean = false,
    val musicWebhookUrl: String = "",
    val webScrobblerEnabled: Boolean = false,
    val isLoadingMusicSettings: Boolean = false,
    val isSavingMusicSettings: Boolean = false,
    val musicSaveMessage: String? = null,
    // ListenBrainz
    val listenbrainzUsername: String = "",
    val listenbrainzToken: String = "",
    val listenbrainzTokenSet: Boolean = false,
    val isSavingListenBrainz: Boolean = false,
    val listenbrainzSaveMessage: String? = null,
    // Stats.fm
    val statsfmUsername: String = "",
    val isSavingStatsFm: Boolean = false,
    val statsfmSaveMessage: String? = null,
    // Language
    val appLanguage: String = "system",
    // Integration Plugins
    val enabledPluginIds: Set<String> = emptySet()
)

class SettingsViewModel(
    private val repository: PersonalRecordsRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var hasInitialized = false

    init {
        viewModelScope.launch {
            preferences.serverBaseUrl.collect { url ->
                _uiState.update { it.copy(serverUrl = url) }
            }
        }
        viewModelScope.launch {
            preferences.appLanguage.collect { lang ->
                _uiState.update { it.copy(appLanguage = lang) }
            }
        }
        viewModelScope.launch {
            preferences.enabledIntegrationPlugins.collect { enabledSet ->
                _uiState.update { it.copy(enabledPluginIds = enabledSet) }
            }
        }
    }

    fun ensureLoaded() {
        if (hasInitialized) return
        hasInitialized = true
        loadAllSettings()
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            preferences.setAppLanguage(language)
        }
    }

    fun loadAllSettings() {
        hasInitialized = true
        loadFatSecretSettings()
        loadLetterboxdSettings()
        loadGoodreadsSettings()
        loadMusicSettings()
    }

    fun loadFatSecretSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFsSettings = true) }
            val result = repository.getFatSecretSettings()
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingFsSettings = false,
                            hasKeys = result.data.has_keys,
                            isConnected = result.data.is_connected,
                            maskedKey = result.data.consumer_key
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoadingFsSettings = false) }
                }
                else -> _uiState.update { it.copy(isLoadingFsSettings = false) }
            }
        }
    }

    fun updateConsumerKey(key: String) {
        _uiState.update { it.copy(consumerKey = key, keySaveMessage = null) }
    }

    fun updateConsumerSecret(secret: String) {
        _uiState.update { it.copy(consumerSecret = secret, keySaveMessage = null) }
    }

    fun saveFatSecretKeys() {
        val key = _uiState.value.consumerKey.trim()
        val secret = _uiState.value.consumerSecret.trim()
        if (key.isBlank() || secret.isBlank()) {
            _uiState.update { it.copy(keySaveMessage = "Consumer Key ve Secret boş olamaz.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingKeys = true, keySaveMessage = null) }
            val result = repository.saveFatSecretKeys(key, secret)
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSavingKeys = false,
                            keySaveMessage = result.data.message.ifBlank { "Anahtarlar kaydedildi!" },
                            consumerKey = "",
                            consumerSecret = ""
                        )
                    }
                    loadFatSecretSettings()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingKeys = false,
                            keySaveMessage = "Hata: ${result.message}"
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSavingKeys = false) }
            }
        }
    }

    fun updateServerUrlInput(url: String) {
        _uiState.update { it.copy(serverUrl = url, connectionSuccess = null) }
    }

    fun saveServerUrl() {
        viewModelScope.launch {
            preferences.setServerBaseUrl(_uiState.value.serverUrl)
            testConnection()
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, connectionSuccess = null) }
            val ok = repository.checkHealth()
            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    connectionSuccess = ok
                )
            }
        }
    }

    fun syncFatSecret(days: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = "fatsecret_$days", syncStatusMessage = null) }
            val result = repository.syncFatSecret(days)
            when (result) {
                is Resource.Success -> {
                    val serverMsg = result.data.message.takeIf { it.isNotBlank() }
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = serverMsg ?: "FatSecret başarıyla eşitlendi ($days gün)!"
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = "Hata: ${result.message}"
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSyncing = null) }
            }
        }
    }

    fun syncGoodreads() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = "goodreads", syncStatusMessage = null) }
            val result = repository.syncGoodreads()
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = "Goodreads RSS eşitlendi!"
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = "Hata: ${result.message}"
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSyncing = null) }
            }
        }
    }

    fun syncLetterboxd() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = "letterboxd", syncStatusMessage = null) }
            val result = repository.syncLetterboxd()
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = "Letterboxd RSS eşitlendi!"
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = "Hata: ${result.message}"
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSyncing = null) }
            }
        }
    }

    fun syncLastFm() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = "lastfm", syncStatusMessage = null) }
            val result = repository.syncLastFm(allPages = false)
            when (result) {
                is Resource.Success -> {
                    val count = result.data.inserted + result.data.updated
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = "Last.fm eşitlendi: $count parça güncellendi!"
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = "Hata: ${result.message}"
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSyncing = null) }
            }
        }
    }

    fun loadLetterboxdSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLetterboxd = true) }
            when (val res = repository.getLetterboxdSettings()) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingLetterboxd = false,
                            letterboxdUrl = res.data.feed_url
                        )
                    }
                }
                else -> _uiState.update { it.copy(isLoadingLetterboxd = false) }
            }
        }
    }

    fun updateLetterboxdUrl(url: String) {
        _uiState.update { it.copy(letterboxdUrl = url, letterboxdSaveMessage = null) }
    }

    fun saveLetterboxdSettings() {
        var input = _uiState.value.letterboxdUrl.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(letterboxdSaveMessage = "Letterboxd kullanıcı adı veya RSS linki girin.") }
            return
        }
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            val username = input.removePrefix("@").trim('/')
            input = "https://letterboxd.com/$username/rss/"
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingLetterboxd = true, letterboxdSaveMessage = null) }
            when (val res = repository.saveLetterboxdSettings(input)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSavingLetterboxd = false,
                            letterboxdUrl = res.data.feed_url.ifBlank { input },
                            letterboxdSaveMessage = res.data.message.ifBlank { "Letterboxd kaydedildi!" }
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingLetterboxd = false,
                            letterboxdSaveMessage = "Hata: ${res.message}"
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSavingLetterboxd = false) }
            }
        }
    }

    fun loadGoodreadsSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGoodreads = true) }
            when (val res = repository.getGoodreadsSettings()) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingGoodreads = false,
                            goodreadsUrl = res.data.feed_url
                        )
                    }
                }
                else -> _uiState.update { it.copy(isLoadingGoodreads = false) }
            }
        }
    }

    fun updateGoodreadsUrl(url: String) {
        _uiState.update { it.copy(goodreadsUrl = url, goodreadsSaveMessage = null) }
    }

    fun saveGoodreadsSettings() {
        var input = _uiState.value.goodreadsUrl.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(goodreadsSaveMessage = "Goodreads RSS linki girin.") }
            return
        }
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            val id = input.removePrefix("@").trim('/')
            input = "https://www.goodreads.com/review/list_rss/$id"
        } else if (input.contains("goodreads.com/user/show/")) {
            val id = input.substringAfter("goodreads.com/user/show/").substringBefore("?").trim('/')
            input = "https://www.goodreads.com/review/list_rss/$id"
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingGoodreads = true, goodreadsSaveMessage = null) }
            when (val res = repository.saveGoodreadsSettings(input)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSavingGoodreads = false,
                            goodreadsUrl = res.data.feed_url.ifBlank { input },
                            goodreadsSaveMessage = res.data.message.ifBlank { "Goodreads kaydedildi!" }
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingGoodreads = false,
                            goodreadsSaveMessage = "Hata: ${res.message}"
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSavingGoodreads = false) }
            }
        }
    }

    fun loadMusicSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMusicSettings = true) }
            when (val result = repository.getMusicSettings()) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingMusicSettings = false,
                            lastfmUsername = result.data.lastfmUsername,
                            lastfmApiKeySet = result.data.lastfmApiKeySet,
                            listenbrainzUsername = result.data.listenbrainzUsername,
                            listenbrainzTokenSet = result.data.listenbrainzTokenSet,
                            statsfmUsername = result.data.statsfmUsername,
                            musicWebhookUrl = result.data.webhookUrl,
                            webScrobblerEnabled = result.data.webScrobblerEnabled
                        )
                    }
                }
                else -> _uiState.update { it.copy(isLoadingMusicSettings = false) }
            }
        }
    }

    fun toggleWebScrobbler(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(webScrobblerEnabled = enabled) }
            preferences.toggleIntegrationPlugin("web_scrobbler", enabled)
            repository.togglePluginOnServer("web_scrobbler", enabled)
            repository.saveMusicSettings(webScrobblerEnabled = enabled)
        }
    }

    fun updateLastfmUsername(username: String) {
        _uiState.update { it.copy(lastfmUsername = username, musicSaveMessage = null) }
    }

    fun updateLastfmApiKey(apiKey: String) {
        _uiState.update { it.copy(lastfmApiKey = apiKey, musicSaveMessage = null) }
    }

    fun saveMusicSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingMusicSettings = true, musicSaveMessage = null) }
            val username = _uiState.value.lastfmUsername.trim()
            val apiKey = _uiState.value.lastfmApiKey.trim().takeIf { it.isNotBlank() }
            val result = repository.saveMusicSettings(username = username, apiKey = apiKey)
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSavingMusicSettings = false,
                            musicSaveMessage = "Müzik ayarları kaydedildi",
                            lastfmApiKey = ""
                        )
                    }
                    loadMusicSettings()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingMusicSettings = false,
                            musicSaveMessage = result.message
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSavingMusicSettings = false) }
            }
        }
    }

    fun updateListenBrainzUsername(username: String) {
        _uiState.update { it.copy(listenbrainzUsername = username, listenbrainzSaveMessage = null) }
    }

    fun updateListenBrainzToken(token: String) {
        _uiState.update { it.copy(listenbrainzToken = token, listenbrainzSaveMessage = null) }
    }

    fun saveListenBrainzSettings(clearToken: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingListenBrainz = true, listenbrainzSaveMessage = null) }
            val username = _uiState.value.listenbrainzUsername.trim()
            val token = _uiState.value.listenbrainzToken.trim().takeIf { it.isNotBlank() }
            val result = repository.saveMusicSettings(
                listenbrainzUsername = username,
                listenbrainzToken = if (clearToken) null else token,
                clearListenbrainzToken = if (clearToken) true else null
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSavingListenBrainz = false,
                            listenbrainzSaveMessage = "ListenBrainz ayarları kaydedildi",
                            listenbrainzToken = ""
                        )
                    }
                    loadMusicSettings()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingListenBrainz = false,
                            listenbrainzSaveMessage = "Hata: ${result.message}"
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSavingListenBrainz = false) }
            }
        }
    }

    fun syncListenBrainz() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = "listenbrainz", syncStatusMessage = null) }
            val result = repository.syncListenBrainz()
            when (result) {
                is Resource.Success -> {
                    val count = result.data.inserted + result.data.updated
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = "ListenBrainz eşitlendi: $count parça güncellendi!"
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = "Hata: ${result.message}"
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSyncing = null) }
            }
        }
    }

    fun updateStatsFmUsername(username: String) {
        _uiState.update { it.copy(statsfmUsername = username, statsfmSaveMessage = null) }
    }

    fun saveStatsFmSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingStatsFm = true, statsfmSaveMessage = null) }
            val username = _uiState.value.statsfmUsername.trim()
            val result = repository.saveMusicSettings(
                statsfmUsername = username
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSavingStatsFm = false,
                            statsfmSaveMessage = "Stats.fm ayarları kaydedildi"
                        )
                    }
                    loadMusicSettings()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingStatsFm = false,
                            statsfmSaveMessage = "Hata: ${result.message}"
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSavingStatsFm = false) }
            }
        }
    }

    fun syncStatsFm() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = "statsfm", syncStatusMessage = null) }
            val result = repository.syncStatsFm()
            when (result) {
                is Resource.Success -> {
                    val count = result.data.inserted + result.data.updated
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = "Stats.fm eşitlendi: $count akış güncellendi!"
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = "Hata: ${result.message}"
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSyncing = null) }
            }
        }
    }

    fun clearListenbrainzSaveMessage() {
        _uiState.update { it.copy(listenbrainzSaveMessage = null) }
    }

    fun clearStatsfmSaveMessage() {
        _uiState.update { it.copy(statsfmSaveMessage = null) }
    }

    fun clearSyncStatusMessage() {
        _uiState.update { it.copy(syncStatusMessage = null) }
    }

    fun clearMusicSaveMessage() {
        _uiState.update { it.copy(musicSaveMessage = null) }
    }
}
