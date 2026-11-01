package dev.joeherbert.availabliss

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.deeplink.DeepLinkBus
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.isDark
import dev.joeherbert.availabliss.navigation.AppRoot
import dev.joeherbert.availabliss.navigation.RootViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var deepLinks: DeepLinkBus

    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        requestLocalNetworkForDevServer()

        setContent {
            val root: RootViewModel = hiltViewModel()
            val appearance by root.appearance.collectAsStateWithLifecycle()
            AvailablissTheme(mode = appearance.mode, colorTheme = appearance.theme) {
                val dark = appearance.mode.isDark()
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                AppRoot(root)
            }
        }
    }

    /**
     * Debug builds talk to a dev server on the local network (or the emulator host), which
     * newer Android versions block without ACCESS_LOCAL_NETWORK. Only the debug manifest
     * declares it, so release builds skip this.
     */
    private fun requestLocalNetworkForDevServer() {
        if (!BuildConfig.DEBUG) return
        val declared = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().contains(LOCAL_NETWORK)
        if (declared && checkSelfPermission(LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            localNetworkPermission.launch(LOCAL_NETWORK)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** VIEW intents (links, our own notifications) and FCM notification taps (data in extras). */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val link = intent.data?.let(DeepLink::parse)
            ?: intent.extras?.takeIf { it.containsKey("kind") }?.let { extras ->
                DeepLink.fromPushData(extras.keySet().associateWith { extras.getString(it).orEmpty() })
            }
        link?.let(deepLinks::post)
    }

    private companion object {
        const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
