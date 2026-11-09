package com.example.wifiscanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var rvWifi: RecyclerView
    private lateinit var btnScanWifi: Button
    private lateinit var btnNetworkScan: Button
    private lateinit var tvCurrentWifi: TextView
    private lateinit var progressWifi: ProgressBar
    private lateinit var wifiManager: WifiManager
    private lateinit var wifiAdapter: WifiAdapter

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.net.wifi.SCAN_RESULTS_AVAILABLE_ACTION") {
                progressWifi.visibility = ProgressBar.GONE
                wifiAdapter.updateList(wifiManager.scanResults ?: emptyList())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvWifi = findViewById(R.id.rvWifiList)
        btnScanWifi = findViewById(R.id.btnScanWifi)
        btnNetworkScan = findViewById(R.id.btnNetworkScan)
        tvCurrentWifi = findViewById(R.id.tvCurrentWifi)
        progressWifi = findViewById(R.id.progressWifi)

        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager

        rvWifi.layoutManager = LinearLayoutManager(this)
        wifiAdapter = WifiAdapter { scanResult ->
            showConnectDialog(scanResult)
        }
        rvWifi.adapter = wifiAdapter

        // Check current WiFi connection
        updateConnectionStatus()

        // Enable network scan button if connected
        btnNetworkScan.setOnClickListener {
            val info = wifiManager.connectionInfo
            if (info.ssid == "NULL" || info.ssid.isEmpty()) {
                Toast.makeText(this, "Not connected to any WiFi network", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, NetworkScanActivity::class.java))
        }

        btnScanWifi.setOnClickListener {
            requestPermissions()
        }

        // Register broadcast receiver
        val filter = IntentFilter("android.net.wifi.SCAN_RESULTS_AVAILABLE_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(scanReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(scanReceiver)
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_WIFI_STATE,
                android.Manifest.permission.CHANGE_WIFI_STATE
            )
        } else {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_WIFI_STATE,
                android.Manifest.permission.CHANGE_WIFI_STATE
            )
        }

        ActivityCompat.requestPermissions(this, permissions, 1001)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startWifiScan()
            } else {
                Toast.makeText(this, "Location and WiFi permissions required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startWifiScan() {
        progressWifi.visibility = ProgressBar.VISIBLE
        val started = wifiManager.startScan()
        if (!started) {
            progressWifi.visibility = ProgressBar.GONE
            Toast.makeText(this, "Scan failed. Try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showConnectDialog(result: ScanResult) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Connect to ${result.SSID}")
            .setMessage("Security: ${getSecurityType(result)}\nSignal: ${result.level} dBm")
            .setPositiveButton("Connect") { _, _ ->
                connectToWifi(result)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.purple_500))
        }
        dialog.show()
    }

    private fun connectToWifi(result: ScanResult) {
        if (result.capabilities.contains("WPA2") || result.capabilities.contains("WPA")) {
            showPasswordDialog(result, "WPA")
        } else if (result.capabilities.contains("WEP")) {
            showPasswordDialog(result, "WEP")
        } else {
            // Open network
            connectToOpenNetwork(result)
        }
    }

    private fun showPasswordDialog(result: ScanResult, securityType: String) {
        val input = android.widget.EditText(this)
        input.hint = "Enter password"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle("Enter password for ${result.SSID}")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val password = input.text.toString()
                connectToSecureNetwork(result, password, securityType)
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun connectToSecureNetwork(result: ScanResult, password: String, securityType: String) {
        val config = android.net.wifi.WifiConfiguration()
        config.SSID = "\"${result.SSID}\""
        config.status = android.net.wifi.WifiConfiguration.Status.ENABLED

        when (securityType) {
            "WPA" -> {
                config.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK)
                config.allowedProtocols.set(android.net.wifi.WifiConfiguration.Protocol.RSN)
                config.allowedProtocols.set(android.net.wifi.WifiConfiguration.Protocol.WPA)
                config.allowedGroupCiphers.set(android.net.wifi.WifiConfiguration.GroupCipher.TKIP)
                config.allowedGroupCiphers.set(android.net.wifi.WifiConfiguration.GroupCipher.CCMP)
                config.allowedPairwiseCiphers.set(android.net.wifi.WifiConfiguration.PairwiseCipher.TKIP)
                config.allowedPairwiseCiphers.set(android.net.wifi.WifiConfiguration.PairwiseCipher.CCMP)
                config.preSharedKey = "\"$password\""
            }
            "WEP" -> {
                config.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
                config.wepKeys[0] = "\"$password\""
                config.wepTxKeyIndex = 0
            }
        }

        val netId = wifiManager.addNetwork(config)
        if (netId == -1) {
            Toast.makeText(this, "Failed to add network", Toast.LENGTH_SHORT).show()
            return
        }

        wifiManager.disconnect()
        val enabled = wifiManager.enableNetwork(netId, true)
        if (enabled) {
            Toast.makeText(this, "Connecting to ${result.SSID}...", Toast.LENGTH_LONG).show()
            btnScanWifi.isEnabled = false
        } else {
            Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
            btnScanWifi.isEnabled = true
        }
    }

    private fun connectToOpenNetwork(result: ScanResult) {
        val config = android.net.wifi.WifiConfiguration()
        config.SSID = "\"${result.SSID}\""
        config.status = android.net.wifi.WifiConfiguration.Status.ENABLED
        config.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)

        val netId = wifiManager.addNetwork(config)
        if (netId == -1) {
            Toast.makeText(this, "Failed to add network", Toast.LENGTH_SHORT).show()
            return
        }

        wifiManager.disconnect()
        val enabled = wifiManager.enableNetwork(netId, true)
        if (enabled) {
            Toast.makeText(this, "Connecting to ${result.SSID}...", Toast.LENGTH_LONG).show()
            btnScanWifi.isEnabled = false
        } else {
            Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
            btnScanWifi.isEnabled = true
        }
    }

    private fun updateConnectionStatus() {
        val info = wifiManager.connectionInfo
        val ssid = info.ssid.trim('"')
        val bssid = info.bssid ?: ""
        val isRealConnection = ssid != "NULL" && ssid.isNotEmpty() && ssid != "<unknown ssid>" && bssid != "00:00:00:00:00:00"
        if (isRealConnection) {
            tvCurrentWifi.text = "Connected: $ssid"
            btnNetworkScan.visibility = Button.VISIBLE
        } else {
            tvCurrentWifi.text = "Not connected to WiFi"
            btnNetworkScan.visibility = Button.GONE
        }
    }

    private fun getSecurityType(result: ScanResult): String {
        return when {
            result.capabilities.contains("WPA") -> "WPA/WPA2"
            result.capabilities.contains("WEP") -> "WEP"
            result.capabilities.isEmpty() -> "Open"
            else -> "Unknown"
        }
    }
}
