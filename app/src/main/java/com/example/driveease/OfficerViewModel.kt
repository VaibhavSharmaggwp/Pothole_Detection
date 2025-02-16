package com.example.driveease

import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class OfficerViewModel: ViewModel() {
    private val database = Firebase.database
    private val _reports = MutableStateFlow<List<PotholeReport>>(emptyList())
    private val _roleCounts = MutableStateFlow<RoleCounts?>(null)

    val reports = _reports.asStateFlow()
    val roleCounts = _roleCounts.asStateFlow()

    init {
        loadReports()
        loadRoleCounts()
    }

    private fun loadReports() {
        database.getReference("pothole_reports").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reportsList = mutableListOf<PotholeReport>()
                for (reportSnapshot in snapshot.children) {
                    val report = reportSnapshot.getValue(PotholeReport::class.java)
                    report?.let {
                        // Set the Firebase key as the report ID and status to "in-progress"
                        val reportWithId = it.copy(id = reportSnapshot.key ?: "", status = "in-progress")
                        reportsList.add(reportWithId)
                    }
                }
                _reports.value = reportsList.sortedByDescending { it.severity }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun loadRoleCounts() {
        database.getReference("admins/role_counts").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _roleCounts.value = snapshot.getValue(RoleCounts::class.java)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    fun calculateWorkersDeployed(): Int {
        val availableWorkers = _roleCounts.value?.workers ?: 0
        var requiredWorkers = 0

        for (report in _reports.value) {
            val severity = report.severity.toLowerCase(Locale.ROOT)
            when (severity) {
                "high" -> requiredWorkers += 3
                "medium" -> requiredWorkers += 2
                else -> requiredWorkers += 1
            }
        }

        // Ensure the required workers do not exceed the available workers
        return minOf(requiredWorkers, availableWorkers)
    }
}

data class RoleCounts(
    val officers: Int = 0,
    val sdo: Int = 0,
    val workers: Int = 0
)