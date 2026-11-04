package com.stanley.foodjournal.ui.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stanley.foodjournal.appContainer
import com.stanley.foodjournal.data.MealDao
import com.stanley.foodjournal.data.PlaceDao
import com.stanley.foodjournal.logic.District
import com.stanley.foodjournal.logic.DistrictMap
import com.stanley.foodjournal.logic.MealMap
import com.stanley.foodjournal.logic.formatResultDate
import com.stanley.foodjournal.logic.mealMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** A district tapped on the map, with how many meals were eaten in it. */
data class SelectedDistrict(val district: District, val meals: Int)

data class MapUiState(
    val isLoading: Boolean = true,
    /** Taiwan's districts, or null if the bundled file can't be read. */
    val map: DistrictMap? = null,
    val meals: MealMap = MealMap(pins = emptyList(), outsideTaiwan = 0, mealsByDistrict = emptyMap()),
    val selected: SelectedDistrict? = null,
) {
    /** No meal has a position yet, or there is no map to put one on. */
    val isEmpty: Boolean get() = !isLoading && (map == null || meals.isEmpty)
}

private data class Content(val map: DistrictMap?, val meals: MealMap)

/**
 * Taiwan with its districts and a pin for each place where meals were eaten. The selected district
 * is kept in the saved state, so it survives switching tabs.
 */
class MapViewModel(
    private val savedState: SavedStateHandle,
    mealDao: MealDao,
    placeDao: PlaceDao,
    districtMap: suspend () -> DistrictMap?,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val selectedCode = savedState.getStateFlow<String?>(SELECTED, null)

    private val content = combine(
        mealDao.observeAll(),
        placeDao.observeAll(),
        flow { emit(districtMap()) },
    ) { meals, places, map ->
        if (map == null) {
            Content(null, MealMap(emptyList(), 0, emptyMap()))
        } else {
            val now = today()
            Content(
                map,
                mealMap(meals, places, { latitude, longitude -> map.districtContaining(latitude, longitude)?.code }) { meal ->
                    meal.name?.takeIf { it.isNotBlank() } ?: formatResultDate(meal.eatenAt.toLocalDate(), now)
                },
            )
        }
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<MapUiState> = combine(content, selectedCode) { content, code ->
        MapUiState(
            isLoading = false,
            map = content.map,
            meals = content.meals,
            selected = code?.let { content.map?.district(it) }?.let { SelectedDistrict(it, content.meals.mealsByDistrict[it.code] ?: 0) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    /**
     * A tap on the map away from any pin, at a point. It selects the district there; a second tap
     * on the selected district returns its code to open in Search, if it has meals. A tap outside
     * Taiwan clears the selection.
     */
    fun onTap(latitude: Double, longitude: Double): String? {
        val state = uiState.value
        val district = state.map?.districtContaining(latitude, longitude)
        if (district == null) {
            savedState[SELECTED] = null
            return null
        }
        if (selectedCode.value == district.code) return openSelected()
        savedState[SELECTED] = district.code
        return null
    }

    /** The selected district's code, to open in Search, or null when it has no meals to show. */
    fun openSelected(): String? {
        val code = selectedCode.value ?: return null
        return code.takeIf { (uiState.value.meals.mealsByDistrict[it] ?: 0) > 0 }
    }

    companion object {
        private const val SELECTED = "selectedDistrict"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer
                MapViewModel(createSavedStateHandle(), container.mealDao, container.placeDao, container::districtMap)
            }
        }
    }
}
