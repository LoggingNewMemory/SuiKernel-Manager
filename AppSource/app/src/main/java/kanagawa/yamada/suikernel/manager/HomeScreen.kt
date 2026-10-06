package kanagawa.yamada.suikernel.manager

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kanagawa.yamada.suikernel.manager.ui.theme.*

@Composable
fun DashboardScreen(onNavigateToSettings: () -> Unit = {}) {
    val context = LocalContext.current
    var wallpaperBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val scrollState = rememberScrollState()

    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    val kernelInfo = remember {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/version"))
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()

            val realKernelString = output?.split(" ")?.getOrNull(2) ?: ""
            val isSuiKernel = realKernelString.contains("SuiKernel", ignoreCase = true)
            val isKsuNext = realKernelString.contains("KernelSU-Next", ignoreCase = true)
            
            val versionPrefix = if (realKernelString.isNotEmpty()) realKernelString.split("-")[0] else "Unknown"
            Pair(versionPrefix, isSuiKernel && isKsuNext)
        } catch (e: Exception) {
            Pair("Unknown", false)
        }
    }
    
    val kernelVersion = kernelInfo.first
    val isOfficial = kernelInfo.second

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

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var isLoaded = false
            try {
                // Since this app is a root app, fetch wallpaper directly from system to bypass permissions
                val cacheFile = java.io.File(context.cacheDir, "root_wallpaper")
                val cmd = "cp /data/system/users/0/wallpaper ${cacheFile.absolutePath} && chmod 644 ${cacheFile.absolutePath}"
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                process.waitFor()
                
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                    if (bitmap != null) {
                        wallpaperBitmap = bitmap.asImageBitmap()
                        isLoaded = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (!isLoaded) {
                try {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    val drawable = wallpaperManager.drawable
                    if (drawable != null) {
                        if (drawable is BitmapDrawable) {
                            wallpaperBitmap = drawable.bitmap.asImageBitmap()
                        } else {
                            val bitmap = Bitmap.createBitmap(
                                if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1080,
                                if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1920,
                                Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(bitmap)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            wallpaperBitmap = bitmap.asImageBitmap()
                        }
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    // Requires READ_EXTERNAL_STORAGE permission
                }
            }
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
        Text(
            text = "SuiKernel Manager",
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            color = TextPrimary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
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

        // Recommended Modules
        Card(
            modifier = Modifier.fillMaxWidth(),
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
