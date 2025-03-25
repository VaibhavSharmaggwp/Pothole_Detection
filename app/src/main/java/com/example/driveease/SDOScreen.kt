
package com.example.driveease
import androidx.appcompat.app.AppCompatActivity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import kotlin.random.Random

// Data Models
data class Task(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val imageUrl: String = "",
    val description: String = "",
    val severity: String = "low",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val date: String = "",
    val time: String = "",
    val status: String = "in-progress",
    val progress: Int = 0,
    val workers: List<String> = emptyList()
)

data class Worker(
    val name: String = "",
    val status: String = "available",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val currentTask: String? = null
)

// ViewModel
class SDOViewModel : ViewModel() {
    private val _tasks = MutableLiveData<List<Task>>()
    val tasks: LiveData<List<Task>> = _tasks

    private val _workers = MutableLiveData<List<Worker>>()
    val workers: LiveData<List<Worker>> = _workers

    private val database = FirebaseDatabase.getInstance()

    init {
        fetchData()
    }

    private fun fetchData() {
        // Fetch workers from "admins/roleAssigned/Worker"
        val workersRef = database.getReference("admins/rolesAssigned/Worker")
        workersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val workersList = mutableListOf<Worker>()
                for (child in snapshot.children) {
                    val name = child.child("name").getValue(String::class.java) ?: ""
                    val lat = child.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lon = child.child("longitude").getValue(Double::class.java) ?: 0.0
                    workersList.add(
                        Worker(
                            name = name,
                            status = "available",
                            latitude = lat,
                            longitude = lon
                        )
                    )
                }
                assignTasks(workersList)
            }

            override fun onCancelled(error: DatabaseError) {
                println("Error fetching workers: ${error.message}")
            }
        })
    }

    private fun assignTasks(availableWorkers: List<Worker>) {
        val potholeReportsRef = database.getReference("pothole_reports")
        potholeReportsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tasksList = mutableListOf<Task>()
                val workersList = availableWorkers.toMutableList()

                for (report in snapshot.children) {
                    val potholeReport = report.getValue(PotholeReport::class.java)
                    if (potholeReport != null &&
                        !(potholeReport.latitude == null && potholeReport.longitude == null) &&
                        potholeReport.imageUrl != null
                    ) {
                        // Determine workers needed based on severity
                        val workersNeeded = when (potholeReport.severity) {
                            "High" -> 3
                            "Medium" -> 2
                            else -> 1
                        }

                        val assignedWorkers = mutableListOf<String>()
                        val availableCount = workersList.count { it.status == "available" }

                        val taskProgress = if (availableCount >= workersNeeded) {
                            for (i in 0 until workersNeeded) {
                                val worker = workersList.find { it.status == "available" }
                                worker?.let { w ->
                                    assignedWorkers.add(w.name)
                                    workersList[workersList.indexOf(w)] =
                                        w.copy(status = "busy", currentTask = potholeReport.id)
                                }
                            }
                            Random.nextInt(10, 100) // Some progress if workers available
                        } else {
                            0 // Zero progress if not enough workers
                        }

                        tasksList.add(
                            Task(
                                id = potholeReport.id,
                                userId = potholeReport.userId,
                                userEmail = potholeReport.userEmail,
                                imageUrl = potholeReport.imageUrl,
                                description = potholeReport.description,
                                severity = potholeReport.severity,
                                latitude = potholeReport.latitude!!,
                                longitude = potholeReport.longitude!!,
                                address = potholeReport.address,
                                date = potholeReport.date,
                                time = potholeReport.time,
                                status = if (taskProgress > 0) "in-progress" else "pending",
                                progress = taskProgress,
                                workers = assignedWorkers
                            )
                        )
                    }
                }
                _tasks.value = tasksList
                _workers.value = workersList
            }

            override fun onCancelled(error: DatabaseError) {
                println("Error fetching pothole reports: ${error.message}")
            }
        })
    }

    fun updateTaskProgress(taskId: String, newProgress: Int) {
        val currentTasks = _tasks.value?.toMutableList() ?: mutableListOf()
        val taskIndex = currentTasks.indexOfFirst { it.id == taskId }
        if (taskIndex != -1) {
            val updatedTask = currentTasks[taskIndex].copy(
                progress = newProgress.coerceIn(0, 100)
            )
            currentTasks[taskIndex] = updatedTask
            _tasks.value = currentTasks
        }
    }

    fun markTaskCompleted(taskId: String) {
        val currentTasks = _tasks.value?.toMutableList() ?: mutableListOf()
        val taskIndex = currentTasks.indexOfFirst { it.id == taskId }
        if (taskIndex != -1) {
            val updatedTask = currentTasks[taskIndex].copy(
                status = "completed",
                progress = 100
            )
            currentTasks[taskIndex] = updatedTask
            _tasks.value = currentTasks

            // Update workers
            val currentWorkers = _workers.value?.toMutableList() ?: mutableListOf()
            currentWorkers.forEachIndexed { index, worker ->
                if (worker.currentTask == taskId) {
                    currentWorkers[index] = worker.copy(
                        status = "available",
                        currentTask = null
                    )
                }
            }
            _workers.value = currentWorkers
        }
    }
}

