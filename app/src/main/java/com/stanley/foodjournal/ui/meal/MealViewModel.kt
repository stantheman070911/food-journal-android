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
import com.stanley.foodjournal.edits.PendingEdit
import com.stanley.foodjournal.edits.RenameResult
import com.stanley.foodjournal.edits.UndoableEdits
import com.stanley.foodjournal.logic.LocationFix
import com.stanley.foodjournal.logic.PlacePickerContent
import com.stanley.foodjournal.logic.cleanPriceInput
import com.stanley.foodjournal.logic.mealLocation
import com.stanley.foodjournal.logic.normalizePlaceName
import com.stanley.foodjournal.logic.placePickerContent
import com.stanley.foodjournal.logic.prefillPlace
import com.stanley.foodjournal.logic.suggestNames
import com.stanley.foodjournal.photos.PhotoProcessing
import com.stanley.foodjournal.photos.PhotoStore
import com.stanley.foodjournal.ui.navigation.MealRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.Serializable
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
    /** The last photo taken or picked couldn't be read, so the one before it is back. */
    val photoFailed: Boolean,
    /** Save was tapped while the photo was still being resized; it saves once that is done. */
    val savePending: Boolean,
    /** The screen is closing. Its buttons do nothing from here on. */
    val closing: Boolean,
    val nameSuggestions: List<String>,
    /** The chosen place's name, or a new one typed in the picker. Null without a place. */
    val placeName: String?,
    /** A new meal always shows Save. A saved meal shows it once something has changed. */
    val showSave: Boolean,
)

/**
 * A photo the draft can fall back to if a newer one can't be read, with where it came from, so a
 * failed capture after a gallery pick still places the meal by the gallery photo.
 */
data class PhotoRef(
    val name: String?,
    val isPending: Boolean,
    val fromGallery: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
) : Serializable

