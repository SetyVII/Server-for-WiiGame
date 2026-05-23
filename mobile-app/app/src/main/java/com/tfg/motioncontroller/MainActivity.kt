package com.tfg.motioncontroller

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tfg.motioncontroller.presentation.controller.ControllerScreen
import com.tfg.motioncontroller.presentation.controller.ControllerViewModel
import com.tfg.motioncontroller.presentation.settings.SettingsScreen
import com.tfg.motioncontroller.presentation.connection.ConnectionScreen
import com.tfg.motioncontroller.presentation.settings.SettingsViewModel
import com.tfg.motioncontroller.ui.theme.TFGMotionControllerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsState(initial = com.tfg.motioncontroller.domain.model.GameSettings())
            val darkTheme = remember(settings) { settings.darkMode }

            TFGMotionControllerTheme(darkTheme = darkTheme) {
                val colorScheme = MaterialTheme.colorScheme

                SideEffect {
                    val window = window
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "connection"
                    ) {
                        composable("connection") {
                            LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                            ConnectionScreen(
                                onConnected = { navController.navigate("controller") }
                            )
                        }
                        composable("controller") {
                            LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                            ControllerScreenWithLifecycle(
                                onSettingsClick = { navController.navigate("settings") },
                                onDisconnect = {
                                    navController.navigate("connection") {
                                        popUpTo("connection") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("settings") {
                            LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                            SettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LockScreenOrientation(orientation: Int) {
    val context = LocalContext.current
    DisposableEffect(orientation) {
        val activity = context as? ComponentActivity
        activity?.requestedOrientation = orientation
        onDispose {
            // Restaurar al salir
        }
    }
}

@Composable
fun ControllerScreenWithLifecycle(
    onSettingsClick: () -> Unit,
    onDisconnect: () -> Unit
) {
    val viewModel: ControllerViewModel = hiltViewModel()
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // App va a background
                    viewModel.onAppBackground()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // App vuelve a foreground
                    viewModel.onAppForeground()
                }
                else -> {}
            }
        }

        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
        }
    }

    ControllerScreen(
        onSettingsClick = onSettingsClick,
        onDisconnect = onDisconnect
    )
}
