package com.vvai.calmwave.util

import android.media.MediaMetadataRetriever
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private data class AudioDurationEntry(
    val durationMs: Long,
    val lastModified: Long,
    val sizeBytes: Long
)

object AudioMetadataCache {
    private val durationCache = ConcurrentHashMap<String, AudioDurationEntry>()

    fun getDurationMs(file: File): Long {
        val path = file.absolutePath
        val lastModified = file.lastModified()
        val sizeBytes = file.length()

        val cached = durationCache[path]
        if (cached != null && cached.lastModified == lastModified && cached.sizeBytes == sizeBytes) {
            return cached.durationMs
        }

        val duration = resolveDurationInternal(path)
        durationCache[path] = AudioDurationEntry(duration, lastModified, sizeBytes)
        return duration
    }

    fun invalidateMissing(existingPaths: Set<String>) {
        durationCache.keys.removeIf { it !in existingPaths }
    }

    fun invalidate(filePath: String) {
        durationCache.remove(filePath)
    }

    fun clear() {
        durationCache.clear()
    }

    private fun resolveDurationInternal(filePath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: -1L
            if (duration in 1 until (1000L * 60L * 60L * 10L)) duration else -1L
        } catch (_: Exception) {
            -1L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}
