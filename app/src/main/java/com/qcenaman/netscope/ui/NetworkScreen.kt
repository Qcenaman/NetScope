package com.qcenaman.netscope.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qcenaman.netscope.model.HostInfo
import com.qcenaman.netscope.net.NetworkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(modifier: Modifier = Modifier, viewModel: NetworkViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            val current = state.current
            if (current == null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "Connect to a Wi-Fi network first, then come back to scan it.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                NetworkInfoCard(
                    ip = current.ipAddress,
                    subnet = "${current.subnet}/${current.prefixLength}",
                    gateway = current.gateway,
                    broadcast = current.broadcast
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp).width(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.phase, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = {
                                if (state.total == 0) 0f
                                else state.progress.toFloat() / state.total
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        )
                    }
                } else {
                    Text(
                        text = state.phase.ifEmpty { "Ready" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.scan() },
                        enabled = state.current != null
                    ) {
                        Icon(Icons.Filled.Radar, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Scan network")
                    }
                }
            }
        }

        items(state.hosts, key = { it.ip }) { host ->
            HostRow(host = host)
        }

        if (state.finished && state.hosts.isEmpty()) {
            item {
                Text(
                    text = "No devices found. Some networks block ping traffic; try again later.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NetworkInfoCard(
    ip: String,
    subnet: String,
    gateway: String,
    broadcast: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            InfoRow("This device", ip)
            Spacer(Modifier.height(4.dp))
            InfoRow("Subnet", subnet)
            Spacer(Modifier.height(4.dp))
            InfoRow("Gateway", gateway)
            Spacer(Modifier.height(4.dp))
            InfoRow("Broadcast", broadcast)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun HostRow(host: HostInfo) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (host.isGateway) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = host.ip,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(8.dp))
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            host.type.label + if (host.isGateway) " · gateway" else "",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
            if (host.hostname != host.ip) {
                Text(
                    text = host.hostname,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (host.mac != null) {
                Text(
                    text = "MAC ${host.mac}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (host.openPorts.isNotEmpty()) {
                Text(
                    text = "open ports: ${host.openPorts.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
