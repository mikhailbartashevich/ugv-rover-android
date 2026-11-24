package io.github.mikhailbartashevich.roverkit.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Splits a `multipart/x-mixed-replace` MJPEG byte stream into JPEG frames by their
 * SOI (FFD8) / EOI (FFD9) markers, so it doesn't depend on part headers.
 */
class MjpegFrameSplitter(private val maxFrameBytes: Int = 4 * 1024 * 1024) {
    private val buf = ByteArrayOutputStream()
    private var inFrame = false
    private var prev = -1

    /** Feeds bytes; calls [onFrame] for each complete JPEG. */
    fun feed(data: ByteArray, len: Int = data.size, onFrame: (ByteArray) -> Unit) {
        for (i in 0 until len) {
            val b = data[i].toInt() and 0xFF
            if (!inFrame) {
                if (prev == 0xFF && b == 0xD8) {
                    inFrame = true
                    buf.reset()
                    buf.write(0xFF); buf.write(0xD8)
                }
            } else {
                buf.write(b)
                if (prev == 0xFF && b == 0xD9) {
                    inFrame = false
                    onFrame(buf.toByteArray())
                    buf.reset()
                } else if (buf.size() > maxFrameBytes) {
                    inFrame = false
                    buf.reset()
                }
            }
            prev = b
        }
    }
}

/** Streams `/video_feed` as raw JPEG frames, reconnecting until the collector stops. */
class MjpegStream(client: OkHttpClient = RoverHttp.defaultClient) {
    private val client = client.newBuilder().readTimeout(5, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()

    fun frames(url: String, retryDelayMs: Long = 1000): Flow<ByteArray> = flow {
        while (true) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    val input = resp.body.byteStream()
                    val splitter = MjpegFrameSplitter()
                    val chunk = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(chunk)
                        if (n < 0) break
                        val frames = ArrayList<ByteArray>(1)
                        splitter.feed(chunk, n) { frames += it }
                        for (f in frames) emit(f)
                    }
                }
            } catch (e: IOException) {
                // Dropped or not reachable yet — try again below.
            }
            delay(retryDelayMs)
        }
    }.flowOn(Dispatchers.IO)
}
