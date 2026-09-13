package com.example.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.example.data.db.PlaceDao
import com.example.data.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

data class GeocodeResult(
    val locality: String?,
    val district: String?,
    val region: String?,
    val country: String?,
    val placeId: String?,
    val status: String // "RESOLVED", "FAILED", "OFFLINE"
)

class TripGeocoder(
    private val context: Context,
    private val placeDao: PlaceDao
) {
    companion object {
        private const val TAG = "TripGeocoder"
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodeResult {
        return withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) {
                return@withContext GeocodeResult(null, null, null, null, null, "OFFLINE")
            }

            try {
                val geocoder = Geocoder(context, Locale.US)
                val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var resultList: List<Address>? = null
                    val lock = Object()
                    var isDone = false

                    geocoder.getFromLocation(latitude, longitude, 1) { list ->
                        synchronized(lock) {
                            resultList = list
                            isDone = true
                            lock.notifyAll()
                        }
                    }

                    synchronized(lock) {
                        if (!isDone) {
                            lock.wait(5000L)
                        }
                    }
                    resultList
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)
                }

                val address = addresses?.firstOrNull()
                if (address == null) {
                    return@withContext GeocodeResult(null, null, null, null, null, "FAILED")
                }

                val locality = address.locality ?: address.subAdminArea ?: address.featureName
                val district = address.subAdminArea
                val region = address.adminArea
                val country = address.countryName ?: "Pakistan"

                // Check or insert Place entity for canonical tracking
                val placeId = findOrCreatePlace(locality, district, region)

                GeocodeResult(
                    locality = locality,
                    district = district,
                    region = region,
                    country = country,
                    placeId = placeId,
                    status = "RESOLVED"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Reverse geocode error: ${e.message}")
                GeocodeResult(null, null, null, null, null, "OFFLINE")
            }
        }
    }

    private suspend fun findOrCreatePlace(locality: String?, district: String?, region: String?): String? {
        val targetName = locality ?: district ?: region ?: return null
        val existing = placeDao.findByName(targetName)
        if (existing != null) return existing.id

        val isDistrict = district != null && targetName.contains("District", ignoreCase = true)
        val newPlace = Place(
            id = UUID.randomUUID().toString(),
            canonicalName = targetName,
            type = if (isDistrict) "DISTRICT" else "LOCALITY",
            countryCode = "PK",
            aliasesJson = "[\"${targetName.lowercase()}\"]",
            provenance = "reverse_geocoder"
        )
        placeDao.insertPlace(newPlace)
        return newPlace.id
    }
}
