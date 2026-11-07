package com.qcenaman.netscope.model

enum class DeviceType(val label: String) {
    ROUTER("Router"),
    SMARTPHONE("Smartphone / Tablet"),
    COMPUTER("Computer"),
    CAMERA("IP Camera"),
    PRINTER("Printer"),
    NAS("NAS / Storage"),
    SMART_HOME("Smart Home Hub"),
    MEDIA("Media / TV"),
    IOT("IoT Device"),
    UNKNOWN("Unknown")
}
