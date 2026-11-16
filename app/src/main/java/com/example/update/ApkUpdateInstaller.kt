package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class ApkUpdateInstaller(
    private val context: Context,
    private val client: OkHttpClient = GitHubUpdateService.defaultClient(),
) {
    private val appContext = context.applicationContext

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun downloadApk(release: GitHubReleaseInfo): Flow<ApkDownloadState> = flow {
        val downloadUrl = release.apkDownloadUrl
            ?: throw IllegalStateException("Aucun fichier APK trouvé dans cette version GitHub.")

        emit(ApkDownloadState.Downloading(progress = 0f, bytesRead = 0L, totalBytes = release.apkSizeBytes))

        val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        updatesDir.listFiles()?.forEach { oldFile ->
            if (oldFile.name.endsWith(".apk")) {
                oldFile.delete()
            }
        }

        val safeTag = release.versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val targetFile = File(updatesDir, "GoCycleMaps-$safeTag.apk")

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "GoCycleMaps-Updater")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Téléchargement échoué (HTTP ${response.code}).")
            }
            val body = response.body ?: throw IllegalStateException("Réponse vide lors du téléchargement.")
            val totalBytes = body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesReadTotal = 0L
                    var lastEmittedProgress = -1f
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesReadTotal += read
                        val progress = if (totalBytes > 0L) {
                            (bytesReadTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        if (progress - lastEmittedProgress >= 0.02f || bytesReadTotal == totalBytes) {
                            lastEmittedProgress = progress
                            emit(ApkDownloadState.Downloading(progress, bytesReadTotal, totalBytes))
                        }
                    }
                    output.flush()
                }
            }
        }

        emit(
            ApkDownloadState.ReadyToInstall(
                filePath = targetFile.absolutePath,
                needsUnknownSourcePermission = !canInstallPackages(),
            )
        )
    }.flowOn(Dispatchers.IO)

    fun launchInstaller(apkFilePath: String): Boolean {
        val file = File(apkFilePath)
        if (!file.exists()) return false
        if (!canInstallPackages()) {
            appContext.startActivity(buildUnknownSourcesSettingsIntent())
            return false
        }
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(installIntent)
        return true
    }

    fun openReleaseInBrowser(htmlUrl: String) {
        if (htmlUrl.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }
}
