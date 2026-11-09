package com.qcenaman.netscope.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.ScanResultsCallback
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.qcenaman.netscope.model.SecurityType
import com.qcenaman.netscope.model.WifiNetwork
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.util.concurrent.Executor

/** Thrown when Wi-Fi scan data cannot be read (e.g. missing location permission). */
class WifiScanException(message: String, cause: Throwable? = null) : Exception(message, cause)

class WifiRepository(context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // android.os.Executors (with a mainThread() factory) does not exist on
    // Android, so post the scan callback onto the main looper manually.
    private val mainExecutor = Executor { Handler(Looper.getMainLooper()).post(it) }

    private var scanCallback: ScanResultsCallback? = null

    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    /**
     * Returns the latest cached scan results. On Android 12+ the system runs
     * scans automatically, so this normally returns fresh data right away.
     *
     * Requires ACCESS_WIFI_STATE + ACCESS_FINE_LOCATION (see
     * WifiManager#getScanResults); throws [WifiScanException] instead of
     * returning a silently empty or redacted list.
     */
    fun scanResults(): List<WifiNetwork> {
        val raw: List<ScanResult> = try {
            wifiManager.scanResults
        } catch (e: SecurityException) {
            throw WifiScanException(
                "Wi-Fi scan results require the location permission",
                e
            )
        }
        // When location permission is missing, Android redacts every entry:
        // SSID becomes WifiManager.UNKNOWN_SSID and BSSID becomes a sentinel.
        val visible = raw.filter { it.BSSID != REDACTED_BSSID }
        if (raw.isNotEmpty() && visible.isEmpty()) {
            throw WifiScanException(
                "Wi-Fi scan results are redacted. Grant the location permission to see networks."
            )
        }
        return visible.map { it.toModel() }
            .sortedWith(compareByDescending<WifiNetwork> { it.level }.thenBy { it.ssid })
    }

    /**
     * Registers a callback fired whenever new scan results are available
     * (Android 11+). On Android 10 the system delivers
     * SCAN_RESULTS_AVAILABLE_ACTION broadcasts instead, which the ViewModel
     * still listens for.
     */
    fun registerScanCallback(onResults: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (scanCallback != null) return
        @Suppress("NewApi") // guarded by the SDK_INT check above
        val callback = object : ScanResultsCallback() {
            override fun onScanResultsAvailable() = onResults()
        }
        scanCallback = callback
        try {
            @Suppress("NewApi") // guarded by the SDK_INT check above
            wifiManager.registerScanResultsCallback(mainExecutor, callback)
        } catch (e: Exception) {
            scanCallback = null
            throw WifiScanException("Could not register scan results callback", e)
        }
    }

    fun unregisterScanCallback() {
        val callback = scanCallback ?: return
        scanCallback = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            @Suppress("NewApi") // guarded by the SDK_INT check above
            wifiManager.unregisterScanResultsCallback(callback)
        }
    }

    /** SSID of the network the phone is connected to, or null. */
    fun currentSsid(): String? {
        // WifiManager#getConnectionInfo is deprecated, but it still returns
        // the SSID while the app holds ACCESS_FINE_LOCATION (which this app
        // requests and gates on). The modern replacement requires a
        // persistent NetworkCallback with FLAG_INCLUDE_LOCATION_INFO, which
        // is overkill for reading the current SSID.
        @Suppress("DEPRECATION")
        val ssid = wifiManager.connectionInfo?.ssid ?: return null
        // WifiInfo#getSSID returns WifiManager.UNKNOWN_SSID (empty string)
        // when the location permission is missing.
        return ssid.removeSurrounding("\"").takeIf { it.isNotEmpty() }
    }

    /** IP address of the phone on the current network, or null. */
    fun currentIpAddress(): String? {
        val network = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        return linkProperties.linkAddresses
            ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?.address
            ?.hostAddress
    }

    /**
     * Waits until the phone is connected to [ssid], watching for network
     * state changes via a ConnectivityManager callback (not a fixed SSID
     * poll). Returns false when the timeout elapses first.
     */
    suspend fun awaitConnection(ssid: String, timeoutMs: Long = 30_000): Boolean =
        withContext(Dispatchers.IO) {
            if (currentSsid() == ssid) return@withContext true
            val connected = CompletableDeferred<Boolean>()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = checkConnected()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    checkConnected()

                private fun checkConnected() {
                    if (connected.isCompleted) return
                    if (currentSsid() == ssid) connected.complete(true)
                }
            }
            try {
                connectivityManager.registerNetworkCallback(
                    NetworkRequest.Builder().build(),
                    callback
                )
            } catch (e: Exception) {
                return@withContext currentSsid() == ssid
            }
            try {
                withTimeoutOrNull(timeoutMs) { connected.await() } ?: false
            } finally {
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
        }

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
        return try {
            wifiManager.scanResults
                .firstOrNull { it.SSID == ssid }
                ?.capabilities
                ?: ""
        } catch (e: SecurityException) {
            // Location permission was revoked; no capabilities available.
            Log.w(TAG, "Cannot read Wi-Fi scan capabilities", e)
            ""
        }
    }

    companion object {
        private const val TAG = "WifiRepository"

        /** BSSID Android substitutes for every redacted scan result. */
        private const val REDACTED_BSSID = "02:00:00:00:00:00"
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
