package com.qcenaman.netscope.model

enum class SecurityType(val label: String) {
    OPEN("Open"),
    WPA("WPA"),
    WPA2("WPA2"),
    WPA3("WPA3"),
    WPA2_WPA3("WPA2/WPA3"),
    ENTERPRISE("Enterprise"),
    OTHER("Encrypted");

    companion object {
        fun fromCapabilities(capabilities: String): SecurityType {
            val c = capabilities.uppercase()
            return when {
                c.contains("ENTERPRISE") || c.contains("EAP") -> ENTERPRISE
                c.contains("WPA3-SAE") && c.contains("WPA2") -> WPA2_WPA3
                c.contains("WPA3-SAE") -> WPA3
                c.contains("WPA2") -> WPA2
                c.contains("WPA") -> WPA
                c.contains("WEP") -> OTHER
                else -> OPEN
            }
        }
    }
}

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val frequencyMHz: Int,
    val security: SecurityType
) {
    val isSecured: Boolean get() = security != SecurityType.OPEN
}

fun signalQuality(level: Int): Int = when {
    level >= -55 -> 4
    level >= -67 -> 3
    level >= -78 -> 2
    level >= -88 -> 1
    else -> 0
}
