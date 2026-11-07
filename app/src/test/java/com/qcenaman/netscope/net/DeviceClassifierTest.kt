package com.qcenaman.netscope.net

import com.qcenaman.netscope.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceClassifierTest {

    @Test
    fun `gateway is a router`() {
        assertEquals(DeviceType.ROUTER, DeviceClassifier.classify(true, emptySet(), "192.168.1.1"))
    }

    @Test
    fun `printer ports`() {
        assertEquals(DeviceType.PRINTER, DeviceClassifier.classify(false, setOf(9100, 631), "hp-printer"))
        assertEquals(DeviceType.PRINTER, DeviceClassifier.classify(false, setOf(515), "print"))
    }

    @Test
    fun `camera ports`() {
        assertEquals(DeviceType.CAMERA, DeviceClassifier.classify(false, setOf(554, 8554), "cam"))
        assertEquals(DeviceType.CAMERA, DeviceClassifier.classify(false, setOf(37777), "x"))
    }

    @Test
    fun `windows computer via smb or rdp`() {
        assertEquals(DeviceType.COMPUTER, DeviceClassifier.classify(false, setOf(445, 139), "pc"))
        assertEquals(DeviceType.COMPUTER, DeviceClassifier.classify(false, setOf(3389), "workstation"))
    }

    @Test
    fun `smart home hub`() {
        assertEquals(DeviceType.SMART_HOME, DeviceClassifier.classify(false, setOf(8123), "homeassistant"))
        assertEquals(DeviceType.SMART_HOME, DeviceClassifier.classify(false, setOf(1883), "hub"))
    }

    @Test
    fun `nas via ssh plus web`() {
        assertEquals(DeviceType.NAS, DeviceClassifier.classify(false, setOf(22, 5000), "diskstation"))
        assertEquals(DeviceType.NAS, DeviceClassifier.classify(false, setOf(22), "synology-nas"))
    }

    @Test
    fun `apple device via airplay and bonjour`() {
        assertEquals(DeviceType.SMARTPHONE, DeviceClassifier.classify(false, setOf(5353, 32768), "iphone"))
    }

    @Test
    fun `adb port means phone`() {
        assertEquals(DeviceType.SMARTPHONE, DeviceClassifier.classify(false, setOf(5555), "android"))
    }

    @Test
    fun `generic web server is iot`() {
        assertEquals(DeviceType.IOT, DeviceClassifier.classify(false, setOf(80, 443), "box"))
    }

    @Test
    fun `telnet only is iot`() {
        assertEquals(DeviceType.IOT, DeviceClassifier.classify(false, setOf(23), "device"))
    }

    @Test
    fun `no open ports and no hints is unknown`() {
        assertEquals(DeviceType.UNKNOWN, DeviceClassifier.classify(false, emptySet(), "192.168.1.77"))
    }

    @Test
    fun `hostname hints win over nothing`() {
        assertEquals(DeviceType.ROUTER, DeviceClassifier.classify(false, emptySet(), "my-router"))
        assertEquals(DeviceType.CAMERA, DeviceClassifier.classify(false, emptySet(), "cam-01"))
    }
}
