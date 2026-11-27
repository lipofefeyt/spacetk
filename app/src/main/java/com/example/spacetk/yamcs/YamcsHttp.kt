package com.example.spacetk.yamcs

import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException

/**
 * Runs one HTTP call against [connection] and returns the response body. A malformed server
 * address (the host and port are typed by the user), a network or read error, and a non-2xx
 * status all come back as [YamcsResult.Failure] rather than being thrown.
 */
internal fun executeForBody(
    connection: YamcsConnection,
    httpClient: OkHttpClient,
    path: String,
    configure: Request.Builder.() -> Unit = {},
): YamcsResult<String> {
    val url = "${connection.baseUrl}$path".toHttpUrlOrNull()
        ?: return YamcsResult.Failure("Invalid server address: ${connection.baseUrl}")
    val request = Request.Builder().url(url).apply(configure).build()
    return try {
        httpClient.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (response.isSuccessful) {
                YamcsResult.Success(text)
            } else {
                YamcsResult.Failure("HTTP ${response.code} from $url: $text")
            }
        }
    } catch (e: IOException) {
        YamcsResult.Failure("Could not reach ${connection.baseUrl}: ${e.message}")
    }
}

/** Turns a response that isn't the expected JSON into a [YamcsResult.Failure]. */
internal inline fun <T> parsingJson(
    connection: YamcsConnection,
    parse: () -> YamcsResult<T>,
): YamcsResult<T> = try {
    parse()
} catch (e: JSONException) {
    YamcsResult.Failure("Unexpected response from ${connection.baseUrl}: ${e.message}")
}
