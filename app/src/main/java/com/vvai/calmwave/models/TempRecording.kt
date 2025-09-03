package com.vvai.calmwave.models

// ========================================
// BACKEND: TEMP RECORDING MODEL
// ========================================
// Este arquivo representa gravações temporárias que podem ser renomeadas antes de salvar
//  MANTER: Modelo para gravações em processo de finalização

import java.io.File

// ========================================
// BACKEND: Estrutura de dados para gravações temporárias
// ========================================
//  MANTER: Todas as propriedades são necessárias para controle de gravações temporárias
data class TempRecording(
    // ========================================
    // BACKEND: Identificação da gravação
    // ========================================
    val id: String,                              //  MANTER: ID único da gravação
    val originalFilePath: String,                //  MANTER: Caminho original do arquivo
    val tempFilePath: String,                    //  MANTER: Caminho temporário do arquivo
    
    // ========================================
    // BACKEND: Metadados da gravação
    // ========================================
    val startTime: Long,                         //  MANTER: Início da gravação
    val endTime: Long,                           //  MANTER: Fim da gravação
    val duration: Long,                          //  MANTER: Duração total
    val fileSize: Long,                          //  MANTER: Tamanho do arquivo
    
    // ========================================
    // BACKEND: Status da gravação
    // ========================================
    val status: TempRecordingStatus = TempRecordingStatus.PENDING_RENAME, //  MANTER: Status atual
    
    // ========================================
    // BACKEND: Informações de renomeação
    // ========================================
    val suggestedName: String = "",              //  MANTER: Nome sugerido baseado na data/hora
    val customName: String? = null,              //  MANTER: Nome personalizado do usuário
    val finalName: String? = null                //  MANTER: Nome final após confirmação
) {
    // ========================================
    // BACKEND: Funções de conveniência
    // ========================================
    //  MANTER: Métodos úteis para verificação de estado
    fun isPendingRename(): Boolean = status == TempRecordingStatus.PENDING_RENAME
    fun isConfirmed(): Boolean = status == TempRecordingStatus.CONFIRMED
    fun isCancelled(): Boolean = status == TempRecordingStatus.CANCELLED
    
    // ========================================
    // BACKEND: Obter nome para exibição
    // ========================================
    //  MANTER: Retorna o nome mais apropriado para exibição
    fun getDisplayName(): String {
        return when {
            finalName != null -> finalName
            customName != null -> customName
            suggestedName.isNotEmpty() -> suggestedName
            else -> "Gravação ${id.take(8)}"
        }
    }
    
    // ========================================
    // BACKEND: Obter caminho final
    // ========================================
    //  MANTER: Retorna o caminho final onde o arquivo será salvo
    fun getFinalPath(): String {
        val fileName = finalName ?: customName ?: suggestedName
        val directory = File(originalFilePath).parent
        return "$directory/$fileName.wav"
    }
    
    // ========================================
    // BACKEND: Atualizar status
    // ========================================
    //  MANTER: Cria nova instância com status atualizado
    fun updateStatus(newStatus: TempRecordingStatus): TempRecording {
        return copy(status = newStatus)
    }
    
    // ========================================
    // BACKEND: Confirmar nome
    // ========================================
    //  MANTER: Cria nova instância com nome confirmado
    fun confirmName(name: String): TempRecording {
        return copy(
            customName = name,
            finalName = name,
            status = TempRecordingStatus.CONFIRMED
        )
    }
    
    // ========================================
    // BACKEND: Cancelar gravação
    // ========================================
    //  MANTER: Cria nova instância com status cancelado
    fun cancel(): TempRecording {
        return copy(status = TempRecordingStatus.CANCELLED)
    }
}

// ========================================
// BACKEND: Enum para status de gravação temporária
// ========================================
//  MANTER: Estados possíveis de uma gravação temporária
enum class TempRecordingStatus {
    PENDING_RENAME,  //  MANTER: Aguardando renomeação pelo usuário
    CONFIRMED,       //  MANTER: Nome confirmado, pronto para salvar
    CANCELLED        //  MANTER: Gravação cancelada pelo usuário
}
