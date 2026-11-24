package io.github.mikhailbartashevich.ugvcontrol.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.mikhailbartashevich.roverkit.net.RoverSocket
import io.github.mikhailbartashevich.roverkit.protocol.HeadLight
import io.github.mikhailbartashevich.ugvcontrol.R
import io.github.mikhailbartashevich.ugvcontrol.control.RoverViewModel
import io.github.mikhailbartashevich.ugvcontrol.ui.components.ConnectionStatus
import io.github.mikhailbartashevich.ugvcontrol.ui.components.FloatingStick
import io.github.mikhailbartashevich.ugvcontrol.ui.components.GlassGroup
import io.github.mikhailbartashevich.ugvcontrol.ui.components.GlassSeparator
import io.github.mikhailbartashevich.ugvcontrol.ui.components.GroupButton
import io.github.mikhailbartashevich.ugvcontrol.ui.components.OfflineCard
import io.github.mikhailbartashevich.ugvcontrol.ui.components.RecordingBadge
import io.github.mikhailbartashevich.ugvcontrol.ui.components.Reticle
import io.github.mikhailbartashevich.ugvcontrol.ui.components.RoundAction
import io.github.mikhailbartashevich.ugvcontrol.ui.components.SpeedSelector
import io.github.mikhailbartashevich.ugvcontrol.ui.components.StopButton
import io.github.mikhailbartashevich.ugvcontrol.ui.components.TelemetryReadouts
import io.github.mikhailbartashevich.ugvcontrol.ui.components.rememberHaptics
import io.github.mikhailbartashevich.ugvcontrol.ui.dialogs.ConnectionDialog
import io.github.mikhailbartashevich.ugvcontrol.ui.dialogs.HelpDialog
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.LocalGlassBackdrop
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.RoverColors
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.glass
import kotlinx.coroutines.delay

/**
 * The one screen: full-screen video with liquid-glass controls floating over it.
 * Left half = drive stick, right half = camera stick, STOP between the thumbs,
 * status bar on top, lights top-left, camera and media top-right.
 */
