package com.example.spacetk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spacetk.serial.TargetProfile
import com.example.spacetk.serial.UsbConnectionState
import com.hoho.android.usbserial.driver.UsbSerialDriver

@Composable
fun DeviceConnectScreen(
    drivers: List<UsbSerialDriver>,
    profiles: List<TargetProfile>,
    selectedProfile: TargetProfile,
    onProfileSelected: (TargetProfile) -> Unit,
    onRefresh: () -> Unit,
    onConnect: (UsbSerialDriver) -> Unit,
    connectionState: UsbConnectionState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("SpaceTK — bench serial", style = MaterialTheme.typography.headlineSmall)

        when (connectionState) {
            is UsbConnectionState.RequestingPermission ->
                Text("Waiting for USB permission…", style = MaterialTheme.typography.bodyMedium)
            is UsbConnectionState.Error ->
                Text(
                    "Error: ${connectionState.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            else -> {}
        }

        Text("Target profile", style = MaterialTheme.typography.titleMedium)
        Column {
            profiles.forEach { profile ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = profile == selectedProfile,
                        onClick = { onProfileSelected(profile) },
                    )
                    Text("${profile.name} @ ${profile.baudRate} baud")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("USB devices", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }

        if (drivers.isEmpty()) {
            Text("No USB-serial device found. Plug one in via OTG and tap Refresh.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(drivers) { driver ->
                    ElevatedCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(driver.device.deviceName)
                                Text(
                                    "VID ${driver.device.vendorId} / PID ${driver.device.productId}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Button(onClick = { onConnect(driver) }) { Text("Connect") }
                        }
                    }
                }
            }
        }
    }
}
