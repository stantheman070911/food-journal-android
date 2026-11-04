package com.stanley.foodjournal.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanley.foodjournal.R
import com.stanley.foodjournal.logic.DistrictMap
import com.stanley.foodjournal.logic.LabelBox
import com.stanley.foodjournal.logic.MapPin
import com.stanley.foodjournal.logic.TaiwanProjection
import com.stanley.foodjournal.logic.formatPrice
import com.stanley.foodjournal.logic.labelsThatFit
import com.stanley.foodjournal.ui.components.EmptyState
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Taiwan with its counties and districts, drawn by the app, and a pin for each place meals were
 * eaten at. A place's pin opens its meals in Search, and a lone meal's pin opens the meal. A tap
 * selects a district, and a second tap, or a tap on its line, opens its meals in Search.
 */
@Composable
fun MapScreen(
    onOpenMeal: (Long) -> Unit,
    onOpenPlace: (Long) -> Unit,
    onOpenDistrict: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val map = uiState.map
    when {
        uiState.isLoading -> Unit
        // The bundled file should always read; if it can't, say so rather than claim there are no meals.
        map == null -> EmptyState(message = stringResource(R.string.map_unreadable), modifier = modifier)
        uiState.isEmpty -> EmptyState(message = stringResource(R.string.map_empty), modifier = modifier)
        else -> Box(modifier.fillMaxSize()) {
            TaiwanMap(
                map = map,
                uiState = uiState,
                onTapPin = { pin -> pin.placeId?.let(onOpenPlace) ?: pin.mealId?.let(onOpenMeal) },
                onTapMap = { latitude, longitude -> viewModel.onTap(latitude, longitude)?.let(onOpenDistrict) },
            )
            BottomLines(
                uiState = uiState,
                onOpenSelected = { viewModel.openSelected()?.let(onOpenDistrict) },
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

/**
 * Where the map is looking: [scale] in pixels per map unit, and the map point at the middle of the
 * view. Saved, so the map stays put while other tabs show.
 */
private class MapView(scale: Double, centerX: Double, centerY: Double) {
    var scale by mutableDoubleStateOf(scale)
    var centerX by mutableDoubleStateOf(centerX)
    var centerY by mutableDoubleStateOf(centerY)

    fun toScreen(x: Double, y: Double, width: Float, height: Float) = Offset(
        ((x - centerX) * scale + width / 2).toFloat(),
        ((y - centerY) * scale + height / 2).toFloat(),
    )

    fun toMap(point: Offset, width: Float, height: Float): Pair<Double, Double> =
        centerX + (point.x - width / 2) / scale to centerY + (point.y - height / 2) / scale
}

@Composable
private fun TaiwanMap(
    map: DistrictMap,
    uiState: MapUiState,
    onTapPin: (MapPin) -> Unit,
    onTapMap: (latitude: Double, longitude: Double) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val pins = uiState.meals.pins
    val currentPins by rememberUpdatedState(pins)
    val currentOnTapPin by rememberUpdatedState(onTapPin)
    val currentOnTapMap by rememberUpdatedState(onTapMap)

    // Built once: every boundary line, in map units from an origin near Taiwan's middle, so the
    // numbers stay small enough to draw precisely at any zoom.
    val outlines = remember(map) { Outlines(map) }
    val selectedFill = remember(uiState.selected?.district?.code) {
        uiState.selected?.district?.let { district -> ringsPath(district.rings) }
    }
    // Each name is measured once; only where it goes changes as the map moves.
    val measured = remember(labelStyle) { HashMap<String, TextLayoutResult>() }
    val countyLabels = remember(map) { map.counties.sortedByDescending { it.area }.map { it.name to it.label } }
    val districtLabels = remember(map) { map.districts.sortedByDescending { it.area }.map { it.district to it.label } }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val fit = TaiwanProjection.fit(TaiwanProjection.Opening, width, height)
        val opening = TaiwanProjection.Opening
        val view = rememberSaveable(
            saver = listSaver(
                save = { listOf(it.scale, it.centerX, it.centerY) },
                restore = { MapView(it[0], it[1], it[2]) },
            ),
        ) {
            MapView(
                scale = fit,
                centerX = TaiwanProjection.x((opening.west + opening.east) / 2),
                centerY = TaiwanProjection.y((opening.south + opening.north) / 2),
            )
        }
        val reach = TaiwanProjection.Reach
        val pinTarget = with(density) { PinTarget.toPx() }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val nearest = currentPins
                            .map { pin -> pin to view.toScreen(TaiwanProjection.x(pin.longitude), TaiwanProjection.y(pin.latitude), w, h) }
                            .minByOrNull { (_, at) -> hypot(at.x - tap.x, at.y - tap.y) }
                        if (nearest != null && hypot(nearest.second.x - tap.x, nearest.second.y - tap.y) <= pinTarget / 2) {
                            currentOnTapPin(nearest.first)
                        } else {
                            val (x, y) = view.toMap(tap, w, h)
                            currentOnTapMap(TaiwanProjection.latitude(y), TaiwanProjection.longitude(x))
                        }
                    }
                }
                .pointerInput(fit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        // Zoom about the fingers, so the point under them stays under them.
                        val (ux, uy) = view.toMap(centroid, w, h)
                        val scale = (view.scale * zoom).coerceIn(fit * MinZoom, fit * MaxZoom)
                        view.scale = scale
                        view.centerX = (ux - (centroid.x - w / 2) / scale - pan.x / scale)
                            .coerceIn(TaiwanProjection.x(reach.west), TaiwanProjection.x(reach.east))
                        view.centerY = (uy - (centroid.y - h / 2) / scale - pan.y / scale)
                            .coerceIn(TaiwanProjection.y(reach.north), TaiwanProjection.y(reach.south))
                    }
                },
        ) {
            val origin = view.toScreen(Outlines.OriginX, Outlines.OriginY, size.width, size.height)
            val scale = view.scale.toFloat()
            withTransform({
                translate(origin.x, origin.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                selectedFill?.let { drawPath(it, color = colors.onSurface.copy(alpha = SelectedAlpha)) }
                drawPath(outlines.districts, color = colors.onSurface.copy(alpha = DistrictLineAlpha), style = Stroke(width = 1.dp.toPx() / scale))
                drawPath(outlines.counties, color = colors.onSurface.copy(alpha = CountyLineAlpha), style = Stroke(width = 1.2.dp.toPx() / scale))
            }

            // Counties zoomed out, districts zoomed in, each left out where it would cover another.
            val showDistricts = view.scale >= DistrictLabelsAt.toPx()
            val labels = if (showDistricts) districtLabels else countyLabels
            val placed = ArrayList<Pair<TextLayoutResult, Offset>>()
            val boxes = ArrayList<LabelBox>()
            for ((name, at) in labels) {
                val center = view.toScreen(TaiwanProjection.x(at.longitude), TaiwanProjection.y(at.latitude), size.width, size.height)
                if (center.x !in 0f..size.width || center.y !in 0f..size.height) continue
                val text = measured.getOrPut(name) { measurer.measure(name, labelStyle) }
                val topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f)
                placed += text to topLeft
                boxes += LabelBox(topLeft.x, topLeft.y, topLeft.x + text.size.width, topLeft.y + text.size.height)
            }
            for (index in labelsThatFit(boxes)) {
                val (text, topLeft) = placed[index]
                drawText(text, topLeft = topLeft)
            }

            // Ink dots, each ringed in the background so it reads over the lines.
            for (pin in pins) {
                val at = view.toScreen(TaiwanProjection.x(pin.longitude), TaiwanProjection.y(pin.latitude), size.width, size.height)
                val radius = pinRadius(pin).toPx()
                drawCircle(colors.background, radius = radius + PinRing.toPx(), center = at)
                drawCircle(colors.onSurface, radius = radius, center = at)
            }
        }

        // Each pin as a node for screen readers, placed where its dot is. They take no touches;
        // the map's own tap finds the nearest pin.
        pins.forEach { pin ->
            val description = pinDescription(pin)
            Box(
                Modifier
                    .offset {
                        val at = view.toScreen(TaiwanProjection.x(pin.longitude), TaiwanProjection.y(pin.latitude), width, height)
                        IntOffset((at.x - pinTarget / 2).roundToInt(), (at.y - pinTarget / 2).roundToInt())
                    }
                    .size(PinTarget)
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                        onClick { currentOnTapPin(pin); true }
                    },
            )
        }
    }
}

