package com.qcenaman.netscope.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityTypeTest {

    @Test
    fun `empty capabilities is open`() {
        assertEquals(SecurityType.OPEN, SecurityType.fromCapabilities(""))
    }

    @Test
    fun `wpa2 psk`() {
        assertEquals(SecurityType.WPA2, SecurityType.fromCapabilities("WPA2-PSK"))
    }

    @Test
    fun `wpa only`() {
        assertEquals(SecurityType.WPA, SecurityType.fromCapabilities("WPA"))
    }

    @Test
    fun `wpa3 sae`() {
        assertEquals(SecurityType.WPA3, SecurityType.fromCapabilities("WPA3-SAE"))
    }

    @Test
    fun `wpa2 and wpa3 mixed`() {
        assertEquals(SecurityType.WPA2_WPA3, SecurityType.fromCapabilities("WPA2-PSK WPA3-SAE"))
    }

    @Test
    fun `enterprise`() {
        assertEquals(SecurityType.ENTERPRISE, SecurityType.fromCapabilities("WPA2-PSK-ENTERPRISE"))
        assertEquals(SecurityType.ENTERPRISE, SecurityType.fromCapabilities("EAP"))
    }

    @Test
    fun `wep is other`() {
        assertEquals(SecurityType.OTHER, SecurityType.fromCapabilities("WEP"))
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(SecurityType.WPA2, SecurityType.fromCapabilities("wpa2-psk"))
    }
}
