package com.qcenaman.netscope.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubnetCalculatorTest {

    @Test
    fun `toLong and fromLong round trip`() {
        assertEquals(0xC0A8012AL, SubnetCalculator.toLong("192.168.1.42"))
        assertEquals("192.168.1.42", SubnetCalculator.fromLong(0xC0A8012AL))
        assertEquals("0.0.0.0", SubnetCalculator.fromLong(0L))
        assertEquals("255.255.255.255", SubnetCalculator.fromLong(0xFFFFFFFFL))
    }

    @Test
    fun `network and broadcast for 24 bit prefix`() {
        assertEquals("192.168.1.0", SubnetCalculator.networkAddress("192.168.1.42", 24))
        assertEquals("192.168.1.255", SubnetCalculator.broadcastAddress("192.168.1.42", 24))
    }

    @Test
    fun `network and broadcast for 16 bit prefix`() {
        assertEquals("10.0.0.0", SubnetCalculator.networkAddress("10.0.5.9", 16))
        assertEquals("10.0.255.255", SubnetCalculator.broadcastAddress("10.0.5.9", 16))
    }

    @Test
    fun `network and broadcast for 20 bit prefix`() {
        assertEquals("172.16.0.0", SubnetCalculator.networkAddress("172.16.5.9", 20))
        assertEquals("172.16.15.255", SubnetCalculator.broadcastAddress("172.16.5.9", 20))
    }

    @Test
    fun `host addresses exclude network and broadcast`() {
        val hosts = SubnetCalculator.hostAddresses("192.168.1.42", 24)
        assertEquals(254, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
        assertTrue(!hosts.contains("192.168.1.0"))
        assertTrue(!hosts.contains("192.168.1.255"))
    }

    @Test
    fun `host addresses for 30 bit prefix`() {
        val hosts = SubnetCalculator.hostAddresses("10.0.0.5", 30)
        assertEquals(listOf("10.0.0.5", "10.0.0.6"), hosts)
    }

    @Test
    fun `prefix from mask`() {
        assertEquals(24, SubnetCalculator.prefixFromMask("255.255.255.0"))
        assertEquals(16, SubnetCalculator.prefixFromMask("255.255.0.0"))
        assertEquals(30, SubnetCalculator.prefixFromMask("255.255.255.252"))
        assertEquals(0, SubnetCalculator.prefixFromMask("0.0.0.0"))
        assertEquals(32, SubnetCalculator.prefixFromMask("255.255.255.255"))
    }
}
