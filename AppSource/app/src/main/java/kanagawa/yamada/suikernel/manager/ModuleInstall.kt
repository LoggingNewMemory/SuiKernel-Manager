package kanagawa.yamada.suikernel.manager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class InstalledModuleInfo(
    val id: String,
    val name: String = "",
    val author: String = "",
    val version: String,
    val versionCode: Int,
    val hasPendingUpdate: Boolean = false,
    val updateJson: String = ""
)

data class DefinitionItem(
    val title: String,
    val author: String,
    val repo: String
)

// ----------------- MODULE VERSION CACHE HELPERS -----------------

private const val PREFS_MODULE_CACHE = "module_version_cache"

fun getCachedModuleVersion(context: Context, repo: String): String {
    return try {
        context.getSharedPreferences(PREFS_MODULE_CACHE, Context.MODE_PRIVATE)
            .getString("ver_$repo", "") ?: ""
    } catch (e: Exception) {
        ""
    }
}

fun getCachedModuleDownloadUrl(context: Context, repo: String): String {
    return try {
        context.getSharedPreferences(PREFS_MODULE_CACHE, Context.MODE_PRIVATE)
            .getString("url_$repo", "") ?: ""
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

// ----------------- ROOT & GITHUB HELPER FUNCTIONS -----------------

fun fetchInstalledModules(): List<InstalledModuleInfo> {
    val list = mutableListOf<InstalledModuleInfo>()
    try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ksud module list 2>/dev/null || /data/adb/ksud module list"))
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line).append("\n")
        }
        process.waitFor()

        val jsonStr = sb.toString().trim()
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
    val cleanTitle = title.trim().lowercase()
    val cleanAuthor = author.trim().lowercase()
    val repoProject = repo.substringAfter("/").trim().lowercase()

    return installedList.firstOrNull { inst ->
        val instName = inst.name.trim().lowercase()
        val instId = inst.id.trim().lowercase()
        val instAuthor = inst.author.trim().lowercase()

        // 1. Direct updateJson check
        if (inst.updateJson.isNotBlank() && inst.updateJson.contains(repo, ignoreCase = true)) {
            return@firstOrNull true
        }

        // 2. Name / title match
        val nameMatch = instName == cleanTitle ||
                (instName.isNotBlank() && cleanTitle.contains(instName)) ||
                (cleanTitle.isNotBlank() && instName.contains(cleanTitle))

        // 3. ID match
        val idMatch = instId == repoProject ||
                instId == cleanTitle.replace(" ", "") ||
                instId.replace("_", "") == cleanTitle.replace(" ", "") ||
                repoProject.contains(instId)

        // 4. Author match
        val authorMatch = cleanAuthor.isNotBlank() && (
                instAuthor.contains(cleanAuthor) ||
                cleanAuthor.contains(instAuthor) ||
                inst.updateJson.contains(cleanAuthor, ignoreCase = true)
        )

        (nameMatch && (authorMatch || instAuthor.isBlank())) || (idMatch && authorMatch)
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
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
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
    // 2. Prefer filename not containing 'debug'
    val nonDebug = nonArchive.firstOrNull { !it.substringAfterLast("/").contains("debug", ignoreCase = true) }
    if (nonDebug != null) return nonDebug
    return nonArchive.first()
}

fun fetchLatestRelease(
    repo: String,
    installedUpdateJson: String? = null
): Pair<String, String> {
    // 1. Try update.json first (installed updateJson -> main/update.json -> master/update.json)
    val candidateUpdateUrls = mutableListOf<String>()
    if (!installedUpdateJson.isNullOrBlank()) {
        candidateUpdateUrls.add(installedUpdateJson)
    }
    candidateUpdateUrls.add("https://raw.githubusercontent.com/$repo/main/update.json")
    candidateUpdateUrls.add("https://raw.githubusercontent.com/$repo/master/update.json")

    for (uUrl in candidateUpdateUrls) {
        val updateInfo = fetchUpdateJson(uUrl)
        if (updateInfo != null && updateInfo.third.isNotBlank()) {
            return Pair(updateInfo.first, updateInfo.third)
        }
    }

    // 2. Try GitHub Releases web scraping (redirect/expanded_assets like KernelSU-Next, immune to API rate limits)
    val webResult = fetchGitHubReleaseViaWeb(repo)
    if (webResult != null && webResult.second.isNotBlank()) {
        return webResult
    }

    // 3. Fall back to GitHub Releases API
    val apiResult = fetchGitHubReleaseViaApi(repo)
    if (apiResult != null && apiResult.second.isNotBlank()) {
        return apiResult
    }

    return Pair("", "")
}

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
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
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

fun isNewerVersion(latestTag: String, installedVersion: String, installedVersionCode: Int): Boolean {
    if (latestTag.isBlank() || installedVersion.isBlank()) return false

    if (installedVersion.equals(latestTag, ignoreCase = true)) return false

    fun normalize(v: String): String {
        return v.trim()
            .trimStart('v', 'V')
            .trimEnd('+')
            .split("-")[0]
            .split(" ")[0]
            .trim()
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

fun downloadModuleZip(context: Context, downloadUrl: String, onProgress: (Int) -> Unit): File? {
    val file = File(context.cacheDir, "module_install_${System.currentTimeMillis()}.zip")
    val destPath = file.absolutePath

    return try {
        val sizeCmd = "/system/bin/curl -sIL -k -A 'SuiKernelManager' '$downloadUrl' | grep -i 'content-length' | tail -n 1 | tr -d '\\r\\n' | awk '{print \$2}'"
        val sizeProc = Runtime.getRuntime().exec(arrayOf("su", "-c", sizeCmd))
        val totalBytesStr = sizeProc.inputStream.bufferedReader().use { it.readText() }.trim()
        val totalBytes = totalBytesStr.toLongOrNull() ?: 0L
        sizeProc.waitFor()

        val downloadCmd = "/system/bin/curl -L -k -A 'SuiKernelManager' --connect-timeout 15 '$downloadUrl' -o '$destPath'"
        val downloadProc = Runtime.getRuntime().exec(arrayOf("su", "-c", downloadCmd))

        while (downloadProc.isAlive) {
            Thread.sleep(250)
            if (file.exists()) {
                val currentSize = file.length()
                if (totalBytes > 0) {
                    val p = ((currentSize * 100) / totalBytes).toInt().coerceIn(1, 99)
                    onProgress(p)
                } else {
                    val p = ((currentSize / 1024) / 30).toInt().coerceIn(5, 95)
                    onProgress(p)
                }
            }
        }
        downloadProc.waitFor()

        Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 666 '$destPath'")).waitFor()

        if (file.exists() && file.length() > 512) {
            onProgress(100)
            file
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun flashModuleZip(zipFile: File, onLine: (String) -> Unit) {
    try {
        val cmd = "ksud module install ${zipFile.absolutePath} 2>&1 || /data/adb/ksud module install ${zipFile.absolutePath} 2>&1"
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            onLine(line ?: "")
        }
        process.waitFor()
    } catch (e: Exception) {
        e.printStackTrace()
        onLine("Error executing ksud: ${e.message}")
    }
}

fun rebootDevice() {
    try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", "/system/bin/svc power reboot || /system/bin/reboot"))
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
        val currentSet = prefs.getStringSet(KEY_PENDING_REPOS, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(repo)
        prefs.edit()
            .putString(KEY_BOOT_ID, currentBootId)
            .putStringSet(KEY_PENDING_REPOS, currentSet)
            .apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

