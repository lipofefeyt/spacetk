package com.example.spacetk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** One received chunk, rendered as a hex dump column and a printable-ASCII column. */
data class LogLine(val hex: String, val ascii: String) {
    companion object {
        fun fromBytes(bytes: ByteArray): LogLine {
            val hex = bytes.joinToString(" ") { byte -> "%02X".format(byte) }
            val ascii = bytes.joinToString("") { byte ->
                val c = byte.toInt() and 0xFF
                if (c in 32..126) c.toChar().toString() else "."
            }
            return LogLine(hex, ascii)
        }
    }
}

@Composable
fun TerminalScreen(
    profileName: String,
    lines: List<LogLine>,
    txValue: String,
    onTxValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Connected: $profileName", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onDisconnect) { Text("Disconnect") }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            items(lines) { line ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = line.hex,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(2f),
                    )
                    Text(
                        text = line.ascii,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = txValue,
                onValueChange = onTxValueChange,
                modifier = Modifier.weight(1f),
                label = { Text("Raw TX") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            Button(onClick = onSend) { Text("Send") }
        }
    }
}
