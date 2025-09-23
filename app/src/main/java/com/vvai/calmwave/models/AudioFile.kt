package com.vvai.calmwave.models

// ========================================
// BACKEND: AUDIO FILE MODEL
// ========================================
// Este arquivo representa arquivos de áudio com informações formatadas
//  MANTER: Modelo de dados para arquivos de áudio e formatação para exibição

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ========================================
// BACKEND: Estrutura de dados para arquivos de áudio
// ========================================
//  MANTER: Todas as propriedades são necessárias para representação de arquivos
data class AudioFile(
    // ========================================
    // BACKEND: Referência ao arquivo
    // ========================================
    val file: File,                               //  MANTER: Referência ao arquivo físico
    
    // ========================================
    // BACKEND: Propriedades básicas
    // ========================================
    val name: String = file.name,                 //  MANTER: Nome do arquivo
    val size: Long = file.length(),               //  MANTER: Tamanho em bytes
    val lastModified: Long = file.lastModified(), //  MANTER: Data de modificação
    
    // ========================================
    // BACKEND: Propriedades de áudio
    // ========================================
    val duration: Long = 0L,                      //  MANTER: Duração em milissegundos
    val sampleRate: Int = 44100,                  //  MANTER: Taxa de amostragem
    val channels: Int = 1,                        //  MANTER: Número de canais (mono)
    val bitDepth: Int = 16                        //  MANTER: Profundidade de bits
) {
    // ========================================
    // BACKEND: Formatação de tamanho para exibição
    // ========================================
    //  MANTER: Converte bytes para formato legível (B, KB, MB)
    val formattedSize: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }

    // ========================================
    // BACKEND: Formatação de duração para exibição
    // ========================================
    //  MANTER: Converte milissegundos para formato MM:SS
    val formattedDuration: String
        get() {
            val seconds = duration / 1000
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            return String.format("%02d:%02d", minutes, remainingSeconds)
        }

    // ========================================
    // BACKEND: Formatação de data para exibição
    // ========================================
    //  MANTER: Converte timestamp para formato de data legível
    val formattedDate: String
        get() {
            val date = Date(lastModified)
            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return formatter.format(date)
        }

    // ========================================
    // BACKEND: Factory method para criação
    // ========================================
    //  MANTER: Método conveniente para criar instâncias
    companion object {
        fun fromFile(file: File): AudioFile {
            return AudioFile(file = file)
        }
    }
}
