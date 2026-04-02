package com.vvai.calmwave.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo de resposta de login da API
 */
data class LoginResponse(
    @SerializedName(value = "token", alternate = ["access_token", "accessToken"])
    val token: String,
    @SerializedName(value = "refresh_token", alternate = ["refreshToken"])
    val refreshToken: String? = null,
    @SerializedName(value = "expires_in", alternate = ["expiresIn"])
    val expiresInSeconds: Long? = null,
    @SerializedName(value = "expires_at", alternate = ["expiresAt"])
    val expiresAt: Long? = null,
    val user: User? = null
)

/**
 * Modelo de usuário
 */
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val active: Boolean = true,
    
    @SerializedName("account_type")
    val accountType: String = "free",
    
    @SerializedName("profile_photo_url")
    val profilePhotoUrl: String? = null,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    val settings: UserSettings? = null
)

/**
 * Configurações do usuário
 */
data class UserSettings(
    @SerializedName("dark_mode")
    val darkMode: Boolean = false,
    
    @SerializedName("notifications_enabled")
    val notificationsEnabled: Boolean = true,
    
    @SerializedName("auto_process_audio")
    val autoProcessAudio: Boolean = true,
    
    @SerializedName("audio_quality")
    val audioQuality: String = "high"
)

/**
 * Requisição de login
 */
data class LoginRequest(
    val email: String,
    val password: String
)

/**
 * Requisição de registro
 */
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    
    @SerializedName("account_type")
    val accountType: String = "free"
)
