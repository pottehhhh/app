package ir.weirdnet.client

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ir.weirdnet.client.core.DeepLinkRepository
import ir.weirdnet.client.data.repository.AppTheme
import ir.weirdnet.client.ui.nav.WeirdNetNavHost
import ir.weirdnet.client.ui.theme.ThemePreference
import ir.weirdnet.client.ui.theme.WeirdNetTheme
import ir.weirdnet.client.util.WeirdLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // A short-lived scope for the one-shot "read this deep-linked file's content"
    // work in handleIncomingIntent(); intentionally not tied to a ViewModel since
    // it does nothing beyond that single read before handing off to DeepLinkRepository.
    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val app = WeirdNetApplication.from(this)
            val settings by app.settingsRepository.settings.collectAsState(initial = null)

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* No-op: the connection notification simply won't show if denied. */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val themePreference = when (settings?.theme) {
                AppTheme.LIGHT -> ThemePreference.LIGHT
                AppTheme.DARK -> ThemePreference.DARK
                else -> ThemePreference.SYSTEM
            }

            WeirdNetTheme(themePreference = themePreference) {
                WeirdNetNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Extracts an importable configuration from a deep-linked [Intent] and hands
     * it to [DeepLinkRepository], which [ir.weirdnet.client.ui.addprofile.AddProfileScreen]
     * consumes the next time it's composed. Two distinct cases (matching the two
     * `<intent-filter>` blocks declared for this activity in AndroidManifest.xml):
     *
     * 1. A `vless://`/`vmess://`/`trojan://`/`ss://` link tapped
     *    elsewhere on the device -- the URI string itself IS the config, so no
     *    file I/O is needed. (There is no equivalent for WireGuard: no
     *    standardized `wireguard://` URI format exists, so it isn't declared as
     *    a scheme in the manifest or handled here -- see the manifest's comment.)
     * 2. A `.conf` file opened from a file manager or email attachment -- its
     *    `content://`/`file://` URI has to actually be read via ContentResolver
     *    to get the WireGuard config text ConfigParser expects.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return

        val scheme = uri.scheme?.lowercase()
        if (scheme in setOf("vless", "vmess", "trojan", "ss")) {
            WeirdLogger.i("MainActivity", "Received deep-linked $scheme:// import")
            DeepLinkRepository.offer(uri.toString())
            return
        }

        // Anything else that reached this activity's ACTION_VIEW filter is the
        // file-open case (content:// or file:// pointing at a .conf); read it off
        // the main thread since it may involve real I/O (e.g. a cloud-backed
        // document provider), then hand the *content*, not the URI, to the parser.
        activityScope.launch {
            val text = readUriContent(uri)
            if (text != null) {
                WeirdLogger.i("MainActivity", "Received deep-linked file import")
                DeepLinkRepository.offer(text)
            } else {
                WeirdLogger.w("MainActivity", "Could not read content from deep-linked URI")
            }
        }
    }

    private suspend fun readUriContent(uri: Uri): String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            WeirdLogger.e("MainActivity", "Failed reading deep-linked URI content", e)
            null
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}

