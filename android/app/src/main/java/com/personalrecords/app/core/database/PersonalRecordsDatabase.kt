package com.personalrecords.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.personalrecords.app.core.database.dao.*
import com.personalrecords.app.core.database.entity.*

@Database(
    entities = [
        CachedActivityEntity::class,
        CachedBookEntity::class,
        CachedFilmEntity::class,
        CachedFoodEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PersonalRecordsDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun bookDao(): BookDao
    abstract fun filmDao(): FilmDao
    abstract fun foodDao(): FoodDao

    companion object {
        @Volatile
        private var INSTANCE: PersonalRecordsDatabase? = null

        fun getInstance(context: Context): PersonalRecordsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PersonalRecordsDatabase::class.java,
                    "personal_records_mobile.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
