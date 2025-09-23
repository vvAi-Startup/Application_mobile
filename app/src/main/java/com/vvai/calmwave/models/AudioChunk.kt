package com.vvai.calmwave.models

// ========================================
// BACKEND: AUDIO CHUNK MODEL
// ========================================
// Este arquivo representa chunks de áudio para envio em tempo real
//  MANTER: Modelo de dados para comunicação com API

// ========================================
// BACKEND: Estrutura de dados para chunks de áudio
// ========================================
//  MANTER: Todas as propriedades são necessárias para o envio de chunks
data class AudioChunk(
    // ========================================
    // BACKEND: Dados do chunk
    // ========================================
    val data: ByteArray,                    //  MANTER: Dados de áudio em bytes
    val index: Int,                         //  MANTER: Índice sequencial do chunk
    
    // ========================================
    // BACKEND: Metadados do chunk
    // ========================================
    val timestamp: Long = System.currentTimeMillis(), //  MANTER: Timestamp de criação
    val size: Int = data.size               //  MANTER: Tamanho em bytes
) {
    // ========================================
    // BACKEND: Implementação de equals para comparação
    // ========================================
    //  MANTER: Necessário para comparações e coleções
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioChunk

        if (index != other.index) return false
        if (timestamp != other.timestamp) return false
        if (size != other.size) return false
        return data.contentEquals(other.data)
    }

    // ========================================
    // BACKEND: Implementação de hashCode para coleções
    // ========================================
    //  MANTER: Necessário para uso em HashMap, HashSet, etc.
    override fun hashCode(): Int {
        var result = index
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + size
        result = 31 * result + data.contentHashCode()
        return result
    }
}
