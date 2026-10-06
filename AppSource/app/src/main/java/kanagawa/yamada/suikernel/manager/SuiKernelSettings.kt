package kanagawa.yamada.suikernel.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import kanagawa.yamada.suikernel.manager.ui.theme.*

@Composable
fun SuiKernelSettingsScreen(onNavigateBack: () -> Unit = {}) {
    var anyaThermal by remember { mutableStateOf(true) }
    var yamadaBoost by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
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
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Anya Melfissa Thermal
        SettingToggleItem(
            title = "Anya Melfissa Thermal",
            subtitle = "Enable / Disable Anya Thermal Kernel Side",
            checked = anyaThermal,
            onCheckedChange = { anyaThermal = it }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Yamada Gaming Boost
        SettingToggleItem(
            title = "Yamada Gaming Boost",
            subtitle = "Enable / Disable Yamada Gaming Boost",
            checked = yamadaBoost,
            onCheckedChange = { yamadaBoost = it }
        )
    }
}

@Composable
fun SettingToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontFamily = GoogleSansFlex,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
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
