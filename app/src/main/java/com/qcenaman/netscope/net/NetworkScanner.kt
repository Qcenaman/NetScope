package com.qcenaman.netscope.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class CurrentNetwork(
    val ipAddress: String,
    val prefixLength: Int,
    val gateway: String,
    val subnet: String,
    val broadcast: String,
    val hosts: List<String>
)

/** Minimal route info for gateway selection (testable without Android). */
data class RouteInfoLite(
    val gateway: InetAddress?,
    val isDefaultRoute: Boolean
)

class NetworkScanner(context: Context) {

    private val context = context.applicationContext

    /**
     * Detects the subnet the phone is currently on.
     * Uses the active network's link properties for the IP, prefix and
     * gateway (WifiManager#getConnectionInfo is deprecated), falling back
     * to the wlan0 interface with a /24 assumption.
     */
    suspend fun detectCurrentNetwork(): CurrentNetwork? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val linkProps = activeLinkProperties()
                val addr = linkProps?.linkAddresses
                    ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
                if (addr != null) {
                    val ip = addr.address.hostAddress
                    val prefix = addr.prefixLength.takeIf { it in 8..30 } ?: 24
                    val gateway = pickGateway(
                        linkProps?.routes
                            ?.map { RouteInfoLite(it.gateway, it.isDefaultRoute) }
                            ?: emptyList(),
                        defaultGateway(ip, prefix)
                    )
                    buildNetwork(ip, prefix, gateway)
                } else {
                    detectFromInterface()
                }
            }.getOrNull()
        }
    }

    private fun activeLinkProperties(): LinkProperties? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork?.let { cm.getLinkProperties(it) }
    }

    private fun buildNetwork(ip: String, prefix: Int, gateway: String): CurrentNetwork {
        val subnet = SubnetCalculator.networkAddress(ip, prefix)
        val broadcast = SubnetCalculator.broadcastAddress(ip, prefix)
        val hosts = SubnetCalculator.hostAddresses(ip, prefix)
        return CurrentNetwork(ip, prefix, gateway, subnet, broadcast, hosts)
    }

    private fun defaultGateway(ip: String, prefix: Int): String {
        val base = SubnetCalculator.networkAddress(ip, prefix)
        return SubnetCalculator.fromLong(SubnetCalculator.toLong(base) + 1)
    }

    private fun detectFromInterface(): CurrentNetwork? {
        val ni = NetworkInterface.getNetworkInterfaces()?.toList()
            ?.firstOrNull { it.name.startsWith("wlan") && it.isUp }
            ?: return null
        val ip = ni.inetAddresses?.toList()?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?: return null
        return buildNetwork(ip.hostAddress, 24, defaultGateway(ip.hostAddress, 24))
    }

    /**
     * Two-pass device discovery over the whole host list.
     *
     * Pass 1 probes a small set of liveness ports on every host with a short
     * timeout; a host with any open port counts as alive. Pass 2 probes the
     * full common-port list on the (few) live hosts only.
     *
     * Host concurrency is bounded with a *suspending* semaphore, so the
     * bounded IO pool is never blocked waiting for a permit (a blocking
     * java.util.concurrent.Semaphore here starved the pool and stalled the
     * scan). The system ping binary is not usable from app sandboxes
     * (SELinux), so port reachability is the liveness signal.
     */
    suspend fun sweep(
        hosts: List<String>,
        concurrency: Int = 64,
        timeoutMs: Int = 400,
        onProgress: (done: Int, total: Int) -> Unit
    ): Map<String, List<Int>> {
        return withContext(Dispatchers.IO) {
            val hostSemaphore = Semaphore(concurrency)
            val lock = Any()
            var done = 0

            // Pass 1: quick liveness check on every host.
            val alive = coroutineScope {
                hosts.map { host ->
                    async {
                        hostSemaphore.withPermit {
                            val open = probePorts(host, LIVENESS_PORTS, LIVENESS_TIMEOUT_MS)
                            synchronized(lock) {
                                done++
                                onProgress(done, hosts.size)
                            }
                            host to open
                        }
                    }
                }.awaitAll()
            }.filter { (_, open) -> open.isNotEmpty() }

            // Pass 2: full port list on the live hosts only.
            val results = mutableMapOf<String, List<Int>>()
            for ((host, livenessPorts) in alive) {
                results[host] = probePorts(host, COMMON_PORTS, timeoutMs).ifEmpty { livenessPorts }
            }
            results
        }
    }

    private suspend fun probePorts(ip: String, ports: List<Int>, timeoutMs: Int): List<Int> {
        return coroutineScope {
            ports.map { port ->
                async {
                    try {
                        val socket = Socket()
                        try {
                            socket.connect(InetSocketAddress(ip, port), timeoutMs)
                            port
                        } finally {
                            socket.close()
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull().sorted()
        }
    }

    /** Reverse DNS; returns the IP itself when there is no PTR record. */
    suspend fun resolveHostname(ip: String): String {
        return withContext(Dispatchers.IO) {
            runCatching {
                InetAddress.getByName(ip).canonicalHostName
            }.getOrDefault(ip)
        }
    }

    companion object {
        /**
         * Selects the IPv4 default gateway from the link's routes. On
         * dual-stack links the IPv6 link-local route (e.g. fe80::2) can come
         * first, but this app sweeps the IPv4 subnet, so the gateway must be
         * the IPv4 one. Falls back to [fallback] when no IPv4 default route
         * exists.
         */
        fun pickGateway(routes: List<RouteInfoLite>, fallback: String): String =
            routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway?.hostAddress ?: fallback

        /**
         * Ports probed in the fast liveness pass. A hit on any of these marks
         * a host as alive; only those hosts get the full [COMMON_PORTS] list.
         * Covers routers, Windows/Linux hosts, NAS, cameras and printers.
         */
        val LIVENESS_PORTS = listOf(445, 139, 443, 80, 22, 3389, 53, 5900)

        /** Shorter timeout for the liveness pass so dead hosts fail fast. */
        const val LIVENESS_TIMEOUT_MS = 300

        val COMMON_PORTS = listOf(
            21, 22, 23, 53, 80, 110, 135, 139, 143, 443, 445, 554, 587, 631,
            993, 995, 1433, 1883, 2049, 3306, 3389, 5000, 5001, 5060, 5353,
            5432, 5555, 5900, 8000, 8080, 8081, 8083, 8123, 8443, 8554, 8888,
            9090, 9100, 9443, 32768, 37777, 49152, 62078
        )
    }
}