/** "滷三塊, 12 meals", as a pin reads aloud. */
@Composable
private fun pinDescription(pin: MapPin): String =
    stringResource(R.string.map_pin, pin.name, pluralStringResource(R.plurals.meal_count, pin.meals, formatPrice(pin.meals)))

/** A place with more meals gets a slightly larger pin; a lone meal's is the smallest. */
private fun pinRadius(pin: MapPin) =
    if (pin.placeId == null) MealPinRadius else PlacePinRadius + (ln(pin.meals.toDouble()) * PinGrowth).coerceAtMost(PinGrowthMax).dp

/**
 * The selected district's line, "臺北市 大安區 · 12 meals", which opens its meals in Search, and the
 * count of meals outside Taiwan. Both leave room for the Add button at the right.
 */
@Composable
private fun BottomLines(uiState: MapUiState, onOpenSelected: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium
    Column(modifier.padding(start = 16.dp, end = 88.dp, bottom = 16.dp)) {
        uiState.selected?.let { selected ->
            val count = if (selected.meals > 0) {
                pluralStringResource(R.plurals.meal_count, selected.meals, formatPrice(selected.meals))
            } else {
                stringResource(R.string.map_no_meals)
            }
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainer)
                    .then(
                        if (selected.meals > 0) {
                            Modifier.clickable(
                                onClickLabel = stringResource(R.string.spending_show_meals),
                                role = Role.Button,
                                onClick = onOpenSelected,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.map_district_line, selected.district.name, count),
                    style = style.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                )
            }
        }
        val outside = uiState.meals.outsideTaiwan
        if (outside > 0) {
            Text(
                text = pluralStringResource(R.plurals.map_outside, outside, formatPrice(outside)),
                style = style,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(colors.background.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Every boundary line once, as two paths: district borders, and county borders with the coast. */
private class Outlines(map: DistrictMap) {
    val districts = Path()
    val counties = Path()

    init {
        map.lines.forEachIndexed { index, line ->
            val path = if (index in map.countyLines) counties else districts
            for (point in 0 until line.size / 2) {
                val x = (TaiwanProjection.x(line[point * 2]) - OriginX).toFloat()
                val y = (TaiwanProjection.y(line[point * 2 + 1]) - OriginY).toFloat()
                if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
    }

    companion object {
        val OriginX = TaiwanProjection.x(121.0)
        val OriginY = TaiwanProjection.y(23.7)
    }
}

/** A district's area, for filling it in when selected. Its holes stay open. */
private fun ringsPath(rings: List<DoubleArray>) = Path().apply {
    fillType = PathFillType.EvenOdd
    for (ring in rings) {
        for (point in 0 until ring.size / 2) {
            val x = (TaiwanProjection.x(ring[point * 2]) - Outlines.OriginX).toFloat()
            val y = (TaiwanProjection.y(ring[point * 2 + 1]) - Outlines.OriginY).toFloat()
            if (point == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

// Lines and fills are the ink at low strength, so the map stays monochrome in both themes.
private const val DistrictLineAlpha = 0.14f
private const val CountyLineAlpha = 0.38f
private const val SelectedAlpha = 0.08f

private const val MinZoom = 0.6
private const val MaxZoom = 80.0

/** District names show once the map is this many dp to a degree of latitude, about six times the opening view. */
private val DistrictLabelsAt = 900.dp

private val PinTarget = 48.dp
private val PlacePinRadius = 4.dp
private val MealPinRadius = 3.dp
private val PinRing = 1.5.dp
private const val PinGrowth = 1.2
private const val PinGrowthMax = 4.0
