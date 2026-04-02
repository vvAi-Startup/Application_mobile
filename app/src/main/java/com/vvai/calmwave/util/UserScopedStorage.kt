package com.vvai.calmwave.util

import android.content.Context
import java.io.File

fun getUserScopeId(context: Context): String {
    val prefs = getSecureAuthPrefs(context)

    if (prefs.contains("user_id")) {
        val id = prefs.getLong("user_id", -1L)
        if (id > 0L) return "uid_$id"
    }

    val email = prefs.getString("user_email", null)
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("[^a-z0-9]"), "_")
        ?.trim('_')

    return if (!email.isNullOrBlank()) "email_$email" else "guest"
}

fun getUserAudioDir(context: Context): File {
    val base = context.getExternalFilesDir(null) ?: context.filesDir
    val dir = File(base, "audios/${getUserScopeId(context)}")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

fun getUserScopedKey(context: Context, baseKey: String): String {
    return "${baseKey}_${getUserScopeId(context)}"
}
