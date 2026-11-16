package com.example.update

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val isPreRelease: Boolean,
)

data class UpdateSettings(
    val githubRepoSlug: String = DEFAULT_GITHUB_REPO,
    val autoCheckEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val includePreReleases: Boolean = false,
    val lastCheckedAtMillis: Long = 0L,
    val lastNotifiedTag: String? = null,
) {
    val isRepoConfigured: Boolean
        get() = parseOwnerRepo(githubRepoSlug) != null

    companion object {
        const val DEFAULT_GITHUB_REPO = "cloclo44400/GoCycleMaps"

        fun parseOwnerRepo(raw: String): Pair<String, String>? {
            val cleaned = raw.trim()
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .trim('/')
            val parts = cleaned.split("/")
            if (parts.size != 2) return null
            val owner = parts[0].trim()
            val repo = parts[1].trim()
            if (owner.isEmpty() || repo.isEmpty()) return null
            if (!owner.matches(Regex("^[a-zA-Z0-9._-]+$")) || !repo.matches(Regex("^[a-zA-Z0-9._-]+$"))) {
                return null
            }
            return owner to repo
        }
    }
}

sealed interface UpdateCheckStatus {
    data object Idle : UpdateCheckStatus
    data object Checking : UpdateCheckStatus
    data class UpToDate(val currentVersion: String) : UpdateCheckStatus
    data class UpdateAvailable(val release: GitHubReleaseInfo) : UpdateCheckStatus
    data class Error(val message: String) : UpdateCheckStatus
}

sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data class Downloading(val progress: Float, val bytesRead: Long, val totalBytes: Long) : ApkDownloadState
    data class ReadyToInstall(val filePath: String, val needsUnknownSourcePermission: Boolean) : ApkDownloadState
    data class Error(val message: String) : ApkDownloadState
}

object VersionComparator {
    fun normalize(version: String): List<Int> {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        val core = cleaned.substringBefore('-').substringBefore('+')
        return core.split('.')
            .mapNotNull { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() }
            .ifEmpty { listOf(0) }
    }

    fun isRemoteNewer(currentVersion: String, remoteTagOrVersion: String): Boolean {
        val current = normalize(currentVersion)
        val remote = normalize(remoteTagOrVersion)
        val maxLen = maxOf(current.size, remote.size)
        for (i in 0 until maxLen) {
            val c = current.getOrElse(i) { 0 }
            val r = remote.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}

class UpdatePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<UpdateSettings> = _settings.asStateFlow()

    private fun readSettings(): UpdateSettings {
        val storedRepo = prefs.getString(KEY_REPO_SLUG, UpdateSettings.DEFAULT_GITHUB_REPO)
            ?: UpdateSettings.DEFAULT_GITHUB_REPO
        val resolvedRepo = if (storedRepo == "cloclousa/GoCycleMaps") {
            prefs.edit { putString(KEY_REPO_SLUG, UpdateSettings.DEFAULT_GITHUB_REPO) }
            UpdateSettings.DEFAULT_GITHUB_REPO
        } else {
            storedRepo
        }
        return UpdateSettings(
            githubRepoSlug = resolvedRepo,
            autoCheckEnabled = prefs.getBoolean(KEY_AUTO_CHECK, true),
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
            includePreReleases = prefs.getBoolean(KEY_PRE_RELEASES, false),
            lastCheckedAtMillis = prefs.getLong(KEY_LAST_CHECKED, 0L),
            lastNotifiedTag = prefs.getString(KEY_LAST_NOTIFIED_TAG, null),
        )
    }

    fun saveSettings(
        githubRepoSlug: String = _settings.value.githubRepoSlug,
        autoCheckEnabled: Boolean = _settings.value.autoCheckEnabled,
        notificationsEnabled: Boolean = _settings.value.notificationsEnabled,
        includePreReleases: Boolean = _settings.value.includePreReleases,
    ) {
        val normalizedSlug = UpdateSettings.parseOwnerRepo(githubRepoSlug)
            ?.let { (o, r) -> "$o/$r" }
            ?: githubRepoSlug.trim()

        prefs.edit {
            putString(KEY_REPO_SLUG, normalizedSlug)
            putBoolean(KEY_AUTO_CHECK, autoCheckEnabled)
            putBoolean(KEY_NOTIFICATIONS, notificationsEnabled)
            putBoolean(KEY_PRE_RELEASES, includePreReleases)
        }
        _settings.value = readSettings()
    }

    fun markChecked(nowMillis: Long, notifiedTag: String? = null) {
        prefs.edit {
            putLong(KEY_LAST_CHECKED, nowMillis)
            if (notifiedTag != null) {
                putString(KEY_LAST_NOTIFIED_TAG, notifiedTag)
            }
        }
        _settings.value = readSettings()
    }

    private companion object {
        const val PREFS_NAME = "gocyclemaps_update_prefs"
        const val KEY_REPO_SLUG = "github_repo_slug"
        const val KEY_AUTO_CHECK = "auto_check_enabled"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_PRE_RELEASES = "include_pre_releases"
        const val KEY_LAST_CHECKED = "last_checked_at"
        const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
    }
}
