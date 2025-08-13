package com.vvai.calmwave

import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class WavRecorder{
    private val sampleRate = 44100 // Taxa de amostragem padrão
    private  val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var audioRecorder: AudioRecord? = null
    private var isRecording = false

    suspend fun startRecording(filePath: String){
        // Envolve a gravação em uma coroutine para que ela rode em background
        suspendCoroutine<Unit> {continuation ->
            val outputFile = File(filePath)
            isRecording = true

            audioRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            try{
                FileOutputStream(outputFile).use{ fileOutputStream ->
                    writeWavHeader(fileOutputStream)

                    val data = ByteArray(bufferSize)
                    audioRecorder?.startRecording()

                    while (isRecording){
                        val bytesRead = audioRecorder?.read(data,0, bufferSize)?:0
                        if (bytesRead != AudioRecord.ERROR_INVALID_OPERATION){
                            fileOutputStream.write(data,0,bytesRead)
                        }
                    }
                }
            } catch (e: Exception){
                e.printStackTrace()
            } finally {
                audioRecorder?.stop()
                audioRecorder?.release()
                audioRecorder = null
                updateWavHeader(outputFile)
                continuation.resume(Unit)
            }
        }
    }
    fun stopRecording(){
        isRecording = false
    }

    private fun writeWavHeader(fileOutputStream: FileOutputStream){
        // Cabeçalho WAV padrão
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x46464952) // "RIFF"
        buffer.putInt(0) // Tamanho do arquivo, será atualizado
        buffer.putInt(0x45564157) // "WAVE"
        buffer.putInt(0x20746d66) // "fmt " - Correção aqui, 4 bytes
        buffer.putInt(16) // Tamanho do subchunk 1
        buffer.putShort(1) // Formato de áudio (1 = PCM)
        buffer.putShort(1) // Numero de canais
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate*2) // Byte rate
        buffer.putShort(2) //Block Align
        buffer.putShort(16) // Bits por amostra
        buffer.putInt(0x61746164) // "data"
        buffer.putInt(0) // Tamanho do subchunk 2, será atualizado
        fileOutputStream.write(header,0,44)
    }

    private fun updateWavHeader(file: File){
        val fileSize = file.length()
        val dataSize = fileSize - 44
        try{
            // Correção: Usar 'file' em FileOutputStream
            DataOutputStream(FileOutputStream(file)).use { dos ->
                dos.writeBytes("RIFF")
                // Correção: Usar 'fileSize'
                dos.writeInt(fileSize.toInt() - 8)
                dos.writeBytes("WAVE")
                // Correção: "fmt " com 4 bytes
                dos.writeBytes("fmt ")
                dos.writeInt(16)
                dos.writeShort(1)
                dos.writeInt(sampleRate)
                dos.writeInt(sampleRate * 2)
                dos.writeShort(2)
                dos.writeShort(16)
                dos.writeBytes("data")
                dos.writeInt(dataSize.toInt())
            }
        }catch (e: Exception){
            e.printStackTrace()
        }
    }
}