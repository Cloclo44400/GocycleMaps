package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.signOutUser
import com.example.data.model.UserProfile
import com.example.data.repository.CyclingRepository
import com.example.data.repository.LocalGuestRepository
import com.example.location.RideLocationTracker
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RideScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UpdateAvailableDialog
import com.example.ui.theme.VeloQuestTheme
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.UpdateViewModel
import com.example.ui.viewmodel.VeloQuestViewModel
import com.example.update.UpdateCheckStatus
import com.example.update.UpdateNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_RIDE = "ride"
const val ROUTE_EDITOR = "editor"
const val ROUTE_LEADERBOARD = "leaderboard"
const val ROUTE_PROFILE = "profile"
const val ROUTE_SETTINGS = "settings"

private data class VeloTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

private val VELO_TABS = listOf(
    VeloTab(
        route = ROUTE_RIDE,
        labelRes = R.string.nav_ride,
        selectedIcon = Icons.AutoMirrored.Rounded.DirectionsBike,
        unselectedIcon = Icons.AutoMirrored.Outlined.DirectionsBike,
        testTag = "nav_tab_ride",
    ),
    VeloTab(
        route = ROUTE_EDITOR,
        labelRes = R.string.nav_editor,
        selectedIcon = Icons.Rounded.EditLocationAlt,
        unselectedIcon = Icons.Outlined.EditLocationAlt,
        testTag = "nav_tab_editor",
    ),
    VeloTab(
        route = ROUTE_LEADERBOARD,
        labelRes = R.string.nav_leaderboard,
        selectedIcon = Icons.Rounded.EmojiEvents,
        unselectedIcon = Icons.Outlined.EmojiEvents,
        testTag = "nav_tab_leaderboard",
    ),
    VeloTab(
        route = ROUTE_PROFILE,
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Rounded.Person,
        unselectedIcon = Icons.Outlined.Person,
        testTag = "nav_tab_profile",
    ),
    VeloTab(
        route = ROUTE_SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings",
    ),
)

class MainActivity : ComponentActivity() {
    private var openUpdatesOnStart by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openUpdatesOnStart = intent?.getBooleanExtra(UpdateNotificationHelper.EXTRA_OPEN_UPDATES, false) == true
        enableEdgeToEdge()
        setContent {
            VeloQuestTheme {
                AppNavigation(
                    openUpdatesFromNotification = openUpdatesOnStart,
                    onConsumedOpenUpdates = { openUpdatesOnStart = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(UpdateNotificationHelper.EXTRA_OPEN_UPDATES, false)) {
            openUpdatesOnStart = true
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    openUpdatesFromNotification: Boolean = false,
    onConsumedOpenUpdates: () -> Unit = {},
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    var isGuestBypassActive by rememberSaveable { mutableStateOf(false) }

    if (user == null && !isGuestBypassActive) {
        AuthScreen(
            onAuthSuccess = { isGuestBypassActive = false },
            onBypassSignIn = { isGuestBypassActive = true },
        )
    } else {
        val isGuestMode = user == null
        val activeUserId = user?.uid ?: LocalGuestRepository.GUEST_USER_ID
        val activeDisplayName = user?.displayName ?: "Invité"
        key(activeUserId) {
            MainFeatureScreen(
                currentUserId = activeUserId,
                initialDisplayName = activeDisplayName,
                isGuestMode = isGuestMode,
                onExitGuestMode = { isGuestBypassActive = false },
                openUpdatesFromNotification = openUpdatesFromNotification,
                onConsumedOpenUpdates = onConsumedOpenUpdates,
            )
        }
    }
}

@Composable
fun MainFeatureScreen(
    currentUserId: String,
    initialDisplayName: String,
    isGuestMode: Boolean = false,
    onExitGuestMode: () -> Unit = {},
    openUpdatesFromNotification: Boolean = false,
    onConsumedOpenUpdates: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: VeloQuestViewModel = viewModel(
        key = "${currentUserId}_$isGuestMode",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val repository = if (isGuestMode) {
                    LocalGuestRepository(app)
                } else {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    CyclingRepository(db, Firebase.auth)
                }
                VeloQuestViewModel(
                    repository = repository,
                    locationTracker = RideLocationTracker(app),
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName,
                )
            }
        },
    )

    val updateViewModel: UpdateViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                UpdateViewModel(context = app)
            }
        },
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val ridesState by viewModel.ridesState.collectAsStateWithLifecycle()
    val leaderboardCategory by viewModel.leaderboardCategory.collectAsStateWithLifecycle()
    val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
    val rideSession by viewModel.rideSession.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val plannedRoutesState by viewModel.plannedRoutesState.collectAsStateWithLifecycle()

    val updateSettings by updateViewModel.settings.collectAsStateWithLifecycle()
    val updateCheckStatus by updateViewModel.checkStatus.collectAsStateWithLifecycle()
    val apkDownloadState by updateViewModel.downloadState.collectAsStateWithLifecycle()
    val showUpdateDialog by updateViewModel.showUpdateDialog.collectAsStateWithLifecycle()

