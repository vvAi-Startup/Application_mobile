package com.vvai.calmwave.data.auth

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.vvai.calmwave.data.model.LoginRequest
import com.vvai.calmwave.data.model.RegisterRequest
import com.vvai.calmwave.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Garante que o app tenha um token JWT mesmo sem login explícito.
 *
 * Estratégia (silenciosa):
 * - Se já existe token salvo, reutiliza.
 * - Caso contrário, cria credenciais locais (email/senha) e tenta `register`.
 * - Se já existir (409), faz `login`.
 */
object GuestSessionManager {

    private const val TAG = "GuestSessionManager"

    private const val PREFS_NAME = "calmwave_auth"
    private const val KEY_GUEST_EMAIL = "guest_email"
    private const val KEY_GUEST_PASSWORD = "guest_password"
    private const val KEY_ACCESS_TOKEN = "access_token"

    suspend fun ensureGuestSession(context: Context): String? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val existingToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        if (!existingToken.isNullOrBlank()) {
            ApiClient.setAuthToken(existingToken)
            return@withContext existingToken
        }

        val (email, password) = getOrCreateGuestCredentials(context)
        val api = ApiClient.getApiService()

        // 1) Tenta registrar (idempotente via fallback para login)
        try {
            val registerResp = api.register(
                RegisterRequest(
                    name = "Guest",
                    email = email,
                    password = password,
                    accountType = "free"
                )
            )

            if (registerResp.isSuccessful) {
                val token = registerResp.body()?.token
                if (!token.isNullOrBlank()) {
                    saveToken(prefs, token)
                    ApiClient.setAuthToken(token)
                    Log.i(TAG, "Guest registrado e token obtido")
                    return@withContext token
                }
            } else if (registerResp.code() != 409) {
                Log.w(TAG, "Register guest falhou: HTTP ${registerResp.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exceção ao registrar guest: ${e.message}")
        }

        // 2) Se já existe, tenta login
        try {
            val loginResp = api.login(LoginRequest(email = email, password = password))
            if (loginResp.isSuccessful) {
                val token = loginResp.body()?.token
                if (!token.isNullOrBlank()) {
                    saveToken(prefs, token)
                    ApiClient.setAuthToken(token)
                    Log.i(TAG, "Guest logado e token obtido")
                    return@withContext token
                }
            } else {
                Log.w(TAG, "Login guest falhou: HTTP ${loginResp.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exceção ao logar guest: ${e.message}")
        }

        null
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_GUEST_EMAIL)
            .remove(KEY_GUEST_PASSWORD)
            .apply()
        ApiClient.clear()
    }

    private fun saveToken(prefs: android.content.SharedPreferences, token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    private fun getOrCreateGuestCredentials(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val existingEmail = prefs.getString(KEY_GUEST_EMAIL, null)
        val existingPassword = prefs.getString(KEY_GUEST_PASSWORD, null)

        if (!existingEmail.isNullOrBlank() && !existingPassword.isNullOrBlank()) {
            return existingEmail to existingPassword
        }

        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        val seed = (androidId ?: UUID.randomUUID().toString())
            .replace(Regex("[^A-Za-z0-9]"), "")
            .take(24)
            .ifBlank { UUID.randomUUID().toString().replace("-", "").take(24) }

        val email = "guest_$seed@calmwave.local"
        val password = UUID.randomUUID().toString().replace("-", "")

        prefs.edit()
            .putString(KEY_GUEST_EMAIL, email)
            .putString(KEY_GUEST_PASSWORD, password)
            .apply()

        return email to password
    }
}
