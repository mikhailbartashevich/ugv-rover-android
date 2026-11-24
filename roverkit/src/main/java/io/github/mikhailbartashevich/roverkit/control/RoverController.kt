package io.github.mikhailbartashevich.roverkit.control

import io.github.mikhailbartashevich.roverkit.net.RoverHttp
import io.github.mikhailbartashevich.roverkit.net.RoverScanner
import io.github.mikhailbartashevich.roverkit.net.RoverSocket
import io.github.mikhailbartashevich.roverkit.protocol.CameraZoom
import io.github.mikhailbartashevich.roverkit.protocol.Esp32Command
import io.github.mikhailbartashevich.roverkit.protocol.HeadLight
import io.github.mikhailbartashevich.roverkit.protocol.RoverAddress
import io.github.mikhailbartashevich.roverkit.protocol.CtrlCommand
import io.github.mikhailbartashevich.roverkit.protocol.RoverLimits
import io.github.mikhailbartashevich.roverkit.protocol.SpeedPreset
import io.github.mikhailbartashevich.roverkit.protocol.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Everything the app does to the rover, independent of Android UI. Driving and modes
 * go over Socket.IO like the stock web UI; HTTP `POST /send_command` is the fallback.
 * Mirrors the parts of `RoverClient.swift` the phone app needs.
 *
 * @param saveAddress called whenever the address changes (the app persists it).
 */
