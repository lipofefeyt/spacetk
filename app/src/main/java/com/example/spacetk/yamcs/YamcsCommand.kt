package com.example.spacetk.yamcs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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

            val path = "/api/processors/${connection.instance}/" +
                "${connection.processor}/commands${command.qualifiedName}"
            val result = executeForBody(connection, httpClient, path) {
                post(requestBody)
                bearerToken?.let { header("Authorization", "Bearer $it") }
            }
            when (result) {
                is YamcsResult.Failure -> YamcsResult.Failure(
                    "Could not issue ${command.qualifiedName}: ${result.message}",
                )
                is YamcsResult.Success -> parsingJson(connection) {
                    val json = JSONObject(result.value)
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
}
