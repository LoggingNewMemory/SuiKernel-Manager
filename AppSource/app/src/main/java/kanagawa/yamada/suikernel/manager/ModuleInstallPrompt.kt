package kanagawa.yamada.suikernel.manager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flashing pulse indicator while installing
                if (isInstalling) {
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

                // Animated X close button
                AnimatedVisibility(
                    visible = !isInstalling,
                    enter = fadeIn(tween(300)) + scaleIn(
                        initialScale = 0.6f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ),
                    exit = fadeOut(tween(150)) + scaleOut(tween(150))
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

            // Console output box
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

        // Overlay Reboot Button (Just like KernelSU Next Manager) with pop-up animation
        AnimatedVisibility(
            visible = !isInstalling,
            enter = fadeIn(tween(350, delayMillis = 100)) +
                    slideInVertically(
                        animationSpec = tween(350, delayMillis = 100, easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 2 }
                    ) +
                    scaleIn(
                        initialScale = 0.8f,
                        animationSpec = tween(350, delayMillis = 100, easing = FastOutSlowInEasing)
                    ),
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 } + scaleOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
        ) {
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
                contentColor = Color.White
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
