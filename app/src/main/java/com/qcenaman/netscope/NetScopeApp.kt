package com.qcenaman.netscope

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.qcenaman.netscope.ui.NetworkScreen
import com.qcenaman.netscope.ui.WifiScreen

private val tabs = listOf("Wi-Fi", "Network")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetScopeApp(permissionGranted: Boolean, onRequestPermission: () -> Unit) {
    NetScopeTheme {
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { TopAppBar(title = { Text("NetScope") }) }
        ) { padding ->
            Column(Modifier.padding(padding)) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = if (index == 0) Icons.Filled.Wifi else Icons.Filled.Lan,
                                    contentDescription = null
                                )
                            },
                            text = { Text(title) }
                        )
                    }
                }
                when (selectedTab) {
                    0 -> WifiScreen(
                        permissionGranted = permissionGranted,
                        onRequestPermission = onRequestPermission
                    )
                    else -> NetworkScreen()
                }
            }
        }
    }
}
