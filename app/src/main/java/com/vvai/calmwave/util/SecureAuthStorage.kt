package com.vvai.calmwave.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

private const val AUTH_PREFS_LEGACY = "calmwave_auth"
private const val AUTH_PREFS_SECURE = "calmwave_auth_secure"
private const val MIGRATION_DONE_KEY = "__migrated_v1"

const val KEY_ACCESS_TOKEN = "access_token"
const val KEY_REFRESH_TOKEN = "refresh_token"
const val KEY_ACCESS_TOKEN_EXPIRES_AT = "access_token_expires_at"
const val KEY_USER_NAME = "user_name"
const val KEY_USER_EMAIL = "user_email"
const val KEY_USER_ID = "user_id"

fun getSecureAuthPrefs(context: Context): SharedPreferences {
    val appContext = context.applicationContext
    val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val securePrefs = EncryptedSharedPreferences.create(
        appContext,
        AUTH_PREFS_SECURE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    if (!securePrefs.getBoolean(MIGRATION_DONE_KEY, false)) {
        migrateLegacyAuthPrefs(appContext, securePrefs)
    }

    return securePrefs
}

private fun migrateLegacyAuthPrefs(context: Context, securePrefs: SharedPreferences) {
    val legacy = context.getSharedPreferences(AUTH_PREFS_LEGACY, Context.MODE_PRIVATE)
    val editor = securePrefs.edit()

    legacy.all.forEach { (key, value) ->
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Boolean -> editor.putBoolean(key, value)
        }
    }

    editor.putBoolean(MIGRATION_DONE_KEY, true).apply()
    legacy.edit().clear().apply()
}

fun saveAuthSession(
    context: Context,
    accessToken: String,
    userName: String?,
    userEmail: String?,
    userId: Long?,
    refreshToken: String? = null,
    expiresInSeconds: Long? = null,
    expiresAtEpochMs: Long? = null
) {
    val prefs = getSecureAuthPrefs(context)
    val inferredExpiry = expiresAtEpochMs
        ?: expiresInSeconds?.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 1000 }
        ?: parseJwtExpToEpochMillis(accessToken)

    prefs.edit()
        .putString(KEY_ACCESS_TOKEN, accessToken)
        .putString(KEY_USER_NAME, userName)
        .putString(KEY_USER_EMAIL, userEmail)
        .putLong(KEY_USER_ID, userId ?: -1L)
        .apply {
            if (!refreshToken.isNullOrBlank()) putString(KEY_REFRESH_TOKEN, refreshToken)
            if (inferredExpiry != null) putLong(KEY_ACCESS_TOKEN_EXPIRES_AT, inferredExpiry)
        }
        .apply()
}

fun updateAccessToken(
    context: Context,
    accessToken: String,
    refreshToken: String? = null,
    expiresAtEpochMs: Long? = null
) {
    val prefs = getSecureAuthPrefs(context)
    val inferredExpiry = expiresAtEpochMs ?: parseJwtExpToEpochMillis(accessToken)

    prefs.edit()
        .putString(KEY_ACCESS_TOKEN, accessToken)
        .apply {
            if (!refreshToken.isNullOrBlank()) putString(KEY_REFRESH_TOKEN, refreshToken)
            if (inferredExpiry != null) putLong(KEY_ACCESS_TOKEN_EXPIRES_AT, inferredExpiry)
        }
        .apply()
}

fun clearAuthSession(context: Context) {
    val prefs = getSecureAuthPrefs(context)
    prefs.edit()
        .remove(KEY_ACCESS_TOKEN)
        .remove(KEY_REFRESH_TOKEN)
        .remove(KEY_ACCESS_TOKEN_EXPIRES_AT)
        .remove(KEY_USER_NAME)
        .remove(KEY_USER_EMAIL)
        .remove(KEY_USER_ID)
        .apply()
}

fun getAccessToken(context: Context): String? = getSecureAuthPrefs(context).getString(KEY_ACCESS_TOKEN, null)

fun getRefreshToken(context: Context): String? = getSecureAuthPrefs(context).getString(KEY_REFRESH_TOKEN, null)

fun isAccessTokenExpired(context: Context, skewMs: Long = 30_000L): Boolean {
    val prefs = getSecureAuthPrefs(context)
    val expiresAt = prefs.getLong(KEY_ACCESS_TOKEN_EXPIRES_AT, -1L)
    if (expiresAt <= 0L) return false
    return System.currentTimeMillis() + skewMs >= expiresAt
}

private fun parseJwtExpToEpochMillis(jwt: String): Long? {
    return runCatching {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val payload = String(payloadBytes)
        val expSeconds = JSONObject(payload).optLong("exp", -1L)
        if (expSeconds > 0) expSeconds * 1000 else null
    }.getOrNull()
}
