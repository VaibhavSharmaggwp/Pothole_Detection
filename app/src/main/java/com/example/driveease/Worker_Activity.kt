package com.example.driveease

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// ViewModel
class WorkerViewModel(private val workerUid: String) : ViewModel() {
    private val database = FirebaseDatabase.getInstance()
    private val tasksRef = database.getReference("pothole_reports")
    private val storageRef = FirebaseStorage.getInstance().getReference("completed_tasks")

    private val _tasks = MutableLiveData<List<Task>>(emptyList())
    val tasks: LiveData<List<Task>> = _tasks

    private val _selectedTask = MutableLiveData<Task?>(null)
    val selectedTask: LiveData<Task?> = _selectedTask

    private var isListening = false

    init {
        fetchTasks()
    }

    private fun fetchTasks() {
        if (isListening) return
        isListening = true

        // Fetch tasks where currently_working matches worker's UID
        tasksRef.orderByChild("currently_working").equalTo(workerUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val taskList = snapshot.children.mapNotNull { data ->
                        Task(
                            id = data.key ?: "",
                            userId = data.child("userId").getValue(String::class.java) ?: "",
                            userEmail = data.child("userEmail").getValue(String::class.java) ?: "",
                            imageUrl = data.child("imageUrl").getValue(String::class.java) ?: "",
                            description = data.child("description").getValue(String::class.java) ?: "",
                            severity = data.child("severity").getValue(String::class.java) ?: "low",
                            latitude = data.child("latitude").getValue(Double::class.java) ?: 0.0,
                            longitude = data.child("longitude").getValue(Double::class.java) ?: 0.0,
                            address = data.child("address").getValue(String::class.java) ?: "",
                            date = data.child("date").getValue(String::class.java) ?: "",
                            time = data.child("time").getValue(String::class.java) ?: "",
                            status = data.child("status").getValue(String::class.java) ?: "in-progress",
                            progress = data.child("progress").getValue(Int::class.java) ?: 0,
                            workers = data.child("workerAssigned").children.mapNotNull {
                                it.getValue(String::class.java)
                            },
                            completedPhotoUrl = data.child("completedPhotoUrl").getValue(String::class.java)
                        )
                    }
                    _tasks.value = taskList
                    _selectedTask.value = taskList.firstOrNull { it.status != "completed" }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })

        // Fetch completed tasks separately
        tasksRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val completedTasks = snapshot.children.mapNotNull { data ->
                    val workers = data.child("workerAssigned").children.mapNotNull {
                        it.getValue(String::class.java)
                    }
                    if (workerUid in workers && data.child("status").getValue(String::class.java) == "completed") {
                        Task(
                            id = data.key ?: "",
                            userId = data.child("userId").getValue(String::class.java) ?: "",
                            userEmail = data.child("userEmail").getValue(String::class.java) ?: "",
                            imageUrl = data.child("imageUrl").getValue(String::class.java) ?: "",
                            description = data.child("description").getValue(String::class.java) ?: "",
                            severity = data.child("severity").getValue(String::class.java) ?: "low",
                            latitude = data.child("latitude").getValue(Double::class.java) ?: 0.0,
                            longitude = data.child("longitude").getValue(Double::class.java) ?: 0.0,
                            address = data.child("address").getValue(String::class.java) ?: "",
                            date = data.child("date").getValue(String::class.java) ?: "",
                            time = data.child("time").getValue(String::class.java) ?: "",
                            status = data.child("status").getValue(String::class.java) ?: "completed",
                            progress = data.child("progress").getValue(Int::class.java) ?: 100,
                            workers = workers,
                            completedPhotoUrl = data.child("completedPhotoUrl").getValue(String::class.java)
                        )
                    } else null
                }
                _tasks.value = (_tasks.value ?: emptyList()) + completedTasks
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    fun markTaskComplete(taskId: String, photoUri: Uri, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val photoRef = storageRef.child("$taskId/${System.currentTimeMillis()}.jpg")
        photoRef.putFile(photoUri).addOnSuccessListener {
            photoRef.downloadUrl.addOnSuccessListener { uri ->
                tasksRef.child(taskId).updateChildren(
                    mapOf(
                        "completedPhotoUrl" to uri.toString(),
                        "status" to "completed",
                        "progress" to 100,
                        "currently_working" to null
                    )
                )
                onSuccess()
            }
        }.addOnFailureListener {
            onFailure(it.message ?: "Upload failed")
        }
    }

    override fun onCleared() {
        isListening = false
        super.onCleared()
    }
}

class WorkerViewModelFactory(private val workerUid: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkerViewModel::class.java)) {
            return WorkerViewModel(workerUid) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Activity
class Worker_Activity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var workerUid: String
    private lateinit var workerName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        workerUid = auth.currentUser?.uid ?: ""

        if (workerUid.isEmpty()) {
            finish()
            return
        }

