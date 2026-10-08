package kanagawa.yamada.suikernel.manager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import kanagawa.yamada.suikernel.manager.ui.theme.*

@Composable
fun SuiKernelSettingsScreen(onNavigateBack: () -> Unit = {}) {
    BackHandler(onBack = onNavigateBack)
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = context.getSharedPreferences("SuiKernelPrefs", android.content.Context.MODE_PRIVATE)

    var anyaThermal by remember { mutableStateOf(sharedPrefs.getBoolean("anyaThermal", true)) }
    var yamadaBoost by remember { mutableStateOf(sharedPrefs.getBoolean("yamadaBoost", true)) }
    var performanceMode by remember { mutableStateOf(sharedPrefs.getString("performanceMode", "Balanced") ?: "Balanced") }
    
    var inahoAudio by remember { mutableStateOf(sharedPrefs.getBoolean("inahoAudio", true)) }
    var tenebrion by remember { mutableStateOf(sharedPrefs.getBoolean("tenebrion", true)) }
    var airaniCpuset by remember { mutableStateOf(sharedPrefs.getBoolean("airaniCpuset", true)) }
    var sandevistan by remember { mutableStateOf(sharedPrefs.getBoolean("sandevistan", false)) }
    var sparxieSwap by remember { mutableStateOf(sharedPrefs.getFloat("sparxieSwap", 60f)) }
    var sparxieEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("sparxieEnabled", true)) }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        // Header
        Column(modifier = Modifier.wrapContentWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_comet),
                    contentDescription = "Comet Icon",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Divider(
                    color = Color.White,
                    modifier = Modifier
                        .height(48.dp)
                        .width(1.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = "SuiKernel Settings",
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFFE05A67))) { append("Customize ") }
                            withStyle(SpanStyle(color = Color(0xFF26C6DA))) { append("SuiKernel ") }
                            withStyle(SpanStyle(color = Color.White)) { append("Built-In ") }
                            withStyle(SpanStyle(color = Color(0xFFA1D477))) { append("Driver") }
                        },
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Light,
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Anya Melfissa Thermal Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            SettingToggleItem(
                title = "Anya Melfissa Thermal",
                subtitle = "Enable / Disable Anya Thermal Kernel Side",
                checked = anyaThermal,
                onCheckedChange = { 
                    anyaThermal = it
                    sharedPrefs.edit().putBoolean("anyaThermal", it).apply()
                    SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_ANYA_THERMAL, if (it) 1L else 0L)
                },
                modifier = Modifier.padding(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Yamada Touch Boost Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingToggleItem(
                    title = "Yamada Touch Boost",
                    subtitle = "Schedutil Direct Hook for Touch Input",
                    checked = yamadaBoost,
                    onCheckedChange = { 
                        yamadaBoost = it 
                        sharedPrefs.edit().putBoolean("yamadaBoost", it).apply()
                        if (it) {
                            if (performanceMode == "Gaming") {
                                SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_YAMADA_TOUCH_BOOST_GAMING, 1L)
                            } else {
                                SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_YAMADA_TOUCH_BOOST_BALANCED, 1L)
                            }
                        } else {
                            SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_YAMADA_TOUCH_BOOST_DISABLE, 1L)
                        }
                    }
                )
                
                AnimatedVisibility(visible = yamadaBoost) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Balanced Button
                        Button(
                            onClick = { 
                                performanceMode = "Balanced" 
                                sharedPrefs.edit().putString("performanceMode", "Balanced").apply()
                                SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_YAMADA_TOUCH_BOOST_BALANCED, 1L)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (performanceMode == "Balanced") Color(0xFF00ACC1) else Color(0xFF1C2830)
                            )
                        ) {
                            Text(
                                text = "Balanced",
                                fontFamily = GoogleSansFlex,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Gaming Button
                        Button(
                            onClick = { 
                                performanceMode = "Gaming" 
                                sharedPrefs.edit().putString("performanceMode", "Gaming").apply()
                                SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_YAMADA_TOUCH_BOOST_GAMING, 1L)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (performanceMode == "Gaming") Color(0xFFE05A67) else Color(0xFF1C2830)
                            )
                        ) {
                            Text(
                                text = "Gaming",
                                fontFamily = GoogleSansFlex,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Inaho Audio Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            SettingToggleItem(
                title = "Ochinai Inaho Audio",
                subtitle = "SCHED_FIFO boost & PM QoS for high-res audio",
                checked = inahoAudio,
                onCheckedChange = { 
                    inahoAudio = it
                    sharedPrefs.edit().putBoolean("inahoAudio", it).apply()
                    SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_INAHO_AUDIO, if (it) 1L else 0L)
                },
                modifier = Modifier.padding(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Tenebrion Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            SettingToggleItem(
                title = "Tenebrion",
                subtitle = "Screen state based CPU frequency throttler",
                checked = tenebrion,
                onCheckedChange = { 
                    tenebrion = it
                    sharedPrefs.edit().putBoolean("tenebrion", it).apply()
                    SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_TENEBRION, if (it) 1L else 0L)
                },
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Airani Cpuset Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            SettingToggleItem(
                title = "Airani Iofifteen",
                subtitle = "Maximum CPUSet Tweaks via Raco API",
                checked = airaniCpuset,
                onCheckedChange = { 
                    airaniCpuset = it
                    sharedPrefs.edit().putBoolean("airaniCpuset", it).apply()
                    SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_AIRANI_CPUSET, if (it) 1L else 0L)
                },
                modifier = Modifier.padding(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sandevistan Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            SettingToggleItem(
                title = "Sandevistan Boot",
                subtitle = "min=max frequency lock for fast booting",
                checked = sandevistan,
                onCheckedChange = { 
                    sandevistan = it
                    sharedPrefs.edit().putBoolean("sandevistan", it).apply()
                    SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_SANDEVISTAN, if (it) 1L else 0L)
                },
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Sparxie Swappiness Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingToggleItem(
                    title = "Sparxie Swappiness Tuner",
                    subtitle = "Actively override vm_swappiness (Current: ${sparxieSwap.toInt()})",
                    checked = sparxieEnabled,
                    onCheckedChange = { 
                        sparxieEnabled = it
                        if (!it) {
                            sparxieSwap = 100f
                            SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_SPARXIE_SWAP, 100L)
                        } else {
                            SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_SPARXIE_SWAP, sparxieSwap.toLong())
                        }
                        sharedPrefs.edit()
                            .putBoolean("sparxieEnabled", it)
                            .putFloat("sparxieSwap", sparxieSwap)
                            .apply()
                    }
                )
                
                AnimatedVisibility(visible = sparxieEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = sparxieSwap,
                            onValueChange = { 
                                sparxieSwap = it 
                                sharedPrefs.edit().putFloat("sparxieSwap", it).apply()
                                SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_SPARXIE_SWAP, it.toLong())
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF00ACC1),
                                inactiveTrackColor = Color.DarkGray
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = Color.White
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF00ACC1),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}
