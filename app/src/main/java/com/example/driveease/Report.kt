package com.example.driveease

data class Report(
    val id: Int,
    val description: String?,
    val imageUrl: String?,
    val severity: String?,
    val createdAt: String?,
    val zoneName: String?,
    val status: String?
) {
    fun getFormattedLocation(): String {
        return zoneName?.takeIf { it.isNotEmpty() } ?: "Location not available"
    }

    fun getFormattedTimestamp(): String {
        return createdAt?.takeIf { it.isNotEmpty() } ?: "Date not available"
    }
}