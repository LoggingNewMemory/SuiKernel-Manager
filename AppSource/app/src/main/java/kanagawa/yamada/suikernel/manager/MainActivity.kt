package kanagawa.yamada.suikernel.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                    
                    when (currentScreen) {
                        "Dashboard" -> DashboardScreen(onNavigateToSettings = { currentScreen = "Settings" })
                        "Settings" -> SuiKernelSettingsScreen(onNavigateBack = { currentScreen = "Dashboard" })
                    }
                }
            }
        }
    }
}
