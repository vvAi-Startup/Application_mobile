package com.vvai.calmwave.data.remote

import android.content.Context
import com.vvai.calmwave.Config
import com.vvai.calmwave.util.ResilientDns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente Retrofit para comunicação com a API CalmWave
 */
object ApiClient {
    
    private var authToken: String? = null
    private var retrofit: Retrofit? = null
    
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
            
            // Se temos token, adiciona ao header
            val requestBuilder = originalRequest.newBuilder()
            authToken?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }
            
            chain.proceed(requestBuilder.build())
        }
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
}
