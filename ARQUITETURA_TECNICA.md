# 🏛️ CalmWave - Como o Aplicativo Foi Construído

## 📋 O que você vai aprender aqui

1. [Visão geral da estrutura](#visão-geral-da-estrutura)
2. [Diagrama das peças do aplicativo](#diagrama-das-peças-do-aplicativo)
3. [Como os dados fluem](#como-os-dados-fluem)
4. [Padrões usados](#padrões-usados)
5. [Tecnologias em cada camada](#tecnologias-em-cada-camada)
6. [Decisões importantes que tomamos](#decisões-importantes-que-tomamos)
7. [Como fizemos para ser rápido](#como-fizemos-para-ser-rápido)
8. [Como protegemos seus dados](#como-protegemos-seus-dados)

---

## 🏗️ Visão geral da estrutura

### O aplicativo é organizado em camadas (como um bolo)

Imagine que o CalmWave é um prédio de vários andares. Cada andar tem uma função específica:

```
┌─────────────────────────────────────────────────────────────┐
│                  ANDAR 1: APRESENTAÇÃO                       │
│            (O que você vê e toca na tela)                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Tela de     │  │  Tela de     │  │  Tela do     │      │
│  │  Gravação    │  │  Pastas      │  │  Player      │      │
│  │  (Compose)   │  │  (Compose)   │  │  (Compose)   │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                  │                  │              │
└─────────┼──────────────────┼──────────────────┼──────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────┐
│                  ANDAR 2: GERENCIAMENTO                      │
│            (O "Cérebro" que coordena tudo)                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              MainViewModel                          │    │
│  │  • Guarda informações importantes                   │    │
│  │  • Sabe se está gravando ou não                     │    │
│  │  • Controla o que aparece na tela                   │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│                  ANDAR 3: AÇÃO                               │
│            (Os "trabalhadores" que fazem acontecer)         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ AudioService │  │ WavRecorder  │  │ ExoPlayer    │      │
│  │              │  │              │  │   Player     │      │
│  └──────┬───────┘  └──────┬───────┘  └──────────────┘      │
│         │                  │                                 │
│  ┌──────┴──────────────────┴────┐  ┌──────────────┐        │
│  │   Serviço de Upload           │  │ Serviço      │        │
│  │   (Envia para internet)       │  │ WebSocket    │        │
│  └───────────────────────────────┘  └──────────────┘        │
└─────────────────────────────────────────────────────────────┘
          │                              │
          ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  ANDAR 4: EXTERNO                            │
│            (Coisas fora do aplicativo)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Servidor    │  │  Sistema de  │  │  Arquivos    │      │
│  │  Whisper     │  │  Limpeza     │  │  do Celular  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### Por que essa organização?

**Vantagens:**
- 🔧 **Fácil de manter** - Se algo quebra, sabemos onde procurar
- 🧪 **Fácil de testar** - Podemos testar cada andar separadamente
- 📈 **Fácil de crescer** - Adicionar funcionalidades novas é simples
- 🎯 **Responsabilidades claras** - Cada peça tem uma função específica

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

## 🔄 Como os dados fluem

### 1. O que acontece quando você grava um áudio

Imagine a gravação como uma linha de produção numa fábrica. Cada etapa acontece em sequência:

**PASSO A PASSO:**

```
VOS VOCÊ TOCA EM "INICIAR" 👆
          │
          ▼
1️⃣ TELA DE GRAVAÇÃO recebe o toque
   - Avisa o "Cérebro" (MainViewModel)
   - Pede para começar a gravação
          │
          ▼
2️⃣ CÉREBRO (MainViewModel) organiza tudo
   - Cria um nome para o arquivo
   - Prepara a conexão (se for usar internet)
   - Ativa o gravador
          │
          ▼
3️⃣ GRAVADOR (WavRecorder) entra em ação
   - Liga o microfone do celular
   - Começa a capturar o som
   - A cada 1 segundo, pega um "pedacinho" do áudio
          │
          ▼
4️⃣ LIMPEZA (LocalAudioDenoiser) trabalha
   - Recebe os "pedacinhos" de áudio
   - Usa inteligência artificial para identificar ruídos
   - Remove o ruído, mantém apenas a voz
   - Demora cerca de 0.2 segundos por pedacinho
          │
          ▼
5️⃣ REPRODUÇÃO (AudioService) toca o áudio limpo
   - Recebe o áudio já limpo
   - Toca no alto-falante para você ouvir
   - Salva numa pasta temporária
          │
          ▼
6️⃣ SALVAMENTO final
   - Quando você toca "Encerrar"
   - Salva DOIS arquivos:
     * Áudio original (com ruídos)
     * Áudio limpo (sem ruídos)
   - Ambos ficam na pasta Downloads
          │
          ▼
✅ PRONTO! Você pode ouvir qualquer um dos dois
```

**Linha do tempo:**
- ⏱️ Gravação: Tempo real (enquanto você fala)
- ⏱️ Limpeza: 0.2 segundos por segundo de áudio
- ⏱️ Reprodução: Quase instantânea
- ⏱️ Salvamento: 1-2 segundos

---

### 2. O que acontece quando você transcreve (transforma fala em texto)

A transcrição é opcional e precisa de internet:

**PASSO A PASSO:**

```
VOCÊ ESCOLHE UM ÁUDIO E PEDE TRANSCRIÇÃO 📝
          │
          ▼
1️⃣ CÉREBRO verifica se tem internet
   - Se não tiver → Mostra erro
   - Se tiver → Continua
          │
          ▼
2️⃣ PREPARAÇÃO do arquivo
   - Pega o áudio que você escolheu
   - Prepara para enviar
   - Mostra "Enviando: 0%"
          │
          ▼
3️⃣ ENVIO para o servidor
   - Envia o arquivo pela internet
   - Vai mostrando o progresso: 10%, 20%, 30%...
   - Você vê na tela quanto falta
          │
          ▼
4️⃣ SERVIDOR PROCESSA (Whisper AI)
   - Recebe o áudio
   - A IA "escuta" e escreve o que ouviu
   - Detecta automaticamente o idioma
   - Pode demorar de 10 segundos a 2 minutos
          │
          ▼
5️⃣ RESULTADO volta para você
   - Servidor envia o texto escrito
   - Aplicativo recebe e mostra
   - Você pode copiar ou salvar
          │
          ▼
✅ TEXTO PRONTO! Sua fala virou escrita
```

**Parâmetros da transcrição:**
- 🌍 **Idioma**: Português (mas detecta automaticamente)
- 🎯 **Qualidade**: Média (boa precisão, velocidade ok)
- 🔊 **Tipo**: Alta qualidade habilitada

---

### 3. O que acontece quando você ouve um áudio

Muito mais simples:

```
VOCÊ TOCA NUM ÁUDIO DA LISTA 🎵
          │
          ▼
1️⃣ CÉREBRO identifica qual áudio você escolheu
          │
          ▼
2️⃣ PLAYER (ExoPlayer) carrega o arquivo
          │
          ▼
3️⃣ REPRODUÇÃO começa
   - Você vê a barra de progresso se enchendo
   - Pode pausar, pular, mudar velocidade
          │
          ▼
✅ ÁUDIO TOCANDO! Você ouve seu áudio
```
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
