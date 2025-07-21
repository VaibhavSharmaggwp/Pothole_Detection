package com.example.driveease

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.database.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

// Constants for map defaults
private val INDIA_CENTER = GeoPoint(20.5937, 78.9629)
private const val DEFAULT_ZOOM = 7.0

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
    val status: String = "pending",
    val progress: Int = 0,
    val completedPhotoUrl: String? = null,
    val workers: List<String> = emptyList() // List of worker UIDs
)

data class Worker(
    val name: String = "",
    val uid: String = "", // Changed from email to uid for consistency with Firebase UID
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

    // Map to convert worker UIDs to names for display
    private val _workerUidToName = MutableLiveData<Map<String, String>>()
    val workerUidToName: LiveData<Map<String, String>> = _workerUidToName

    private val database = FirebaseDatabase.getInstance()
    private val potholeReportsRef = database.getReference("pothole_reports")
    private val workersRef = database.getReference("admins/rolesAssigned/Worker")

    private var isListening = false

    init {
        fetchData()
    }

    private fun fetchData() {
        if (isListening) return
        isListening = true

        // Fetch workers from "admins/rolesAssigned/Worker"
        workersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val workersList = mutableListOf<Worker>()
                for (child in snapshot.children) {
                    val name = child.child("name").getValue(String::class.java) ?: ""
                    val uid = child.key ?: "" // UID is the key in Firebase
                    val lat = child.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lon = child.child("longitude").getValue(Double::class.java) ?: 0.0
                    workersList.add(
                        Worker(
                            name = name,
                            uid = uid,
                            status = "available",
                            latitude = lat,
                            longitude = lon
                        )
                    )
                }
                _workers.value = workersList
                // Create UID-to-name map for UI display
                _workerUidToName.value = workersList.associate { it.uid to it.name }
                fetchTasks()
            }

            override fun onCancelled(error: DatabaseError) {
                _workers.value = emptyList()
                _workerUidToName.value = emptyMap()
            }
        })
    }

    private fun fetchTasks() {
        potholeReportsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tasksList = mutableListOf<Task>()
                for (report in snapshot.children) {
                    try {
                        val task = Task(
                            id = report.key ?: "",
                            userId = report.child("userId").getValue(String::class.java) ?: "",
                            userEmail = report.child("userEmail").getValue(String::class.java) ?: "",
                            imageUrl = report.child("imageUrl").getValue(String::class.java) ?: "",
                            description = report.child("description").getValue(String::class.java) ?: "",
                            severity = report.child("severity").getValue(String::class.java) ?: "low",
                            latitude = report.child("latitude").getValue(Double::class.java) ?: 0.0,
                            longitude = report.child("longitude").getValue(Double::class.java) ?: 0.0,
                            address = report.child("address").getValue(String::class.java) ?: "",
                            date = report.child("date").getValue(String::class.java) ?: "",
                            time = report.child("time").getValue(String::class.java) ?: "",
                            status = report.child("status").getValue(String::class.java) ?: "pending",
                            progress = report.child("progress").getValue(Int::class.java) ?: 0,
                            workers = report.child("workerAssigned").children.mapNotNull {
                                it.getValue(String::class.java)
                            } // Worker UIDs
                        )
                        if (task.latitude != 0.0 && task.longitude != 0.0 && task.imageUrl.isNotEmpty()) {
                            tasksList.add(task)
                        }
                    } catch (e: Exception) {
                        println("Error processing report ${report.key}: ${e.message}")
                    }
                }
                _tasks.value = tasksList.sortedWith(
                    compareByDescending<Task> {
                        when (it.severity) {
                            "High" -> 2
                            "Medium" -> 1
                            else -> 0
                        }
                    }.thenBy { it.date }
                )
                assignWorkersToPendingTasks()
            }

            override fun onCancelled(error: DatabaseError) {
                _tasks.value = emptyList()
            }
        })
    }

    private fun assignWorkersToPendingTasks() {
        val currentTasks = _tasks.value?.toMutableList() ?: return
        val currentWorkers = _workers.value?.toMutableList() ?: return

        val pendingTasks = currentTasks.filter { it.status == "pending" && it.workers.isEmpty() }
            .sortedWith(
                compareByDescending<Task> {
                    when (it.severity) {
                        "High" -> 2
                        "Medium" -> 1
                        else -> 0
                    }
                }.thenBy { it.date }
            )

        for (task in pendingTasks) {
            val workersNeeded = 1 // Only one worker per task
            val availableWorkers = currentWorkers.filter { it.status == "available" }
            if (availableWorkers.isNotEmpty()) {
                val assignableWorker = availableWorkers.first()
                val assignedWorkerUid = listOf(assignableWorker.uid)

                // Update task in Firebase with worker UID and currently_working
                potholeReportsRef.child(task.id).updateChildren(
                    mapOf(
                        "workerAssigned" to assignedWorkerUid,
                        "currently_working" to assignableWorker.uid, // Track current worker
                        "status" to "in-progress",
                        "progress" to 0
                    )
                )

                // Update local task
                val taskIndex = currentTasks.indexOf(task)
                currentTasks[taskIndex] = task.copy(
                    status = "in-progress",
                    progress = 0,
                    workers = assignedWorkerUid
                )

                // Mark worker as busy
                val workerIndex = currentWorkers.indexOf(assignableWorker)
                currentWorkers[workerIndex] = assignableWorker.copy(
                    status = "busy",
                    currentTask = task.id
                )
            }
        }

        _tasks.value = currentTasks
        _workers.value = currentWorkers
    }

    fun updateTaskProgress(taskId: String, newProgress: Int) {
        val currentTasks = _tasks.value?.toMutableList() ?: return
        val taskIndex = currentTasks.indexOfFirst { it.id == taskId }
        if (taskIndex != -1) {
            val updatedTask = currentTasks[taskIndex].copy(progress = newProgress.coerceIn(0, 100))
            currentTasks[taskIndex] = updatedTask
            _tasks.value = currentTasks
            potholeReportsRef.child(taskId).child("progress").setValue(newProgress)
        }
    }

    fun markTaskCompleted(taskId: String) {
        val currentTasks = _tasks.value?.toMutableList() ?: return
        val taskIndex = currentTasks.indexOfFirst { it.id == taskId }
        if (taskIndex != -1) {
            val updatedTask = currentTasks[taskIndex].copy(
                status = "completed",
                progress = 100
            )
            currentTasks[taskIndex] = updatedTask
            _tasks.value = currentTasks

            // Update Firebase
            potholeReportsRef.child(taskId).updateChildren(
                mapOf(
                    "status" to "completed",
                    "progress" to 100,
                    "currently_working" to null // Clear currently_working when completed
                )
            )

            // Free up worker
            val currentWorkers = _workers.value?.toMutableList() ?: return
            currentWorkers.forEachIndexed { index, worker ->
                if (worker.currentTask == taskId) {
                    currentWorkers[index] = worker.copy(
                        status = "available",
                        currentTask = null
                    )
                }
            }
            _workers.value = currentWorkers

            // Reassign available workers to new tasks
            assignWorkersToPendingTasks()
        }
    }

    override fun onCleared() {
        isListening = false
        super.onCleared()
    }
}

