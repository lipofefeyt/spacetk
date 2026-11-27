package com.example.spacetk.yamcs

import okhttp3.FormBody
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * POST /auth/token (password grant), shared by YamcsRestClient and YamcsCommand. The opensvf
 * demo instance runs unauthenticated (see YamcsConnection), so this is only exercised when the
 * user supplies credentials for a deployment that has auth enabled.
 */
internal fun fetchBearerToken(connection: YamcsConnection, httpClient: OkHttpClient): YamcsResult<String> {
    val body = FormBody.Builder()
        .add("grant_type", "password")
        .add("username", connection.username.orEmpty())
        .add("password", connection.password.orEmpty())
        .build()
    val result = executeForBody(connection, httpClient, "/auth/token") { post(body) }
    return when (result) {
        is YamcsResult.Failure -> YamcsResult.Failure("Authentication failed: ${result.message}")
        is YamcsResult.Success -> parsingJson(connection) {
            val token = JSONObject(result.value).optString("access_token").ifBlank { null }
            if (token == null) {
                YamcsResult.Failure("Authentication response had no access_token")
            } else {
                YamcsResult.Success(token)
            }
        }
    }
}
