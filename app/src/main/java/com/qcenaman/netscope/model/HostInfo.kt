package com.qcenaman.netscope.model

data class HostInfo(
    val ip: String,
    val hostname: String,
    val mac: String?,
    val openPorts: List<Int>,
    val type: DeviceType,
    val isGateway: Boolean = false
)
