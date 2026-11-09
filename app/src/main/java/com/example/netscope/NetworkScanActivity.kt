package com.example.netscope

import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class NetworkScanActivity : AppCompatActivity() {

    private lateinit var rvDevices: RecyclerView
    private lateinit var tvSubnet: TextView
    private lateinit var btnScan: Button
    private lateinit var progressScan: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var deviceAdapter: DeviceAdapter
    private val devices = mutableListOf<NetworkDevice>()

    // Common ports to scan
    private val commonPorts = listOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP",
        53 to "DNS", 80 to "HTTP", 110 to "POP3", 143 to "IMAP",
        443 to "HTTPS", 445 to "SMB", 993 to "IMAPS", 995 to "POP3S",
        3306 to "MySQL", 3389 to "RDP", 5432 to "PostgreSQL",
        6379 to "Redis", 8080 to "HTTP-Alt", 8443 to "HTTPS-Alt",
        27017 to "MongoDB"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_scan)

        rvDevices = findViewById(R.id.rvDevices)
        tvSubnet = findViewById(R.id.tvSubnet)
        btnScan = findViewById(R.id.btnScan)
        progressScan = findViewById(R.id.progressScan)
        tvStatus = findViewById(R.id.tvStatus)

        deviceAdapter = DeviceAdapter(devices)
        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = deviceAdapter

        // Get current subnet
        val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val subnet = getSubnet(wifiInfo)
        tvSubnet.text = "Scanning subnet: $subnet"

        btnScan.setOnClickListener {
            startNetworkScan(subnet)
        }
    }

    private fun getSubnet(wifiInfo: android.net.wifi.WifiInfo): String {
        val ipAddress = wifiInfo.ipAddress
        val octets = arrayOf(
            (ipAddress and 0xff).toString(),
            ((ipAddress shr 8) and 0xff).toString(),
            ((ipAddress shr 16) and 0xff).toString(),
            ((ipAddress shr 24) and 0xff).toString()
        )
        return "${octets[0]}.${octets[1]}.${octets[2]}"
    }

    private fun startNetworkScan(subnet: String) {
        devices.clear()
        deviceAdapter.notifyDataSetChanged()
        progressScan.visibility = ProgressBar.VISIBLE
        tvStatus.text = "Scanning..."
        btnScan.isEnabled = false

        val scope = CoroutineScope(Dispatchers.IO + Job())

        // Scan each IP in the subnet (1-254)
        for (lastOctet in 1..254) {
            val ip = "$subnet.$lastOctet"
            scope.launch {
                val host = try {
                    InetAddress.getByName(ip).hostName
                } catch (e: Exception) {
                    null
                }

                // Check if host is reachable with a short timeout
                val reachable = try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(InetAddress.getByName(ip), 500), 500)
                    socket.close()
                    true
                } catch (e: Exception) {
                    false
                }

                if (reachable) {
                    val ports = scanPorts(ip, scope)
                    devices.add(NetworkDevice(ip, host ?: "Unknown", ports))

                    scope.launch(Dispatchers.Main) {
                        deviceAdapter.notifyDataSetChanged()
                        tvStatus.text = "Found ${devices.size} devices..."
                    }
                }
            }
        }

        // Wait for all scans to complete
        scope.coroutineContext[Job]?.invokeOnCompletion {
            scope.launch(Dispatchers.Main) {
                progressScan.visibility = ProgressBar.GONE
                tvStatus.text = "Scan complete: ${devices.size} devices found"
                btnScan.isEnabled = true
            }
        }
    }

    private suspend fun scanPorts(ip: String, scope: CoroutineScope): List<Pair<Int, String>> {
        val openPorts = mutableListOf<Pair<Int, String>>()

        for ((port, serviceName) in commonPorts) {
            val result = try {
                val socket = Socket()
                socket.connect(InetSocketAddress(InetAddress.getByName(ip), port), 300)
                socket.close()
                true
            } catch (e: Exception) {
                false
            }

            if (result) {
                openPorts.add(port to serviceName)
            }

            // Update UI periodically
            if (port % 5 == 0) {
                scope.launch(Dispatchers.Main) {
                    tvStatus.text = "Scanning ports on $ip: $port/${commonPorts.size}..."
                }
            }
        }

        return openPorts.sortedBy { it.first }
    }
}

data class NetworkDevice(
    val ip: String,
    val hostname: String,
    val openPorts: List<Pair<Int, String>>
)
