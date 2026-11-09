package com.example.netscope

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDeviceTest {

    @Test
    fun networkDevice_createsWithIpAndHostname() {
        val device = NetworkDevice("192.168.1.1", "router.local", emptyList())
        assertEquals("192.168.1.1", device.ip)
        assertEquals("router.local", device.hostname)
        assertTrue(device.openPorts.isEmpty())
    }

    @Test
    fun networkDevice_withOpenPorts() {
        val ports = listOf(80 to "HTTP", 443 to "HTTPS")
        val device = NetworkDevice("192.168.1.10", "server.local", ports)
        assertEquals("192.168.1.10", device.ip)
        assertEquals(2, device.openPorts.size)
        assertEquals(80, device.openPorts[0].first)
        assertEquals("HTTP", device.openPorts[0].second)
    }

    @Test
    fun subnetCalculation_validIp() {
        val octets = intArrayOf(192, 168, 1, 100)
        val subnet = "${octets[0]}.${octets[1]}.${octets[2]}"
        assertEquals("192.168.1", subnet)
    }

    @Test
    fun subnetCalculation_anotherIp() {
        val octets = intArrayOf(10, 0, 0, 50)
        val subnet = "${octets[0]}.${octets[1]}.${octets[2]}"
        assertEquals("10.0.0", subnet)
    }
}
