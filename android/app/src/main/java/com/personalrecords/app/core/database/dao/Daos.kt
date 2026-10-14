package com.personalrecords.app.core.database.dao

import androidx.room.*
import com.personalrecords.app.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM cached_activities ORDER BY date DESC, time DESC")
    fun getAllActivities(): Flow<List<CachedActivityEntity>>

    @Query("SELECT * FROM cached_activities ORDER BY date DESC, time DESC")
    suspend fun getAllActivitiesList(): List<CachedActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(items: List<CachedActivityEntity>)

    @Query("DELETE FROM cached_activities")
    suspend fun clearActivities()
}

@Dao
interface BookDao {
    @Query("SELECT * FROM cached_books ORDER BY bookId DESC")
    fun getAllBooks(): Flow<List<CachedBookEntity>>

    @Query("SELECT * FROM cached_books ORDER BY bookId DESC")
    suspend fun getAllBooksList(): List<CachedBookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<CachedBookEntity>)

    @Query("DELETE FROM cached_books")
    suspend fun clearBooks()
}

@Dao
interface FilmDao {
    @Query("SELECT * FROM cached_films ORDER BY watchedDate DESC, filmKey DESC")
    fun getAllFilms(): Flow<List<CachedFilmEntity>>

    @Query("SELECT * FROM cached_films ORDER BY watchedDate DESC, filmKey DESC")
    suspend fun getAllFilmsList(): List<CachedFilmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilms(films: List<CachedFilmEntity>)

    @Query("DELETE FROM cached_films")
    suspend fun clearFilms()
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM cached_foods ORDER BY dateInt DESC, foodEntryId DESC")
    fun getAllFoods(): Flow<List<CachedFoodEntity>>

    @Query("SELECT * FROM cached_foods ORDER BY dateInt DESC, foodEntryId DESC")
    suspend fun getAllFoodsList(): List<CachedFoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoods(foods: List<CachedFoodEntity>)

    @Query("DELETE FROM cached_foods")
    suspend fun clearFoods()
}

@Dao
interface HealthRecordDao {
    @Query("SELECT * FROM cached_health_records ORDER BY endTime DESC, startTime DESC")
    suspend fun getAllHealthRecordsList(): List<CachedHealthRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthRecords(records: List<CachedHealthRecordEntity>)

    @Query("DELETE FROM cached_health_records")
    suspend fun clearHealthRecords()
}

