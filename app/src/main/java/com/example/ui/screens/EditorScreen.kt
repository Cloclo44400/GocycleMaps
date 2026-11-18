package com.example.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.PlannedRoute
import com.example.data.model.SportType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.RouteEditorState
import com.example.ui.viewmodel.UiState
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale

@Composable
fun EditorScreen(
    editorState: RouteEditorState,
    plannedRoutesState: UiState<List<PlannedRoute>>,
    currentLocation: GeoPoint?,
    onSelectSport: (SportType) -> Unit,
    onAddWaypoint: (GeoPoint) -> Unit,
    onUndoWaypoint: () -> Unit,
    onCloseLoop: () -> Unit,
    onClearRoute: () -> Unit,
    onSaveRoute: (String) -> Unit,
    onLoadRoute: (PlannedRoute) -> Unit,
    onFollowRoute: (PlannedRoute) -> Unit,
    onDeleteRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnAddWaypoint by rememberUpdatedState(onAddWaypoint)

    var showSaveDialog by remember { mutableStateOf(false) }
    var showSavedRoutesDialog by remember { mutableStateOf(false) }
    var centeredOnce by remember { mutableStateOf(false) }

    val sportAccentColor = if (editorState.sportType == SportType.RUNNING) {
        Color(0xFFE65100)
    } else {
        MaterialTheme.colorScheme.primary
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
            controller.setZoom(14.5)
            controller.setCenter(currentLocation ?: defaultGeoPoint)
        }
    }

    val tapOverlay = remember(mapView) {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    latestOnAddWaypoint(p)
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    latestOnAddWaypoint(p)
                }
                return true
            }
        })
    }

    val routeLine = remember(mapView) {
        Polyline(mapView).apply {
            outlinePaint.strokeWidth = 12f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
        }
    }

    LaunchedEffect(currentLocation) {
        if (!centeredOnce && currentLocation != null && editorState.waypoints.isEmpty()) {
            mapView.controller.animateTo(currentLocation, 15.0, 500L)
            centeredOnce = true
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

    val savedRoutes = (plannedRoutesState as? UiState.Success)?.data.orEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .testTag("editor_map_view"),
            update = { view ->
                view.overlays.clear()
                view.overlays.add(tapOverlay)

                if (editorState.waypoints.size >= 2) {
                    routeLine.outlinePaint.color = sportAccentColor.toArgb()
                    routeLine.setPoints(editorState.waypoints)
                    view.overlays.add(routeLine)
                }

                editorState.waypoints.forEachIndexed { index, pt ->
                    val isStart = index == 0
                    val isEnd = index == editorState.waypoints.lastIndex && editorState.waypoints.size > 1
                    val dot = Polygon(view).apply {
                        points = Polygon.pointsAsCircle(
                            pt,
                            when {
                                isStart || isEnd -> 13.0
                                else -> 8.0
                            }
                        )
                        fillPaint.color = when {
                            isStart -> Color(0xFF16A34A).toArgb()
                            isEnd -> Color(0xFFDC2626).toArgb()
                            else -> sportAccentColor.toArgb()
                        }
                        outlinePaint.color = android.graphics.Color.WHITE
                        outlinePaint.strokeWidth = 3f
                    }
                    view.overlays.add(dot)
                }

                currentLocation?.let { pos ->
                    val userDot = Polygon(view).apply {
                        points = Polygon.pointsAsCircle(pos, 10.0)
                        fillPaint.color = Color(0xFF0284C7).toArgb()
                        outlinePaint.color = android.graphics.Color.WHITE
                        outlinePaint.strokeWidth = 3f
                    }
                    view.overlays.add(userDot)
                }

                view.invalidate()
            },
        )

        // Top Route Designer HUD
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
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                    imageVector = Icons.Rounded.EditLocationAlt,
                                    contentDescription = null,
                                    tint = sportAccentColor,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(20.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Éditeur de trajet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (editorState.waypoints.isEmpty()) {
                                        "Touchez la carte pour tracer"
                                    } else {
                                        "${editorState.waypoints.size} repère${if (editorState.waypoints.size > 1) "s" else ""}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = sportAccentColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "+${editorState.estimatedPoints} pts",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = sportAccentColor,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    // Compact 2-sport selector matching RideScreen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SportType.entries.forEach { sport ->
                            val selected = sport == editorState.sportType
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
                                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("editor_sport_${sport.id}"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = chipIcon,
                                        contentDescription = null,
                                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
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
                            }
                        }
                    }

                    // Route metrics summary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        EditorMetricItem(
                            label = "Distance",
                            value = String.format(Locale.FRANCE, "%.2f km", editorState.distanceMeters / 1000.0),
                        )
                        EditorMetricItem(
                            label = "Durée est.",
                            value = "${editorState.estimatedDurationMinutes} min",
                        )
                        EditorMetricItem(
                            label = "Points est.",
                            value = "+${editorState.estimatedPoints}",
                            highlightColor = sportAccentColor,
                        )
                    }
                }
            }

            if (editorState.waypoints.size < 2) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
                    shadowElevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Touchez la carte pour placer au moins 2 points",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Bottom Controls Dock
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (currentLocation != null) {
                SmallFloatingActionButton(
                    onClick = {
                        mapView.controller.animateTo(currentLocation, 15.5, 450L)
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = sportAccentColor,
                    modifier = Modifier.testTag("editor_recenter_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Recentrer sur ma position",
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onUndoWaypoint,
                            enabled = editorState.waypoints.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("editor_undo_button"),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Annuler", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
                        }

                        FilledTonalButton(
                            onClick = onCloseLoop,
                            enabled = editorState.waypoints.size >= 2,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("editor_loop_button"),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.Loop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Boucler", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
                        }

                        FilledTonalButton(
                            onClick = onClearRoute,
                            enabled = editorState.waypoints.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("editor_clear_button"),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Effacer", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showSavedRoutesDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("editor_saved_routes_button"),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Mes trajets (${savedRoutes.size})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Button(
                            onClick = { showSaveDialog = true },
                            enabled = editorState.waypoints.size >= 2 && !editorState.isSaving,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("editor_save_route_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = sportAccentColor,
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) {
                            if (editorState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Enregistrer", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveRouteDialog(
            editorState = editorState,
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                onSaveRoute(name)
                showSaveDialog = false
            },
        )
    }

    if (showSavedRoutesDialog) {
        SavedRoutesDialog(
            plannedRoutesState = plannedRoutesState,
            onDismiss = { showSavedRoutesDialog = false },
            onLoadInEditor = { route ->
                onLoadRoute(route)
                route.decodeWaypoints().firstOrNull()?.let { firstPt ->
                    mapView.controller.animateTo(GeoPoint(firstPt.latitude, firstPt.longitude), 15.0, 500L)
                }
                showSavedRoutesDialog = false
            },
            onFollowRoute = { route ->
                showSavedRoutesDialog = false
                onFollowRoute(route)
            },
            onDeleteRoute = onDeleteRoute,
        )
    }
}

@Composable
private fun EditorMetricItem(
    label: String,
    value: String,
    highlightColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
            color = highlightColor,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SaveRouteDialog(
    editorState: RouteEditorState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val defaultName = remember(editorState.sportType, editorState.distanceMeters) {
        val kmLabel = String.format(Locale.FRANCE, "%.1f km", editorState.distanceMeters / 1000.0)
        if (editorState.sportType == SportType.RUNNING) {
            "Course $kmLabel"
        } else {
            "Boucle vélo $kmLabel"
        }
    }
    var routeName by remember { mutableStateOf(defaultName) }
    val trimmed = routeName.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Save,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = "Enregistrer le trajet",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${editorState.sportType.labelFr} • ${String.format(Locale.FRANCE, "%.2f km", editorState.distanceMeters / 1000.0)} • +${editorState.estimatedPoints} pts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = routeName,
                    onValueChange = { if (it.length <= 80) routeName = it },
                    singleLine = true,
                    label = { Text("Nom du trajet") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("route_name_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(trimmed.ifEmpty { defaultName }) },
                enabled = trimmed.isNotEmpty(),
                modifier = Modifier.testTag("confirm_save_route_button"),
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
    )
}

@Composable
private fun SavedRoutesDialog(
    plannedRoutesState: UiState<List<PlannedRoute>>,
    onDismiss: () -> Unit,
    onLoadInEditor: (PlannedRoute) -> Unit,
    onFollowRoute: (PlannedRoute) -> Unit,
    onDeleteRoute: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Trajets conçus",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            when (plannedRoutesState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Text(
                        text = plannedRoutesState.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is UiState.Success -> {
                    val routes = plannedRoutesState.data
                    if (routes.isEmpty()) {
                        Text(
                            text = "Aucun trajet enregistré. Touchez la carte dans l'éditeur pour concevoir votre premier parcours.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(routes, key = { it.id }) { route ->
                                val sport = route.resolvedSportType()
                                val accent = if (sport == SportType.RUNNING) Color(0xFFE65100) else MaterialTheme.colorScheme.primary
                                val icon = if (sport == SportType.RUNNING) {
                                    Icons.AutoMirrored.Rounded.DirectionsRun
                                } else {
                                    Icons.AutoMirrored.Rounded.DirectionsBike
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(accent.copy(alpha = 0.16f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = accent,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = route.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = "${String.format(Locale.FRANCE, "%.2f km", route.distanceMeters / 1000.0)} • +${route.estimatedPoints} pts",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteRoute(route.id) },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("delete_planned_route_${route.id}"),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Supprimer ce trajet",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            OutlinedButton(
                                                onClick = { onLoadInEditor(route) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 42.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                shape = RoundedCornerShape(12.dp),
                                            ) {
                                                Text("Modifier", maxLines = 1, softWrap = false)
                                            }
                                            Button(
                                                onClick = { onFollowRoute(route) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 42.dp)
                                                    .testTag("follow_planned_route_${route.id}"),
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                shape = RoundedCornerShape(12.dp),
                                            ) {
                                                Icon(Icons.Rounded.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Suivre", maxLines = 1, softWrap = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        },
    )
}
