package com.stanley.foodjournal.ui.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stanley.foodjournal.R
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.logic.PickerPlace
import com.stanley.foodjournal.logic.PickerSubmit
import com.stanley.foodjournal.logic.PlacePickerContent
import com.stanley.foodjournal.logic.pickerSubmit
import com.stanley.foodjournal.ui.components.UndoBar
import com.stanley.foodjournal.ui.icons.FoodJournalIcons
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews
import kotlinx.coroutines.launch

/**
 * The place picker: a full-height sheet with a field that filters saved places and offers a
 * new one, "No place", and the places nearest first. Each place can be renamed in its row;
 * renaming to another place's name merges the two, with the undo bar inside the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePickerSheet(
    content: (query: String) -> PlacePickerContent,
    onSelect: (placeId: Long?) -> Unit,
    onAdd: (name: String) -> Unit,
    onRename: (Place, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }

    fun close(then: () -> Unit) {
        then()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxSize()) {
            PlacePicker(
                query = query,
                onQueryChange = { query = it },
                content = content(query),
                onSelect = { close { onSelect(it) } },
                onAdd = { close { onAdd(it) } },
                onRename = onRename,
                onClose = { close {} },
                focusQuery = true,
            )
            // The sheet is its own window, so the tabs' bar can't show over it.
            UndoBar(Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun PlacePicker(
    query: String,
    onQueryChange: (String) -> Unit,
    content: PlacePickerContent,
    onSelect: (Long?) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (Place, String) -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    focusQuery: Boolean = false,
    initialEditingId: Long? = null,
) {
    var editingId by rememberSaveable { mutableStateOf(initialEditingId) }
    Column(modifier.fillMaxSize()) {
        PickerHeader(onClose = onClose)
        QueryField(
            query = query,
            onQueryChange = onQueryChange,
            onSubmit = {
                when (val submit = pickerSubmit(content, query)) {
                    is PickerSubmit.Select -> onSelect(submit.placeId)
                    is PickerSubmit.Add -> onAdd(submit.name)
                    PickerSubmit.Nothing -> Unit
                }
            },
            requestFocus = focusQuery,
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
            content.addName?.let { name ->
                item(key = "add") {
                    PickerRow(icon = FoodJournalIcons.Add, onClick = { onAdd(name) }) {
                        RowText(stringResource(R.string.place_add, name))
                    }
                }
            }
            content.pendingName?.let { name ->
                item(key = "pending") {
                    PickerRow(icon = FoodJournalIcons.Check, onClick = { onAdd(name) }) {
                        RowText(name)
                        Detail(stringResource(R.string.place_new))
                    }
                }
            }
            if (content.showNoPlace) {
                item(key = "none") {
                    PickerRow(
                        icon = FoodJournalIcons.Check.takeIf { content.noPlaceSelected },
                        onClick = { onSelect(null) },
                    ) {
                        RowText(stringResource(R.string.place_none), muted = true)
                    }
                }
            }
            items(content.places, key = { it.place.id }) { row ->
                if (editingId == row.place.id) {
                    RenameRow(
                        place = row.place,
                        onDone = { typed ->
                            editingId = null
                            onRename(row.place, typed)
                        },
                    )
                } else {
                    PlaceRow(row = row, onSelect = { onSelect(row.place.id) }, onEdit = { editingId = row.place.id })
                }
            }
        }
    }
}

/** A centered title with a close button, as on any sheet. */
@Composable
private fun PickerHeader(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Text(
            text = stringResource(R.string.meal_place),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.align(Alignment.Center),
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
        ) {
            Icon(FoodJournalIcons.Close, contentDescription = stringResource(R.string.close))
        }
    }
}

/** The filter, in a soft grey pill. Its icon and text line up with the rows below. */
@Composable
private fun QueryField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    requestFocus: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    if (requestFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainer)
            .padding(start = RowStart - 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(IconColumn)) {
            Icon(FoodJournalIcons.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.place_search),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(colors.onSurface),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onSubmit()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
    }
}

@Composable
private fun PlaceRow(row: PickerPlace, onSelect: () -> Unit, onEdit: () -> Unit) {
    PickerRow(
        icon = FoodJournalIcons.Check.takeIf { row.isSelected },
        onClick = onSelect,
        trailingButton = true,
    ) {
        RowText(row.place.name)
        row.distance?.let { Detail(it) }
        IconButton(onClick = onEdit) {
            Icon(
                FoodJournalIcons.Edit,
                contentDescription = stringResource(R.string.place_rename, row.place.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A place's row turned into a field, with its name selected so typing replaces it. */
@Composable
private fun RenameRow(place: Place, onDone: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(place.name, TextRange(0, place.name.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val colors = MaterialTheme.colorScheme
    PickerRow(icon = null, trailingButton = true) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(colors.onSurface),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone(value.text) }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus),
        )
        IconButton(onClick = { onDone(value.text) }) {
            Icon(FoodJournalIcons.Check, contentDescription = stringResource(R.string.place_rename_done))
        }
    }
}

/**
 * One picker row: an icon column on the left, so names line up whatever is checked, and the
 * row's text beside it. Rows that end in a button leave it room instead of the usual margin.
 */
@Composable
private fun PickerRow(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailingButton: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(start = RowStart, end = if (trailingButton) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(IconColumn)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        content()
    }
}

@Composable
private fun RowScope.RowText(text: String, muted: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun Detail(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp),
    )
}

// The icon column sits inside the search pill's inset, so icons and names line up below it.
private val RowStart = 28.dp
private val IconColumn = 36.dp

private val samplePlaces = listOf(
    PickerPlace(Place(1, "Lin Dong Fang", 25.0526, 121.5395), "80 m", isSelected = true),
    PickerPlace(Place(2, "Yongkang Beef Noodles", 25.0330, 121.5297), "140 m", isSelected = false),
    PickerPlace(Place(3, "Simple Kaffa", 25.0418, 121.5490), "1.2 km", isSelected = false),
    PickerPlace(Place(4, "Night market"), null, isSelected = false),
)

@ThemePreviews
@Composable
private fun PlacePickerPreview() {
    PreviewSurface {
        PlacePicker(
            query = "",
            onQueryChange = {},
            content = PlacePickerContent(
                addName = null,
                pendingName = null,
                showNoPlace = true,
                noPlaceSelected = false,
                places = samplePlaces,
            ),
            onSelect = {},
            onAdd = {},
            onRename = { _, _ -> },
        )
    }
}

@ThemePreviews
@Composable
private fun PlacePickerTypingPreview() {
    PreviewSurface {
        PlacePicker(
            query = "Kaf",
            onQueryChange = {},
            content = PlacePickerContent(
                addName = "Kaf",
                pendingName = null,
                showNoPlace = false,
                noPlaceSelected = false,
                places = samplePlaces.filter { it.place.id == 3L },
            ),
            onSelect = {},
            onAdd = {},
            onRename = { _, _ -> },
        )
    }
}

@ThemePreviews
@Composable
private fun PlacePickerRenamePreview() {
    PreviewSurface {
        PlacePicker(
            query = "",
            onQueryChange = {},
            content = PlacePickerContent(
                addName = null,
                pendingName = null,
                showNoPlace = true,
                noPlaceSelected = false,
                places = samplePlaces,
            ),
            onSelect = {},
            onAdd = {},
            onRename = { _, _ -> },
            initialEditingId = 2,
        )
    }
}
