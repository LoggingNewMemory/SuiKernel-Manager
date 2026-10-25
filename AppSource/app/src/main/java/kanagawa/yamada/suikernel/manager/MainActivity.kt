/*
 * SuiKernel Manager
 * Copyright (C) 2026 Kanagawa Yamada
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
        
        // Warm up Yamada SU root session & config cache in background
        Thread {
            SuiKernelIoctl.isNativeRootSupported()
            SuiKernelIoctl.readStorage()
        }.start()

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
                            if ((targetState == "Settings" || targetState == "RecomendedModules" || targetState == "Terminal") && initialState == "Dashboard") {
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
                            "Dashboard" -> DashboardScreen(
                                onNavigateToSettings = { currentScreen = "Settings" },
                                onNavigateToRecomendedModules = { currentScreen = "RecomendedModules" },
                                onNavigateToTerminal = { currentScreen = "Terminal" }
                            )
                            "Settings" -> SuiKernelSettingsScreen(onNavigateBack = { currentScreen = "Dashboard" })
                            "RecomendedModules" -> RecomendedModulesScreen(onNavigateBack = { currentScreen = "Dashboard" })
                            "Terminal" -> SuiTerminalScreen(onNavigateBack = { currentScreen = "Dashboard" })
                        }
                    }
                }
            }
        }
    }
}
