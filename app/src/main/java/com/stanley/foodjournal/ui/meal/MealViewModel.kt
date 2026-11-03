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
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.data.PlaceDao
import com.stanley.foodjournal.data.PlaceSummary
import com.stanley.foodjournal.edits.RenameResult
import com.stanley.foodjournal.edits.UndoableEdits
import com.stanley.foodjournal.location.LocationTracker
import com.stanley.foodjournal.logic.LocationFix
import com.stanley.foodjournal.logic.PermissionRequest
import com.stanley.foodjournal.logic.PlacePickerContent
import com.stanley.foodjournal.logic.cleanPriceInput
import com.stanley.foodjournal.logic.mealLocation
import com.stanley.foodjournal.logic.normalizePlaceName
import com.stanley.foodjournal.logic.permissionsToRequest
import com.stanley.foodjournal.logic.placePickerContent
import com.stanley.foodjournal.logic.prefillPlace
import com.stanley.foodjournal.logic.suggestNames
import com.stanley.foodjournal.photos.PhotoProcessor
import com.stanley.foodjournal.photos.PhotoStore
import com.stanley.foodjournal.settings.AppSettings
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
    /** The chosen place's name, or a new one typed in the picker. Null without a place. */
    val placeName: String?,
    /** A new meal always shows Save. A saved meal shows it once something has changed. */
    val showSave: Boolean,
)

class MealViewModel(
    private val savedState: SavedStateHandle,
    private val mealDao: MealDao,
    private val placeDao: PlaceDao,
    private val edits: UndoableEdits,
    private val photos: PhotoStore,
    private val processor: PhotoProcessor,
    private val settings: AppSettings,
    private val locationTracker: LocationTracker,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val mealId: Long? = savedState.toRoute<MealRoute>().mealId
    private val isNew = mealId == null
    private var original by mutableStateOf<Meal?>(null)
    private var recentNames by mutableStateOf<List<String>>(emptyList())
    private var places by mutableStateOf<List<PlaceSummary>>(emptyList())
    private var deviceFix by mutableStateOf<LocationFix?>(null)

    /** The last merge that moved the draft to its survivor, so an undo can move it back. */
    private var mergedAway: Pair<Long, Long>? = null

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
                placeName = current.newPlaceName
                    ?: current.placeId?.let { id -> places.firstOrNull { it.place.id == id }?.place?.name },
                showSave = isNew || (saved != null && current.hasChangesFrom(saved)),
            )
        }

    /** Set when the screen should close. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    init {
        viewModelScope.launch { recentNames = mealDao.recentNames(RECENT_NAME_COUNT) }
        viewModelScope.launch {
            placeDao.observeSummaries().collect {
                places = it
                followUndoneMerge()
                prefill()
            }
        }
        if (isNew) {
            viewModelScope.launch {
                locationTracker.fix.collect {
                    deviceFix = it
                    prefill()
                }
            }
        }
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

    /** A saved place picked in the picker, or null for "No place". */
    fun onPlaceSelected(placeId: Long?) =
        update { it.copy(placeId = placeId, newPlaceName = null, placeTouched = true) }

    /** A new place typed in the picker. It is created when the meal is saved. */
    fun onNewPlace(typed: String) {
        val name = normalizePlaceName(typed)
        if (name.isEmpty()) return
        update { it.copy(placeId = null, newPlaceName = name, placeTouched = true) }
    }

    /** The picker's rows for what has been typed. Read it from composition. */
    fun placePicker(query: String): PlacePickerContent {
        val current = draft
        return placePickerContent(
            places = places,
            query = query,
            location = current?.let { location(it) },
            selectedPlaceId = current?.placeId,
            newPlaceName = current?.newPlaceName,
        )
    }

    /**
     * Renames a place from the picker. Renaming it to another place's name merges the two, and
     * a draft that pointed at the merged-away place moves to the survivor.
     */
    fun renamePlace(place: Place, typed: String) {
        // Renaming the draft's place keeps it, so a prefill can't clear it while it merges away.
        val isDraftPlace = draft?.placeId == place.id
        if (isDraftPlace) update { it.copy(placeTouched = true) }
        viewModelScope.launch {
            val result = edits.renamePlace(place, typed)
            if (result is RenameResult.Merged && isDraftPlace && draft?.placeId == place.id) {
                update { it.copy(placeId = result.survivor.id) }
                mergedAway = place.id to result.survivor.id
            }
        }
    }

    /**
     * What to ask for as the camera opens. Location is asked once ever, so asking marks it.
     * Call [startLocation] once the answer is in, or right away when nothing is asked.
     */
    suspend fun permissionsToRequest(cameraGranted: Boolean): PermissionRequest {
        val request = permissionsToRequest(
            cameraGranted = cameraGranted,
            locationGranted = locationTracker.hasPermission(),
            locationAsked = settings.locationAsked(),
        )
        if (request.location) settings.markLocationAsked()
        return request
    }

    /** Starts finding the phone's location for a new meal, if permission allows. */
    fun startLocation() {
        if (isNew) locationTracker.start()
    }

    /** A name and file for the next camera capture. */
    fun newCaptureFile(): Pair<String, File> {
        photos.ensureFolders()
        val name = photos.newName()
        return name to photos.captureFile(name)
    }

    fun onCaptured(name: String) {
        // A photo taken now is located by the phone, even if it replaces a gallery photo.
        if (isNew) update { it.copy(fromGallery = false, photoLatitude = null, photoLongitude = null) }
        attachPhoto(name)
        prefill()
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
                    prefill()
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
            // The photo's metadata may have arrived while it was being resized.
            var toSave = withContext(Dispatchers.Main) { this@MealViewModel.draft } ?: draft
            val location = mealLocation(isNew, toSave.fromGallery, toSave.photoLocation, locationTracker.fix.value)
            if (draft.photo != null && draft.photoIsPending) {
                if (photos.pendingPhotoFile(draft.photo).exists()) {
                    photos.commit(draft.photo)
                } else {
                    toSave = draft.copy(photo = null, photoIsPending = false)
                }
            }
            if (!toSave.canSave) return@launch
            mealDao.save(toSave.toMeal(mealId ?: 0), newPlace = toSave.newPlace(location))
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

    private fun location(draft: MealDraft): LocationFix? =
        mealLocation(isNew, draft.fromGallery, draft.photoLocation, deviceFix)

    /**
     * Fills the place of a new meal from where it is, until a place is chosen by hand. It runs
     * again whenever the places, the fix, or the photo change, so a late fix still fills it.
     */
    private fun prefill() {
        val current = draft ?: return
        if (!isNew || current.placeTouched) return
        val placeId = prefillPlace(places.map { it.place }, location(current))?.id
        if (placeId != current.placeId) update { it.copy(placeId = placeId) }
    }

    /** An undone merge brings the merged-away place back; a draft moved off it moves back too. */
    private fun followUndoneMerge() {
        val (awayId, survivorId) = mergedAway ?: return
        if (places.none { it.place.id == awayId }) return
        mergedAway = null
        if (draft?.placeId == survivorId) update { it.copy(placeId = awayId) }
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
                    placeDao = container.placeDao,
                    edits = container.edits,
                    photos = container.photoStore,
                    processor = container.photoProcessor,
                    settings = container.settings,
                    locationTracker = container.locationTracker,
                    appScope = container.appScope,
                )
            }
        }
    }
}
