package com.personalrecords.app.ui.screens.settings

import com.personalrecords.app.R
import com.personalrecords.app.ui.common.UiMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    val serverApiKey: String = "",
    val authStatusMessage: UiMessage? = null,
    val isTestingConnection: Boolean = false,
    val connectionSuccess: Boolean? = null,
    val isSyncing: String? = null,
    val syncStatusMessage: UiMessage? = null,
    // FatSecret BYOK
    val consumerKey: String = "",
    val consumerSecret: String = "",
    val hasKeys: Boolean = false,
    val isConnected: Boolean = false,
    val maskedKey: String = "",
    val isLoadingFsSettings: Boolean = false,
    val isSavingKeys: Boolean = false,
    val keySaveMessage: UiMessage? = null,
    // Letterboxd
    val letterboxdUrl: String = "",
    val isLoadingLetterboxd: Boolean = false,
    val isSavingLetterboxd: Boolean = false,
    val letterboxdSaveMessage: UiMessage? = null,
    // Goodreads
    val goodreadsUrl: String = "",
    val isLoadingGoodreads: Boolean = false,
    val isSavingGoodreads: Boolean = false,
    val goodreadsSaveMessage: UiMessage? = null,
    // Music & Scrobbler
    val lastfmUsername: String = "",
    val lastfmApiKey: String = "",
    val lastfmApiKeySet: Boolean = false,
    val musicWebhookUrl: String = "",
    val webScrobblerEnabled: Boolean = false,
    val isLoadingMusicSettings: Boolean = false,
    val isSavingMusicSettings: Boolean = false,
    val musicSaveMessage: UiMessage? = null,
    // ListenBrainz
    val listenbrainzUsername: String = "",
    val listenbrainzToken: String = "",
    val listenbrainzTokenSet: Boolean = false,
    val isSavingListenBrainz: Boolean = false,
    val listenbrainzSaveMessage: UiMessage? = null,
    // Stats.fm
    val statsfmUsername: String = "",
    val isSavingStatsFm: Boolean = false,
    val statsfmSaveMessage: UiMessage? = null,
    // Trakt.tv
    val traktUsername: String = "",
    val traktClientId: String = "",
    val traktSyncMovies: Boolean = true,
    val traktSyncEpisodes: Boolean = true,
    val traktSyncRatings: Boolean = true,
    val isLoadingTrakt: Boolean = false,
    val isSavingTrakt: Boolean = false,
    val traktSaveMessage: UiMessage? = null,
    // MDBList
    val mdblistApiKey: String = "",
    val mdblistSyncRatings: Boolean = true,
    val mdblistSyncLists: Boolean = false,
    val isLoadingMdbList: Boolean = false,
    val isSavingMdbList: Boolean = false,
    val mdblistSaveMessage: UiMessage? = null,
    // Simkl
    val simklClientId: String = "",
    val simklAccessToken: String = "",
    val simklMaskedToken: String = "",
    val isSimklConnected: Boolean = false,
    val simklSyncMovies: Boolean = true,
    val simklSyncShows: Boolean = true,
    val simklSyncAnime: Boolean = true,
    val simklSyncRatings: Boolean = true,
    val isLoadingSimkl: Boolean = false,
    val isSavingSimkl: Boolean = false,
    val simklSaveMessage: UiMessage? = null,
    // Language
    val appLanguage: String = "system",
    // Appearance
    val themeMode: String = "system",
    val dynamicColor: Boolean = true,
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
            preferences.serverApiKey.collect { key ->
                _uiState.update { it.copy(serverApiKey = key) }
            }
        }
        viewModelScope.launch {
            preferences.appLanguage.collect { lang ->
                _uiState.update { it.copy(appLanguage = lang) }
            }
        }
        viewModelScope.launch {
            preferences.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            preferences.dynamicColor.collect { enabled ->
                _uiState.update { it.copy(dynamicColor = enabled) }
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

    /** Integration (or "server") whose settings page is open; set before navigating there. */
    var openSettingsPageId by androidx.compose.runtime.mutableStateOf<String?>(null)

    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
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
        loadTraktSettings()
        loadMdbListSettings()
        loadSimklSettings()
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
            _uiState.update { it.copy(keySaveMessage = UiMessage.error(R.string.settings_msg_keys_empty)) }
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
                            keySaveMessage = UiMessage.serverOr(result.data.message, R.string.settings_msg_keys_saved),
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
                            keySaveMessage = UiMessage.error(R.string.settings_msg_error, result.message)
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSavingKeys = false) }
            }
        }
    }

    fun updateServerUrlInput(url: String) {
        _uiState.update { it.copy(serverUrl = url, connectionSuccess = null, authStatusMessage = null) }
    }

    fun updateServerApiKeyInput(apiKey: String) {
        _uiState.update { it.copy(serverApiKey = apiKey, connectionSuccess = null, authStatusMessage = null) }
    }

    fun saveServerUrl() {
        viewModelScope.launch {
            preferences.setServerBaseUrl(_uiState.value.serverUrl)
            preferences.setServerApiKey(_uiState.value.serverApiKey)
            testConnection()
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, connectionSuccess = null, authStatusMessage = null) }
            val ok = repository.checkHealth()
            if (!ok) {
                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        connectionSuccess = false,
                        authStatusMessage = UiMessage.error(R.string.settings_msg_server_unreachable)
                    )
                }
                return@launch
            }

            val authResult = repository.verifyAuth()
            when (authResult) {
                is Resource.Success -> {
                    val auth = authResult.data
                    if (auth.required && !auth.authenticated) {
                        _uiState.update {
                            it.copy(
                                isTestingConnection = false,
                                connectionSuccess = false,
                                authStatusMessage = UiMessage.error(R.string.settings_msg_server_key_missing)
                            )
                        }
                    } else if (auth.required) {
                        _uiState.update {
                            it.copy(
                                isTestingConnection = false,
                                connectionSuccess = true,
                                authStatusMessage = UiMessage.of(R.string.settings_msg_server_verified)
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isTestingConnection = false,
                                connectionSuccess = true,
                                authStatusMessage = UiMessage.of(R.string.settings_msg_server_open)
                            )
                        }
                    }
                }
                is Resource.Error -> {
                    val is401 = authResult.message.contains("401") || authResult.cause?.message?.contains("401") == true
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectionSuccess = !is401,
                            authStatusMessage = if (is401) UiMessage.error(R.string.settings_msg_server_unauthorized) else UiMessage.of(R.string.settings_msg_server_connected)
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(isTestingConnection = false, connectionSuccess = true)
                    }
                }
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
                            syncStatusMessage = UiMessage.serverOr(serverMsg, R.string.settings_msg_fatsecret_synced, days)
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = UiMessage.error(R.string.settings_msg_error, result.message)
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
                            syncStatusMessage = UiMessage.of(R.string.settings_msg_synced, "Goodreads RSS")
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = UiMessage.error(R.string.settings_msg_error, result.message)
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
                            syncStatusMessage = UiMessage.of(R.string.settings_msg_synced, "Letterboxd RSS")
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = UiMessage.error(R.string.settings_msg_error, result.message)
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
                            syncStatusMessage = UiMessage.of(R.string.settings_msg_synced_tracks, "Last.fm", count)
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = UiMessage.error(R.string.settings_msg_error, result.message)
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
            _uiState.update { it.copy(letterboxdSaveMessage = UiMessage.error(R.string.settings_msg_letterboxd_empty)) }
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
                            letterboxdSaveMessage = UiMessage.serverOr(res.data.message, R.string.settings_msg_saved, "Letterboxd")
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingLetterboxd = false,
                            letterboxdSaveMessage = UiMessage.error(R.string.settings_msg_error, res.message)
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
            _uiState.update { it.copy(goodreadsSaveMessage = UiMessage.error(R.string.settings_msg_goodreads_empty)) }
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
                            goodreadsSaveMessage = UiMessage.serverOr(res.data.message, R.string.settings_msg_saved, "Goodreads")
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingGoodreads = false,
                            goodreadsSaveMessage = UiMessage.error(R.string.settings_msg_error, res.message)
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
                            musicSaveMessage = UiMessage.of(R.string.settings_msg_settings_saved, "Last.fm"),
                            lastfmApiKey = ""
                        )
                    }
                    loadMusicSettings()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingMusicSettings = false,
                            musicSaveMessage = UiMessage.error(R.string.settings_msg_error, result.message)
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
                            listenbrainzSaveMessage = UiMessage.of(R.string.settings_msg_settings_saved, "ListenBrainz"),
                            listenbrainzToken = ""
                        )
                    }
                    loadMusicSettings()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingListenBrainz = false,
                            listenbrainzSaveMessage = UiMessage.error(R.string.settings_msg_error, result.message)
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
                            syncStatusMessage = UiMessage.of(R.string.settings_msg_synced_tracks, "ListenBrainz", count)
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = UiMessage.error(R.string.settings_msg_error, result.message)
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
                            statsfmSaveMessage = UiMessage.of(R.string.settings_msg_settings_saved, "Stats.fm")
                        )
                    }
                    loadMusicSettings()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingStatsFm = false,
                            statsfmSaveMessage = UiMessage.error(R.string.settings_msg_error, result.message)
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
                            syncStatusMessage = UiMessage.of(R.string.settings_msg_synced_tracks, "Stats.fm", count)
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = UiMessage.error(R.string.settings_msg_error, result.message)
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

    fun clearKeySaveMessage() {
        _uiState.update { it.copy(keySaveMessage = null) }
    }

    fun clearLetterboxdSaveMessage() {
        _uiState.update { it.copy(letterboxdSaveMessage = null) }
    }

    fun clearGoodreadsSaveMessage() {
        _uiState.update { it.copy(goodreadsSaveMessage = null) }
    }

    fun clearMusicSaveMessage() {
        _uiState.update { it.copy(musicSaveMessage = null) }
    }

    // ==========================================
    // Trakt.tv Methods
    // ==========================================

    fun loadTraktSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTrakt = true) }
            when (val result = repository.getTraktSettings()) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingTrakt = false,
                            traktUsername = result.data.username,
                            traktClientId = result.data.client_id,
                            traktSyncMovies = result.data.sync_movies,
                            traktSyncEpisodes = result.data.sync_episodes,
                            traktSyncRatings = result.data.sync_ratings
                        )
                    }
                }
                else -> _uiState.update { it.copy(isLoadingTrakt = false) }
            }
        }
    }

    fun saveTraktSettings(
        username: String,
        clientId: String,
        syncMovies: Boolean = true,
        syncEpisodes: Boolean = true,
        syncRatings: Boolean = true
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingTrakt = true, traktSaveMessage = null) }
            val req = com.personalrecords.app.core.network.model.SaveTraktSettingsRequest(
                username = username.trim(),
                client_id = clientId.trim().ifEmpty { null },
                sync_movies = syncMovies,
                sync_episodes = syncEpisodes,
                sync_ratings = syncRatings
            )
            when (val result = repository.saveTraktSettings(req)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSavingTrakt = false,
                            traktUsername = username.trim(),
                            traktClientId = clientId.trim(),
                            traktSyncMovies = syncMovies,
                            traktSyncEpisodes = syncEpisodes,
                            traktSyncRatings = syncRatings,
                            traktSaveMessage = UiMessage.of(R.string.settings_msg_settings_saved, "Trakt")
                        )
                    }
                    if (username.isNotBlank()) {
                        preferences.toggleIntegrationPlugin("trakt", true)
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingTrakt = false,
                            traktSaveMessage = UiMessage.error(R.string.settings_msg_error, result.message)
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSavingTrakt = false) }
            }
        }
    }

    fun syncTrakt() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = "trakt", syncStatusMessage = null) }
            when (val result = repository.syncTrakt(force = true)) {
                is Resource.Success -> {
                    val count = result.data.inserted + result.data.updated
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = UiMessage.of(R.string.settings_msg_synced_items, "Trakt.tv", count)
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = UiMessage.error(R.string.settings_msg_error, result.message)
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSyncing = null) }
            }
        }
    }

    fun clearTraktSaveMessage() {
        _uiState.update { it.copy(traktSaveMessage = null) }
    }

    // ==========================================
    // MDBList Methods
    // ==========================================

    fun loadMdbListSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMdbList = true) }
            when (val result = repository.getMdbListSettings()) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingMdbList = false,
                            mdblistApiKey = result.data.api_key,
                            mdblistSyncRatings = result.data.sync_ratings_enabled,
                            mdblistSyncLists = result.data.sync_lists_enabled
                        )
                    }
                }
                else -> _uiState.update { it.copy(isLoadingMdbList = false) }
            }
        }
    }

    fun saveMdbListSettings(
        apiKey: String,
        syncRatings: Boolean = true,
        syncLists: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingMdbList = true, mdblistSaveMessage = null) }
            val req = com.personalrecords.app.core.network.model.SaveMdbListSettingsRequest(
                api_key = apiKey.trim(),
                sync_ratings_enabled = syncRatings,
                sync_lists_enabled = syncLists
            )
            when (val result = repository.saveMdbListSettings(req)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSavingMdbList = false,
                            mdblistApiKey = apiKey.trim(),
                            mdblistSyncRatings = syncRatings,
                            mdblistSyncLists = syncLists,
                            mdblistSaveMessage = UiMessage.of(R.string.settings_msg_settings_saved, "MDBList")
                        )
                    }
                    if (apiKey.isNotBlank()) {
                        preferences.toggleIntegrationPlugin("mdblist", true)
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingMdbList = false,
                            mdblistSaveMessage = UiMessage.error(R.string.settings_msg_error, result.message)
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSavingMdbList = false) }
            }
        }
    }

    fun syncMdbList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = "mdblist", syncStatusMessage = null) }
            when (val result = repository.syncMdbList(force = true)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = UiMessage.of(R.string.settings_msg_mdblist_synced)
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = UiMessage.error(R.string.settings_msg_error, result.message)
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSyncing = null) }
            }
        }
    }

    fun clearMdbListSaveMessage() {
        _uiState.update { it.copy(mdblistSaveMessage = null) }
    }

    // ==========================================
    // Simkl Methods
    // ==========================================

    fun loadSimklSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSimkl = true) }
            when (val result = repository.getSimklSettings()) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingSimkl = false,
                            simklClientId = result.data.client_id,
                            simklMaskedToken = result.data.masked_token,
                            isSimklConnected = result.data.is_connected,
                            simklSyncMovies = result.data.sync_movies,
                            simklSyncShows = result.data.sync_shows,
                            simklSyncAnime = result.data.sync_anime,
                            simklSyncRatings = result.data.sync_ratings
                        )
                    }
                }
                else -> _uiState.update { it.copy(isLoadingSimkl = false) }
            }
        }
    }

    fun saveSimklSettings(
        clientId: String,
        accessToken: String,
        syncMovies: Boolean = true,
        syncShows: Boolean = true,
        syncAnime: Boolean = true,
        syncRatings: Boolean = true
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingSimkl = true, simklSaveMessage = null) }
            val req = com.personalrecords.app.core.network.model.SaveSimklSettingsRequest(
                client_id = clientId.trim().ifEmpty { null },
                access_token = accessToken.trim().ifEmpty { null },
                sync_movies = syncMovies,
                sync_shows = syncShows,
                sync_anime = syncAnime,
                sync_ratings = syncRatings
            )
            when (val result = repository.saveSimklSettings(req)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSavingSimkl = false,
                            simklClientId = clientId.trim(),
                            simklSyncMovies = syncMovies,
                            simklSyncShows = syncShows,
                            simklSyncAnime = syncAnime,
                            simklSyncRatings = syncRatings,
                            isSimklConnected = accessToken.isNotBlank() || it.isSimklConnected,
                            simklSaveMessage = UiMessage.of(R.string.settings_msg_settings_saved, "Simkl")
                        )
                    }
                    if (accessToken.isNotBlank()) {
                        preferences.toggleIntegrationPlugin("simkl", true)
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingSimkl = false,
                            simklSaveMessage = UiMessage.error(R.string.settings_msg_error, result.message)
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSavingSimkl = false) }
            }
        }
    }

    fun syncSimkl() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = "simkl", syncStatusMessage = null) }
            when (val result = repository.syncSimkl(force = true)) {
                is Resource.Success -> {
                    val count = result.data.inserted + result.data.updated
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = UiMessage.of(R.string.settings_msg_synced_items, "Simkl", count)
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncStatusMessage = UiMessage.error(R.string.settings_msg_error, result.message)
                        )
                    }
                }
                else -> _uiState.update { it.copy(isSyncing = null) }
            }
        }
    }

    fun clearSimklSaveMessage() {
        _uiState.update { it.copy(simklSaveMessage = null) }
    }
}
