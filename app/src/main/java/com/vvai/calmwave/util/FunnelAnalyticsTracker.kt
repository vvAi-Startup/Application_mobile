package com.vvai.calmwave.util

import android.content.Context
import com.vvai.calmwave.data.repository.AnalyticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FunnelAnalyticsTracker {
    private const val PREFS = "calmwave_metrics"
    private const val KEY_FIRST_OPEN_AT = "first_open_at"
    private const val KEY_FIRST_OPEN_SENT = "first_open_sent"
    private const val KEY_D1_SENT = "retention_d1_sent"
    private const val KEY_D7_SENT = "retention_d7_sent"
    private const val KEY_FIRST_RECORDING_SENT = "first_recording_sent"

    suspend fun trackAppOpen(context: Context, repository: AnalyticsRepository) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val firstOpenAt = prefs.getLong(KEY_FIRST_OPEN_AT, -1L)

        if (firstOpenAt <= 0L) {
            prefs.edit().putLong(KEY_FIRST_OPEN_AT, now).apply()
        }

        if (!prefs.getBoolean(KEY_FIRST_OPEN_SENT, false)) {
            repository.logEvent(
                eventType = "FUNNEL_APP_FIRST_OPEN",
                details = mapOf("timestamp" to now),
                screen = "Application"
            )
            prefs.edit().putBoolean(KEY_FIRST_OPEN_SENT, true).apply()
        }

        val baseline = if (firstOpenAt > 0L) firstOpenAt else now
        val daysSinceFirstOpen = ((now - baseline) / (24L * 60L * 60L * 1000L)).coerceAtLeast(0L)

        if (daysSinceFirstOpen >= 1L && !prefs.getBoolean(KEY_D1_SENT, false)) {
            repository.logEvent(
                eventType = "RETENTION_D1",
                details = mapOf("days_since_first_open" to daysSinceFirstOpen),
                screen = "Application"
            )
            prefs.edit().putBoolean(KEY_D1_SENT, true).apply()
        }

        if (daysSinceFirstOpen >= 7L && !prefs.getBoolean(KEY_D7_SENT, false)) {
            repository.logEvent(
                eventType = "RETENTION_D7",
                details = mapOf("days_since_first_open" to daysSinceFirstOpen),
                screen = "Application"
            )
            prefs.edit().putBoolean(KEY_D7_SENT, true).apply()
        }
    }

    suspend fun trackSignupCompleted(
        context: Context,
        repository: AnalyticsRepository,
        userId: Long?
    ) {
        repository.logEvent(
            eventType = "FUNNEL_SIGNUP_COMPLETED",
            details = mapOf(
                "user_id" to userId,
                "has_user" to (userId != null && userId > 0L)
            ),
            screen = "CadastroActivity",
            userId = userId
        )
    }

    suspend fun trackFirstRecordingCompleted(
        context: Context,
        repository: AnalyticsRepository,
        recordingDurationMs: Long,
        processed: Boolean
    ) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FIRST_RECORDING_SENT, false)) return@withContext

        repository.logEvent(
            eventType = "FUNNEL_FIRST_RECORDING_COMPLETED",
            details = mapOf(
                "recording_duration_ms" to recordingDurationMs,
                "processed" to processed
            ),
            screen = "GravarActivity"
        )

        prefs.edit().putBoolean(KEY_FIRST_RECORDING_SENT, true).apply()
    }
}
