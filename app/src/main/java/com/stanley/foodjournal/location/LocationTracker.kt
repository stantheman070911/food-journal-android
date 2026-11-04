package com.stanley.foodjournal.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.os.CancellationSignal
import android.os.SystemClock
import com.stanley.foodjournal.logic.LocationFix
import com.stanley.foodjournal.logic.isUsable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Where the phone is, for the meal being logged. An interface so tests can decide where. */
interface PhoneLocation {
    /** The latest fix precise enough to use, or null until one arrives. */
    val fix: StateFlow<LocationFix?>

    /** Whether location is allowed at all, precise or approximate. */
    fun isAllowed(): Boolean

    /**
     * Starts finding the phone's location, if precise location is allowed: an approximate one can
     * be kilometres off. Call on the main thread.
     */
    fun start()

    /** Stops looking, as when the Meal screen closes. */
    fun stop()
}

/**
 * Finds where the phone is with Android's built-in fused provider: a fix under two minutes old at
 * once, then a current one at high accuracy, for up to 30 seconds. Fixes worse than 100 m are not
 * used. [start] is called as the camera opens, so a fix is usually ready by the time the form shows.
 */
class LocationTracker(private val context: Context) : PhoneLocation {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val _fix = MutableStateFlow<LocationFix?>(null)
    private var request: CancellationSignal? = null

    override val fix: StateFlow<LocationFix?> = _fix.asStateFlow()

    override fun isAllowed(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun start() {
        if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || request != null) return
        try {
            manager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)?.takeIf { it.isRecent() }?.let(::publish)
            val signal = CancellationSignal()
            request = signal
            manager.getCurrentLocation(LocationManager.FUSED_PROVIDER, CurrentRequest, signal, context.mainExecutor) { location ->
                if (request === signal) request = null
                location?.let(::publish)
            }
        } catch (_: SecurityException) {
            // Permission was taken back between the check and the call.
            request = null
        }
    }

    override fun stop() {
        request?.cancel()
        request = null
    }

    /** Keeps [location] only if it is precise enough to place a meal. */
    private fun publish(location: Location) {
        // A fix without an accuracy can't be trusted to pick a place 150 m away.
        val fix = LocationFix(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else Float.POSITIVE_INFINITY)
        if (fix.isUsable()) _fix.value = fix
    }

    private fun isGranted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.isRecent(): Boolean =
        SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos < MAX_AGE.inWholeNanoseconds

    private companion object {
        val MAX_AGE = 2.minutes

        // A 150 m match needs a precise fix. The default, balanced quality may settle for Wi-Fi or
        // cell positions, or none at all where those are off.
        val CurrentRequest: LocationRequest = LocationRequest.Builder(0)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .setDurationMillis(30.seconds.inWholeMilliseconds)
            .build()
    }
}
