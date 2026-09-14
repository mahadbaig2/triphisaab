package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.db.LocationSnapshotDao
import com.example.data.db.PlaceDao
import com.example.data.model.LocationSnapshot
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class TripLocationProvider(
    private val context: Context,
    private val locationSnapshotDao: LocationSnapshotDao,
    private val placeDao: PlaceDao
) {
    companion object {
        private const val TAG = "TripLocationProvider"
        const val MAX_FRESH_AGE_MS = 120_000L // 2 minutes
        const val MAX_FRESH_ACCURACY_M = 250f  // 250 meters
        const val BUFFER_RETENTION_MS = 15 * 60 * 1000L // 15 minutes rolling buffer
        const val FRESH_ACQUISITION_TIMEOUT_MS = 10_000L // 10 seconds bounded acquisition
    }

    private val geocoder = TripGeocoder(context, placeDao)
    private val rollingBuffer = mutableListOf<Location>()

    private val _currentLocationSnapshot = MutableStateFlow<LocationSnapshot?>(null)
    val currentLocationSnapshot: StateFlow<LocationSnapshot?> = _currentLocationSnapshot.asStateFlow()

    fun recordBufferFix(location: Location) {
        synchronized(rollingBuffer) {
            rollingBuffer.add(location)
            val cutoff = System.currentTimeMillis() - BUFFER_RETENTION_MS
            rollingBuffer.removeAll { it.time < cutoff }
        }
        // Update current location snapshot state
        val ageMs = calculateFixAgeMs(location)
        val accuracy = if (location.hasAccuracy()) location.accuracy else 500f
        val quality = when {
            ageMs <= MAX_FRESH_AGE_MS && accuracy <= MAX_FRESH_ACCURACY_M -> "FRESH"
            ageMs <= MAX_FRESH_AGE_MS && accuracy > MAX_FRESH_ACCURACY_M -> "APPROXIMATE"
            else -> "STALE"
        }
        val snapshot = LocationSnapshot(
            id = UUID.randomUUID().toString(),
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = accuracy,
            capturedAt = location.time,
            capturedElapsedRealtime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) location.elapsedRealtimeNanos / 1_000_000 else null,
            detectedAt = System.currentTimeMillis(),
            qualityStatus = quality,
            source = "DEVICE",
            geocodeState = "PENDING"
        )
        _currentLocationSnapshot.value = snapshot
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun requestFreshFix(timeoutMs: Long = FRESH_ACQUISITION_TIMEOUT_MS): Location? {
        if (!hasLocationPermission()) return null
        return withContext(Dispatchers.IO) {
            try {
                val fused = LocationServices.getFusedLocationProviderClient(context)
                val cts = CancellationTokenSource()
                withTimeoutOrNull(timeoutMs) {
                    val loc = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                    if (loc != null) {
                        recordBufferFix(loc)
                    }
                    loc
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bounded fresh location acquisition failed: ${e.message}")
                null
            }
        }
    }

    suspend fun captureCandidateSnapshot(candidateTime: Long = System.currentTimeMillis()): LocationSnapshot {
        return withContext(Dispatchers.IO) {
            if (!hasLocationPermission()) {
                val snapshot = LocationSnapshot(
                    id = UUID.randomUUID().toString(),
                    detectedAt = System.currentTimeMillis(),
                    qualityStatus = "DENIED",
                    source = "DEVICE"
                )
                locationSnapshotDao.insert(snapshot)
                _currentLocationSnapshot.value = snapshot
                return@withContext snapshot
            }

            // 1. Check rolling buffer for a recent fresh fix within 2 minutes
            val bufferCandidate = synchronized(rollingBuffer) {
                rollingBuffer.filter { Math.abs(it.time - candidateTime) <= MAX_FRESH_AGE_MS }
                    .minByOrNull { Math.abs(it.time - candidateTime) }
            }

            // 2. If no fresh fix in buffer, attempt a bounded 10s fresh acquisition
            val bestFix = bufferCandidate
                ?: requestFreshFix(timeoutMs = FRESH_ACQUISITION_TIMEOUT_MS)
                ?: synchronized(rollingBuffer) { rollingBuffer.minByOrNull { Math.abs(it.time - candidateTime) } }
                ?: queryLastKnownLocation()

            if (bestFix == null) {
                val snapshot = LocationSnapshot(
                    id = UUID.randomUUID().toString(),
                    detectedAt = System.currentTimeMillis(),
                    qualityStatus = "UNAVAILABLE",
                    source = "DEVICE"
                )
                locationSnapshotDao.insert(snapshot)
                _currentLocationSnapshot.value = snapshot
                return@withContext snapshot
            }

            val ageMs = calculateFixAgeMs(bestFix)
            val accuracy = if (bestFix.hasAccuracy()) bestFix.accuracy else 500f

            val qualityStatus = when {
                ageMs <= MAX_FRESH_AGE_MS && accuracy <= MAX_FRESH_ACCURACY_M -> "FRESH"
                ageMs <= MAX_FRESH_AGE_MS && accuracy > MAX_FRESH_ACCURACY_M -> "APPROXIMATE"
                ageMs > MAX_FRESH_AGE_MS -> "STALE"
                else -> "APPROXIMATE"
            }

            val snapshotId = UUID.randomUUID().toString()
            val initialSnapshot = LocationSnapshot(
                id = snapshotId,
                latitude = bestFix.latitude,
                longitude = bestFix.longitude,
                accuracyMeters = accuracy,
                capturedAt = bestFix.time,
                capturedElapsedRealtime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) bestFix.elapsedRealtimeNanos / 1_000_000 else null,
                detectedAt = System.currentTimeMillis(),
                qualityStatus = qualityStatus,
                source = "DEVICE",
                geocodeState = "PENDING"
            )
            locationSnapshotDao.insert(initialSnapshot)

            // Geocode coordinates (preserving coordinates without guessing city on failure)
            val geo = geocoder.reverseGeocode(bestFix.latitude, bestFix.longitude)
            val enrichedSnapshot = initialSnapshot.copy(
                locality = geo.locality,
                district = geo.district,
                region = geo.region,
                country = geo.country,
                placeId = geo.placeId,
                geocodeState = geo.status
            )
            locationSnapshotDao.update(enrichedSnapshot)
            _currentLocationSnapshot.value = enrichedSnapshot
            return@withContext enrichedSnapshot
        }
    }

    private fun calculateFixAgeMs(location: Location): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val elapsedNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            if (elapsedNanos >= 0) {
                return elapsedNanos / 1_000_000L
            }
        }
        return Math.max(0L, System.currentTimeMillis() - location.time)
    }

    private fun queryLastKnownLocation(): Location? {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = locationManager.getProviders(true)
            var best: Location? = null
            for (provider in providers) {
                val l = try {
                    locationManager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    null
                }
                if (l != null && (best == null || l.accuracy < best.accuracy)) {
                    best = l
                }
            }
            return best
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get last known location: ${e.message}")
            return null
        }
    }
}
