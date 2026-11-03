package com.stanley.foodjournal.logic

/** What opening the camera asks for. */
data class PermissionRequest(val camera: Boolean, val location: Boolean) {
    val isEmpty: Boolean get() = !camera && !location
}

/**
 * Camera is asked whenever it is off, since logging needs it; Android limits how often the
 * dialog actually shows. Location is asked once ever: without it, places come from the list.
 */
fun permissionsToRequest(cameraGranted: Boolean, locationGranted: Boolean, locationAsked: Boolean) =
    PermissionRequest(camera = !cameraGranted, location = !locationGranted && !locationAsked)
