package com.example.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.db.LocationSnapshotDao
import com.example.data.db.PlaceDao
import com.example.data.model.LocationSnapshot
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    }

    private val geocoder = TripGeocoder(context, placeDao)
    private val rollingBuffer = mutableListOf<Location>()

    fun recordBufferFix(location: Location) {
        synchronized(rollingBuffer) {
            rollingBuffer.add(location)
            val cutoff = System.currentTimeMillis() - BUFFER_RETENTION_MS
            rollingBuffer.removeAll { it.time < cutoff }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
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
                return@withContext snapshot
            }

            // 1. Check buffer for nearest fix within 15 minutes
            val bestFix = synchronized(rollingBuffer) {
                rollingBuffer.minByOrNull { Math.abs(it.time - candidateTime) }
            } ?: queryLastKnownLocation()

            if (bestFix == null) {
                val snapshot = LocationSnapshot(
                    id = UUID.randomUUID().toString(),
                    detectedAt = System.currentTimeMillis(),
                    qualityStatus = "UNAVAILABLE",
                    source = "DEVICE"
                )
                locationSnapshotDao.insert(snapshot)
                return@withContext snapshot
            }

            val ageMs = Math.abs(candidateTime - bestFix.time)
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
                capturedElapsedRealtime = bestFix.elapsedRealtimeNanos / 1_000_000,
                detectedAt = System.currentTimeMillis(),
                qualityStatus = qualityStatus,
                source = "DEVICE",
                geocodeState = "PENDING"
            )
            locationSnapshotDao.insert(initialSnapshot)

            // Geocode if fresh or approximate
            if (qualityStatus == "FRESH" || qualityStatus == "APPROXIMATE") {
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
                return@withContext enrichedSnapshot
            }

            return@withContext initialSnapshot
        }
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
