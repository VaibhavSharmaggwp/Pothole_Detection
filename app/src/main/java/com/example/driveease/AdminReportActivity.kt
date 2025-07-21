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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.firebase.database.*
import org.osmdroid.config.Configuration
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
                AdminReportScreen(taskId = userId) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReportScreen(taskId: String, onBackClick: () -> Unit) {
    var report by remember { mutableStateOf<PotholeReport?>(null) }
    val database = FirebaseDatabase.getInstance()
    val taskRef = database.getReference("pothole_reports/$taskId")
    val context = LocalContext.current

    // Initialize OSMDroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
    }

    // Fetch report from Firebase
    DisposableEffect(taskId) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                report = snapshot.getValue(PotholeReport::class.java)
                if (report == null) {
                    println("No data found for taskId: $taskId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                println("Error fetching report: ${error.message}")
            }
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
        if (report != null) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column {
                        AsyncImage(
                            model = report!!.imageUrl,
                            contentDescription = "Pothole Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )

                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        getSeverityColor(report!!.severity).copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = getSeverityColor(report!!.severity)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${report!!.severity} Severity",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = getSeverityColor(report!!.severity)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            DetailRow(
                                "Location:",
                                if (report!!.address.isNotEmpty()) report!!.address else "Coordinates: ${report!!.latitude}, ${report!!.longitude}"
                            )
                            DetailRow("Date Reported:", report!!.date)
                            DetailRow("Time Reported:", report!!.time)
                            DetailRow(
                                label = "Status:",
                                value = report!!.status,
                                badgeText = if (report!!.status == "completed") "COMPLETED" else "IN PROGRESS",
                                badgeColor = if (report!!.status == "completed") Color(0xFF2ECC71) else Color(0xFFF1C40F)
                            )
                            DetailRow("Coordinates:", "${report!!.latitude}, ${report!!.longitude}")

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                "Map:",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34495E),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            report!!.latitude?.let { lat ->
                                report!!.longitude?.let { lon ->
                                    MapViewContainer(lat, lon)
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = { taskRef.updateChildren(mapOf("status" to "completed")) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mark as Completed", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Button(
                                    onClick = { taskRef.updateChildren(mapOf("status" to "in-progress")) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                                    shape = RoundedCornerShape(10.dp)
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

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
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
            view.controller.setCenter(GeoPoint(latitude, longitude))
            view.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color(0xFFEEEEEE), shape = RoundedCornerShape(5.dp))
    )
}



