package com.example.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.BikeRide
import com.example.data.model.CosmeticCatalog
import com.example.data.model.SportType
import com.example.data.model.UserProfile
import com.example.ui.components.PointsPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ProfileScreen(
    profile: UserProfile,
    ridesState: UiState<List<BikeRide>>,
    onUpdateDisplayName: (String) -> Unit,
    onDeleteRide: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onFollowRecordedRide: (BikeRide) -> Unit = {},
) {
    var showEditPseudoDialog by remember { mutableStateOf(false) }
    var selectedRideForMap by remember { mutableStateOf<BikeRide?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    PointsPill(points = profile.totalPointsEarned)
                }
            }

            item {
                CyclistIdentityCard(
                    profile = profile,
                    onEditPseudoClick = { showEditPseudoDialog = true },
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProfileStatCard(
                        label = "Distance totale",
                        value = String.format(Locale.FRANCE, "%.1f km", profile.totalDistanceMeters / 1000.0),
                        modifier = Modifier.weight(1f),
                    )
                    ProfileStatCard(
                        label = "Points cumulés",
                        value = "${profile.totalPointsEarned}",
                        modifier = Modifier.weight(1f),
                    )
                    ProfileStatCard(
                        label = "Sorties",
                        value = "${profile.totalRides}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SportBreakdownCard(
                        title = "Vélo",
                        icon = Icons.AutoMirrored.Rounded.DirectionsBike,
                        accentColor = MaterialTheme.colorScheme.primary,
                        distanceMeters = profile.cyclingDistanceMeters,
                        points = profile.cyclingPointsEarned,
                        sessions = profile.cyclingRides,
                        modifier = Modifier.weight(1f),
                    )
                    SportBreakdownCard(
                        title = "Course à pied",
                        icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                        accentColor = Color(0xFFE65100),
                        distanceMeters = profile.runningDistanceMeters,
                        points = profile.runningPointsEarned,
                        sessions = profile.runningRides,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Text(
                    text = "Journal des activités",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            when (ridesState) {
                is UiState.Loading -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Chargement des activités...",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = ridesState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (ridesState.data.isEmpty()) {
                        item {
                            EmptyRidesCard()
                        }
                    } else {
                        items(ridesState.data, key = { it.id }) { ride ->
                            RideHistoryCard(
                                ride = ride,
                                onClick = { selectedRideForMap = ride },
                                onDelete = {
                                    if (selectedRideForMap?.id == ride.id) {
                                        selectedRideForMap = null
                                    }
                                    onDeleteRide(ride.id)
                                },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_sign_out))
                }
            }
        }
    }

    if (showEditPseudoDialog) {
        EditPseudoDialog(
            currentName = profile.displayName,
            onDismiss = { showEditPseudoDialog = false },
            onConfirm = { updatedName ->
                onUpdateDisplayName(updatedName)
                showEditPseudoDialog = false
            },
        )
    }

    selectedRideForMap?.let { ride ->
        RideRouteMapDialog(
            ride = ride,
            onDismiss = { selectedRideForMap = null },
            onFollowOnSportMap = {
                selectedRideForMap = null
                onFollowRecordedRide(ride)
            },
        )
    }
}

@Composable
private fun CyclistIdentityCard(
    profile: UserProfile,
    onEditPseudoClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0F221A),
        tonalElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0C1E17),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            Color(0xFF0E2920),
                        )
                    )
                )
                .padding(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    modifier = Modifier.size(62.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.DirectionsBike,
                            contentDescription = null,
                            tint = Color(0xFFA7F3D0),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Pseudo du sportif",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFA7F3D0),
                    )
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FilledTonalButton(
                    onClick = onEditPseudoClick,
                    modifier = Modifier.testTag("edit_pseudo_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.profile_edit_pseudo),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Modifier")
                }
            }
        }
    }
}

