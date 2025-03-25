package com.example.driveease

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class OfficerActivity : ComponentActivity() {
    private val viewModel: OfficerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            MaterialTheme {
                OfficerScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerScreen(viewModel: OfficerViewModel) {
    val reports by viewModel.reports.collectAsState()
    var isDarkTheme by remember { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<PotholeReport?>(null) }

    MaterialTheme(colorScheme = if (isDarkTheme) darkColors() else lightColors()) {
        Scaffold(
            topBar = { OfficerHeader(onThemeToggle = { isDarkTheme = !isDarkTheme }) },
            floatingActionButton = { FloatingActionButton(onClick = { /* Open report form */ }) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                StatsCard(
                    activeReports = reports.size,
                    workersDeployed = viewModel.calculateWorkersDeployed(),
                    totalWorkers = viewModel.getTotalAvailableWorkers()
                )
                ContentWrapper(
                    reports = reports,
                    selectedReport = selectedReport,
                    onReportClicked = { report -> selectedReport = report }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerHeader(onThemeToggle: () -> Unit) {
    TopAppBar(
        title = { Text("🚧 Pothole Patrol", color = Color.White) },
        actions = {
            IconButton(onClick = onThemeToggle) {
                Icon(Icons.Default.DarkMode, contentDescription = "Toggle Theme", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2C3E50)),
        modifier = Modifier.shadow(4.dp)
    )
}

@Composable
fun StatsCard(activeReports: Int, workersDeployed: Int, totalWorkers: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3498DB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📊 Current Status", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text("🚧 Active: $activeReports", color = Color.White)
            Text("✅ Resolved: 18", color = Color.White) // TODO: Replace with real data
            Text("👷‍♂️ Deployed: $workersDeployed", color = Color.White)
            Text("👥 Total: $totalWorkers", color = Color.White)
        }
    }
}

@Composable
fun ContentWrapper(
    reports: List<PotholeReport>,
    selectedReport: PotholeReport?,
    onReportClicked: (PotholeReport) -> Unit
) {
    val isLargeScreen = LocalContext.current.resources.configuration.screenWidthDp > 768
    if (isLargeScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            RecentReportsList(
                reports = reports,
                onReportClicked = onReportClicked,
                modifier = Modifier.weight(1f)
            )
            OpenStreetMapView(
                reports = reports,
                selectedReport = selectedReport,
                modifier = Modifier.weight(2f)
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            OpenStreetMapView(reports = reports, selectedReport = selectedReport, modifier = Modifier.height(300.dp))
            RecentReportsList(reports = reports, onReportClicked = onReportClicked)
        }
    }
}

@Composable
fun RecentReportsList(reports: List<PotholeReport>, onReportClicked: (PotholeReport) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.padding(8.dp)) {
        items(reports) { report ->
            ReportItem(report = report, onClick = { onReportClicked(report) })
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ReportItem(report: PotholeReport, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🚨 Report #${report.id}", style = MaterialTheme.typography.titleMedium)
                Text("📅 ${report.date}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("📝 ${report.description}", style = MaterialTheme.typography.bodyMedium)
            SeverityIndicator(severity = report.severity)
            Text("📍 ${report.latitude?.toFixed(4)}, ${report.longitude?.toFixed(4)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun SeverityIndicator(severity: String) {
    val (backgroundColor, textColor) = when (severity.lowercase()) {
        "high" -> Color(0xFFE74C3C) to Color.White
        "medium" -> Color(0xFFF1C40F) to Color.Black
        else -> Color(0xFF7F8C8D) to Color.White
    }
    Text(
        text = severity.uppercase(),
        modifier = Modifier
            .background(backgroundColor, shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = textColor,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
fun OpenStreetMapView(reports: List<PotholeReport>, selectedReport: PotholeReport?, modifier: Modifier = Modifier) {
    var mapView by remember { mutableStateOf<MapView?>(null) }

    Card(
        modifier = modifier.padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setCenter(GeoPoint(20.5937, 78.9629)) // Center of India
                    controller.setZoom(4.0)
                    mapView = this
                }
            },
            update = { view ->
                view.overlays.clear()
                reports.forEach { report ->
                    Marker(view).apply {
                        position = report.latitude?.let { report.longitude?.let { it1 ->
                            GeoPoint(it,
                                it1
                            )
                        } }
                        title = "Report #${report.id}"
                        snippet = "${report.description}\nSeverity: ${report.severity}"
                        setOnMarkerClickListener { _, _ -> true }
                    }.also { view.overlays.add(it) }
                }
                selectedReport?.let {
                    view.controller.setZoom(20.0)
                    view.controller.setCenter(it.latitude?.let { it1 -> it.longitude?.let { it2 ->
                        GeoPoint(it1,
                            it2
                        )
                    } })
                }
                view.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(Unit) {
        onDispose { mapView?.onDetach() }
    }
}

@Composable
fun FloatingActionButton(onClick: () -> Unit) {
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = Color(0xFFE74C3C),
        contentColor = Color.White,
        modifier = Modifier
            .padding(16.dp)
            .shadow(4.dp, CircleShape)
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add Report")
    }
}

// Helper extension for formatting doubles
fun Double.toFixed(digits: Int): String = String.format("%.${digits}f", this)

// Theme configuration
private fun lightColors() = lightColorScheme(
    primary = Color(0xFF2C3E50),
    secondary = Color(0xFF3498DB),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF2C3E50),
    surface = Color.White
)

private fun darkColors() = darkColorScheme(
    primary = Color(0xFF1A252F),
    secondary = Color(0xFF2C3E50),
    background = Color(0xFF2C3E50),
    onBackground = Color(0xFFECF0F1),
    surface = Color(0xFF2C3E50)
)

@Preview(showBackground = true)
@Composable
fun PreviewOfficerHeader(){
    MaterialTheme{
        OfficerHeader(onThemeToggle = {})
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewStatsCard(){
    MaterialTheme {
        StatsCard(activeReports = 5, workersDeployed = 3, totalWorkers = 10)
    }
}
@Preview(showBackground = true)
@Composable
fun PreviewReportItem() {
    MaterialTheme {
        ReportItem(
            report = PotholeReport(
                id = "null",
                date = "2023-10-01",
                description = "Large pothole near the intersection",
                severity = "High",
                latitude = 20.5937,
                longitude = 78.9629
            ),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSeverityIndicator() {
    MaterialTheme {
        Column {
            SeverityIndicator(severity = "High")
            SeverityIndicator(severity = "Medium")
            SeverityIndicator(severity = "Low")
        }
    }
}
@Preview(showBackground = true)
@Composable
fun PreviewRecentReportsList() {
    MaterialTheme {
        RecentReportsList(
            reports = listOf(
                PotholeReport(
                    id = "",
                    date = "2023-10-01",
                    description = "Large pothole near the intersection",
                    severity = "High",
                    latitude = 20.5937,
                    longitude = 78.9629
                ),
                PotholeReport(
                    id = "",
                    date = "2023-10-02",
                    description = "Small pothole on the side road",
                    severity = "Medium",
                    latitude = 20.5940,
                    longitude = 78.9630
                )
            ),
            onReportClicked = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewFloatingActionButton() {
    MaterialTheme {
        FloatingActionButton(onClick = {})
    }
}

