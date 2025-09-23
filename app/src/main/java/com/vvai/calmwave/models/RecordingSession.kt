package com.vvai.calmwave.models

// ========================================
// BACKEND: RECORDING SESSION MODEL
// ========================================
// Este arquivo gerencia sessões de gravação com status e metadados
//  MANTER: Controle de sessões e rastreamento de progresso

import java.io.File
import java.util.UUID

// ========================================
// BACKEND: Estrutura de dados para sessões de gravação
// ========================================
//  MANTER: Todas as propriedades são necessárias para controle de gravação
data class RecordingSession(
    // ========================================
    // BACKEND: Identificação da sessão
    // ========================================
    val id: String = UUID.randomUUID().toString(), //  MANTER: ID único da sessão
    val filePath: String,                          //  MANTER: Caminho do arquivo de gravação
    
    // ========================================
    // BACKEND: Controle de tempo
    // ========================================
    val startTime: Long = System.currentTimeMillis(), //  MANTER: Início da gravação
    val endTime: Long? = null,                       //  MANTER: Fim da gravação (null se ativa)
    val duration: Long = 0L,                         //  MANTER: Duração total da gravação
    
    // ========================================
    // BACKEND: Controle de chunks
    // ========================================
    val chunksSent: Int = 0,                         //  MANTER: Número de chunks enviados
    val totalChunks: Int = 0,                        //  MANTER: Total de chunks esperados
    
    // ========================================
    // BACKEND: Status da sessão
    // ========================================
    val status: RecordingStatus = RecordingStatus.IDLE, //  MANTER: Status atual da sessão
    val file: File? = null                             //  MANTER: Referência ao arquivo (opcional)
) {
    // ========================================
    // BACKEND: Funções de conveniência para status
    // ========================================
    //  MANTER: Métodos úteis para verificação de estado
    fun isActive(): Boolean = status == RecordingStatus.RECORDING
    fun isCompleted(): Boolean = status == RecordingStatus.COMPLETED
    fun isFailed(): Boolean = status == RecordingStatus.FAILED
}

// ========================================
// BACKEND: Enum para status de gravação
// ========================================
//  MANTER: Estados possíveis de uma sessão de gravação
enum class RecordingStatus {
    IDLE,        //  MANTER: Sessão criada mas não iniciada
    RECORDING,   //  MANTER: Gravação em andamento
    PROCESSING,  //  MANTER: Processando gravação
    COMPLETED,   //  MANTER: Gravação concluída com sucesso
    FAILED       //  MANTER: Gravação falhou
}
