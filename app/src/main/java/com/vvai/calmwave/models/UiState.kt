package com.vvai.calmwave.models

// ========================================
// BACKEND: UI STATE MODEL
// ========================================
// Este arquivo define a estrutura de dados para o estado da interface
//  MANTER: Modelo de dados para comunicação entre camadas

import java.io.File

// ========================================
// BACKEND: Estrutura de dados do estado da UI
// ========================================
//  MANTER: Todas as propriedades são necessárias para o estado da aplicação
data class UiState(
    // ========================================
    // BACKEND: Estado de gravação
    // ========================================
    val isRecording: Boolean = false,        //  MANTER: Indica se está gravando
    val isProcessing: Boolean = false,       //  MANTER: Indica se está processando
    val statusText: String = "Pronto para gravar", //  MANTER: Texto de status para o usuário
    
    // ========================================
    // BACKEND: Estado de reprodução
    // ========================================
    val isPlaying: Boolean = false,          //  MANTER: Indica se está reproduzindo
    val isPaused: Boolean = false,           //  MANTER: Indica se está pausado
    val currentPosition: Long = 0,           //  MANTER: Posição atual da reprodução
    val totalDuration: Long = 0,             //  MANTER: Duração total do áudio
    val playbackProgress: Float = 0f,        //  MANTER: Progresso da reprodução (0.0 a 1.0)
    
    // ========================================
    // BACKEND: Lista de arquivos
    // ========================================
    val wavFiles: List<File> = emptyList(), //  MANTER: Lista de arquivos WAV disponíveis
    val currentPlayingFile: String? = null, //  MANTER: Arquivo atualmente reproduzindo
    
    // ========================================
    // BACKEND: Estado de áudio ativo
    // ========================================
    val hasActiveAudio: Boolean = false,     //  MANTER: Para manter a barra visível mesmo quando pausado
    
    // ========================================
    // BACKEND: Gravação temporária para renomeação
    // ========================================
    val tempRecording: TempRecording? = null, //  MANTER: Gravação temporária aguardando renomeação
    val showRenameDialog: Boolean = false,    //  MANTER: Indica se deve mostrar o diálogo de renomeação
    val renameDialogText: String = "",        //  MANTER: Texto atual do campo de renomeação
    val isSaving: Boolean = false             //  MANTER: Indica se está salvando a gravação renomeada
)
