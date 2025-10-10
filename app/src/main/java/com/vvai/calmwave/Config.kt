package com.vvai.calmwave

object Config {
    val apiBaseUrl: String get() = BuildConfig.API_BASE_URL.trimEnd('/')
    val wsBaseUrl: String get() = BuildConfig.WS_BASE_URL.trimEnd('/')
    val dbBaseUrl: String get() = BuildConfig.DB_BASE_URL.trimEnd('/')

    // REST endpoints
    val uploadUrl: String get() = "$apiBaseUrl/upload" // Endpoint legado
    val transcriptionUrl: String get() = "$apiBaseUrl/api/v1/audio/transcricao" // Endpoint de transcrição Whisper
    val healthUrl: String get() = "$apiBaseUrl/health"

    // WebSocket endpoints
    val wsStreamUrl: String get() = "$wsBaseUrl/api/v1/streaming/ws/audio-streaming"
}