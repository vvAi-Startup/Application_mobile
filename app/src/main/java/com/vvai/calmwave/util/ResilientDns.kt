package com.vvai.calmwave.util

import android.util.Log
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * DNS resiliente para reduzir falhas intermitentes de resolução em algumas redes Android.
 *
 * Estratégia:
 * 1) tenta DNS do sistema
 * 2) se falhar para host conhecido, usa IPs fallback
 */
object ResilientDns : Dns {

    private const val TAG = "ResilientDns"

    private val fallbackIpByHost: Map<String, List<String>> = mapOf(
        "calm-wave-backend.onrender.com" to listOf(
            "216.24.57.251",
            "216.24.57.7"
        )
    )

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            val fallbackIps = fallbackIpByHost[hostname]
            if (fallbackIps.isNullOrEmpty()) throw e

            val addresses = fallbackIps.mapNotNull { ip ->
                runCatching { InetAddress.getByName(ip) }
                    .onFailure { err -> Log.w(TAG, "IP fallback inválido ($ip): ${err.message}") }
                    .getOrNull()
            }

            if (addresses.isNotEmpty()) {
                Log.w(TAG, "DNS do sistema falhou para $hostname. Usando fallback IP.")
                return addresses
            }

            throw e
        }
    }
}
