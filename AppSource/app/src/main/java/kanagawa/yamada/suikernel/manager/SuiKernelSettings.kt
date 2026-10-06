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
import kanagawa.yamada.suikernel.manager.ui.theme.*

@Composable
fun SuiKernelSettingsScreen(onNavigateBack: () -> Unit = {}) {
    var anyaThermal by remember { mutableStateOf(true) }
    var yamadaBoost by remember { mutableStateOf(true) }
    var performanceMode by remember { mutableStateOf("Balanced") }
    
    var inahoAudio by remember { mutableStateOf(true) }
    var tenebrion by remember { mutableStateOf(true) }
    var airaniCpuset by remember { mutableStateOf(true) }
    var sandevistan by remember { mutableStateOf(false) }
    var sparxieSwap by remember { mutableStateOf(60f) }
    var sparxieEnabled by remember { mutableStateOf(true) }

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
                onCheckedChange = { anyaThermal = it },
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
                    onCheckedChange = { yamadaBoost = it }
                )
                
                AnimatedVisibility(visible = yamadaBoost) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Balanced Button
                        Button(
                            onClick = { performanceMode = "Balanced" },
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
                            onClick = { performanceMode = "Gaming" },
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
                onCheckedChange = { inahoAudio = it },
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
                onCheckedChange = { tenebrion = it },
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
                onCheckedChange = { airaniCpuset = it },
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
                onCheckedChange = { sandevistan = it },
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
                        if (!it) sparxieSwap = 100f
                    }
                )
                
                AnimatedVisibility(visible = sparxieEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = sparxieSwap,
                            onValueChange = { sparxieSwap = it },
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
