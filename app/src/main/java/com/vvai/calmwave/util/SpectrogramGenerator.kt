package com.vvai.calmwave.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.vvai.calmwave.SignalProcessor
import java.io.File
import java.io.FileInputStream
import kotlin.math.log10
import kotlin.math.max

object SpectrogramGenerator {

    private const val TAG = "SpectrogramGenerator"
    private const val MIN_DB = -80f
    private const val MAX_DB = 0f

    /**
     * Gera um Bitmap contendo o espectrograma de um arquivo WAV usando as
     * definições do SignalProcessor.
     */
    fun generateSpectrogram(wavFile: File, width: Int = 800, height: Int = 400): Bitmap? {
        if (!wavFile.exists()) return null

        try {
            val pcmBytes = FileInputStream(wavFile).use { fis ->
                fis.skip(44) // Skip WAV header
                fis.readBytes()
            }

            if (pcmBytes.size < 2) return null

            val floatData = SignalProcessor.pcm16ToFloat(pcmBytes)
            val stftResult = SignalProcessor.stft(floatData)
            val magnitudes = stftResult.first // [freq_bins][time_frames]

            val freqBins = magnitudes.size
            if (freqBins == 0) return null
            val timeFrames = magnitudes[0].size
            if (timeFrames == 0) return null

            // Calcular Dbs e achar o max
            var maxMag = 1e-6f
            for (b in 0 until freqBins) {
                for (t in 0 until timeFrames) {
                    if (magnitudes[b][t] > maxMag) {
                        maxMag = magnitudes[b][t]
                    }
                }
            }
            
            // maxDB de referencia
            val refDb = 20 * log10(maxMag.toDouble()).toFloat()

            // Criar imagem
            val bitmapWidth = timeFrames
            val bitmapHeight = freqBins
            val pixels = IntArray(bitmapWidth * bitmapHeight)

            for (t in 0 until timeFrames) {
                for (b in 0 until freqBins) {
                    val mag = magnitudes[b][t]
                    var db = 20 * log10((mag + 1e-6f).toDouble()).toFloat() - refDb
                    
                    if (db < MIN_DB) db = MIN_DB
                    if (db > MAX_DB) db = MAX_DB
                    
                    val normalized = (db - MIN_DB) / (MAX_DB - MIN_DB)
                    
                    // Y axis is inverted: low freq at bottom, high freq at top
                    val y = bitmapHeight - 1 - b
                    val idx = y * bitmapWidth + t
                    
                    pixels[idx] = magmaColormap(normalized)
                }
            }

            val rawBitmap = Bitmap.createBitmap(pixels, bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            
            // Resize to target dimensions
            return Bitmap.createScaledBitmap(rawBitmap, width, height, true)

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gerar espectrograma: ${e.message}", e)
            return null
        }
    }

    /**
     * Retorna o arquivo 'denoised' correspondente para um arquivo original, se existir.
     */
    fun findProcessedFile(originalFile: File): File? {
        val parentDir = originalFile.parentFile ?: return null
        
        // Vamos procurar pelo nome de arquivo processado. No LocalAudioDenoiser, ele salva como:
        // "denoised_${System.currentTimeMillis()}.wav"
        // Então não é fácil parear exatamente pelo nome se não salvamos o mapa original->processado no SQLite.
        // Espera, no PlaylistActivity os arquivos são listados e a distinção é isProcessed.
        // Vamos varrer os arquivos e tentar deduzir ou mostrar a lista.
        // O MainViewModel salva em Downloads como "calmwave_processed_...wav" ou "processed_... .wav".
        // A lógica do app salva o arquivo gravado e envia pro processamento, os nomes não são estritamente ligados.
        // Retornarei nulo por enquanto. Veremos como ligar os dois no UI.
        return null
    }

    // Aproximação do Magma Colormap
    private fun magmaColormap(v: Float): Int {
        val t = v.coerceIn(0f, 1f)
        val r: Float
        val g: Float
        val b: Float

        if (t < 0.25f) {
            val f = t / 0.25f
            r = interpolate(0f, 0.2f, f)
            g = interpolate(0f, 0f, f)
            b = interpolate(0f, 0.3f, f)
        } else if (t < 0.5f) {
            val f = (t - 0.25f) / 0.25f
            r = interpolate(0.2f, 0.5f, f)
            g = interpolate(0f, 0.1f, f)
            b = interpolate(0.3f, 0.4f, f)
        } else if (t < 0.75f) {
            val f = (t - 0.5f) / 0.25f
            r = interpolate(0.5f, 0.9f, f)
            g = interpolate(0.1f, 0.4f, f)
            b = interpolate(0.4f, 0.2f, f)
        } else {
            val f = (t - 0.75f) / 0.25f
            r = interpolate(0.9f, 0.98f, f)
            g = interpolate(0.4f, 0.98f, f)
            b = interpolate(0.2f, 0.7f, f)
        }

        return Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    private fun interpolate(start: Float, end: Float, f: Float): Float {
        return start + (end - start) * f
    }
}
