/*
 * SuiKernel Manager
 * Copyright (C) 2026 Kanagawa Yamada
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package kanagawa.yamada.suikernel.manager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class InstalledModuleInfo(
    val id: String,
    val name: String = "",
    val author: String = "",
    val version: String,
    val versionCode: Int,
    val hasPendingUpdate: Boolean = false,
    val updateJson: String = ""
)

enum class HostPlatform {
    GITHUB,
    GITLAB
}

data class DefinitionItem(
    val title: String,
    val author: String,
    val repo: String,
    val platform: HostPlatform = HostPlatform.GITHUB
)

// ----------------- MODULE VERSION CACHE HELPERS -----------------

private const val PREFS_MODULE_CACHE = "module_version_cache"

fun getCachedModuleVersion(context: Context, repo: String): String {
    return try {
        val prefs = context.getSharedPreferences(PREFS_MODULE_CACHE, Context.MODE_PRIVATE)
        val ver = prefs.getString("ver_$repo", "") ?: ""
        if (ver.isNotBlank()) return ver
        val clean = repo.removePrefix("gitlab:").removePrefix("github:").trim()
        val altVer = prefs.getString("ver_$clean", "") ?: ""
        if (altVer.isNotBlank()) return altVer
        if (clean.contains("AlwaysStrong", ignoreCase = true)) {
            prefs.getString("ver_evoker0/AlwaysStrong", "") ?: ""
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}

fun getCachedModuleDownloadUrl(context: Context, repo: String): String {
    return try {
        val prefs = context.getSharedPreferences(PREFS_MODULE_CACHE, Context.MODE_PRIVATE)
        val url = prefs.getString("url_$repo", "") ?: ""
        if (url.isNotBlank()) return url
        val clean = repo.removePrefix("gitlab:").removePrefix("github:").trim()
        val altUrl = prefs.getString("url_$clean", "") ?: ""
        if (altUrl.isNotBlank()) return altUrl
        if (clean.contains("AlwaysStrong", ignoreCase = true)) {
            prefs.getString("url_evoker0/AlwaysStrong", "") ?: ""
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}

fun saveCachedModuleInfo(context: Context, repo: String, version: String, downloadUrl: String = "") {
    try {
        val prefs = context.getSharedPreferences(PREFS_MODULE_CACHE, Context.MODE_PRIVATE).edit()
        if (version.isNotBlank()) {
            prefs.putString("ver_$repo", version)
        }
        if (downloadUrl.isNotBlank()) {
            prefs.putString("url_$repo", downloadUrl)
        }
        prefs.apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getLastUpdateCheckTime(context: Context): Long {
    return try {
        context.getSharedPreferences(PREFS_MODULE_CACHE, Context.MODE_PRIVATE)
            .getLong("last_check_timestamp", 0L)
    } catch (e: Exception) {
        0L
    }
}

fun setLastUpdateCheckTime(context: Context, timestamp: Long = System.currentTimeMillis()) {
    try {
        context.getSharedPreferences(PREFS_MODULE_CACHE, Context.MODE_PRIVATE).edit()
            .putLong("last_check_timestamp", timestamp)
            .apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ----------------- ROOT & GITHUB HELPER FUNCTIONS -----------------

fun fetchInstalledModules(): List<InstalledModuleInfo> {
    val list = mutableListOf<InstalledModuleInfo>()
    try {
        val ksudBin = SuiKernelIoctl.runCommandWithOutput("which ksud").firstOrNull()?.trim() ?: "ksud"
        val lines = SuiKernelIoctl.runCommandWithOutput("$ksudBin module list")
        val jsonStr = lines.joinToString("\n").trim()
        if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                val author = obj.optString("author", "")
                val version = obj.optString("version", "")
                val versionCode = obj.optInt("versionCode", 0)
                val hasPendingUpdate = obj.optBoolean("update", false)
                val updateJson = obj.optString("updateJson", "")
                if (id.isNotBlank() || name.isNotBlank()) {
                    list.add(InstalledModuleInfo(id, name, author, version, versionCode, hasPendingUpdate, updateJson))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun findInstalledModule(
    installedList: List<InstalledModuleInfo>,
    title: String,
    author: String,
    repo: String
): InstalledModuleInfo? {
    fun normKey(s: String): String = s.lowercase().replace("-", "").replace("_", "").replace(" ", "").trim()

    val cleanTitle = title.trim().lowercase()
    val cleanAuthor = author.trim().lowercase()
    val cleanRepo = repo.removePrefix("gitlab:").removePrefix("github:").trim()
    val repoProject = cleanRepo.substringAfterLast("/").trim().lowercase()

    val normTitle = normKey(title)
    val normRepoProject = normKey(repoProject)

    return installedList.firstOrNull { inst ->
        val instName = inst.name.trim().lowercase()
        val instId = inst.id.trim().lowercase()
        val instAuthor = inst.author.trim().lowercase()

        val normInstName = normKey(inst.name)
        val normInstId = normKey(inst.id)
        val normUpdateJson = normKey(inst.updateJson)

        // 1. Direct updateJson check
        if (inst.updateJson.isNotBlank() && (
            inst.updateJson.contains(repo, ignoreCase = true) ||
            inst.updateJson.contains(cleanRepo, ignoreCase = true) ||
            inst.updateJson.contains(repoProject, ignoreCase = true) ||
            (normRepoProject.isNotBlank() && normUpdateJson.contains(normRepoProject))
        )) {
            return@firstOrNull true
        }

        // 2. Name / title match
        val nameMatch = instName == cleanTitle ||
                normInstName == normTitle ||
                (normInstName.isNotBlank() && normTitle.isNotBlank() && (normInstName.contains(normTitle) || normTitle.contains(normInstName))) ||
                (instName.isNotBlank() && cleanTitle.contains(instName)) ||
                (cleanTitle.isNotBlank() && instName.contains(cleanTitle))

        // 3. ID match
        val idMatch = instId == repoProject ||
                normInstId == normRepoProject ||
                normInstId == normTitle ||
                (normRepoProject.isNotBlank() && normInstId.contains(normRepoProject)) ||
                (normInstId.isNotBlank() && normRepoProject.contains(normInstId))

        // 4. Author match
        val authorMatch = cleanAuthor.isNotBlank() && (
                instAuthor.contains(cleanAuthor) ||
                cleanAuthor.contains(instAuthor) ||
                inst.updateJson.contains(cleanAuthor, ignoreCase = true) ||
                (cleanAuthor.contains("evoker") && instAuthor.contains("evoker"))
        )

        (nameMatch && (authorMatch || instAuthor.isBlank())) || (idMatch && (authorMatch || instAuthor.isBlank()))
    }
}

fun fetchUpdateJson(updateJsonUrl: String): Triple<String, Int, String>? {
    if (updateJsonUrl.isBlank()) return null
    var jsonText = ""

    // Try standard HTTP
    try {
        val url = URL(updateJsonUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "SuiKernelManager")
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        if (conn.responseCode == 200) {
            jsonText = conn.inputStream.bufferedReader().use { it.readText() }
        }
    } catch (e: Exception) {
        // Fall back to root curl
    }

    // Fall back to root curl
    if (jsonText.isBlank()) {
        try {
            val cmd = "/system/bin/curl -s -L -k -A 'SuiKernelManager' --connect-timeout 8 '$updateJsonUrl'"
            val text = SuiKernelIoctl.runCommandWithOutput(cmd).joinToString("\n").trim()
            if (text.startsWith("{") && text.endsWith("}")) {
                jsonText = text
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (jsonText.isBlank()) return null

    return try {
        val json = JSONObject(jsonText)
        val version = json.optString("version", "")
        val versionCode = json.optInt("versionCode", 0)
        val zipUrl = json.optString("zipUrl", "")
        if (zipUrl.isNotBlank()) {
            Triple(version, versionCode, zipUrl)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

fun selectBestZip(urls: List<String>): String? {
    val nonArchive = urls.filter { !it.contains("/archive/", ignoreCase = true) }
    if (nonArchive.isEmpty()) return null
    // 1. Prefer filename containing 'release'
    val release = nonArchive.firstOrNull { it.substringAfterLast("/").contains("release", ignoreCase = true) }
    if (release != null) return release
    // 2. Prefer standard/clean filename (not containing debug, nopif, inject, test)
    val standard = nonArchive.firstOrNull {
        val name = it.substringAfterLast("/").lowercase()
        !name.contains("debug") && !name.contains("nopif") && !name.contains("inject") && !name.contains("test")
    }
    if (standard != null) return standard
    // 3. Prefer filename not containing 'debug'
    val nonDebug = nonArchive.firstOrNull { !it.substringAfterLast("/").contains("debug", ignoreCase = true) }
    if (nonDebug != null) return nonDebug
    return nonArchive.first()
}

fun buildCheckUrl(repo: String, platform: HostPlatform): String {
    val clean = repo.removePrefix("gitlab:").removePrefix("github:").trim()
        .removePrefix("https://gitlab.com/").removePrefix("https://github.com/").trimEnd('/')
    return when (platform) {
        HostPlatform.GITHUB -> "https://github.com/$clean/releases/latest"
        HostPlatform.GITLAB -> "https://gitlab.com/$clean/-/releases/permalink/latest"
    }
}

fun extractVersionFromLocation(locationHeader: String, platform: HostPlatform): String {
    val target = locationHeader.substringBefore("?").substringBefore("#").trimEnd('/')
    return when (platform) {
        HostPlatform.GITHUB -> target.substringAfterLast("/tag/")
        HostPlatform.GITLAB -> target.substringAfterLast("/releases/")
    }
}

fun fetchLatestTagViaHead(repo: String, platform: HostPlatform): String? {
    val checkUrl = buildCheckUrl(repo, platform)
    var tag: String? = null

    try {
        val conn = URL(checkUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 3500
        conn.readTimeout = 3500
        conn.setRequestProperty("User-Agent", "SuiKernelManager")
        val code = conn.responseCode
        val location = conn.getHeaderField("Location")
        conn.disconnect()

        if ((code == 301 || code == 302 || code == 307 || code == 308) && !location.isNullOrBlank()) {
            val t = extractVersionFromLocation(location, platform)
            if (t.isNotBlank() && !t.contains("/") && !t.startsWith("http")) {
                tag = t
            }
        }
    } catch (e: Exception) {
        // Fall back to curl
    }

    if (tag.isNullOrBlank()) {
        try {
            val cmd = "/system/bin/curl -s -I -k -A 'SuiKernelManager' --connect-timeout 4 '$checkUrl'"
            val lines = SuiKernelIoctl.runCommandWithOutput(cmd)

            for (line in lines) {
                if (line.trim().startsWith("location:", ignoreCase = true)) {
                    val loc = line.substringAfter(":").trim()
                    val t = extractVersionFromLocation(loc, platform)
                    if (t.isNotBlank() && !t.contains("/") && !t.startsWith("http")) {
                        tag = t
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    return tag?.let { resolveCleanVersion(it) }
}

fun fetchVersionFromModuleProp(repo: String, platform: HostPlatform): String? {
    val clean = repo.removePrefix("gitlab:").removePrefix("github:").trim()
        .removePrefix("https://gitlab.com/").removePrefix("https://github.com/").trimEnd('/')

    val candidateUrls = when (platform) {
        HostPlatform.GITLAB -> listOf(
            "https://gitlab.com/$clean/-/raw/main/module.prop",
            "https://gitlab.com/$clean/-/raw/main/module/module.prop",
            "https://gitlab.com/$clean/-/raw/master/module.prop",
            "https://gitlab.com/$clean/-/raw/master/module/module.prop"
        )
        HostPlatform.GITHUB -> listOf(
            "https://raw.githubusercontent.com/$clean/main/module.prop",
            "https://raw.githubusercontent.com/$clean/main/module/module.prop",
            "https://raw.githubusercontent.com/$clean/master/module.prop",
            "https://raw.githubusercontent.com/$clean/master/module/module.prop"
        )
    }

    for (url in candidateUrls) {
        var text: String? = null
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("User-Agent", "SuiKernelManager")
            if (conn.responseCode == 200) {
                text = conn.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            // Fall back to curl
        }

        if (text.isNullOrBlank()) {
            try {
                val cmd = "/system/bin/curl -s -L -k -A 'SuiKernelManager' --connect-timeout 4 '$url'"
                val out = SuiKernelIoctl.runCommandWithOutput(cmd).joinToString("\n").trim()
                if (out.contains("version=")) {
                    text = out
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (text != null && text.contains("version=")) {
            val ver = text.lines().firstOrNull { it.trim().startsWith("version=") }
                ?.substringAfter("version=")?.trim()
            if (!ver.isNullOrBlank()) {
                return resolveCleanVersion(ver)
            }
        }
    }

    return null
}

private fun fetchGitHubZipAssets(assetUrl: String): List<String> {
    var html = ""
    try {
        val conn = URL(assetUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 3500
        conn.readTimeout = 3500
        conn.setRequestProperty("User-Agent", "SuiKernelManager")
        if (conn.responseCode == 200) {
            html = conn.inputStream.bufferedReader().use { it.readText() }
        }
    } catch (e: Exception) {
        // Fall back to curl
    }

    if (html.isBlank()) {
        try {
            val cmd = "/system/bin/curl -s -L -k -A 'SuiKernelManager' --connect-timeout 4 '$assetUrl'"
            html = SuiKernelIoctl.runCommandWithOutput(cmd).joinToString("\n")
        } catch (e: Exception) {
            // Ignore
        }
    }

    return """href="(/[^"]+/releases/download/[^"]+\.zip)"""".toRegex()
        .findAll(html)
        .map { "https://github.com" + it.groupValues[1] }
        .toList()
}

fun fetchLatestRelease(
    repo: String,
    platform: HostPlatform = HostPlatform.GITHUB,
    installedUpdateJson: String? = null
): Pair<String, String> {
    val cleanRepo = repo.removePrefix("gitlab:").removePrefix("github:").trim()
        .removePrefix("https://gitlab.com/").removePrefix("https://github.com/").trimEnd('/')

    val effectivePlatform = if (repo.startsWith("gitlab:", ignoreCase = true) || repo.contains("gitlab.com", ignoreCase = true)) {
        HostPlatform.GITLAB
    } else {
        platform
    }

    // 1. FAST PATH: HTTP HEAD Redirect trick (takes ~100-200ms, 0 payload)
    val headTag = fetchLatestTagViaHead(cleanRepo, effectivePlatform)

    // For GitLab: query permalink/latest directly to get the assets / package links
    if (effectivePlatform == HostPlatform.GITLAB) {
        val glResult = fetchGitLabReleaseViaApi(cleanRepo)
        if (glResult != null && glResult.second.isNotBlank()) {
            return glResult
        }
        if (!headTag.isNullOrBlank()) {
            return Pair(headTag, "")
        }
    }

    // 2. Try update.json first if available
    val candidateUpdateUrls = mutableListOf<String>()
    if (!installedUpdateJson.isNullOrBlank()) {
        candidateUpdateUrls.add(installedUpdateJson)
    }
    when (effectivePlatform) {
        HostPlatform.GITHUB -> {
            candidateUpdateUrls.add("https://raw.githubusercontent.com/$cleanRepo/main/update.json")
            candidateUpdateUrls.add("https://raw.githubusercontent.com/$cleanRepo/master/update.json")
        }
        HostPlatform.GITLAB -> {
            candidateUpdateUrls.add("https://gitlab.com/$cleanRepo/-/raw/main/update.json")
            candidateUpdateUrls.add("https://gitlab.com/$cleanRepo/-/raw/master/update.json")
        }
    }

    for (uUrl in candidateUpdateUrls) {
        val updateInfo = fetchUpdateJson(uUrl)
        if (updateInfo != null && updateInfo.third.isNotBlank()) {
            return Pair(updateInfo.first, updateInfo.third)
        }
    }

    // 3. For GitHub: if headTag was found, query expanded_assets directly for that tag
    if (effectivePlatform == HostPlatform.GITHUB && !headTag.isNullOrBlank()) {
        val assetUrl = "https://github.com/$cleanRepo/releases/expanded_assets/$headTag"
        val zipMatches = fetchGitHubZipAssets(assetUrl)
        val bestZip = selectBestZip(zipMatches)
        if (bestZip != null) {
            return Pair(headTag, bestZip)
        }
        return Pair(headTag, "")
    }

    // 4. Try GitHub Releases web scraping
    if (effectivePlatform == HostPlatform.GITHUB) {
        val webResult = fetchGitHubReleaseViaWeb(cleanRepo)
        if (webResult != null && webResult.second.isNotBlank()) {
            return webResult
        }
    }

    // 5. Fallback: Raw module.prop
    val propVer = fetchVersionFromModuleProp(cleanRepo, effectivePlatform)
    if (!propVer.isNullOrBlank()) {
        return Pair(propVer, "")
    }

    // 6. Last resort: GitHub Releases API
    if (effectivePlatform == HostPlatform.GITHUB) {
        val apiResult = fetchGitHubReleaseViaApi(cleanRepo)
        if (apiResult != null && apiResult.second.isNotBlank()) {
            return apiResult
        }
    }

    return Pair(headTag ?: "", "")
}

fun fetchLatestRelease(
    repo: String,
    installedUpdateJson: String?
): Pair<String, String> = fetchLatestRelease(repo, HostPlatform.GITHUB, installedUpdateJson)

fun resolveCleanVersion(rawTag: String, releaseTitle: String = "", zipUrl: String = ""): String {
    val tag = rawTag.trim()
    val tagNum = tag.trimStart('v', 'V')
    if (tagNum.all { it.isDigit() } && tagNum.isNotBlank()) {
        return tag // e.g. "551"
    }

    // Standard semver tag like "v0.0.71", "v2.2", "v1.0.4", "v2.1.1"
    if ("""^v?\d+(\.\d+)+(\+)?$""".toRegex(RegexOption.IGNORE_CASE).matches(tag)) {
        return tag
    }

    val semverRegex = """v?(\d+(\.\d+)+(\+)?)""".toRegex(RegexOption.IGNORE_CASE)

    // Check release title (e.g. "BreWheel 0.0.11+")
    if (releaseTitle.isNotBlank()) {
        val m = semverRegex.find(releaseTitle)
        if (m != null) {
            return m.value
        }
    }

    // Check zip filename (e.g. "BreWheel-0.0.11+.zip")
    if (zipUrl.isNotBlank()) {
        try {
            val decoded = java.net.URLDecoder.decode(zipUrl, "UTF-8")
            val filename = decoded.substringAfterLast("/")
            val m = semverRegex.find(filename)
            if (m != null) {
                return m.value
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    // Check if tag contains semver inside it
    val m = semverRegex.find(tag)
    if (m != null) {
        return m.value
    }

    return tag
}

private fun fetchGitHubReleaseViaWeb(repo: String): Pair<String, String>? {
    return try {
        val repoUrl = "https://github.com/$repo"
        val latestUrl = "$repoUrl/releases/latest"
        val connection = URL(latestUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "SuiKernelManager")

        val redirectUrl = connection.getHeaderField("Location")
        connection.disconnect()

        var tag: String? = null
        var releaseTitle = ""

        if (redirectUrl != null && redirectUrl.contains("/tag/")) {
            tag = redirectUrl.substringAfterLast("/tag/").substringBefore("?").substringBefore("#")
        } else {
            val releasesConn = URL("$repoUrl/releases").openConnection() as HttpURLConnection
            releasesConn.connectTimeout = 5000
            releasesConn.readTimeout = 5000
            releasesConn.setRequestProperty("User-Agent", "SuiKernelManager")
            val releasesHtml = releasesConn.inputStream.bufferedReader().use { it.readText() }
            releasesConn.disconnect()

            val tagLinkMatch = """href="[^"]*/releases/tag/([^"/?\s]+)"[^>]*>\s*([^<]+)\s*</a>""".toRegex().find(releasesHtml)
            if (tagLinkMatch != null) {
                tag = tagLinkMatch.groupValues[1]
                releaseTitle = tagLinkMatch.groupValues[2].trim()
            } else {
                tag = """/releases/tag/([^"/?\s]+)""".toRegex().find(releasesHtml)?.groupValues?.get(1)
            }
        }

        if (tag.isNullOrBlank()) return null

        val assetUrl = "$repoUrl/releases/expanded_assets/$tag"
        val assetConn = URL(assetUrl).openConnection() as HttpURLConnection
        assetConn.connectTimeout = 5000
        assetConn.readTimeout = 5000
        assetConn.setRequestProperty("User-Agent", "SuiKernelManager")
        val html = assetConn.inputStream.bufferedReader().use { it.readText() }
        assetConn.disconnect()

        val zipMatches = """href="(/[^"]+/releases/download/[^"]+\.zip)"""".toRegex()
            .findAll(html)
            .map { "https://github.com" + it.groupValues[1] }
            .toList()

        val bestZip = selectBestZip(zipMatches)
        if (bestZip != null) {
            val cleanVersion = resolveCleanVersion(tag, releaseTitle, bestZip)
            Pair(cleanVersion, bestZip)
        } else null
    } catch (e: Exception) {
        null
    }
}

