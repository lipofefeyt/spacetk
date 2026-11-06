package com.example.spacetk.yamcs

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

data class ParameterUpdate(val name: String, val value: Double?)

sealed interface WsConnectionState {
    data object Disconnected : WsConnectionState
    data object Connecting : WsConnectionState
    data object Connected : WsConnectionState
    data class Error(val message: String) : WsConnectionState
}

/**
 * YAMCS's WebSocket API is not a plain ad-hoc JSON API: it's the protobuf
 * ClientMessage/ServerMessage envelope in proto3 JSON form (with "@type" discriminators),
 * confirmed by probing a real running instance -- still plain-text JSON on the wire
 * (subprotocol "json"), so a normal JSON parser works as long as it matches this exact shape.
 * See the plan for the captured subscribe/update message examples this is built from.
 */
class YamcsWebSocketClient(
    private val connection: YamcsConnection,
    private val httpClient: OkHttpClient,
) {
    private var webSocket: WebSocket? = null
    private var nextCallId = 1
    private val numericIdToName = mutableMapOf<Int, String>()

    private val _updates = MutableSharedFlow<ParameterUpdate>(extraBufferCapacity = 64)
    val updates: SharedFlow<ParameterUpdate> = _updates.asSharedFlow()

    private val _connectionState = MutableStateFlow<WsConnectionState>(WsConnectionState.Disconnected)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    fun connectAndSubscribe(parameterNames: List<String>) {
        val wsUrl = connection.baseUrl
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://") + "/api/websocket"
        val request = Request.Builder()
            .url(wsUrl)
            .header("Sec-WebSocket-Protocol", "json")
            .build()
        _connectionState.value = WsConnectionState.Connecting
        webSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionState.value = WsConnectionState.Connected
                    sendSubscribe(webSocket, parameterNames)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connectionState.value = WsConnectionState.Error(t.message ?: "WebSocket failure")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionState.value = WsConnectionState.Disconnected
                }
            },
        )
    }

    private fun sendSubscribe(webSocket: WebSocket, parameterNames: List<String>) {
        val idArray = JSONArray()
        parameterNames.forEach { name -> idArray.put(JSONObject().put("name", name)) }
        val options = JSONObject()
            .put("@type", "type.googleapis.com/yamcs.protobuf.processing.SubscribeParametersRequest")
            .put("instance", connection.instance)
            .put("processor", connection.processor)
            .put("id", idArray)
            .put("abortOnInvalid", false)
        val message = JSONObject()
            .put("type", "parameters")
            .put("id", nextCallId++)
            .put("options", options)
        webSocket.send(message.toString())
    }

    private fun handleMessage(text: String) {
        val json = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        if (json.optString("type") != "parameters") return
        val data = json.optJSONObject("data") ?: return

        data.optJSONObject("mapping")?.let { mapping ->
            val keys = mapping.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val name = mapping.optJSONObject(key)?.optString("name") ?: continue
                key.toIntOrNull()?.let { numericIdToName[it] = name }
            }
        }

        val values = data.optJSONArray("values") ?: return
        for (i in 0 until values.length()) {
            val value = values.optJSONObject(i) ?: continue
            val numericId = value.optInt("numericId", -1)
            val name = numericIdToName[numericId] ?: continue
            val numeric = (value.optJSONObject("engValue") ?: value.optJSONObject("rawValue"))
                ?.let(::extractNumeric)
            _updates.tryEmit(ParameterUpdate(name, numeric))
        }
    }

    fun disconnect() {
        webSocket?.close(1000, null)
        webSocket = null
        numericIdToName.clear()
        _connectionState.value = WsConnectionState.Disconnected
    }
}

private fun extractNumeric(value: JSONObject): Double? {
    val keys = listOf(
        "floatValue", "doubleValue",
        "sint32Value", "sint64Value", "uint32Value", "uint64Value",
        "int32Value", "int64Value",
    )
    for (key in keys) {
        if (value.has(key)) return value.optDouble(key).takeIf { !it.isNaN() }
    }
    return null
}
