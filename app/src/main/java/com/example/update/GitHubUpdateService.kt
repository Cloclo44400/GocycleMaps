package com.example.update

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GitHubUpdateService(
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun fetchLatestRelease(
        repoSlug: String,
        includePreReleases: Boolean,
    ): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val (owner, repo) = UpdateSettings.parseOwnerRepo(repoSlug)
                ?: throw IllegalArgumentException("Format du dépôt invalide. Utilisez « proprietaire/depot ».")

            val url = if (includePreReleases) {
                "https://api.github.com/repos/$owner/$repo/releases?per_page=1"
            } else {
                "https://api.github.com/repos/$owner/$repo/releases/latest"
            }

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "GoCycleMaps/${BuildConfig.VERSION_NAME}")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        404 -> "Dépôt GitHub introuvable ou aucune version publiée ($owner/$repo)."
                        403, 429 -> "Limite de requêtes GitHub atteinte. Réessayez plus tard."
                        else -> "Erreur GitHub (${response.code})."
                    }
                    throw IllegalStateException(message)
                }

                val releaseJson = if (includePreReleases) {
                    val array = JSONArray(bodyString)
                    if (array.length() == 0) {
                        throw IllegalStateException("Aucune version publiée sur $owner/$repo.")
                    }
                    array.getJSONObject(0)
                } else {
                    JSONObject(bodyString)
                }

                parseReleaseJson(releaseJson)
            }
        }
    }

    internal fun parseReleaseJson(json: JSONObject): GitHubReleaseInfo {
        val tagName = json.optString("tag_name", "").trim()
        require(tagName.isNotEmpty()) { "Tag de version manquant dans la release GitHub." }

        val versionName = tagName.removePrefix("v").removePrefix("V")
        val releaseName = json.optString("name", "").ifBlank { "Version $versionName" }
        val releaseNotes = json.optString("body", "").ifBlank { "Améliorations et corrections de bugs." }
        val htmlUrl = json.optString("html_url", "")
        val publishedAt = json.optString("published_at", "")
        val isPreRelease = json.optBoolean("prerelease", false)

        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = 0L

        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkName = name
                    apkUrl = asset.optString("browser_download_url", "").ifBlank { null }
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
        }

        return GitHubReleaseInfo(
            tagName = tagName,
            versionName = versionName,
            releaseName = releaseName,
            releaseNotes = releaseNotes,
            htmlUrl = htmlUrl,
            publishedAt = publishedAt,
            apkDownloadUrl = apkUrl,
            apkFileName = apkName,
            apkSizeBytes = apkSize,
            isPreRelease = isPreRelease,
        )
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