@OptIn(UnstableApi::class)
@Composable
fun ControlScreen(vm: RoverViewModel, onRequestLocalNetwork: () -> Unit) {
    val rover = vm.rover
    val fps by vm.video.fps.collectAsStateWithLifecycle()
    val t by rover.telemetry.collectAsStateWithLifecycle()
    val state by vm.socketState.collectAsStateWithLifecycle()
    val reachable by rover.reachable.collectAsStateWithLifecycle()
    val searching by rover.searching.collectAsStateWithLifecycle()
    val address by rover.address.collectAsStateWithLifecycle()
    val speed by rover.speed.collectAsStateWithLifecycle()
    val driving by rover.driving.collectAsStateWithLifecycle()
    val zoom by rover.zoom.collectAsStateWithLifecycle()
    val head by rover.headLight.collectAsStateWithLifecycle()
    val chassis by rover.chassisLight.collectAsStateWithLifecycle()
    val recSince by rover.recordingSinceMs.collectAsStateWithLifecycle()
    val gimbal by rover.gimbal.collectAsStateWithLifecycle()
    val localNetworkAllowed by vm.localNetworkAllowed.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val backdrop = rememberHazeState()

    var showConnection by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var fill by rememberSaveable { mutableStateOf(true) }
    var toast by remember { mutableStateOf<String?>(null) }
    val connected = state == RoverSocket.State.CONNECTED

    // Keep the screen awake while connected to the rover.
    val view = LocalView.current
    LaunchedEffect(connected) { view.keepScreenOn = connected }
    LaunchedEffect(Unit) { rover.messages.collect { toast = it } }
    LaunchedEffect(toast) { if (toast != null) { delay(2500); toast = null } }

    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Backdrop: video (or a soft gradient while there's none) that every glass panel blurs.
            Box(Modifier.fillMaxSize().hazeSource(backdrop)) {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(
                    0f to Color(0xFF1C2A33), 0.6f to Color(0xFF0C1216), 1f to Color.Black,
                    center = Offset(900f, 300f), radius = 1600f,
                )))
                // TextureView (not SurfaceView) so the glass panels can blur the video.
                ContentFrame(
                    player = vm.video.player,
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = "Live camera view from the rover" },
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                    keepContentOnReset = true,
                    shutter = {},
                )
            }
            Reticle(Modifier.align(Alignment.Center))

            // Thumb zones
            Row(Modifier.fillMaxSize().padding(top = 128.dp)) {
                FloatingStick(
                    label = "DRIVE",
                    hint = "Touch anywhere here & drag",
                    description = "Drive stick. Touch anywhere on the left half and drag: up drives forward, " +
                        "down reverses, sideways turns. Release to stop.",
                    color = RoverColors.Accent,
                    onMove = { x, y -> rover.drive(x, y) },
                    onRelease = { rover.drive(0.0, 0.0) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    restAnchor = Offset(0.42f, 0.6f),
                )
                Spacer(Modifier.width(220.dp))
                FloatingStick(
                    label = "CAMERA",
                    hint = "Drag to aim · double-tap: look ahead",
                    description = "Camera stick. Touch anywhere on the right half and drag to pan and tilt the camera; " +
                        "it stays where you leave it. Double-tap to look straight ahead.",
                    color = RoverColors.Camera,
                    onMove = { x, y -> rover.look(x, y) },
                    onRelease = { rover.look(0.0, 0.0) },
                    onDoubleTap = { haptics.confirm(); rover.lookAhead() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    restAnchor = Offset(0.58f, 0.6f),
                )
            }

            // Status bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.height(40.dp).glass(CircleShape).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConnectionStatus(state, reachable, searching, address.toString()) { haptics.click(); showConnection = true }
                    GlassSeparator()
                    Spacer(Modifier.width(10.dp))
                    TelemetryReadouts(t, fps)
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                recSince?.let { since ->
                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(since) { while (true) { now = System.currentTimeMillis(); delay(500) } }
                    RecordingBadge(((now - since) / 1000).coerceAtLeast(0))
                    Spacer(Modifier.width(8.dp))
                }
                RoundAction(R.drawable.ic_help, "Help", "How to drive: all controls explained") {
                    haptics.click(); showHelp = true
                }
            }

            // Lights (top-left)
            GlassGroup(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 62.dp)) {
                GroupButton(
                    icon = when (head) {
                        HeadLight.OFF -> R.drawable.ic_lightbulb_off
                        HeadLight.AUTO -> R.drawable.ic_lightbulb_auto
                        HeadLight.ON -> R.drawable.ic_lightbulb_on
                    },
                    label = "Head ${head.title}",
                    active = head != HeadLight.OFF,
                    activeColor = RoverColors.Camera,
                    hint = "Head light: tap to cycle Off → Auto → On (now ${head.title}). Auto turns on in the dark.",
                ) { haptics.toggle(head.next != HeadLight.OFF); rover.cycleHeadLight() }
                GroupButton(
                    icon = if (chassis) R.drawable.ic_base_light_on else R.drawable.ic_base_light_off,
                    label = if (chassis) "Base On" else "Base Off",
                    active = chassis,
                    activeColor = RoverColors.Camera,
                    hint = "Chassis (base) light: tap to switch ${if (chassis) "off" else "on"}",
                ) { haptics.toggle(!chassis); rover.toggleChassisLight() }
            }

            // Camera and media (top-right)
            GlassGroup(Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 62.dp)) {
                GroupButton(
                    R.drawable.ic_center_focus, "Ahead", gimbal != (0.0 to 0.0),
                    "Point the camera straight ahead (now pan ${gimbal.first.toInt()}°, tilt ${gimbal.second.toInt()}°)",
                ) { haptics.click(); rover.lookAhead() }
                GroupButton(
                    R.drawable.ic_zoom_in, zoom.title, zoom.factor > 1,
                    "Zoom: tap to cycle 1× → 2× → 4× (now ${zoom.title})",
                ) { haptics.click(); rover.cycleZoom() }
                GroupButton(R.drawable.ic_photo_camera, "Photo", false, "Take a photo; it is saved on the rover") {
                    haptics.confirm(); rover.capturePhoto()
                }
                GroupButton(
                    R.drawable.ic_record, if (recSince != null) "Stop" else "Record", recSince != null,
                    if (recSince != null) "Stop recording; the video is saved on the rover" else "Start recording video on the rover",
                    activeColor = RoverColors.Danger,
                ) { haptics.confirm(); rover.toggleRecording() }
            }

            // Between the thumbs: speed presets + STOP
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpeedSelector(speed) { haptics.click(); vm.setSpeed(it) }
                Spacer(Modifier.height(12.dp))
                StopButton(driving) { haptics.stop(); rover.emergencyStop() }
            }

            AnimatedVisibility(
                !connected, Modifier.align(Alignment.TopCenter).padding(top = 124.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut() + scaleOut(targetScale = 0.92f),
            ) {
                OfflineCard(
                    address = address.toString(), searching = searching, reachable = reachable,
                    localNetworkAllowed = localNetworkAllowed,
                    onGrant = { haptics.click(); onRequestLocalNetwork() },
                    onFind = { haptics.click(); vm.connectOrDiscover() },
                    onSettings = { haptics.click(); showConnection = true },
                )
            }

            AnimatedVisibility(
                toast != null && connected, Modifier.align(Alignment.TopCenter).padding(top = 124.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut(),
            ) {
                Text(
                    toast ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.glass(CircleShape).padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }

    if (showConnection) ConnectionDialog(vm, fill, onFillChange = { fill = it }) { showConnection = false }
    if (showHelp) HelpDialog { showHelp = false }
}