// Custom Theme
@Composable
fun DriveEaseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1A5276),
            secondary = Color(0xFF27AE60),
            tertiary = Color(0xFF003366),
            background = Color(0xFFF6F6F6),
            surface = Color.White
        ),
        typography = Typography(
            displayLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp
            ),
            headlineLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            ),
            labelLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        ),
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}

// Activity
class SDOScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DriveEaseTheme {
                SDOComposeUI()
            }
        }
    }
}

// Main UI Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SDOComposeUI() {
    val viewModel: SDOViewModel = viewModel()
    val tasks by viewModel.tasks.observeAsState(emptyList())
    val workers by viewModel.workers.observeAsState(emptyList())
    val workerUidToName by viewModel.workerUidToName.observeAsState(emptyMap())
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var taskClickCount by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DriveEase - SDO Dashboard",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            StatsOverview(tasks = tasks, workers = workers)
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.large)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        MaterialTheme.shapes.large
                    )
            ) {
                MapContainer(
                    workers = workers,
                    tasks = tasks,
                    selectedTaskId = selectedTaskId,
                    taskClickCount = taskClickCount,
                    onTaskSelected = { selectedTaskId = null }
                )
                IconButton(
                    onClick = { /* TODO: Navigate to full-screen map */ },
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Expand Map",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Assigned Tasks",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            TaskList(
                tasks = tasks,
                viewModel = viewModel,
                workerUidToName = workerUidToName,
                selectedTaskId = selectedTaskId,
                onTaskClick = { taskId ->
                    selectedTaskId = taskId
                    taskClickCount++
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Stats Overview Composable
@Composable
fun StatsOverview(tasks: List<Task>, workers: List<Worker>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(
            title = "Assigned",
            value = tasks.count { it.status != "completed" }.toString()
        )
        StatItem(
            title = "Completed",
            value = tasks.count { it.status == "completed" }.toString()
        )
        StatItem(
            title = "Workers Available",
            value = workers.count { it.status == "available" }.toString()
        )
    }
}

@Composable
fun StatItem(title: String, value: String) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .width(100.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

// Map Container Composable
@Composable
fun MapContainer(
    workers: List<Worker>,
    tasks: List<Task>,
    selectedTaskId: String?,
    taskClickCount: Int,
    onTaskSelected: (String?) -> Unit
) {
    val context = LocalContext.current
    Configuration.getInstance().userAgentValue = context.packageName
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            overlays.add(RotationGestureOverlay(this))
        }
    }

    val markerClickListener = object : Marker.OnMarkerClickListener {
        override fun onMarkerClick(marker: Marker, mapView: MapView): Boolean {
            val task = marker.relatedObject as? Task
            task?.let {
                val intent = Intent(context, AdminReportActivity::class.java).apply {
                    putExtra("TASK_ID", it.id)
                }
                context.startActivity(intent)
                return true
            }
            return false
        }
    }

    LaunchedEffect(selectedTaskId, taskClickCount) {
        if (selectedTaskId != null) {
            tasks.find { it.id == selectedTaskId }?.let { task ->
                if (task.latitude != 0.0 && task.longitude != 0.0) {
                    mapView.controller.animateTo(
                        GeoPoint(task.latitude, task.longitude),
                        16.0,
                        1500L
                    )
                }
            }
        } else {
            val allPoints = mutableListOf<GeoPoint>().apply {
                addAll(tasks.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                    .map { GeoPoint(it.latitude, it.longitude) })
                addAll(workers.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                    .map { GeoPoint(it.latitude, it.longitude) })
            }
            if (allPoints.isNotEmpty()) {
                val boundingBox = org.osmdroid.util.BoundingBox(
                    allPoints.maxOf { it.latitude },
                    allPoints.maxOf { it.longitude },
                    allPoints.minOf { it.latitude },
                    allPoints.minOf { it.longitude }
                )
                mapView.zoomToBoundingBox(boundingBox, true, 100)
            } else {
                mapView.controller.setCenter(INDIA_CENTER)
                mapView.controller.setZoom(DEFAULT_ZOOM)
            }
        }
    }

    AndroidView(
        factory = { mapView },
        update = { view ->
            view.overlays.clear()
            tasks.filter { it.latitude != 0.0 && it.longitude != 0.0 }.forEach { task ->
                Marker(view).apply {
                    position = GeoPoint(task.latitude, task.longitude)
                    title = "Pothole: ${task.description.take(20)}..."
                    snippet = "Severity: ${task.severity} | Progress: ${task.progress}%"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = when (task.severity) {
                        "High" -> ContextCompat.getDrawable(context, R.drawable.ic_baseline_warning_24_red)
                        "Medium" -> ContextCompat.getDrawable(context, R.drawable.ic_baseline_warning_24_orange)
                        else -> ContextCompat.getDrawable(context, R.drawable.ic_baseline_warning_24)
                    }
                    relatedObject = task
                    setOnMarkerClickListener(markerClickListener)
                }.also { view.overlays.add(it) }
            }
            workers.filter { it.latitude != 0.0 && it.longitude != 0.0 }.forEach { worker ->
                Marker(view).apply {
                    position = GeoPoint(worker.latitude, worker.longitude)
                    title = "Worker: ${worker.name}"
                    snippet = "Status: ${worker.status}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_person_pin_24)
                }.also { view.overlays.add(it) }
            }
            view.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

// Task List Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskList(
    tasks: List<Task>,
    viewModel: SDOViewModel,
    workerUidToName: Map<String, String>, // Added to map UIDs to names
    selectedTaskId: String?,
    onTaskClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCompleted by remember { mutableStateOf(false) }
    val (activeTasks, completedTasks) = remember(tasks) {
        tasks.partition { it.status != "completed" }
    }

    val sortedActiveTasks = remember(activeTasks) {
        activeTasks.sortedWith(
            compareByDescending<Task> {
                when (it.severity) {
                    "High" -> 2
                    "Medium" -> 1
                    else -> 0
                }
            }.thenBy { it.date }
        )
    }

    val sortedCompletedTasks = remember(completedTasks) {
        completedTasks.sortedByDescending { it.date }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Tasks (${sortedActiveTasks.size + if (showCompleted) sortedCompletedTasks.size else 0})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            FilterChip(
                selected = showCompleted,
                onClick = { showCompleted = !showCompleted },
                label = { Text("Show Completed") }
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (sortedActiveTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Active Tasks (${sortedActiveTasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(sortedActiveTasks) { task ->
                    TaskItem(
                        task = task,
                        viewModel = viewModel,
                        workerUidToName = workerUidToName,
                        isSelected = task.id == selectedTaskId,
                        onClick = { onTaskClick(task.id) }
                    )
                }
            }
            if (showCompleted && sortedCompletedTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Completed Tasks (${sortedCompletedTasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(sortedCompletedTasks) { task ->
                    TaskItem(
                        task = task,
                        viewModel = viewModel,
                        workerUidToName = workerUidToName,
                        isSelected = task.id == selectedTaskId,
                        onClick = { onTaskClick(if (task.id == selectedTaskId) "" else task.id) }
                    )
                }
            }
            if (sortedActiveTasks.isEmpty() && (sortedCompletedTasks.isEmpty() || !showCompleted)) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// Task Item Composable
@Composable
fun TaskItem(
    task: Task,
    viewModel: SDOViewModel,
    workerUidToName: Map<String, String>, // Added to display worker names
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Task #${task.id}",
                    style = MaterialTheme.typography.headlineLarge
                )
                Box(
                    modifier = Modifier
                        .background(getSeverityColor(task.severity), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        task.severity,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
            Text(
                text = task.address.ifEmpty { "Coordinates: ${"%.4f".format(task.latitude)}, ${"%.4f".format(task.longitude)}" },
                style = MaterialTheme.typography.bodyLarge
            )
            val timeout = 8
            Spacer(modifier = Modifier.height(timeout.dp))

            // Display worker name instead of UID
            if (task.workers.isNotEmpty()) {
                val workerNames = task.workers.mapNotNull { workerUidToName[it] }.joinToString(", ")
                Text(
                    "Assigned Worker: $workerNames",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            } else {
                Text(
                    "No worker assigned",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = task.progress / 100f,
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = getProgressColor(task.progress),
                    trackColor = Color.LightGray
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${task.progress}%",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.updateTaskProgress(task.id, (task.progress + 10).coerceAtMost(100)) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    enabled = task.status != "completed"
                ) {
                    Text("+10%", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
                Button(
                    onClick = { viewModel.markTaskCompleted(task.id) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = task.status != "completed"
                ) {
                    Text("Mark Completed", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
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

// Utility Functions
fun getSeverityColor(severity: String): Color {
    return when (severity.lowercase()) {
        "high" -> Color(0xFFE74C3C)
        "medium" -> Color(0xFFF39C12)
        "low" -> Color(0xFF2ECC71)
        else -> Color.Gray
    }
}

fun getProgressColor(progress: Int): Color {
    return when {
        progress < 30 -> Color(0xFFF39C12)
        progress < 60 -> Color(0xFF3498DB)
        else -> Color(0xFF2ECC71)
    }
}