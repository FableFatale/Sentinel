package app.permissionsentinel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.permissionsentinel.data.AppSettings
import app.permissionsentinel.ui.SentinelApp
import app.permissionsentinel.ui.theme.SentinelTheme

class MainActivity : ComponentActivity() {

    private val splashKeep = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().setKeepOnScreenCondition { splashKeep.value }
        enableEdgeToEdge()
        setContent {
            val vm: SentinelViewModel = viewModel { SentinelViewModel(application) }
            val settings by vm.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = when (settings.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(Unit) { splashKeep.value = false }
            SideEffectStatusBar(!dark)
            SentinelTheme(themeMode = settings.themeMode) {
                SentinelApp(vm)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SideEffectStatusBar(lightIcons: Boolean) {
        androidx.compose.runtime.SideEffect {
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = lightIcons
        }
    }
}
