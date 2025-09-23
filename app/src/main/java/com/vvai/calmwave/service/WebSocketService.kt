package com.vvai.calmwave.service

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class WebSocketService(private val client: OkHttpClient) {
    interface Listener {
        fun onOpen()
        fun onTextMessage(text: String)
        fun onClosed(code: Int, reason: String)
        fun onFailure(t: Throwable)
    }

    private var webSocket: WebSocket? = null

    fun connect(url: String, listener: Listener) {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                listener.onOpen()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                listener.onTextMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: okhttp3.Response?) {
                listener.onFailure(t)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                listener.onClosed(code, reason)
            }
        })
    }

    fun sendText(text: String) {
        webSocket?.send(text)
    }

    fun close(code: Int = 1000, reason: String = "Normal closure") {
        try { webSocket?.close(code, reason) } catch (_: Exception) {}
        webSocket = null
    }
}