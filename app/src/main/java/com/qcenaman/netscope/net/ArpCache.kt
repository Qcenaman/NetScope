package com.qcenaman.netscope.net

import java.io.File

/**
 * Reads the kernel ARP cache (/proc/net/arp) to map IPs to MAC addresses.
 * After a ping sweep, recently seen hosts are present here.
 */
object ArpCache {

    /**
     * @param content optional raw content for testing; reads /proc/net/arp when null.
     */
    fun parse(content: String?): Map<String, String> {
        val text = content ?: runCatching { File("/proc/net/arp").readText() }.getOrNull() ?: return emptyMap()
        val lines = text.lines()
        // Skip the header row only when present (tests pass headerless content too).
        val data = if (lines.firstOrNull()?.trim()?.startsWith("IP address") == true) lines.drop(1) else lines
        val result = mutableMapOf<String, String>()
        for (line in data) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 4) continue
            val ip = parts[0]
            val mac = parts[3]
            if (!ip.contains('.') || mac.contains("incomplete")) continue
            if (!isIpv4(ip)) continue
            result[ip] = mac.lowercase()
        }
        return result
    }

    private fun isIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        return parts.size == 4 && parts.all { it.all(Char::isDigit) && it.toInt() in 0..255 }
    }
}
