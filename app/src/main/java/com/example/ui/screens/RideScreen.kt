package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.CosmeticCatalog
import com.example.data.model.CosmeticDefaults
import com.example.data.model.CyclingPointsCalculator
import com.example.data.model.SportType
import com.example.data.model.UserProfile
import com.example.ui.components.PointsPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.RideSessionState
import com.example.ui.viewmodel.RideStatus
import com.example.ui.viewmodel.RideSummary
import com.example.update.GitHubReleaseInfo
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale

@Composable
fun RideScreen(
    profile: UserProfile?,
    rideSession: RideSessionState,
    availableUpdate: GitHubReleaseInfo?,
    onOpenUpdateDialog: () -> Unit,
    onSelectSport: (SportType) -> Unit,
    onLocationPermissionGranted: () -> Unit,
    onStartRide: () -> Unit,
    onPauseRide: () -> Unit,
    onResumeRide: () -> Unit,
    onFinishRide: (String) -> Unit,
    onDismissSummary: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            onLocationPermissionGranted()
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            onLocationPermissionGranted()
        }
    }

    val activeSport = rideSession.selectedSport
    val equippedTrailId = profile?.equippedTrailId ?: CosmeticDefaults.TRAIL_EMERALD
    val sportAccentColor = if (activeSport == SportType.RUNNING) {
        Color(0xFFE65100)
    } else {
        CosmeticCatalog.trailColorFor(equippedTrailId)
    }
    val sportIcon = if (activeSport == SportType.RUNNING) {
        Icons.AutoMirrored.Rounded.DirectionsRun
    } else {
        Icons.AutoMirrored.Rounded.DirectionsBike
    }
    val defaultGeoPoint = remember { GeoPoint(48.8566, 2.3522) }

    val mapView = remember(context) {
        val appContext = context.applicationContext
        val osmdroidConfig = Configuration.getInstance()
        osmdroidConfig.userAgentValue = appContext.packageName
        osmdroidConfig.osmdroidBasePath = File(appContext.cacheDir, "osmdroid").apply { mkdirs() }
        osmdroidConfig.osmdroidTileCache = File(appContext.cacheDir, "osmdroid/tiles").apply { mkdirs() }

        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.5)
            controller.setCenter(defaultGeoPoint)
        }
    }

    val routeOverlay = remember(mapView) {
        Polyline(mapView).apply {
            outlinePaint.strokeWidth = 14f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
        }
    }

    val riderHaloOverlay = remember(mapView) {
        Polygon(mapView).apply {
            outlinePaint.strokeWidth = 4f
        }
    }

    val riderDotOverlay = remember(mapView) {
        Polygon(mapView).apply {
            outlinePaint.strokeWidth = 3f
            outlinePaint.color = android.graphics.Color.WHITE
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(rideSession.currentLocation) {
        rideSession.currentLocation?.let { target ->
            mapView.controller.animateTo(target, 16.5, 600L)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.overlays.clear()

                if (rideSession.routePoints.size >= 2) {
                    routeOverlay.outlinePaint.color = sportAccentColor.toArgb()
                    routeOverlay.setPoints(rideSession.routePoints)
                    view.overlays.add(routeOverlay)
                }

                rideSession.currentLocation?.let { pos ->
                    riderHaloOverlay.points = Polygon.pointsAsCircle(pos, 18.0)
                    riderHaloOverlay.fillPaint.color = sportAccentColor.copy(alpha = 0.25f).toArgb()
                    riderHaloOverlay.outlinePaint.color = sportAccentColor.toArgb()
                    view.overlays.add(riderHaloOverlay)

                    riderDotOverlay.points = Polygon.pointsAsCircle(pos, 7.0)
                    riderDotOverlay.fillPaint.color = sportAccentColor.toArgb()
                    view.overlays.add(riderDotOverlay)
                }

                view.invalidate()
            },
        )

        // Top Floating Gamification HUD & Sport Selector
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = sportAccentColor.copy(alpha = 0.16f),
                            ) {
                                Icon(
                                    imageVector = sportIcon,
                                    contentDescription = activeSport.labelFr,
                                    tint = sportAccentColor,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(20.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile?.displayName ?: "Sportif",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${activeSport.labelFr} • ${activeSport.rewardRuleShort}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sportAccentColor,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))
                        PointsPill(points = profile?.totalPointsEarned ?: 0L)
                    }

                    if (rideSession.status == RideStatus.IDLE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SportType.entries.forEach { sport ->
                                val selected = sport == activeSport
                                val chipIcon = if (sport == SportType.RUNNING) {
                                    Icons.AutoMirrored.Rounded.DirectionsRun
                                } else {
                                    Icons.AutoMirrored.Rounded.DirectionsBike
                                }
                                val selectedColor = if (sport == SportType.RUNNING) {
                                    Color(0xFFE65100)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                                Surface(
                                    selected = selected,
                                    onClick = { onSelectSport(sport) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) {
                                        selectedColor
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                    },
                                    border = if (selected) {
                                        null
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("sport_selector_${sport.id}"),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Icon(
                                                imageVector = chipIcon,
                                                contentDescription = null,
                                                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = sport.labelFr,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = if (sport == SportType.RUNNING) "25 pts / 100 m" else "10 pts / km",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) {
                                                Color.White.copy(alpha = 0.9f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (availableUpdate != null && rideSession.status == RideStatus.IDLE) {
                Surface(
                    onClick = onOpenUpdateDialog,
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .testTag("update_available_banner"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = "Mise à jour ${availableUpdate.tagName} disponible • Toucher pour installer",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (!hasLocationPermission) {
                Surface(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ride_permission_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.ride_permission_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("grant_location_button"),
                        ) {
                            Icon(Icons.Rounded.MyLocation, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ride_permission_cta))
                        }
                    }
                }
            }
        }

        // Bottom Telemetry & Controls Dock
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (hasLocationPermission && rideSession.currentLocation != null) {
                SmallFloatingActionButton(
                    onClick = {
                        rideSession.currentLocation?.let { pos ->
                            mapView.controller.animateTo(pos, 16.5, 450L)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = sportAccentColor,
                    modifier = Modifier.testTag("recenter_map_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Recentrer sur ma position",
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TelemetryStat(
                            label = "Distance",
                            value = String.format(Locale.FRANCE, "%.2f km", rideSession.distanceMeters / 1000.0),
                            highlight = false,
                        )
                        TelemetryStat(
                            label = "Points gagnés",
                            value = "+${rideSession.livePointsEarned}",
                            highlight = true,
                            highlightColor = sportAccentColor,
                        )
                        TelemetryStat(
                            label = "Vitesse",
                            value = String.format(Locale.FRANCE, "%.1f km/h", rideSession.currentSpeedKmh),
                            highlight = false,
                        )
                        TelemetryStat(
                            label = "Durée",
                            value = formatDuration(rideSession.elapsedSeconds),
                            highlight = false,
                        )
                    }

                    // Progress toward next reward step
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val progress = CyclingPointsCalculator.progressTowardNextReward(
                            distanceMeters = rideSession.distanceMeters,
                            sportType = activeSport,
                        )
                        val stepTitle = if (activeSport == SportType.RUNNING) {
                            "Palier 100 m (+25 pts)"
                        } else {
                            "Palier 1 km (+10 pts)"
                        }
                        val stepValue = if (activeSport == SportType.RUNNING) {
                            "${rideSession.distanceMeters % 100L} / 100 m"
                        } else {
                            "${rideSession.distanceMeters % 1000L} / 1000 m"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stepTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stepValue,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = sportAccentColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = sportAccentColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    when (rideSession.status) {
                        RideStatus.IDLE -> {
                            Button(
                                onClick = {
                                    if (!hasLocationPermission) {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            )
                                        )
                                    } else {
                                        onStartRide()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 54.dp)
                                    .testTag("start_ride_button"),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = sportAccentColor,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(
                                        if (activeSport == SportType.RUNNING) R.string.run_start else R.string.ride_start
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        RideStatus.RIDING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = onPauseRide,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("pause_ride_button"),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Icon(Icons.Rounded.Pause, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.ride_pause))
                                }
                                Button(
                                    onClick = { onFinishRide(equippedTrailId) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("finish_ride_button"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                ) {
                                    Icon(Icons.Rounded.Flag, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.ride_finish))
                                }
                            }
                        }

                        RideStatus.PAUSED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Button(
                                    onClick = onResumeRide,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("resume_ride_button"),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.ride_resume))
                                }
                                OutlinedButton(
                                    onClick = { onFinishRide(equippedTrailId) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("finish_ride_button"),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Icon(Icons.Rounded.Flag, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.ride_finish))
                                }
                            }
                        }

                        RideStatus.SAVING -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    rideSession.lastCompletedSummary?.let { summary ->
        RideSummaryDialog(
            summary = summary,
            onDismiss = onDismissSummary,
            onGoToLeaderboard = {
                onDismissSummary()
                onNavigateToLeaderboard()
            },
        )
    }
}

@Composable
private fun TelemetryStat(
    label: String,
    value: String,
    highlight: Boolean,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) highlightColor else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RideSummaryDialog(
    summary: RideSummary,
    onDismiss: () -> Unit,
    onGoToLeaderboard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(14.dp).size(32.dp),
                )
            }
        },
        title = {
            Text(
                text = if (summary.sportType == SportType.RUNNING) {
                    "Bravo pour votre course !"
                } else {
                    "Bravo pour votre balade !"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "+${summary.pointsEarned} points ajoutés (${summary.sportType.labelFr})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Text(
                    text = "Distance : ${String.format(Locale.FRANCE, "%.2f km", summary.distanceMeters / 1000.0)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Durée : ${formatDuration(summary.durationSeconds)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Vitesse moyenne : ${String.format(Locale.FRANCE, "%.1f km/h", summary.avgSpeedKmh)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGoToLeaderboard,
                modifier = Modifier.testTag("view_leaderboard_button"),
            ) {
                Text("Voir le classement")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        },
    )
}

fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
