package com.example.spacetk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.spacetk.serial.TargetProfile
import com.example.spacetk.serial.UsbConnectionState
import com.example.spacetk.serial.UsbSerialManager
import com.example.spacetk.ui.DeviceConnectScreen
import com.example.spacetk.ui.LogLine
import com.example.spacetk.ui.TerminalScreen
import com.example.spacetk.ui.theme.SpacetkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpacetkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpaceTkApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SpaceTkApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val usbSerialManager = remember { UsbSerialManager(context) }
    DisposableEffect(usbSerialManager) {
        onDispose { usbSerialManager.dispose() }
    }

    val connectionState by usbSerialManager.state.collectAsState()
    var drivers by remember { mutableStateOf(usbSerialManager.availableDrivers()) }
    var selectedProfile by remember { mutableStateOf(TargetProfile.builtIn.first()) }
    var txValue by remember { mutableStateOf("") }
    val logLines = remember { mutableStateListOf<LogLine>() }

    LaunchedEffect(usbSerialManager) {
        usbSerialManager.rxData.collect { bytes ->
            logLines.add(LogLine.fromBytes(bytes))
            if (logLines.size > 500) logLines.removeAt(0)
        }
    }

    when (val state = connectionState) {
        is UsbConnectionState.Connected -> {
            TerminalScreen(
                profileName = "${state.profile.name} (${state.deviceName})",
                lines = logLines,
                txValue = txValue,
                onTxValueChange = { txValue = it },
                onSend = {
                    if (txValue.isNotEmpty()) {
                        usbSerialManager.send(txValue.toByteArray())
                        txValue = ""
                    }
                },
                onDisconnect = {
                    usbSerialManager.disconnect()
                    logLines.clear()
                },
                modifier = modifier,
            )
        }
        else -> {
            DeviceConnectScreen(
                drivers = drivers,
                profiles = TargetProfile.builtIn,
                selectedProfile = selectedProfile,
                onProfileSelected = { selectedProfile = it },
                onRefresh = { drivers = usbSerialManager.availableDrivers() },
                onConnect = { driver -> usbSerialManager.connect(driver, selectedProfile) },
                connectionState = state,
                modifier = modifier,
            )
        }
    }
}
