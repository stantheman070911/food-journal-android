package com.stanley.foodjournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stanley.foodjournal.R
import com.stanley.foodjournal.logic.unbreakableWords
import java.io.File

// Portrait tiles, like the photos; in the Journal about four and a half fit across, so a strip
// invites a swipe.
val MealTileWidth = 88.dp
val MealTileHeight = 117.dp

/** Tiles in a strip sit this far apart. */
val MealTileGap = 2.dp

/**
 * One meal: its photo, or a plain grey tile with its name. A short [label], such as the weekday,
 * sits in the lower left, in white over a photo and grey on the plain tile.
 *
 * A [thumb] that can't be read, such as one not yet rebuilt after a restore, gives way to the
 * full [photo], and if that fails too, to the plain tile.
 */
@Composable
fun MealTile(
    thumb: File?,
    photo: File?,
    name: String?,
    label: String,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = MealTileWidth,
    height: Dp = MealTileHeight,
) {
    val colors = MaterialTheme.colorScheme
    var failures by remember(thumb, photo) { mutableIntStateOf(0) }
    val image = listOfNotNull(thumb, photo).getOrNull(failures)
    Box(
        modifier
            .size(width, height)
            .clip(shape)
            .background(colors.surfaceContainer)
            .clickable(onClickLabel = stringResource(R.string.open_meal), onClick = onClick),
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onError = { failures++ },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val text = name.orEmpty()
            Text(
                text = text,
                style = tileNameStyle(text, width),
                color = colors.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(NamePadding),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                shadow = if (image != null) LabelShadow else null,
            ),
            color = if (image != null) Color.White else colors.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 6.dp),
        )
    }
}

/**
 * A plain tile's name, set so a word too long for the tile, as at a large font size, rarely breaks
 * just anywhere. The name first shrinks, by up to a fifth, until its longest word fits; past that,
 * words break with a hyphen. English patterns come first, as the interface is English; the phone's
 * own locales follow, so Chinese names keep their glyphs. [style] and [padding] are the Journal
 * tile's unless a larger tile, such as a saved meal's, sets its own.
 */
@Composable
fun tileNameStyle(
    name: String,
    width: Dp,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    padding: Dp = NamePadding,
): TextStyle {
    val base = style.copy(
        hyphens = Hyphens.Auto,
        localeList = LocaleList(listOf(Locale("en")) + LocaleList.current.localeList),
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scale = remember(name, base, density, width, padding) {
        val room = with(density) { (width - padding * 2).toPx() }
        val widest = unbreakableWords(name).maxOfOrNull { word ->
            measurer.measure(word, base, softWrap = false, maxLines = 1).size.width
        } ?: 0
        // A little to spare, so a word sized to fit exactly isn't broken after all.
        if (widest <= room) 1f else (room * FitSlack / widest).coerceAtLeast(MinNameScale)
    }
    return if (scale == 1f) {
        base
    } else {
        base.copy(
            fontSize = base.fontSize * scale,
            lineHeight = base.lineHeight * scale,
            letterSpacing = base.letterSpacing * scale,
        )
    }
}

/** A strip of tiles reads as one rounded band: only its two ends are rounded. */
fun stripShape(first: Boolean, last: Boolean): Shape {
    val start = if (first) StripCorner else 0.dp
    val end = if (last) StripCorner else 0.dp
    return RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
}

private val StripCorner = 12.dp
private val NamePadding = 10.dp
private const val MinNameScale = 0.8f
private const val FitSlack = 0.97f
private val LabelShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), blurRadius = 6f)
