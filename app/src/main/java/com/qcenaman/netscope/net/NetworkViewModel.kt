package com.qcenaman.netscope.net

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qcenaman.netscope.model.HostInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NetworkUiState(
    val current: CurrentNetwork? = null,
    val scanning: Boolean = false,
    val phase: String = "",
    val progress: Int = 0,
    val total: Int = 0,
    val hosts: List<HostInfo> = emptyList(),
    val finished: Boolean = false
)

class NetworkViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = NetworkScanner(application)
    private val _state = MutableStateFlow(NetworkUiState())
    val state: StateFlow<NetworkUiState> = _state

    fun refresh() {
        viewModelScope.launch {
            val current = scanner.detectCurrentNetwork()
            _state.update { it.copy(current = current, hosts = emptyList(), finished = false) }
        }
    }

    fun scan() {
        val current = _state.value.current
        if (current == null || _state.value.scanning) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    scanning = true,
                    finished = false,
                    hosts = emptyList(),
                    progress = 0,
                    total = current.hosts.size,
                    phase = "Probing ${current.hosts.size} hosts…"
                )
            }
            // Single pass: the sweep probes common ports on every host and
            // returns the alive ones with their open ports.
            val live = scanner.sweep(current.hosts) { done, total ->
                _state.update { it.copy(progress = done, total = total) }
            }
            _state.update {
                it.copy(phase = "Resolving ${live.size} live host(s)…")
            }
            val hosts = withContext(Dispatchers.IO) {
                live.entries.map { (ip, ports) ->
                    val hostname = scanner.resolveHostname(ip)
                    HostInfo(
                        ip = ip,
                        hostname = hostname,
                        openPorts = ports,
                        type = DeviceClassifier.classify(ip == current.gateway, ports.toSet(), hostname),
                        isGateway = ip == current.gateway
                    )
                }.sortedBy { SubnetCalculator.toLong(it.ip) }
            }
            _state.update {
                it.copy(
                    scanning = false,
                    hosts = hosts,
                    finished = true,
                    phase = "Found ${hosts.size} device(s) on ${current.subnet}/${current.prefixLength}"
                )
            }
        }
    }
}