@Composable
private fun SportBreakdownCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    distanceMeters: Long,
    points: Long,
    sessions: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "$points pts",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
            )
            Text(
                text = String.format(
                    Locale.FRANCE,
                    "%.1f km • %d sortie%s",
                    distanceMeters / 1000.0,
                    sessions,
                    if (sessions > 1) "s" else "",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EditPseudoDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draftName by remember(currentName) { mutableStateOf(currentName) }
    val trimmed = draftName.trim()
    val isValid = trimmed.length in 2..30

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.profile_edit_pseudo),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Ce pseudo apparaîtra dans les classements.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { if (it.length <= 30) draftName = it },
                    singleLine = true,
                    label = { Text("Nouveau pseudo") },
                    supportingText = {
                        Text(
                            if (trimmed.length < 2) "Minimum 2 caractères"
                            else "${trimmed.length}/30 caractères"
                        )
                    },
                    isError = draftName.isNotEmpty() && !isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pseudo_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(trimmed) },
                enabled = isValid,
                modifier = Modifier.testTag("save_pseudo_button"),
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
private fun ProfileStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyRidesCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_rides),
                contentDescription = "Aucune activité enregistrée",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Text(
                text = "Aucune sortie pour le moment",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Choisissez Vélo (10 pts/km) ou Course à pied (25 pts/100m) dans l'onglet Sport pour cumuler des points.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RideHistoryCard(
    ride: BikeRide,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val sport = ride.resolvedSportType()
    val accentColor = if (sport == SportType.RUNNING) {
        Color(0xFFE65100)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val sportIcon = if (sport == SportType.RUNNING) {
        Icons.AutoMirrored.Rounded.DirectionsRun
    } else {
        Icons.AutoMirrored.Rounded.DirectionsBike
    }
    val dateLabel = formatFrenchDate(ride.createdAt ?: Timestamp.now())
    val hasRecordedRoute = remember(ride.waypointsJson) { ride.hasRecordedRoute() }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ride_history_card_${ride.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = sportIcon,
                        contentDescription = sport.labelFr,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = ride.startLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${sport.labelFr} • $dateLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_ride_${ride.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Supprimer cette activité",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FlowRow(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = String.format(Locale.FRANCE, "%.2f km", ride.distanceMeters / 1000.0),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = formatDuration(ride.durationSeconds),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = String.format(Locale.FRANCE, "%.1f km/h", ride.avgSpeedKmh),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "+${ride.pointsEarned}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Map,
                    contentDescription = null,
                    tint = if (hasRecordedRoute) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (hasRecordedRoute) {
                        "Toucher pour afficher le parcours sur la carte"
                    } else {
                        "Toucher pour voir les détails (tracé antérieur à la v2.3)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (hasRecordedRoute) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (hasRecordedRoute) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RideRouteMapDialog(
    ride: BikeRide,
    onDismiss: () -> Unit,
    onFollowOnSportMap: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sport = ride.resolvedSportType()
    val accentColor = if (sport == SportType.RUNNING) {
        Color(0xFFE65100)
    } else {
        CosmeticCatalog.trailColorFor(ride.trailCosmeticId)
    }
    val geoPoints = remember(ride.waypointsJson) {
        ride.decodeWaypoints().map { GeoPoint(it.latitude, it.longitude) }
    }
    val hasRecordedRoute = geoPoints.size >= 2
    val dateLabel = formatFrenchDate(ride.createdAt ?: Timestamp.now())

    val mapView = remember(context, ride.id) {
        val appContext = context.applicationContext
        val osmdroidConfig = Configuration.getInstance()
        osmdroidConfig.userAgentValue = appContext.packageName
        osmdroidConfig.osmdroidBasePath = File(appContext.cacheDir, "osmdroid").apply { mkdirs() }
        osmdroidConfig.osmdroidTileCache = File(appContext.cacheDir, "osmdroid/tiles").apply { mkdirs() }

        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.0)
            controller.setCenter(geoPoints.firstOrNull() ?: GeoPoint(48.8566, 2.3522))
        }
    }

    val routeLine = remember(mapView) {
        Polyline(mapView).apply {
            outlinePaint.strokeWidth = 14f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
        }
    }

    val fitRouteOnMap: () -> Unit = remember(mapView, geoPoints) {
        {
            if (geoPoints.size >= 2) {
                mapView.post {
                    val bbox = BoundingBox.fromGeoPoints(geoPoints)
                    val latSpan = kotlin.math.abs(bbox.latNorth - bbox.latSouth)
                    val lonSpan = kotlin.math.abs(bbox.lonEast - bbox.lonWest)
                    if (latSpan > 0.0002 || lonSpan > 0.0002) {
                        mapView.zoomToBoundingBox(bbox.increaseByScale(1.3f), false, 48)
                    } else {
                        mapView.controller.setZoom(16.5)
                        mapView.controller.setCenter(geoPoints.first())
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        fitRouteOnMap()
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .testTag("ride_route_map_dialog"),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.16f),
                    ) {
                        Icon(
                            imageVector = if (sport == SportType.RUNNING) {
                                Icons.AutoMirrored.Rounded.DirectionsRun
                            } else {
                                Icons.AutoMirrored.Rounded.DirectionsBike
                            },
                            contentDescription = sport.labelFr,
                            tint = accentColor,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ride.startLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${sport.labelFr} • $dateLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_ride_map_dialog"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fermer la carte",
                        )
                    }
                }

                // Interactive OpenStreetMap Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(18.dp)),
                ) {
                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("ride_history_map_view"),
                        update = { view ->
                            view.overlays.clear()

                            if (geoPoints.size >= 2) {
                                routeLine.outlinePaint.color = accentColor.toArgb()
                                routeLine.setPoints(geoPoints)
                                view.overlays.add(routeLine)

                                val startDot = Polygon(view).apply {
                                    points = Polygon.pointsAsCircle(geoPoints.first(), 13.0)
                                    fillPaint.color = Color(0xFF16A34A).toArgb()
                                    outlinePaint.color = android.graphics.Color.WHITE
                                    outlinePaint.strokeWidth = 3f
                                }
                                view.overlays.add(startDot)

                                val endDot = Polygon(view).apply {
                                    points = Polygon.pointsAsCircle(geoPoints.last(), 13.0)
                                    fillPaint.color = Color(0xFFDC2626).toArgb()
                                    outlinePaint.color = android.graphics.Color.WHITE
                                    outlinePaint.strokeWidth = 3f
                                }
                                view.overlays.add(endDot)
                            }

                            view.invalidate()
                        },
                    )

                    if (hasRecordedRoute) {
                        SmallFloatingActionButton(
                            onClick = fitRouteOnMap,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = accentColor,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .testTag("fit_ride_route_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CenterFocusStrong,
                                contentDescription = "Recentrer sur le parcours",
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(20.dp),
                        ) {
                            Text(
                                text = "Aucun tracé GPS enregistré pour cette sortie (antérieure à la version 2.3).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                // Bottom Stats & Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = String.format(Locale.FRANCE, "%.2f km", ride.distanceMeters / 1000.0),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = formatDuration(ride.durationSeconds),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = String.format(Locale.FRANCE, "%.1f km/h", ride.avgSpeedKmh),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "+${ride.pointsEarned} pts",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Fermer")
                        }
                        if (hasRecordedRoute) {
                            Button(
                                onClick = onFollowOnSportMap,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .heightIn(min = 48.dp)
                                    .testTag("follow_recorded_ride_button"),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Navigation,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Suivre sur Sport",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFrenchDate(timestamp: Timestamp): String {
    val formatter = SimpleDateFormat("d MMM yyyy • HH:mm", Locale.FRANCE)
    return formatter.format(timestamp.toDate())
}
