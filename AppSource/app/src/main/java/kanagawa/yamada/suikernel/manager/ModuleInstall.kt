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

        val tag = if (redirectUrl != null && redirectUrl.contains("/tag/")) {
            redirectUrl.substringAfterLast("/tag/").substringBefore("?").substringBefore("#")
        } else {
            val releasesConn = URL("$repoUrl/releases").openConnection() as HttpURLConnection
            releasesConn.connectTimeout = 5000
            releasesConn.readTimeout = 5000
            releasesConn.setRequestProperty("User-Agent", "SuiKernelManager")
            val releasesHtml = releasesConn.inputStream.bufferedReader().use { it.readText() }
            releasesConn.disconnect()
            """/releases/tag/([^"/?\s]+)""".toRegex().find(releasesHtml)?.groupValues?.get(1)
        } ?: return null

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
            Pair(tag, bestZip)
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

    if (jsonStr.isBlank()) return null

    return try {
        val json = JSONObject(jsonStr)
        val tag = json.optString("tag_name", "")
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
        if (bestZip != null) Pair(tag, bestZip) else null
    } catch (e: Exception) {
        null
    }
}

fun isNewerVersion(latestTag: String, installedVersion: String, installedVersionCode: Int): Boolean {
    if (latestTag.isBlank() || installedVersion.isBlank()) return false

    // If installedVersion contains or equals the latest tag (e.g. BreZygisk "551" inside "v1.0.0 (551-10-8806ea1-release)" or versionCode == 551)
    if (installedVersion.equals(latestTag, ignoreCase = true)) return false
    if (installedVersion.contains(latestTag, ignoreCase = true)) return false

    // Compare pure integer versionCode
    val tagInt = latestTag.filter { it.isDigit() }.toIntOrNull()
    if (tagInt != null && installedVersionCode > 0) {
        return tagInt > installedVersionCode
    }

    val installedInt = installedVersion.filter { it.isDigit() }.toIntOrNull()
    if (tagInt != null && installedInt != null) {
        return tagInt > installedInt
    }

    // Compare semver components
    val cleanLatest = latestTag.trimStart('v', 'V').split("-")[0].split(" ")[0]
    val cleanInstalled = installedVersion.trimStart('v', 'V').split("-")[0].split(" ")[0]

    if (cleanLatest.equals(cleanInstalled, ignoreCase = true)) return false

    val v1Parts = cleanLatest.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
    val v2Parts = cleanInstalled.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

    if (v1Parts.isNotEmpty() && v2Parts.isNotEmpty()) {
        for (i in 0 until maxOf(v1Parts.size, v2Parts.size)) {
            val part1 = v1Parts.getOrElse(i) { 0 }
            val part2 = v2Parts.getOrElse(i) { 0 }
            if (part1 > part2) return true
            if (part1 < part2) return false
        }
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
