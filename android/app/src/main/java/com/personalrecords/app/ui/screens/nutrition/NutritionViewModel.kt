package com.personalrecords.app.ui.screens.nutrition

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.datastore.AppPreferences
import com.personalrecords.app.core.network.model.DailySummaryDto
import com.personalrecords.app.core.network.model.FoodEntryDto
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NutritionUiState(
    val isLoading: Boolean = true,
    val isFiltering: Boolean = false,
    val calorieTarget: Int? = null,
    val todayCalories: Double = 0.0,
    val todayEntriesCount: Int = 0,
    val todayCarb: Double = 0.0,
    val todayProtein: Double = 0.0,
    val todayFat: Double = 0.0,
    val totalCarb: Double = 0.0,
    val totalProtein: Double = 0.0,
    val totalFat: Double = 0.0,
    val dailySummaries: List<DailySummaryDto> = emptyList(),
    val foodEntries: List<FoodEntryDto> = emptyList(),
    val filteredFoods: List<FoodEntryDto> = emptyList(),
    val searchQuery: String = "",
    val selectedDateFilter: String? = null,
    val fromDateFilter: String? = null,
    val toDateFilter: String? = null,
    val selectedMeal: String = "all", // "all", "Breakfast", "Lunch", "Dinner", "Other"
    val onlyHighProtein: Boolean = false,
    val error: String? = null
)

private fun filterFoodEntries(
    entries: List<FoodEntryDto>,
    query: String,
    singleDate: String?,
    fromDate: String?,
    toDate: String?,
    meal: String,
    highProt: Boolean
): List<FoodEntryDto> {
    if (entries.isEmpty()) return emptyList()
    return entries.filter { item ->
        val matchesSearch = query.isBlank() ||
            item.displayName.contains(query, ignoreCase = true) ||
            item.displayAmount.contains(query, ignoreCase = true)
        val matchesDate = when {
            singleDate != null -> item.displayDate == singleDate
            fromDate != null && toDate != null -> item.displayDate in fromDate..toDate
            fromDate != null -> item.displayDate >= fromDate
            toDate != null -> item.displayDate <= toDate
            else -> true
        }
        val matchesMeal = meal == "all" || item.meal.equals(meal, ignoreCase = true)
        val matchesProtein = !highProt || item.protein >= 20.0
        matchesSearch && matchesDate && matchesMeal && matchesProtein
    }
}

