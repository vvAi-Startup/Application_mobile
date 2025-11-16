package com.vvai.calmwave

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioTrack.MODE_STREAM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * LiveBufferPlayer mantém um buffer deslizante de áudio PCM vindo do backend
 * e permite rewind (voltar alguns segundos) e retornar ao ponto "Ao Vivo".
 *
 * Premissas:
 * - Áudio é PCM 16-bit mono em sampleRate configurado (default 16k).
 * - Chunks podem ter qualquer tamanho; calculamos duração com: bytes / (sampleRate * 2).
 * - Mantemos um buffer circular em memória limitado por maxBufferMs (ex: 5 minutos).
 * - Playback é desacoplado da ingestão: um ponteiro de leitura (playPositionMs) percorre o buffer.
 * - Quando o usuário está a <= liveCatchUpThresholdMs do final, voltamos ao modo ao vivo.
 */
class LiveBufferPlayer(
	private val sampleRate: Int = 16000,
	private val maxBufferMs: Long = 5 * 60 * 1000L, // 5 minutos de histórico
	private val liveCatchUpThresholdMs: Long = 400L,
	private val expectedOverlapMs: Long = 45L // estimativa de overlap entre chunks processados
) {
	private data class Chunk(val data: ByteArray, val durationMs: Long)

	// Lista dinâmica de chunks (remoção do início quando excede maxBufferMs)
	private val chunks = ArrayList<Chunk>()
	private var totalBufferedMs: Long = 0L // duração acumulada de todos os chunks

	// Playback state
	private var audioTrack: AudioTrack? = null
	private var playbackJob: Job? = null
	private val scope = CoroutineScope(Dispatchers.IO)
	private var readIndex = 0 // índice do chunk atual
	private var readOffsetMsInChunk = 0L // offset dentro do chunk
	private val isLiveMode = AtomicBoolean(true) // se verdadeiro segue sempre a cabeça
	private val playPositionMs = AtomicLong(0L) // posição relativa ao início do buffer atual
	private val liveHeadPositionMs = AtomicLong(0L) // duração total == posição da cabeça
	private val isStarted = AtomicBoolean(false)

	// Estatísticas de underrun
	@Volatile private var underruns = 0

	fun start() {
		if (isStarted.get()) return
		setupAudioTrack()
		isStarted.set(true)
		playbackJob = scope.launch { playbackLoop() }
	}

	fun stop() {
		isStarted.set(false)
		playbackJob?.cancel()
		playbackJob = null
		try { audioTrack?.stop() } catch (_: Exception) {}
		try { audioTrack?.release() } catch (_: Exception) {}
		audioTrack = null
		synchronized(chunks) {
			chunks.clear()
			totalBufferedMs = 0L
		}
		readIndex = 0
		readOffsetMsInChunk = 0L
		playPositionMs.set(0L)
		liveHeadPositionMs.set(0L)
	}

	private fun setupAudioTrack() {
		val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
		audioTrack = AudioTrack(
			AudioManager.STREAM_MUSIC,
			sampleRate,
			AudioFormat.CHANNEL_OUT_MONO,
			AudioFormat.ENCODING_PCM_16BIT,
			bufferSize,
			MODE_STREAM
		)
		if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
			audioTrack?.play()
		}
	}

	/**
	 * Adiciona um chunk PCM novo ao final do buffer.
	 */
	fun appendPcm(pcm: ByteArray) {
		if (!isStarted.get()) start()
		// Remove início sobreposto (overlap) dos chunks subsequentes para evitar "dobro" audível
		val dataToAppend = synchronized(chunks) {
			if (chunks.isNotEmpty() && expectedOverlapMs > 0) {
				val bytesPerMs = sampleRate * 2 / 1000
				val trimBytes = (expectedOverlapMs * bytesPerMs).toInt().coerceAtMost(pcm.size - 1)
				if (trimBytes > 0) pcm.copyOfRange(trimBytes, pcm.size) else pcm
			} else pcm
		}
		val durationMs = (dataToAppend.size * 1000L) / (sampleRate * 2)
		synchronized(chunks) {
			chunks.add(Chunk(dataToAppend, durationMs))
			totalBufferedMs += durationMs
			liveHeadPositionMs.set(totalBufferedMs)
			// Remove chunks antigos se exceder limite
			while (totalBufferedMs > maxBufferMs && chunks.isNotEmpty()) {
				val removed = chunks.removeAt(0)
				totalBufferedMs -= removed.durationMs
				// Ajusta ponteiro de leitura se estávamos no início
				if (!isLiveMode.get()) {
					// Se o usuário está em rewind, deslocar playPosition para refletir remoção
					val newPos = playPositionMs.get() - removed.durationMs
					playPositionMs.set(newPos.coerceAtLeast(0L))
					// Recalcular readIndex
					recomputeReadPointersFromPlayPosition()
				}
			}
			// Em modo live: posicionar no INÍCIO do último chunk para reprodução imediata
			if (isLiveMode.get()) {
				val head = liveHeadPositionMs.get()
				playPositionMs.set((head - durationMs).coerceAtLeast(0L))
				recomputeReadPointersFromPlayPosition()
			}
		}
	}

	/**
	 * Loop de reprodução que escreve para o AudioTrack respeitando velocidade normal.
	 */
	private suspend fun playbackLoop() {
		while (isStarted.get() && scope.isActive) {
			val chunk: Chunk?
			val localReadIndex: Int
			val offsetMs: Long
			synchronized(chunks) {
				if (readIndex >= chunks.size) {
					chunk = null
				} else {
					chunk = chunks[readIndex]
				}
				localReadIndex = readIndex
				offsetMs = readOffsetMsInChunk
			}
			if (chunk == null) {
				// Nada para reproduzir ainda
				delay(20)
				continue
			}
			val pcm = chunk.data
			// Se offsetMs > 0 precisamos pular parte do chunk (calculado em bytes)
			val bytesPerMs = sampleRate * 2 / 1000
			val startByte = (offsetMs * bytesPerMs).toInt().coerceAtMost(pcm.size)
			val slice = if (startByte == 0) pcm else pcm.copyOfRange(startByte, pcm.size)
			if (slice.isEmpty()) {
				advanceToNextChunk()
				continue
			}
			val written = audioTrack?.write(slice, 0, slice.size) ?: 0
			if (written <= 0) {
				underruns++
				delay(10)
			} else {
				val playedMs = (written * 1000L) / (sampleRate * 2)
				playPositionMs.addAndGet(playedMs)
				liveHeadPositionMs.get() // for observability
				synchronized(chunks) {
					readOffsetMsInChunk += playedMs
					if (readOffsetMsInChunk >= chunk.durationMs) {
						advanceToNextChunkLocked()
					}
				}
				// Se estivermos próximos da cabeça em rewind, voltar ao live
				if (!isLiveMode.get()) {
					val behindLive = liveHeadPositionMs.get() - playPositionMs.get()
					if (behindLive <= liveCatchUpThresholdMs) {
						goLive()
					}
				}
				// Não aguardar o tempo exato do bloco — AudioTrack já faz o buffering.
				// Um pequeno sleep evita busy-looping sem introduzir cortes quando novos
				// chunks chegam rapidamente.
				delay(10)
			}
		}
	}

	private fun advanceToNextChunkLocked() {
		readIndex++
		readOffsetMsInChunk = 0L
	}

	private fun advanceToNextChunk() {
		synchronized(chunks) { advanceToNextChunkLocked() }
	}

	/** Retorna duração total disponível no buffer (ms). */
	fun getBufferedDurationMs(): Long = liveHeadPositionMs.get()

	/** Posição atual de reprodução relativa ao início do buffer (ms). */
	fun getPlayPositionMs(): Long = playPositionMs.get()

	/** Quanto atrás do live estamos (ms). */
	fun getBehindLiveMs(): Long = liveHeadPositionMs.get() - playPositionMs.get()

	fun isLive(): Boolean = isLiveMode.get()

	/**
	 * Faz seek relativo à cauda (live). Ex: offsetMs = 5000 => volta 5s.
	 */
	fun seekBehindLive(offsetMs: Long) {
		synchronized(chunks) {
			val buffered = getBufferedDurationMs()
			val targetFromStart = (buffered - offsetMs).coerceIn(0L, buffered)
			playPositionMs.set(targetFromStart)
			isLiveMode.set(offsetMs <= liveCatchUpThresholdMs)
			recomputeReadPointersFromPlayPosition()
		}
	}

	/** Vai diretamente ao ponto ao vivo. */
	fun goLive() {
		synchronized(chunks) {
			playPositionMs.set(liveHeadPositionMs.get())
			isLiveMode.set(true)
			recomputeReadPointersFromPlayPosition()
		}
	}

	private fun recomputeReadPointersFromPlayPosition() {
		var remaining = playPositionMs.get()
		readIndex = 0
		readOffsetMsInChunk = 0L
		// Caso especial: se estamos em modo live e posição == início do último chunk recém chegado
		if (isLiveMode.get() && liveHeadPositionMs.get() > 0 && remaining == (liveHeadPositionMs.get() - (chunks.lastOrNull()?.durationMs ?: 0L))) {
			readIndex = chunks.size.coerceAtLeast(1) - 1
			readOffsetMsInChunk = 0L
			return
		}
		for (i in chunks.indices) {
			val c = chunks[i]
			if (remaining < c.durationMs) {
				readIndex = i
				readOffsetMsInChunk = remaining
				return
			} else {
				remaining -= c.durationMs
			}
		}
		// Se passou de tudo, fixa no início do último chunk (não no fim) para evitar posição vazia
		readIndex = chunks.size.coerceAtLeast(1) - 1
		readOffsetMsInChunk = 0L
	}
}
