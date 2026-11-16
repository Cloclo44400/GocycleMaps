package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.update.ApkDownloadState
import com.example.update.ApkUpdateInstaller
import com.example.update.GitHubReleaseInfo
import com.example.update.GitHubUpdateService
import com.example.update.UpdateCheckStatus
import com.example.update.UpdateNotificationHelper
import com.example.update.UpdatePreferences
import com.example.update.UpdateSettings
import com.example.update.VersionComparator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class UpdateViewModel(
    context: Context,
    private val preferences: UpdatePreferences = UpdatePreferences(context),
    private val updateService: GitHubUpdateService = GitHubUpdateService(),
    private val installer: ApkUpdateInstaller = ApkUpdateInstaller(context),
) : ViewModel() {

    private val appContext = context.applicationContext

    val settings: StateFlow<UpdateSettings> = preferences.settings

    private val _checkStatus = MutableStateFlow<UpdateCheckStatus>(UpdateCheckStatus.Idle)
    val checkStatus: StateFlow<UpdateCheckStatus> = _checkStatus.asStateFlow()

    private val _downloadState = MutableStateFlow<ApkDownloadState>(ApkDownloadState.Idle)
    val downloadState: StateFlow<ApkDownloadState> = _downloadState.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var downloadJob: Job? = null

    init {
        if (settings.value.autoCheckEnabled && settings.value.isRepoConfigured) {
            checkForUpdates(fromAutoLaunch = true)
        }
    }

    fun checkForUpdates(fromAutoLaunch: Boolean = false) {
        val currentSettings = settings.value
        if (!currentSettings.isRepoConfigured) {
            if (!fromAutoLaunch) {
                _checkStatus.value = UpdateCheckStatus.Error("Configurez d'abord un dépôt GitHub valide (proprietaire/depot).")
            }
            return
        }

        viewModelScope.launch {
            _checkStatus.value = UpdateCheckStatus.Checking
            val result = updateService.fetchLatestRelease(
                repoSlug = currentSettings.githubRepoSlug,
                includePreReleases = currentSettings.includePreReleases,
            )

            result.onSuccess { release ->
                val isNewer = VersionComparator.isRemoteNewer(BuildConfig.VERSION_NAME, release.tagName)
                if (isNewer) {
                    _checkStatus.value = UpdateCheckStatus.UpdateAvailable(release)
                    val shouldNotify = currentSettings.notificationsEnabled &&
                        (!fromAutoLaunch || currentSettings.lastNotifiedTag != release.tagName)
                    val notifiedTag = if (shouldNotify && UpdateNotificationHelper.showUpdateNotification(appContext, release)) {
                        release.tagName
                    } else {
                        null
                    }
                    preferences.markChecked(System.currentTimeMillis(), notifiedTag)
                } else {
                    preferences.markChecked(System.currentTimeMillis())
                    _checkStatus.value = UpdateCheckStatus.UpToDate(BuildConfig.VERSION_NAME)
                    if (!fromAutoLaunch) {
                        _events.tryEmit("Vous utilisez déjà la dernière version (${BuildConfig.VERSION_NAME}).")
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (fromAutoLaunch) {
                    _checkStatus.value = UpdateCheckStatus.Idle
                } else {
                    _checkStatus.value = UpdateCheckStatus.Error(
                        error.localizedMessage ?: "Impossible de vérifier les mises à jour sur GitHub."
                    )
                }
            }
        }
    }

    fun saveSettings(
        repoSlug: String,
        autoCheckEnabled: Boolean,
        notificationsEnabled: Boolean,
        includePreReleases: Boolean,
    ): Boolean {
        val parsed = UpdateSettings.parseOwnerRepo(repoSlug)
        if (parsed == null) {
            _events.tryEmit("Format invalide. Exemple attendu : proprietaire/GoCycleMaps")
            return false
        }
        preferences.saveSettings(
            githubRepoSlug = "${parsed.first}/${parsed.second}",
            autoCheckEnabled = autoCheckEnabled,
            notificationsEnabled = notificationsEnabled,
            includePreReleases = includePreReleases,
        )
        _events.tryEmit("Paramètres de mise à jour enregistrés.")
        return true
    }

    fun startDownloadAndInstall(release: GitHubReleaseInfo) {
        if (release.apkDownloadUrl == null) {
            openReleaseInBrowser(release.htmlUrl)
            return
        }
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            installer.downloadApk(release)
                .catch { error ->
                    if (error is CancellationException) throw error
                    _downloadState.value = ApkDownloadState.Error(
                        error.localizedMessage ?: "Échec du téléchargement de l'APK."
                    )
                }
                .collect { state ->
                    _downloadState.value = state
                    if (state is ApkDownloadState.ReadyToInstall && !state.needsUnknownSourcePermission) {
                        installer.launchInstaller(state.filePath)
                    }
                }
        }
    }

    fun installDownloadedApk(filePath: String) {
        val launched = installer.launchInstaller(filePath)
        if (!launched) {
            _events.tryEmit("Autorisez l'installation d'applications pour GoCycleMaps puis relancez l'installation.")
        }
    }

    fun openReleaseInBrowser(htmlUrl: String) {
        installer.openReleaseInBrowser(htmlUrl)
    }

    fun openUpdateDialog() {
        _showUpdateDialog.value = true
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun resetDownloadState() {
        downloadJob?.cancel()
        _downloadState.value = ApkDownloadState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
    }
}
