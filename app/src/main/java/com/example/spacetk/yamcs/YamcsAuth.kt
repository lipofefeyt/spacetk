package com.example.spacetk.yamcs

import java.io.IOException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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
    val request = Request.Builder()
        .url("${connection.baseUrl}/auth/token")
        .post(body)
        .build()
    val response = try {
        httpClient.newCall(request).execute()
    } catch (e: IOException) {
        return YamcsResult.Failure("Could not reach ${connection.baseUrl}: ${e.message}")
    }
    response.use {
        val text = it.body?.string().orEmpty()
        if (!it.isSuccessful) {
            return YamcsResult.Failure("Authentication failed (HTTP ${it.code}): $text")
        }
        val token = JSONObject(text).optString("access_token").ifBlank { null }
        return if (token == null) {
            YamcsResult.Failure("Authentication response had no access_token")
        } else {
            YamcsResult.Success(token)
        }
    }
}
