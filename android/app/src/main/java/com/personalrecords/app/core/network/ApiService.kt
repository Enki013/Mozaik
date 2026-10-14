package com.personalrecords.app.core.network

import com.personalrecords.app.core.network.model.*
import retrofit2.http.*

interface ApiService {
    @GET("api/health")
    suspend fun checkHealth(): HealthResponse

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
}

