package com.vvai.calmwave.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo de resposta de login da API
 */
data class LoginResponse(
    val token: String,
    val user: User
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
