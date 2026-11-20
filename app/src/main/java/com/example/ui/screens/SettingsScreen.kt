package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.R
import com.example.location.BatteryOptimizationHelper
import com.example.location.PowerMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.update.ApkDownloadState
import com.example.update.GitHubReleaseInfo
import com.example.update.UpdateCheckStatus
import com.example.update.UpdateNotificationHelper
import com.example.update.UpdateSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    powerMode: PowerMode,
    onSelectPowerMode: (PowerMode) -> Unit,
    settings: UpdateSettings,
    checkStatus: UpdateCheckStatus,
    downloadState: ApkDownloadState,
    onSaveSettings: (String, Boolean, Boolean, Boolean) -> Boolean,
    onCheckForUpdates: () -> Unit,
    onDownloadAndInstall: (GitHubReleaseInfo) -> Unit,
    onInstallReadyApk: (String) -> Unit,
    onOpenReleaseInBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var repoInput by remember(settings.githubRepoSlug) { mutableStateOf(settings.githubRepoSlug) }
    var autoCheck by remember(settings.autoCheckEnabled) { mutableStateOf(settings.autoCheckEnabled) }
    var notificationsEnabled by remember(settings.notificationsEnabled) { mutableStateOf(settings.notificationsEnabled) }
    var includePreReleases by remember(settings.includePreReleases) { mutableStateOf(settings.includePreReleases) }
    var hasNotifPermission by remember { mutableStateOf(UpdateNotificationHelper.hasNotificationPermission(context)) }
    var isUnrestrictedBattery by remember { mutableStateOf(BatteryOptimizationHelper.isUnrestrictedBattery(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isUnrestrictedBattery = BatteryOptimizationHelper.isUnrestrictedBattery(context)
                hasNotifPermission = UpdateNotificationHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    val isRepoValid = UpdateSettings.parseOwnerRepo(repoInput) != null

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
                    Column {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Version installée : v${BuildConfig.VERSION_NAME}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(24.dp),
                        )
                    }
                }
            }

            // Battery & GPS Power Mode Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Gestion de la batterie",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Choisissez comment GoCycleMaps gère la fréquence GPS et la batterie pendant vos sorties.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        PowerModeOptionCard(
                            mode = PowerMode.ECO,
                            selected = powerMode == PowerMode.ECO,
                            icon = Icons.Rounded.BatterySaver,
                            description = "Avec limitation de la batterie : économise l'énergie avec des relevés GPS espacés (5 s / 8 m), suivi maintenu en sortie.",
                            onClick = { onSelectPowerMode(PowerMode.ECO) },
                        )

                        PowerModeOptionCard(
                            mode = PowerMode.STANDARD,
                            selected = powerMode == PowerMode.STANDARD,
                            icon = Icons.Rounded.BatteryChargingFull,
                            description = "Aucune restriction de batterie : précision GPS maximale en continu (2 s / 2 m), même téléphone en veille.",
                            onClick = { onSelectPowerMode(PowerMode.STANDARD) },
                        )

                        if (powerMode == PowerMode.STANDARD) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isUnrestrictedBattery) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
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
                                        Icon(
                                            imageVector = if (isUnrestrictedBattery) {
                                                Icons.Rounded.CheckCircle
                                            } else {
                                                Icons.Rounded.WarningAmber
                                            },
                                            contentDescription = null,
                                            tint = if (isUnrestrictedBattery) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            },
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = if (isUnrestrictedBattery) {
                                                "Système Android : aucune restriction appliquée"
                                            } else {
                                                "Android limite encore l'app en arrière-plan"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isUnrestrictedBattery) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            },
                                        )
                                    }

                                    if (!isUnrestrictedBattery) {
                                        OutlinedButton(
                                            onClick = { BatteryOptimizationHelper.openAppBatterySettings(context) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("open_battery_settings_button"),
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Passer en « Non restreinte » (Android)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // GitHub Repository Configuration
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Source GitHub des mises à jour",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Les nouvelles versions APK publiées dans GitHub Releases seront détectées automatiquement depuis ce dépôt.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            singleLine = true,
                            label = { Text("Dépôt GitHub (proprietaire/depot)") },
                            placeholder = { Text("cloclo44400/GoCycleMaps") },
                            isError = repoInput.isNotEmpty() && !isRepoValid,
                            supportingText = {
                                Text(
                                    if (isRepoValid) "API GitHub Releases prête"
                                    else "Format attendu : utilisateur/nom-du-depot"
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_repo_input"),
                        )

                        SettingSwitchRow(
                            title = "Vérification automatique",
                            subtitle = "Chercher une nouvelle version à chaque ouverture",
                            checked = autoCheck,
                            onCheckedChange = {
                                autoCheck = it
                                onSaveSettings(repoInput, autoCheck, notificationsEnabled, includePreReleases)
                            },
                            testTag = "auto_check_switch",
                        )

                        SettingSwitchRow(
                            title = "Notifications de mise à jour",
                            subtitle = "Recevoir une alerte Android dès qu'une version sort",
                            checked = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                notificationsEnabled = enabled
                                if (enabled && !hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onSaveSettings(repoInput, autoCheck, notificationsEnabled, includePreReleases)
                            },
                            testTag = "notifications_switch",
                        )

                        if (notificationsEnabled && !hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            OutlinedButton(
                                onClick = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_notifications_button"),
                            ) {
                                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Autoriser les notifications Android")
                            }
                        }

                        SettingSwitchRow(
                            title = "Inclure les pré-versions (bêta)",
                            subtitle = "Détecter également les tags pré-release",
                            checked = includePreReleases,
                            onCheckedChange = {
                                includePreReleases = it
                                onSaveSettings(repoInput, autoCheck, notificationsEnabled, includePreReleases)
                            },
                            testTag = "prerelease_switch",
                        )

                        Button(
                            onClick = {
                                if (onSaveSettings(repoInput, autoCheck, notificationsEnabled, includePreReleases)) {
                                    onCheckForUpdates()
                                }
                            },
                            enabled = isRepoValid && checkStatus !is UpdateCheckStatus.Checking,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("save_and_check_updates_button"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Enregistrer et vérifier")
                        }
                    }
                }
            }

            // Update Check & Installer Status Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "État des mises à jour",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (settings.lastCheckedAtMillis > 0L) {
                                Text(
                                    text = formatUpdateDate(settings.lastCheckedAtMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        when (checkStatus) {
                            is UpdateCheckStatus.Idle -> {
                                Text(
                                    text = "Appuyez sur le bouton ci-dessous pour vérifier si une nouvelle version est disponible sur GitHub.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            is UpdateCheckStatus.Checking -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                    Text("Vérification sur GitHub en cours...")
                                }
                            }

                            is UpdateCheckStatus.UpToDate -> {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        Text(
                                            text = "Application à jour (v${checkStatus.currentVersion})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }

                            is UpdateCheckStatus.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        Text(
                                            text = checkStatus.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                            }

                            is UpdateCheckStatus.UpdateAvailable -> {
                                UpdateAvailableSection(
                                    release = checkStatus.release,
                                    downloadState = downloadState,
                                    onDownloadAndInstall = { onDownloadAndInstall(checkStatus.release) },
                                    onInstallReadyApk = onInstallReadyApk,
                                    onOpenReleaseInBrowser = { onOpenReleaseInBrowser(checkStatus.release.htmlUrl) },
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onCheckForUpdates,
                            enabled = checkStatus !is UpdateCheckStatus.Checking,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("check_updates_button"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Vérifier maintenant")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
fun UpdateAvailableSection(
    release: GitHubReleaseInfo,
    downloadState: ApkDownloadState,
    onDownloadAndInstall: () -> Unit,
    onInstallReadyApk: (String) -> Unit,
    onOpenReleaseInBrowser: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Nouvelle version : ${release.tagName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                if (release.apkSizeBytes > 0L) {
                    Text(
                        text = String.format(Locale.FRANCE, "%.1f Mo", release.apkSizeBytes / (1024f * 1024f)),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Text(
                text = release.releaseNotes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )

            when (downloadState) {
                is ApkDownloadState.Downloading -> {
                    val percent = (downloadState.progress * 100).toInt()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                        )
                        Text(
                            text = "Téléchargement de l'APK : $percent %",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                is ApkDownloadState.ReadyToInstall -> {
                    Button(
                        onClick = { onInstallReadyApk(downloadState.filePath) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("launch_apk_installer_button"),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (downloadState.needsUnknownSourcePermission) {
                                "Autoriser & Installer l'APK"
                            } else {
                                "Installer maintenant"
                            }
                        )
                    }
                }

                is ApkDownloadState.Error -> {
                    Text(
                        text = downloadState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = onDownloadAndInstall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Réessayer le téléchargement")
                    }
                }

                is ApkDownloadState.Idle -> {
                    if (release.apkDownloadUrl != null) {
                        Button(
                            onClick = onDownloadAndInstall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("install_update_button"),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Télécharger et installer")
                        }
                    } else {
                        Text(
                            text = "Aucun fichier .apk joint à cette release GitHub.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            if (release.htmlUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = onOpenReleaseInBrowser,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("open_github_release_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ouvrir la page GitHub")
                }
            }
        }
    }
}

@Composable
fun UpdateAvailableDialog(
    release: GitHubReleaseInfo,
    downloadState: ApkDownloadState,
    onDismiss: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onInstallReadyApk: (String) -> Unit,
    onOpenReleaseInBrowser: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = "Mise à jour disponible (${release.tagName})",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            UpdateAvailableSection(
                release = release,
                downloadState = downloadState,
                onDownloadAndInstall = onDownloadAndInstall,
                onInstallReadyApk = onInstallReadyApk,
                onOpenReleaseInBrowser = onOpenReleaseInBrowser,
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Plus tard")
            }
        },
    )
}

@Composable
private fun PowerModeOptionCard(
    mode: PowerMode,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag("power_mode_${mode.id}"),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = mode.labelFr,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = if (selected) {
                    Icons.Rounded.RadioButtonChecked
                } else {
                    Icons.Rounded.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun formatUpdateDate(millis: Long): String {
    val formatter = SimpleDateFormat("d MMM • HH:mm", Locale.FRANCE)
    return formatter.format(Date(millis))
}
