package com.stanley.foodjournal.ui.meal

import android.net.Uri
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
import com.stanley.foodjournal.photos.PhotoProcessor
import com.stanley.foodjournal.photos.PhotoStore
import com.stanley.foodjournal.ui.navigation.MealRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** A new meal starts at the camera. A photo, a gallery pick, or Skip leads to the form. */
enum class MealStage { Camera, Form }

data class MealUiState(
    val isNew: Boolean,
    val stage: MealStage,
    val draft: MealDraft,
    val photoFile: File?,
    /** The photo is still being resized; its file isn't ready to show yet. */
    val photoProcessing: Boolean,
    val nameSuggestions: List<String>,
    /** A new meal always shows Save. A saved meal shows it once something has changed. */
    val showSave: Boolean,
)

class MealViewModel(
    private val savedState: SavedStateHandle,
    private val mealDao: MealDao,
    private val edits: UndoableEdits,
    private val photos: PhotoStore,
    private val processor: PhotoProcessor,
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
    private var stage by mutableStateOf(
        savedState.get<MealStage>(STAGE) ?: if (isNew) MealStage.Camera else MealStage.Form,
    )

    /** Whether the camera was opened from the form to replace the photo, so Back returns there. */
    private var replacingPhoto: Boolean = savedState.get<Boolean>(REPLACING_PHOTO) ?: false
    private var photoProcessing by mutableStateOf(false)
    private var photoJob: Job? = null
    private val _finished = MutableStateFlow(false)

    /** Null while a saved meal is still loading. Read it from composition. */
    val uiState: MealUiState?
        get() = draft?.let { current ->
            val saved = original
            MealUiState(
                isNew = isNew,
                stage = stage,
                draft = current,
                photoFile = current.photo?.let { photoFile(it, current.photoIsPending) },
                photoProcessing = photoProcessing,
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

    /** A name and file for the next camera capture. */
    fun newCaptureFile(): Pair<String, File> {
        photos.ensureFolders()
        val name = photos.newName()
        return name to photos.captureFile(name)
    }

    fun onCaptured(name: String) {
        attachPhoto(name)
        processPhoto(name) { processor.fromCapture(name) }
    }

    /**
     * A photo picked from the gallery. For a new meal, its metadata sets the time, and its
     * location, if shared, is the only one used for the place.
     */
    fun onPicked(uri: Uri) {
        val name = photos.newName()
        attachPhoto(name)
        processPhoto(name) {
            val metadata = processor.fromPicker(uri, name) ?: return@processPhoto false
            if (isNew) {
                withContext(Dispatchers.Main) {
                    update {
                        it.copy(
                            eatenAt = metadata.takenAt ?: it.eatenAt,
                            fromGallery = true,
                            photoLatitude = metadata.location?.latitude,
                            photoLongitude = metadata.location?.longitude,
                        )
                    }
                }
            }
            true
        }
    }

    fun onSkipPhoto() = showStage(MealStage.Form)

    fun onRetakePhoto() {
        replacingPhoto = true
        savedState[REPLACING_PHOTO] = true
        showStage(MealStage.Camera)
    }

    /** Back from the camera: to the form when replacing a photo, otherwise out, discarding. */
    fun onCameraBack() {
        if (replacingPhoto) {
            replacingPhoto = false
            savedState[REPLACING_PHOTO] = false
            showStage(MealStage.Form)
        } else {
            onBack()
        }
    }

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

    private fun attachPhoto(name: String) {
        val previous = draft
        if (previous?.photo != null && previous.photoIsPending) {
            val old = previous.photo
            appScope.launch { photos.discardPending(old) }
        }
        update { it.copy(photo = name, photoIsPending = true) }
        replacingPhoto = false
        savedState[REPLACING_PHOTO] = false
        showStage(MealStage.Form)
    }

    /** Resizes a new photo in the background. An unreadable photo is dropped from the draft. */
    private fun processPhoto(name: String, work: suspend () -> Boolean) {
        photoProcessing = true
        photoJob = appScope.launch {
            val ok = work()
            withContext(Dispatchers.Main) {
                if (!ok) update { if (it.photo == name) it.copy(photo = null, photoIsPending = false) else it }
                // A newer photo may have replaced this one; its own job clears the flag.
                val current = draft?.photo
                if (current == name || current == null) photoProcessing = false
            }
        }
    }

    private fun showStage(next: MealStage) {
        stage = next
        savedState[STAGE] = next
    }

    private fun update(change: (MealDraft) -> MealDraft) {
        val current = draft ?: return
        replaceDraft(change(current))
    }

    private fun replaceDraft(new: MealDraft) {
        draft = new
        savedState[DRAFT] = new
    }

    // Runs in the app scope, so leaving the screen never cuts a save short. A photo still being
    // resized is waited for first; one that turned out unreadable is left off the meal.
    private fun persist(draft: MealDraft) {
        val replacedPhoto = original?.photo?.takeIf { it != draft.photo }
        val pendingWork = photoJob
        appScope.launch {
            pendingWork?.join()
            var toSave = draft
            if (draft.photo != null && draft.photoIsPending) {
                if (photos.pendingPhotoFile(draft.photo).exists()) {
                    photos.commit(draft.photo)
                } else {
                    toSave = draft.copy(photo = null, photoIsPending = false)
                }
            }
            if (!toSave.canSave) return@launch
            mealDao.save(toSave.toMeal(mealId ?: 0))
            replacedPhoto?.let { photos.delete(it) }
            edits.removeEmptyPlaces()
        }
    }

    /** Drops what the draft added but never saved: a pending photo. */
    private fun discard(draft: MealDraft) {
        if (draft.photo != null && draft.photoIsPending) {
            val pendingWork = photoJob
            appScope.launch {
                pendingWork?.join()
                photos.discardPending(draft.photo)
            }
        }
    }

    private fun photoFile(name: String, isPending: Boolean): File =
        if (isPending) photos.pendingPhotoFile(name) else photos.photoFile(name)

    companion object {
        private const val DRAFT = "draft"
        private const val STAGE = "stage"
        private const val REPLACING_PHOTO = "replacingPhoto"
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
                    processor = container.photoProcessor,
                    appScope = container.appScope,
                )
            }
        }
    }
}
