package com.example.driveease



data class PotholeReport(
    val userId: String = "",
    val userEmail: String = "",
    val imageUrl: String? = "",
    val description: String = "",
    val severity: String = "low",
    val latitude: Double? = 0.0,
    val longitude: Double? = 0.0,
    val address: String = "",  // Add address field
    val date: String = "",    // Add separate  date field
    val time: String = "",   // Add separate time field
    val status: String = "in-progress",
    val id: String = "",
)