package com.stanley.foodjournal.ui.meal

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanley.foodjournal.R
import com.stanley.foodjournal.logic.formatMealTime
import com.stanley.foodjournal.logic.groupDigits
import com.stanley.foodjournal.logic.groupedToOriginal
import com.stanley.foodjournal.logic.originalToGrouped
import com.stanley.foodjournal.ui.icons.FoodJournalIcons
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews
import java.time.LocalDate
import java.time.LocalDateTime

/** Adds, shows, and edits one meal. */
@Composable
fun MealScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealViewModel = viewModel(factory = MealViewModel.Factory),
) {
    val uiState = viewModel.uiState
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onClose() }
    BackHandler(enabled = !finished) {
        if (uiState?.stage == MealStage.Camera) viewModel.onCameraBack() else viewModel.onBack()
    }
    val picker = rememberLauncherForActivityResult(PickPhotoWithLocation()) { uri ->
        if (uri != null) viewModel.onPicked(uri)
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
                            onDelete = viewModel::delete,
                            onReplacePhoto = viewModel::onRetakePhoto,
                            onPriceChange = viewModel::onPriceChange,
                            onNameChange = viewModel::onNameChange,
                            onNoteChange = viewModel::onNoteChange,
                            onEatenAtChange = viewModel::onEatenAtChange,
                            onSave = viewModel::save,
                        ),
                    )
                }
            }
        }
    }
}

private const val STAGE_FADE_MILLIS = 150

/** Everything the Meal screen can ask its ViewModel to do. */
class MealActions(
    val onBack: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onReplacePhoto: () -> Unit = {},
    val onPriceChange: (String) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onEatenAtChange: (LocalDateTime) -> Unit = {},
    val onSave: () -> Unit = {},
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
    var editingTime by rememberSaveable { mutableStateOf(false) }

    // A new meal starts with the number pad up, ready for the price.
    if (uiState.isNew) {
        LaunchedEffect(Unit) { priceFocus.requestFocus() }
    }

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        MealTopBar(
            isFavorite = draft.isFavorite,
            onBack = actions.onBack,
            onToggleFavorite = actions.onToggleFavorite,
            onDelete = actions.onDelete,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            PhotoArea(
                photoFile = uiState.photoFile,
                processing = uiState.photoProcessing,
                onClick = actions.onReplacePhoto,
            )
            PriceField(
                priceText = draft.priceText,
                onPriceChange = actions.onPriceChange,
                onDone = { if (uiState.isNew && draft.canSave) actions.onSave() },
                focusRequester = priceFocus,
            )
            Divider()
            NameField(
                name = draft.name,
                suggestions = uiState.nameSuggestions,
                onNameChange = actions.onNameChange,
            )
            Divider()
            FieldRow(
                label = stringResource(R.string.meal_time),
                modifier = Modifier.clickable { editingTime = true },
            ) {
                Text(formatMealTime(draft.eatenAt, today), style = MaterialTheme.typography.bodyLarge)
            }
            Divider()
            NoteField(note = draft.note, onNoteChange = actions.onNoteChange)
            Divider()
        }
        if (uiState.showSave) {
            Button(
                onClick = actions.onSave,
                enabled = draft.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.meal_save))
            }
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

@Composable
private fun MealTopBar(
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(FoodJournalIcons.Back, contentDescription = stringResource(R.string.back))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) FoodJournalIcons.FavoriteOn else FoodJournalIcons.Favorite,
                contentDescription = stringResource(
                    if (isFavorite) R.string.meal_unfavorite else R.string.meal_favorite,
                ),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(FoodJournalIcons.Delete, contentDescription = stringResource(R.string.meal_delete))
        }
    }
}

/**
 * The photo on top, cropped to 4:3 so the price stays in view above the number pad. Tapping it
 * retakes or picks another. Without a photo, one quiet row offers to add one.
 */
@Composable
private fun PhotoArea(photoFile: File?, processing: Boolean, onClick: () -> Unit) {
    if (photoFile == null && !processing) {
        FieldRow(label = stringResource(R.string.meal_photo), modifier = Modifier.clickable(onClick = onClick)) {
            Text(
                text = stringResource(R.string.meal_add_photo),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Divider()
        return
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = stringResource(R.string.meal_change_photo), onClick = onClick),
    ) {
        if (!processing && photoFile != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(photoFile)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.meal_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
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
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
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
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp + LabelWidth, end = 16.dp, bottom = 8.dp),
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
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
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

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

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
                nameSuggestions = emptyList(),
                showSave = true,
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
                nameSuggestions = emptyList(),
                showSave = false,
            ),
            actions = MealActions(),
            today = sampleToday,
        )
    }
}
