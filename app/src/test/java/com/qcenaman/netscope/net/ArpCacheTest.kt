package com.qcenaman.netscope.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArpCacheTest {

    private val sample = """
        IP address       HW type     Flags       HW address            Mask     Device
        192.168.1.1      0x1         0x2         aa:bb:cc:dd:ee:01     *        wlan0
        192.168.1.42     0x1         0x0         aa:bb:cc:dd:ee:42     *        wlan0
        192.168.1.99     0x1         0x0         (incomplete)          *        wlan0
        192.168.1.255    0x1         0x2         ff:ff:ff:ff:ff:ff     *        wlan0
    """.trimIndent()

    @Test
    fun `parses ip to mac mappings`() {
        val map = ArpCache.parse(sample)
        assertEquals(3, map.size)
        assertEquals("aa:bb:cc:dd:ee:01", map["192.168.1.1"])
        assertEquals("aa:bb:cc:dd:ee:42", map["192.168.1.42"])
        assertEquals("ff:ff:ff:ff:ff:ff", map["192.168.1.255"])
    }

    @Test
    fun `skips incomplete entries`() {
        val map = ArpCache.parse(sample)
        assertTrue(!map.containsKey("192.168.1.99"))
    }

    @Test
    fun `skips malformed lines`() {
        val content = """
            IP address       HW type     Flags       HW address            Mask     Device
            not-an-ip        0x1         0x2         aa:bb:cc:dd:ee:01     *        wlan0
            192.168.1.300    0x1         0x2         aa:bb:cc:dd:ee:02     *        wlan0
            192.168.1.7
        """.trimIndent()
        val map = ArpCache.parse(content)
        assertTrue(map.isEmpty())
    }

    @Test
    fun `normalizes mac to lowercase`() {
        val content = "192.168.1.5      0x1         0x2         AA:BB:CC:DD:EE:05     *        wlan0"
        val map = ArpCache.parse(content)
        assertEquals("aa:bb:cc:dd:ee:05", map["192.168.1.5"])
    }

    @Test
    fun `empty content yields empty map`() {
        assertTrue(ArpCache.parse("").isEmpty())
    }

    @Test
    fun `null content falls back to file read without crashing`() {
        // On a dev machine /proc/net/arp does not exist; must return an empty map, not throw.
        assertTrue(ArpCache.parse(null).isEmpty() || ArpCache.parse(null).values.all { it.length == 17 })
    }
}
