package com.example.driveease

data class PotholeReport(
    val userId: String = "",
    val userEmail: String = "",
    val imageUrl: String = "",
    val description: String = "",
    val severity: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
   // val address: String = "", // new added if any error in future remove it
    val timestamp: Long = 0
)
