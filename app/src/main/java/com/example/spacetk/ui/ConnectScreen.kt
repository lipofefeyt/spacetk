package com.example.spacetk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.spacetk.yamcs.YamcsInstanceInfo

sealed interface ConnectStatus {
    data object Idle : ConnectStatus
    data object Connecting : ConnectStatus
    data class Connected(val info: YamcsInstanceInfo) : ConnectStatus
    data class Error(val message: String) : ConnectStatus
}

@Composable
fun ConnectScreen(
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    status: ConnectStatus,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("SpaceTK — YAMCS client", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Point this at a laptop running opensvf's scripts/start-yamcs.sh + " +
                "scripts/demo_yamcs.py. Username/password are only needed if that instance has " +
                "auth enabled.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("Host") },
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password (optional)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onConnect,
            enabled = status !is ConnectStatus.Connecting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Connect")
        }

        when (status) {
            is ConnectStatus.Connecting -> CircularProgressIndicator()
            is ConnectStatus.Connected -> ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Connected to '${status.info.name}'" + (status.info.state?.let { " ($it)" } ?: ""))
                    Text("Parameters: ${status.info.parameterCount ?: "unknown"}")
                    Text("Commands: ${status.info.commandCount ?: "unknown"}")
                }
            }
            is ConnectStatus.Error -> Text(
                "Error: ${status.message}",
                color = MaterialTheme.colorScheme.error,
            )
            is ConnectStatus.Idle -> {}
        }
    }
}
