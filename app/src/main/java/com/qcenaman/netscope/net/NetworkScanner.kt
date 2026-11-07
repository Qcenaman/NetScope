package com.qcenaman.netscope.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.wifi.WifiManager
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
import java.util.concurrent.TimeUnit

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
    private val wifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Detects the subnet the phone is currently on.
     * Uses the active network's link properties for the prefix and gateway,
     * falling back to the wlan0 interface with a /24 assumption.
     */
    suspend fun detectCurrentNetwork(): CurrentNetwork? {
        return withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                val info = wifiManager.connectionInfo
                val ipInt = info?.ipAddress ?: 0
                if (ipInt != 0) {
                    val ip = fromNetworkOrder(ipInt)
                    val linkProps = activeLinkProperties()
                    val prefix = linkProps?.linkAddresses
                        ?.firstOrNull { it.address is Inet4Address }
                        ?.prefixLength
                        ?.takeIf { it in 8..30 }
                        ?: 24
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

    private fun fromNetworkOrder(v: Int): String =
        "${v and 0xFF}.${(v ushr 8) and 0xFF}.${(v ushr 16) and 0xFF}.${(v ushr 24) and 0xFF}"

    /**
     * Ping sweep over the given host list using the system ping binary.
     */
    suspend fun pingSweep(
        hosts: List<String>,
        concurrency: Int = 32,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<String> {
        return withContext(Dispatchers.IO) {
            val semaphore = Semaphore(concurrency)
            val alive = java.util.Collections.synchronizedList(mutableListOf<String>())
            val lock = Any()
            var done = 0
            coroutineScope {
                hosts.map { host ->
                    async {
                        semaphore.acquire()
                        try {
                            if (ping(host)) alive.add(host)
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
            alive
        }
    }

    private fun ping(host: String): Boolean {
        return try {
            val process = ProcessBuilder("ping", "-c", "1", "-W", "1", host)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            finished && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /** TCP connect scan of common ports; returns the open ones. */
    suspend fun scanPorts(ip: String, timeoutMs: Int = 400): List<Int> {
        return withContext(Dispatchers.IO) {
            coroutineScope {
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
