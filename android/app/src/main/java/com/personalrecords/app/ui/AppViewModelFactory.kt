package com.personalrecords.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.personalrecords.app.PersonalRecordsApp
import com.personalrecords.app.ui.screens.books.BooksViewModel
import com.personalrecords.app.ui.screens.movies.MoviesViewModel
import com.personalrecords.app.ui.screens.nutrition.NutritionViewModel
import com.personalrecords.app.ui.screens.overview.OverviewViewModel
import com.personalrecords.app.ui.screens.settings.SettingsViewModel
import com.personalrecords.app.ui.screens.timeline.TimelineViewModel

class AppViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val app = PersonalRecordsApp.instance
        val repository = app.repository
        val preferences = app.preferences

        return when {
            modelClass.isAssignableFrom(OverviewViewModel::class.java) -> {
                OverviewViewModel(repository) as T
            }
            modelClass.isAssignableFrom(TimelineViewModel::class.java) -> {
                TimelineViewModel(repository) as T
            }
            modelClass.isAssignableFrom(NutritionViewModel::class.java) -> {
                NutritionViewModel(repository, preferences) as T
            }
            modelClass.isAssignableFrom(BooksViewModel::class.java) -> {
                BooksViewModel(repository) as T
            }
            modelClass.isAssignableFrom(MoviesViewModel::class.java) -> {
                MoviesViewModel(repository) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(repository, preferences) as T
            }
            modelClass.isAssignableFrom(com.personalrecords.app.ui.screens.notifications.NotificationsViewModel::class.java) -> {
                com.personalrecords.app.ui.screens.notifications.NotificationsViewModel(repository) as T
            }
            modelClass.isAssignableFrom(com.personalrecords.app.ui.screens.records.RecordsViewModel::class.java) -> {
                com.personalrecords.app.ui.screens.records.RecordsViewModel(repository) as T
            }
            modelClass.isAssignableFrom(com.personalrecords.app.ui.screens.health.HealthConnectViewModel::class.java) -> {
                com.personalrecords.app.ui.screens.health.HealthConnectViewModel(repository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

