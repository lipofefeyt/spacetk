package com.example.spacetk.yamcs

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class YamcsInstanceInfo(
    val name: String,
    val state: String?,
    val parameterCount: Int?,
    val commandCount: Int?,
)

sealed interface YamcsResult<out T> {
    data class Success<T>(val value: T) : YamcsResult<T>
    data class Failure(val message: String) : YamcsResult<Nothing>
}

/**
 * Minimal REST client proving connectivity to a YAMCS instance: optional auth (the opensvf demo
 * instance runs unauthenticated, see YamcsConnection) and GET /api/instances.
 */
class YamcsRestClient(
    private val connection: YamcsConnection,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private var bearerToken: String? = null

    suspend fun connectAndFetchInstance(): YamcsResult<YamcsInstanceInfo> = withContext(Dispatchers.IO) {
        if (connection.isAuthenticated) {
            when (val authResult = fetchBearerToken(connection, httpClient)) {
                is YamcsResult.Failure -> return@withContext authResult
                is YamcsResult.Success -> bearerToken = authResult.value
            }
        }
        fetchInstance(connection.instance)
    }

    private fun fetchInstance(name: String): YamcsResult<YamcsInstanceInfo> {
        val requestBuilder = Request.Builder().url("${connection.baseUrl}/api/instances")
        bearerToken?.let { requestBuilder.header("Authorization", "Bearer $it") }
        val response = try {
            httpClient.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            return YamcsResult.Failure("Could not reach ${connection.baseUrl}: ${e.message}")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                return YamcsResult.Failure("HTTP ${it.code} from ${connection.baseUrl}: $text")
            }
            val info = parseInstance(text, name)
                ?: return YamcsResult.Failure("Instance '$name' not found in response from ${connection.baseUrl}")
            return YamcsResult.Success(info)
        }
    }

    /**
     * GET /api/instances' exact shape wasn't pinned down byte-for-byte during the wire-format
     * spike (only that it carries parameterCount/commandCount per instance), so this tolerates
     * either a bare JSON array or an {"instances": [...]} wrapper, and either flat
     * parameterCount/commandCount fields or ones nested under "missionDatabase".
     */
    private fun parseInstance(json: String, name: String): YamcsInstanceInfo? {
        val array: JSONArray = if (json.trim().startsWith("[")) {
            JSONArray(json)
        } else {
            JSONObject(json).optJSONArray("instances") ?: return null
        }
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val entryName = entry.optString("name", entry.optString("instance", ""))
            if (entryName != name) continue
            val mdb = entry.optJSONObject("missionDatabase")
            return YamcsInstanceInfo(
                name = entryName,
                state = entry.optString("state").ifBlank { null },
                parameterCount = entry.optIntOrNull("parameterCount") ?: mdb?.optIntOrNull("parameterCount"),
                commandCount = entry.optIntOrNull("commandCount") ?: mdb?.optIntOrNull("commandCount"),
            )
        }
        return null
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
