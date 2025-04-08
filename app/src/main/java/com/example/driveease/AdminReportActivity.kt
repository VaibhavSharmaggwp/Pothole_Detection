package com.example.driveease

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.firebase.database.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class AdminReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = intent.getStringExtra("TASK_ID") ?: ""

        setContent {
            DriveEaseTheme {
                AdminReportScreen(taskId = userId) {
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReportScreen(taskId: String, onBackClick: () -> Unit) {
    var task by remember { mutableStateOf<Task?>(null) }
    val database = FirebaseDatabase.getInstance()
    val taskRef = database.getReference("pothole_reports/$taskId")
    val context = LocalContext.current

    DisposableEffect(taskId) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                task = snapshot.getValue(Task::class.java)

            }

            override fun onCancelled(error: DatabaseError) {}
        }
        taskRef.addValueEventListener(listener)
        onDispose { taskRef.removeEventListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pothole Report", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        modifier = Modifier.background(Color(0xFFF5F7FA))
    ) { padding ->
        if (task != null) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column {
                        AsyncImage(
                            model = task!!.imageUrl,
                            contentDescription = "Pothole Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )

                        Column(modifier = Modifier.padding(25.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        getSeverityColor(task!!.severity).copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 15.dp, vertical = 5.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = getSeverityColor(task!!.severity))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${task!!.severity} Severity",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = getSeverityColor(task!!.severity)
                                )
                            }

                            Spacer(modifier = Modifier.height(15.dp))

                            DetailRow("Location:", task!!.address)
                            DetailRow("Date Reported:", task!!.date)
                            DetailRow("Time Reported:", task!!.time)
                            DetailRow(
                                label = "Status:",
                                value = task!!.status,
                                badgeText = if (task!!.status == "completed") "COMPLETED" else "IN PROGRESS",
                                badgeColor = if (task!!.status == "completed") Color(0xFF2ECC71) else Color(0xFFF1C40F)
                            )
                            DetailRow("Coordinates:", "${task!!.latitude}, ${task!!.longitude}")

                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Map:", fontWeight = FontWeight.Bold, color = Color(0xFF34495E), modifier = Modifier.padding(bottom = 8.dp))
                            MapViewContainer(task!!.latitude, task!!.longitude)

                            Spacer(modifier = Modifier.height(25.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(15.dp)
                            ) {
                                Button(
                                    onClick = {
                                        taskRef.updateChildren(mapOf("status" to "completed"))
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                                    shape = RoundedCornerShape(5.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mark as Completed", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        taskRef.updateChildren(mapOf("status" to "in-progress"))
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                                    shape = RoundedCornerShape(5.dp)
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mark as Incomplete", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    badgeText: String? = null,
    badgeColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF34495E),
            modifier = Modifier.width(120.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, color = Color(0xFF555555))
            if (badgeText != null && badgeColor != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(5.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MapViewContainer(latitude: Double, longitude: Double) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(latitude, longitude))
        }
    }

    AndroidView(
        factory = { mapView },
        update = { view ->
            view.overlays.clear()
            val marker = Marker(view).apply {
                position = GeoPoint(latitude, longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            view.overlays.add(marker)
            view.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color(0xFFEEEEEE), shape = RoundedCornerShape(5.dp))
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AdminReportScreenMockPreview() {
    DriveEaseTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column {
                        // Mock image placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Pothole Image Preview")
                        }

                        Column(modifier = Modifier.padding(25.dp)) {
                            // Rest of your UI components with mock data
                            DetailRow("Location:", "123 Main Street, Cityville")
                            DetailRow("Date Reported:", "2023-05-15")
                            DetailRow("Time Reported:", "14:30")
                            // ... etc
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DetailRowPreview(){
    DriveEaseTheme {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailRow(label = "Location:", value =  "123 Main Street, City ville")
            DetailRow(
                label = "Status:",
                value = "In Progress",
                badgeText = "IN PROGRESS",
                badgeColor = Color(0xFFF1C40F)
            )
            DetailRow(
                label = "Status:",
                value = "Completed",
                badgeText = "COMPLETED",
                badgeColor = Color(0xFF2ECC71)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MapViewContainerPreview(){
    DriveEaseTheme {
        MapViewContainer(latitude =  37.7749, longitude = -122.4194)
    }
}