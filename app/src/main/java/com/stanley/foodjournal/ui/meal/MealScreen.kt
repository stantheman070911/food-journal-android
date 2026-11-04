package com.stanley.foodjournal.ui.meal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.stanley.foodjournal.photos.PickPhotoWithLocation
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanley.foodjournal.R
import com.stanley.foodjournal.logic.MealSteps
import com.stanley.foodjournal.logic.formatMealTime
import com.stanley.foodjournal.logic.groupDigits
import com.stanley.foodjournal.logic.groupedToOriginal
import com.stanley.foodjournal.logic.originalToGrouped
import com.stanley.foodjournal.ui.components.PillButton
import com.stanley.foodjournal.ui.components.tileNameStyle
import com.stanley.foodjournal.ui.icons.FoodJournalIcons
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Adds, shows, and edits one meal. [onOpenPlace] opens a place's meals in Search, and [onStepTo]
 * opens another meal in this one's place.
 */
@Composable
fun MealScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlace: (placeId: Long) -> Unit = {},
    onStepTo: (mealId: Long) -> Unit = {},
    viewModel: MealViewModel = viewModel(factory = MealViewModel.Factory),
) {
    val uiState = viewModel.uiState
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onClose() }
    val stepTo by viewModel.stepTo.collectAsStateWithLifecycle()
    LaunchedEffect(stepTo) { stepTo?.let(onStepTo) }
    BackHandler(enabled = !finished) {
        if (uiState?.stage == MealStage.Camera) viewModel.onCameraBack() else viewModel.onBack()
    }
    val picker = rememberLauncherForActivityResult(PickPhotoWithLocation()) { uri ->
        if (uri != null) viewModel.onPicked(uri)
    }
    var pickingPlace by rememberSaveable { mutableStateOf(false) }

    // Opening the camera asks for it while it is off, and on the first Add for location too;
    // Android limits how often each shows. Finding the location starts then, so it is usually
    // ready by the time the form shows.
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(context.isGranted(Manifest.permission.CAMERA)) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCamera = context.isGranted(Manifest.permission.CAMERA)
        viewModel.onPermissionsChecked()
    }
    val stage = uiState?.stage
    LaunchedEffect(stage) {
        if (stage != MealStage.Camera) return@LaunchedEffect
        val request = viewModel.permissionsToRequest(hasCamera)
        if (request.isEmpty()) {
            viewModel.onPermissionsChecked()
        } else {
            // Nothing suspends between the two, so location is marked asked only once it is.
            viewModel.onPermissionsRequested(request)
            permissions.launch(request.toTypedArray())
        }
    }
    // Camera or location access may have been turned on in Settings.
    LifecycleResumeEffect(Unit) {
        hasCamera = context.isGranted(Manifest.permission.CAMERA)
        viewModel.onPermissionsChecked()
        onPauseOrDispose {}
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        uiState?.let { state ->
            AnimatedContent(
                targetState = state.stage,
                transitionSpec = { fadeIn(tween(STAGE_FADE_MILLIS)) togetherWith fadeOut(tween(STAGE_FADE_MILLIS)) },
                label = "stage",
            ) { stage ->
                when (stage) {
                    MealStage.Camera -> CameraStage(
                        hasCamera = hasCamera,
                        newCaptureFile = viewModel::newCaptureFile,
                        onCaptured = viewModel::onCaptured,
                        onPickFromGallery = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onSkip = viewModel::onSkipPhoto,
                        onClose = viewModel::onCameraBack,
                    )
                    MealStage.Form -> MealScreen(
                        uiState = state,
                        actions = MealActions(
                            onBack = viewModel::onBack,
                            onToggleFavorite = viewModel::onToggleFavorite,
                            onSaveLocationChange = viewModel::onSaveLocationChange,
                            onDelete = viewModel::delete,
                            onReplacePhoto = viewModel::onRetakePhoto,
                            onPriceChange = viewModel::onPriceChange,
                            onNameChange = viewModel::onNameChange,
                            onNoteChange = viewModel::onNoteChange,
                            onEatenAtChange = viewModel::onEatenAtChange,
                            onPickPlace = { pickingPlace = true },
                            onSave = viewModel::save,
                            onPreviousMeal = viewModel::stepToPrevious,
                            onNextMeal = viewModel::stepToNext,
                        ),
                    )
                }
            }
            if (pickingPlace && state.stage == MealStage.Form) {
                PlacePickerSheet(
                    content = viewModel::placePicker,
                    onSelect = viewModel::onPlaceSelected,
                    onAdd = viewModel::onNewPlace,
                    onRename = viewModel::renamePlace,
                    onDelete = viewModel::deletePlace,
                    onDismiss = { pickingPlace = false },
                    onMealsHere = if (viewModel.mealsHerePlaceId != null) {
                        { viewModel.leaveForPlace()?.let(onOpenPlace) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private fun Context.isGranted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private const val STAGE_FADE_MILLIS = 150

/** Everything the Meal screen can ask its ViewModel to do. */
class MealActions(
    val onBack: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onSaveLocationChange: (Boolean) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onReplacePhoto: () -> Unit = {},
    val onPriceChange: (String) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onEatenAtChange: (LocalDateTime) -> Unit = {},
    val onPickPlace: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onPreviousMeal: () -> Unit = {},
    val onNextMeal: () -> Unit = {},
)

@Composable
fun MealScreen(
    uiState: MealUiState,
    actions: MealActions,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val draft = uiState.draft
    val priceFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var editingTime by rememberSaveable { mutableStateOf(false) }

    // A new meal starts with the number pad up, ready for the price.
    if (uiState.isNew) {
        LaunchedEffect(Unit) { priceFocus.requestFocus() }
    }

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        MealTopBar(
            isFavorite = draft.isFavorite,
            enabled = !uiState.closing,
            onBack = actions.onBack,
            onToggleFavorite = actions.onToggleFavorite,
            onDelete = actions.onDelete,
            // A new meal's photo opens the camera itself.
            camera = if (uiState.isNew) null else CameraButton(hasPhoto = draft.photo != null, onClick = actions.onReplacePhoto),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (uiState.isNew) {
                PhotoArea(
                    photoFile = uiState.photoFile,
                    processing = uiState.photoProcessing,
                    onClick = actions.onReplacePhoto,
                )
            } else {
                // On a saved meal the time is mostly read, so it heads the screen.
                DateLine(text = formatMealTime(draft.eatenAt, today), enabled = !uiState.closing, onClick = { editingTime = true })
                WeekSegments(steps = uiState.steps)
                SteppingPhoto(
                    photoFile = uiState.photoFile,
                    processing = uiState.photoProcessing,
                    name = draft.name,
                    steps = uiState.steps,
                    onPrevious = actions.onPreviousMeal,
                    onNext = actions.onNextMeal,
                )
            }
            if (uiState.photoFailed) {
                Text(
                    text = stringResource(R.string.meal_photo_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
                )
            }
            PriceField(
                priceText = draft.priceText,
                onPriceChange = actions.onPriceChange,
                onDone = {
                    focusManager.clearFocus()
                    if (uiState.isNew && draft.canSave) actions.onSave()
                },
                focusRequester = priceFocus,
            )
            FieldGroup {
                NameField(
                    name = draft.name,
                    suggestions = uiState.nameSuggestions,
                    onNameChange = actions.onNameChange,
                )
                Divider()
                FieldRow(
                    label = stringResource(R.string.meal_place),
                    modifier = Modifier.clickable(onClick = actions.onPickPlace),
                ) {
                    Text(
                        text = uiState.placeName.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .alignByBaseline(),
                    )
                    uiState.placeDistrict?.let { district ->
                        // At a large font size, this still leaves the place's name some room.
                        Text(
                            text = district,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .widthIn(max = DistrictMaxWidth)
                                .alignByBaseline(),
                        )
                    }
                }
                Divider()
                uiState.locationRow?.let { row ->
                    SaveLocationRow(
                        title = stringResource(if (uiState.isNew) R.string.meal_save_location else R.string.meal_location),
                        row = row,
                        enabled = !uiState.closing,
                        onChange = actions.onSaveLocationChange,
                    )
                    Divider()
                }
                if (uiState.isNew) {
                    FieldRow(
                        label = stringResource(R.string.meal_time),
                        modifier = Modifier.clickable { editingTime = true },
                    ) {
                        Text(formatMealTime(draft.eatenAt, today), style = MaterialTheme.typography.bodyLarge)
                    }
                    Divider()
                }
                NoteField(note = draft.note, onNoteChange = actions.onNoteChange)
            }
            Spacer(Modifier.height(16.dp))
        }
        if (uiState.showSave) {
            PillButton(
                text = stringResource(R.string.meal_save),
                onClick = actions.onSave,
                enabled = draft.canSave && !uiState.savePending,
                interactive = !uiState.closing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    if (editingTime) {
        MealTimeDialogs(
            eatenAt = draft.eatenAt,
            today = today,
            onDone = {
                actions.onEatenAtChange(it)
                editingTime = false
            },
            onDismiss = { editingTime = false },
        )
    }
}

/** A saved meal's way to the camera, now its photo steps between meals. */
private class CameraButton(val hasPhoto: Boolean, val onClick: () -> Unit)

@Composable
private fun MealTopBar(
    isFavorite: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    camera: CameraButton?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = enabled, colors = closingIconColors()) {
            Icon(FoodJournalIcons.Back, contentDescription = stringResource(R.string.back))
        }
        Spacer(Modifier.weight(1f))
        camera?.let {
            IconButton(onClick = it.onClick, enabled = enabled, colors = closingIconColors()) {
                Icon(
                    FoodJournalIcons.Camera,
                    contentDescription = stringResource(if (it.hasPhoto) R.string.meal_change_photo else R.string.meal_add_photo),
                )
            }
        }
        IconButton(onClick = onToggleFavorite, enabled = enabled, colors = closingIconColors()) {
            Icon(
                imageVector = if (isFavorite) FoodJournalIcons.FavoriteOn else FoodJournalIcons.Favorite,
                contentDescription = stringResource(
                    if (isFavorite) R.string.meal_unfavorite else R.string.meal_favorite,
                ),
            )
        }
        IconButton(onClick = onDelete, enabled = enabled, colors = closingIconColors()) {
            Icon(FoodJournalIcons.Delete, contentDescription = stringResource(R.string.meal_delete))
        }
    }
}

/** A closing screen's buttons stop responding but keep their look through the fade-out. */
@Composable
private fun closingIconColors() = IconButtonDefaults.iconButtonColors(
    disabledContentColor = LocalContentColor.current,
)

/** A saved meal's date, in grey at the photo's left edge. A tap edits it, until the screen closes. */
@Composable
private fun DateLine(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClickLabel = stringResource(R.string.meal_change_time), role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A thin segment for each meal of the week, oldest on the left, with this meal's in ink. Its room
 * is kept while the meals are read, so the photo below never jumps.
 */
@Composable
private fun WeekSegments(steps: MealSteps?) {
    val colors = MaterialTheme.colorScheme
    Canvas(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            .fillMaxWidth()
            .height(SegmentHeight),
    ) {
        val count = steps?.weekCount ?: return@Canvas
        val gap = SegmentGap.toPx()
        val width = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        repeat(count) { index ->
            drawRoundRect(
                color = if (index == steps.weekIndex) colors.primary else colors.surfaceContainerHighest,
                topLeft = Offset(index * (width + gap), 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(size.height / 2),
            )
        }
    }
}

/**
 * A saved meal's photo, or a plain tile with its name. The left third opens the previous meal and
 * the rest the next; a swipe left opens the next and a swipe right the previous. A vertical drag
 * still scrolls the form.
 */
@Composable
private fun SteppingPhoto(
    photoFile: File?,
    processing: Boolean,
    name: String,
    steps: MealSteps?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val previousLabel = stringResource(R.string.meal_previous)
    val nextLabel = stringResource(R.string.meal_next)
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(PhotoShape)
            .background(colors.surfaceContainer)
            .pointerInput(Unit) {
                detectTapGestures { offset -> if (offset.x < size.width / 3f) previous() else next() }
            }
            .pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        val swipe = SwipeDistance.toPx()
                        if (dragged <= -swipe) next() else if (dragged >= swipe) previous()
                    },
                ) { change, amount ->
                    change.consume()
                    dragged += amount
                }
            }
            .semantics(mergeDescendants = true) {
                // Only the steps there are: at either end of the meals, one has nowhere to go.
                customActions = listOfNotNull(
                    steps?.previous?.let { CustomAccessibilityAction(previousLabel) { previous(); true } },
                    steps?.next?.let { CustomAccessibilityAction(nextLabel) { next(); true } },
                )
            },
    ) {
        if (photoFile != null && !processing) {
            MealPhoto(photoFile)
        } else if (!processing) {
            // Set as the Journal's plain tile sets it, so a long word shrinks, then hyphenates.
            BoxWithConstraints {
                Text(
                    text = name,
                    style = tileNameStyle(
                        name = name,
                        width = maxWidth,
                        style = MaterialTheme.typography.titleLarge,
                        padding = PlainTilePadding,
                    ),
                    color = colors.onSurface,
                    modifier = Modifier.padding(PlainTilePadding),
                )
            }
        }
    }
}

/**
 * A new meal's photo, inset with rounded corners, at the camera's 3:4 frame so none of the shot
 * is cut away. With the number pad up, the form scrolls the price into view below it. Tapping
 * it retakes or picks another. Without a photo, a short grey tile offers to add one.
 */
@Composable
private fun PhotoArea(photoFile: File?, processing: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (photoFile == null && !processing) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .height(72.dp)
                .clip(PhotoShape)
                .background(colors.surfaceContainer)
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(FoodJournalIcons.Add, contentDescription = null, tint = colors.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.meal_add_photo),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
        return
    }
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(PhotoShape)
            .background(colors.surfaceContainer)
            .clickable(onClickLabel = stringResource(R.string.meal_change_photo), onClick = onClick),
    ) {
        if (!processing && photoFile != null) {
            MealPhoto(photoFile)
        }
    }
}

@Composable
private fun MealPhoto(file: File) {
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(file)
            .crossfade(true)
            .build(),
        contentDescription = stringResource(R.string.meal_photo),
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

/** The price, large, in Fraunces. Digits group as they are typed: "2,340". */
@Composable
private fun PriceField(
    priceText: String,
    onPriceChange: (String) -> Unit,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 12.dp)) {
        Text(
            text = stringResource(R.string.meal_price),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
        BasicTextField(
            value = priceText,
            onValueChange = onPriceChange,
            textStyle = MaterialTheme.typography.displayMedium.copy(color = colors.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(colors.onSurface),
            visualTransformation = GroupedDigits,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
}

@Composable
private fun NameField(
    name: String,
    suggestions: List<String>,
    onNameChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    FieldRow(label = stringResource(R.string.meal_name)) {
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
    }
    if (focused && suggestions.isNotEmpty()) {
        val focusManager = LocalFocusManager.current
        // The chips appear below the field, behind the keyboard; scroll them up into view.
        val bringIntoView = remember { BringIntoViewRequester() }
        LaunchedEffect(Unit) { bringIntoView.bringIntoView() }
        Row(
            modifier = Modifier
                .bringIntoViewRequester(bringIntoView)
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp + LabelWidth, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = {
                        onNameChange(suggestion)
                        focusManager.clearFocus()
                    },
                    label = { Text(suggestion) },
                    shape = CircleShape,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = null,
                )
            }
        }
    }
}

@Composable
private fun NoteField(note: String, onNoteChange: (String) -> Unit) {
    FieldRow(label = stringResource(R.string.meal_note), alignment = Alignment.Top) {
        BasicTextField(
            value = note,
            onValueChange = onNoteChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * "Save location", with the county and district in grey, and a switch. On, the meal keeps where it
 * was eaten. The whole row takes the tap, as a switch row does in Settings.
 */
@Composable
private fun SaveLocationRow(title: String, row: LocationRow, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = row.isOn, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            maxLines = 1,
        )
        row.district?.let { district ->
            Text(
                text = district,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
            )
        }
        Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
        // In ink, as Retro's switches are in color: filled when on, outlined when off.
        Switch(
            checked = row.isOn,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                checkedBorderColor = colors.primary,
                uncheckedThumbColor = colors.onSurface,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = colors.onSurface,
            ),
        )
    }
}

private val LabelWidth = 72.dp

/** A form row: a grey label on the left, the value beside it. */
@Composable
private fun FieldRow(
    label: String,
    modifier: Modifier = Modifier,
    alignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = alignment,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LabelWidth),
        )
        Box(Modifier.weight(1f)) { Row(content = content) }
    }
}

/** Rows grouped on one soft grey card, so the form reads as a single block below the price. */
@Composable
private fun FieldGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(GroupShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

/** A hairline between rows on the card, inset to start at the labels. */
@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.background,
    )
}

