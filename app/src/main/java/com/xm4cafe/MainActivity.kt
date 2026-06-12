// app/src/main/java/com/coffeehouse/MainActivity.kt
package com.coffeehouse

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.coffeehouse.ui.MainScreen

/**
 * Single Activity host.
 *
 * Constraint A: edge-to-edge — WindowCompat.setDecorFitsSystemWindows(false)
 * before setContent. MainScreen applies safeDrawingPadding internally.
 *
 * Constraints C + D: runtime permissions (POST_NOTIFICATIONS,
 * BLUETOOTH_CONNECT) are requested on first composition via a LaunchedEffect.
 * Results are intentionally ignored — the app degrades gracefully (no
 * notification / "No headphones" status) rather than gating functionality.
 *
 * Constraint B: no back-handling. Single screen, no nav stack.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme(colorScheme = CoffeehouseDarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionWrapper {
                        MainScreen()
                    }
                }
            }
        }
    }
}

private val CoffeehouseDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF14002E),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFF1E8FF),
    secondary = Color(0xFFC4B5FD),
    onSecondary = Color(0xFF1F123A),
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = Color(0xFFF0ABFC),
    onTertiary = Color(0xFF2E1037),
    background = Color.Black,
    onBackground = Color(0xFFF8F7FF),
    surface = Color.Black,
    onSurface = Color(0xFFF8F7FF),
    surfaceVariant = Color(0xFF17121F),
    onSurfaceVariant = Color(0xFFC9C2D8),
    outline = Color(0xFF5E536F),
    outlineVariant = Color(0xFF2A2335),
)

@Composable
private fun PermissionWrapper(content: @Composable () -> Unit) {
    val permissions = arrayOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.BLUETOOTH_CONNECT,
    )
    var permissionsRequested by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Results ignored — the app works regardless of grant state.
    }

    LaunchedEffect(Unit) {
        if (!permissionsRequested) {
            permissionsRequested = true
            launcher.launch(permissions)
        }
    }

    // Battery-optimization exemption — required for reboot auto-start on
    // Android 15+. WorkManager's post-boot startForegroundService is only
    // permitted when the app is allowlisted ("Unrestricted"). Runs once,
    // after the notification/bluetooth request above, guarded by a saveable
    // flag so it does not nag on every recomposition.
    val context = LocalContext.current
    var batteryPromptShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!batteryPromptShown) {
            batteryPromptShown = true
            val pm = context.getSystemService(PowerManager::class.java)
            val pkg = context.packageName
            if (pm != null && !pm.isIgnoringBatteryOptimizations(pkg)) {
                try {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$pkg")
                    )
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Some devices/ROMs lack this exact action — fail silently;
                    // the app still works, only boot auto-start is affected.
                }
            }
        }
    }
    content()
}
