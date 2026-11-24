package io.github.mikhailbartashevich.roverkit.net

import io.github.mikhailbartashevich.roverkit.protocol.Esp32Command
import io.github.mikhailbartashevich.roverkit.protocol.RoverAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The rover's plain HTTP routes: reachability check and `POST /send_command`. */
class RoverHttp(private val client: OkHttpClient = defaultClient) {

    sealed interface Result {
        data object Ok : Result
        data class Failed(val message: String) : Result
    }

    /** GET `/` — is the rover's web app answering? */
    suspend fun ping(address: RoverAddress): Result = withContext(Dispatchers.IO) {
        try {
            quick.newCall(Request.Builder().url(address.baseUrl + "/").build()).execute().use { Result.Ok }
        } catch (e: IOException) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Runs a line through the host's command line (`POST /send_command`,
     * form `command=…`), e.g. `base -c {"T":1,"L":0,"R":0}`.
     */
    suspend fun sendCommandLine(address: RoverAddress, line: String): Result = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder().add("command", line).build()
            val req = Request.Builder().url(address.sendCommandUrl).post(body).build()
            quick.newCall(req).execute().use { resp ->
                if (resp.code == 200) Result.Ok else Result.Failed("HTTP ${resp.code}")
            }
        } catch (e: IOException) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun send(address: RoverAddress, command: Esp32Command): Result =
        sendCommandLine(address, baseCommandLine(command))

    private val quick = client.newBuilder().callTimeout(2, TimeUnit.SECONDS).build()

    companion object {
        /** Shared client: one connection pool for socket, HTTP and video. */
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.SECONDS)
            .build()

        fun baseCommandLine(command: Esp32Command) = "base -c ${command.json}"
    }
}
