package com.qcenaman.netscope.net

import java.util.Locale

/**
 * Pure IPv4 subnet math. All functions work on dotted-quad strings.
 */
object SubnetCalculator {

    fun toLong(ip: String): Long {
        val parts = ip.split(".")
        require(parts.size == 4) { "Not an IPv4 address: $ip" }
        var value = 0L
        for (part in parts) {
            val n = part.toLong()
            require(n in 0..255) { "Octet out of range in $ip" }
            value = (value shl 8) or n
        }
        return value
    }

    fun fromLong(value: Long): String {
        return listOf(
            (value ushr 24) and 0xFF,
            (value ushr 16) and 0xFF,
            (value ushr 8) and 0xFF,
            value and 0xFF
        ).joinToString(".") { it.toString() }
    }

    fun prefixMask(prefix: Int): Long {
        require(prefix in 0..32) { "Prefix out of range: $prefix" }
        return if (prefix == 0) 0L else (0xFFFFFFFFL ushr (32 - prefix)) shl (32 - prefix)
    }

    fun networkAddress(ip: String, prefix: Int): String {
        return fromLong(toLong(ip) and prefixMask(prefix))
    }

    fun broadcastAddress(ip: String, prefix: Int): String {
        return fromLong(toLong(ip) or prefixMask(prefix).inv() and 0xFFFFFFFFL)
    }

    /** All usable host addresses in the subnet (network and broadcast excluded). */
    fun hostAddresses(ip: String, prefix: Int): List<String> {
        require(prefix in 1..30) { "No usable hosts for prefix $prefix" }
        val base = toLong(ip) and prefixMask(prefix)
        val count = 1L shl (32 - prefix)
        return (1 until count - 1).map { fromLong(base + it) }
    }

    /** Prefix length from a dotted-quad netmask, e.g. "255.255.255.0" -> 24. */
    fun prefixFromMask(mask: String): Int {
        val v = toLong(mask)
        val bits = String.format(Locale.US, "%32s", v.toString(2)).replace(' ', '0')
        var prefix = 0
        for (c in bits) {
            if (c == '1') prefix++ else break
        }
        return prefix
    }
}
