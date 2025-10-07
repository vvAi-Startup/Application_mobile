package com.vvai.calmwave

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class WavRecorder {
    // Definir as constantes em um objeto para fácil acesso e manutenção
    private object AudioConstants {
        val SAMPLE_RATE = 16000 // Taxa de amostragem (Hz)
        val NUM_CHANNELS = 1 // Mono
        val BITS_PER_SAMPLE = 16 // 16 bits
        val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecorder: AudioRecord? = null
    @Volatile private var isRecording = false
    @Volatile private var isPaused = false
    
    // Callback para enviar chunks em tempo real
    // Parâmetros: (chunk completo, índice, tamanho do overlap)
    private var chunkCallback: ((ByteArray, Int, Int) -> Unit)? = null
    
    // Configurações de chunking e overlap
    private object ChunkingConfig {
        const val CHUNK_INTERVAL_MS = 1000L // 1 segundo entre chunks
        const val OVERLAP_DURATION_MS = 45L // 50ms de overlap entre chunks
    }
    
    fun setChunkCallback(callback: (ByteArray, Int, Int) -> Unit) {
        chunkCallback = callback
    }
    
    /**
     * Calcula o tamanho do overlap em bytes baseado na duração em milliseconds
     */
    private fun calculateOverlapSize(overlapDurationMs: Long): Int {
        return (AudioConstants.SAMPLE_RATE * AudioConstants.NUM_CHANNELS * 
                AudioConstants.BITS_PER_SAMPLE / 8 * overlapDurationMs / 1000).toInt()
    }

    suspend fun startRecording(filePath: String) {
        val outputFile = File(filePath)
        isRecording = true
        isPaused = false

        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConstants.SAMPLE_RATE,
            AudioConstants.CHANNEL_CONFIG,
            AudioConstants.AUDIO_FORMAT
        )

        // Usar um buffer maior para maior estabilidade
        val bufferSize = minBufferSize * 2

        // Envolve a gravação em uma coroutine para que rode em background
        suspendCoroutine<Unit> { continuation ->
            try {
                audioRecorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AudioConstants.SAMPLE_RATE,
                    AudioConstants.CHANNEL_CONFIG,
                    AudioConstants.AUDIO_FORMAT,
                    bufferSize
                ).apply { startRecording() }

                FileOutputStream(outputFile).use { fileOutputStream ->
                    writeWavHeader(fileOutputStream)

                    val audioData = ByteArray(bufferSize)
                    var chunkIndex = 0
                    var accumulatedData = ByteArray(0)
                    var lastChunkTime = System.currentTimeMillis()
                    val chunkIntervalMs = ChunkingConfig.CHUNK_INTERVAL_MS
                    
                    // Buffer para overlap - dados que serão incluídos na próxima chunk
                    val overlapSizeBytes = calculateOverlapSize(ChunkingConfig.OVERLAP_DURATION_MS)
                    var overlapBuffer = ByteArray(0)

                    while (isRecording) {
                        if (isPaused) {
                            Thread.sleep(100)
                            continue
                        }
                        val bytesRead = audioRecorder?.read(audioData, 0, bufferSize) ?: 0
                        if (bytesRead > 0) {
                            fileOutputStream.write(audioData, 0, bytesRead)
                            
                             // Acumula dados para envio em chunks
                             accumulatedData += audioData.copyOf(bytesRead)
                             
                             val currentTime = System.currentTimeMillis()
                             
                             // Envia chunk a cada 1 segundo
                             if (currentTime - lastChunkTime >= chunkIntervalMs && accumulatedData.isNotEmpty()) {
                                 // Combina overlap da chunk anterior com os dados atuais
                                 val chunkToSend = if (overlapBuffer.isNotEmpty()) {
                                     overlapBuffer + accumulatedData
                                 } else {
                                     accumulatedData
                                 }
                                 
                                 println("WavRecorder: Enviando chunk $chunkIndex com ${chunkToSend.size} bytes")
                                 
                                 // Envia o chunk E o tamanho do overlap
                                 val overlapSizeToReport = if (chunkIndex == 0) 0 else overlapBuffer.size
                                 chunkCallback?.invoke(chunkToSend, chunkIndex, overlapSizeToReport)
                                 
                                 // Salva os últimos bytes para overlap da próxima chunk
                                 overlapBuffer = if (accumulatedData.size >= overlapSizeBytes) {
                                     accumulatedData.takeLast(overlapSizeBytes).toByteArray()
                                 } else {
                                     accumulatedData.copyOf()
                                 }
                                 
                                 chunkIndex++
                                 lastChunkTime = currentTime
                                 accumulatedData = ByteArray(0)
                             }
                         }
                     }
                    
                    // Envia qualquer dado restante com overlap
                    if (accumulatedData.isNotEmpty()) {
                        val finalChunk = if (overlapBuffer.isNotEmpty()) {
                            overlapBuffer + accumulatedData
                        } else {
                            accumulatedData
                        }
                        println("WavRecorder: Enviando chunk final $chunkIndex com ${finalChunk.size} bytes")
                        val overlapSizeToReport = if (chunkIndex == 0) 0 else overlapBuffer.size
                        chunkCallback?.invoke(finalChunk, chunkIndex, overlapSizeToReport)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Garantir que os recursos sejam liberados
                audioRecorder?.stop()
                audioRecorder?.release()
                audioRecorder = null

                // Atualizar o cabeçalho do arquivo WAV
                updateWavHeader(outputFile)

                continuation.resume(Unit)
            }
        }
    }

    fun stopRecording() {
        isRecording = false
    }

    fun pauseRecording() {
        isPaused = true
    }

    fun resumeRecording() {
        isPaused = false
    }

    private fun writeWavHeader(fileOutputStream: FileOutputStream) {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(0x46464952) // "RIFF"
        buffer.putInt(0) // Tamanho do arquivo, será atualizado
        buffer.putInt(0x45564157) // "WAVE"
        buffer.putInt(0x20746d66) // "fmt "
        buffer.putInt(16) // Tamanho do subchunk 1
        buffer.putShort(1) // Formato de áudio (1 = PCM)
        buffer.putShort(AudioConstants.NUM_CHANNELS.toShort())
        buffer.putInt(AudioConstants.SAMPLE_RATE)
        buffer.putInt(AudioConstants.SAMPLE_RATE * AudioConstants.NUM_CHANNELS * AudioConstants.BITS_PER_SAMPLE / 8)
        buffer.putShort((AudioConstants.NUM_CHANNELS * AudioConstants.BITS_PER_SAMPLE / 8).toShort())
        buffer.putShort(AudioConstants.BITS_PER_SAMPLE.toShort())
        buffer.putInt(0x61746164) // "data"
        buffer.putInt(0) // Tamanho do subchunk 2, será atualizado

        fileOutputStream.write(header, 0, 44)
    }

    private fun updateWavHeader(file: File) {
        if (!file.exists()) return

        val fileSize = file.length()
        val dataSize = fileSize - 44

        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(4L)
                raf.writeInt(Integer.reverseBytes((fileSize - 8).toInt()))

                raf.seek(40L)
                raf.writeInt(Integer.reverseBytes(dataSize.toInt()))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