// Screen and UI Components
class SDOScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SDOComposeUI()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SDOComposeUI() {
    val viewModel: SDOViewModel = viewModel()
    val tasks by viewModel.tasks.observeAsState(initial = emptyList())
    val workers by viewModel.workers.observeAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SDO Work Management") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A5276))
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Compact Sidebar
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .padding(8.dp)
            ) {
                StatsCard(tasks, workers)
                Spacer(modifier = Modifier.height(8.dp))
                WorkerAvailability(workers = workers)
            }

            // Main content area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                // Map Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.LightGray.copy(alpha = 0.2f))
                ) {
                    MapContainer(workers = workers, tasks = tasks)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Assigned Tasks Section (Expands to fill remaining space)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Text(
                        "Assigned Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        TaskList(
                            tasks = tasks,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatsCard(tasks: List<Task>, workers: List<Worker>) {
    val assignedTasksCount = tasks.count { it.status == "in-progress" || it.status == "pending" }
    val completedTasksCount = tasks.count { it.status == "completed" }
    val availableWorkersCount = workers.count { it.status == "available" }
    val totalWorkersCount = workers.size

    val totalTime = tasks.sumOf {
        when (it.severity) {
            "High" -> 24.0
            "Medium" -> 12.0
            else -> 6.0
        }
    }
    val avgTime = if (tasks.isNotEmpty()) totalTime / tasks.size else 0.0
    val avgHours = avgTime.toInt()
    val avgMinutes = ((avgTime - avgHours) * 60).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A5276)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Team Statistics",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Assigned Tasks: $assignedTasksCount", color = Color.White)
            Text("Completed: $completedTasksCount", color = Color.White)
            Text("Workers available: $availableWorkersCount/ $totalWorkersCount", color = Color.White)
            Text("Avg. Completion Time: ${avgHours}h ${avgMinutes}m", color = Color.White)
        }
    }
}

@Composable
fun WorkerAvailability(workers: List<Worker>) {
    Column {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
        ) {
            items(workers) { worker ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(getStatusColor(worker.status), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(worker.name)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (worker.currentTask != null) "Task #${worker.currentTask}" else "Available",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Divider(color = Color.LightGray, thickness = 1.dp)
            }
        }
    }
}

