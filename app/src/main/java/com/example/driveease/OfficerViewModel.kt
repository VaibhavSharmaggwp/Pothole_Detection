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
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)

    val reports = _reports.asStateFlow()
    val roleCounts = _roleCounts.asStateFlow()
    val authState = _authState.asStateFlow()

    sealed class AuthState {
        object Initial : AuthState()
        object Authenticated : AuthState()
        object Unauthenticated : AuthState()
    }

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        if (auth.currentUser != null) {
            _authState.value = AuthState.Authenticated
            loadReports()
            loadRoleCounts()
        } else {
            _authState.value = AuthState.Unauthenticated
            _reports.value = emptyList()
            _roleCounts.value = null
        }
    }

    private fun loadReports() {
        database.getReference("pothole_reports").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reportsList = mutableListOf<PotholeReport>()
                for (reportSnapshot in snapshot.children) {
                    try {
                        val report = reportSnapshot.getValue(PotholeReport::class.java)
                        report?.let {
                            if (it.latitude != null && it.longitude != null && it.latitude != 0.0 && it.longitude != 0.0 && !it.imageUrl.isNullOrEmpty()) {
                                val reportWithId = it.copy(id = reportSnapshot.key ?: "", status = "in-progress")
                                reportsList.add(reportWithId)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                _reports.value = reportsList.sortedWith(compareByDescending { report ->
                    when (report.severity.lowercase(Locale.ROOT)) {
                        "high" -> 3
                        "medium" -> 2
                        "low" -> 1
                        else -> 0
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                _reports.value = emptyList()
                println("Firebase error: ${error.message}")
            }
        })
    }

    private fun loadRoleCounts() {
        database.getReference("admins/role_counts").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    _roleCounts.value = snapshot.getValue(RoleCounts::class.java)
                } catch (e: Exception) {
                    _roleCounts.value = null
                    e.printStackTrace()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _roleCounts.value = null
                println("Firebase error: ${error.message}")
            }
        })
    }

    fun calculateWorkersDeployed(): Int {
        return _reports.value.sumOf { report: PotholeReport ->
            when (report.severity.lowercase(Locale.ROOT)) {
                "high" -> 3
                "medium" -> 2
                "low" -> 1
                else -> 0
            } as Int
        }
    }

    fun getTotalAvailableWorkers(): Int {
        return _roleCounts.value?.workers ?: 0
    }

    fun retryAuthCheck() {
        checkAuthState()
    }
}

data class RoleCounts(
    @PropertyName("Officer") val officers: Int = 0,
    @PropertyName("Sub-divisional Officer (SDO)") val sdo: Int = 0,
    @PropertyName("Worker") val workers: Int = 0
)