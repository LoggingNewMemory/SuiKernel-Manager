package kanagawa.yamada.suikernel.manager

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
    INSTALLED,
    REBOOT
}

data class RecomendedModuleItem(
    val id: String = "",
    val title: String,
    val author: String,
    val repo: String,
    val version: String = "",
    val latestVersion: String = "",
    val downloadUrl: String = "",
    val status: ModuleStatus = ModuleStatus.DOWNLOAD,
    val isBusy: Boolean = false,
    val progressText: String = "",
    val downloadProgress: Float = 0f
)

@Composable
fun RecomendedModulesScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var consoleOutput by remember { mutableStateOf<String?>(null) }
    var isInstalling by remember { mutableStateOf(false) }

    val moduleDefs = remember {
        listOf(
            DefinitionItem(
                title = "Brene SUSFS",
                author = "rrr333nnn333",
                repo = "rrr333nnn333/BRENE"
            ),
            DefinitionItem(
                title = "BreZygisk",
                author = "rrr333nnn333",
                repo = "rrr333nnn333/BreZygisk"
            ),
            DefinitionItem(
                title = "BreWheel",
                author = "rrr333nnn333",
                repo = "rrr333nnn333/BreWheel"
            ),
            DefinitionItem(
                title = "Vector",
                author = "JingMatrix",
                repo = "JingMatrix/Vector"
            ),
            DefinitionItem(
                title = "Always Strong",
                author = "evoker0",
                repo = "evoker0/AlwaysStrong"
            ),
            DefinitionItem(
                title = "NoMount",
                author = "maxsteeel",
                repo = "maxsteeel/nomount"
            )
        )
    }

    var modulesState by remember {
        mutableStateOf(
            moduleDefs.map { def ->
                val cachedVer = getCachedModuleVersion(context, def.repo)
                val cachedUrl = getCachedModuleDownloadUrl(context, def.repo)
                RecomendedModuleItem(
                    title = def.title,
                    author = def.author,
                    repo = def.repo,
                    version = cachedVer,
                    latestVersion = cachedVer,
                    downloadUrl = cachedUrl,
                    status = ModuleStatus.DOWNLOAD
                )
            }
        )
    }

    fun refreshModules() {
        coroutineScope.launch(Dispatchers.IO) {
            val pendingReboot = getPendingRebootRepos(context)

            // Phase 1: FAST LOCAL CHECK
            // Immediately query ksud module list to mark installed modules with "OK" (no network delay)
            val installedList = fetchInstalledModules()
            val localChecked = modulesState.map { item ->
                val installed = findInstalledModule(installedList, item.title, item.author, item.repo)
                if (installed != null) {
                    saveCachedModuleInfo(context, item.repo, installed.version)
                    val status = if (pendingReboot.contains(item.repo)) ModuleStatus.REBOOT else ModuleStatus.INSTALLED
                    item.copy(
                        id = installed.id,
                        version = installed.version,
                        status = status,
                        isBusy = false,
                        progressText = "",
                        downloadProgress = 0f
                    )
                } else {
                    val cachedVer = getCachedModuleVersion(context, item.repo).ifBlank { item.version }
                    item.copy(
                        version = cachedVer,
                        latestVersion = cachedVer.ifBlank { item.latestVersion },
                        status = ModuleStatus.DOWNLOAD,
                        isBusy = false,
                        progressText = "",
                        downloadProgress = 0f
                    )
                }
            }

            withContext(Dispatchers.Main) {
                modulesState = localChecked
            }

            // Phase 2: ASYNC REMOTE CHECK
            // Check update.json first, then fall back to GitHub releases in background
            val updated = localChecked.map { item ->
                val installed = findInstalledModule(installedList, item.title, item.author, item.repo)
                val effectiveUpdateJson = installed?.updateJson?.ifBlank { null }

                val (latestTag, downloadUrl) = fetchLatestRelease(item.repo, effectiveUpdateJson)
                
                val latest = if (latestTag.isNotBlank()) {
                    saveCachedModuleInfo(context, item.repo, latestTag, downloadUrl)
                    latestTag
                } else {
                    item.latestVersion.ifBlank { getCachedModuleVersion(context, item.repo) }
                }

                val effectiveDownloadUrl = if (downloadUrl.isNotBlank()) {
                    downloadUrl
                } else {
                    item.downloadUrl.ifBlank { getCachedModuleDownloadUrl(context, item.repo) }
                }

                val displayVersion = installed?.version ?: latest

                val status = when {
                    pendingReboot.contains(item.repo) -> ModuleStatus.REBOOT
                    installed == null -> ModuleStatus.DOWNLOAD
                    latest.isNotBlank() && isNewerVersion(latest, installed.version, installed.versionCode) -> ModuleStatus.UPDATE
                    else -> ModuleStatus.INSTALLED
                }

                item.copy(
                    id = installed?.id ?: item.id,
                    version = displayVersion,
                    latestVersion = latest,
                    downloadUrl = effectiveDownloadUrl,
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
            modulesState = modulesState.map { it.copy(isBusy = false, progressText = "", downloadProgress = 0f) }
            refreshModules()
        } else if (!isInstalling) {
            onNavigateBack()
        }
    })

    Box(modifier = Modifier.fillMaxSize()) {
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
            items(modulesState, key = { it.repo }) { module ->
                ModuleRowItem(
                    module = module,
                    onActionClick = {
                        if (module.status == ModuleStatus.REBOOT) {
                            Toast.makeText(context, "Rebooting device...", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch(Dispatchers.IO) {
                                rebootDevice()
                            }
                            return@ModuleRowItem
                        }
                        if (module.status == ModuleStatus.INSTALLED) {
                            Toast.makeText(context, "${module.title} is already installed", Toast.LENGTH_SHORT).show()
                            return@ModuleRowItem
                        }

                        // Start Download and Install
                        coroutineScope.launch {
                            modulesState = modulesState.map {
                                if (it.repo == module.repo) it.copy(isBusy = true, progressText = "Preparing...", downloadProgress = 0f) else it
                            }

                            // If downloadUrl is blank, attempt to resolve it now
                            var targetUrl = module.downloadUrl
                            if (targetUrl.isBlank()) {
                                val (latestTag, url) = withContext(Dispatchers.IO) {
                                    fetchLatestRelease(module.repo)
                                }
                                if (url.isNotBlank()) {
                                    targetUrl = url
                                    modulesState = modulesState.map {
                                        if (it.repo == module.repo) it.copy(downloadUrl = url, latestVersion = latestTag.ifBlank { it.latestVersion }) else it
                                    }
                                }
                            }

                            if (targetUrl.isBlank()) {
                                Toast.makeText(context, "Could not fetch release URL. Check connection.", Toast.LENGTH_SHORT).show()
                                modulesState = modulesState.map {
                                    if (it.repo == module.repo) it.copy(isBusy = false, progressText = "") else it
                                }
                                return@launch
                            }

                            consoleOutput = "--- Preparing installation for ${module.title} ---\n"
                            isInstalling = true

                            val zipFile = withContext(Dispatchers.IO) {
                                downloadModuleZip(context, targetUrl) { progress ->
                                    modulesState = modulesState.map {
                                        if (it.repo == module.repo) it.copy(
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
                                    if (it.repo == module.repo) it.copy(isBusy = false, progressText = "", downloadProgress = 0f) else it
                                }
                                return@launch
                            }

                            modulesState = modulesState.map {
                                if (it.repo == module.repo) it.copy(progressText = "Flashing...", downloadProgress = 1f) else it
                            }

                            consoleOutput = consoleOutput + "\n--- Download Complete (${zipFile.length() / 1024} KB) ---\n"
                            consoleOutput = consoleOutput + "- Running module installer via ksud...\n\n"

                            try {
                                withContext(Dispatchers.IO) {
                                    flashModuleZip(zipFile) { line ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            consoleOutput = (consoleOutput ?: "") + line + "\n"
                                        }
                                    }
                                }
                            } finally {
                                withContext(Dispatchers.IO) {
                                    try {
                                        if (zipFile.exists()) {
                                            zipFile.delete()
                                        }
                                        // Root rm cleanup as guaranteed fallback
                                        Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -f '${zipFile.absolutePath}'")).waitFor()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }

                            markModulePendingReboot(context, module.repo)
                            isInstalling = false
                            consoleOutput = (consoleOutput ?: "") + "\n\n--- Finished! Please Reboot to activate ---"
                            modulesState = modulesState.map {
                                if (it.repo == module.repo) it.copy(status = ModuleStatus.REBOOT, isBusy = false, progressText = "", downloadProgress = 0f) else it
                            }
                            refreshModules()
                        }
                    }
                )
            }
        } // Close LazyColumn
    } // Close main Column

    // Animated modal for Module Flasher
    AnimatedVisibility(
        visible = consoleOutput != null,
        enter = fadeIn(tween(300)) + slideInVertically(
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ),
        exit = fadeOut(tween(250)) + slideOutVertically(
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            targetOffsetY = { it }
        )
    ) {
        ModuleInstallPrompt(
            title = "Module Flasher",
            output = consoleOutput ?: "",
            isInstalling = isInstalling,
            onClose = {
                consoleOutput = null
                modulesState = modulesState.map { it.copy(isBusy = false, progressText = "", downloadProgress = 0f) }
                refreshModules()
            }
        )
    }
} // Close Box
} // Close RecomendedModulesScreen

@Composable
fun ModuleRowItem(
    module: RecomendedModuleItem,
    onActionClick: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = module.downloadProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
        label = "DownloadProgress"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Title row
            if (module.isBusy && animatedProgress > 0f) {
                val greenColor = Color(0xFFA1D477)
                val whiteColor = Color.White
                
                // Horizontal brush that transitions from green to white smoothly at current progress point
                val titleBrush = Brush.horizontalGradient(
                    0.0f to greenColor,
                    animatedProgress to greenColor,
                    animatedProgress to whiteColor,
                    1.0f to whiteColor
                )

                val displayTitle = when (module.status) {
                    ModuleStatus.UPDATE -> "${module.title} - UPDATE"
                    ModuleStatus.REBOOT -> "${module.title} - REBOOT"
                    else -> module.title
                }

                Text(
                    text = displayTitle,
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
                    ModuleStatus.REBOOT -> {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.White)) { append(module.title) }
                                append(" - ")
                                withStyle(SpanStyle(color = Color(0xFFFFF185))) { append("REBOOT") }
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
            } else if (module.version.isNotBlank()) {
                module.version
            } else if (module.latestVersion.isNotBlank()) {
                module.latestVersion
            } else {
                ""
            }

            val versionSuffix = if (subtitleVersion.isNotBlank()) " • $subtitleVersion" else ""

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("By: ${module.author}") }
                    if (versionSuffix.isNotBlank()) {
                        withStyle(SpanStyle(color = Color(0xFF26C6DA))) { append(versionSuffix) }
                    }
                    if (module.isBusy && module.progressText.isNotBlank()) {
                        withStyle(SpanStyle(color = Color(0xFFE05A67))) { append(" [${module.progressText}]") }
                    }
                },
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp
            )
        }

        // Action icon button with smooth morph animation
        IconButton(
            onClick = onActionClick,
            enabled = !module.isBusy,
            modifier = Modifier.size(36.dp)
        ) {
            AnimatedContent(
                targetState = module.isBusy to module.status,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220, delayMillis = 40)) + scaleIn(initialScale = 0.75f, animationSpec = tween(220, delayMillis = 40)))
                        .togetherWith(fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.75f, animationSpec = tween(140)))
                },
                label = "ActionIconAnim"
            ) { (isBusy, status) ->
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF26C6DA)
                    )
                } else {
                    when (status) {
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
                        ModuleStatus.REBOOT -> {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_restart),
                                contentDescription = "Reboot for ${module.title}",
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
}
