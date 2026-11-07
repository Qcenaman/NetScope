package com.qcenaman.netscope.wifi

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import com.qcenaman.netscope.model.SecurityType
import com.qcenaman.netscope.model.WifiNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WifiRepository(context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    fun startScan(): Boolean = wifiManager.startScan()

    fun scanResults(): List<WifiNetwork> {
        @Suppress("DEPRECATION")
        val results = wifiManager.scanResults
        return results.map { it.toModel() }
            .sortedWith(compareByDescending<WifiNetwork> { it.level }.thenBy { it.ssid })
    }

    fun currentSsid(): String? {
        @Suppress("DEPRECATION")
        val ssid = wifiManager.connectionInfo?.ssid ?: return null
        return ssid.removeSurrounding("\"").ifEmpty { null }
    }

    /** IP address of the phone on the current network, or null. */
    fun currentIpAddress(): String? {
        @Suppress("DEPRECATION")
        val ip = wifiManager.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        // WifiInfo#getIpAddress is in network byte order: low byte = first octet.
        return "${ip and 0xFF}.${(ip ushr 8) and 0xFF}.${(ip ushr 16) and 0xFF}.${(ip ushr 24) and 0xFF}"
    }

    fun isConnectedTo(ssid: String): Boolean = currentSsid() == ssid

    /**
     * Registers the network as a system suggestion. With
     * [WifiNetworkSuggestion.Builder.setIsAppInteractionRequired] set, Android
     * shows a dialog so the user approves the connection.
     * Returns true if the suggestion was accepted by the system.
     */
    suspend fun connect(ssid: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            val builder = WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setIsAppInteractionRequired(true)
            if (password.isNotEmpty()) {
                val caps = securityCapsFor(ssid)
                if (caps.contains("SAE") && !caps.contains("WPA2-PSK")) {
                    builder.setWpa3Passphrase(password)
                } else {
                    builder.setWpa2Passphrase(password)
                }
            }
            val status = wifiManager.addNetworkSuggestions(listOf(builder.build()))
            status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
        }
    }

    private fun securityCapsFor(ssid: String): String {
        return runCatching {
            @Suppress("DEPRECATION")
            wifiManager.scanResults
                .firstOrNull { it.SSID == ssid }
                ?.capabilities
                ?: ""
        }.getOrDefault("")
    }
}

private fun ScanResult.toModel(): WifiNetwork {
    return WifiNetwork(
        ssid = SSID,
        bssid = BSSID ?: "",
        level = level,
        frequencyMHz = frequency,
        security = SecurityType.fromCapabilities(capabilities)
    )
}
