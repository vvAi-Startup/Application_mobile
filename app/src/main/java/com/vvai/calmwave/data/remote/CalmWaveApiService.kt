package com.vvai.calmwave.data.remote

import com.vvai.calmwave.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Interface da API CalmWave
 * Define todos os endpoints REST do backend
 */
interface CalmWaveApiService {
    
    // ========== Autenticação ==========
    
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/login")
    suspend fun loginNoApiPrefix(@Body request: LoginRequest): Response<LoginResponse>
    
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<LoginResponse>
    
    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<User>
    
    // ========== Analytics & Events ==========
    
    /**
     * Envia um evento de analytics para o backend
     */
    @POST("api/events/")
    suspend fun sendEvent(@Body event: Map<String, Any?>): Response<Map<String, Any>>
    
    /**
     * Lista eventos
     */
    @GET("api/events/")
    suspend fun getEvents(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
        @Query("level") level: String? = null
    ): Response<Map<String, Any>>
    
    // ========== Áudios ==========
    
    /**
     * Sincroniza metadados do áudio (JSON).
     * Observação: o backend em produção pode estar exigindo multipart em /audios/sync;
     * para isso use syncAudioMultipart.
     */
    @POST("api/audios/sync")
    suspend fun syncAudioMetadataJson(
        @Body data: AudioSyncRequest
    ): Response<Map<String, Any>>

    /**
     * Sincronização via multipart (compatível com handler que exige o arquivo em `file`).
     */
    @Multipart
    @POST("api/audios/sync")
    suspend fun syncAudioMultipart(
        @Part file: MultipartBody.Part,
        @Part("device_origin") deviceOrigin: RequestBody? = null,
        @Part("duration_seconds") durationSeconds: RequestBody? = null,
        @Part("processing_time_ms") processingTimeMs: RequestBody? = null,
        @Part("transcription_text") transcriptionText: RequestBody? = null,
        @Part processedFile: MultipartBody.Part? = null
    ): Response<Map<String, Any>>
    
    /**
     * Lista áudios do usuário
     */
    @GET("api/audios")
    suspend fun getAudios(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("processed") processed: Boolean? = null,
        @Query("favorite") favorite: Boolean? = null
    ): Response<Map<String, Any>>
    
    /**
     * Obtém informações de um áudio específico
     */
    @GET("api/audios/{audio_id}")
    suspend fun getAudio(@Path("audio_id") audioId: Long): Response<Map<String, Any>>
    
    /**
     * Atualiza informações de um áudio
     */
    @PUT("api/audios/{audio_id}")
    suspend fun updateAudio(
        @Path("audio_id") audioId: Long,
        @Body data: Map<String, Any?>
    ): Response<Map<String, Any>>
    
    /**
     * Deleta um áudio
     */
    @DELETE("api/audios/{audio_id}")
    suspend fun deleteAudio(@Path("audio_id") audioId: Long): Response<Map<String, Any>>
    
    // ========== Estatísticas ==========
    
    /**
     * Obtém estatísticas do dashboard
     */
    @GET("api/stats/dashboard")
    suspend fun getDashboardStats(): Response<Map<String, Any>>
    
    /**
     * Obtém analytics
     */
    @GET("api/stats/analytics")
    suspend fun getAnalytics(): Response<Map<String, Any>>
    
    // ========== Playlists ==========
    
    @GET("api/playlists")
    suspend fun getPlaylists(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): Response<Map<String, Any>>
    
    @POST("api/playlists")
    suspend fun createPlaylist(@Body data: Map<String, Any?>): Response<Map<String, Any>>
    
    // ========== Usuário ==========
    
    @PUT("api/users/me/settings")
    suspend fun updateSettings(@Body settings: Map<String, Any?>): Response<Map<String, Any>>
    
    @GET("api/users/me/achievements")
    suspend fun getAchievements(): Response<Map<String, Any>>
    
    // ========== Health Check ==========
    
    @GET("health")
    suspend fun healthCheck(): Response<Map<String, Any>>
}
