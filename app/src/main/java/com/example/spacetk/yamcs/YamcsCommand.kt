package com.example.spacetk.yamcs

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class CommandAck(
    val commandName: String,
    val id: String?,
    val generationTime: String?,
)

/** A command available from the demo scenario, by its qualified name and required args. */
enum class DemoCommand(val qualifiedName: String, val argName: String? = null) {
    AreYouAlive("/opensvf/TC_17_1_AreYouAlive"),
    SetBdotGain("/opensvf/TC_20_1_SetBdotGain", argName = "value"),
    GetBdotGain("/opensvf/TC_20_3_GetBdotGain"),
}

/**
 * Issues a named YAMCS command. Path and body confirmed against a real running opensvf
 * instance: POST /api/processors/{instance}/{processor}/commands{qualifiedName}, body
 * {"args": {...}}.
 */
class YamcsCommand(
    private val connection: YamcsConnection,
    private val httpClient: OkHttpClient,
) {
    suspend fun issue(command: DemoCommand, argValue: Double? = null): YamcsResult<CommandAck> =
        withContext(Dispatchers.IO) {
            var bearerToken: String? = null
            if (connection.isAuthenticated) {
                when (val authResult = fetchBearerToken(connection, httpClient)) {
                    is YamcsResult.Failure -> return@withContext authResult
                    is YamcsResult.Success -> bearerToken = authResult.value
                }
            }

            val argsJson = JSONObject()
            if (command.argName != null && argValue != null) {
                argsJson.put(command.argName, argValue)
            }
            val requestBody = JSONObject().put("args", argsJson).toString()
                .toRequestBody("application/json".toMediaType())

            val url = "${connection.baseUrl}/api/processors/${connection.instance}/" +
                "${connection.processor}/commands${command.qualifiedName}"
            val requestBuilder = Request.Builder().url(url).post(requestBody)
            bearerToken?.let { requestBuilder.header("Authorization", "Bearer $it") }

            val response = try {
                httpClient.newCall(requestBuilder.build()).execute()
            } catch (e: IOException) {
                return@withContext YamcsResult.Failure("Could not reach ${connection.baseUrl}: ${e.message}")
            }
            response.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    return@withContext YamcsResult.Failure(
                        "HTTP ${it.code} issuing ${command.qualifiedName}: $text",
                    )
                }
                val json = JSONObject(text)
                YamcsResult.Success(
                    CommandAck(
                        commandName = json.optString("commandName", command.qualifiedName),
                        id = json.optString("id").ifBlank { null },
                        generationTime = json.optString("generationTime").ifBlank { null },
                    ),
                )
            }
        }
}
