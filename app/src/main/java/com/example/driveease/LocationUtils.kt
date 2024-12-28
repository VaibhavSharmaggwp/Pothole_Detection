package com.example.driveease

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.util.Locale

object LocationUtils {
    fun getAddressFromLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        callback: (String) -> Unit
    ) {
        val geocoder = Geocoder(context, Locale.getDefault())

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    val address = addresses.firstOrNull()
                    callback(formatAddress(address))
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                val address = addresses?.firstOrNull()
                callback(formatAddress(address))
            }
        } catch (e: Exception) {
            callback("Unknown Location")
        }
    }

    private fun formatAddress(address: Address?): String {
        if (address == null) return "Unknown Location"

        val addressComponents = mutableListOf<String>()

        // Add address components if they exist
        address.subThoroughfare?.let { addressComponents.add(it) } // House number
        address.thoroughfare?.let { addressComponents.add(it) }     // Street name
        address.subLocality?.let { addressComponents.add(it) }      // Neighborhood
        address.locality?.let { addressComponents.add(it) }         // City
        address.subAdminArea?.let { addressComponents.add(it) }     // District
        address.adminArea?.let { addressComponents.add(it) }        // State
        address.postalCode?.let { addressComponents.add(it) }       // PIN code

        return if (addressComponents.isEmpty()) {
            "Unknown Location"
        } else {
            addressComponents.joinToString(", ")
        }
    }
}