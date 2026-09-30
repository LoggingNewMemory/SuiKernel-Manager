package kanagawa.yamada.suikernel.manager

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.view.WindowCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SuiKernelManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen()
                }
            }
        }
    }
}

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    var wallpaperBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
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
        Text(
            text = "By: Kanagawa Yamada",
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.Light,
            fontSize = 14.sp,
            color = TextPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

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
                    colors = CardDefaults.cardColors(containerColor = CardDark)
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
                            text = "1.0",
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
                    colors = CardDefaults.cardColors(containerColor = CardDark)
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
                            text = "5.10.259",
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp,
                            color = TextPrimary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "TECNO LH8n",
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
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
            ) {
                if (wallpaperBitmap != null) {
                    Image(
                        bitmap = wallpaperBitmap!!,
                        contentDescription = "User Wallpaper",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF3B4045)))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big Cyan Button
        Button(
            onClick = { },
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
                Text(text = ">", fontSize = 20.sp, color = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SuiKernel Settings
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
                Text(text = ">", fontSize = 20.sp, color = TextSecondary)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp)) // Extra space at bottom for scrolling comfortably
    }
}
