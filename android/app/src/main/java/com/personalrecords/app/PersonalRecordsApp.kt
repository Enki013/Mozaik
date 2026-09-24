package com.personalrecords.app

import android.app.Application
import com.personalrecords.app.core.database.PersonalRecordsDatabase
import com.personalrecords.app.core.datastore.AppPreferences
import com.personalrecords.app.core.network.NetworkClient
import com.personalrecords.app.data.repository.PersonalRecordsRepository

class PersonalRecordsApp : Application() {
    lateinit var preferences: AppPreferences
        private set

    lateinit var database: PersonalRecordsDatabase
        private set

    lateinit var repository: PersonalRecordsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = AppPreferences(this)
        database = PersonalRecordsDatabase.getInstance(this)
        val apiService = NetworkClient.createApiService(preferences)
        repository = PersonalRecordsRepository(apiService, database)
    }

    companion object {
        lateinit var instance: PersonalRecordsApp
            private set
    }
}
