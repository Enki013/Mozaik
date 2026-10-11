package com.personalrecords.app.data.repository

import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.database.PersonalRecordsDatabase
import com.personalrecords.app.core.database.entity.*
import com.personalrecords.app.core.network.ApiService
import com.personalrecords.app.core.network.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PersonalRecordsRepository(
    private val api: ApiService,
    private val db: PersonalRecordsDatabase
) {
    @Volatile
    var cachedDashboard: DashboardDto? = null
        private set

    @Volatile
    var cachedDefaultActivity: ActivityResponseDto? = null
        private set

    @Volatile
    var cachedBooks: List<BookDto>? = null
        private set

    @Volatile
    var cachedFilms: List<FilmDto>? = null
        private set

    @Volatile
    var cachedFoods: List<FoodEntryDto>? = null
        private set

    @Volatile
    var cachedDailySummaries: List<DailySummaryDto>? = null
        private set

    @Volatile
    var cachedNotifications: List<NotificationDto>? = null
        private set

    suspend fun getCachedBooks(): List<BookDto> = withContext(Dispatchers.IO) {
        cachedBooks?.let { if (it.isNotEmpty()) return@withContext it }
        try {
            val cached = db.bookDao().getAllBooksList()
            if (cached.isNotEmpty()) {
                val dtos = cached.map {
                    BookDto(
                        book_id = it.bookId,
                        title = it.title,
                        author = it.author,
                        my_rating = it.myRating,
                        average_rating = it.averageRating,
                        number_of_pages = it.numberOfPages,
                        exclusive_shelf = it.exclusiveShelf,
                        image_url = it.imageUrl,
                        date_read = it.dateRead,
                        year_published = it.yearPublished
                    )
                }
                cachedBooks = dtos
                return@withContext dtos
            }
        } catch (_: Exception) {}
        emptyList()
    }

    suspend fun getCachedFilms(): List<FilmDto> = withContext(Dispatchers.IO) {
        cachedFilms?.let { if (it.isNotEmpty()) return@withContext it }
        try {
            val cached = db.filmDao().getAllFilmsList()
            if (cached.isNotEmpty()) {
                val dtos = cached.map {
                    FilmDto(
                        film_key = it.filmKey,
                        name = it.title,
                        year = it.year,
                        rating = it.rating,
                        rewatch = it.rewatch,
                        review = it.review,
                        image_url = it.imageUrl,
                        watched_date = it.watchedDate,
                        imported_at = it.createdAt
                    )
                }
                cachedFilms = dtos
                return@withContext dtos
            }
        } catch (_: Exception) {}
        emptyList()
    }

    suspend fun getCachedFoodEntries(): List<FoodEntryDto> = withContext(Dispatchers.IO) {
        cachedFoods?.let { if (it.isNotEmpty()) return@withContext it }
        try {
            val cached = db.foodDao().getAllFoodsList()
            if (cached.isNotEmpty()) {
                val dtos = cached.map {
                    FoodEntryDto(
                        food_entry_id = it.foodEntryId,
                        date_int = it.dateInt,
                        date = it.dateStr,
                        date_str = it.dateStr,
                        meal = it.meal,
                        name = it.name,
                        amount = it.amount,
                        calories = it.calories,
                        carbohydrate = it.carbohydrate,
                        protein = it.protein,
                        fat = it.fat
                    )
                }
                cachedFoods = dtos
                return@withContext dtos
            }
        } catch (_: Exception) {}
        emptyList()
    }

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            api.checkHealth().ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getDashboard(): Resource<DashboardDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.getDashboard()
            cachedDashboard = response
            Resource.Success(response)
        } catch (e: Exception) {
            cachedDashboard?.let { return@withContext Resource.Success(it) }
            Resource.Error(e.localizedMessage ?: "Sunucuya ulaşılamadı", e)
        }
    }

    suspend fun getActivity(
        days: Int? = null,
        from: String? = null,
        to: String? = null,
        date: String? = null,
        limit: Int? = 100
    ): Resource<ActivityResponseDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.getActivity(days, from, to, date, limit)
            if (days == 14 && from == null && to == null && date == null && (limit ?: 0) >= 100) {
                cachedDefaultActivity = response
            }
            try {
                val entities = response.items.map { dto ->
                    CachedActivityEntity(
                        id = dto.id,
                        source = dto.source,
                        type = dto.type,
                        date = dto.date,
                        time = dto.time,
                        timestamp = dto.timestamp,
                        title = dto.title,
                        subtitle = dto.subtitle,
                        imageUrl = dto.image_url,
                        rewatch = dto.rewatch,
                        rawDetailsJson = dto.details.toString()
                    )
                }
                if (entities.isNotEmpty()) {
                    db.activityDao().insertActivities(entities)
                }
            } catch (dbEx: Exception) {
                android.util.Log.e("PersonalRecords", "Failed to cache activities in Room", dbEx)
            }
            Resource.Success(response)
        } catch (e: Exception) {
            android.util.Log.e("PersonalRecords", "getActivity error: ${e.message}", e)
            Resource.Error(e.localizedMessage ?: "Aktiviteler getirilemedi", e)
        }
    }

    suspend fun getFoodEntries(limit: Int = 500): Resource<List<FoodEntryDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getFoodEntries(limit)
            cachedFoods = response.items
            try {
                val entities = response.items.map { dto ->
                    CachedFoodEntity(
                        foodEntryId = dto.food_entry_id.ifBlank { "${dto.date_int}_${dto.displayName}" },
                        dateInt = dto.date_int,
                        dateStr = dto.displayDate,
                        meal = dto.meal,
                        name = dto.displayName,
                        amount = dto.displayAmount,
                        calories = dto.calories,
                        carbohydrate = dto.carbohydrate,
                        protein = dto.protein,
                        fat = dto.fat
                    )
                }
                if (entities.isNotEmpty()) {
                    db.foodDao().insertFoods(entities)
                }
            } catch (dbEx: Exception) {
                android.util.Log.e("PersonalRecords", "Failed to cache foods in Room", dbEx)
            }
            Resource.Success(response.items)
        } catch (e: Exception) {
            android.util.Log.e("PersonalRecords", "getFoodEntries error: ${e.message}", e)
            val fallback = getCachedFoodEntries()
            if (fallback.isNotEmpty()) {
                return@withContext Resource.Success(fallback)
            }
            Resource.Error(e.localizedMessage ?: "Öğünler getirilemedi", e)
        }
    }

    suspend fun getDailySummary(limit: Int = 30): Resource<List<DailySummaryDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getDailySummary(limit)
            cachedDailySummaries = response.items
            Resource.Success(response.items)
        } catch (e: Exception) {
            cachedDailySummaries?.let { if (it.isNotEmpty()) return@withContext Resource.Success(it) }
            Resource.Error(e.localizedMessage ?: "Günlük özet getirilemedi", e)
        }
    }

    suspend fun getBooks(query: String? = null, shelf: String? = null, limit: Int = 500): Resource<List<BookDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getBooks(query, shelf, limit)
            if (query == null && shelf == null) {
                cachedBooks = response.items
            }
            try {
                val entities = response.items.map { dto ->
                    CachedBookEntity(
                        bookId = dto.book_id,
                        title = dto.title,
                        author = dto.author,
                        myRating = dto.my_rating,
                        averageRating = dto.average_rating,
                        numberOfPages = dto.number_of_pages,
                        exclusiveShelf = dto.exclusive_shelf,
                        imageUrl = dto.image_url,
                        dateRead = dto.date_read,
                        yearPublished = dto.year_published
                    )
                }
                if (entities.isNotEmpty()) {
                    db.bookDao().insertBooks(entities)
                }
            } catch (dbEx: Exception) {
                android.util.Log.e("PersonalRecords", "Failed to cache books in Room", dbEx)
            }
            Resource.Success(response.items)
        } catch (e: Exception) {
            android.util.Log.e("PersonalRecords", "getBooks error: ${e.message}", e)
            val fallback = getCachedBooks()
            if (fallback.isNotEmpty()) {
                return@withContext Resource.Success(fallback)
            }
            Resource.Error(e.localizedMessage ?: "Kitaplar getirilemedi", e)
        }
    }

    suspend fun getFilms(limit: Int = 500): Resource<List<FilmDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getFilms(limit)
            cachedFilms = response.items
            try {
                val entities = response.items.map { dto ->
                    CachedFilmEntity(
                        filmKey = dto.film_key.ifBlank { dto.displayTitle },
                        title = dto.displayTitle,
                        year = dto.safeYear,
                        rating = dto.safeRating,
                        rewatch = dto.rewatch,
                        review = dto.safeReview,
                        imageUrl = dto.image_url,
                        watchedDate = dto.safeWatchedDate,
                        createdAt = dto.imported_at ?: ""
                    )
                }
                if (entities.isNotEmpty()) {
                    db.filmDao().insertFilms(entities)
                }
            } catch (dbEx: Exception) {
                android.util.Log.e("PersonalRecords", "Failed to cache films in Room", dbEx)
            }
            Resource.Success(response.items)
        } catch (e: Exception) {
            android.util.Log.e("PersonalRecords", "getFilms error: ${e.message}", e)
            val fallback = getCachedFilms()
            if (fallback.isNotEmpty()) {
                return@withContext Resource.Success(fallback)
            }
            Resource.Error(e.localizedMessage ?: "Filmler getirilemedi: ${e.message}", e)
        }
    }

    suspend fun syncFatSecret(days: Int): Resource<SyncResultDto> {
        return try {
            val result = api.syncFatSecret(SyncFatSecretRequest(days))
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "FatSecret sync başarısız", e)
        }
    }

    suspend fun syncGoodreads(): Resource<SyncResultDto> {
        return try {
            val result = api.syncGoodreads()
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Goodreads sync başarısız", e)
        }
    }

    suspend fun getGoodreadsSettings(): Resource<SyncSettingsDto> {
        return try {
            val result = api.getGoodreadsSettings()
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Goodreads ayarları alınamadı", e)
        }
    }

    suspend fun saveGoodreadsSettings(feedUrl: String, intervalMinutes: Int = 360, enabled: Boolean = true): Resource<SaveSyncSettingsResponse> {
        return try {
            val result = api.saveGoodreadsSettings(SaveSyncSettingsRequest(feed_url = feedUrl, interval_minutes = intervalMinutes, enabled = enabled))
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Goodreads ayarları kaydedilemedi", e)
        }
    }

    suspend fun syncLetterboxd(): Resource<SyncResultDto> {
        return try {
            val result = api.syncLetterboxd()
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Letterboxd sync başarısız", e)
        }
    }

    suspend fun getLetterboxdSettings(): Resource<SyncSettingsDto> {
        return try {
            val result = api.getLetterboxdSettings()
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Letterboxd ayarları alınamadı", e)
        }
    }

    suspend fun saveLetterboxdSettings(feedUrl: String, intervalMinutes: Int = 360, enabled: Boolean = true): Resource<SaveSyncSettingsResponse> {
        return try {
            val result = api.saveLetterboxdSettings(SaveSyncSettingsRequest(feed_url = feedUrl, interval_minutes = intervalMinutes, enabled = enabled))
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Letterboxd ayarları kaydedilemedi", e)
        }
    }

    suspend fun getCalorieTarget(): Resource<CalorieTargetResponseDto> {
        return try {
            val result = api.getCalorieTarget()
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Kalori hedefi alınamadı", e)
        }
    }

    suspend fun updateCalorieTarget(target: Int): Resource<CalorieTargetResponseDto> {
        return try {
            val result = api.updateCalorieTarget(target)
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Kalori hedefi güncellenemedi", e)
        }
    }

    suspend fun getFatSecretSettings(): Resource<FatSecretSettingsDto> {
        return try {
            val result = api.getFatSecretSettings()
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "FatSecret ayarları alınamadı", e)
        }
    }

    suspend fun saveFatSecretKeys(key: String, secret: String): Resource<SaveFatSecretKeysResponse> {
        return try {
            val result = api.saveFatSecretKeys(SaveFatSecretKeysRequest(consumer_key = key, consumer_secret = secret))
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "FatSecret anahtarları kaydedilemedi", e)
        }
    }

    suspend fun getNotifications(limit: Int = 100, packageName: String? = null): Resource<List<NotificationDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getNotifications(limit, packageName)
            if (packageName == null) {
                cachedNotifications = response.items
            }
            Resource.Success(response.items)
        } catch (e: Exception) {
            if (packageName == null) {
                cachedNotifications?.let { if (it.isNotEmpty()) return@withContext Resource.Success(it) }
            }
            Resource.Error(e.localizedMessage ?: "Bildirimler alınamadı", e)
        }
    }

    suspend fun prewarmRoomCaches() = withContext(Dispatchers.IO) {
        getCachedBooks()
        getCachedFilms()
        getCachedFoodEntries()
    }
}
