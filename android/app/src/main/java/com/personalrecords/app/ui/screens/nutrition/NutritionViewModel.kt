package com.personalrecords.app.ui.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.datastore.AppPreferences
import com.personalrecords.app.core.network.model.DailySummaryDto
import com.personalrecords.app.core.network.model.FoodEntryDto
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class NutritionUiState(
    val isLoading: Boolean = false,
    val calorieTarget: Int? = null,
    val todayCalories: Double = 0.0,
    val todayEntriesCount: Int = 0,
    val totalCarb: Double = 0.0,
    val totalProtein: Double = 0.0,
    val totalFat: Double = 0.0,
    val dailySummaries: List<DailySummaryDto> = emptyList(),
    val foodEntries: List<FoodEntryDto> = emptyList(),
    val searchQuery: String = "",
    val selectedDateFilter: String? = null,
    val fromDateFilter: String? = null,
    val toDateFilter: String? = null,
    val selectedMeal: String = "all", // "all", "Breakfast", "Lunch", "Dinner", "Other"
    val onlyHighProtein: Boolean = false,
    val error: String? = null
)

class NutritionViewModel(
    private val repository: PersonalRecordsRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(NutritionUiState())
    val uiState: StateFlow<NutritionUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val savedTarget = preferences.calorieTarget.first()
            val summaryRes = repository.getDailySummary(30)
            val foodsRes = repository.getFoodEntries(500)
            val targetRes = repository.getCalorieTarget()

            var activeTarget = savedTarget
            if (targetRes is Resource.Success) {
                val serverTarget = targetRes.data.target ?: targetRes.data.calorieTarget
                if (serverTarget != null && serverTarget > 0) {
                    activeTarget = serverTarget
                    preferences.setCalorieTarget(serverTarget)
                }
            }

            val summaries = if (summaryRes is Resource.Success) summaryRes.data else emptyList()
            val foods = if (foodsRes is Resource.Success) foodsRes.data else emptyList()

            // Calculate today stats from summaries (only match today's date, otherwise 0)
            val todayIso = java.time.LocalDate.now().toString()
            val todaySummary = summaries.firstOrNull { it.displayDate == todayIso }
            val todayFoods = foods.filter { it.displayDate == todayIso }
            val todayCal = todaySummary?.displayCalories ?: todayFoods.sumOf { it.calories }
            val todayCount = todaySummary?.displayCount ?: todayFoods.size

            // Calculate overall macros
            var carb = 0.0
            var prot = 0.0
            var fat = 0.0
            foods.forEach {
                carb += it.carbohydrate
                prot += it.protein
                fat += it.fat
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                calorieTarget = activeTarget,
                todayCalories = todayCal,
                todayEntriesCount = todayCount,
                totalCarb = carb,
                totalProtein = prot,
                totalFat = fat,
                dailySummaries = summaries,
                foodEntries = foods
            )
        }
    }

    fun updateCalorieTarget(newTarget: Int) {
        viewModelScope.launch {
            preferences.setCalorieTarget(newTarget)
            repository.updateCalorieTarget(newTarget)
            _uiState.value = _uiState.value.copy(calorieTarget = newTarget)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setDateFilter(date: String?) {
        _uiState.value = _uiState.value.copy(
            selectedDateFilter = date,
            fromDateFilter = null,
            toDateFilter = null
        )
    }

    fun setDateRangeFilter(from: String?, to: String?) {
        _uiState.value = _uiState.value.copy(
            selectedDateFilter = null,
            fromDateFilter = from,
            toDateFilter = to
        )
    }

    fun clearDateFilter() {
        _uiState.value = _uiState.value.copy(
            selectedDateFilter = null,
            fromDateFilter = null,
            toDateFilter = null
        )
    }

    fun setMeal(meal: String) {
        _uiState.value = _uiState.value.copy(selectedMeal = meal)
    }

    fun toggleHighProtein() {
        _uiState.value = _uiState.value.copy(onlyHighProtein = !_uiState.value.onlyHighProtein)
    }

    fun resetFilters() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            selectedDateFilter = null,
            fromDateFilter = null,
            toDateFilter = null,
            selectedMeal = "all",
            onlyHighProtein = false
        )
    }
}
