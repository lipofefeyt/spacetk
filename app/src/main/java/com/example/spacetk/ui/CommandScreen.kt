package com.example.spacetk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.spacetk.yamcs.DemoCommand

data class CommandLogEntry(val label: String, val outcome: String, val isError: Boolean)

/**
 * The three commands demo_yamcs.py exposes, with a confirmation dialog before any send -- this
 * drives a real running simulation. SetBdotGain's effect shows up by watching the bdot_gain
 * stat tile above update on the next telemetry message; no extra correlation plumbing needed
 * since both live on the same screen.
 */
@Composable
fun CommandScreen(
    onIssueCommand: (DemoCommand, Double?) -> Unit,
    results: List<CommandLogEntry>,
    modifier: Modifier = Modifier,
) {
    var gainInput by remember { mutableStateOf("") }
    var pendingCommand by remember { mutableStateOf<DemoCommand?>(null) }
    var pendingArgValue by remember { mutableStateOf<Double?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Commands", style = MaterialTheme.typography.titleMedium)

        OutlinedButton(
            onClick = { pendingCommand = DemoCommand.AreYouAlive; pendingArgValue = null },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Ping (TC_17_1_AreYouAlive)") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = gainInput,
                onValueChange = { gainInput = it },
                label = { Text("New bdot_gain") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val value = gainInput.toDoubleOrNull()
                    if (value != null) {
                        pendingCommand = DemoCommand.SetBdotGain
                        pendingArgValue = value
                    }
                },
                enabled = gainInput.toDoubleOrNull() != null,
            ) { Text("Set") }
        }

        OutlinedButton(
            onClick = { pendingCommand = DemoCommand.GetBdotGain; pendingArgValue = null },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Get bdot_gain (TC_20_3_GetBdotGain)") }

        results.asReversed().take(5).forEach { entry ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(entry.label, style = MaterialTheme.typography.labelMedium)
                    Text(
                        entry.outcome,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    val command = pendingCommand
    if (command != null) {
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text("Send command?") },
            text = {
                Text(
                    "This drives the real running simulation: ${command.qualifiedName}" +
                        (pendingArgValue?.let { " with value=$it" } ?: ""),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onIssueCommand(command, pendingArgValue)
                    pendingCommand = null
                    if (command == DemoCommand.SetBdotGain) gainInput = ""
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCommand = null }) { Text("Cancel") }
            },
        )
    }
}
