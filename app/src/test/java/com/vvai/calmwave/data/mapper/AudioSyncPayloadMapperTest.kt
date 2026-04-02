package com.vvai.calmwave.data.mapper

import com.vvai.calmwave.data.model.AudioProcessingMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudioSyncPayloadMapperTest {

    @Test
    fun `should map processed audio payload with metrics`() {
        val tempFile = File.createTempFile("processed_test", ".wav")
        tempFile.writeBytes(ByteArray(256) { 1 })

        val metrics = AudioProcessingMetrics(
            filename = tempFile.name,
            processingTimeMs = 1500L,
            recordingDurationMs = 10_000L,
            originalFileSizeBytes = 1024L,
            processedFileSizeBytes = tempFile.length(),
            wasOfflineProcessed = true
        )

        val payload = AudioSyncPayloadMapper.fromProcessedAudio(
            audioFile = tempFile,
            metrics = metrics,
            durationSeconds = 10.0,
            recordedAt = "2026-04-01T23:30:16Z",
            resolvedDeviceOrigin = "Android"
        )

        assertEquals(tempFile.name, payload.filename)
        assertEquals(10.0, payload.durationSeconds, 0.0)
        assertEquals(tempFile.length(), payload.sizeBytes)
        assertEquals("2026-04-01T23:30:16Z", payload.recordedAt)
        assertTrue(payload.processed)
        assertEquals(1500L, payload.processingTimeMs)
        assertEquals("Android", payload.deviceOrigin)
        assertEquals(false, payload.transcribed)
        assertNull(payload.transcriptionText)

        tempFile.delete()
    }

    @Test
    fun `should map payload without metrics`() {
        val tempFile = File.createTempFile("processed_test_no_metrics", ".wav")
        tempFile.writeBytes(ByteArray(128) { 2 })

        val payload = AudioSyncPayloadMapper.fromProcessedAudio(
            audioFile = tempFile,
            metrics = null,
            durationSeconds = -4.0,
            recordedAt = "2026-04-01T23:30:16Z",
            resolvedDeviceOrigin = "Moto G"
        )

        assertEquals(0.0, payload.durationSeconds, 0.0)
        assertNull(payload.processingTimeMs)
        assertEquals("Moto G", payload.deviceOrigin)

        tempFile.delete()
    }
}
