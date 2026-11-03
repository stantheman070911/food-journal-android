package com.stanley.foodjournal.logic

import kotlin.math.max
import kotlin.math.roundToInt

/** Stored photos are at most this long on their long edge. */
const val PHOTO_LONG_EDGE = 1600

/** Thumbnails for tiles are at most this long on their long edge. */
const val THUMB_LONG_EDGE = 400

/** JPEG quality for stored photos and thumbnails. */
const val JPEG_QUALITY = 85

/** Pixel dimensions. */
data class PixelSize(val width: Int, val height: Int)

/**
 * The size that fits [size] within [longEdge] on its long edge, keeping the aspect ratio.
 * Never enlarges a smaller image.
 */
fun fitWithin(size: PixelSize, longEdge: Int): PixelSize {
    val longest = max(size.width, size.height)
    if (longest <= longEdge) return size
    val scale = longEdge.toDouble() / longest
    return PixelSize(
        width = (size.width * scale).roundToInt().coerceAtLeast(1),
        height = (size.height * scale).roundToInt().coerceAtLeast(1),
    )
}
