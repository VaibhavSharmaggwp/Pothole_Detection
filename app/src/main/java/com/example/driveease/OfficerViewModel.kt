package com.example.driveease

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.PropertyName
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class OfficerViewModel : ViewModel() {
    private val database = Firebase.database
    private val auth = FirebaseAuth.getInstance()
    private val _reports = MutableStateFlow<List<PotholeReport>>(emptyList())
    private val _roleCounts = MutableStateFlow<RoleCounts?>(null)

    val reports = _reports.asStateFlow()
    val roleCounts = _roleCounts.asStateFlow()

    init {
        if (auth.currentUser != null) {
            loadReports()
            loadRoleCounts()
        }
    }

    private fun loadReports() {
        database.getReference("pothole_reports").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reportsList = mutableListOf<PotholeReport>()
                for (reportSnapshot in snapshot.children) {
                    val report = reportSnapshot.getValue(PotholeReport::class.java)
                    report?.let {
                        // Set the Firebase key as the report ID and status to "in-progress"
                        if (it.latitude != 0.0 && it.longitude != 0.0 && it.imageUrl?.isNotEmpty() == true){
                            // Set the Firebase key as the report ID and status to "in-progress"
                            val reportWithId = it.copy(id = reportSnapshot.key ?: "", status = "in-progress")
                            reportsList.add(reportWithId)
                        }


                    }
                }
                // Sort reports by severity: High → Medium → Low → Not Specified
                _reports.value = reportsList.sortedWith(compareByDescending<PotholeReport> { report ->
                    when (report.severity.lowercase(Locale.ROOT)) {
                        "high" -> 3
                        "medium" -> 2
                        "low" -> 1
                        else -> 0 // Not Specified or any other value
                    }
                })
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

    // Calculate total workers deployed based on report severity
    fun calculateWorkersDeployed(): Int {
        var requiredWorkers = 0
        for (report in _reports.value) {
            val severity = report.severity.lowercase(Locale.ROOT)
            when (severity) {
                "high" -> requiredWorkers += 3
                "medium" -> requiredWorkers += 2
                "low" -> requiredWorkers +=1
                else -> requiredWorkers += 0
            }
        }
        return requiredWorkers
    }

    // Get total available workers (not deployed, but total in database)
    fun getTotalAvailableWorkers(): Int {
        return _roleCounts.value?.workers ?: 0
    }
}

data class RoleCounts(
    @PropertyName("Officer") val officers: Int = 0,
    @PropertyName("Sub-divisional Officer (SDO)") val sdo: Int = 0,
    @PropertyName("Worker") val workers: Int = 0
)