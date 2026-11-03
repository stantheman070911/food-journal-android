package com.stanley.foodjournal.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoSizingTest {
    @Test
    fun `fits the long edge, keeping the aspect ratio`() {
        assertEquals(PixelSize(1600, 1200), fitWithin(PixelSize(4000, 3000), PHOTO_LONG_EDGE))
        assertEquals(PixelSize(1200, 1600), fitWithin(PixelSize(3000, 4000), PHOTO_LONG_EDGE))
        assertEquals(PixelSize(400, 300), fitWithin(PixelSize(1600, 1200), THUMB_LONG_EDGE))
        assertEquals(PixelSize(1600, 900), fitWithin(PixelSize(1920, 1080), PHOTO_LONG_EDGE))
    }

    @Test
    fun `never enlarges a small image`() {
        assertEquals(PixelSize(800, 600), fitWithin(PixelSize(800, 600), PHOTO_LONG_EDGE))
        assertEquals(PixelSize(1600, 1600), fitWithin(PixelSize(1600, 1600), PHOTO_LONG_EDGE))
    }

    @Test
    fun `keeps very thin images at least a pixel wide`() {
        assertEquals(PixelSize(1600, 1), fitWithin(PixelSize(10000, 2), PHOTO_LONG_EDGE))
    }
}
