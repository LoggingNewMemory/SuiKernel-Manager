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

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kanagawa.yamada.suikernel.manager.ui.theme.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.ui.draw.alpha

enum class WallpaperMode {
    SUISEI_1,
    SUISEI_2,
    DEVICE
}

private const val PREFS_UI = "ui_preferences"
private const val KEY_WALLPAPER_MODE = "wallpaper_mode"

private fun getSavedWallpaperMode(context: Context): WallpaperMode {
    val prefs = context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
    val saved = prefs.getString(KEY_WALLPAPER_MODE, WallpaperMode.DEVICE.name)
    return try {
        WallpaperMode.valueOf(saved ?: WallpaperMode.DEVICE.name)
    } catch (e: Exception) {
        WallpaperMode.DEVICE
    }
}

private fun saveWallpaperMode(context: Context, mode: WallpaperMode) {
    val prefs = context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_WALLPAPER_MODE, mode.name).apply()
}

object WallpaperCache {
    val bitmap = mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    var isLoaded = false
    val loadAttempted = mutableStateOf(false)
}

@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRecomendedModules: () -> Unit = {}
) {
    val context = LocalContext.current
    val wallpaperBitmap by WallpaperCache.bitmap
    val loadAttempted by WallpaperCache.loadAttempted
    var wallpaperMode by remember { mutableStateOf(getSavedWallpaperMode(context)) }
    val effectiveMode = when {
        wallpaperMode == WallpaperMode.DEVICE && wallpaperBitmap != null -> WallpaperMode.DEVICE
        wallpaperMode == WallpaperMode.DEVICE && loadAttempted && wallpaperBitmap == null -> WallpaperMode.SUISEI_1
        wallpaperMode == WallpaperMode.DEVICE -> WallpaperMode.DEVICE
        else -> wallpaperMode
    }
    var fetchTrigger by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                    WallpaperCache.isLoaded = false
                    WallpaperCache.loadAttempted.value = false
                    fetchTrigger++
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    val kernelInfo = remember {
        try {
            val hasRoot = checkRootAccess()
            var output: String? = null
            if (hasRoot) {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/version"))
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                output = reader.readLine()
                process.waitFor()
            }

            val realKernelString = output?.split(" ")?.getOrNull(2) ?: ""
            val isSuiKernel = output?.contains("SuiKernel", ignoreCase = true) == true
            val isKsuNext = output?.contains("KernelSU-Next", ignoreCase = true) == true
            val versionPrefix = if (realKernelString.isNotEmpty()) realKernelString.split("-")[0] else "Unknown"

            val isOfficial = hasRoot && isSuiKernel && isKsuNext
            val rejectReason = when {
                !hasRoot -> "No Root"
                !isSuiKernel -> "Not SuiKernel"
                !isKsuNext -> "Not KernelSU-Next"
                else -> ""
            }

            Triple(versionPrefix, isOfficial, rejectReason)
        } catch (e: Exception) {
            Triple("Unknown", false, "Unknown Error")
        }
    }
    
    val kernelVersion = kernelInfo.first
    val isOfficial = kernelInfo.second
    val rejectReason = kernelInfo.third

    val deviceName = remember {
        var vendorModel = ""
        try {
            val process = Runtime.getRuntime().exec("getprop ro.product.vendor.model")
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            vendorModel = reader.readLine() ?: ""
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (vendorModel.isNotBlank()) {
            vendorModel
        } else {
            val manufacturer = android.os.Build.MANUFACTURER
            val model = android.os.Build.MODEL
            if (model.lowercase().startsWith(manufacturer.lowercase())) {
                model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            } else {
                manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() } + " " + model
            }
        }
    }

    LaunchedEffect(fetchTrigger) {
        if (WallpaperCache.isLoaded) {
            WallpaperCache.loadAttempted.value = true
            return@LaunchedEffect
        }

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var localIsLoaded = false
            try {
                // Since this app is a root app, fetch wallpaper directly from system to bypass permissions
                val cacheFile = java.io.File(context.cacheDir, "root_wallpaper")
                val cmd = "cp /data/system/users/0/wallpaper ${cacheFile.absolutePath} && chmod 644 ${cacheFile.absolutePath}"
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                process.waitFor()
                
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                    if (bitmap != null) {
                        WallpaperCache.bitmap.value = bitmap.asImageBitmap()
                        localIsLoaded = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (!localIsLoaded) {
                try {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    var drawable = try {
                        wallpaperManager.drawable
                    } catch (e: SecurityException) {
                        null
                    }

                    if (drawable == null) {
                        try {
                            drawable = wallpaperManager.builtInDrawable
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }

                    if (drawable != null) {
                        if (drawable is BitmapDrawable) {
                            WallpaperCache.bitmap.value = drawable.bitmap.asImageBitmap()
                            localIsLoaded = true
                        } else {
                            val bitmap = Bitmap.createBitmap(
                                if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1080,
                                if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1920,
                                Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(bitmap)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            WallpaperCache.bitmap.value = bitmap.asImageBitmap()
                            localIsLoaded = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (localIsLoaded) {
                WallpaperCache.isLoaded = true
            }
            WallpaperCache.loadAttempted.value = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = "SuiKernel Manager",
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    color = TextPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "By: Kanagawa Yamada",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Light,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isOfficial) Color(0xFF4CAF50) else Color(0xFFE05A67),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isOfficial) "OFFICIAL" else "REJECTED",
                            fontFamily = GoogleSansFlex,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .offset(y = 2.dp)
                    .size(35.dp)
                    .clip(CircleShape)
                    .clickable {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/LoggingNewMemory/SuiKernel-Manager")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = "GitHub Repository",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(35.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp), // Reduced height drastically
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // S.Manager IS Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF373F45))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "S.Manager IS",
                            fontFamily = HarmonyOS,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = appVersion,
                            fontFamily = SFCompactRounded,
                            fontSize = 32.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .background(PillBackground, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Kanagawa Yamada",
                                fontFamily = GoogleSansFlex,
                                fontSize = 10.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // SuiKernel IS Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2830))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "SuiKernel IS",
                            fontFamily = HarmonyOS,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = kernelVersion,
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp,
                            color = TextPrimary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = deviceName,
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Right Column (Image)
            Card(
                onClick = {
                    val nextMode = when (effectiveMode) {
                        WallpaperMode.SUISEI_1 -> WallpaperMode.SUISEI_2
                        WallpaperMode.SUISEI_2 -> if (wallpaperBitmap != null) WallpaperMode.DEVICE else WallpaperMode.SUISEI_1
                        WallpaperMode.DEVICE -> WallpaperMode.SUISEI_1
                    }
                    wallpaperMode = nextMode
                    saveWallpaperMode(context, nextMode)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF34404A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                ) {
                    Crossfade(
                        targetState = effectiveMode,
                        animationSpec = tween(durationMillis = 350),
                        label = "WallpaperCrossfade"
                    ) { mode ->
                        when (mode) {
                            WallpaperMode.SUISEI_1 -> {
                                Image(
                                    painter = painterResource(id = R.drawable.suisei_1),
                                    contentDescription = "Suisei 1",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            WallpaperMode.SUISEI_2 -> {
                                Image(
                                    painter = painterResource(id = R.drawable.suisei_2),
                                    contentDescription = "Suisei 2",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            WallpaperMode.DEVICE -> {
                                if (wallpaperBitmap != null) {
                                    Image(
                                        bitmap = wallpaperBitmap!!,
                                        contentDescription = "User Wallpaper",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF3B4045))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big Cyan Button
        Button(
            onClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://youtu.be/a51VH9BYzZA"))
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
        ) {
            Text(
                text = "すいちゃんは今日もかわいい！",
                fontFamily = HarmonyOS,
                fontSize = 14.sp,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isOfficial) {
            // Recommended Modules
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToRecomendedModules() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recomended Modules",
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(style = SpanStyle(color = AccentRed)) {
                                    append("Enhance")
                                }
                                append(" Your ")
                                withStyle(style = SpanStyle(color = PrimaryCyan)) {
                                    append("SuiKernel")
                                }
                                append(" Experience")
                            },
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Light,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_right_arrow),
                        contentDescription = "Right Arrow",
                        tint = TextSecondary,
                        modifier = Modifier.height(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SuiKernel Settings
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSettings() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SuiKernel Settings",
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(style = SpanStyle(color = AccentRed)) {
                                    append("Customize")
                                }
                                append(" ")
                                withStyle(style = SpanStyle(color = PrimaryCyan)) {
                                    append("SuiKernel")
                                }
                                append(" Built-In Driver")
                            },
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Light,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_right_arrow),
                        contentDescription = "Right Arrow",
                        tint = TextSecondary,
                        modifier = Modifier.height(16.dp)
                    )
                }
            }
        } else {
            // Rejection Banner Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFE05A67),
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "REJECTED - $rejectReason",
                                fontFamily = GoogleSansFlex,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Text(
                        text = "Modules & Settings Unavailable",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )

                    Text(
                        text = when (rejectReason) {
                            "Not SuiKernel" -> "SuiKernel manager only works with SuiKernel! Why you think SuiKernel Manager can work with other kernels?!"
                            "Not KernelSU-Next" -> "SuiKernel manager only works with KernelSU Next variant, if you use Vanilla. It just doesn't works, thanks."
                            "No Root" -> "You think you use the SuiKernel that already supports SuiKernel Manager huh? Nah it's not. Get out"
                            else -> "This device does not meet the requirements to access SuiKernel modules and settings."
                        },
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp)) // Extra space at bottom for scrolling comfortably
    }
}

fun checkRootAccess(): Boolean {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
        val output = reader.readLine()
        process.waitFor()
        output?.contains("uid=0(root)") == true || process.exitValue() == 0
    } catch (e: Exception) {
        false
    }
}
