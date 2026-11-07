package com.qcenaman.netscope.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Wifi1Bar
import androidx.compose.material.icons.filled.Wifi2Bar
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.qcenaman.netscope.model.WifiNetwork
import com.qcenaman.netscope.model.signalQuality
import com.qcenaman.netscope.wifi.WifiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(
    modifier: Modifier = Modifier,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    viewModel: WifiViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingNetwork by remember { mutableStateOf<WifiNetwork?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SnackbarHost(snackbar)

        if (!permissionGranted) {
            PermissionCard(onRequest = onRequestPermission)
        }

        StatusCard(
            wifiEnabled = state.wifiEnabled,
            currentSsid = state.currentSsid,
            currentIp = state.currentIp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${state.networks.size} networks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { viewModel.scan() }, enabled = permissionGranted) {
                if (state.scanning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Scan")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 0.dp, 16.dp, 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.networks, key = { it.bssid.ifEmpty { it.ssid } + it.ssid }) { network ->
                NetworkRow(
                    network = network,
                    isCurrent = network.ssid == state.currentSsid,
                    isConnecting = network.ssid == state.connectingSsid,
                    onClick = {
                        when {
                            network.security == com.qcenaman.netscope.model.SecurityType.ENTERPRISE ->
                                scope.launch { snackbar.showSnackbar("Enterprise networks are not supported yet") }
                            network.isSecured -> pendingNetwork = network
                            else -> viewModel.connect(network.ssid, "")
                        }
                    }
                )
            }
        }
    }

    pendingNetwork?.let { network ->
        PasswordDialog(
            network = network,
            connecting = network.ssid == state.connectingSsid,
            onConnect = { password ->
                viewModel.connect(network.ssid, password)
                pendingNetwork = null
            },
            onDismiss = { pendingNetwork = null }
        )
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Location / Wi-Fi permission needed",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Android requires this permission to see available Wi-Fi networks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequest) { Text("Grant permission") }
        }
    }
}

@Composable
private fun StatusCard(
    wifiEnabled: Boolean,
    currentSsid: String?,
    currentIp: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (wifiEnabled) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                contentDescription = null,
                tint = if (wifiEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = if (currentSsid != null) currentSsid else "Not connected",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (currentIp != null) "IP $currentIp" else "Wi-Fi " +
                        if (wifiEnabled) "on" else "off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NetworkRow(
    network: WifiNetwork,
    isCurrent: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    val signalIcon: ImageVector = when (signalQuality(network.level)) {
        4 -> Icons.Filled.Wifi
        3 -> Icons.Filled.Wifi2Bar
        2 -> Icons.Filled.Wifi1Bar
        1 -> Icons.Filled.Wifi1Bar
        else -> Icons.Filled.WifiOff
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = signalIcon,
                contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = network.ssid,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.NetworkCheck,
                            contentDescription = "Connected",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = buildString {
                        append(network.security.label)
                        if (network.bssid.isNotEmpty()) append("  ·  ${network.bssid}")
                        append("  ·  ${network.level} dBm")
                        if (network.frequencyMHz >= 5000) append("  ·  5 GHz")
                        else if (network.frequencyMHz > 0) append("  ·  2.4 GHz")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (network.isSecured) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Secured",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    Icons.Filled.LockOpen,
                    contentDescription = "Open",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PasswordDialog(
    network: WifiNetwork,
    connecting: Boolean,
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!connecting) onDismiss() },
        title = { Text("Connect to ${network.ssid}") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConnect(password) }, enabled = !connecting) {
                Text(if (connecting) "Connecting…" else "Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !connecting) { Text("Cancel") }
        }
    )
}
