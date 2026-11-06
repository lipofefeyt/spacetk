package com.example.spacetk.yamcs

/**
 * Where to reach a YAMCS instance, and optional credentials.
 *
 * The opensvf demo instance (scripts/start-yamcs.sh + demo_yamcs.py) runs with auth disabled --
 * every /api/* call just works, no token needed. A production YAMCS deployment could still have
 * auth enabled, so username/password are supported but optional.
 */
data class YamcsConnection(
    val baseUrl: String, // e.g. "http://192.168.1.50:8090", no trailing slash
    val instance: String = "opensvf",
    val processor: String = "realtime",
    val username: String? = null,
    val password: String? = null,
) {
    val isAuthenticated: Boolean get() = !username.isNullOrBlank()
}
