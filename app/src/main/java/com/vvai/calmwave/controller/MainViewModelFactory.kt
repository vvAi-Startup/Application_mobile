package com.vvai.calmwave.controller

// ========================================
// BACKEND: MAIN VIEW MODEL FACTORY
// ========================================
// Este arquivo é responsável pela criação do MainViewModel com injeção de dependências
//  MANTER: Factory pattern para criação de ViewModels com dependências

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vvai.calmwave.service.AudioService
import com.vvai.calmwave.service.WavRecorder
import com.vvai.calmwave.service.WebSocketService

// ========================================
// BACKEND: Factory para MainViewModel
// ========================================
//  MANTER: Implementação do padrão Factory para ViewModels
class MainViewModelFactory(
    // ========================================
    // BACKEND: Dependências necessárias
    // ========================================
    //  MANTER: Todas as dependências são necessárias para o ViewModel
    private val audioService: AudioService,    //  MANTER: Serviço de áudio
    private val wavRecorder: WavRecorder,      //  MANTER: Serviço de gravação
    private val webSocketService: WebSocketService, //  MANTER: Serviço WebSocket
    private val context: Context               //  MANTER: Contexto da aplicação
) : ViewModelProvider.Factory {

    // ========================================
    // BACKEND: Criação do ViewModel
    // ========================================
    //  MANTER: Lógica de criação com verificação de tipo
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(audioService, wavRecorder, webSocketService, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
