# 🏛️ CalmWave - Arquitetura Técnica

## 📋 Índice

1. [Visão Geral da Arquitetura](#visão-geral-da-arquitetura)
2. [Diagrama de Componentes](#diagrama-de-componentes)
3. [Fluxos de Dados](#fluxos-de-dados)
4. [Padrões de Design](#padrões-de-design)
5. [Tecnologias por Camada](#tecnologias-por-camada)
6. [Decisões Arquiteturais](#decisões-arquiteturais)
7. [Performance e Otimizações](#performance-e-otimizações)
8. [Segurança](#segurança)

---

## 🏗️ Visão Geral da Arquitetura

### Arquitetura em Camadas

```
┌─────────────────────────────────────────────────────────────┐
│                     PRESENTATION LAYER                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  GravarUI    │  │  PlaylistUI  │  │  PlayerUI    │      │
│  │  (Compose)   │  │  (Compose)   │  │  (Compose)   │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                  │                  │              │
└─────────┼──────────────────┼──────────────────┼──────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────┐
│                      VIEWMODEL LAYER                         │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              MainViewModel                          │    │
│  │  • State Management (StateFlow)                     │    │
│  │  • Business Logic Coordination                      │    │
│  │  • Lifecycle Awareness                              │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│                       DOMAIN LAYER                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  AudioUseCase│  │ PlaylistUse  │  │ TranscriptUse│      │
│  │              │  │    Case      │  │    Case      │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│                        DATA LAYER                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ AudioService │  │ WavRecorder  │  │ ExoPlayer    │      │
│  │              │  │              │  │   Player     │      │
│  └──────┬───────┘  └──────┬───────┘  └──────────────┘      │
│         │                  │                                 │
│  ┌──────┴──────────────────┴────┐  ┌──────────────┐        │
│  │   AudioUploadService          │  │ WebSocket    │        │
│  │   (Network)                   │  │   Service    │        │
│  └───────────────────────────────┘  └──────────────┘        │
└─────────────────────────────────────────────────────────────┘
          │                              │
          ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      EXTERNAL LAYER                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  REST API    │  │  WebSocket   │  │  Local       │      │
│  │  (Whisper)   │  │  Streaming   │  │  Storage     │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

---

## 🧩 Diagrama de Componentes

### Componentes Principais e Suas Interações

```
┌─────────────────────────────────────────────────────────────────┐
│                          Activities                             │
│                                                                 │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Splash    │─▶│    Gravar    │◀─│  Playlist    │          │
│  │  Activity   │  │   Activity   │─▶│   Activity   │          │
│  └─────────────┘  └──────┬───────┘  └──────┬───────┘          │
│                          │                  │                   │
└──────────────────────────┼──────────────────┼───────────────────┘
                           │                  │
                           ▼                  ▼
                    ┌────────────────────────────┐
                    │     MainViewModel          │
                    │  ┌──────────────────────┐  │
                    │  │  UiState (StateFlow) │  │
                    │  └──────────────────────┘  │
                    │  ┌──────────────────────┐  │
                    │  │  Event Handlers      │  │
                    │  └──────────────────────┘  │
                    └────────┬───────────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
                    ▼                 ▼
        ┌───────────────────┐  ┌───────────────────┐
        │   AudioService    │  │   WavRecorder     │
        │                   │  │                   │
        │  • playback       │  │  • record         │
        │  • websocket      │  │  • chunk          │
        │  • seek           │  │  • pause/resume   │
        └─────────┬─────────┘  └─────────┬─────────┘
                  │                      │
                  ▼                      ▼
        ┌───────────────────┐  ┌───────────────────┐
        │  WebSocketService │  │ AudioUploadService│
        │  • connect        │  │ • transcribe      │
        │  • send/receive   │  │ • progress        │
        │  • close          │  │ • retry           │
        └───────────────────┘  └───────────────────┘
                  │                      │
                  └──────────┬───────────┘
                             │
                             ▼
              ┌────────────────────────────┐
              │   External Services        │
              │  • WebSocket Server        │
              │  • Whisper API             │
              │  • Local Storage           │
              └────────────────────────────┘
```

---

## 🔄 Fluxos de Dados

### 1. Fluxo de Gravação

```
┌─────────────────────────────────────────────────────────────┐
│                    GRAVAÇÃO DE ÁUDIO                         │
└─────────────────────────────────────────────────────────────┘

User Action: "Iniciar Gravação"
    │
    ▼
┌─────────────────────────────────────┐
│  GravarActivity.onClick             │
│  • Gera nome do arquivo             │
│  • Chama viewModel.startRecording() │
└─────────────────┬───────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  MainViewModel.startRecording(filePath)             │
│  • Atualiza UiState (isRecording = true)           │
│  • Conecta WebSocket                                │
│  • Configura chunk callback                        │
│  • Inicia Bluetooth SCO                             │
│  • Chama wavRecorder.startRecording()              │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  WavRecorder.startRecording(filePath)               │
│  • Inicializa AudioRecord                           │
│  • Escreve header WAV no arquivo                    │
│  • Loop: Lê buffer → Escreve arquivo                │
│  • A cada 1s: Acumula chunk com overlap             │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼ (a cada 1 segundo)
┌─────────────────────────────────────────────────────┐
│  WavRecorder.chunkCallback(chunk, index, overlap)   │
│  • Invoca callback registrado pelo ViewModel        │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  MainViewModel callback                             │
│  • Remove overlap para evitar duplicação audível    │
│  • Atualiza statusText ("Enviando chunk N...")      │
│  • Chama audioService.sendAudioChunkViaWebSocket()  │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  AudioService.sendAudioChunkViaWebSocket(chunk)     │
│  • Cria header WAV para o chunk                     │
│  • Base64 encode do áudio completo                  │
│  • Monta JSON message:                              │
│    {                                                │
│      "type": "audio_chunk",                         │
│      "session_id": "uuid",                          │
│      "audio_data": "base64...",                     │
│      "sample_rate": 16000,                          │
│      ...                                            │
│    }                                                │
│  • Envia via WebSocket                              │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  WebSocket Server (Backend)                         │
│  • Recebe chunk                                     │
│  • Processa áudio                                   │
│  • Retorna áudio processado:                        │
│    {                                                │
│      "type": "audio_processed",                     │
│      "processed_audio_data": "base64..."            │
│    }                                                │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  AudioService.onTextMessage(message)                │
│  • Parse JSON                                       │
│  • Base64 decode do áudio processado                │
│  • Extrai PCM do WAV                                │
│  • Escreve no AudioTrack (reprodução)               │
│  • Salva em arquivo processado temporário           │
└─────────────────────────────────────────────────────┘

Loop continua até "Encerrar Gravação"

User Action: "Encerrar Gravação"
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  MainViewModel.stopRecordingAndProcess()            │
│  • Atualiza UiState (isRecording = false)          │
│  • Para WavRecorder                                 │
│  • Desconecta WebSocket                             │
│  • Finaliza arquivo processado                      │
│  • Salva arquivo processado no Downloads           │
│  • (Opcional) Inicia transcrição                    │
└─────────────────────────────────────────────────────┘
```

### 2. Fluxo de Transcrição

```
┌─────────────────────────────────────────────────────────────┐
│                   TRANSCRIÇÃO DE ÁUDIO                       │
└─────────────────────────────────────────────────────────────┘

Após gravação ou seleção manual de arquivo
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  MainViewModel.stopRecordingAndProcess()            │
│  • Obtém arquivo processado (ou original)           │
│  • Testa conectividade com servidor                 │
│  • Se OK: chama uploadProcessedAudio()              │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  MainViewModel.uploadProcessedAudio(file, url)      │
│  • Atualiza UiState (isUploading = true)           │
│  • Configura callback de progresso                  │
│  • Chama uploadService.transcribeAudio()            │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  AudioUploadService.transcribeAudio(file)           │
│  • Chama uploadProcessedAudio() com params padrão   │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  AudioUploadService.uploadProcessedAudio()          │
│  • Cria MultipartBody com arquivo                   │
│  • Adiciona parâmetros:                             │
│    - language: "pt"                                 │
│    - model_size: "medium"                           │
│    - high_quality: true                             │
│  • Envia POST para /api/v1/audio/transcricao       │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼ (durante upload)
┌─────────────────────────────────────────────────────┐
│  ProgressRequestBody.write()                        │
│  • A cada chunk enviado:                            │
│    onProgress(bytesUploaded, totalBytes)            │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  MainViewModel onProgress callback                  │
│  • Calcula porcentagem                              │
│  • Atualiza UiState (uploadProgress)                │
│  • Atualiza statusText:                             │
│    - "Enviando áudio: N%"                           │
│    - "Processando transcrição: N%"                  │
│    - "Finalizando transcrição: N%"                  │
└─────────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  Whisper API (Backend)                              │
│  • Recebe arquivo de áudio                          │
│  • Valida formato                                   │
│  • Carrega modelo Whisper (medium)                  │
│  • Executa transcrição                              │
│  • Retorna JSON com texto transcrito                │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  AudioUploadService.onResponse(response)            │
│  • Parse do JSON                                    │
│  • Retorna UploadResult.Success ou Error            │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  MainViewModel recebe resultado                     │
│  • Se Success:                                      │
│    - Atualiza statusText com sucesso                │
│    - Processa texto transcrito (TODO)               │
│  • Se Error:                                        │
│    - Exibe mensagem de erro                         │
│  • Atualiza UiState (isUploading = false)          │
└─────────────────────────────────────────────────────┘
```

### 3. Fluxo de Reprodução

```
┌─────────────────────────────────────────────────────────────┐
│                   REPRODUÇÃO DE ÁUDIO                        │
└─────────────────────────────────────────────────────────────┘

User Action: Clica em arquivo na lista
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  PlaylistActivity / MainActivity                    │
│  • onFileClicked(filePath)                          │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  MainViewModel.playAudioFile(filePath)              │
│  • Atualiza UiState:                                │
│    - isPlaying = true                               │
│    - currentPlayingFile = filePath                  │
│    - hasActiveAudio = true                          │
│  • Chama audioService.playLocalWavFile()            │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  AudioService.playLocalWavFile(filePath, startMs)   │
│  • Verifica se é novo arquivo (reseta se sim)       │
│  • Inicializa AudioTrack                            │
│  • Abre FileInputStream                             │
│  • Pula header WAV (44 bytes)                       │
│  • Calcula totalDuration                            │
│  • Se startMs > 0: pula para posição                │
│  • Loop principal:                                  │
│    while (isPlaying && !isPaused) {                 │
│      • Verifica seekInProgress                      │
│      • Lê chunk do arquivo                          │
│      • Adiciona ao buffer circular                  │
│      • Escreve no AudioTrack                        │
│      • Atualiza currentPosition                     │
│    }                                                │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼ (paralelamente)
┌─────────────────────────────────────────────────────┐
│  MainViewModel.startPlaybackMonitor()               │
│  • Loop infinito com delay(500ms):                  │
│    while (true) {                                   │
│      • Obtém estado do AudioService                 │
│      • Atualiza UiState se houver mudanças          │
│      • Atualiza currentPosition                     │
│      • Atualiza playbackProgress                    │
│    }                                                │
└─────────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  UI (Compose) reage ao UiState                      │
│  • Slider atualiza posição                          │
│  • Textos de tempo atualizam                        │
│  • Botão Play/Pause reflete estado                  │
└─────────────────────────────────────────────────────┘

User Action: Arrasta o Slider (Seek)
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  Slider.onValueChangeFinished                       │
│  • Calcula nova posição em ms                       │
│  • Chama viewModel.seekTo(timeMs)                   │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  MainViewModel.seekTo(timeMs)                       │
│  • Limita posição entre 0 e totalDuration           │
│  • Atualiza currentPosition imediatamente (UI)      │
│  • Chama audioService.seekTo()                      │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  AudioService.seekTo(timeMs, coroutineScope)        │
│  • Cancela seeks anteriores em progresso            │
│  • Marca seekInProgress = true                      │
│  • targetSeekPosition = timeMs                      │
│  • Se seek para frente:                             │
│    - Loop de reprodução vai pular bytes             │
│  • Se seek para trás:                               │
│    - Reinicia arquivo na nova posição               │
│  • Aguarda estabilização (150-300ms)                │
│  • Marca seekInProgress = false                     │
└─────────────────────────────────────────────────────┘

User Action: Pausa
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  MainViewModel.pausePlayback()                      │
│  • Chama audioService.pausePlayback()               │
│  • Atualiza UiState (isPlaying = false, isPaused)  │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│  AudioService.pausePlayback()                       │
│  • Marca isPaused = true                            │
│  • Chama audioTrack.pause()                         │
│  • Mantém posição atual                             │
└─────────────────────────────────────────────────────┘
```

---

## 🎨 Padrões de Design

### 1. **MVVM (Model-View-ViewModel)**

**Implementação**:
- **View**: Activities + Composables
- **ViewModel**: `MainViewModel`
- **Model**: Services + Data classes

**Benefícios**:
✅ Separação de responsabilidades
✅ Testabilidade
✅ Reatividade com StateFlow
✅ Lifecycle awareness

### 2. **Repository Pattern** (Parcial)

```kotlin
// Pode ser expandido para:
interface AudioRepository {
    suspend fun saveAudio(audio: Audio)
    suspend fun getAudios(): List<Audio>
    suspend fun deleteAudio(id: String)
}

class AudioRepositoryImpl : AudioRepository {
    // Implementação
}
```

### 3. **Observer Pattern**

**StateFlow** para observação de mudanças:
```kotlin
val uiState: StateFlow<UiState> = _uiState.asStateFlow()

// UI observa:
val uiState by viewModel.uiState.collectAsState()
```

### 4. **Singleton Pattern**

```kotlin
object Config {
    val apiBaseUrl: String get() = BuildConfig.API_BASE_URL
    // Configurações globais
}
```

### 5. **Factory Pattern**

```kotlin
class MainViewModelFactory(
    private val audioService: AudioService,
    private val wavRecorder: WavRecorder,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(audioService, wavRecorder, context) as T
    }
}
```

### 6. **Callback Pattern**

```kotlin
fun setChunkCallback(callback: (ByteArray, Int, Int) -> Unit) {
    chunkCallback = callback
}

// Uso:
wavRecorder.setChunkCallback { chunk, index, overlap ->
    // Processar chunk
}
```

### 7. **Builder Pattern**

```kotlin
val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .build()
```

---

## 🔧 Tecnologias por Camada

### Presentation Layer

| Tecnologia | Uso |
|-----------|-----|
| **Jetpack Compose** | UI declarativa |
| **Material 3** | Design system |
| **Accompanist** | System UI control |
| **Compose Animation** | Animações |

### ViewModel Layer

| Tecnologia | Uso |
|-----------|-----|
| **ViewModel** | Lifecycle awareness |
| **StateFlow** | Reactive state |
| **Coroutines** | Async operations |
| **LiveData** (Alternativa) | Observação de dados |

### Domain Layer

| Tecnologia | Uso |
|-----------|-----|
| **Kotlin** | Lógica de negócio |
| **Coroutines** | Operações assíncronas |

### Data Layer

| Tecnologia | Uso |
|-----------|-----|
| **OkHttp** | HTTP/WebSocket client |
| **ExoPlayer** | Media playback |
| **AudioRecord** | Audio recording |
| **AudioTrack** | Audio playback |
| **SharedPreferences** | Local storage |

### External Layer

| Tecnologia | Uso |
|-----------|-----|
| **REST API** | Transcrição Whisper |
| **WebSocket** | Streaming em tempo real |
| **File System** | Armazenamento local |

---

## 🧠 Decisões Arquiteturais

### 1. **Por que MVVM?**

**Decisão**: Usar MVVM ao invés de MVP ou MVC

**Razões**:
- ✅ Separação clara de responsabilidades
- ✅ Testabilidade superior
- ✅ Suporte nativo do Android Jetpack
- ✅ Reatividade com StateFlow/LiveData
- ✅ Lifecycle awareness automático

### 2. **Por que Jetpack Compose?**

**Decisão**: UI declarativa ao invés de XML

**Razões**:
- ✅ Menos código boilerplate
- ✅ Recomposição automática
- ✅ Preview em tempo real
- ✅ Animações mais fáceis
- ✅ Type-safe
- ✅ Futuro do Android UI

### 3. **Por que ExoPlayer ao invés de MediaPlayer?**

**Decisão**: ExoPlayer (Media3) para reprodução

**Razões**:
- ✅ Mais features (controle de velocidade)
- ✅ Melhor performance
- ✅ Suporte a múltiplos formatos
- ✅ Playlist nativa
- ✅ Mantido ativamente pelo Google

### 4. **Por que AudioRecord/AudioTrack?**

**Decisão**: API nativa ao invés de MediaRecorder

**Razões**:
- ✅ Controle granular sobre dados de áudio
- ✅ Acesso a raw PCM data
- ✅ Chunking em tempo real
- ✅ Processamento durante gravação
- ✅ Menor latência

### 5. **Por que WebSocket ao invés de apenas REST?**

**Decisão**: WebSocket para streaming em tempo real

**Razões**:
- ✅ Comunicação bidirecional
- ✅ Baixa latência
- ✅ Streaming de dados
- ✅ Push de servidor para cliente
- ✅ Reduz overhead de múltiplas requisições HTTP

### 6. **Por que OkHttp?**

**Decisão**: OkHttp ao invés de HttpURLConnection

**Razões**:
- ✅ Suporte moderno a HTTP/2
- ✅ WebSocket integrado
- ✅ Connection pooling
- ✅ Interceptors para logging/auth
- ✅ Timeouts configuráveis
- ✅ Retry automático

### 7. **Por que WAV ao invés de MP3?**

**Decisão**: Gravar em formato WAV

**Razões**:
- ✅ Sem compressão (melhor qualidade)
- ✅ Mais simples de processar
- ✅ Compatível com Whisper
- ✅ Latência zero de encoding
- ✅ Formato raw PCM

**Trade-off**: Arquivos maiores (pode adicionar compressão MP3 no futuro)

---

## ⚡ Performance e Otimizações

### 1. **Gravação Otimizada**

```kotlin
// Buffer size otimizado
val bufferSize = AudioRecord.getMinBufferSize(...) * 2

// Chunking eficiente (1s)
const val CHUNK_INTERVAL_MS = 1000L

// Overlap mínimo (45ms)
const val OVERLAP_DURATION_MS = 45L
```

### 2. **Reprodução Eficiente**

```kotlin
// Buffer circular para seek
private val audioBuffer = mutableListOf<ByteArray>()
private val bufferSize = 50 // 50 chunks em memória

// Seek inteligente
if (isSeekBackward) {
    // Usa buffer circular
} else {
    // Apenas pula bytes
}
```

### 3. **Rede Otimizada**

```kotlin
// Connection pooling
val client = OkHttpClient.Builder()
    .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
    .build()

// Timeouts apropriados
.connectTimeout(30, TimeUnit.SECONDS)
.readTimeout(180, TimeUnit.SECONDS) // Transcrição pode demorar
```

### 4. **UI Responsiva**

```kotlin
// Update a cada 500ms (não a cada frame)
delay(500)

// Tolerância para evitar updates desnecessários
if (positionDifference > 200) {
    updatePosition()
}
```

### 5. **Memória**

```kotlin
// Limpa buffer circular quando cheio
if (audioBuffer.size > bufferSize) {
    audioBuffer.removeAt(0)
}

// Release de recursos
override fun onCleared() {
    audioService.stopPlayback()
    webSocket?.close()
}
```

### 6. **Coroutines**

```kotlin
// Dispatchers apropriados
viewModelScope.launch(Dispatchers.IO) {
    // I/O operations
    withContext(Dispatchers.Main) {
        // UI updates
    }
}

// Cancelamento automático
viewModelScope // Cancela quando ViewModel é destruído
```

---

## 🔒 Segurança

### Implementadas

✅ **Permissões Mínimas**:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

✅ **Cleartext Traffic** (apenas desenvolvimento):
```xml
android:usesCleartextTraffic="true"
```

✅ **Validação de Inputs**:
```kotlin
if (!file.exists()) return
if (timeMs < 0 || timeMs > totalDuration) return
```

### Recomendadas para Produção

🔐 **HTTPS/WSS**:
```kotlin
val apiBaseUrl = "https://api.calmwave.com"
val wsBaseUrl = "wss://api.calmwave.com"
```

🔐 **Certificate Pinning**:
```kotlin
val certificatePinner = CertificatePinner.Builder()
    .add("api.calmwave.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build()

val client = OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()
```

🔐 **Token Authentication**:
```kotlin
class AuthInterceptor(private val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
```

🔐 **Encriptação de Arquivos**:
```kotlin
class AudioEncryption {
    fun encrypt(file: File, key: SecretKey): File
    fun decrypt(file: File, key: SecretKey): File
}
```

🔐 **ProGuard/R8**:
```proguard
-keep class com.vvai.calmwave.service.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
```

---

## 📊 Métricas e Monitoramento

### Sugestões de Implementação

```kotlin
// Crashlytics
FirebaseCrashlytics.getInstance().recordException(e)

// Analytics
FirebaseAnalytics.getInstance(context).logEvent("recording_started") {
    param("duration", recordingDuration)
}

// Performance
val trace = FirebasePerformance.getInstance().newTrace("audio_upload")
trace.start()
// ... upload
trace.stop()
```

---

## 🔄 Versionamento e CI/CD

### Git Workflow Recomendado

```
main (production)
  ├── develop (staging)
  │   ├── feature/nova-funcionalidade
  │   ├── bugfix/corrige-problema
  │   └── hotfix/problema-critico
```

### GitHub Actions (Exemplo)

```yaml
name: Android CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v2
    - name: Set up JDK 11
      uses: actions/setup-java@v2
      with:
        java-version: '11'
    - name: Build with Gradle
      run: ./gradlew build
    - name: Run tests
      run: ./gradlew test
```

---

**Documento criado em**: 14 de Novembro de 2025  
**Versão**: 1.0.0
