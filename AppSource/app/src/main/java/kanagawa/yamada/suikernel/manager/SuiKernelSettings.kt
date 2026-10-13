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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kanagawa.yamada.suikernel.manager.ui.theme.*

@Composable
fun SuiKernelSettingsScreen(onNavigateBack: () -> Unit = {}) {
    BackHandler(onBack = onNavigateBack)
    val context = androidx.compose.ui.platform.LocalContext.current

    fun showRebootToast() {
        android.widget.Toast.makeText(context, "Please Reboot", android.widget.Toast.LENGTH_SHORT).show()
    }

    var isLoaded by remember { mutableStateOf(SuiKernelIoctl.isLoaded) }
    val initialConfig = SuiKernelIoctl.cachedConfig
    var anyaThermal by remember { mutableStateOf(initialConfig.anyaThermal) }
    var yamadaBoost by remember { mutableStateOf(initialConfig.yamadaBoost) }
    var performanceMode by remember { mutableStateOf(initialConfig.performanceMode) }
    
    var inahoAudio by remember { mutableStateOf(initialConfig.inahoAudio) }
    var tenebrion by remember { mutableStateOf(initialConfig.tenebrion) }
    var airaniCpuset by remember { mutableStateOf(initialConfig.airaniCpuset) }
    var sandevistan by remember { mutableStateOf(initialConfig.sandevistan) }
    var moonaZram by remember { mutableStateOf(initialConfig.moonaZram) }
    var sparxieSwap by remember { mutableStateOf(initialConfig.sparxieSwap) }
    var sparxieEnabled by remember { mutableStateOf(initialConfig.sparxieEnabled) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val config = SuiKernelIoctl.readStorage()
            withContext(Dispatchers.Main) {
                anyaThermal = config.anyaThermal
                yamadaBoost = config.yamadaBoost
                performanceMode = config.performanceMode
                inahoAudio = config.inahoAudio
                tenebrion = config.tenebrion
                airaniCpuset = config.airaniCpuset
                sandevistan = config.sandevistan
                moonaZram = config.moonaZram
                sparxieEnabled = config.sparxieEnabled
                sparxieSwap = config.sparxieSwap
                isLoaded = true
            }
        }
    }

    fun currentConfig() = SuiKernelConfig(
        anyaThermal = anyaThermal,
        yamadaBoost = yamadaBoost,
        performanceMode = performanceMode,
        inahoAudio = inahoAudio,
        tenebrion = tenebrion,
        airaniCpuset = airaniCpuset,
        sandevistan = sandevistan,
        moonaZram = moonaZram,
        sparxieEnabled = sparxieEnabled,
        sparxieSwap = sparxieSwap
    )

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
                
                VerticalDivider(
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
                subtitle = "Spoof temperature to 30 ish",
                checked = anyaThermal,
                onCheckedChange = { 
                    anyaThermal = it
                    SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_ANYA_THERMAL, if (it) 1L else 0L)
                    SuiKernelIoctl.syncStorage(currentConfig().copy(anyaThermal = it))
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
                    subtitle = "Boosts CPU freq to max on touch",
                    checked = yamadaBoost,
                    onCheckedChange = { 
                        yamadaBoost = it 
                        if (it) {
                            if (performanceMode == "Gaming") {
                                SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_YAMADA_TOUCH_BOOST_GAMING, 1L)
                            } else {
                                SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_YAMADA_TOUCH_BOOST_BALANCED, 1L)
                            }
                        } else {
                            SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_YAMADA_TOUCH_BOOST_DISABLE, 1L)
                        }
                        SuiKernelIoctl.syncStorage(currentConfig().copy(yamadaBoost = it))
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
                                SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_YAMADA_TOUCH_BOOST_BALANCED, 1L)
                                SuiKernelIoctl.syncStorage(currentConfig().copy(performanceMode = "Balanced"))
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
                                SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_YAMADA_TOUCH_BOOST_GAMING, 1L)
                                SuiKernelIoctl.syncStorage(currentConfig().copy(performanceMode = "Gaming"))
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
                title = "Ochinai Inaho",
                subtitle = "Boost Audio Priority + PM QoS tuning",
                checked = inahoAudio,
                onCheckedChange = { 
                    inahoAudio = it
                    SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_INAHO_AUDIO, if (it) 1L else 0L)
                    SuiKernelIoctl.syncStorage(currentConfig().copy(inahoAudio = it))
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
                subtitle = "Drops CPU freq to min on screen off",
                checked = tenebrion,
                onCheckedChange = { 
                    tenebrion = it
                    SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_TENEBRION, if (it) 1L else 0L)
                    SuiKernelIoctl.syncStorage(currentConfig().copy(tenebrion = it))
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
                subtitle = "Tweaks CPU Set",
                checked = airaniCpuset,
                onCheckedChange = { 
                    airaniCpuset = it
                    SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_AIRANI_CPUSET, if (it) 1L else 0L)
                    SuiKernelIoctl.syncStorage(currentConfig().copy(airaniCpuset = it))
                    showRebootToast()
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
                subtitle = "Temporary performance boost during boot",
                checked = sandevistan,
                onCheckedChange = { 
                    sandevistan = it
                    SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_SANDEVISTAN, if (it) 1L else 0L)
                    SuiKernelIoctl.syncStorage(currentConfig().copy(sandevistan = it))
                    showRebootToast()
                },
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Moona Hoshinova ZRAM Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            SettingToggleItem(
                title = "Moona Hoshinova ZRAM",
                subtitle = "Enforce page-cluster 0 & VMA readahead tweaks",
                checked = moonaZram,
                onCheckedChange = { 
                    moonaZram = it
                    SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_MOONA_ZRAM, if (it) 1L else 0L)
                    SuiKernelIoctl.syncStorage(currentConfig().copy(moonaZram = it))
                    showRebootToast()
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
                    subtitle = if (sparxieEnabled) "Adjust swappiness value (Current: ${sparxieSwap.toInt()})" else "Adjust swappiness value",
                    checked = sparxieEnabled,
                    onCheckedChange = { 
                        sparxieEnabled = it
                        showRebootToast()
                        if (!it) {
                            sparxieSwap = 30f
                            SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_SPARXIE_SWAP, 30L)
                        } else {
                            SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_SPARXIE_SWAP, sparxieSwap.toLong())
                        }
                        SuiKernelIoctl.syncStorage(currentConfig().copy(sparxieEnabled = it, sparxieSwap = if (!it) 30f else sparxieSwap))
                    }
                )
                
                AnimatedVisibility(visible = sparxieEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = sparxieSwap,
                            onValueChange = { 
                                sparxieSwap = it 
                                SuiKernelIoctl.sendIoctl(SuiKernelIoctl.CMD_SPARXIE_SWAP, it.toLong())
                                SuiKernelIoctl.syncStorage(currentConfig().copy(sparxieSwap = it))
                            },
                            valueRange = 10f..100f,
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
