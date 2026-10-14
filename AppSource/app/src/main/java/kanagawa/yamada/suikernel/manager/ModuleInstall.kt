package kanagawa.yamada.suikernel.manager

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kanagawa.yamada.suikernel.manager.ui.theme.GoogleSansFlex
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class InstalledModuleInfo(
    val id: String,
    val version: String,
    val versionCode: Int
)

data class DefinitionItem(
    val id: String,
    val title: String,
    val author: String,
    val defaultVersion: String,
    val repo: String,
    val assetFilter: ((String) -> Boolean)? = null
)

@Composable
fun InstallConsoleScreen(
    title: String,
    output: String,
    isInstalling: Boolean,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101214))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            if (!isInstalling) {
                Button(
                    onClick = onClose,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1))
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF191B1C), RoundedCornerShape(8.dp))
                .padding(12.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = output,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFFE0E0E0)
            )
        }
    }
}

// ----------------- ROOT & GITHUB HELPER FUNCTIONS -----------------

fun fetchInstalledModules(): Map<String, InstalledModuleInfo> {
    val map = mutableMapOf<String, InstalledModuleInfo>()
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
                val version = obj.optString("version", "")
                val versionCode = obj.optInt("versionCode", 0)
                if (id.isNotBlank()) {
                    map[id] = InstalledModuleInfo(id, version, versionCode)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return map
}

fun fetchLatestRelease(repo: String, assetFilter: ((String) -> Boolean)? = null): Pair<String, String> {
    var jsonStr = ""
    
    // First try standard HTTP
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
        // App sandbox network may be blocked, fall through to root
    }

    // Root fallback: always reliable
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

    if (jsonStr.isBlank()) return Pair("", "")

    return try {
        val json = JSONObject(jsonStr)
        val tag = json.optString("tag_name", "")
        val assets = json.optJSONArray("assets")

        var downloadUrl = ""
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                val assetUrl = asset.optString("browser_download_url", "")
                if (assetName.endsWith(".zip", ignoreCase = true)) {
                    if (assetFilter == null || assetFilter(assetName)) {
                        downloadUrl = assetUrl
                        break
                    }
                }
            }
        }
        Pair(tag, downloadUrl)
    } catch (e: Exception) {
        e.printStackTrace()
        Pair("", "")
    }
}

fun isNewerVersion(latestTag: String, installedVersion: String, installedVersionCode: Int): Boolean {
    if (latestTag.isBlank() || installedVersion.isBlank()) return false
    
    // Compare integer versionCode if tag is pure number
    val tagInt = latestTag.filter { it.isDigit() }.toIntOrNull()
    val installedInt = installedVersion.filter { it.isDigit() }.toIntOrNull()
    if (tagInt != null && installedInt != null && tagInt != installedInt) {
        return tagInt > installedInt
    }

    // Compare semver components
    val cleanLatest = latestTag.trimStart('v', 'V').split("-")[0]
    val cleanInstalled = installedVersion.trimStart('v', 'V').split("-")[0].split(" ")[0]

    val v1Parts = cleanLatest.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
    val v2Parts = cleanInstalled.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

    for (i in 0 until maxOf(v1Parts.size, v2Parts.size)) {
        val part1 = v1Parts.getOrElse(i) { 0 }
        val part2 = v2Parts.getOrElse(i) { 0 }
        if (part1 > part2) return true
        if (part1 < part2) return false
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
