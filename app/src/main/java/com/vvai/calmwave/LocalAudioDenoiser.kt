package com.vvai.calmwave

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Processador local de denoising de áudio usando modelo ONNX (UNet).
 *
 * Replica a lógica do AudioDenoiser do backend Python (app/models/audio_model.py):
 *   1. Lê PCM 16-bit → float normalizado
 *   2. Divide em segmentos de SEGMENT_LENGTH (32 000 amostras = 2 s)
 *   3. Para cada segmento: STFT → log1p(mag) → modelo → máscara → ISTFT
 *   4. Concatena, normaliza e salva como WAV
 */
class LocalAudioDenoiser(private val context: Context) {

    private var ortSession: OrtSession? = null
    private var ortEnvironment: OrtEnvironment? = null
    private var isModelLoaded = false

    companion object {
        private const val MODEL_FILENAME = "denoiser_model.onnx"
        private const val TAG = "LocalAudioDenoiser"
    }

    // ========================================================================
    //  Inicialização
    // ========================================================================

    /**
     * Carrega o modelo ONNX a partir de assets.
     * @return true se carregado com sucesso
     */
    fun initialize(): Boolean {
        return try {
            Log.i(TAG, "Iniciando carregamento do modelo ONNX...")

            ortEnvironment = OrtEnvironment.getEnvironment()
            Log.d(TAG, "OrtEnvironment criado")

            // Lê o modelo de assets/
            val modelBytes = context.assets.open(MODEL_FILENAME).use { it.readBytes() }
            Log.d(TAG, "Modelo lido de assets: ${modelBytes.size} bytes (${modelBytes.size / 1024} KB)")

            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }

            ortSession = ortEnvironment!!.createSession(modelBytes, sessionOptions)
            isModelLoaded = true
            Log.i(TAG, "✅ Modelo ONNX carregado com sucesso (${modelBytes.size / 1024} KB)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao carregar modelo ONNX: ${e.message}", e)
            e.printStackTrace()
            isModelLoaded = false
            false
        }
    }

    /** Verifica se o modelo está pronto para uso */
    fun isReady(): Boolean = isModelLoaded && ortSession != null

    // ========================================================================
    //  Processamento de chunk PCM em tempo real (streaming)
    // ========================================================================

    /**
     * Processa um segmento de áudio PCM 16-bit (raw, sem header WAV).
     *
     * O segmento deve ter exatamente SEGMENT_LENGTH amostras (32000 = 2 s).
     * Se for menor, será preenchido com zeros (zero-padding).
     *
     * @param pcmBytes   Dados PCM 16-bit little-endian (mono 16 kHz)
     * @param actualSamples  Número real de amostras úteis (para trimming da saída)
     * @return PCM 16-bit processado (denoised), ou null em caso de erro
     */
    fun processChunkPcm(pcmBytes: ByteArray, actualSamples: Int = pcmBytes.size / 2): ByteArray? {
        if (!isReady()) return null

        return try {
            // PCM 16-bit → float
            val audioFloat = SignalProcessor.pcm16ToFloat(pcmBytes)

            // Normaliza
            val maxAbs = audioFloat.maxOfOrNull { abs(it) } ?: 1.0f
            if (maxAbs < 1e-8f) {
                // Silêncio — retorna como está
                return pcmBytes
            }
            for (i in audioFloat.indices) audioFloat[i] /= maxAbs

            // Pad para SEGMENT_LENGTH se necessário
            val segmentLen = SignalProcessor.SEGMENT_LENGTH
            val segment = if (audioFloat.size >= segmentLen) {
                audioFloat.copyOf(segmentLen)
            } else {
                FloatArray(segmentLen).also {
                    System.arraycopy(audioFloat, 0, it, 0, audioFloat.size)
                }
            }

            // Processa via STFT → ONNX → ISTFT
            val denoised = processSegment(segment) ?: return pcmBytes

            // Re-escala para amplitude original
            val maxAbsOut = denoised.maxOfOrNull { abs(it) } ?: 1.0f
            if (maxAbsOut > 1e-8f) {
                for (i in denoised.indices) denoised[i] /= maxAbsOut
            }

            // Trim para o número real de amostras
            val trimmed = if (actualSamples < denoised.size) {
                denoised.copyOf(actualSamples)
            } else {
                denoised
            }

            SignalProcessor.floatToPcm16(trimmed)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar chunk em tempo real: ${e.message}", e)
            null
        }
    }

    // ========================================================================
    //  Processamento de arquivo WAV completo
    // ========================================================================

    /**
     * Processa um arquivo WAV, aplicando denoising via modelo ONNX.
     *
     * @param inputFilePath  Caminho do arquivo WAV gravado
     * @return Caminho do arquivo WAV processado, ou null em caso de erro
     */
    fun processWavFile(inputFilePath: String): String? {
        if (!isReady()) {
            Log.w(TAG, "Modelo não carregado – impossível processar")
            return null
        }

        val inputFile = File(inputFilePath)
        if (!inputFile.exists()) {
            Log.w(TAG, "Arquivo não encontrado: $inputFilePath")
            return null
        }

        return try {
            // Lê dados PCM (pula header WAV de 44 bytes)
            val pcmBytes = FileInputStream(inputFile).use { fis ->
                fis.skip(44)
                fis.readBytes()
            }

            if (pcmBytes.size < 2) {
                Log.w(TAG, "Arquivo WAV vazio ou muito pequeno")
                return null
            }

            // PCM 16-bit → float [-1, 1]
            val audioFloat = SignalProcessor.pcm16ToFloat(pcmBytes)

            // Normaliza (como o Python faz)
            val maxAbs = audioFloat.maxOfOrNull { abs(it) } ?: 1.0f
            if (maxAbs > 1e-8f) {
                for (i in audioFloat.indices) audioFloat[i] /= maxAbs
            }

            val originalLength = audioFloat.size
            val segmentLen = SignalProcessor.SEGMENT_LENGTH
            val numSegments = ceil(originalLength.toDouble() / segmentLen).toInt()

            Log.i(TAG, "Processando $numSegments segmento(s) " +
                    "(${originalLength} amostras ≈ ${originalLength / SignalProcessor.SAMPLE_RATE}s)")

            val denoisedChunks = mutableListOf<FloatArray>()

            for (i in 0 until numSegments) {
                val start = i * segmentLen
                val end = minOf(start + segmentLen, originalLength)

                // Extrai e faz padding do segmento para tamanho fixo
                val segment = FloatArray(segmentLen)
                System.arraycopy(audioFloat, start, segment, 0, end - start)

                val denoised = processSegment(segment)
                denoisedChunks.add(denoised ?: segment)  // fallback: segmento original

                Log.d(TAG, "Segmento ${i + 1}/$numSegments concluído")
            }

            // Concatena e recorta ao tamanho original
            val fullDenoised = FloatArray(originalLength)
            var offset = 0
            for (chunk in denoisedChunks) {
                val copyLen = minOf(chunk.size, originalLength - offset)
                if (copyLen > 0) {
                    System.arraycopy(chunk, 0, fullDenoised, offset, copyLen)
                    offset += copyLen
                }
            }

            // Normaliza saída final
            val maxAbsOut = fullDenoised.maxOfOrNull { abs(it) } ?: 1.0f
            if (maxAbsOut > 1e-8f) {
                for (i in fullDenoised.indices) fullDenoised[i] /= maxAbsOut
            }

            // Float → PCM 16-bit
            val outputPcm = SignalProcessor.floatToPcm16(fullDenoised)

            // Salva como novo arquivo WAV
            val outputFile = File(inputFile.parent, "denoised_${System.currentTimeMillis()}.wav")
            FileOutputStream(outputFile).use { fos ->
                writeWavHeader(fos, outputPcm.size)
                fos.write(outputPcm)
            }

            Log.i(TAG, "✅ Arquivo processado salvo: ${outputFile.absolutePath}")
            outputFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao processar: ${e.message}", e)
            null
        }
    }

    // ========================================================================
    //  Processamento de um segmento (replica process_audio_segment do Python)
    // ========================================================================

    private fun processSegment(segment: FloatArray): FloatArray? {
        return try {
            // 1. STFT → magnitude + fase
            val (magnitudes, phases) = SignalProcessor.stft(segment)
            val freqBins = magnitudes.size      // 257
            val timeFrames = magnitudes[0].size  // 251

            // 2. log1p(magnitude) — idêntico ao Python: torch.log1p(torch.abs(stft))
            val logMag = FloatArray(freqBins * timeFrames)
            for (bin in 0 until freqBins) {
                for (frame in 0 until timeFrames) {
                    logMag[bin * timeFrames + frame] = ln(1.0f + magnitudes[bin][frame])
                }
            }

            // 3. Inferência ONNX — input shape (1, 1, 257, 251)
            val inputShape = longArrayOf(1, 1, freqBins.toLong(), timeFrames.toLong())
            val inputTensor = OnnxTensor.createTensor(
                ortEnvironment,
                FloatBuffer.wrap(logMag),
                inputShape
            )

            val results = ortSession!!.run(mapOf("input" to inputTensor))
            val outputTensor = results.get(0) as OnnxTensor

            // Lê máscara (flat float array em row-major order)
            val mask = readTensorAsFloatArray(outputTensor)

            inputTensor.close()
            results.close()

            if (mask == null || mask.size < freqBins * timeFrames) {
                Log.w(TAG, "Máscara com tamanho inesperado")
                return null
            }

            // 4. Aplica máscara: enhanced_mag = (exp(logMag) - 1) * mask
            val enhancedMag = Array(freqBins) { bin ->
                FloatArray(timeFrames) { frame ->
                    val idx = bin * timeFrames + frame
                    val originalMag = (exp(logMag[idx].toDouble()) - 1.0).toFloat()
                    val maskVal = mask[idx].coerceIn(0f, 1f)
                    originalMag * maskVal
                }
            }

            // 5. ISTFT → áudio reconstruído
            SignalProcessor.istft(enhancedMag, phases, segment.size)

        } catch (e: Exception) {
            Log.e(TAG, "Erro no segmento: ${e.message}", e)
            null
        }
    }

    // ========================================================================
    //  Utilitários
    // ========================================================================

    /** Lê os valores de um OnnxTensor como FloatArray flat. */
    private fun readTensorAsFloatArray(tensor: OnnxTensor): FloatArray? {
        return try {
            val buf = tensor.floatBuffer
            val arr = FloatArray(buf.remaining())
            buf.get(arr)
            arr
        } catch (_: Exception) {
            try {
                // Fallback: tenta getValue() e achata
                val value = tensor.value
                flattenToFloatArray(value)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun flattenToFloatArray(obj: Any?): FloatArray? {
        if (obj == null) return null
        val result = mutableListOf<Float>()
        flattenRecursive(obj, result)
        return if (result.isNotEmpty()) result.toFloatArray() else null
    }

    private fun flattenRecursive(obj: Any, result: MutableList<Float>) {
        when (obj) {
            is FloatArray -> result.addAll(obj.toList())
            is Array<*> -> obj.forEach { it?.let { flattenRecursive(it, result) } }
            is Float -> result.add(obj)
            is Number -> result.add(obj.toFloat())
        }
    }

    /** Escreve um header WAV padrão (16 kHz, mono, 16-bit PCM). */
    private fun writeWavHeader(fos: FileOutputStream, dataSize: Int) {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val sr = SignalProcessor.SAMPLE_RATE
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sr * channels * bitsPerSample / 8

        buffer.putInt(0x46464952) // "RIFF"
        buffer.putInt(36 + dataSize)
        buffer.putInt(0x45564157) // "WAVE"
        buffer.putInt(0x20746d66) // "fmt "
        buffer.putInt(16)
        buffer.putShort(1)        // PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sr)
        buffer.putInt(byteRate)
        buffer.putShort((channels * bitsPerSample / 8).toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.putInt(0x61746164) // "data"
        buffer.putInt(dataSize)

        fos.write(header)
    }

    /** Libera recursos do modelo ONNX. */
    fun release() {
        try { ortSession?.close() } catch (_: Exception) {}
        try { ortEnvironment?.close() } catch (_: Exception) {}
        ortSession = null
        ortEnvironment = null
        isModelLoaded = false
        Log.d(TAG, "Recursos liberados")
    }
}
