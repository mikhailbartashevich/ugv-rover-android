package io.github.mikhailbartashevich.roverkit.net

import io.github.mikhailbartashevich.roverkit.protocol.RoverAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Finds rovers on the local network by probing their web app. Mirrors `RoverScanner.swift`. */
class RoverScanner(
    client: OkHttpClient = RoverHttp.defaultClient,
    private val port: Int = RoverAddress.DEFAULT_PORT,
    private val parallelism: Int = 64,
) {
    private val probeClient = client.newBuilder()
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .callTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    /** True if `host` serves a page that looks like the rover's web app. */
    suspend fun isRover(host: String, port: Int = this.port): Boolean = withContext(Dispatchers.IO) {
        try {
            probeClient.newCall(Request.Builder().url("http://$host:$port/").build()).execute().use { resp ->
                if (resp.code != 200) return@use false
                val src = resp.body.source()
                src.request(20_000)
                looksLikeRover(src.buffer.snapshot().utf8())
            }
        } catch (e: IOException) {
            false
        }
    }

    /** Probes every host 1…254 of a /24 prefix ("192.168.1"), returns the rovers found, sorted. */
    suspend fun scan(prefix: String, exclude: Set<String> = emptySet()): List<String> = coroutineScope {
        val gate = Semaphore(parallelism)
        (1..254).map { "$prefix.$it" }.filter { it !in exclude }
            .map { ip -> async { gate.withPermit { if (isRover(ip)) ip else null } } }
            .awaitAll().filterNotNull()
    }

    companion object {
        fun looksLikeRover(body: String): Boolean {
            val b = body.lowercase()
            return "socket.io" in b || "ugv" in b || "rover" in b
        }

        /** "192.168.1.37" → "192.168.1"; null for anything that isn't a dotted IPv4 address. */
        fun prefix24(ipv4: String): String? {
            val parts = ipv4.split('.')
            if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return null
            return parts.take(3).joinToString(".")
        }
    }
}
