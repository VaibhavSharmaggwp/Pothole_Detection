package com.example.driveease

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class WorkerViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance()
    private val _adminWorker = MutableLiveData<AdminWorker>()
    val adminWorker: LiveData<AdminWorker> = _adminWorker
    private val _task = MutableLiveData<PotholeReport?>()
    val task: LiveData<PotholeReport?> = _task

    private var workerListener: ValueEventListener? = null
    private var taskListener: ValueEventListener? = null
    private var currentTaskId: String? = null

    fun fetchWorkerData(email: String) {
        val workerRef = database.getReference("admins/rolesAssigned/Worker/$email")
        workerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val adminWorker = snapshot.getValue(AdminWorker::class.java) ?: return
                _adminWorker.value = adminWorker
                if (adminWorker.currentTask != null && adminWorker.currentTask != currentTaskId) {
                    fetchTaskData(adminWorker.currentTask)
                    currentTaskId = adminWorker.currentTask
                } else if (adminWorker.currentTask == null) {
                    _task.value = null
                    currentTaskId = null
                    taskListener?.let { database.getReference("pothole_reports").removeEventListener(it) }
                    taskListener = null
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        }
        workerRef.addValueEventListener(workerListener!!)
    }

    private fun fetchTaskData(taskId: String) {
        val taskRef = database.getReference("pothole_reports/$taskId")
        taskListener?.let { taskRef.removeEventListener(it) }
        taskListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val task = snapshot.getValue(PotholeReport::class.java)
                if (task != null && task.status != "completed") {
                    _task.value = task
                } else {
                    _task.value = null
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        }
        taskRef.addValueEventListener(taskListener!!)
    }

    fun updateProgress(taskId: String, currentProgress: Int) {
        val newProgress = (currentProgress + 10).coerceAtMost(100)
        val taskRef = database.getReference("pothole_reports/$taskId")
        taskRef.child("progress").setValue(newProgress)
        if (newProgress == 100) {
            taskRef.child("status").setValue("completed")
            val workerEmail = _adminWorker.value?.email
            if (workerEmail != null) {
                database.getReference("admins/rolesAssigned/Worker/$workerEmail/currentTask").setValue(null)
            }
        }
    }

    fun markCompleted(taskId: String) {
        val taskRef = database.getReference("pothole_reports/$taskId")
        taskRef.child("progress").setValue(100)
        taskRef.child("status").setValue("completed")
        val workerEmail = _adminWorker.value?.email
        if (workerEmail != null) {
            database.getReference("admins/rolesAssigned/Worker/$workerEmail/currentTask").setValue(null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        workerListener?.let { database.getReference("admins/rolesAssigned/Worker").removeEventListener(it) }
        taskListener?.let { database.getReference("pothole_reports").removeEventListener(it) }
    }
}

data class AdminWorker(
    val email: String = "",
    val name: String = "",
    val currentTask: String? = null
)

