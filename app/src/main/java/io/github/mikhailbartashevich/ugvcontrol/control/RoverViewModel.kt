package io.github.mikhailbartashevich.ugvcontrol.control

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.edit
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mikhailbartashevich.roverkit.control.RoverController
import io.github.mikhailbartashevich.roverkit.net.MjpegStream
import io.github.mikhailbartashevich.roverkit.net.RoverSocket
import io.github.mikhailbartashevich.roverkit.protocol.RoverAddress
import io.github.mikhailbartashevich.roverkit.protocol.SpeedPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
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
        videoQuality = prefs.getInt(KEY_QUALITY, 70)
        setSpeed(SpeedPreset.entries.getOrNull(prefs.getInt(KEY_SPEED, SpeedPreset.MIDDLE.ordinal)) ?: SpeedPreset.MIDDLE)
    }

    val gamepad = GamepadInput(rover)

    private val _videoFps = MutableStateFlow(0.0)
    /** Frames per second actually shown on the phone. */
    val videoFps: StateFlow<Double> = _videoFps.asStateFlow()

    /** Live video while the rover is reachable and the screen is showing it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val video: StateFlow<ImageBitmap?> = combine(rover.address, rover.reachable) { a, ok -> a.takeIf { ok == true } }
        .distinctUntilChanged()
        .flatMapLatest { a ->
            if (a == null) emptyFlow() else {
                var windowStart = System.currentTimeMillis()
                var count = 0
                MjpegStream().frames(a.videoUrl).conflate().mapNotNull { bytes ->
                    count++
                    val t = System.currentTimeMillis()
                    if (t - windowStart >= 1000) {
                        _videoFps.value = count * 1000.0 / (t - windowStart)
                        windowStart = t; count = 0
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.flowOn(Dispatchers.Default)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), null)

    init {
        network.start()
    }

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

    fun setVideoQuality(q: Int) {
        rover.videoQuality = q
        prefs.edit { putInt(KEY_QUALITY, q) }
        viewModelScope.launch { rover.connect() }
    }

    val socketState: StateFlow<RoverSocket.State> get() = rover.socket.state

    /** The app went to the background: never leave the rover driving. */
    fun onBackground() = rover.emergencyStop()

    override fun onCleared() {
        network.stop()
        rover.disconnect()
    }

    companion object {
        private const val KEY_ADDRESS = "address"
        private const val KEY_SPEED = "speed"
        private const val KEY_QUALITY = "videoQuality"
    }
}
