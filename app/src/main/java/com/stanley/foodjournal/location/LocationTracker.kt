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
import com.stanley.foodjournal.logic.deviceFix
import com.stanley.foodjournal.logic.isRecentFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the phone is. An interface so tests can supply their own fixes. */
interface LocationSource {
    /** The phone's position for the meal being logged, or null until one is found. */
    val fix: StateFlow<LocationFix?>

    fun hasPermission(): Boolean

    /** Starts finding the current location. Does nothing without permission. */
    fun start()
}

/**
 * Finds where the phone is, with Android's built-in fused provider. [start] is called as the
 * camera opens, so a fix is usually ready by the time the form shows.
 */
class LocationTracker(private val context: Context) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val _fix = MutableStateFlow<LocationFix?>(null)
    private var latest: Location? = null
    private var request: CancellationSignal? = null

    override val fix: StateFlow<LocationFix?> = _fix.asStateFlow()

    override fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /**
     * Offers a recent fix at once, then asks for a current one. A fix over two minutes old is
     * dropped, since the phone may have moved since. Does nothing without permission. Call on
     * the main thread.
     */
    override fun start() {
        if (!hasPermission() || request != null) return
        try {
            val lastKnown = manager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
            val now = SystemClock.elapsedRealtimeNanos()
            publish(
                listOfNotNull(latest, lastKnown)
                    .filter { isRecentFix(it.elapsedRealtimeNanos, now) }
                    .maxByOrNull { it.elapsedRealtimeNanos },
            )
            val signal = CancellationSignal()
            request = signal
            manager.getCurrentLocation(LocationManager.FUSED_PROVIDER, CurrentRequest, signal, context.mainExecutor) { location ->
                request = null
                if (location != null) publish(location)
            }
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call.
            request = null
        }
    }

    private fun publish(location: Location?) {
        latest = location
        _fix.value = location?.let {
            deviceFix(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null)
        }
    }

    private companion object {
        // A 150 m match needs a precise fix. The default, balanced quality may settle for
        // Wi-Fi or cell positions, or none at all where those are off.
        val CurrentRequest: LocationRequest = LocationRequest.Builder(0)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .setDurationMillis(30_000)
            .build()
    }
}
