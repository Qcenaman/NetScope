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
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> onScanResultsAvailable()
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
        repository.unregisterScanCallback()
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
            try {
                repository.registerScanCallback(::onScanResultsAvailable)
            } catch (e: WifiScanException) {
                repository.unregisterScanCallback()
                _state.update { it.copy(scanning = false, message = e.message) }
                return@launch
            }
            // New results arrive via the ScanResultsCallback (Android 11+)
            // or the SCAN_RESULTS_AVAILABLE broadcast (Android 10). Clear
            // the spinner after a grace period in case neither arrives.
            delay(2000)
            if (_state.value.scanning) {
                repository.unregisterScanCallback()
                _state.update { it.copy(scanning = false) }
                refreshList(showErrors = true)
            }
        }
    }

    /**
     * New scan results landed. Runs on the main thread (callback executor
     * and broadcast receiver both do), so the [scanning] guard is
     * race-free.
     */
    private fun onScanResultsAvailable() {
        if (!_state.value.scanning) return
        repository.unregisterScanCallback()
        _state.update { it.copy(scanning = false) }
        refreshList(showErrors = true)
    }

    fun connect(ssid: String, password: String) {
        if (_state.value.connectingSsid != null) return
        viewModelScope.launch {
            _state.update { it.copy(connectingSsid = ssid, message = null) }
            val registered = try {
                repository.connect(ssid, password)
            } catch (e: Exception) {
                _state.update {
                    it.copy(connectingSsid = null, message = "Could not register network \"$ssid\"")
                }
                return@launch
            }
            if (!registered) {
                _state.update {
                    it.copy(connectingSsid = null, message = "Could not register network \"$ssid\"")
                }
                return@launch
            }
            // Wait for the ConnectivityManager callback to confirm the
            // connection instead of polling the SSID.
            val confirmed = repository.awaitConnection(ssid, 30_000)
            _state.update {
                it.copy(
                    connectingSsid = null,
                    message = if (confirmed) "Connected to $ssid"
                    else "Connection to $ssid was not confirmed. Check the system Wi-Fi dialog."
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

    private fun refreshList(showErrors: Boolean = false) {
        viewModelScope.launch {
            val result = runCatching { repository.scanResults() }
            _state.update {
                it.copy(
                    networks = result.getOrDefault(emptyList()),
                    currentSsid = repository.currentSsid(),
                    currentIp = repository.currentIpAddress(),
                    message = if (showErrors && result.isFailure) result.exceptionOrNull()?.message
                    else it.message
                )
            }
        }
    }
}
