package com.vvai.calmwave.util

import android.content.Context
import java.io.File

private const val PREFS_PLAYLISTS = "playlists_prefs"
private const val PREFS_AUDIO_DISPLAY_NAMES = "audioDisplayNames"

fun resolveAudioDisplayName(context: Context, audioPath: String?): String {
    if (audioPath.isNullOrBlank()) return "CalmWave"

    val fallback = File(audioPath).name.ifBlank { "CalmWave" }
    val prefs = context.getSharedPreferences(PREFS_PLAYLISTS, Context.MODE_PRIVATE)
    val key = getUserScopedKey(context, PREFS_AUDIO_DISPLAY_NAMES)
    val raw = prefs.getString(key, null).orEmpty()

    if (raw.isBlank()) return fallback

    val match = raw
        .split("||")
        .firstOrNull { entry ->
            val parts = entry.split("|", limit = 2)
            parts.size == 2 && parts[0] == audioPath
        }

    val custom = match
        ?.split("|", limit = 2)
        ?.getOrNull(1)
        ?.trim()

    return if (custom.isNullOrBlank()) fallback else custom
}
