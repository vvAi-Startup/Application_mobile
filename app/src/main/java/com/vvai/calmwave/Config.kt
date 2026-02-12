package com.vvai.calmwave

object Config {
    /**
     * Modo offline: todo o processamento de áudio (denoising) é feito
     * localmente no dispositivo usando o modelo ONNX.
     * Quando true, nenhuma conexão de rede é necessária para gravar e processar.
     */
    val offlineMode: Boolean = true

    val apiBaseUrl: String get() = BuildConfig.API_BASE_URL.trimEnd('/')
    val wsBaseUrl: String get() = BuildConfig.WS_BASE_URL.trimEnd('/')
    val dbBaseUrl: String get() = BuildConfig.DB_BASE_URL.trimEnd('/')

    // REST endpoints (usados apenas quando offlineMode = false)
    val uploadUrl: String get() = "$apiBaseUrl/upload"
    val transcriptionUrl: String get() = "$apiBaseUrl/api/v1/audio/transcricao"
    val healthUrl: String get() = "$apiBaseUrl/health"

    // WebSocket endpoints (usados apenas quando offlineMode = false)
    val wsStreamUrl: String get() = "$wsBaseUrl/api/v1/streaming/ws/audio-streaming"
}