@Composable
fun MapContainer(workers: List<Worker>, tasks: List<Task>) {
    val context = LocalContext.current
    Configuration.getInstance().userAgentValue = context.packageName

    AndroidView(
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                minZoomLevel = 3.0
                maxZoomLevel = 19.0

                // Add rotation gesture
                val rotationGestureOverlay = RotationGestureOverlay(this)
                overlays.add(rotationGestureOverlay)
            }
        },
        update = { mapView ->
            // Clear existing overlays
            mapView.overlays.clear()

            // Collect all points to create a comprehensive view
            val allPoints = mutableListOf<GeoPoint>()

            // Add ALL tasks as markers, regardless of worker availability
            tasks.forEach { task ->
                if (task.latitude != 0.0 && task.longitude != 0.0) {
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(task.latitude, task.longitude)
                        title = "Pothole: ${task.description.take(20)}..."

                        // Customize marker based on task status and severity
                        snippet = "Severity: ${task.severity} | Progress: ${task.progress}%"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                        // Different icons for different severities
                        icon = when (task.severity) {
                            "High" -> ContextCompat.getDrawable(context, R.drawable.ic_baseline_warning_24_red)
                            "Medium" -> ContextCompat.getDrawable(context, R.drawable.ic_baseline_warning_24_orange)
                            else -> ContextCompat.getDrawable(context, R.drawable.ic_baseline_warning_24)
                        }
                    }
                    mapView.overlays.add(marker)
                    allPoints.add(GeoPoint(task.latitude, task.longitude))
                }
            }

            // Add worker markers
            workers.forEach { worker ->
                if (worker.latitude != 0.0 && worker.longitude != 0.0) {
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(worker.latitude, worker.longitude)
                        title = "Worker: ${worker.name}"
                        snippet = "Status: ${worker.status}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_person_pin_24)
                    }
                    mapView.overlays.add(marker)
                    allPoints.add(GeoPoint(worker.latitude, worker.longitude))
                }
            }

            // Adjust map view to show all points
            if (allPoints.isNotEmpty()) {
                val boundingBox = org.osmdroid.util.BoundingBox(
                    allPoints.maxOf { it.latitude },
                    allPoints.maxOf { it.longitude },
                    allPoints.minOf { it.latitude },
                    allPoints.minOf { it.longitude }
                )
                mapView.zoomToBoundingBox(boundingBox, true, 50)
            } else {
                mapView.controller.setZoom(12.0)
                mapView.controller.setCenter(GeoPoint(0.0, 0.0))
            }

            mapView.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun TaskList(
    tasks: List<Task>,
    viewModel: SDOViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(tasks) { task ->
            TaskItem(task, viewModel)
        }
    }
}

@Composable
fun TaskItem(task: Task, viewModel: SDOViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Task #${task.id}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Severity: ${task.severity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Text(task.address, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = task.progress / 100f,
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp),
                    color = getProgressColor(task.progress),
                    trackColor = Color.LightGray
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    "${task.progress}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Buttons and Deadline in Vertical Alignment
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Button(
                    onClick = { viewModel.updateTaskProgress(task.id, task.progress + 10) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))
                ) {
                    Text("+10%", color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.markTaskCompleted(task.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2980B9))
                ) {
                    Text("Mark Completed", color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Deadline: ${task.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

// Utility Functions
@Composable
fun getStatusColor(status: String): Color {
    return when (status) {
        "available" -> Color(0xFF2ECC71)
        "busy" -> Color(0xFFE67E22)
        else -> Color(0xFF95A5A6)
    }
}

fun getProgressColor(progress: Int): Color {
    return when {
        progress < 30 -> Color.Red
        progress < 60 -> Color.Yellow
        progress < 90 -> Color(0xFF27AE60)
        else -> Color.Green
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSDOStatsCard() {
    val sampleTasks = listOf(
        Task(id = "1", severity = "High", progress = 30),
        Task(id = "2", severity = "Medium", progress = 60),
        Task(id = "3", severity = "Low", progress = 90, status = "completed")
    )
    val sampleWorkers = listOf(
        Worker(name = "John Doe", status = "available"),
        Worker(name = "Jane Smith", status = "busy"),
        Worker(name = "Mike Johnson", status = "available")
    )
    StatsCard(tasks = sampleTasks, workers = sampleWorkers)
}

@Preview(showBackground = true)
@Composable
fun PreviewWorkerAvailability() {
    val sampleWorkers = listOf(
        Worker(name = "John Doe", status = "available"),
        Worker(name = "Jane Smith", status = "busy", currentTask = "1"),
        Worker(name = "Mike Johnson", status = "available"),
        Worker(name = "Sarah Williams", status = "busy", currentTask = "2")
    )
    WorkerAvailability(workers = sampleWorkers)
}
