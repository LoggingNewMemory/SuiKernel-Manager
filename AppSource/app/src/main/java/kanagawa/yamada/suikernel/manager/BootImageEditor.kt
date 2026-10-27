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
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kanagawa.yamada.suikernel.manager.ui.theme.CardDark
import kanagawa.yamada.suikernel.manager.ui.theme.GoogleSansFlex
import kanagawa.yamada.suikernel.manager.ui.theme.PrimaryCyan
import kanagawa.yamada.suikernel.manager.ui.theme.TextPrimary
import kanagawa.yamada.suikernel.manager.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipFile

fun interface BootEditorCallback {
    fun onLog(line: String)
}

object SuiKernelBootEditor {
    init {
        try {
            System.loadLibrary("suikernel_ioctl")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    external fun nativeGetActiveSlot(): String
    external fun nativeFindBootPartition(): String?
    external fun nativePullBoot(targetPath: String, callback: BootEditorCallback): Int
    external fun nativeFlashBoot(sourcePath: String, callback: BootEditorCallback): Int
}

@Composable
fun BootImageEditorScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var consoleTitle by remember { mutableStateOf("") }
    var consoleOutput by remember { mutableStateOf<String?>(null) }
    var isOperating by remember { mutableStateOf(false) }
    var canReboot by remember { mutableStateOf(false) }

    var showPullConfirmDialog by remember { mutableStateOf(false) }

    var pendingFlashImgUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFlashImgName by remember { mutableStateOf("") }
    var showFlashImgDialog by remember { mutableStateOf(false) }

    var pendingZipUri by remember { mutableStateOf<Uri?>(null) }
    var pendingZipName by remember { mutableStateOf("") }
    var showFlashZipDialog by remember { mutableStateOf(false) }

    val imgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingFlashImgUri = uri
            pendingFlashImgName = queryFileName(context, uri) ?: "boot.img"
            showFlashImgDialog = true
        }
    }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingZipUri = uri
            pendingZipName = queryFileName(context, uri) ?: "kernel.zip"
            showFlashZipDialog = true
        }
    }

    BackHandler(onBack = {
        if (consoleOutput != null && !isOperating) {
            consoleOutput = null
        } else if (!isOperating) {
            onNavigateBack()
        }
    })

    fun runProcessWithStreaming(
        cmd: String,
        onLine: (String) -> Unit
    ): Int {
        return try {
            try {
                SuiKernelIoctl.nativeElevateThread()
            } catch (e: Throwable) {
                // Ignore if JNI not loaded
            }

            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()

            try {
                process.outputStream.close()
            } catch (ignored: Exception) {}

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { onLine(it) }
            }

            val finished = process.waitFor(300, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                onLine("\n[!] Operation timed out after 300 seconds.")
                -1
            } else {
                process.exitValue()
            }
        } catch (e: Exception) {
            onLine("\n[!] Execution error: ${e.message ?: "Unknown error"}")
            -1
        }
    }

    fun startPullBoot() {
        consoleTitle = "Pull Boot Partition"
        consoleOutput = ""
        isOperating = true
        canReboot = false

        coroutineScope.launch(Dispatchers.IO) {
            val outDir = File("/sdcard/Download")
            if (!outDir.exists()) {
                outDir.mkdirs()
            }
            val outFile = File(outDir, "SuiKernel_boot.img")

            val exitCode = SuiKernelBootEditor.nativePullBoot(outFile.absolutePath) { line ->
                coroutineScope.launch(Dispatchers.Main) {
                    consoleOutput = (consoleOutput ?: "") + line + "\n"
                }
            }

            try {
                MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), null, null)
            } catch (ignored: Exception) {}

            withContext(Dispatchers.Main) {
                isOperating = false
                if (exitCode == 0) {
                    consoleOutput = (consoleOutput ?: "") + "\n--- Pull complete! Stored in Download/SuiKernel_boot.img ---"
                } else {
                    consoleOutput = (consoleOutput ?: "") + "\n--- Pull failed with code $exitCode ---"
                }
            }
        }
    }

    fun startFlashBoot(uri: Uri) {
        consoleTitle = "Flash Boot Partition"
        consoleOutput = ""
        isOperating = true
        canReboot = false

        coroutineScope.launch(Dispatchers.IO) {
            val tempImg = File(context.cacheDir, "flash_boot_${System.currentTimeMillis()}.img")
            try {
                withContext(Dispatchers.Main) {
                    consoleOutput = "[*] Reading selected image file...\n"
                }

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempImg).use { output ->
                        input.copyTo(output)
                    }
                }

                if (!tempImg.exists() || tempImg.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        consoleOutput = (consoleOutput ?: "") + "[!] Error: Empty or unreadable boot image!\n"
                        isOperating = false
                    }
                    return@launch
                }

                val exitCode = SuiKernelBootEditor.nativeFlashBoot(tempImg.absolutePath) { line ->
                    coroutineScope.launch(Dispatchers.Main) {
                        consoleOutput = (consoleOutput ?: "") + line + "\n"
                    }
                }

                withContext(Dispatchers.Main) {
                    isOperating = false
                    if (exitCode == 0) {
                        canReboot = true
                        consoleOutput = (consoleOutput ?: "") + "\n\n--- Flashed Successfully! Please Reboot to activate ---"
                    } else {
                        consoleOutput = (consoleOutput ?: "") + "\n\n--- Flash failed with code $exitCode ---"
                    }
                }
            } finally {
                try {
                    if (tempImg.exists()) tempImg.delete()
                } catch (ignored: Exception) {}
            }
        }
    }

    fun startFlashAnyKernel(uri: Uri) {
        consoleTitle = "AnyKernel Flasher"
        consoleOutput = "=== AnyKernel3 Flasher ===\n"
        isOperating = true
        canReboot = false

        coroutineScope.launch(Dispatchers.IO) {
            val tempZip = File(context.cacheDir, "ak3_${System.currentTimeMillis()}.zip")
            val targetDir = "/data/local/tmp/ak3-tmp"

            try {
                withContext(Dispatchers.Main) {
                    consoleOutput = (consoleOutput ?: "") + "[*] Copying AnyKernel package...\n"
                }

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempZip).use { output ->
                        input.copyTo(output)
                    }
                }

                if (!tempZip.exists() || tempZip.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        consoleOutput = (consoleOutput ?: "") + "[!] Error: Failed to read selected zip file!\n"
                        isOperating = false
                    }
                    return@launch
                }

                val hasInstaller = try {
                    ZipFile(tempZip).use { zip ->
                        zip.getEntry("META-INF/com/google/android/update-binary") != null ||
                                zip.getEntry("anykernel.sh") != null
                    }
                } catch (e: Exception) {
                    false
                }

                if (!hasInstaller) {
                    withContext(Dispatchers.Main) {
                        consoleOutput = (consoleOutput ?: "") + "[!] Error: Invalid AnyKernel3 package!\n"
                        consoleOutput = (consoleOutput ?: "") + "Missing META-INF/com/google/android/update-binary or anykernel.sh.\n"
                        isOperating = false
                    }
                    return@launch
                }

                val targetZipPath = "/data/local/tmp/ak3-input.zip"

                val script = """
                    echo "[*] Setting up AnyKernel3 environment..."
                    rm -rf "$targetDir" "$targetZipPath"
                    mkdir -p "$targetDir"
                    cp "${tempZip.absolutePath}" "$targetZipPath"

                    echo "[*] Extracting package..."
                    unzip -oq "$targetZipPath" -d "$targetDir" 2>/dev/null || true
                    chmod -R 755 "$targetDir"
                    cd "$targetDir"
                    export BOOTMODE=true
                    export AKHOME="$targetDir"

                    echo "[*] Executing AnyKernel3 update-binary..."
                    if [ -f "META-INF/com/google/android/update-binary" ]; then
                        chmod 755 META-INF/com/google/android/update-binary
                        sh META-INF/com/google/android/update-binary 3 1 "$targetZipPath"
                    elif [ -f "anykernel.sh" ]; then
                        sh anykernel.sh
                    else
                        echo "[!] Error: No installer binary found in package!"
                        exit 1
                    fi
                """.trimIndent()

                val exitCode = runProcessWithStreaming(script) { line ->
                    coroutineScope.launch(Dispatchers.Main) {
                        consoleOutput = (consoleOutput ?: "") + line + "\n"
                    }
                }

                // Cleanup
                runProcessWithStreaming("rm -rf '$targetDir' '$targetZipPath'") {}

                withContext(Dispatchers.Main) {
                    isOperating = false
                    if (exitCode == 0) {
                        canReboot = true
                        consoleOutput = (consoleOutput ?: "") + "\n\n--- AnyKernel Succeeded! Please Reboot to activate ---"
                    } else {
                        consoleOutput = (consoleOutput ?: "") + "\n\n--- Flashing failed with exit code $exitCode ---"
                    }
                }
            } finally {
                try {
                    if (tempZip.exists()) tempZip.delete()
                } catch (ignored: Exception) {}
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            // Header matching Designs/OUT/4.png
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Boot Image Editor",
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFF26C6DA))) {
                            append("Pull and Flash")
                        }
                        append(" Boot Partition ")
                        withStyle(SpanStyle(color = Color(0xFFE05A67))) {
                            append("/")
                        }
                        withStyle(SpanStyle(color = Color(0xFFA1D477))) {
                            append("/ Anykernel Flasher")
                        }
                    },
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Card 1: Pull Boot Partition
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPullConfirmDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pull Boot Partition",
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Boot.img will be stored on Download folder",
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Icon(
                        painter = painterResource(id = R.drawable.ic_right_arrow),
                        contentDescription = "Action",
                        tint = Color.White,
                        modifier = Modifier.height(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Card 2: Flash Boot Partition
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { imgPickerLauncher.launch("*/*") },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Flash Boot Partition",
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Flash Patched Boot.img",
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Icon(
                        painter = painterResource(id = R.drawable.ic_right_arrow),
                        contentDescription = "Action",
                        tint = Color.White,
                        modifier = Modifier.height(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Horizontal Divider
            HorizontalDivider(
                color = Color(0xFF383E44),
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Card 3: AnyKernel Flasher (Red tinted arrow matching Designs/OUT/4.png)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { zipPickerLauncher.launch("*/*") },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AnyKernel Flasher",
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Flash AnyKernel.zip",
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Icon(
                        painter = painterResource(id = R.drawable.ic_right_arrow),
                        contentDescription = "Action",
                        tint = Color(0xFFE05A67), // Red/coral tinted arrow as in design
                        modifier = Modifier.height(18.dp)
                    )
                }
            }
        }

        // Pull Boot Confirmation Dialog
        if (showPullConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showPullConfirmDialog = false },
                containerColor = CardDark,
                title = {
                    Text(
                        text = "Pull Boot Partition",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = "Dump active boot partition and save to:\n/sdcard/Download/SuiKernel_boot.img\n\nContinue?",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPullConfirmDialog = false
                            startPullBoot()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                    ) {
                        Text("Pull", fontFamily = GoogleSansFlex, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPullConfirmDialog = false }) {
                        Text("Cancel", fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal, color = TextSecondary)
                    }
                }
            )
        }

        // Flash Boot Confirmation Dialog
        if (showFlashImgDialog && pendingFlashImgUri != null) {
            AlertDialog(
                onDismissRequest = {
                    showFlashImgDialog = false
                    pendingFlashImgUri = null
                },
                containerColor = CardDark,
                title = {
                    Text(
                        text = "Flash Boot Partition",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE05A67)
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to flash this image directly to the active boot partition?\n\nFile: $pendingFlashImgName\n\nWarning: Flashing an incompatible boot image may cause bootloop.",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val uri = pendingFlashImgUri
                            showFlashImgDialog = false
                            pendingFlashImgUri = null
                            if (uri != null) {
                                startFlashBoot(uri)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE05A67))
                    ) {
                        Text("Flash", fontFamily = GoogleSansFlex, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showFlashImgDialog = false
                        pendingFlashImgUri = null
                    }) {
                        Text("Cancel", fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal, color = TextSecondary)
                    }
                }
            )
        }

        // Flash AnyKernel Confirmation Dialog
        if (showFlashZipDialog && pendingZipUri != null) {
            AlertDialog(
                onDismissRequest = {
                    showFlashZipDialog = false
                    pendingZipUri = null
                },
                containerColor = CardDark,
                title = {
                    Text(
                        text = "AnyKernel Flasher",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = "Flash AnyKernel package to your active kernel?\n\nFile: $pendingZipName",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val uri = pendingZipUri
                            showFlashZipDialog = false
                            pendingZipUri = null
                            if (uri != null) {
                                startFlashAnyKernel(uri)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                    ) {
                        Text("Flash", fontFamily = GoogleSansFlex, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showFlashZipDialog = false
                        pendingZipUri = null
                    }) {
                        Text("Cancel", fontFamily = GoogleSansFlex, fontWeight = FontWeight.Normal, color = TextSecondary)
                    }
                }
            )
        }

        // Standalone Terminal Console Overlay
        AnimatedVisibility(
            visible = consoleOutput != null,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200))
        ) {
            BootConsoleOverlay(
                title = consoleTitle,
                output = consoleOutput ?: "",
                isOperating = isOperating,
                canReboot = canReboot,
                onClose = {
                    if (!isOperating) {
                        consoleOutput = null
                    }
                }
            )
        }
    }
}

@Composable
private fun BootConsoleOverlay(
    title: String,
    output: String,
    isOperating: Boolean,
    canReboot: Boolean,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    LaunchedEffect(output) {
        scrollState.animateScrollTo(
            value = scrollState.maxValue,
            animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101214))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOperating) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF26C6DA))
                            .alpha(pulseAlpha)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Text(
                    text = title,
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                AnimatedVisibility(
                    visible = !isOperating,
                    enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.6f),
                    exit = fadeOut(tween(150)) + scaleOut()
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
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
                    color = Color(0xFFE0E0E0),
                    modifier = Modifier.padding(bottom = if (!isOperating && canReboot) 64.dp else 0.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = !isOperating && canReboot,
            enter = fadeIn(tween(350)) + slideInVertically(initialOffsetY = { it / 2 }) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(200)) + slideOutVertically { it / 2 } + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            SuiKernelIoctl.runCommand("/system/bin/svc power reboot || /system/bin/reboot")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reboot",
                        tint = Color.White
                    )
                },
                text = {
                    Text(
                        text = "Reboot",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                },
                containerColor = PrimaryCyan,
                contentColor = Color.White
            )
        }
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        } catch (ignored: Exception) {}
    }
    return uri.lastPathSegment
}