        FirebaseDatabase.getInstance().getReference("admins/rolesAssigned/Worker")
            .child(workerUid).child("name").get()
            .addOnSuccessListener { snapshot ->
                workerName = snapshot.getValue(String::class.java) ?: "Unknown Worker"
                setContent {
                    DriveEaseTheme {
                        WorkerScreen(workerUid = workerUid, workerName = workerName)
                    }
                }
            }.addOnFailureListener {
                workerName = "Unknown Worker"
                setContent {
                    DriveEaseTheme {
                        WorkerScreen(workerUid = workerUid, workerName = workerName)
                    }
                }
            }
    }
}

// Main UI Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerScreen(workerUid: String, workerName: String) {
    val viewModel: WorkerViewModel = viewModel(factory = WorkerViewModelFactory(workerUid))
    val tasks by viewModel.tasks.observeAsState(emptyList())
    val selectedTask by viewModel.selectedTask.observeAsState(null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Worker Dashboard",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = workerName.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Enhanced Worker Profile Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = workerName.take(2).uppercase(),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = workerName,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "UID: $workerUid",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedTask != null) {
                TaskSection(viewModel = viewModel, task = selectedTask)
            } else {
                NoTaskSection()
            }

            if (tasks.any { it.status == "completed" }) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Completed Tasks",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(tasks.filter { it.status == "completed" }) { task ->
                        HistoryItem(task)
                    }
                }
            }
        }
    }
}

@Composable
fun TaskSection(viewModel: WorkerViewModel, task: Task?) {
    task?.let {
        val context = LocalContext.current
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                TaskContent(
                    task = it,
                    onComplete = { uri ->
                        viewModel.markTaskComplete(
                            task.id,
                            uri,
                            onSuccess = { },
                            onFailure = { }
                        )
                    },
                    onNavigate = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${task.latitude},${task.longitude}&travelmode=driving"))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun NoTaskSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F7FA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Tasks Assigned",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "You'll be notified when a new task is assigned.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MapViewContainer(task: Task?) {
    val context = LocalContext.current
    Configuration.getInstance().userAgentValue = context.packageName

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 10.0
            maxZoomLevel = 18.0
        }
    }

    LaunchedEffect(task) {
        task?.let {
            val geoPoint = GeoPoint(it.latitude, it.longitude)
            mapView.controller.setZoom(16.0)
            mapView.controller.setCenter(geoPoint)
            val marker = Marker(mapView).apply {
                position = geoPoint
                title = "Task #${it.id}"
                snippet = it.address
            }
            mapView.overlays.clear()
            mapView.overlays.add(marker)
            mapView.invalidate()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
    )
}

@Composable
fun TaskContent(
    task: Task,
    onComplete: (Uri) -> Unit,
    onNavigate: () -> Unit
) {
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoPicker by remember { mutableStateOf(false) }
    var uploadMessage by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        photoUri = uri
        if (uri != null) {
            onComplete(uri)
            photoUri = null
        }
        showPhotoPicker = false
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Task #${task.id}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Box(
                modifier = Modifier
                    .background(getSeverityColor(task.severity), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = task.severity.uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        MapViewContainer(task = task)

        Spacer(modifier = Modifier.height(16.dp))

        DetailRow(label = "Status", value = task.status.uppercase(), statusColor(task.status))
        DetailRow(label = "Location", value = task.address)
        DetailRow(label = "Coordinates", value = "${"%.4f".format(task.latitude)}, ${"%.4f".format(task.longitude)}")
        DetailRow(label = "Assigned", value = task.date)
        DetailRow(label = "Description", value = task.description)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Progress: ${task.progress}%",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        LinearProgressIndicator(
            progress = task.progress / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = Color.LightGray
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onNavigate,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Directions, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Navigate", color = MaterialTheme.colorScheme.primary)
            }
            Button(
                onClick = {
                    if (task.progress >= 50) {
                        showPhotoPicker = true
                        uploadMessage = "Please select a photo to complete the task"
                    } else {
                        uploadMessage = "Progress must be at least 50% to complete"
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    disabledContainerColor = Color.Gray
                ),
                enabled = task.status != "completed"
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Complete")
            }
        }

        if (showPhotoPicker) {
            LaunchedEffect(Unit) {
                launcher.launch("image/*")
            }
        }

        uploadMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = if (it.contains("failed")) Color.Red else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, badgeColor: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(100.dp)
        )
        if (badgeColor != null) {
            Box(
                modifier = Modifier
                    .background(badgeColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun HistoryItem(task: Task) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Task #${task.id}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = task.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Completed on ${task.date}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            task.completedPhotoUrl?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "View Photo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        // TODO: Implement photo viewing (e.g., open in browser or dialog)
                    }
                )
            }
        }
    }
}

fun statusColor(status: String): Color {
    return when (status.lowercase()) {
        "pending" -> Color(0xFF95A5A6)
        "in-progress" -> Color(0xFF3498DB)
        "completed" -> Color(0xFF2ECC71)
        else -> Color.Gray
    }
}