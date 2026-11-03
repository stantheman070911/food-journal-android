package com.stanley.foodjournal.ui.meal

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
import com.stanley.foodjournal.edits.UndoableEdits
import com.stanley.foodjournal.logic.cleanPriceInput
import com.stanley.foodjournal.logic.suggestNames
import com.stanley.foodjournal.photos.PhotoStore
import com.stanley.foodjournal.ui.navigation.MealRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class MealUiState(
    val isNew: Boolean,
    val draft: MealDraft,
    val photoFile: File?,
    val nameSuggestions: List<String>,
    /** A new meal always shows Save. A saved meal shows it once something has changed. */
    val showSave: Boolean,
)

class MealViewModel(
    private val savedState: SavedStateHandle,
    private val mealDao: MealDao,
    private val edits: UndoableEdits,
    private val photos: PhotoStore,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val mealId: Long? = savedState.toRoute<MealRoute>().mealId
    private val isNew = mealId == null
    private var original by mutableStateOf<Meal?>(null)
    private var recentNames by mutableStateOf<List<String>>(emptyList())

    // Snapshot state rather than a flow, so text fields see every keystroke at once and typing
    // never races the keyboard. Each change is copied to the saved state for process death.
    private var draft by mutableStateOf(
        savedState.get<MealDraft>(DRAFT) ?: if (isNew) MealDraft(eatenAt = now()) else null,
    )
    private val _finished = MutableStateFlow(false)

    /** Null while a saved meal is still loading. Read it from composition. */
    val uiState: MealUiState?
        get() = draft?.let { current ->
            val saved = original
            MealUiState(
                isNew = isNew,
                draft = current,
                photoFile = current.photo?.let { photoFile(it, current.photoIsPending) },
                nameSuggestions = suggestNames(recentNames, current.name),
                showSave = isNew || (saved != null && current.hasChangesFrom(saved)),
            )
        }

    /** Set when the screen should close. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    init {
        viewModelScope.launch { recentNames = mealDao.recentNames(RECENT_NAME_COUNT) }
        if (mealId != null) {
            viewModelScope.launch {
                val meal = mealDao.get(mealId)
                if (meal == null) {
                    _finished.value = true
                } else {
                    original = meal
                    if (draft == null) replaceDraft(MealDraft.from(meal))
                }
            }
        }
    }

    fun onPriceChange(typed: String) = update { it.copy(priceText = cleanPriceInput(typed)) }

    fun onNameChange(name: String) = update { it.copy(name = name) }

    fun onNoteChange(note: String) = update { it.copy(note = note) }

    fun onEatenAtChange(eatenAt: LocalDateTime) = update { it.copy(eatenAt = eatenAt) }

    fun onToggleFavorite() = update { it.copy(isFavorite = !it.isFavorite) }

    fun save() {
        val current = draft ?: return
        if (!current.canSave) return
        persist(current)
        _finished.value = true
    }

    /**
     * Back discards a new meal. A saved meal keeps its edits if they still make a valid meal;
     * otherwise the last saved version stays.
     */
    fun onBack() {
        val current = draft
        val saved = original
        when {
            current == null -> Unit
            isNew -> discard(current)
            saved != null && current.hasChangesFrom(saved) && current.canSave -> persist(current)
            else -> discard(current)
        }
        _finished.value = true
    }

    /** Deletes a saved meal, with undo. A new meal is simply discarded. */
    fun delete() {
        val current = draft
        val saved = original
        if (current != null) discard(current)
        if (saved != null) edits.deleteMeal(saved)
        _finished.value = true
    }

    private fun update(change: (MealDraft) -> MealDraft) {
        val current = draft ?: return
        replaceDraft(change(current))
    }

    private fun replaceDraft(new: MealDraft) {
        draft = new
        savedState[DRAFT] = new
    }

    private fun persist(draft: MealDraft) {
        val replacedPhoto = original?.photo?.takeIf { it != draft.photo }
        appScope.launch {
            if (draft.photo != null && draft.photoIsPending) photos.commit(draft.photo)
            mealDao.save(draft.toMeal(mealId ?: 0))
            replacedPhoto?.let { photos.delete(it) }
            edits.removeEmptyPlaces()
        }
    }

    /** Drops what the draft added but never saved: a pending photo. */
    private fun discard(draft: MealDraft) {
        if (draft.photo != null && draft.photoIsPending) {
            appScope.launch { photos.discardPending(draft.photo) }
        }
    }

    private fun photoFile(name: String, isPending: Boolean): File =
        if (isPending) photos.pendingPhotoFile(name) else photos.photoFile(name)

    companion object {
        private const val DRAFT = "draft"
        private const val RECENT_NAME_COUNT = 200

        private fun now() = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer
                MealViewModel(
                    savedState = createSavedStateHandle(),
                    mealDao = container.mealDao,
                    edits = container.edits,
                    photos = container.photoStore,
                    appScope = container.appScope,
                )
            }
        }
    }
}