class MealViewModel(
    private val savedState: SavedStateHandle,
    private val mealId: Long?,
    private val mealDao: MealDao,
    private val placeDao: PlaceDao,
    private val edits: UndoableEdits,
    private val photos: PhotoStore,
    private val processor: PhotoProcessing,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val isNew = mealId == null
    private var original by mutableStateOf<Meal?>(null)
    private var recentNames by mutableStateOf<List<String>>(emptyList())
    private var places by mutableStateOf<List<PlaceSummary>>(emptyList())

    /** The last merge that moved the draft to its survivor, so an undo can move it back. */
    private var mergedAway: Pair<Long, Long>? = null

    /** The draft's place, deleted from the picker, so an undo can put it back on the draft. */
    private var deletedPlace: Long? = null

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

    /** The newest photo still being resized, if any. */
    private var processingPhoto by mutableStateOf<String?>(null)
    private var photoJob: Job? = null

    /** The last photo that was ready, restored if a newer one can't be read. */
    private var fallback: PhotoRef? = savedState.get<PhotoRef>(FALLBACK)
    private var photoFailed by mutableStateOf(false)
    private var savePending by mutableStateOf(false)
    private val _finished = MutableStateFlow(false)
    private val closing: Boolean get() = _finished.value

    /** Null while a saved meal is still loading. Read it from composition. */
    val uiState: MealUiState?
        get() = draft?.let { current ->
            val saved = original
            MealUiState(
                isNew = isNew,
                stage = stage,
                draft = current,
                photoFile = current.photo?.let { photoFile(it, current.photoIsPending) },
                photoProcessing = processingPhoto != null && processingPhoto == current.photo,
                photoFailed = photoFailed,
                savePending = savePending,
                closing = closing,
                nameSuggestions = suggestNames(recentNames, current.name),
                placeName = current.newPlaceName
                    ?: current.placeId?.let { id -> places.firstOrNull { it.place.id == id }?.place?.name },
                showSave = isNew || (saved != null && current.hasChangesFrom(saved)),
            )
        }

    /** Set when the screen should close. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    /**
     * The place "Meals here" opens in Search: the meal's own, once the meal and the place are both
     * saved. A new meal has none, since leaving would discard it. Nor does a place picked in edits
     * that Back would drop, since the meal would stay at its saved place. Read it from composition.
     */
    val mealsHerePlaceId: Long?
        get() {
            val current = draft ?: return null
            val saved = original ?: return null
            if (isNew || current.newPlaceName != null) return null
            val kept = !current.hasChangesFrom(saved) || current.canSave || current.placeId == saved.placeId
            return current.placeId?.takeIf { id -> kept && places.any { it.place.id == id } }
        }

    init {
        viewModelScope.launch { recentNames = mealDao.recentNames(RECENT_NAME_COUNT) }
        viewModelScope.launch {
            placeDao.observeSummaries().collect {
                places = it
                prefill()
            }
        }
        viewModelScope.launch { edits.undone.collect { followUndo(it) } }
        if (mealId != null) {
            viewModelScope.launch {
                val meal = mealDao.get(mealId)
                if (meal == null) {
                    _finished.value = true
                } else {
                    original = meal
                    if (draft == null) {
                        replaceDraft(MealDraft.from(meal))
                        setFallback(PhotoRef(meal.photo, isPending = false))
                    }
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
     * Deletes a place from the picker, with undo. Its meals stay, without a place. A draft at
     * that place loses it too, and gets it back if the deletion is undone.
     */
    fun deletePlace(place: Place) {
        if (draft?.placeId == place.id) {
            update { it.copy(placeId = null, placeTouched = true) }
            deletedPlace = place.id
        }
        edits.deletePlace(place)
    }

    /** A name and file for the next camera capture. */
    fun newCaptureFile(): Pair<String, File> {
        photos.ensureFolders()
        val name = photos.newName()
        return name to photos.captureFile(name)
    }

    fun onCaptured(name: String) {
        // A capture that lands after the screen closed belongs to no meal.
        if (closing) {
            appScope.launch { photos.discardPending(name) }
            return
        }
        // A photo taken in the app carries no location, even if it replaces a gallery photo.
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
        if (closing) return
        val name = photos.newName()
        attachPhoto(name)
        processPhoto(name) {
            val metadata = processor.readMetadata(uri)
            if (!processor.fromPicker(uri, name)) return@processPhoto false
            if (isNew) {
                withContext(Dispatchers.Main) {
                    if (draft?.photo != name) return@withContext
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

    /**
     * Saves and closes. Runs once: a second tap, or the keyboard's ✓ followed by a tap, does
     * nothing. While the photo is still being resized, the screen waits for it, so a photo that
     * turns out unreadable leaves the meal open with a message instead of losing it.
     */
    fun save() {
        if (closing) return
        val current = draft ?: return
        if (!current.canSave) return
        if (processingPhoto != null && processingPhoto == current.photo) {
            savePending = true
            return
        }
        savePending = false
        persist(current)
        _finished.value = true
    }

    /**
     * Back discards a new meal. A saved meal keeps its edits if they still make a valid meal;
     * otherwise the last saved version stays.
     */
    fun onBack() {
        if (closing) return
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

    /**
     * "Meals here": closes the screen as Back does, keeping edits that still make a valid meal,
     * and returns the place to open in Search. Null when there is none, or the screen is closing.
     */
    fun leaveForPlace(): Long? {
        if (closing) return null
        val placeId = mealsHerePlaceId ?: return null
        onBack()
        return placeId
    }

    /** Deletes a saved meal, with undo. A new meal is simply discarded. */
    fun delete() {
        if (closing) return
        val current = draft
        val saved = original
        if (current != null) discard(current)
        if (saved != null) edits.deleteMeal(saved)
        _finished.value = true
    }

    /**
     * Shows a new photo while it is resized. The previous photo stays on disk as the fallback
     * until the new one is ready, unless it was itself still being resized: then it is dropped
     * when its own resizing ends.
     */
    private fun attachPhoto(name: String) {
        val previous = draft
        if (previous != null && previous.photo != processingPhoto) {
            setFallback(
                PhotoRef(
                    name = previous.photo,
                    isPending = previous.photoIsPending,
                    fromGallery = previous.fromGallery,
                    latitude = previous.photoLatitude,
                    longitude = previous.photoLongitude,
                ),
            )
        }
        photoFailed = false
        update { it.copy(photo = name, photoIsPending = true) }
        replacingPhoto = false
        savedState[REPLACING_PHOTO] = false
        showStage(MealStage.Form)
    }

    /** Resizes a new photo in the background, then settles it with [onProcessed]. */
    private fun processPhoto(name: String, work: suspend () -> Boolean) {
        processingPhoto = name
        photoJob = appScope.launch {
            val ok = try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            withContext(Dispatchers.Main) { onProcessed(name, ok) }
        }
    }

    /**
     * A photo that was replaced while it was resized is dropped. The current one either becomes
     * the new fallback, retiring the old one, or, if unreadable, gives way to the fallback with
     * a message. A Save that was waiting goes ahead only if the photo is good.
     */
    private fun onProcessed(name: String, ok: Boolean) {
        if (draft?.photo != name) {
            if (ok) appScope.launch { photos.discardPending(name) }
            return
        }
        processingPhoto = null
        if (ok) {
            val retired = fallback?.takeIf { it.isPending }?.name?.takeIf { it != name }
            if (retired != null) appScope.launch { photos.discardPending(retired) }
            setFallback(PhotoRef(name, isPending = true, draft?.fromGallery ?: false, draft?.photoLatitude, draft?.photoLongitude))
            if (savePending) save()
        } else {
            val restore = fallback
            update {
                it.copy(
                    photo = restore?.name,
                    photoIsPending = restore?.isPending ?: false,
                    fromGallery = restore?.fromGallery ?: false,
                    photoLatitude = restore?.latitude,
                    photoLongitude = restore?.longitude,
                )
            }
            photoFailed = true
            savePending = false
            prefill()
        }
    }

    private fun setFallback(ref: PhotoRef?) {
        fallback = ref
        savedState[FALLBACK] = ref
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
    // resized (a saved meal closed with Back) is waited for first. Only then is it known which
    // photo the meal keeps: an unreadable replacement gives way to the original, which is kept.
    private fun persist(draft: MealDraft) {
        val pendingWork = photoJob
        appScope.launch {
            pendingWork?.join()
            var toSave = withContext(Dispatchers.Main) { this@MealViewModel.draft } ?: draft
            val location = mealLocation(isNew, toSave.fromGallery, toSave.photoLocation)
            val photo = toSave.photo
            if (photo != null && toSave.photoIsPending) {
                if (photos.pendingPhotoFile(photo).exists()) {
                    photos.commit(photo)
                } else {
                    toSave = toSave.copy(photo = null, photoIsPending = false)
                }
            }
            if (!toSave.canSave) return@launch
            mealDao.save(toSave.toMeal(mealId ?: 0), newPlace = toSave.newPlace(location))
            original?.photo?.takeIf { it != toSave.photo }?.let { photos.delete(it) }
            edits.removeEmptyPlaces()
        }
    }

    /** Drops what the draft added but never saved: its pending photo and a pending fallback. */
    private fun discard(draft: MealDraft) {
        val pendingWork = photoJob
        val names = listOfNotNull(
            draft.photo?.takeIf { draft.photoIsPending },
            fallback?.takeIf { it.isPending }?.name,
        ).distinct()
        if (names.isEmpty()) return
        appScope.launch {
            pendingWork?.join()
            names.forEach { photos.discardPending(it) }
        }
    }

    private fun location(draft: MealDraft): LocationFix? =
        mealLocation(isNew, draft.fromGallery, draft.photoLocation)

    /**
     * Fills the place of a new gallery meal from its photo's location, until a place is chosen
     * by hand. It runs again whenever the places or the photo change.
     */
    private fun prefill() {
        val current = draft ?: return
        if (!isNew || current.placeTouched) return
        val placeId = prefillPlace(places.map { it.place }, location(current))?.id
        if (placeId != current.placeId) update { it.copy(placeId = placeId) }
    }

    /**
     * Follows an undo of the draft's own edit. An undone merge moves a draft that went to the
     * survivor back to the merged-away place; an undone deletion gives the place back to a draft
     * that has no other. Either applies only if the place itself came back: when its name was
     * taken meanwhile, its meals joined that place instead, and the draft stays as it is.
     */
    private suspend fun followUndo(edit: PendingEdit) {
        when (edit) {
            is PendingEdit.PlacesMerged -> {
                val (awayId, survivorId) = mergedAway ?: return
                if (edit.mergedAway.id != awayId) return
                mergedAway = null
                if (placeDao.get(awayId) == null) return
                if (draft?.placeId == survivorId) update { it.copy(placeId = awayId) }
            }
            is PendingEdit.PlaceDeleted -> {
                val id = deletedPlace ?: return
                if (edit.place.id != id) return
                deletedPlace = null
                if (placeDao.get(id) == null) return
                if (draft?.placeId == null && draft?.newPlaceName == null) update { it.copy(placeId = id) }
            }
            is PendingEdit.MealDeleted -> Unit
        }
    }

    private fun photoFile(name: String, isPending: Boolean): File =
        if (isPending) photos.pendingPhotoFile(name) else photos.photoFile(name)

    companion object {
        private const val DRAFT = "draft"
        private const val STAGE = "stage"
        private const val REPLACING_PHOTO = "replacingPhoto"
        private const val FALLBACK = "fallbackPhoto"
        private const val RECENT_NAME_COUNT = 200

        private fun now() = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer
                val savedState = createSavedStateHandle()
                MealViewModel(
                    savedState = savedState,
                    mealId = savedState.toRoute<MealRoute>().mealId,
                    mealDao = container.mealDao,
                    placeDao = container.placeDao,
                    edits = container.edits,
                    photos = container.photoStore,
                    processor = container.photoProcessor,
                    appScope = container.appScope,
                )
            }
        }
    }
}
