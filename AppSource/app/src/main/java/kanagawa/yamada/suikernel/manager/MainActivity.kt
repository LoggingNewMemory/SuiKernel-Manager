package kanagawa.yamada.suikernel.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import kanagawa.yamada.suikernel.manager.ui.theme.SuiKernelManagerTheme

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
                    var currentScreen by remember { mutableStateOf("Dashboard") }
                    
                    AnimatedContent(
                        targetState = currentScreen,
                        label = "ScreenTransition",
                        transitionSpec = {
                            if (targetState == "Settings" && initialState == "Dashboard") {
                                (slideInHorizontally(
                                    animationSpec = tween(300),
                                    initialOffsetX = { fullWidth -> fullWidth }
                                ) + fadeIn(animationSpec = tween(300))).togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { fullWidth -> -fullWidth / 2 }
                                    ) + fadeOut(animationSpec = tween(300))
                                )
                            } else {
                                (slideInHorizontally(
                                    animationSpec = tween(300),
                                    initialOffsetX = { fullWidth -> -fullWidth / 2 }
                                ) + fadeIn(animationSpec = tween(300))).togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(300),
                                        targetOffsetX = { fullWidth -> fullWidth }
                                    ) + fadeOut(animationSpec = tween(300))
                                )
                            }
                        }
                    ) { targetScreen ->
                        when (targetScreen) {
                            "Dashboard" -> DashboardScreen(onNavigateToSettings = { currentScreen = "Settings" })
                            "Settings" -> SuiKernelSettingsScreen(onNavigateBack = { currentScreen = "Dashboard" })
                        }
                    }
                }
            }
        }
    }
}
