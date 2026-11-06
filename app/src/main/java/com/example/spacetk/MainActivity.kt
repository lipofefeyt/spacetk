package com.example.spacetk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.spacetk.ui.ConnectScreen
import com.example.spacetk.ui.ConnectStatus
import com.example.spacetk.ui.DashboardScreen
import com.example.spacetk.ui.theme.SpacetkTheme
import com.example.spacetk.yamcs.YamcsConnection
import com.example.spacetk.yamcs.YamcsRestClient
import com.example.spacetk.yamcs.YamcsResult
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

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
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8090") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<ConnectStatus>(ConnectStatus.Idle) }
    var activeConnection by remember { mutableStateOf<YamcsConnection?>(null) }
    val httpClient = remember { OkHttpClient() }
    val coroutineScope = rememberCoroutineScope()

    val connection = activeConnection
    if (status is ConnectStatus.Connected && connection != null) {
        DashboardScreen(
            connection = connection,
            httpClient = httpClient,
            onDisconnect = {
                status = ConnectStatus.Idle
                activeConnection = null
            },
            modifier = modifier,
        )
    } else {
        ConnectScreen(
            host = host,
            onHostChange = { host = it },
            port = port,
            onPortChange = { port = it },
            username = username,
            onUsernameChange = { username = it },
            password = password,
            onPasswordChange = { password = it },
            status = status,
            onConnect = {
                val newConnection = YamcsConnection(
                    baseUrl = "http://$host:$port",
                    username = username.ifBlank { null },
                    password = password.ifBlank { null },
                )
                activeConnection = newConnection
                status = ConnectStatus.Connecting
                coroutineScope.launch {
                    status = when (val result = YamcsRestClient(newConnection, httpClient).connectAndFetchInstance()) {
                        is YamcsResult.Success -> ConnectStatus.Connected(result.value)
                        is YamcsResult.Failure -> ConnectStatus.Error(result.message)
                    }
                }
            },
            modifier = modifier,
        )
    }
}
