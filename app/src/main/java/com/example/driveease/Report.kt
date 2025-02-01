package com.example.driveease

data class Report(
    var userId: String? = null,
    var userEmail: String? = null,
    var description: String? = null,
    var lat: String? = null,
    var long: String? = null,
    var imageUrl: String? = null,
    var timestamp: String? = null,
    var location: String? = null
) {
    // Optional method to help in the retrieval and proper formatting of the location
    fun getFormattedLocation(): String {
        return if (!lat.isNullOrEmpty() && !long.isNullOrEmpty()) {
            "$lat, $long"  // Return latitude and longitude if both are available
        } else {
            location ?: "Location not available"  // Return the full location or a fallback message
        }
    }
}
