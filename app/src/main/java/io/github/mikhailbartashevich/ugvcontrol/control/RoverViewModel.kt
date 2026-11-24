package io.github.mikhailbartashevich.ugvcontrol.control

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mikhailbartashevich.roverkit.control.RoverController
import io.github.mikhailbartashevich.roverkit.net.RoverSocket
import io.github.mikhailbartashevich.roverkit.protocol.RoverAddress
import io.github.mikhailbartashevich.roverkit.protocol.SpeedPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Android glue around [RoverController]: saved settings, the phone's networks
 * (via [NetworkMonitor]), and the
 * decoded MJPEG video.
 */
class RoverViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("rover", Context.MODE_PRIVATE)
    private val network = NetworkMonitor(app.getSystemService(ConnectivityManager::class.java))

    val rover = RoverController(
        scope = viewModelScope,
        initialAddress = RoverAddress.parse(prefs.getString(KEY_ADDRESS, null) ?: "")
            ?: RoverAddress(RoverAddress.HOTSPOT_HOST),
        saveAddress = { prefs.edit { putString(KEY_ADDRESS, it.toString()) } },
    ).apply {
        setSpeed(SpeedPreset.entries.getOrNull(prefs.getInt(KEY_SPEED, SpeedPreset.MIDDLE.ordinal)) ?: SpeedPreset.MIDDLE)
    }

    val gamepad = GamepadInput(rover)

    /** Live H.264 video over RTSP. */
    val video = RoverVideoPlayer(app)

    private val foreground = MutableStateFlow(true)

    init {
        network.start()
        // Stream while the rover answers and the app is on screen.
        viewModelScope.launch {
            combine(rover.address, rover.reachable, foreground) { a, ok, fg -> a.rtspUrl.takeIf { ok == true && fg } }
                .distinctUntilChanged()
                .collect(video::play)
        }
    }

    /** The activity is visible / hidden: video only streams while visible. */
    fun setForeground(visible: Boolean) { foreground.value = visible }

    private val _localNetworkAllowed = MutableStateFlow(true)
    /** Android 17+ "Local network access" (Nearby devices) permission; without it the rover can't be reached. */
    val localNetworkAllowed: StateFlow<Boolean> = _localNetworkAllowed.asStateFlow()
    fun setLocalNetworkAllowed(allowed: Boolean) { _localNetworkAllowed.value = allowed }

    private var started = false

    /** First connection, once per ViewModel: to [address] if given (launch intent), else saved address or discovery. */
    fun start(address: RoverAddress?) {
        if (started) return
        started = true
        if (address != null) connect(address) else connectOrDiscover()
    }

    fun connectOrDiscover() = viewModelScope.launch { rover.connectOrDiscover(network.localPrefixes()) }

    fun connect(address: RoverAddress) = viewModelScope.launch {
        rover.setAddress(address)
        rover.connect()
    }

    fun setSpeed(p: SpeedPreset) {
        rover.setSpeed(p)
        prefs.edit { putInt(KEY_SPEED, p.ordinal) }
    }

    val socketState: StateFlow<RoverSocket.State> get() = rover.socket.state

    /** The app went to the background: never leave the rover driving. */
    fun onBackground() = rover.emergencyStop()

    override fun onCleared() {
        network.stop()
        video.release()
        rover.disconnect()
    }

    companion object {
        private const val KEY_ADDRESS = "address"
        private const val KEY_SPEED = "speed"
    }
}
