package com.example.driveease

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

class OfficerActivity : ComponentActivity() {
    private val viewModel: OfficerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir.absolutePath, "osmdroid")
            osmdroidTileCache = File(cacheDir.absolutePath, "osmdroid/tiles")
        }

        setContent {
            OfficerScreen(
                viewModel = viewModel,
                navigateToSignIn = {
                    startActivity(Intent(this@OfficerActivity, SignActivity::class.java))
                    finish()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerScreen(viewModel: OfficerViewModel, navigateToSignIn: () -> Unit) {
    val reports by viewModel.reports.collectAsState()
    val authState by viewModel.authState.collectAsState()
    var isDarkTheme by remember { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<PotholeReport?>(null) }
    var isTaskExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(authState) {
        when (authState) {
            OfficerViewModel.AuthState.Unauthenticated -> {
                Toast.makeText(context, "Please sign in to continue", Toast.LENGTH_SHORT).show()
                navigateToSignIn()
            }
            OfficerViewModel.AuthState.Authenticated -> {}
            OfficerViewModel.AuthState.Initial -> {}
        }
    }

    if (authState == OfficerViewModel.AuthState.Authenticated) {
        MaterialTheme(colorScheme = if (isDarkTheme) darkColors() else lightColors()) {
            Scaffold(
                topBar = { OfficerHeader(onThemeToggle = { isDarkTheme = !isDarkTheme }) },
                floatingActionButton = { EnhancedFloatingActionButton(onClick = { /* Open report form */ }) }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(0f, Float.POSITIVE_INFINITY)
                            )
                        )
                ) {
                    StatsCard(
                        activeReports = reports.size,
                        workersDeployed = viewModel.calculateWorkersDeployed(),
                        totalWorkers = viewModel.getTotalAvailableWorkers()
                    )
                    ContentWrapper(
                        reports = reports,
                        selectedReport = selectedReport,
                        onReportClicked = { report ->
                            selectedReport = report
                            isTaskExpanded = !isTaskExpanded
                        },
                        isTaskExpanded = isTaskExpanded,
                        listState = listState
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerHeader(onThemeToggle: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "🚧 Pothole Patrol",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 20.sp
            )
        },
        actions = {
            IconButton(onClick = onThemeToggle) {
                Icon(
                    Icons.Default.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier.shadow(8.dp)
    )
}

@Composable
fun StatsCard(activeReports: Int, workersDeployed: Int, totalWorkers: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    "📊 Current Status",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Active: $activeReports", color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resolved: 18", color = Color.White, fontSize = 16.sp)
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.People, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deployed: $workersDeployed", color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.People, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Total: $totalWorkers", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContentWrapper(
    reports: List<PotholeReport>,
    selectedReport: PotholeReport?,
    onReportClicked: (PotholeReport) -> Unit,
    isTaskExpanded: Boolean,
    listState: LazyListState
) {
    val isLargeScreen = LocalContext.current.resources.configuration.screenWidthDp > 768
    val mapHeight by animateDpAsState(
        targetValue = if (isTaskExpanded) 100.dp else if (isLargeScreen) 400.dp else 300.dp,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing), label = ""
    )
    val listWeight by animateFloatAsState(
        targetValue = if (isTaskExpanded) 1f else if (isLargeScreen) 1f else 2f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing), label = ""
    )

    if (isLargeScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            RecentReportsList(
                reports = reports,
                onReportClicked = onReportClicked,
                isTaskExpanded = isTaskExpanded,
                listState = listState,
                modifier = Modifier.weight(listWeight)
            )
            AnimatedVisibility(
                visible = !isTaskExpanded,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
                modifier = Modifier.weight(2f)
            ) {
                OpenStreetMapView(
                    reports = reports,
                    selectedReport = selectedReport,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !isTaskExpanded,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                OpenStreetMapView(
                    reports = reports,
                    selectedReport = selectedReport,
                    modifier = Modifier
                        .height(mapHeight)
                        .fillMaxWidth()
                )
            }
            RecentReportsList(
                reports = reports,
                onReportClicked = onReportClicked,
                isTaskExpanded = isTaskExpanded,
                listState = listState,
                modifier = Modifier.weight(listWeight)
            )
        }
    }
}

@Composable
fun RecentReportsList(
    reports: List<PotholeReport>,
    onReportClicked: (PotholeReport) -> Unit,
    isTaskExpanded: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(reports) { report ->
            ReportItem(
                report = report,
                onClick = { onReportClicked(report) },
                isExpanded = isTaskExpanded
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ReportItem(report: PotholeReport, onClick: () -> Unit, isExpanded: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (isExpanded) 1.05f else 1f,
        animationSpec = tween(durationMillis = 300)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🚨 Report #${report.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "📅 ${report.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "📝 ${report.description}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            SeverityIndicator(severity = report.severity)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "📍 ${report.latitude?.toFixed(4) ?: "N/A"}, ${report.longitude?.toFixed(4) ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SeverityIndicator(severity: String) {
    val (backgroundColor, textColor) = when (severity.lowercase()) {
        "high" -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        "medium" -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = severity.uppercase(),
        modifier = Modifier
            .background(backgroundColor, shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = textColor,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun OpenStreetMapView(
    reports: List<PotholeReport>,
    selectedReport: PotholeReport?,
    modifier: Modifier = Modifier
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }

    val zoomLevel by animateFloatAsState(
        targetValue = if (selectedReport != null) 20f else 4f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "MapZoomAnimation"
    )

    Card(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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
                    try {
                        val lat = report.latitude
                        val lon = report.longitude
                        if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                            Marker(view).apply {
                                position = GeoPoint(lat, lon)
                                title = "Report #${report.id}"
                                snippet = report.description
                            }.also { view.overlays.add(it) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                selectedReport?.let { report ->
                    try {
                        val selectedLat = report.latitude
                        val selectedLon = report.longitude
                        if (selectedLat != null && selectedLon != null && selectedLat != 0.0 && selectedLon != 0.0) {
                            view.controller.setZoom(zoomLevel.toDouble())
                            view.controller.setCenter(GeoPoint(selectedLat, selectedLon))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                view.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDetach()
        }
    }
}


@Composable
fun EnhancedFloatingActionButton(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    FloatingActionButton(
        onClick = {
            isPressed = true
            onClick()
            isPressed = false
        },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        modifier = Modifier
            .padding(16.dp)
            .scale(scale)
            .shadow(8.dp, CircleShape)
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add Report")
    }
}

fun Double.toFixed(digits: Int): String = String.format("%.${digits}f", this)

private fun lightColors() = lightColorScheme(
    primary = Color(0xFF1976D2),
    secondary = Color(0xFF42A5F5),
    background = Color(0xFFF5F7FA),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFE0E0E0),
    error = Color(0xFFD32F2F),
    onError = Color(0xFFFFFFFF)
)

private fun darkColors() = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF64B5F6),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color(0xFF000000),
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF424242),
    error = Color(0xFFE57373),
    onError = Color(0xFF000000)
)

@Preview(showBackground = true)
@Composable
fun PreviewOfficerHeader() {
    MaterialTheme {
        OfficerHeader(onThemeToggle = {})
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewStatsCard() {
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
                id = "1",
                date = "2023-10-01",
                description = "Large pothole near the intersection",
                severity = "High",
                latitude = 20.5937,
                longitude = 78.9629
            ),
            onClick = {},
            isExpanded = false
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
                    id = "1",
                    date = "2023-10-01",
                    description = "Large pothole near the intersection",
                    severity = "High",
                    latitude = 20.5937,
                    longitude = 78.9629
                ),
                PotholeReport(
                    id = "2",
                    date = "2023-10-02",
                    description = "Small pothole on the side road",
                    severity = "Medium",
                    latitude = 20.5940,
                    longitude = 78.9630
                )
            ),
            onReportClicked = {},
            isTaskExpanded = false,
            listState = rememberLazyListState()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewFloatingActionButton() {
    MaterialTheme {
        EnhancedFloatingActionButton(onClick = {})
    }
}