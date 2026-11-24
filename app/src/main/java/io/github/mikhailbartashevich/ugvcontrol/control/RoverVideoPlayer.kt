package io.github.mikhailbartashevich.ugvcontrol.control

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live camera from the rover: H.264 over RTSP from its mediamtx (`rtsp://<ip>:8554/cam`),
 * played by Media3 ExoPlayer over TCP-interleaved RTP with a minimal buffer for low latency.
 * Reconnects on errors and when frames stop arriving. Mirrors `VideoStream.swift` in the Mac app.
 */
@OptIn(UnstableApi::class)
class RoverVideoPlayer(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context, renderers(context))
        .setLoadControl(
            DefaultLoadControl.Builder()
                // Play as soon as one frame is in, keep almost nothing queued: this is a live camera.
                .setBufferDurationsMs(/* min */ 100, /* max */ 500, /* forPlayback */ 0, /* afterRebuffer */ 0)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        .build()
        .apply {
            volume = 0f
            playWhenReady = true
        }

    private val _fps = MutableStateFlow(0.0)
    /** Frames per second shown on this phone. */
    val fps: StateFlow<Double> = _fps.asStateFlow()

    private val _hasVideo = MutableStateFlow(false)
    /** At least one frame of the current stream has been shown. */
    val hasVideo: StateFlow<Boolean> = _hasVideo.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var url: String? = null
    @Volatile private var frames = 0
    @Volatile private var lastFrameMs = 0L
    private var windowStart = 0L

    init {
        player.setVideoFrameMetadataListener { _, _, _, _ ->
            frames++
            lastFrameMs = System.currentTimeMillis()
        }
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() { _hasVideo.value = true }
            override fun onPlayerError(error: PlaybackException) = scheduleRestart(RETRY_MS)
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) scheduleRestart(RETRY_MS)
            }
        })
    }

    /** Starts (or switches) the stream; null stops it. */
    fun play(rtspUrl: String?) {
        if (rtspUrl == url) return
        url = rtspUrl
        main.removeCallbacksAndMessages(null)
        if (rtspUrl == null) {
            player.stop()
            player.clearMediaItems()
            _hasVideo.value = false
            _fps.value = 0.0
        } else {
            start()
            main.postDelayed(::watch, WATCH_MS)
        }
    }

    private fun start() {
        val u = url ?: return
        _hasVideo.value = false
        lastFrameMs = System.currentTimeMillis()
        val source = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)          // reliable through Wi‑Fi and the emulator's NAT
            .setTimeoutMs(4_000)
            .createMediaSource(MediaItem.fromUri(u))
        player.setMediaSource(source)
        player.prepare()
        player.play()
    }

    private fun scheduleRestart(delayMs: Long) {
        if (url == null) return
        main.removeCallbacks(restart)
        main.postDelayed(restart, delayMs)
    }

    private val restart = Runnable { start() }

    /** Once a second: compute fps, restart a stream that stalled (no frame for [STALL_MS]). */
    private fun watch() {
        if (url == null) return
        val now = System.currentTimeMillis()
        if (windowStart != 0L) _fps.value = frames * 1000.0 / (now - windowStart).coerceAtLeast(1)
        frames = 0
        windowStart = now
        if (now - lastFrameMs > STALL_MS) {
            _fps.value = 0.0
            start()
        }
        main.postDelayed(::watch, WATCH_MS)
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        player.release()
    }

    private companion object {
        /**
         * Decoder fallback on, and the Android emulator's goldfish codecs tried last: its hardware
         * H.264 decoder fails on this stream ("Invalid block data"), its software one works.
         * Real phones keep their hardware decoder first.
         */
        fun renderers(context: Context) = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector { mime, secure, tunneling ->
                MediaCodecUtil.getDecoderInfos(mime, secure, tunneling).sortedBy { "goldfish" in it.name }
            }

        const val RETRY_MS = 1_500L
        const val WATCH_MS = 1_000L
        /** Long enough for the rover's 1 s keyframe interval plus a connect. */
        const val STALL_MS = 6_000L
    }
}