private fun fetchGitHubReleaseViaApi(repo: String): Pair<String, String>? {
    var jsonStr = ""
    try {
        val url = URL("https://api.github.com/repos/$repo/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "SuiKernelManager")
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        if (conn.responseCode == 200) {
            jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
        }
    } catch (e: Exception) {
        // Fall back to root curl
    }

    if (jsonStr.isBlank()) {
        try {
            val cmd = "/system/bin/curl -s -L -k -A 'SuiKernelManager' --connect-timeout 8 'https://api.github.com/repos/$repo/releases/latest'"
            val text = SuiKernelIoctl.runCommandWithOutput(cmd).joinToString("\n").trim()
            if (text.startsWith("{") && text.endsWith("}")) {
                jsonStr = text
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // If releases/latest was 404 (e.g. only pre-releases exist), fall back to /releases?per_page=1
    if (jsonStr.isBlank() || jsonStr.contains("\"status\":\"404\"") || jsonStr.contains("\"message\":\"Not Found\"")) {
        try {
            val url = URL("https://api.github.com/repos/$repo/releases?per_page=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "SuiKernelManager")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val arrayText = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                if (arrayText.startsWith("[") && arrayText.endsWith("]")) {
                    val arr = JSONArray(arrayText)
                    if (arr.length() > 0) {
                        jsonStr = arr.getJSONObject(0).toString()
                    }
                }
            }
        } catch (e: Exception) {
            // Fall back
        }
    }

    if (jsonStr.isBlank()) return null

    return try {
        val json = JSONObject(jsonStr)
        val tag = json.optString("tag_name", "")
        val releaseName = json.optString("name", "")
        val assets = json.optJSONArray("assets")
        val urls = mutableListOf<String>()
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetUrl = asset.optString("browser_download_url", "")
                if (assetUrl.endsWith(".zip", ignoreCase = true)) {
                    urls.add(assetUrl)
                }
            }
        }
        val bestZip = selectBestZip(urls)
        if (bestZip != null) {
            val cleanVersion = resolveCleanVersion(tag, releaseName, bestZip)
            Pair(cleanVersion, bestZip)
        } else null
    } catch (e: Exception) {
        null
    }
}

private fun fetchGitLabReleaseViaApi(repo: String): Pair<String, String>? {
    val cleanRepo = repo.removePrefix("gitlab:").removePrefix("github:").trim()
        .removePrefix("https://gitlab.com/").removePrefix("http://gitlab.com/").trimEnd('/')
    val encodedPath = URLEncoder.encode(cleanRepo, "UTF-8")

    var jsonStr = ""
    try {
        val url = URL("https://gitlab.com/api/v4/projects/$encodedPath/releases/permalink/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "SuiKernelManager")
        conn.connectTimeout = 4000
        conn.readTimeout = 4000

        if (conn.responseCode == 200) {
            jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
        }
    } catch (e: Exception) {
        // Fall back to root curl
    }

    if (jsonStr.isBlank()) {
        try {
            val cmd = "/system/bin/curl -s -L -k -A 'SuiKernelManager' --connect-timeout 6 'https://gitlab.com/api/v4/projects/$encodedPath/releases/permalink/latest'"
            val text = SuiKernelIoctl.runCommandWithOutput(cmd).joinToString("\n").trim()
            if (text.startsWith("{") && text.endsWith("}")) {
                jsonStr = text
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (jsonStr.isBlank()) {
        try {
            val url = URL("https://gitlab.com/api/v4/projects/$encodedPath/releases")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "SuiKernelManager")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            if (conn.responseCode == 200) {
                jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    if (jsonStr.isBlank()) return null

    return try {
        val release = if (jsonStr.trim().startsWith("[")) {
            val array = JSONArray(jsonStr)
            if (array.length() == 0) return null
            array.getJSONObject(0)
        } else {
            JSONObject(jsonStr)
        }

        val tag = release.optString("tag_name", "")
        val releaseName = release.optString("name", "")
        val assets = release.optJSONObject("assets")
        val links = assets?.optJSONArray("links")
        val urls = mutableListOf<String>()

        if (links != null) {
            for (i in 0 until links.length()) {
                val linkObj = links.getJSONObject(i)
                val directUrl = linkObj.optString("direct_asset_url", "").ifBlank {
                    linkObj.optString("url", "")
                }
                if (directUrl.contains(".zip", ignoreCase = true)) {
                    urls.add(directUrl)
                }
            }
        }

        val bestZip = selectBestZip(urls)
        if (bestZip != null) {
            val cleanVersion = resolveCleanVersion(tag, releaseName, bestZip)
            Pair(cleanVersion, bestZip)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

fun isNewerVersion(latestTag: String, installedVersion: String, installedVersionCode: Int): Boolean {
    if (latestTag.isBlank() || installedVersion.isBlank()) return false

    if (installedVersion.equals(latestTag, ignoreCase = true)) return false

    fun normalize(v: String): String {
        var s = v.trim()
            .trimStart('v', 'V')
            .trimEnd('+')
            .split(" ")[0]
            .trim()
        if (s.contains("-") && !s.substringBefore("-").any { it.isDigit() }) {
            s = s.substringAfter("-")
        } else if (s.contains("-")) {
            s = s.substringBefore("-")
        }
        return s.trim()
    }

    val normLatest = normalize(latestTag)
    val normInstalled = normalize(installedVersion)

    if (normLatest.isNotBlank() && normLatest.equals(normInstalled, ignoreCase = true)) {
        return false
    }

    // Check containment if one wraps the other (e.g. "v1.0.0 (551-release)" contains "551" or "0.0.11" inside "0.0.11+")
    if (normInstalled.isNotBlank() && normLatest.isNotBlank()) {
        if (normLatest.contains(normInstalled, ignoreCase = true) || normInstalled.contains(normLatest, ignoreCase = true)) {
            val p1 = normLatest.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
            val p2 = normInstalled.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
            if (p1.isNotEmpty() && p1 == p2) {
                return false
            }
        }
    }

    // Compare pure integer versionCode (e.g. "551")
    val pureLatestInt = latestTag.trim().trimStart('v', 'V').toIntOrNull()
    if (pureLatestInt != null) {
        if (installedVersionCode > 0) {
            return pureLatestInt > installedVersionCode
        }
        val pureInstalledInt = installedVersion.trim().trimStart('v', 'V').toIntOrNull()
        if (pureInstalledInt != null) {
            return pureLatestInt > pureInstalledInt
        }
    }

    // Compare semver components
    val v1Parts = normLatest.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
    val v2Parts = normInstalled.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

    if (v1Parts.isNotEmpty() && v2Parts.isNotEmpty()) {
        for (i in 0 until maxOf(v1Parts.size, v2Parts.size)) {
            val part1 = v1Parts.getOrElse(i) { 0 }
            val part2 = v2Parts.getOrElse(i) { 0 }
            if (part1 > part2) return true
            if (part1 < part2) return false
        }
        return false
    }

    return false
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        else -> String.format(java.util.Locale.US, "%.0f KB", kb)
    }
}

fun downloadModuleZip(
    context: Context,
    downloadUrl: String,
    onProgress: (progress: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
): File? {
    val file = File(context.cacheDir, "module_install_${System.currentTimeMillis()}.zip")
    val destPath = file.absolutePath

    // 1. Direct streaming download via HttpURLConnection
    try {
        var currentUrl = downloadUrl
        var connection: HttpURLConnection? = null
        var redirects = 0
        while (redirects < 8) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "SuiKernelManager")
                setRequestProperty("Accept-Encoding", "identity")
            }
            conn.connect()
            val code = conn.responseCode
            if (code in 301..308) {
                val loc = conn.getHeaderField("Location")
                if (!loc.isNullOrBlank()) {
                    currentUrl = if (loc.startsWith("http", ignoreCase = true)) loc else URL(url, loc).toString()
                    conn.disconnect()
                    redirects++
                    continue
                }
            }
            connection = conn
            break
        }

        if (connection != null && connection.responseCode == 200) {
            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            val buffer = ByteArray(32 * 1024)

            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    var read: Int
                    var lastUpdate = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 80 || (totalBytes > 0 && downloadedBytes == totalBytes)) {
                            lastUpdate = now
                            val p = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                ((downloadedBytes / (1024 * 1024)).toInt() * 5).coerceIn(0, 95)
                            }
                            onProgress(p, downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            val myUid = android.os.Process.myUid()
            SuiKernelIoctl.runCommand("chown $myUid:$myUid '$destPath' && chmod 644 '$destPath'")

            if (file.exists() && file.length() > 512) {
                onProgress(100, file.length(), file.length())
                return file
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // 2. Fallback: su/sh curl download
    return try {
        val sizeCmd = "/system/bin/curl -sIL -k -A 'SuiKernelManager' '$downloadUrl' | grep -i 'content-length' | tail -n 1 | tr -d '\\r\\n' | awk '{print \$2}'"
        val totalBytesStr = SuiKernelIoctl.runCommandWithOutput(sizeCmd).firstOrNull()?.trim() ?: ""
        val totalBytes = totalBytesStr.toLongOrNull() ?: 0L

        val downloadCmd = "/system/bin/curl -L -k -A 'SuiKernelManager' --connect-timeout 15 '$downloadUrl' -o '$destPath'"
        try { SuiKernelIoctl.nativeElevateThread() } catch (e: Throwable) {}
        val downloadProc = Runtime.getRuntime().exec(arrayOf("sh", "-c", downloadCmd))
        try { downloadProc.outputStream.close() } catch (e: Exception) {}

        var loops = 0
        while (downloadProc.isAlive && loops < 300) {
            Thread.sleep(200)
            loops++
            if (file.exists()) {
                val currentSize = file.length()
                if (totalBytes > 0) {
                    val p = ((currentSize * 100) / totalBytes).toInt().coerceIn(1, 99)
                    onProgress(p, currentSize, totalBytes)
                } else {
                    val p = ((currentSize / 1024) / 30).toInt().coerceIn(5, 95)
                    onProgress(p, currentSize, totalBytes)
                }
            }
        }
        if (downloadProc.isAlive) {
            downloadProc.destroyForcibly()
        } else {
            downloadProc.waitFor()
        }

        val myUid = android.os.Process.myUid()
        SuiKernelIoctl.runCommand("chown $myUid:$myUid '$destPath' && chmod 644 '$destPath'")

        if (file.exists() && file.length() > 512) {
            onProgress(100, file.length(), file.length())
            file
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun downloadModuleZip(context: Context, downloadUrl: String, onProgress: (Int) -> Unit): File? =
    downloadModuleZip(context, downloadUrl) { progress, _, _ -> onProgress(progress) }

fun flashModuleZip(zipFile: File, onLine: (String) -> Unit) {
    try {
        val ksudBin = SuiKernelIoctl.runCommandWithOutput("which ksud").firstOrNull()?.trim() ?: "ksud"
        val cmd = "$ksudBin module install ${zipFile.absolutePath} 2>&1"
        val output = SuiKernelIoctl.runTerminalCommand(cmd)
        output.lines().forEach { onLine(it) }
    } catch (e: Exception) {
        e.printStackTrace()
        onLine("Error executing ksud: ${e.message}")
    }
}

fun rebootDevice() {
    try {
        SuiKernelIoctl.runCommand("/system/bin/svc power reboot || /system/bin/reboot")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ----------------- MODULE REBOOT TRACKING HELPERS -----------------

private const val PREFS_REBOOT_PENDING = "module_reboot_pending"
private const val KEY_BOOT_ID = "last_boot_id"
private const val KEY_PENDING_REPOS = "pending_repos"

private fun getSystemBootId(): String {
    return try {
        File("/proc/sys/kernel/random/boot_id").readText().trim()
    } catch (e: Exception) {
        android.os.SystemClock.elapsedRealtime().toString()
    }
}

fun getPendingRebootRepos(context: Context): Set<String> {
    return try {
        val prefs = context.getSharedPreferences(PREFS_REBOOT_PENDING, Context.MODE_PRIVATE)
        val savedBootId = prefs.getString(KEY_BOOT_ID, "") ?: ""
        val currentBootId = getSystemBootId()
        if (savedBootId != currentBootId) {
            // Boot ID changed -> system has rebooted since, clear pending set
            prefs.edit().putString(KEY_BOOT_ID, currentBootId).remove(KEY_PENDING_REPOS).apply()
            emptySet()
        } else {
            prefs.getStringSet(KEY_PENDING_REPOS, emptySet()) ?: emptySet()
        }
    } catch (e: Exception) {
        emptySet()
    }
}

fun markModulePendingReboot(context: Context, repo: String) {
    try {
        val prefs = context.getSharedPreferences(PREFS_REBOOT_PENDING, Context.MODE_PRIVATE)
        val currentBootId = getSystemBootId()
        val currentSet = try {
            prefs.getStringSet(KEY_PENDING_REPOS, emptySet())?.toMutableSet() ?: mutableSetOf()
        } catch (e: Exception) {
            mutableSetOf()
        }
        currentSet.add(repo)
        prefs.edit()
            .putString(KEY_BOOT_ID, currentBootId)
            .putStringSet(KEY_PENDING_REPOS, currentSet)
            .apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

