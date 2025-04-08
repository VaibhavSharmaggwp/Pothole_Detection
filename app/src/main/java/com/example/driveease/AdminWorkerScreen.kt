package com.example.driveease

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.livedata.observeAsState
import coil.compose.AsyncImage
import com.example.driveease.databinding.ActivityAdminWorkerScreenBinding
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import kotlin.random.Random

class AdminWorkerScreen : AppCompatActivity() {
    private lateinit var binding: ActivityAdminWorkerScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminWorkerScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val workerEmail = intent.getStringExtra("WORKER_EMAIL") ?: ""

        binding.composeWorkerView.setContent {
            DriveEaseTheme {
                WorkerScreen(workerEmail = workerEmail)
            }
        }
    }
}

@Composable
fun WorkerScreen(workerEmail: String) {
    val viewModel: WorkerViewModel = viewModel()
    LaunchedEffect(workerEmail) {
        viewModel.fetchWorkerData(workerEmail)
    }
    val adminWorker by viewModel.adminWorker.observeAsState()
    val task by viewModel.task.observeAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        adminWorker?.let { WorkerHeader(worker = it) }
        if (task == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No active task assigned",
                    style = MaterialTheme.typography.h5,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            task?.let {
                MapView(task = it)
                TaskCard(task = it, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun WorkerHeader(worker: AdminWorker) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.primary)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF3498DB)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = worker.name.firstOrNull()?.toString() ?: "",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = worker.name,
                color = Color.White,
                style = MaterialTheme.typography.h6
            )
            Text(
                text = "Worker Email: ${worker.email}",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.body2
            )
        }
    }
}

@Composable
fun MapView(task: PotholeReport) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            overlays.add(RotationGestureOverlay(this))
        }
    }

    LaunchedEffect(task) {
        mapView.controller.setCenter(task.latitude?.let { lat ->
            task.longitude?.let { lon ->
                GeoPoint(lat, lon)
            }
        })
        mapView.controller.setZoom(16.0)
        mapView.overlays.clear()
        val marker = Marker(mapView).apply {
            position = task.latitude?.let { lat ->
                task.longitude?.let { lon ->
                    GeoPoint(lat, lon)
                }
            }
            title = "Task Location: ${task.address}"
        }
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}

@Composable
fun TaskCard(task: PotholeReport, viewModel: WorkerViewModel) {
    val context = LocalContext.current
    // Generate a random progress value (0-100) for display purposes
    val randomProgress = remember { Random.nextInt(0, 101) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Task #${task.id}", style = MaterialTheme.typography.h6)
                Box(
                    modifier = Modifier
                        .background(
                            color = getSeverityColor(task.severity),
                            shape = CircleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(task.severity, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AsyncImage(
                model = task.imageUrl,
                contentDescription = "Pothole Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Status: ${task.status}", style = MaterialTheme.typography.body1)
            Text("Location: ${task.address}", style = MaterialTheme.typography.body1)
            Text("Coordinates: ${task.latitude}, ${task.longitude}", style = MaterialTheme.typography.body1)
            Text("Assigned: N/A", style = MaterialTheme.typography.body1)
            Text("Deadline: N/A", style = MaterialTheme.typography.body1)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = randomProgress / 100f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("$randomProgress%", style = MaterialTheme.typography.body1)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.updateProgress(task.id, randomProgress) }) { // Fixed: Use randomProgress
                    Text("+10%")
                }
                Button(onClick = { viewModel.markCompleted(task.id) }) {
                    Text("Complete")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${task.latitude},${task.longitude}&travelmode=driving")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }) {
                    Text("Navigate")
                }
                Button(onClick = { /* TODO: Request Help */ }) {
                    Text("Request Help")
                }
            }
        }
    }
}


