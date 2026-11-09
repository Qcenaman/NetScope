package com.qcenaman.netscope.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Semaphore

data class CurrentNetwork(
    val ipAddress: String,
    val prefixLength: Int,
    val gateway: String,
    val subnet: String,
    val broadcast: String,
    val hosts: List<String>
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
                    val gateway = linkProps?.routes
                        ?.firstOrNull { it.isDefaultRoute }
                        ?.gateway
                        ?.hostAddress
                        ?: defaultGateway(ip, prefix)
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
     * Single-pass device discovery: TCP-connect probes of common ports over
     * the whole host list. A host with at least one open port counts as
     * alive. Returns a map of alive host to its open ports.
     *
     * The system ping binary is not usable from app sandboxes (SELinux),
     * so port reachability is the liveness signal.
     */
    suspend fun sweep(
        hosts: List<String>,
        concurrency: Int = 32,
        timeoutMs: Int = 400,
        onProgress: (done: Int, total: Int) -> Unit
    ): Map<String, List<Int>> {
        return withContext(Dispatchers.IO) {
            val semaphore = Semaphore(concurrency)
            val results = java.util.Collections.synchronizedMap(mutableMapOf<String, List<Int>>())
            val lock = Any()
            var done = 0
            coroutineScope {
                hosts.map { host ->
                    async {
                        semaphore.acquire()
                        try {
                            val open = probePorts(host, timeoutMs)
                            if (open.isNotEmpty()) results[host] = open
                        } finally {
                            semaphore.release()
                            synchronized(lock) {
                                done++
                                onProgress(done, hosts.size)
                            }
                        }
                    }
                }.awaitAll()
            }
            results
        }
    }

    private suspend fun probePorts(ip: String, timeoutMs: Int): List<Int> {
        return coroutineScope {
            COMMON_PORTS.map { port ->
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
            }.awaitAll().filterNotNull()
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
        val COMMON_PORTS = listOf(
            21, 22, 23, 53, 80, 110, 135, 139, 143, 443, 445, 554, 587, 631,
            993, 995, 1433, 1883, 2049, 3306, 3389, 5000, 5001, 5060, 5353,
            5432, 5555, 5900, 8000, 8080, 8081, 8083, 8123, 8443, 8554, 8888,
            9090, 9100, 9443, 32768, 37777, 49152, 62078
        )
    }
}
