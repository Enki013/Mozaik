package com.personalrecords.app.ui.common

import com.personalrecords.app.R
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.data.repository.PersonalRecordsRepository

/**
 * Runs one integration's sync and describes the result as a [UiMessage]. Shared by the Overview
 * sync row and the integrations catalog so both report the same text in the app language.
 * Returns null for services that have nothing to pull (webhook-only integrations).
 */
suspend fun PersonalRecordsRepository.syncIntegration(serviceKey: String, displayName: String): UiMessage? {
    fun <T> Resource<T>.toMessage(onSuccess: (T) -> UiMessage): UiMessage = when (this) {
        is Resource.Success -> onSuccess(data)
        is Resource.Error -> UiMessage.error(R.string.settings_msg_error, message)
        Resource.Loading -> UiMessage.error(R.string.sync_msg_failed, displayName)
    }
    // Server text when it sends one; otherwise how many items changed.
    fun counted(serverText: String, inserted: Int, updated: Int): UiMessage {
        val changed = inserted + updated
        return when {
            serverText.isNotBlank() -> UiMessage(raw = serverText)
            changed > 0 -> UiMessage.of(R.string.settings_msg_synced_tracks, displayName, changed)
            else -> UiMessage.of(R.string.sync_msg_up_to_date, displayName)
        }
    }
    val synced = { serverText: String -> UiMessage.serverOr(serverText, R.string.settings_msg_synced, displayName) }
    return when (serviceKey) {
        "fatsecret" -> syncFatSecret(30).toMessage { synced(it.message) }
        "goodreads" -> syncGoodreads().toMessage { synced(it.message) }
        "letterboxd" -> syncLetterboxd().toMessage { synced(it.message) }
        "lastfm", "music" -> syncLastFm().toMessage { synced(it.message) }
        "listenbrainz" -> syncListenBrainz().toMessage { counted(it.message, it.inserted, it.updated) }
        "statsfm" -> syncStatsFm().toMessage { counted(it.message, it.inserted, it.updated) }
        "trakt" -> syncTrakt(force = true).toMessage { synced(it.message) }
        "mdblist" -> syncMdbList(force = true).toMessage { synced(it.message) }
        "simkl" -> syncSimkl(force = true).toMessage { synced(it.message) }
        else -> null
    }
}
