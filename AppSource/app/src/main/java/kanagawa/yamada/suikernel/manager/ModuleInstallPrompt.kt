package kanagawa.yamada.suikernel.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kanagawa.yamada.suikernel.manager.ui.theme.GoogleSansFlex
import kanagawa.yamada.suikernel.manager.ui.theme.PrimaryCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ModuleInstallPrompt(
    title: String = "Module Flasher",
    output: String,
    isInstalling: Boolean,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101214))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
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
                    modifier = Modifier.padding(bottom = if (!isInstalling) 64.dp else 0.dp)
                )
            }
        }

        // Overlay Reboot Button (Just like KernelSU Next Manager)
        if (!isInstalling) {
            ExtendedFloatingActionButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        rebootDevice()
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
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
fun InstallConsoleScreen(
    title: String,
    output: String,
    isInstalling: Boolean,
    onClose: () -> Unit
) = ModuleInstallPrompt(
    title = title,
    output = output,
    isInstalling = isInstalling,
    onClose = onClose
)