    val availableUpdate = (updateCheckStatus as? UpdateCheckStatus.UpdateAvailable)?.release

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    LaunchedEffect(updateViewModel) {
        updateViewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(openUpdatesFromNotification) {
        if (openUpdatesFromNotification) {
            navigateToTab(ROUTE_SETTINGS)
            updateViewModel.openUpdateDialog()
            onConsumedOpenUpdates()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        VELO_TABS.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    TabIconWithUpdateBadge(
                                        tab = tab,
                                        selected = selected,
                                        hasUpdate = tab.route == ROUTE_SETTINGS && availableUpdate != null,
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        windowInsets = WindowInsets.safeDrawing,
                    ) {
                        VELO_TABS.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    TabIconWithUpdateBadge(
                                        tab = tab,
                                        selected = selected,
                                        hasUpdate = tab.route == ROUTE_SETTINGS && availableUpdate != null,
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }

                val profile = (profileState as? UiState.Success<UserProfile>)?.data
                    ?: UserProfile(userId = currentUserId, displayName = initialDisplayName)

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_RIDE,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                ) {
                    composable(ROUTE_RIDE) {
                        RideScreen(
                            profile = (profileState as? UiState.Success<UserProfile>)?.data,
                            rideSession = rideSession,
                            availableUpdate = availableUpdate,
                            onOpenUpdateDialog = updateViewModel::openUpdateDialog,
                            onSelectSport = viewModel::selectSport,
                            onClearGuideRoute = viewModel::clearActiveGuideRoute,
                            onLocationPermissionGranted = viewModel::onLocationPermissionGranted,
                            onStartRide = viewModel::startRide,
                            onPauseRide = viewModel::pauseRide,
                            onResumeRide = viewModel::resumeRide,
                            onFinishRide = viewModel::finishRide,
                            onDismissSummary = viewModel::dismissSummary,
                            onNavigateToLeaderboard = { navigateToTab(ROUTE_LEADERBOARD) },
                        )
                    }
                    composable(ROUTE_EDITOR) {
                        EditorScreen(
                            editorState = editorState,
                            plannedRoutesState = plannedRoutesState,
                            currentLocation = rideSession.currentLocation,
                            onSelectSport = viewModel::selectEditorSport,
                            onAddWaypoint = viewModel::addEditorWaypoint,
                            onUndoWaypoint = viewModel::undoEditorWaypoint,
                            onCloseLoop = viewModel::closeEditorLoop,
                            onClearRoute = viewModel::clearEditorRoute,
                            onSaveRoute = viewModel::saveEditorRoute,
                            onLoadRoute = viewModel::loadRouteIntoEditor,
                            onFollowRoute = { route ->
                                viewModel.followPlannedRoute(route)
                                navigateToTab(ROUTE_RIDE)
                            },
                            onDeleteRoute = viewModel::deletePlannedRoute,
                        )
                    }
                    composable(ROUTE_LEADERBOARD) {
                        LeaderboardScreen(
                            entriesState = leaderboardState,
                            selectedCategory = leaderboardCategory,
                            onSelectCategory = viewModel::selectLeaderboardCategory,
                            currentUserId = currentUserId,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    composable(ROUTE_PROFILE) {
                        if (profileState is UiState.Loading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            ProfileScreen(
                                profile = profile,
                                ridesState = ridesState,
                                onUpdateDisplayName = viewModel::updateDisplayName,
                                onDeleteRide = viewModel::deleteRide,
                                onSignOut = {
                                    if (isGuestMode) {
                                        onExitGuestMode()
                                    } else {
                                        signOutUser(
                                            credentialManager = credentialManager,
                                            scope = scope,
                                        )
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            settings = updateSettings,
                            checkStatus = updateCheckStatus,
                            downloadState = apkDownloadState,
                            onSaveSettings = updateViewModel::saveSettings,
                            onCheckForUpdates = { updateViewModel.checkForUpdates(fromAutoLaunch = false) },
                            onDownloadAndInstall = updateViewModel::startDownloadAndInstall,
                            onInstallReadyApk = updateViewModel::installDownloadedApk,
                            onOpenReleaseInBrowser = updateViewModel::openReleaseInBrowser,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showUpdateDialog && availableUpdate != null) {
        UpdateAvailableDialog(
            release = availableUpdate,
            downloadState = apkDownloadState,
            onDismiss = updateViewModel::dismissUpdateDialog,
            onDownloadAndInstall = { updateViewModel.startDownloadAndInstall(availableUpdate) },
            onInstallReadyApk = updateViewModel::installDownloadedApk,
            onOpenReleaseInBrowser = { updateViewModel.openReleaseInBrowser(availableUpdate.htmlUrl) },
        )
    }
}

@Composable
private fun TabIconWithUpdateBadge(
    tab: VeloTab,
    selected: Boolean,
    hasUpdate: Boolean,
) {
    val iconVector = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (hasUpdate) {
        BadgedBox(badge = { Badge() }) {
            Icon(
                imageVector = iconVector,
                contentDescription = stringResource(tab.labelRes),
            )
        }
    } else {
        Icon(
            imageVector = iconVector,
            contentDescription = stringResource(tab.labelRes),
        )
    }
}
