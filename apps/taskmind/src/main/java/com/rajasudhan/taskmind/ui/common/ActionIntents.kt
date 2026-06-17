package com.rajasudhan.taskmind.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the default dialer with [number] pre-filled. No call is placed — the user taps to dial. */
fun dialNumber(context: Context, number: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
}

/**
 * Opens Google Maps directions from the current location to a place. Prefers coordinates; otherwise
 * sends the place name as a destination query so Maps resolves the venue. No-op if neither is given.
 * Falls back to any handler (e.g. a browser) when the Google Maps app isn't installed.
 */
fun openDirections(context: Context, placeName: String?, lat: Double?, lng: Double?) {
    val destination = when {
        lat != null && lng != null -> "$lat,$lng"
        !placeName.isNullOrBlank() -> Uri.encode(placeName)
        else -> return
    }
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destination")
    val maps = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(maps)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
