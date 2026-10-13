package kanagawa.yamada.suikernel.manager

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kanagawa.yamada.suikernel.manager.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

enum class ModuleStatus {
    DOWNLOAD,
    UPDATE,
    INSTALLED
}

data class RecomendedModuleItem(
    val id: String,
    val title: String,
    val author: String,
    val version: String,
    val latestVersion: String,
    val repo: String,
    val downloadUrl: String,
    val status: ModuleStatus,
    val isBusy: Boolean = false,
    val progressText: String = "",
    val downloadProgress: Float = 0f
)

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
fun RecomendedModulesScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var consoleOutput by remember { mutableStateOf<String?>(null) }
    var isInstalling by remember { mutableStateOf(false) }

    val moduleDefs = remember {
        listOf(
            DefinitionItem("brene", "Brene SUSFS", "rrr333nnn333", "v0.0.71", "rrr333nnn333/BRENE"),
            DefinitionItem("rezygisk", "BreZygisk", "rrr333nnn333", "551", "rrr333nnn333/BreZygisk"),
            DefinitionItem("treat_wheel", "BreWheel", "rrr333nnn333", "0.0.11+", "rrr333nnn333/BreWheel"),
            DefinitionItem("zygisk_vector", "Vector", "JingMatrix", "v2.2", "JingMatrix/Vector") { name ->
                name.contains("release", ignoreCase = true) || !name.contains("debug", ignoreCase = true)
            },
            DefinitionItem("tricky_store", "Always Strong", "evoker0", "v1.0.4", "evoker0/AlwaysStrong") { name ->
                name.contains("inject", ignoreCase = true)
            },
            DefinitionItem("nomount", "NoMount", "maxsteeel", "v2.1.1", "maxsteeel/nomount")
        )
    }

    var modulesState by remember {
        mutableStateOf(
            moduleDefs.map { def ->
                RecomendedModuleItem(
                    id = def.id,
                    title = def.title,
                    author = def.author,
                    version = def.defaultVersion,
                    latestVersion = def.defaultVersion,
                    repo = def.repo,
                    downloadUrl = "",
                    status = ModuleStatus.DOWNLOAD
                )
            }
        )
    }

    fun refreshModules() {
        coroutineScope.launch(Dispatchers.IO) {
            val installedMap = fetchInstalledModules()
            val updated = modulesState.map { item ->
                val def = moduleDefs.firstOrNull { it.id == item.id }
                val (latestTag, downloadUrl) = if (def != null) fetchLatestRelease(def.repo, def.assetFilter) else Pair("", "")
                
                val installed = installedMap[item.id]
                val latest = if (latestTag.isNotBlank()) latestTag else item.latestVersion
                
                val displayVersion = installed?.version ?: latest

                val status = when {
                    installed == null -> ModuleStatus.DOWNLOAD
                    isNewerVersion(latest, installed.version, installed.versionCode) -> ModuleStatus.UPDATE
                    else -> ModuleStatus.INSTALLED
                }

                item.copy(
                    version = displayVersion,
                    latestVersion = latest,
                    downloadUrl = downloadUrl,
                    status = status,
                    isBusy = false,
                    progressText = ""
                )
            }
            withContext(Dispatchers.Main) {
                modulesState = updated
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshModules()
    }

    BackHandler(onBack = {
        if (consoleOutput != null && !isInstalling) {
            consoleOutput = null
        } else if (!isInstalling) {
            onNavigateBack()
        }
    })

    if (consoleOutput != null) {
        // Installation terminal output modal
        InstallConsoleScreen(
            title = "Module Flasher",
            output = consoleOutput ?: "",
            isInstalling = isInstalling,
            onClose = {
                consoleOutput = null
                refreshModules()
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Recomended Modules",
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                color = Color.White,
                textDecoration = TextDecoration.Underline
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFFE05A67))) { append("Enhance ") }
                    withStyle(SpanStyle(color = Color.White)) { append("Your ") }
                    withStyle(SpanStyle(color = Color(0xFF26C6DA))) { append("SuiKernel ") }
                    withStyle(SpanStyle(color = Color.White)) { append("Experience") }
                },
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Light,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Module list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(modulesState, key = { it.id }) { module ->
                ModuleRowItem(
                    module = module,
                    onActionClick = {
                        if (module.status == ModuleStatus.INSTALLED) {
                            Toast.makeText(context, "${module.title} is already installed", Toast.LENGTH_SHORT).show()
                            return@ModuleRowItem
                        }

                        // Start Download and Install
                        coroutineScope.launch {
                            modulesState = modulesState.map {
                                if (it.id == module.id) it.copy(isBusy = true, progressText = "Preparing...", downloadProgress = 0f) else it
                            }

                            // If downloadUrl is blank, attempt to resolve it now
                            var targetUrl = module.downloadUrl
                            if (targetUrl.isBlank()) {
                                val def = moduleDefs.firstOrNull { it.id == module.id }
                                if (def != null) {
                                    val (latestTag, url) = withContext(Dispatchers.IO) {
                                        fetchLatestRelease(def.repo, def.assetFilter)
                                    }
                                    if (url.isNotBlank()) {
                                        targetUrl = url
                                        modulesState = modulesState.map {
                                            if (it.id == module.id) it.copy(downloadUrl = url, latestVersion = latestTag.ifBlank { it.latestVersion }) else it
                                        }
                                    }
                                }
                            }

                            if (targetUrl.isBlank()) {
                                Toast.makeText(context, "Could not fetch release URL. Check connection.", Toast.LENGTH_SHORT).show()
                                modulesState = modulesState.map {
                                    if (it.id == module.id) it.copy(isBusy = false, progressText = "") else it
                                }
                                return@launch
                            }

                            consoleOutput = "--- Preparing installation for ${module.title} ---\n"
                            isInstalling = true

                            val zipFile = withContext(Dispatchers.IO) {
                                downloadModuleZip(context, targetUrl) { progress ->
                                    modulesState = modulesState.map {
                                        if (it.id == module.id) it.copy(
                                            progressText = "$progress%",
                                            downloadProgress = (progress / 100f).coerceIn(0f, 1f)
                                        ) else it
                                    }
                                }
                            }

                            if (zipFile == null || !zipFile.exists()) {
                                isInstalling = false
                                consoleOutput = consoleOutput + "\n[!] Download failed or invalid file."
                                modulesState = modulesState.map {
                                    if (it.id == module.id) it.copy(isBusy = false, progressText = "", downloadProgress = 0f) else it
                                }
                                return@launch
                            }

                            modulesState = modulesState.map {
                                if (it.id == module.id) it.copy(progressText = "Flashing...", downloadProgress = 1f) else it
                            }

                            consoleOutput = consoleOutput + "\n--- Download Complete (${zipFile.length() / 1024} KB) ---\n"
                            consoleOutput = consoleOutput + "- Running module installer via ksud...\n\n"

                            withContext(Dispatchers.IO) {
                                flashModuleZip(zipFile) { line ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        consoleOutput = (consoleOutput ?: "") + line + "\n"
                                    }
                                }
                                zipFile.delete()
                            }

                            isInstalling = false
                            consoleOutput = (consoleOutput ?: "") + "\n\n--- Finished! Please Reboot if required ---"
                            refreshModules()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ModuleRowItem(
    module: RecomendedModuleItem,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Title row
            if (module.isBusy && module.downloadProgress > 0f) {
                val progress = module.downloadProgress.coerceIn(0f, 1f)
                val greenColor = Color(0xFFA1D477)
                val whiteColor = Color.White
                
                // Horizontal brush that transitions from green to white at current progress point
                val titleBrush = Brush.horizontalGradient(
                    0.0f to greenColor,
                    progress to greenColor,
                    progress to whiteColor,
                    1.0f to whiteColor
                )

                Text(
                    text = if (module.status == ModuleStatus.UPDATE) "${module.title} - UPDATE" else module.title,
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    style = TextStyle(brush = titleBrush)
                )
            } else {
                when (module.status) {
                    ModuleStatus.UPDATE -> {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.White)) { append(module.title) }
                                append(" - ")
                                withStyle(SpanStyle(color = Color(0xFFE05A67))) { append("UPDATE") }
                            },
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    ModuleStatus.INSTALLED -> {
                        Text(
                            text = module.title,
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFFA1D477)
                        )
                    }
                    ModuleStatus.DOWNLOAD -> {
                        Text(
                            text = module.title,
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle
            val subtitleVersion = if (module.status == ModuleStatus.UPDATE) {
                "${module.version} → ${module.latestVersion}"
            } else {
                module.version
            }

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("By: ${module.author} • ") }
                    withStyle(SpanStyle(color = Color(0xFF26C6DA))) { append(subtitleVersion) }
                    if (module.isBusy && module.progressText.isNotBlank()) {
                        withStyle(SpanStyle(color = Color(0xFFE05A67))) { append(" [${module.progressText}]") }
                    }
                },
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp
            )
        }

        // Action icon button
        IconButton(
            onClick = onActionClick,
            enabled = !module.isBusy,
            modifier = Modifier.size(36.dp)
        ) {
            if (module.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF26C6DA)
                )
            } else {
                when (module.status) {
                    ModuleStatus.DOWNLOAD -> {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download),
                            contentDescription = "Download ${module.title}",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    ModuleStatus.UPDATE -> {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download_upd),
                            contentDescription = "Update ${module.title}",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    ModuleStatus.INSTALLED -> {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ok),
                            contentDescription = "${module.title} Installed",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

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

    // Execute root curl with progress output format: # {percent}
    return try {
        // First get Content-Length if possible to track exact progress
        val sizeCmd = "/system/bin/curl -sIL -k -A 'SuiKernelManager' '$downloadUrl' | grep -i 'content-length' | tail -n 1 | tr -d '\\r\\n' | awk '{print \$2}'"
        val sizeProc = Runtime.getRuntime().exec(arrayOf("su", "-c", sizeCmd))
        val totalBytesStr = sizeProc.inputStream.bufferedReader().use { it.readText() }.trim()
        val totalBytes = totalBytesStr.toLongOrNull() ?: 0L
        sizeProc.waitFor()

        val downloadCmd = "/system/bin/curl -L -k -A 'SuiKernelManager' --connect-timeout 15 '$downloadUrl' -o '$destPath'"
        val downloadProc = Runtime.getRuntime().exec(arrayOf("su", "-c", downloadCmd))

        // Monitor file size in background loop to drive title progress gradient
        while (downloadProc.isAlive) {
            Thread.sleep(250)
            if (file.exists()) {
                val currentSize = file.length()
                if (totalBytes > 0) {
                    val p = ((currentSize * 100) / totalBytes).toInt().coerceIn(1, 99)
                    onProgress(p)
                } else {
                    // Estimate progress for small zips (~1-5MB)
                    val p = ((currentSize / 1024) / 30).toInt().coerceIn(5, 95)
                    onProgress(p)
                }
            }
        }
        downloadProc.waitFor()

        // Fix file permissions so app can read/delete it
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
