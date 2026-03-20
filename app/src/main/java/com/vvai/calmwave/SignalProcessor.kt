package com.vvai.calmwave

import kotlin.math.*

/**
 * Processador de sinais para STFT/ISTFT necessários ao denoising local.
 * Replica o comportamento do torch.stft / torch.istft do PyTorch com center=True.
 *
 * Parâmetros alinhados com o backend Python:
 *   n_fft = 512, hop_length = 128, segment_length = 32000, sample_rate = 16000
 */
object SignalProcessor {

    const val N_FFT = 512
    const val HOP_LENGTH = 128
    const val SEGMENT_LENGTH = 32000  // 2 segundos a 16 kHz
    const val SAMPLE_RATE = 16000
    const val FREQ_BINS = N_FFT / 2 + 1  // 257

    // Janela Hann pré-computada
    private val hannWindow: FloatArray by lazy {
        FloatArray(N_FFT) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / N_FFT))).toFloat()
        }
    }

    // ========================================================================
    //  FFT – Cooley-Tukey radix-2 (in-place)
    // ========================================================================

    /**
     * FFT in-place.
     * @param re  Partes reais (tamanho n, potência de 2)
     * @param im  Partes imaginárias (mesmo tamanho)
     * @param n   Tamanho (deve ser potência de 2)
     */
    fun fft(re: FloatArray, im: FloatArray, n: Int) {
        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
            var m = n shr 1
            while (m >= 1 && j >= m) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        // Butterfly
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * PI / len
            for (i in 0 until n step len) {
                for (k in 0 until halfLen) {
                    val wR = cos(angle * k).toFloat()
                    val wI = sin(angle * k).toFloat()
                    val evenIdx = i + k
                    val oddIdx = i + k + halfLen
                    val tR = wR * re[oddIdx] - wI * im[oddIdx]
                    val tI = wR * im[oddIdx] + wI * re[oddIdx]
                    re[oddIdx] = re[evenIdx] - tR
                    im[oddIdx] = im[evenIdx] - tI
                    re[evenIdx] += tR
                    im[evenIdx] += tI
                }
            }
            len = len shl 1
        }
    }

    /**
     * Inverse FFT in-place.
     */
    fun ifft(re: FloatArray, im: FloatArray, n: Int) {
        // Conjugate
        for (i in 0 until n) im[i] = -im[i]
        fft(re, im, n)
        val scale = 1.0f / n
        for (i in 0 until n) {
            re[i] *= scale
            im[i] = -im[i] * scale
        }
    }

    // ========================================================================
    //  STFT  (compatível com torch.stft, center=True, window=hann)
    // ========================================================================

    /**
     * Short-Time Fourier Transform.
     *
     * @param signal  Amostras de áudio float (mono, normalizado em [-1,1])
     * @return Pair(magnitude[freqBins][timeFrames], phase[freqBins][timeFrames])
     */
    fun stft(signal: FloatArray): Pair<Array<FloatArray>, Array<FloatArray>> {
        val padAmount = N_FFT / 2
        val paddedLength = signal.size + 2 * padAmount
        val padded = FloatArray(paddedLength)
        System.arraycopy(signal, 0, padded, padAmount, signal.size)

        val numFrames = (paddedLength - N_FFT) / HOP_LENGTH + 1
        val magnitudes = Array(FREQ_BINS) { FloatArray(numFrames) }
        val phases     = Array(FREQ_BINS) { FloatArray(numFrames) }

        val re = FloatArray(N_FFT)
        val im = FloatArray(N_FFT)

        for (frame in 0 until numFrames) {
            val start = frame * HOP_LENGTH

            // Aplica janela Hann e prepara entrada FFT
            for (i in 0 until N_FFT) {
                re[i] = if (start + i < paddedLength) padded[start + i] * hannWindow[i] else 0f
                im[i] = 0f
            }

            fft(re, im, N_FFT)

            for (bin in 0 until FREQ_BINS) {
                val r = re[bin]
                val imag = im[bin]
                magnitudes[bin][frame] = sqrt(r * r + imag * imag)
                phases[bin][frame] = atan2(imag, r)
            }
        }

        return Pair(magnitudes, phases)
    }

    // ========================================================================
    //  ISTFT  (compatível com torch.istft, center=True, window=hann)
    // ========================================================================

    /**
     * Inverse Short-Time Fourier Transform.
     *
     * @param magnitudes  [freqBins][timeFrames]
     * @param phases      [freqBins][timeFrames]
     * @param length      Comprimento desejado de saída (amostras)
     * @return Sinal reconstruído float
     */
    fun istft(magnitudes: Array<FloatArray>, phases: Array<FloatArray>, length: Int): FloatArray {
        val numFrames = magnitudes[0].size
        val padAmount = N_FFT / 2
        val outputLength = (numFrames - 1) * HOP_LENGTH + N_FFT

        val output = FloatArray(outputLength)
        val windowSum = FloatArray(outputLength)

        val re = FloatArray(N_FFT)
        val im = FloatArray(N_FFT)

        for (frame in 0 until numFrames) {
            // Reconstrói espectro complexo (frequências positivas)
            for (bin in 0 until FREQ_BINS) {
                val mag = magnitudes[bin][frame]
                val phase = phases[bin][frame]
                re[bin] = (mag * cos(phase.toDouble())).toFloat()
                im[bin] = (mag * sin(phase.toDouble())).toFloat()
            }

            // Espelha para frequências negativas (simetria conjugada)
            for (bin in 1 until N_FFT / 2) {
                val mirrorBin = N_FFT - bin
                re[mirrorBin] = re[bin]
                im[mirrorBin] = -im[bin]
            }

            ifft(re, im, N_FFT)

            val start = frame * HOP_LENGTH
            for (i in 0 until N_FFT) {
                if (start + i < outputLength) {
                    output[start + i] += re[i] * hannWindow[i]
                    windowSum[start + i] += hannWindow[i] * hannWindow[i]
                }
            }
        }

        // Normaliza pela soma de janelas
        for (i in output.indices) {
            if (windowSum[i] > 1e-8f) {
                output[i] /= windowSum[i]
            }
        }

        // Remove padding central e recorta ao comprimento desejado
        val result = FloatArray(length)
        val copyLen = minOf(length, outputLength - padAmount)
        if (copyLen > 0 && padAmount < outputLength) {
            System.arraycopy(output, padAmount, result, 0, copyLen)
        }

        return result
    }

    // ========================================================================
    //  Conversões PCM ↔ Float
    // ========================================================================

    /**
     * Converte PCM 16-bit little-endian para float normalizado [-1, 1].
     */
    fun pcm16ToFloat(pcmBytes: ByteArray): FloatArray {
        val numSamples = pcmBytes.size / 2
        val result = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val low = pcmBytes[2 * i].toInt() and 0xFF
            val high = pcmBytes[2 * i + 1].toInt()
            val sample = (high shl 8) or low  // Little-endian
            result[i] = sample / 32768.0f
        }
        return result
    }

    /**
     * Converte float [-1, 1] para PCM 16-bit little-endian.
     */
    fun floatToPcm16(floatData: FloatArray): ByteArray {
        val result = ByteArray(floatData.size * 2)
        for (i in floatData.indices) {
            val clamped = floatData[i].coerceIn(-1.0f, 1.0f)
            val sample = (clamped * 32767.0f).toInt().coerceIn(-32768, 32767)
            result[2 * i] = (sample and 0xFF).toByte()
            result[2 * i + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return result
    }
}
