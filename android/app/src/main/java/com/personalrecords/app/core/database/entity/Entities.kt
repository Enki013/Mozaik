package com.personalrecords.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_activities")
data class CachedActivityEntity(
    @PrimaryKey val id: String,
    val source: String,
    val type: String,
    val date: String,
    val time: String?,
    val timestamp: String?,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val rewatch: Boolean,
    val rawDetailsJson: String
)

@Entity(tableName = "cached_books")
data class CachedBookEntity(
    @PrimaryKey val bookId: String,
    val title: String,
    val author: String,
    val myRating: Double,
    val averageRating: Double,
    val numberOfPages: Int,
    val exclusiveShelf: String,
    val imageUrl: String?,
    val dateRead: String,
    val yearPublished: Int
)

@Entity(tableName = "cached_films")
data class CachedFilmEntity(
    @PrimaryKey val filmKey: String,
    val title: String,
    val year: Int,
    val rating: Double,
    val rewatch: Boolean,
    val review: String,
    val imageUrl: String?,
    val watchedDate: String,
    val createdAt: String
)

@Entity(tableName = "cached_foods")
data class CachedFoodEntity(
    @PrimaryKey val foodEntryId: String,
    val dateInt: Int,
    val dateStr: String,
    val meal: String,
    val name: String,
    val amount: String,
    val calories: Double,
    val carbohydrate: Double,
    val protein: Double,
    val fat: Double
)
