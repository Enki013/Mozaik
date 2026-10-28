package com.personalrecords.app.core.network

import com.personalrecords.app.core.network.model.*
import retrofit2.http.*

interface ApiService {
    @GET("api/health")
    suspend fun checkHealth(): HealthResponse

    @GET("api/auth/verify")
    suspend fun verifyAuth(): AuthVerifyDto

    @GET("api/dashboard")
    suspend fun getDashboard(): DashboardDto

    @GET("api/activity")
    suspend fun getActivity(
        @Query("days") days: Int? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("date") date: String? = null,
        @Query("limit") limit: Int? = 100
    ): ActivityResponseDto

    @GET("api/fatsecret/food-entries")
    suspend fun getFoodEntries(
        @Query("limit") limit: Int? = 500
    ): FoodEntriesResponseDto

    @GET("api/fatsecret/daily-summary")
    suspend fun getDailySummary(
        @Query("limit") limit: Int? = 30
    ): DailySummaryResponseDto

    @GET("api/goodreads/books")
    suspend fun getBooks(
        @Query("q") query: String? = null,
        @Query("shelf") shelf: String? = null,
        @Query("limit") limit: Int? = 500
    ): BooksResponseDto

    @GET("api/letterboxd/films")
    suspend fun getFilms(
        @Query("limit") limit: Int? = 500
    ): FilmsResponseDto

    @POST("api/sync/fatsecret")
    suspend fun syncFatSecret(
        @Body request: SyncFatSecretRequest
    ): SyncResultDto

    @POST("api/sync/goodreads")
    suspend fun syncGoodreads(): SyncResultDto

    @GET("api/settings/goodreads")
    suspend fun getGoodreadsSettings(): SyncSettingsDto

    @POST("api/settings/goodreads")
    suspend fun saveGoodreadsSettings(
        @Body request: SaveSyncSettingsRequest
    ): SaveSyncSettingsResponse

    @POST("api/sync/letterboxd")
    suspend fun syncLetterboxd(): SyncResultDto

    @GET("api/settings/letterboxd")
    suspend fun getLetterboxdSettings(): SyncSettingsDto

    @POST("api/settings/letterboxd")
    suspend fun saveLetterboxdSettings(
        @Body request: SaveSyncSettingsRequest
    ): SaveSyncSettingsResponse

    @GET("api/settings/calorie-target")
    suspend fun getCalorieTarget(): CalorieTargetResponseDto

    @POST("settings/calorie-target")
    suspend fun updateCalorieTarget(
        @Query("target") target: Int
    ): CalorieTargetResponseDto

    @GET("api/settings/fatsecret")
    suspend fun getFatSecretSettings(): FatSecretSettingsDto

    @POST("api/settings/fatsecret")
    suspend fun saveFatSecretKeys(
        @Body request: SaveFatSecretKeysRequest
    ): SaveFatSecretKeysResponse

    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("limit") limit: Int = 100,
        @Query("package") packageName: String? = null
    ): NotificationsResponse

    @GET("api/health-connect/records")
    suspend fun getHealthConnectRecords(
        @Query("category") category: String? = null,
        @Query("type") recordType: String? = null,
        @Query("date") date: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("limit") limit: Int = 300
    ): HealthConnectResponse

    // Music & Audio
    @GET("api/music")
    suspend fun getMusicScrobbles(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("search") search: String? = null,
        @Query("artist") artist: String? = null,
        @Query("album") album: String? = null,
        @Query("source") source: String? = null,
        @Query("date") date: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): MusicResponse

    @GET("api/music/stats")
    suspend fun getMusicStats(): MusicStatsDto

    @GET("api/settings/music")
    suspend fun getMusicSettings(): MusicSettingsDto

    @POST("api/settings/music")
    suspend fun saveMusicSettings(
        @Body request: SaveMusicSettingsRequest
    ): SaveMusicSettingsResponse

    @POST("api/music/sync-lastfm")
    suspend fun syncLastFm(
        @Body request: SyncLastFmRequest = SyncLastFmRequest()
    ): SyncLastFmResponse

    @POST("api/music/sync-listenbrainz")
    suspend fun syncListenBrainz(
        @Body request: SyncListenBrainzRequest = SyncListenBrainzRequest()
    ): SyncListenBrainzResponse

    @POST("api/music/sync-statsfm")
    suspend fun syncStatsFm(
        @Body request: SyncStatsFmRequest = SyncStatsFmRequest()
    ): SyncStatsFmResponse

    // Integration Plugins
    @GET("api/settings/plugins")
    suspend fun getPluginSettings(): PluginSettingsResponse

    @POST("api/settings/plugins")
    suspend fun togglePlugin(
        @Body request: PluginToggleRequest
    ): PluginSettingsResponse

    @POST("api/settings/plugins")
    suspend fun syncEnabledPlugins(
        @Body request: PluginsSyncRequest
    ): PluginSettingsResponse

    // Trakt.tv Integration
    @GET("api/settings/trakt")
    suspend fun getTraktSettings(): TraktSettingsDto

    @POST("api/settings/trakt")
    suspend fun saveTraktSettings(
        @Body request: SaveTraktSettingsRequest
    ): SyncResultDto

    @POST("api/sync/trakt")
    suspend fun syncTrakt(
        @Query("force") force: Boolean = false
    ): SyncResultDto

    @GET("api/trakt/history")
    suspend fun getTraktHistory(
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("page") page: Int = 1
    ): TraktHistoryResponseDto

    // MDBList Integration
    @GET("api/settings/mdblist")
    suspend fun getMdbListSettings(): MdbListSettingsDto

    @POST("api/settings/mdblist")
    suspend fun saveMdbListSettings(
        @Body request: SaveMdbListSettingsRequest
    ): SyncResultDto

    @GET("api/media/ratings")
    suspend fun getMediaRatings(
        @Query("imdb_id") imdbId: String? = null,
        @Query("tmdb_id") tmdbId: Long? = null,
        @Query("title") title: String? = null,
        @Query("year") year: Int? = null,
        @Query("type") type: String? = null
    ): MediaRatingsResponseDto

    @POST("api/sync/mdblist")
    suspend fun syncMdbList(
        @Query("force") force: Boolean = false
    ): SyncResultDto

    // Simkl Integration
    @GET("api/settings/simkl")
    suspend fun getSimklSettings(): SimklSettingsDto

    @POST("api/settings/simkl")
    suspend fun saveSimklSettings(
        @Body request: SaveSimklSettingsRequest
    ): SyncResultDto

    @POST("api/sync/simkl")
    suspend fun syncSimkl(
        @Query("force") force: Boolean = false
    ): SyncResultDto

    @GET("api/simkl/history")
    suspend fun getSimklHistory(
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("page") page: Int = 1
    ): SimklHistoryResponseDto
}

