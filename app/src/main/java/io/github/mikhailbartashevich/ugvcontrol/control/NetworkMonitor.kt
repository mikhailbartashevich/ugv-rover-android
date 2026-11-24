package io.github.mikhailbartashevich.ugvcontrol.control

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.github.mikhailbartashevich.roverkit.net.RoverScanner
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap

/**
 * Watches the phone's Wi‑Fi networks.
 *  - Provides their /24 prefixes for rover discovery.
 *  - A rover hotspot has no internet, so Android would rather use mobile data; while
 *    such a Wi‑Fi is up the app is pinned to it so the rover stays reachable.
 */
class NetworkMonitor(private val cm: ConnectivityManager) {
    private val wifi = ConcurrentHashMap<Network, LinkProperties>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            wifi[network] = lp
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            when {
                !validated -> cm.bindProcessToNetwork(network)
                cm.boundNetworkForProcess == network -> cm.bindProcessToNetwork(null)
            }
        }

        override fun onLost(network: Network) {
            wifi.remove(network)
            if (cm.boundNetworkForProcess == network) cm.bindProcessToNetwork(null)
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    fun stop() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    /** /24 prefixes to scan: Wi‑Fi networks first, then the default network (e.g. an emulator's). */
    fun localPrefixes(): List<String> {
        val default = cm.activeNetwork?.let(cm::getLinkProperties)
        return (wifi.values + listOfNotNull(default))
            .flatMap(LinkProperties::getLinkAddresses)
            .mapNotNull { (it.address as? Inet4Address)?.hostAddress }
            .filterNot { it.startsWith("127.") }
            .mapNotNull(RoverScanner::prefix24)
            .distinct()
    }
}
