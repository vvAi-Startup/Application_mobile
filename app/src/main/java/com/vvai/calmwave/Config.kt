package com.vvai.calmwave

object Config {
    val apiBaseUrl: String get() = BuildConfig.API_BASE_URL.trimEnd('/')
    val wsBaseUrl: String get() = BuildConfig.WS_BASE_URL.trimEnd('/')
    val dbBaseUrl: String get() = BuildConfig.DB_BASE_URL.trimEnd('/')

    // REST endpoints
    val uploadUrl: String get() = "$apiBaseUrl/upload"
    val healthUrl: String get() = "$apiBaseUrl/health"

    // WebSocket endpoints
    val wsStreamUrl: String get() = "$wsBaseUrl/stream"
}