package io.github.mikhailbartashevich.ugvcontrol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.mikhailbartashevich.roverkit.protocol.RoverAddress
import io.github.mikhailbartashevich.ugvcontrol.control.RoverViewModel
import io.github.mikhailbartashevich.ugvcontrol.ui.screen.ControlScreen
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.UgvTheme

class MainActivity : ComponentActivity() {
    private val vm: RoverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        val host = takeHostExtra()
        val askLocalNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.setLocalNetworkAllowed(granted)
            vm.start(host)
        }
        if (hasLocalNetworkAccess()) {
            vm.setLocalNetworkAllowed(true)
            vm.start(host)
        } else {
            askLocalNetwork.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
        setContent {
            UgvTheme {
                ControlScreen(vm, onRequestLocalNetwork = { askLocalNetwork.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) })
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeHostExtra()?.let { vm.connect(it) }
    }

    /** `adb shell am start -n …/.MainActivity --es host 10.0.2.2:5055` sets the address and connects. */
    private fun takeHostExtra(): RoverAddress? {
        val host = intent?.getStringExtra("host") ?: return null
        intent?.removeExtra("host")
        return RoverAddress.parse(host)
    }

    private fun hasLocalNetworkAccess() =
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        // The user may have granted it in Settings meanwhile.
        if (hasLocalNetworkAccess() && !vm.localNetworkAllowed.value) {
            vm.setLocalNetworkAllowed(true)
            vm.connectOrDiscover()
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        vm.onBackground()
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean =
        vm.gamepad.onMotion(ev) || super.dispatchGenericMotionEvent(ev)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        vm.gamepad.onKey(event) || super.dispatchKeyEvent(event)
}