class RoverController(
    private val scope: CoroutineScope,
    initialAddress: RoverAddress,
    private val saveAddress: (RoverAddress) -> Unit = {},
    val socket: RoverSocket = RoverSocket(),
    private val http: RoverHttp = RoverHttp(),
    private val scanner: RoverScanner = RoverScanner(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    // MARK: Connection

    private val _address = MutableStateFlow(initialAddress)
    val address: StateFlow<RoverAddress> = _address.asStateFlow()

    private val _reachable = MutableStateFlow<Boolean?>(null)
    val reachable: StateFlow<Boolean?> = _reachable.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _searching = MutableStateFlow(false)
    /** True while looking for the rover on the network. */
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    val telemetry: StateFlow<Telemetry> get() = socket.telemetry

    // MARK: Driving & camera

    private val _speed = MutableStateFlow(SpeedPreset.MIDDLE)
    val speed: StateFlow<SpeedPreset> = _speed.asStateFlow()

    private val _driving = MutableStateFlow(false)
    /** The drive stick (or gamepad) is deflected and commands are streaming. */
    val driving: StateFlow<Boolean> = _driving.asStateFlow()

    private val _gimbal = MutableStateFlow(0.0 to 0.0)
    /** Last commanded (pan, tilt) in degrees. */
    val gimbal: StateFlow<Pair<Double, Double>> = _gimbal.asStateFlow()

    @Volatile private var driveStick = 0.0 to 0.0
    @Volatile private var cameraStick = 0.0 to 0.0
    private var driveJob: Job? = null
    private var cameraJob: Job? = null
    private val httpInFlight = AtomicBoolean(false)

    // MARK: Media & lights

    private val _zoom = MutableStateFlow(CameraZoom.X1)
    val zoom: StateFlow<CameraZoom> = _zoom.asStateFlow()

    private val _recordingSinceMs = MutableStateFlow<Long?>(null)
    val recordingSinceMs: StateFlow<Long?> = _recordingSinceMs.asStateFlow()

    private val _headLight = MutableStateFlow(HeadLight.OFF)
    val headLight: StateFlow<HeadLight> = _headLight.asStateFlow()

    private val _chassisLight = MutableStateFlow(false)
    val chassisLight: StateFlow<Boolean> = _chassisLight.asStateFlow()

    /** Local changes win over telemetry for this long, then the rover's report wins. */
    private val pendingUntil = mutableMapOf<String, Long>()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Short user-facing notices ("Photo saved on rover"). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        scope.launch {
            socket.telemetry.collect { t ->
                val n = now()
                t.headLight?.let { if (n > (pendingUntil["head"] ?: 0)) _headLight.value = it }
                t.chassisLightOn?.let { if (n > (pendingUntil["chassis"] ?: 0)) _chassisLight.value = it }
            }
        }
    }

    // MARK: - Connection

    fun setAddress(a: RoverAddress) {
        if (a == _address.value) return
        _address.value = a
        saveAddress(a)
    }

    /** Checks the web app and (re)opens the Socket.IO channel. Returns whether it's reachable. */
    suspend fun connect(): Boolean {
        val a = _address.value
        when (val r = http.ping(a)) {
            RoverHttp.Result.Ok -> {
                _reachable.value = true
                _lastError.value = null
                socket.connect(a)
                return true
            }
            is RoverHttp.Result.Failed -> {
                _reachable.value = false
                _lastError.value = r.message
                socket.disconnect()
                return false
            }
        }
    }

    fun disconnect() {
        stopAll()
        socket.disconnect()
        _reachable.value = null
    }

    /**
     * Connects to the saved address; if the rover isn't there, looks for it: its own
     * hotspot (192.168.50.5), then every host on the phone's /24 networks. A rover
     * that's found becomes the saved address.
     */
    suspend fun connectOrDiscover(localPrefixes: List<String>) {
        if (connect() || _searching.value) return
        _searching.value = true
        try {
            val current = _address.value
            val port = current.port
            if (current.host != RoverAddress.HOTSPOT_HOST && scanner.isRover(RoverAddress.HOTSPOT_HOST, port)) {
                adopt(RoverAddress(RoverAddress.HOTSPOT_HOST, port)); return
            }
            for (prefix in localPrefixes.distinct()) {
                val found = scanner.scan(prefix, exclude = setOf(current.host)).firstOrNull()
                if (found != null) { adopt(RoverAddress(found, port)); return }
            }
            _messages.tryEmit("No rover found — check Wi‑Fi or enter its address")
        } finally {
            _searching.value = false
        }
    }

    private suspend fun adopt(a: RoverAddress) {
        setAddress(a)
        if (connect()) _messages.tryEmit("Found the rover at ${a.host}")
    }

    // MARK: - Sending

    /** Sends an ESP32 command over Socket.IO, or over HTTP if the socket is down. */
    fun send(command: Esp32Command) {
        if (socket.emit(command)) return
        if (!httpInFlight.compareAndSet(false, true)) return
        val a = _address.value
        scope.launch {
            try { http.send(a, command) } finally { httpInFlight.set(false) }
        }
    }

    private fun ctrl(command: CtrlCommand): Boolean {
        if (socket.emitCtrl(command)) return true
        _messages.tryEmit("Control channel offline")
        return false
    }

    // MARK: - Driving

    fun setSpeed(p: SpeedPreset) { _speed.value = p }

    /**
     * Drive stick position (x right +, y forward +, both −1…1). While deflected,
     * T:13 is re-sent every [DRIVE_PERIOD_MS] because the ESP32 stops on its own
     * when commands stop. Centring the stick sends a stop.
     */
    fun drive(x: Double, y: Double) {
        val (cx, cy) = DriveMath.clampToCircle(x, y)
        val idle = DriveMath.shape(cx) == 0.0 && DriveMath.shape(cy) == 0.0
        driveStick = cx to cy
        if (idle) {
            if (driveJob != null) {
                driveJob?.cancel(); driveJob = null
                _driving.value = false
                send(Esp32Command.STOP)
            }
            return
        }
        if (driveJob == null) {
            _driving.value = true
            driveJob = scope.launch {
                while (isActive) {
                    val (sx, sy) = driveStick
                    send(DriveMath.velocity(sx, sy, _speed.value.rate))
                    delay(DRIVE_PERIOD_MS)
                }
            }
        }
    }

    /** Camera stick: a rate control — holding it moves the pan-tilt, releasing it holds the angle. */
    fun look(x: Double, y: Double) {
        cameraStick = x to y
        val idle = DriveMath.shape(x) == 0.0 && DriveMath.shape(y) == 0.0
        if (idle) { cameraJob?.cancel(); cameraJob = null; return }
        if (cameraJob != null) return
        cameraJob = scope.launch {
            var last = now()
            while (isActive) {
                delay(CAMERA_PERIOD_MS)
                val t = now()
                val (sx, sy) = cameraStick
                val (p, tl) = _gimbal.value
                val next = DriveMath.gimbalStep(p, tl, sx, sy, (t - last) / 1000.0)
                last = t
                if (next != _gimbal.value) setGimbal(next.first, next.second)
            }
        }
    }

    fun setGimbal(pan: Double, tilt: Double) {
        val p = pan.coerceIn(RoverLimits.PAN_RANGE)
        val t = tilt.coerceIn(RoverLimits.TILT_RANGE)
        _gimbal.value = p to t
        send(Esp32Command.gimbal(p, t))
    }

    /** Camera straight ahead ("Ahead" in the web UI). */
    fun lookAhead() = setGimbal(0.0, 0.0)

    /** Stops the wheels immediately, over the socket and over HTTP as a backup. */
    fun emergencyStop() {
        stopAll()
        socket.emit(Esp32Command.STOP)
        val a = _address.value
        scope.launch { http.send(a, Esp32Command.STOP) }
    }

    private fun stopAll() {
        driveJob?.cancel(); driveJob = null
        cameraJob?.cancel(); cameraJob = null
        driveStick = 0.0 to 0.0
        cameraStick = 0.0 to 0.0
        _driving.value = false
    }

    // MARK: - Media

    fun setZoom(z: CameraZoom) { if (ctrl(CtrlCommand.zoom(z))) _zoom.value = z }
    fun cycleZoom() = setZoom(_zoom.value.next)

    fun capturePhoto() { if (ctrl(CtrlCommand.CAPTURE_PHOTO)) _messages.tryEmit("Photo saved on rover") }

    fun toggleRecording() {
        if (_recordingSinceMs.value != null) {
            if (!ctrl(CtrlCommand.recording(false))) return
            _recordingSinceMs.value = null
            _messages.tryEmit("Recording saved on rover")
        } else {
            if (!ctrl(CtrlCommand.recording(true))) return
            _recordingSinceMs.value = now()
        }
    }

    // MARK: - Lights

    fun setHeadLight(mode: HeadLight) {
        if (!ctrl(CtrlCommand.headLight(mode))) return
        _headLight.value = mode
        pendingUntil["head"] = now() + PENDING_MS
    }

    fun cycleHeadLight() = setHeadLight(_headLight.value.next)

    fun setChassisLight(on: Boolean) {
        if (!ctrl(CtrlCommand.chassisLight(if (on) 255 else 0))) return
        _chassisLight.value = on
        pendingUntil["chassis"] = now() + PENDING_MS
    }

    fun toggleChassisLight() = setChassisLight(!_chassisLight.value)

    companion object {
        const val DRIVE_PERIOD_MS = 150L
        const val CAMERA_PERIOD_MS = 100L
        const val PENDING_MS = 1500L
    }
}