private val PhotoShape = RoundedCornerShape(20.dp)
private val GroupShape = RoundedCornerShape(16.dp)
private val SegmentHeight = 3.dp
private val SegmentGap = 2.dp
private val DistrictMaxWidth = 150.dp
private val PlainTilePadding = 20.dp

/** How far a drag has to run to count as a swipe. */
private val SwipeDistance = 48.dp

/** Shows "2340" as "2,340" while keeping the cursor where it belongs. */
private object GroupedDigits : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        return TransformedText(
            AnnotatedString(groupDigits(digits)),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = originalToGrouped(digits, offset)

                override fun transformedToOriginal(offset: Int) = groupedToOriginal(digits, offset)
            },
        )
    }
}

private val sampleToday = LocalDate.of(2026, 11, 3)

@ThemePreviews
@Composable
private fun MealNewPreview() {
    PreviewSurface {
        MealScreen(
            uiState = MealUiState(
                isNew = true,
                draft = MealDraft(eatenAt = LocalDateTime.of(2026, 11, 3, 12, 40), priceText = "280"),
                stage = MealStage.Form,
                photoFile = null,
                photoProcessing = true,
                photoFailed = false,
                savePending = false,
                closing = false,
                nameSuggestions = emptyList(),
                placeName = "Lin Dong Fang",
                showSave = true,
                locationRow = LocationRow(isOn = false, district = "臺北市 中山區"),
            ),
            actions = MealActions(),
            today = sampleToday,
        )
    }
}

@ThemePreviews
@Composable
private fun MealSavedPreview() {
    PreviewSurface {
        MealScreen(
            uiState = MealUiState(
                isNew = false,
                draft = MealDraft(
                    eatenAt = LocalDateTime.of(2026, 10, 27, 19, 5),
                    name = "Beef noodle soup",
                    priceText = "1280",
                    isFavorite = true,
                    note = "Extra spicy, with pickled greens",
                ),
                stage = MealStage.Form,
                photoFile = null,
                photoProcessing = false,
                photoFailed = false,
                savePending = false,
                closing = false,
                nameSuggestions = emptyList(),
                placeName = "Yongkang Beef Noodles",
                showSave = false,
                placeDistrict = "臺北市 大安區",
                locationRow = LocationRow(isOn = true, district = "臺北市 大安區"),
            ),
            actions = MealActions(),
            today = sampleToday,
        )
    }
}
