package com.qcenaman.netscope.net

import com.qcenaman.netscope.model.DeviceType

/**
 * Rule-based device fingerprinting from open ports and hostname hints.
 */
object DeviceClassifier {

    private val CAMERA_PORTS = setOf(554, 8554, 37777, 34567)
    private val PRINTER_PORTS = setOf(9100, 631, 515)
    private val APPLE_PORTS = setOf(5353, 32768, 49152, 62078, 548)
    private val WEB_PORTS = setOf(80, 443, 8080, 8081, 8443, 9443, 8888)

    fun classify(
        isGateway: Boolean,
        openPorts: Set<Int>,
        hostname: String
    ): DeviceType {
        val name = hostname.lowercase()

        if (isGateway) return DeviceType.ROUTER
        if (name.contains("router") || name.contains("gateway") || name.contains("modem")) {
            return DeviceType.ROUTER
        }
        if (openPorts.any { it in PRINTER_PORTS }) return DeviceType.PRINTER
        if (openPorts.any { it in CAMERA_PORTS }) return DeviceType.CAMERA
        if (name.contains("camera") || name.contains("cam-")) return DeviceType.CAMERA
        if (openPorts.contains(8123) || openPorts.contains(1883) || openPorts.contains(8083)) {
            return DeviceType.SMART_HOME
        }
        if (name.contains("home-assistant") || name.contains("hub")) return DeviceType.SMART_HOME
        if (openPorts.contains(445) || openPorts.contains(139) || openPorts.contains(3389)) {
            return DeviceType.COMPUTER
        }
        if (name.contains("synology") || name.contains("qnap") || name.contains("nas")) {
            return DeviceType.NAS
        }
        if (openPorts.contains(22) && (openPorts.contains(5000) || openPorts.contains(5001) || openPorts.contains(80))) {
            return DeviceType.NAS
        }
        if (openPorts.contains(5555)) return DeviceType.SMARTPHONE
        if (openPorts.any { it in APPLE_PORTS }) return DeviceType.SMARTPHONE
        if (name.contains("iphone") || name.contains("android") || name.contains("phone")) {
            return DeviceType.SMARTPHONE
        }
        if (name.contains("tv") || name.contains("roku") || name.contains("apple-tv") ||
            openPorts.contains(32768) && openPorts.contains(8086)
        ) {
            return DeviceType.MEDIA
        }
        if (openPorts.contains(23)) return DeviceType.IOT
        if (openPorts.any { it in WEB_PORTS }) return DeviceType.IOT
        return DeviceType.UNKNOWN
    }
}
