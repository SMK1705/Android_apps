package com.rajasudhan.taskmind.data.source

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Resolves a place name from a message (e.g. "Panda Express, Dunwoody GA") to coordinates so an
 * approved note can show it on the map. Uses the on-device [Geocoder] (no extra API key); biased to
 * the user's last known location so a bare name resolves to the nearby one. Returns null when the
 * place can't be resolved — callers fall back to a Google Maps name search for directions.
 */
@Singleton
class PlaceGeocoder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun geocode(placeName: String): Pair<Double, Double>? {
        if (placeName.isBlank() || !Geocoder.isPresent()) return null
        val near = lastLocation()
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocodeAsync(geocoder, placeName, near)
        } else {
            geocodeSync(geocoder, placeName, near)
        }
    }

    /** Android 13+ (API 33): the non-blocking listener API. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocodeAsync(geocoder: Geocoder, placeName: String, near: Location?): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val listener = object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (cont.isActive) cont.resume(addresses.firstOrNull()?.let { it.latitude to it.longitude })
                }
                override fun onError(errorMessage: String?) {
                    if (cont.isActive) cont.resume(null)
                }
            }
            try {
                if (near != null) {
                    val d = 0.5 // ~55 km bounding box so "Panda Express" finds the local one.
                    geocoder.getFromLocationName(
                        placeName, 1,
                        near.latitude - d, near.longitude - d,
                        near.latitude + d, near.longitude + d,
                        listener
                    )
                } else {
                    geocoder.getFromLocationName(placeName, 1, listener)
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    /** Pre-API-33 fallback: the deprecated blocking overload, run off the main thread. */
    @Suppress("DEPRECATION")
    private suspend fun geocodeSync(geocoder: Geocoder, placeName: String, near: Location?): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val list = if (near != null) {
                    val d = 0.5
                    geocoder.getFromLocationName(
                        placeName, 1,
                        near.latitude - d, near.longitude - d,
                        near.latitude + d, near.longitude + d
                    )
                } else {
                    geocoder.getFromLocationName(placeName, 1)
                }
                list?.firstOrNull()?.let { it.latitude to it.longitude }
            }.getOrNull()
        }

    @SuppressLint("MissingPermission")
    private suspend fun lastLocation(): Location? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }
}
