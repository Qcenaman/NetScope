package com.qcenaman.netscope

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val permissionState = mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Re-check the real grant state: the user may have granted only
            // some of the requested permissions.
            checkPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermission()
        setContent {
            NetScopeApp(permissionGranted = permissionState.value, onRequestPermission = ::requestPermission)
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermission()
    }

    /**
     * Wi-Fi scan results and the connected SSID require ACCESS_FINE_LOCATION
     * on every Android version (see WifiManager#getScanResults docs).
     * NEARBY_WIFI_DEVICES is the preferred Wi-Fi permission on Android 13+
     * and is requested alongside it.
     */
    private fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }

    private fun checkPermission() {
        permissionState.value = requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermission() {
        permissionLauncher.launch(requiredPermissions().toTypedArray())
    }
}
