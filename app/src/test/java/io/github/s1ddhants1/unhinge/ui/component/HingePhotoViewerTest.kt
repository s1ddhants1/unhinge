package io.github.s1ddhants1.unhinge.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HingePhotoViewerTest {

    @Test
    fun testHingePhotoAspectRatioConstant() {
        assertEquals(0.8f, HINGE_PHOTO_ASPECT_RATIO, 0.0001f)
        assertEquals(4f / 5f, HINGE_PHOTO_ASPECT_RATIO, 0.0001f)
    }

    @Test
    fun testCardDimensionsWidthConstrained() {
        val (width, height) = calculateHingeCardDimensions(360f, 600f)
        assertEquals(360f, width, 0.001f)
        assertEquals(450f, height, 0.001f)
        assertEquals(HINGE_PHOTO_ASPECT_RATIO, width / height, 0.001f)
    }

    @Test
    fun testCardDimensionsHeightConstrained() {
        val (width, height) = calculateHingeCardDimensions(600f, 300f)
        assertEquals(300f, height, 0.001f)
        assertEquals(240f, width, 0.001f)
        assertEquals(HINGE_PHOTO_ASPECT_RATIO, width / height, 0.001f)
    }

    @Test
    fun testCardDimensionsSquareScreen() {
        val (width, height) = calculateHingeCardDimensions(400f, 400f)
        assertEquals(400f, height, 0.001f)
        assertEquals(320f, width, 0.001f)
        assertEquals(HINGE_PHOTO_ASPECT_RATIO, width / height, 0.001f)
    }

    @Test
    fun testCalculateMaxPanOffset() {
        assertEquals(0f, calculateMaxPanOffset(400f, 1.0f), 0.001f)
        assertEquals(0f, calculateMaxPanOffset(400f, 0.8f), 0.001f)

        assertEquals(200f, calculateMaxPanOffset(400f, 2.0f), 0.001f)

        assertEquals(270f, calculateMaxPanOffset(360f, 2.5f), 0.001f)

        assertEquals(720f, calculateMaxPanOffset(360f, 5.0f), 0.001f)
    }

    @Test
    fun testPhotosSanitization() {
        val rawList = listOf("https://example.com/1.jpg", "", "   ", "https://example.com/2.jpg")
        val valid = rawList.filter { it.isNotBlank() }
        assertEquals(2, valid.size)
        assertEquals("https://example.com/1.jpg", valid[0])
        assertEquals("https://example.com/2.jpg", valid[1])
    }

    @Test
    fun testInitialIndexCoercion() {
        val photos = listOf("photo1", "photo2", "photo3")
        val negative = (-5).coerceIn(0, photos.size - 1)
        val overflow = 10.coerceIn(0, photos.size - 1)
        val valid = 1.coerceIn(0, photos.size - 1)

        assertEquals(0, negative)
        assertEquals(2, overflow)
        assertEquals(1, valid)
    }
}