class NutritionViewModel(
    private val repository: PersonalRecordsRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(NutritionUiState())
    val uiState: StateFlow<NutritionUiState> = _uiState.asStateFlow()
    private var filterJob: Job? = null

    init {
        loadInitialData()
    }

    private suspend fun applyNutritionData(
        foods: List<FoodEntryDto>,
        summaries: List<DailySummaryDto>,
        activeTarget: Int?
    ) {
        val snap = _uiState.value
        val computed = withContext(Dispatchers.Default) {
            val todayIso = java.time.LocalDate.now().toString()
            val todaySummary = summaries.firstOrNull { it.displayDate == todayIso }
            val todayFoods = foods.filter { it.displayDate == todayIso }
            val todayCal = todaySummary?.displayCalories ?: todayFoods.sumOf { it.calories }
            val todayCount = todaySummary?.displayCount ?: todayFoods.size
            val todayCarb = todayFoods.sumOf { it.carbohydrate }
            val todayProt = todayFoods.sumOf { it.protein }
            val todayFat = todayFoods.sumOf { it.fat }

            var carb = 0.0
            var prot = 0.0
            var fat = 0.0
            for (entry in foods) {
                carb += entry.carbohydrate
                prot += entry.protein
                fat += entry.fat
            }

            val filtered = filterFoodEntries(
                entries = foods,
                query = snap.searchQuery,
                singleDate = snap.selectedDateFilter,
                fromDate = snap.fromDateFilter,
                toDate = snap.toDateFilter,
                meal = snap.selectedMeal,
                highProt = snap.onlyHighProtein
            )

            NutritionComputedResult(
                todayCalories = todayCal,
                todayEntriesCount = todayCount,
                todayCarb = todayCarb,
                todayProtein = todayProt,
                todayFat = todayFat,
                totalCarb = carb,
                totalProtein = prot,
                totalFat = fat,
                filteredFoods = filtered
            )
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isFiltering = false,
                calorieTarget = activeTarget,
                todayCalories = computed.todayCalories,
                todayEntriesCount = computed.todayEntriesCount,
                todayCarb = computed.todayCarb,
                todayProtein = computed.todayProtein,
                todayFat = computed.todayFat,
                totalCarb = computed.totalCarb,
                totalProtein = computed.totalProtein,
                totalFat = computed.totalFat,
                dailySummaries = summaries,
                foodEntries = foods,
                filteredFoods = computed.filteredFoods
            )
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val savedTarget = preferences.calorieTarget.first()
            val cachedFoods = repository.getCachedFoodEntries()
            val cachedSummaries = repository.cachedDailySummaries ?: emptyList()
            if (cachedFoods.isNotEmpty()) {
                applyNutritionData(cachedFoods, cachedSummaries, savedTarget)
            }
            // Wait for NavHost enter transition (~320ms) to finish before network + SQLite write
            delay(340L)
            fetchRemoteNutrition(showLoadingIfEmpty = cachedFoods.isEmpty())
        }
    }

    fun loadData() {
        viewModelScope.launch {
            fetchRemoteNutrition(showLoadingIfEmpty = true)
        }
    }

    private suspend fun fetchRemoteNutrition(showLoadingIfEmpty: Boolean) {
        if (showLoadingIfEmpty) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }
        val savedTarget = preferences.calorieTarget.first()

        val (summaryRes, foodsRes, targetRes) = coroutineScope {
            val sDeferred = async { repository.getDailySummary(30) }
            val fDeferred = async { repository.getFoodEntries(500) }
            val tDeferred = async { repository.getCalorieTarget() }
            Triple(sDeferred.await(), fDeferred.await(), tDeferred.await())
        }

        var activeTarget = savedTarget
        if (targetRes is Resource.Success) {
            val serverTarget = targetRes.data.target ?: targetRes.data.calorieTarget
            if (serverTarget != null && serverTarget > 0) {
                activeTarget = serverTarget
                preferences.setCalorieTarget(serverTarget)
            }
        }

        val summaries = if (summaryRes is Resource.Success) summaryRes.data else _uiState.value.dailySummaries
        val foods = if (foodsRes is Resource.Success) foodsRes.data else _uiState.value.foodEntries

        if (foods != _uiState.value.foodEntries ||
            summaries != _uiState.value.dailySummaries ||
            activeTarget != _uiState.value.calorieTarget ||
            _uiState.value.isLoading
        ) {
            applyNutritionData(foods, summaries, activeTarget)
        }
    }

    fun updateCalorieTarget(newTarget: Int) {
        viewModelScope.launch {
            preferences.setCalorieTarget(newTarget)
            repository.updateCalorieTarget(newTarget)
            _uiState.update { it.copy(calorieTarget = newTarget) }
        }
    }

    fun setSearchQuery(query: String) {
        if (_uiState.value.searchQuery == query) return
        _uiState.update { it.copy(searchQuery = query, isFiltering = true) }
        recomputeFilter(debounceMs = if (query.isEmpty()) 0L else 100L)
    }

    fun setDateFilter(date: String?) {
        _uiState.update {
            it.copy(
                selectedDateFilter = date,
                fromDateFilter = null,
                toDateFilter = null,
                isFiltering = true
            )
        }
        recomputeFilter(debounceMs = 0L)
    }

    fun setDateRangeFilter(from: String?, to: String?) {
        _uiState.update {
            it.copy(
                selectedDateFilter = null,
                fromDateFilter = from,
                toDateFilter = to,
                isFiltering = true
            )
        }
        recomputeFilter(debounceMs = 0L)
    }

    fun clearDateFilter() {
        _uiState.update {
            it.copy(
                selectedDateFilter = null,
                fromDateFilter = null,
                toDateFilter = null,
                isFiltering = true
            )
        }
        recomputeFilter(debounceMs = 0L)
    }

    fun setMeal(meal: String) {
        if (_uiState.value.selectedMeal == meal) return
        _uiState.update { it.copy(selectedMeal = meal, isFiltering = true) }
        recomputeFilter(debounceMs = 0L)
    }

    fun toggleHighProtein() {
        _uiState.update {
            it.copy(
                onlyHighProtein = !it.onlyHighProtein,
                isFiltering = true
            )
        }
        recomputeFilter(debounceMs = 0L)
    }

    fun resetFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                selectedDateFilter = null,
                fromDateFilter = null,
                toDateFilter = null,
                selectedMeal = "all",
                onlyHighProtein = false,
                isFiltering = true
            )
        }
        recomputeFilter(debounceMs = 0L)
    }

    private fun recomputeFilter(debounceMs: Long) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val snap = _uiState.value
            val filtered = withContext(Dispatchers.Default) {
                filterFoodEntries(
                    entries = snap.foodEntries,
                    query = snap.searchQuery,
                    singleDate = snap.selectedDateFilter,
                    fromDate = snap.fromDateFilter,
                    toDate = snap.toDateFilter,
                    meal = snap.selectedMeal,
                    highProt = snap.onlyHighProtein
                )
            }
            _uiState.update {
                it.copy(
                    filteredFoods = filtered,
                    isFiltering = false
                )
            }
        }
    }
}

private data class NutritionComputedResult(
    val todayCalories: Double,
    val todayEntriesCount: Int,
    val todayCarb: Double,
    val todayProtein: Double,
    val todayFat: Double,
    val totalCarb: Double,
    val totalProtein: Double,
    val totalFat: Double,
    val filteredFoods: List<FoodEntryDto>
)
