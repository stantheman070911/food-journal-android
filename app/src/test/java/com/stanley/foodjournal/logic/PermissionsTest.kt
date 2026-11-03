package com.stanley.foodjournal.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {
    @Test
    fun `the first Add asks for camera and location together`() {
        assertEquals(
            PermissionRequest(camera = true, location = true),
            permissionsToRequest(cameraGranted = false, locationGranted = false, locationAsked = false),
        )
    }

    @Test
    fun `location is never asked twice`() {
        assertEquals(
            PermissionRequest(camera = true, location = false),
            permissionsToRequest(cameraGranted = false, locationGranted = false, locationAsked = true),
        )
    }

    @Test
    fun `nothing is asked once both are granted`() {
        assertTrue(permissionsToRequest(cameraGranted = true, locationGranted = true, locationAsked = false).isEmpty)
    }

    @Test
    fun `location granted elsewhere is not asked`() {
        assertEquals(
            PermissionRequest(camera = true, location = false),
            permissionsToRequest(cameraGranted = false, locationGranted = true, locationAsked = false),
        )
    }
}
