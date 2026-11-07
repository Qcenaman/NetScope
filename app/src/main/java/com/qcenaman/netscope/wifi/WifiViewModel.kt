package com.qcenaman.netscope.wifi

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qcenaman.netscope.model.WifiNetwork
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class WifiUiState(
    val wifiEnabled: Boolean = false,
    val scanning: Boolean = false,
    val networks: List<WifiNetwork> = emptyList(),
    val currentSsid: String? = null,
    val currentIp: String? = null,
    val connectingSsid: String? = null,
    val message: String? = null
)

class WifiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WifiRepository(application)
    private val _state = MutableStateFlow(WifiUiState())
    val state: StateFlow<WifiUiState> = _state

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> refreshList()
                WifiManager.WIFI_STATE_CHANGED_ACTION ->
                    _state.update { it.copy(wifiEnabled = repository.isWifiEnabled()) }
                android.net.ConnectivityManager.CONNECTIVITY_ACTION -> refreshStatus()
            }
        }
    }

    init {
        getApplication<Application>().registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
            }
        )
        refreshAll()
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(receiver) }
        super.onCleared()
    }

    fun refreshAll() {
        refreshStatus()
        refreshList()
    }

    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, message = null) }
        viewModelScope.launch {
            repository.startScan()
            // Scan results arrive via SCAN_RESULTS_AVAILABLE; clear the spinner
            // after a grace period in case the system throttles the request.
            delay(3000)
            _state.update { it.copy(scanning = false) }
            refreshList()
        }
    }

    fun connect(ssid: String, password: String) {
        if (_state.value.connectingSsid != null) return
        viewModelScope.launch {
            _state.update { it.copy(connectingSsid = ssid, message = null) }
            val registered = repository.connect(ssid, password)
            if (!registered) {
                _state.update {
                    it.copy(connectingSsid = null, message = "Could not register network \"$ssid\"")
                }
                return@launch
            }
            val connected = withTimeoutOrNull(20_000) {
                while (true) {
                    if (repository.isConnectedTo(ssid)) return@withTimeoutOrNull true
                    delay(1500)
                }
            }
            _state.update {
                it.copy(
                    connectingSsid = null,
                    message = if (connected == true) "Connected to $ssid"
                    else "Could not connect to $ssid"
                )
            }
            refreshAll()
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun refreshStatus() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    wifiEnabled = repository.isWifiEnabled(),
                    currentSsid = repository.currentSsid(),
                    currentIp = repository.currentIpAddress()
                )
            }
        }
    }

    private fun refreshList() {
        viewModelScope.launch {
            val networks = runCatching { repository.scanResults() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    networks = networks,
                    currentSsid = repository.currentSsid(),
                    currentIp = repository.currentIpAddress()
                )
            }
        }
    }
}
