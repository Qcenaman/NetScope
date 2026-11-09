package com.qcenaman.netscope.net

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class GatewaySelectionTest {

    @Test
    fun `ipv4 default route wins over ipv6 link-local listed first`() {
        // Regression: on dual-stack links the IPv6 link-local route (fe80::2)
        // comes first and was shown as the gateway.
        val routes = listOf(
            RouteInfoLite(InetAddress.getByName("fe80::2"), true),
            RouteInfoLite(InetAddress.getByName("10.0.2.2"), true)
        )
        assertEquals("10.0.2.2", NetworkScanner.pickGateway(routes, "10.0.2.1"))
    }

    @Test
    fun `ipv4 default route wins when listed after ipv6`() {
        val routes = listOf(
            RouteInfoLite(InetAddress.getByName("10.0.2.2"), true),
            RouteInfoLite(InetAddress.getByName("fe80::2"), true)
        )
        assertEquals("10.0.2.2", NetworkScanner.pickGateway(routes, "10.0.2.1"))
    }

    @Test
    fun `falls back when only an ipv6 default route exists`() {
        val routes = listOf(RouteInfoLite(InetAddress.getByName("fe80::2"), true))
        assertEquals("10.0.2.1", NetworkScanner.pickGateway(routes, "10.0.2.1"))
    }

    @Test
    fun `non-default routes are ignored`() {
        val routes = listOf(
            RouteInfoLite(InetAddress.getByName("10.0.2.2"), false),
            RouteInfoLite(InetAddress.getByName("192.168.1.1"), true)
        )
        assertEquals("192.168.1.1", NetworkScanner.pickGateway(routes, "10.0.2.1"))
    }

    @Test
    fun `empty routes fall back`() {
        assertEquals("10.0.2.1", NetworkScanner.pickGateway(emptyList(), "10.0.2.1"))
    }
}
