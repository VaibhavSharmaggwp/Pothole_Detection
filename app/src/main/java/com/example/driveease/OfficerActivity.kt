package com.example.driveease

import android.content.Context
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class OfficerActivity : AppCompatActivity() { // Use AppCompatActivity for XML support
    private val viewModel: OfficerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_officer) // Load XML layout

        // Initialize OSMDroid configuration
        Configuration.getInstance().userAgentValue = packageName

        findViewById<ComposeView>(R.id.composeView).setContent {
            OfficerScreen(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerScreen(viewModel: OfficerViewModel) {
    // Collect state from ViewModel
    val reports by viewModel.reports.collectAsState()
    val roleCounts by viewModel.roleCounts.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Officer Dashboard") },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color(0xFF2C3E50))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Stats card at the top
            StatsCard(
                activeReports = reports.size,
                workersDeployed = viewModel.calculateWorkersDeployed(),
                roleCounts = roleCounts
            )
            // Main content row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                // Reports list (1/3 of screen width)
                RecentReportsList(
                    reports = reports,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                // Map view (2/3 of screen width)
                OpenStreetMapView(
                    reports = reports,
                    modifier = Modifier
                        .weight(2f)
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
fun StatsCard(activeReports: Int, workersDeployed: Int, roleCounts: RoleCounts?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Current Status",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Stats items
            Text("Active Reports: $activeReports", color = Color.White)
            Text("Resolved: 18", color = Color.White) // TODO: Update with real data
            Text("Workers Deployed: $workersDeployed", color = Color.White)
            Text("Available Workers: ${roleCounts?.workers ?: 0}", color = Color.White)
        }
    }
}

@Composable
fun RecentReportsList(reports: List<PotholeReport>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.padding(8.dp)) {
        items(reports) { report ->
            RecentReportItem(report = report)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun RecentReportItem(report: PotholeReport) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.LightGray)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "Report #${report.id}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                report.description,
                style = MaterialTheme.typography.bodyMedium
            )
            SeverityIndicator(severity = report.severity)
        }
    }
}

@Composable
private fun SeverityIndicator(severity: String) {
    Text(
        text = "Severity: ${severity.uppercase()}",
        color = when (severity.lowercase()) {
            "high" -> Color.Red
            "medium" -> Color(0xFFFFA500) // orange
            else -> Color.Gray
        },
        style = MaterialTheme.typography.labelLarge
    )
}

@Composable
fun OpenStreetMapView(reports: List<PotholeReport>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    // OSMDroid map implementation
    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)

                    // Set initial view to India
                    controller.setCenter(GeoPoint(20.5937, 78.9629)) // India coordinates
                    controller.setZoom(4.0)

                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Clear existing markers
                view.overlays.clear()

                // Add new markers for each report
                reports.forEach { report ->
                    Marker(view).apply {
                        position = GeoPoint(report.latitude, report.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Report #${report.id}"
                        subDescription = report.description

                        // Set custom marker icon from drawable
                        setIcon(ContextCompat.getDrawable(context, R.drawable.ic_map_marker))

                        // Set click listener
                        setOnMarkerClickListener { marker, _ ->
                            showReportPopup(context, report)
                            true
                        }
                    }.also { view.overlays.add(it) }
                }

                // Refresh the map view
                view.invalidate()
            }
        )
    }

    // Handle map lifecycle
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDetach()
        }
    }
}

// Function to show report details in a popup
private fun showReportPopup(context: Context, report: PotholeReport) {
    AlertDialog.Builder(context)
        .setTitle("Report #${report.id}")
        .setMessage(
            """
            Description: ${report.description}
            Severity: ${report.severity}
            Location: (${String.format("%.4f", report.latitude)}, ${String.format("%.4f", report.longitude)})
            Reported by: ${report.userEmail}
            """.trimIndent()
        )
        .setPositiveButton("OK", null)
        .show()
}

// Preview for StatsCard
@Preview(showBackground = true)
@Composable
fun PreviewStatsCard() {
    StatsCard(
        activeReports = 24,
        workersDeployed = 45,
        roleCounts = RoleCounts(officers = 5, sdo = 3, workers = 45)
    )
}

// Preview for RecentReportItem
@Preview(showBackground = true)
@Composable
fun PreviewRecentReportItem() {
    RecentReportItem(
        report = PotholeReport(
            id = "1",
            description = "Large pothole on the highway",
            severity = "high",
            latitude = 19.0760,
            longitude = 72.8777,
            userEmail = "user1@example.com"
        )
    )
}

// Preview for RecentReportsList
@Preview(showBackground = true)
@Composable
fun PreviewRecentReportsList() {
    val reports = listOf(
        PotholeReport(
            id = "1",
            description = "Large pothole on the highway",
            severity = "high",
            latitude = 19.0760,
            longitude = 72.8777,
            userEmail = "user1@example.com"
        ),
        PotholeReport(
            id = "2",
            description = "Medium pothole on the main road",
            severity = "medium",
            latitude = 28.7041,
            longitude = 77.1025,
            userEmail = "user2@example.com"
        )
    )
    RecentReportsList(reports = reports)
}

@Preview(showBackground = true, widthDp = 800, heightDp = 1200)
@Composable
fun PreviewOfficerScreen() {
    val viewModel = OfficerViewModel()
    OfficerScreen(viewModel)
}