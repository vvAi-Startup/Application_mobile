package com.vvai.calmwave.data.remote

import android.content.Context
import com.vvai.calmwave.Config
import com.vvai.calmwave.util.clearAuthSession
import com.vvai.calmwave.util.getAccessToken
import com.vvai.calmwave.util.getRefreshToken
import com.vvai.calmwave.util.isAccessTokenExpired
import com.vvai.calmwave.util.updateAccessToken
import com.vvai.calmwave.util.ResilientDns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente Retrofit para comunicação com a API CalmWave
 */
object ApiClient {
    
    private var authToken: String? = null
    @Volatile
    private var appContext: Context? = null
    private var retrofit: Retrofit? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val token = getAccessToken(context)
        if (!token.isNullOrBlank() && !isAccessTokenExpired(context)) {
            setAuthToken(token)
        } else if (!token.isNullOrBlank()) {
            clearAuthSession(context)
            clear()
        }
    }
    
    /**
     * Define o token de autenticação JWT
     */
    fun setAuthToken(token: String?) {
        authToken = token
        // Recriar retrofit com novo token
        retrofit = null
    }
    
    /**
     * Obtém token atual
     */
    fun getAuthToken(): String? = authToken
    
    /**
     * Cria interceptor de autenticação
     */
    private fun createAuthInterceptor(): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            val context = appContext

            // Se token local expirou, limpa imediatamente a sessão em memória.
            if (context != null && isAccessTokenExpired(context)) {
                clearAuthSession(context)
                clear()
            }

            if (authToken.isNullOrBlank() && context != null) {
                authToken = getAccessToken(context)
            }
            
            // Se temos token, adiciona ao header
            val requestBuilder = originalRequest.newBuilder()
            authToken?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }
            val authorizedRequest = requestBuilder.build()
            val response = chain.proceed(authorizedRequest)

            // Se recebeu 401, tenta refresh (quando backend suportar).
            if (response.code != 401) return@Interceptor response

            val refreshed = context?.let { refreshAccessTokenIfPossible(it) }
            if (refreshed.isNullOrBlank()) {
                context?.let { clearAuthSession(it) }
                clear()
                return@Interceptor response
            }

            response.close()
            val retry = originalRequest.newBuilder()
                .header("Authorization", "Bearer $refreshed")
                .build()
            chain.proceed(retry)
        }
    }

    private fun refreshAccessTokenIfPossible(context: Context): String? {
        val refreshToken = getRefreshToken(context) ?: return null

        val refreshBody = JSONObject()
            .put("refresh_token", refreshToken)
            .toString()
            .toRequestBody(CONTENT_TYPE_JSON)

        val refreshRequestCandidates = listOf(
            "${Config.apiBaseUrl}/api/auth/refresh",
            "${Config.apiBaseUrl}/auth/refresh"
        )

        val rawClient = OkHttpClient.Builder()
            .dns(ResilientDns)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        for (url in refreshRequestCandidates) {
            val request = Request.Builder()
                .url(url)
                .post(refreshBody)
                .build()

            runCatching {
                rawClient.newCall(request).execute().use { refreshResponse ->
                    if (!refreshResponse.isSuccessful) return@use
                    val body = refreshResponse.body?.string().orEmpty()
                    if (body.isBlank()) return@use

                    val json = JSONObject(body)
                    val newAccessToken = json.optString("access_token")
                        .ifBlank { json.optString("token") }
                        .ifBlank { json.optString("accessToken") }

                    if (newAccessToken.isBlank()) return@use

                    val newRefresh = json.optString("refresh_token").ifBlank { refreshToken }
                    val expiresIn = json.optLong("expires_in", -1L)
                    val expiresAt = when {
                        expiresIn > 0L -> System.currentTimeMillis() + expiresIn * 1000L
                        else -> json.optLong("expires_at", -1L)
                    }.takeIf { it > 0L }

                    updateAccessToken(
                        context = context,
                        accessToken = newAccessToken,
                        refreshToken = newRefresh,
                        expiresAtEpochMs = expiresAt
                    )
                    authToken = newAccessToken
                    retrofit = null
                    return newAccessToken
                }
            }
        }

        return null
    }
    
    /**
     * Cria OkHttpClient com interceptors
     */
    private fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .dns(ResilientDns)
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Obtém instância do Retrofit
     */
    private fun getRetrofit(): Retrofit {
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(Config.apiBaseUrl + "/") // Garante trailing slash
                .client(createOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }
    
    /**
     * Obtém serviço da API
     */
    fun getApiService(): CalmWaveApiService {
        return getRetrofit().create(CalmWaveApiService::class.java)
    }
    
    /**
     * Limpa instância (usado em logout)
     */
    fun clear() {
        authToken = null
        retrofit = null
    }

    private val CONTENT_TYPE_JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
}
