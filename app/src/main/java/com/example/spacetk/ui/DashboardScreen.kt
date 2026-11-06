package com.example.spacetk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spacetk.yamcs.DemoCommand
import com.example.spacetk.yamcs.YamcsCommand
import com.example.spacetk.yamcs.YamcsConnection
import com.example.spacetk.yamcs.YamcsResult
import com.example.spacetk.yamcs.YamcsWebSocketClient
import com.example.spacetk.yamcs.WsConnectionState
import kotlin.math.sqrt
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

private const val SPARKLINE_MAX_SAMPLES = 60

// Dataviz skill reference palette: categorical slot 1 / sequential default hue (blue).
private val AccentLight = Color(0xFF2A78D6)
private val AccentDark = Color(0xFF3987E5)

private val DIPOLE_PARAMETERS = listOf(
    "/opensvf/mtq_dipole_x",
    "/opensvf/mtq_dipole_y",
    "/opensvf/mtq_dipole_z",
)

@Composable
fun DashboardScreen(
    connection: YamcsConnection,
    httpClient: OkHttpClient,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wsClient = remember(connection) { YamcsWebSocketClient(connection, httpClient) }
    DisposableEffect(wsClient) {
        wsClient.connectAndSubscribe(
            DIPOLE_PARAMETERS + listOf("/opensvf/omega_norm", "/opensvf/bdot_gain"),
        )
        onDispose { wsClient.disconnect() }
    }
    val wsState by wsClient.connectionState.collectAsState()
    val yamcsCommand = remember(connection) { YamcsCommand(connection, httpClient) }
    val commandLog = remember { mutableStateListOf<CommandLogEntry>() }
    val coroutineScope = rememberCoroutineScope()

    var dipoleX by remember { mutableStateOf<Double?>(null) }
    var dipoleY by remember { mutableStateOf<Double?>(null) }
    var dipoleZ by remember { mutableStateOf<Double?>(null) }
    var omegaNorm by remember { mutableStateOf<Double?>(null) }
    var bdotGain by remember { mutableStateOf<Double?>(null) }
    val magnitudeHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(wsClient) {
        wsClient.updates.collect { update ->
            when (update.name) {
                "/opensvf/mtq_dipole_x" -> dipoleX = update.value
                "/opensvf/mtq_dipole_y" -> dipoleY = update.value
                "/opensvf/mtq_dipole_z" -> dipoleZ = update.value
                "/opensvf/omega_norm" -> omegaNorm = update.value
                "/opensvf/bdot_gain" -> bdotGain = update.value
            }
            val x = dipoleX
            val y = dipoleY
            val z = dipoleZ
            if (update.name in DIPOLE_PARAMETERS && x != null && y != null && z != null) {
                magnitudeHistory.add(sqrt(x * x + y * y + z * z).toFloat())
                if (magnitudeHistory.size > SPARKLINE_MAX_SAMPLES) magnitudeHistory.removeAt(0)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Connected to '${connection.instance}'", style = MaterialTheme.typography.titleMedium)
                Text(wsState.label(), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDisconnect) { Text("Disconnect") }
        }

        DipoleMagnitudeTile(history = magnitudeHistory)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                label = "omega_norm (known upstream issue: may read 0)",
                value = omegaNorm?.let { "%.4f".format(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "bdot_gain",
                value = bdotGain?.let { "%.0f".format(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }

        CommandScreen(
            onIssueCommand = { command, argValue ->
                coroutineScope.launch {
                    val label = command.name + (argValue?.let { " ($it)" } ?: "")
                    when (val result = yamcsCommand.issue(command, argValue)) {
                        is YamcsResult.Success -> commandLog.add(
                            CommandLogEntry(
                                label = label,
                                outcome = "Accepted: id=${result.value.id}, at ${result.value.generationTime}",
                                isError = false,
                            ),
                        )
                        is YamcsResult.Failure -> commandLog.add(
                            CommandLogEntry(label = label, outcome = result.message, isError = true),
                        )
                    }
                }
            },
            results = commandLog,
        )
    }
}

private fun WsConnectionState.label(): String = when (this) {
    WsConnectionState.Disconnected -> "Disconnected"
    WsConnectionState.Connecting -> "Connecting…"
    WsConnectionState.Connected -> "Live"
    is WsConnectionState.Error -> "Error: $message"
}

@Composable
private fun DipoleMagnitudeTile(history: List<Float>) {
    val isDark = isSystemInDarkTheme()
    val accent = if (isDark) AccentDark else AccentLight
    val surface = MaterialTheme.colorScheme.surface

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("MTQ dipole magnitude", style = MaterialTheme.typography.labelMedium)
            Text(
                text = history.lastOrNull()?.let { "%.4f".format(it) } ?: "—",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Sparkline(
                values = history,
                lineColor = accent,
                surfaceColor = surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Per the dataviz skill: a stat tile's trend is a sparkline, not a full axed chart. Single
 * series (no legend needed), 2dp line with round caps, a ~10%-opacity area fill below it, and
 * an end marker (>=8dp) with a surface-color ring so it reads clearly over the line.
 */
@Composable
private fun Sparkline(
    values: List<Float>,
    lineColor: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val maxValue = (values.max()).coerceAtLeast(0.0001f)
        val stepX = size.width / (values.size - 1)

        fun yOf(value: Float) = size.height - (value / maxValue) * size.height

        val linePath = Path().apply {
            values.forEachIndexed { index, value ->
                val point = Offset(index * stepX, yOf(value))
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo((values.size - 1) * stepX, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(fillPath, color = lineColor.copy(alpha = 0.10f))
        drawPath(
            linePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val lastPoint = Offset((values.size - 1) * stepX, yOf(values.last()))
        drawCircle(color = surfaceColor, radius = 6.dp.toPx(), center = lastPoint)
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = lastPoint)
    }
}
