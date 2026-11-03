package com.stanley.foodjournal.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.stanley.foodjournal.appContainer
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.MealDao
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.data.PlaceDao
import com.stanley.foodjournal.logic.SearchFilter
import com.stanley.foodjournal.logic.formatDateRange
import com.stanley.foodjournal.logic.formatPrice
import com.stanley.foodjournal.logic.formatResultDate
import com.stanley.foodjournal.logic.searchMeals
import com.stanley.foodjournal.photos.PhotoStore
import com.stanley.foodjournal.ui.navigation.SearchRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.LocalDate

/** One result: a meal's thumbnail, name, "Tue, Nov 3 · 林家乾麵", and price. */
data class SearchResult(
    val mealId: Long,
    val thumb: File?,
    val photo: File?,
    val name: String?,
    val detail: String,
    val price: String,
    val isFavorite: Boolean,
)

data class SearchUiState(
    val isLoading: Boolean = true,
    /** Whether any meal exists at all; without one, the tab shows its empty state. */
    val hasMeals: Boolean = false,
    /** The place chip, or null without one. */
    val placeName: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    /** The dates pill's label once dates are set: "Oct 1 – Nov 4". */
    val dates: String? = null,
    val favoritesOnly: Boolean = false,
    val count: Int = 0,
    val total: String = "0",
    val results: List<SearchResult> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && !hasMeals
}

/**
 * Search over every meal, with a place chip, dates, and favorites. Each part is kept in the saved
 * state, so a search survives switching tabs. A [SearchRoute] with a place or dates starts with
 * just them.
 */
class SearchViewModel(
    private val savedState: SavedStateHandle,
    route: SearchRoute,
    mealDao: MealDao,
    placeDao: PlaceDao,
    private val photos: PhotoStore,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    /**
     * The typed text. Snapshot state rather than a flow, so the field sees every keystroke at
     * once, as on the Meal screen. Read it from composition.
     */
    var query by mutableStateOf(savedState.get<String>(TEXT).orEmpty())
        private set

    /** The same text, for filtering. */
    private val text = MutableStateFlow(query)

    private val placeId = savedState.getStateFlow(PLACE, route.placeId)

    /** The first and last day as epoch days, kept as one value so they always change together. */
    private val days = savedState.getStateFlow(
        DAYS,
        route.fromDay?.let { from -> longArrayOf(from, route.toDay ?: from) },
    )
    private val favoritesOnly = savedState.getStateFlow(FAVORITES, false)

    private val filter = combine(
        text,
        placeId,
        days,
        favoritesOnly,
    ) { text, place, days, favorites ->
        SearchFilter(
            text = text,
            placeId = place,
            from = days?.let { LocalDate.ofEpochDay(it[0]) },
            to = days?.let { LocalDate.ofEpochDay(it[1]) },
            favoritesOnly = favorites,
        )
    }

    val uiState: StateFlow<SearchUiState> =
        combine(mealDao.observeAll(), placeDao.observeAll(), filter) { meals, places, filter ->
            uiState(meals, places, filter)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(text: String) {
        query = text
        this.text.value = text
        savedState[TEXT] = text
    }

    fun clearPlace() {
        savedState[PLACE] = null
    }

    /** Sets the dates, both included, or clears them with nulls. */
    fun setDates(from: LocalDate?, to: LocalDate?) {
        savedState[DAYS] = from?.let { longArrayOf(it.toEpochDay(), (to ?: it).toEpochDay()) }
    }

    fun toggleFavorites() {
        savedState[FAVORITES] = !favoritesOnly.value
    }

    private fun uiState(meals: List<Meal>, places: List<Place>, filter: SearchFilter): SearchUiState {
        val names = places.associate { it.id to it.name }
        // A place merged away or deleted leaves no chip, and filters nothing out.
        val placeName = filter.placeId?.let(names::get)
        val effective = if (placeName == null) filter.copy(placeId = null) else filter
        val found = searchMeals(meals, names, effective)
        val now = today()
        return SearchUiState(
            isLoading = false,
            hasMeals = meals.isNotEmpty(),
            placeName = placeName,
            from = filter.from,
            to = filter.to,
            dates = filter.from?.let { from -> formatDateRange(from, filter.to ?: from, now) },
            favoritesOnly = filter.favoritesOnly,
            count = found.size,
            total = formatPrice(found.sumOf { it.price }),
            results = found.map { meal ->
                SearchResult(
                    mealId = meal.id,
                    thumb = meal.photo?.let(photos::thumbFile),
                    photo = meal.photo?.let(photos::photoFile),
                    name = meal.name?.takeIf { it.isNotBlank() },
                    detail = listOfNotNull(
                        formatResultDate(meal.eatenAt.toLocalDate(), now),
                        meal.placeId?.let(names::get),
                    ).joinToString(" · "),
                    price = formatPrice(meal.price),
                    isFavorite = meal.isFavorite,
                )
            },
        )
    }

    companion object {
        private const val TEXT = "text"
        private const val PLACE = "place"
        private const val DAYS = "days"
        private const val FAVORITES = "favorites"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer
                val savedState = createSavedStateHandle()
                SearchViewModel(
                    savedState = savedState,
                    route = savedState.toRoute<SearchRoute>(),
                    mealDao = container.mealDao,
                    placeDao = container.placeDao,
                    photos = container.photoStore,
                )
            }
        }
    }
}
