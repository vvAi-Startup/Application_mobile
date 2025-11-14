# 📱 CalmWave - Documentação Completa

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Arquitetura do Projeto](#arquitetura-do-projeto)
3. [Tecnologias Utilizadas](#tecnologias-utilizadas)
4. [Estrutura de Diretórios](#estrutura-de-diretórios)
5. [Componentes Principais](#componentes-principais)
6. [Guia para Desenvolvedores](#guia-para-desenvolvedores)
7. [Guia para Usuários](#guia-para-usuários)
8. [Funcionalidades Implementadas](#funcionalidades-implementadas)
9. [Melhorias Sugeridas](#melhorias-sugeridas)
10. [APIs e Integrações](#apis-e-integrações)
11. [Configuração e Build](#configuração-e-build)

---

## 🎯 Visão Geral

**CalmWave** é um aplicativo Android nativo para gravação, processamento e reprodução de áudios, com foco em experiência infantil amigável e processamento em tempo real via WebSocket.

### Características Principais

- ✅ Gravação de áudio em tempo real (WAV 16kHz mono)
- ✅ Streaming de áudio via WebSocket com processamento em tempo real
- ✅ Transcrição de áudio usando OpenAI Whisper (Faster-Whisper)
- ✅ Sistema de playlists personalizáveis
- ✅ Interface moderna com Jetpack Compose
- ✅ Suporte a Bluetooth SCO para dispositivos externos
- ✅ Reprodução com controle de velocidade (0.5x - 1.5x)
- ✅ Animações suaves e design infantil
- ✅ Salvamento automático de áudios processados

---

## 🏗️ Arquitetura do Projeto

### Padrão Arquitetural: MVVM (Model-View-ViewModel)

```
┌─────────────────┐
│   View (UI)     │  ← Jetpack Compose
│  Activities     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   ViewModel     │  ← MainViewModel
│  (State Logic)  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│     Model       │  ← Services & Recorders
│  (Business)     │
└─────────────────┘
```

### Fluxo de Dados

```
User Action → Activity → ViewModel → Service → API/WebSocket
                ↑                                    ↓
                └──────── State Update ──────────────┘
```

---

## 🛠️ Tecnologias Utilizadas

### Core Technologies

| Tecnologia | Versão | Uso |
|-----------|--------|-----|
| **Kotlin** | 2.0.21 | Linguagem principal |
| **Android SDK** | API 24-36 | Plataforma Android |
| **Jetpack Compose** | BOM 2024.09.00 | UI declarativa |
| **Material 3** | Latest | Design system |
| **Coroutines** | 1.7.3 | Programação assíncrona |

### Bibliotecas Principais

#### 1. **Jetpack Compose**
```kotlin
// Compose BOM 2024.09.00
implementation(platform("androidx.compose:compose-bom:2024.09.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.activity:activity-compose:1.10.1")
```

**Uso**: Interface de usuário declarativa moderna

#### 2. **ExoPlayer (Media3)**
```kotlin
implementation("androidx.media3:media3-exoplayer:1.8.0")
implementation("androidx.media3:media3-ui:1.8.0")
```

**Uso**: Reprodução de áudio avançada com controle de velocidade

#### 3. **OkHttp**
```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

**Uso**: Cliente HTTP/WebSocket para comunicação com backend

#### 4. **Accompanist System UI Controller**
```kotlin
implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")
```

**Uso**: Controle de barra de status e navegação

#### 5. **Material Icons Extended**
```kotlin
implementation("androidx.compose.material:material-icons-extended")
```

**Uso**: Ícones Material Design

### Ferramentas de Build

- **Gradle**: 8.8.0 (Kotlin DSL)
- **Android Gradle Plugin**: 8.8.0
- **Java**: 11 (source/target compatibility)
- **Kotlin Serialization**: 2.0.21

### APIs Externas Integradas

#### 1. **API de Transcrição (Whisper)**
- **Endpoint**: `http://10.67.57.104:5000/api/v1/audio/transcricao`
- **Método**: POST multipart/form-data
- **Tecnologia Backend**: OpenAI Whisper (Faster-Whisper)
- **Funcionalidades**:
  - Transcrição de áudio para texto
  - Suporte a 99+ idiomas
  - Detecção automática de idioma
  - Modelos: tiny, base, small, medium, large

#### 2. **WebSocket de Streaming**
- **Endpoint**: `ws://10.67.57.104:5000/api/v1/streaming/ws/audio-streaming`
- **Protocolo**: WebSocket (JSON messages)
- **Funcionalidades**:
  - Streaming de áudio em tempo real
  - Processamento por chunks (1s)
  - Resposta processada em tempo real
  - Suporte a sessões

---

## 📁 Estrutura de Diretórios

```
Application_mobile/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/vvai/calmwave/
│   │   │   │   ├── MainActivity.kt                  # Tela principal
│   │   │   │   ├── GravarActivity.kt                # Tela de gravação
│   │   │   │   ├── PlaylistActivity.kt              # Tela de playlists
│   │   │   │   ├── SplashActivity.kt                # Tela de splash
│   │   │   │   ├── MainViewModel.kt                 # ViewModel principal
│   │   │   │   ├── AudioService.kt                  # Serviço de áudio
│   │   │   │   ├── WavRecorder.kt                   # Gravador WAV
│   │   │   │   ├── ExoPlayerAudioPlayer.kt          # Player de áudio
│   │   │   │   ├── Config.kt                        # Configurações
│   │   │   │   ├── service/
│   │   │   │   │   ├── AudioUploadService.kt        # Upload/transcrição
│   │   │   │   │   └── WebSocketService.kt          # Cliente WebSocket
│   │   │   │   └── ui/
│   │   │   │       ├── components/
│   │   │   │       │   ├── BottomNavigationBar.kt   # Navegação inferior
│   │   │   │       │   ├── TopBar.kt                # Barra superior
│   │   │   │       │   └── PlaylistComponents/      # Componentes de playlist
│   │   │   │       └── theme/
│   │   │   │           ├── Color.kt                 # Cores do tema
│   │   │   │           ├── Theme.kt                 # Tema principal
│   │   │   │           └── Type.kt                  # Tipografia
│   │   │   ├── res/
│   │   │   │   ├── anim/                            # Animações de transição
│   │   │   │   ├── drawable/                        # Recursos visuais
│   │   │   │   ├── values/                          # Strings, cores, temas
│   │   │   │   └── xml/                             # Configurações XML
│   │   │   └── AndroidManifest.xml                  # Manifesto do app
│   │   ├── androidTest/                             # Testes instrumentados
│   │   └── test/                                    # Testes unitários
│   ├── build.gradle.kts                             # Build do módulo
│   └── proguard-rules.pro                           # Regras ProGuard
├── gradle/
│   ├── libs.versions.toml                           # Catálogo de versões
│   └── wrapper/                                     # Gradle Wrapper
├── build.gradle.kts                                 # Build raiz
├── settings.gradle.kts                              # Configurações Gradle
├── gradle.properties                                # Propriedades Gradle
└── IMPLEMENTACAO_AUDIOS_PROCESSADOS.md              # Documentação de feature
```

---

## 🧩 Componentes Principais

### 1. **MainViewModel** 📊

**Localização**: `app/src/main/java/com/vvai/calmwave/MainViewModel.kt`

**Responsabilidades**:
- Gerenciamento de estado da aplicação
- Coordenação entre UI e serviços
- Controle de gravação e reprodução
- Upload e transcrição de áudios

**Estados Principais**:
```kotlin
data class UiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val statusText: String = "Pronto para gravar",
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentPosition: Long = 0,
    val totalDuration: Long = 0,
    val playbackProgress: Float = 0f,
    val wavFiles: List<File> = emptyList(),
    val currentPlayingFile: String? = null,
    val hasActiveAudio: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Int = 0
)
```

**Funções Principais**:
- `startRecording(filePath: String)` - Inicia gravação
- `stopRecordingAndProcess(apiEndpoint: String)` - Para e processa
- `playAudioFile(filePath: String)` - Reproduz arquivo
- `transcribeAudioFile(audioFile: File)` - Transcreve áudio

### 2. **AudioService** 🎵

**Localização**: `app/src/main/java/com/vvai/calmwave/AudioService.kt`

**Responsabilidades**:
- Gerenciamento de conexão WebSocket
- Reprodução de áudio via AudioTrack
- Sistema de seek inteligente
- Buffer circular para seek para trás
- Controle de Bluetooth SCO

**Características**:
- Taxa de amostragem: 16kHz
- Canal: Mono
- Formato: PCM 16-bit
- Buffer circular: 50 chunks

**Funções Principais**:
```kotlin
fun connectWebSocket(apiWsUrl: String, context: Context)
fun sendAudioChunkViaWebSocket(chunk: ByteArray)
fun playLocalWavFile(filePath: String, startTimeMs: Long)
fun seekTo(timeMs: Long, coroutineScope: CoroutineScope)
fun pausePlayback()
fun resumePlayback(coroutineScope: CoroutineScope)
fun stopPlayback()
```

### 3. **WavRecorder** 🎙️

**Localização**: `app/src/main/java/com/vvai/calmwave/WavRecorder.kt`

**Responsabilidades**:
- Gravação de áudio WAV
- Chunking em tempo real (1s chunks)
- Sistema de overlap (45ms)
- Pause/Resume durante gravação

**Configurações**:
```kotlin
SAMPLE_RATE = 16000 // Hz
NUM_CHANNELS = 1 // Mono
BITS_PER_SAMPLE = 16
CHUNK_INTERVAL_MS = 1000L // 1 segundo
OVERLAP_DURATION_MS = 45L // 45ms overlap
```

**Funções Principais**:
```kotlin
suspend fun startRecording(filePath: String)
fun stopRecording()
fun pauseRecording()
fun resumeRecording()
fun setChunkCallback(callback: (ByteArray, Int, Int) -> Unit)
```

### 4. **AudioUploadService** 📤

**Localização**: `app/src/main/java/com/vvai/calmwave/service/AudioUploadService.kt`

**Responsabilidades**:
- Upload de áudio para transcrição
- Monitoramento de progresso
- Retry logic
- Timeout management

**Parâmetros de Transcrição**:
```kotlin
language: String = "pt" // Idioma (pt, en, es, fr, etc.)
modelSize: String = "medium" // tiny, base, small, medium, large
highQuality: Boolean = true // Otimizações de qualidade
```

**Funções Principais**:
```kotlin
suspend fun uploadProcessedAudio(
    uploadUrl: String,
    audioFile: File,
    language: String,
    modelSize: String,
    highQuality: Boolean,
    onProgress: (Long, Long) -> Unit
): UploadResult

suspend fun transcribeAudio(
    audioFile: File,
    onProgress: (Long, Long) -> Unit
): UploadResult

suspend fun testTranscriptionEndpoint(
    transcriptionUrl: String
): Boolean
```

### 5. **Activities** 📱

#### **GravarActivity**
- Tela principal de gravação
- Waveform animada
- Contador de tempo
- Botões Iniciar/Pausar/Encerrar
- Transições com animação

#### **PlaylistActivity**
- Gerenciamento de playlists
- Lista de áudios
- Player modal com controle de velocidade
- Sistema de favoritos
- Filtros e busca
- Persistência local (SharedPreferences)

#### **MainActivity**
- Tela de debug/testes
- Lista de áudios gravados
- Player integrado
- Botões de teste de API

#### **SplashActivity**
- Tela inicial do app
- Animação de loading
- Exibição única (primeira vez)

---

## 👨‍💻 Guia para Desenvolvedores

### Setup do Ambiente

#### Requisitos
- Android Studio Hedgehog (2023.1.1) ou superior
- JDK 11 ou superior
- Android SDK API 24-36
- Kotlin 2.0.21

#### Configuração Inicial

1. **Clone o repositório**:
```bash
git clone https://github.com/vvAi-Startup/Application_mobile.git
cd Application_mobile
```

2. **Configure variáveis de ambiente** (opcional):
```bash
export API_BASE_URL="http://10.67.57.104:5000"
export WS_BASE_URL="ws://10.67.57.104:5000"
export DB_BASE_URL="http://10.67.57.104:5000"
```

3. **Build do projeto**:
```bash
./gradlew build
```

4. **Executar no emulador/dispositivo**:
```bash
./gradlew installDebug
```

### Estrutura de Build

#### **build.gradle.kts** (módulo app)

```kotlin
android {
    namespace = "com.vvai.calmwave"
    compileSdk = 36
    
    defaultConfig {
        applicationId = "com.vvai.calmwave"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        
        // BuildConfig fields
        val apiBaseUrl = System.getenv("API_BASE_URL") ?: "http://10.67.57.104:5000"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
```

### Adicionando Novas Funcionalidades

#### 1. **Nova Tela (Activity)**

```kotlin
@Composable
fun NovaTelaScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(title = "Nova Tela")
        // Seu conteúdo aqui
        BottomNavigationBar(selected = "NovaTela")
    }
}
```

#### 2. **Novo Serviço**

```kotlin
class NovoServico {
    private val client = OkHttpClient()
    
    suspend fun executarOperacao(): Result = withContext(Dispatchers.IO) {
        // Lógica assíncrona
    }
}
```

#### 3. **Novo Componente Compose**

```kotlin
@Composable
fun NovoComponente(
    texto: String,
    onClick: () -> Unit
) {
    Button(onClick = onClick) {
        Text(texto)
    }
}
```

### Padrões de Código

#### Nomenclatura
- Classes: `PascalCase` (ex: `AudioService`)
- Funções: `camelCase` (ex: `startRecording`)
- Variáveis: `camelCase` (ex: `isRecording`)
- Constantes: `UPPER_SNAKE_CASE` (ex: `SAMPLE_RATE`)

#### Composables
```kotlin
@Composable
fun MeuComponente(
    parametro: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    // Implementação
}
```

#### ViewModels
```kotlin
class MeuViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    fun executarAcao() {
        viewModelScope.launch {
            // Lógica assíncrona
        }
    }
}
```

### Debug e Logs

#### Logging
```kotlin
println("=== TÍTULO DO LOG ===")
println("Variável: $valor")
println("✅ Sucesso!")
println("❌ Erro!")
```

#### Android Logcat
```kotlin
import android.util.Log

Log.d("TAG", "Debug message")
Log.i("TAG", "Info message")
Log.e("TAG", "Error message", exception)
```

### Testes

#### Testes Unitários
```kotlin
@Test
fun `deve calcular corretamente`() {
    val resultado = minhaFuncao(10)
    assertEquals(20, resultado)
}
```

#### Testes de Compose
```kotlin
@Test
fun `deve exibir texto`() {
    composeTestRule.setContent {
        MeuComponente(texto = "Teste")
    }
    composeTestRule.onNodeWithText("Teste").assertIsDisplayed()
}
```

---

## 👤 Guia para Usuários

### Instalação

1. **Baixe o APK** do release mais recente
2. **Habilite "Instalar de fontes desconhecidas"** nas configurações
3. **Instale o APK**
4. **Conceda as permissões necessárias**:
   - Microfone (gravação)
   - Armazenamento (salvar arquivos)
   - Bluetooth (dispositivos externos)

### Funcionalidades

#### 1. **Gravação de Áudio** 🎙️

**Passos**:
1. Abra o app (tela de Gravação)
2. Toque em **"Iniciar"**
3. Fale no microfone
4. Toque em **"Pausar"** para pausar (opcional)
5. Toque em **"Encerrar"** para finalizar
6. O áudio será salvo automaticamente

**Recursos**:
- Visualização de waveform em tempo real
- Contador de tempo
- Pausar/retomar durante gravação
- Processamento automático após gravação

#### 2. **Playlists** 📂

**Criando Playlist**:
1. Vá para a aba **"Playlists"**
2. Toque no botão **"+"**
3. Digite o nome da playlist
4. Escolha uma cor
5. Toque em **"Criar"**

**Adicionando Áudios**:
1. Vá para a aba **"Áudios"**
2. Toque nos três pontos **"⋮"** ao lado do áudio
3. Selecione **"Mover para playlist"**
4. Escolha a playlist desejada

**Reproduzindo**:
1. Toque no áudio desejado
2. O player modal será aberto
3. Use os controles:
   - **Play/Pause**
   - **Anterior/Próximo**
   - **Velocidade** (0.5x - 1.5x)
   - **Barra de progresso** (arrastar para navegar)

#### 3. **Busca e Filtros** 🔍

**Buscar**:
- Digite na caixa de busca no topo
- Busca por nome de playlist ou áudio

**Filtrar**:
1. Toque no ícone de filtro **"≡"**
2. Marque **"Apenas favoritos"** (opcional)
3. Selecione uma **playlist específica** (opcional)
4. Toque em **"Aplicar"**

#### 4. **Favoritos** ⭐

- Toque no ícone de coração na playlist
- Playlists favoritadas aparecem com coração preenchido
- Use o filtro para ver apenas favoritos

### Dicas de Uso

✅ **Melhores Práticas**:
- Grave em ambiente silencioso
- Use fones Bluetooth para melhor qualidade
- Organize áudios em playlists temáticas
- Use favoritos para acesso rápido

⚠️ **Limitações**:
- Gravação apenas em formato WAV
- Processamento requer conexão com servidor
- Armazenamento no dispositivo

---

## ✨ Funcionalidades Implementadas

### Core Features

✅ **Gravação de Áudio**
- Gravação WAV 16kHz mono 16-bit
- Pause/Resume durante gravação
- Contador de tempo em tempo real
- Waveform animada

✅ **Processamento em Tempo Real**
- Streaming via WebSocket
- Chunking com overlap (45ms)
- Reprodução do áudio processado
- Salvamento automático

✅ **Transcrição de Áudio**
- OpenAI Whisper (Faster-Whisper)
- Suporte a múltiplos idiomas
- Progresso de upload
- Detecção automática de idioma

✅ **Sistema de Playlists**
- Criar/editar/excluir playlists
- Cores personalizadas
- Favoritos
- Filtros e busca
- Persistência local

✅ **Player de Áudio**
- ExoPlayer integrado
- Controle de velocidade (0.5x - 1.5x)
- Seek bar interativa
- Anterior/Próximo
- Timer de posição

✅ **Interface Moderna**
- Jetpack Compose
- Material Design 3
- Animações suaves
- Design infantil amigável
- Transições entre telas

### Integrações

✅ **WebSocket**
- Conexão persistente
- Mensagens JSON
- Sessões de streaming
- Reconexão automática

✅ **API REST**
- Upload multipart/form-data
- Transcrição Whisper
- Health check
- Retry logic

✅ **Bluetooth**
- Suporte a Bluetooth SCO
- Gravação via dispositivos externos
- Controle automático

---

## 🚀 Melhorias Sugeridas

### Prioridade Alta 🔴

#### 1. **Autenticação e Usuários**
**Descrição**: Sistema de login e cadastro de usuários

**Implementação**:
```kotlin
// UserService.kt
class UserService {
    suspend fun login(email: String, password: String): User
    suspend fun register(userData: UserData): User
    suspend fun logout()
}

// User.kt
data class User(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String?
)
```

**Benefícios**:
- Sincronização entre dispositivos
- Backup na nuvem
- Perfis personalizados

#### 2. **Sincronização na Nuvem**
**Descrição**: Upload automático de áudios e playlists para servidor

**Implementação**:
```kotlin
class CloudSyncService {
    suspend fun syncAudios()
    suspend fun syncPlaylists()
    suspend fun downloadFromCloud(audioId: String)
}
```

**Benefícios**:
- Backup automático
- Acesso de múltiplos dispositivos
- Recuperação de dados

#### 3. **Notificações Push**
**Descrição**: Notificar quando transcrição está pronta

**Implementação**:
```kotlin
// Firebase Cloud Messaging
class NotificationService {
    fun sendTranscriptionComplete(audioName: String)
    fun sendProcessingError(error: String)
}
```

**Benefícios**:
- Melhor experiência do usuário
- Feedback em tempo real

#### 4. **Modo Offline**
**Descrição**: Funcionar sem conexão com servidor

**Implementação**:
```kotlin
class OfflineManager {
    fun queueForUpload(audioFile: File)
    fun processQueueWhenOnline()
    fun isOnline(): Boolean
}
```

**Benefícios**:
- Usar em qualquer lugar
- Queue de upload automática
- Melhor confiabilidade

### Prioridade Média 🟡

#### 5. **Compartilhamento de Áudios**
**Implementação**:
```kotlin
fun shareAudio(context: Context, audioFile: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", audioFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/wav"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar áudio"))
}
```

#### 6. **Editor de Áudio Básico**
**Funcionalidades**:
- Cortar início/fim
- Aumentar/diminuir volume
- Aplicar fade in/out
- Remover ruído de fundo

```kotlin
class AudioEditor {
    fun trim(audioFile: File, startMs: Long, endMs: Long): File
    fun changeVolume(audioFile: File, volumeMultiplier: Float): File
    fun applyFadeIn(audioFile: File, durationMs: Long): File
}
```

#### 7. **Temas Personalizados**
**Implementação**:
```kotlin
sealed class AppTheme {
    object Light : AppTheme()
    object Dark : AppTheme()
    data class Custom(val colors: ColorScheme) : AppTheme()
}

@Composable
fun CalmWaveTheme(
    theme: AppTheme = AppTheme.Light,
    content: @Composable () -> Unit
) {
    val colors = when(theme) {
        is AppTheme.Light -> lightColorScheme
        is AppTheme.Dark -> darkColorScheme
        is AppTheme.Custom -> theme.colors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
```

#### 8. **Visualizador Avançado de Waveform**
**Implementação**:
```kotlin
@Composable
fun AdvancedWaveform(
    audioFile: File,
    modifier: Modifier = Modifier,
    onSeek: (positionMs: Long) -> Unit
) {
    // Calcular e desenhar waveform real do arquivo
    val waveformData = remember(audioFile) {
        calculateWaveformData(audioFile)
    }
    
    Canvas(modifier = modifier) {
        // Desenhar waveform baseado nos dados reais
    }
}
```

#### 9. **Estatísticas de Uso**
**Implementação**:
```kotlin
data class UsageStats(
    val totalRecordings: Int,
    val totalDuration: Long,
    val averageRecordingLength: Long,
    val favoritePlaylist: String?,
    val mostPlayedAudio: String?
)

class StatsManager {
    fun trackRecording(duration: Long)
    fun trackPlayback(audioFile: File)
    fun getStats(): UsageStats
}
```

#### 10. **Exportar em Múltiplos Formatos**
**Implementação**:
```kotlin
enum class AudioFormat {
    WAV, MP3, AAC, FLAC, OGG
}

class AudioConverter {
    suspend fun convert(
        inputFile: File,
        outputFormat: AudioFormat,
        quality: Int = 128
    ): File
}
```

### Prioridade Baixa 🟢

#### 11. **Controle por Voz**
```kotlin
class VoiceCommandService {
    fun startListening()
    fun onCommand(command: VoiceCommand)
}

sealed class VoiceCommand {
    object StartRecording : VoiceCommand()
    object StopRecording : VoiceCommand()
    object PlayLastRecording : VoiceCommand()
}
```

#### 12. **Widgets do Android**
- Widget de gravação rápida
- Widget de último áudio
- Widget de playlist favorita

#### 13. **Suporte a Android Auto**
- Reproduzir playlists no carro
- Controles de voz
- Interface simplificada

#### 14. **Integração com Assistente Google**
```
"Ok Google, gravar áudio no CalmWave"
"Ok Google, reproduzir última gravação"
```

#### 15. **Modo Criança**
- Interface ainda mais simples
- Cores vibrantes
- Animações divertidas
- Controle parental

---

## 🔌 APIs e Integrações

### API de Transcrição (Whisper)

#### **Endpoint de Transcrição**

```http
POST /api/v1/audio/transcricao
Content-Type: multipart/form-data
```

**Parâmetros Query**:
```
language: string (default: "pt") - Código do idioma
model_size: string (default: "medium") - Tamanho do modelo
high_quality: boolean (default: true) - Qualidade otimizada
```

**Body**:
```
audio: file - Arquivo de áudio (WAV, MP3, M4A, FLAC, OGG)
```

**Resposta (Sucesso - 200)**:
```json
{
  "transcription": "texto transcrito do áudio",
  "language": "pt",
  "confidence": 0.95,
  "duration": 12.5,
  "word_count": 45
}
```

**Resposta (Erro - 422)**:
```json
{
  "error": "Invalid audio format",
  "detail": "Only WAV, MP3, M4A, FLAC, OGG are supported"
}
```

#### **Exemplo de Uso (Kotlin)**:
```kotlin
val service = AudioUploadService()
val result = service.transcribeAudio(
    audioFile = File("/path/to/audio.wav"),
    onProgress = { uploaded, total ->
        val percent = (uploaded * 100 / total).toInt()
        println("Progresso: $percent%")
    }
)

when (result) {
    is UploadResult.Success -> {
        println("Transcrição: ${result.response}")
    }
    is UploadResult.Error -> {
        println("Erro: ${result.message}")
    }
}
```

### WebSocket de Streaming

#### **Endpoint**

```
ws://10.67.57.104:5000/api/v1/streaming/ws/audio-streaming
```

#### **Protocolo de Mensagens**

**1. Iniciar Sessão**:
```json
{
  "type": "start_session",
  "session_id": "uuid-da-sessao"
}
```

**2. Enviar Chunk de Áudio**:
```json
{
  "type": "audio_chunk",
  "session_id": "uuid-da-sessao",
  "chunk_id": "chunk_timestamp",
  "audio_data": "base64_encoded_wav_data",
  "is_final": false,
  "format": "wav",
  "sample_rate": 16000,
  "channels": 1,
  "bits_per_sample": 16
}
```

**3. Receber Áudio Processado**:
```json
{
  "type": "audio_processed",
  "session_id": "uuid-da-sessao",
  "chunk_id": "chunk_timestamp",
  "processed_audio_data": "base64_encoded_processed_wav"
}
```

**4. Encerrar Sessão**:
```json
{
  "type": "stop_session",
  "session_id": "uuid-da-sessao"
}
```

#### **Exemplo de Uso (Kotlin)**:
```kotlin
val wsService = WebSocketService(okHttpClient)

wsService.connect("ws://10.67.57.104:5000/api/v1/streaming/ws/audio-streaming", 
    object : WebSocketService.Listener {
        override fun onOpen() {
            val startMsg = JSONObject()
                .put("type", "start_session")
                .put("session_id", UUID.randomUUID().toString())
            wsService.sendText(startMsg.toString())
        }
        
        override fun onTextMessage(text: String) {
            val json = JSONObject(text)
            when (json.getString("type")) {
                "audio_processed" -> {
                    val processedData = json.getString("processed_audio_data")
                    val audioBytes = Base64.decode(processedData, Base64.DEFAULT)
                    // Reproduzir áudio processado
                }
            }
        }
        
        override fun onClosed(code: Int, reason: String) {
            println("WebSocket fechado: $reason")
        }
        
        override fun onFailure(t: Throwable) {
            println("Erro no WebSocket: ${t.message}")
        }
    }
)
```

### Health Check

```http
GET /health
```

**Resposta**:
```json
{
  "status": "healthy",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## ⚙️ Configuração e Build

### Variáveis de Ambiente

**Definir antes do build**:
```bash
export API_BASE_URL="http://seu-servidor:5000"
export WS_BASE_URL="ws://seu-servidor:5000"
export DB_BASE_URL="http://seu-servidor:5000"
```

Ou edite diretamente em `app/build.gradle.kts`:
```kotlin
defaultConfig {
    val apiBaseUrl = "http://10.67.57.104:5000"
    buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
}
```

### Builds

#### **Debug Build**:
```bash
./gradlew assembleDebug
# APK em: app/build/outputs/apk/debug/app-debug.apk
```

#### **Release Build**:
```bash
./gradlew assembleRelease
# APK em: app/build/outputs/apk/release/app-release.apk
```

#### **Instalar no Dispositivo**:
```bash
./gradlew installDebug
# ou
./gradlew installRelease
```

### ProGuard (Ofuscação)

**Configurar em `app/proguard-rules.pro`**:
```proguard
# Keep classes do serviço
-keep class com.vvai.calmwave.service.** { *; }

# Keep ExoPlayer
-keep class androidx.media3.** { *; }

# Keep OkHttp
-keep class okhttp3.** { *; }
```

### Assinatura do APK

**Criar keystore**:
```bash
keytool -genkey -v -keystore calmwave.keystore -alias calmwave -keyalg RSA -keysize 2048 -validity 10000
```

**Configurar em `app/build.gradle.kts`**:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../calmwave.keystore")
            storePassword = "sua-senha"
            keyAlias = "calmwave"
            keyPassword = "sua-senha"
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### Versioning

**Atualizar versão em `app/build.gradle.kts`**:
```kotlin
defaultConfig {
    versionCode = 2 // Incrementar a cada release
    versionName = "1.1.0" // Semantic versioning
}
```

---

## 📊 Monitoramento e Analytics

### Sugestões de Implementação

#### **Firebase Analytics**:
```kotlin
// Track events
FirebaseAnalytics.getInstance(context).logEvent("recording_started") {
    param("duration", recordingDuration)
    param("format", "wav")
}
```

#### **Crashlytics**:
```kotlin
try {
    // Operação arriscada
} catch (e: Exception) {
    FirebaseCrashlytics.getInstance().recordException(e)
    throw e
}
```

---

## 🔒 Segurança e Privacidade

### Boas Práticas Implementadas

✅ **Permissões Mínimas**: Apenas permissões essenciais
✅ **Armazenamento Local**: Dados sensíveis no dispositivo
✅ **HTTPS/WSS**: Comunicação criptografada (quando configurado)
✅ **No Tracking**: Sem rastreamento de usuários

### Melhorias de Segurança Recomendadas

🔐 **Encriptação de Áudios**:
```kotlin
class AudioEncryption {
    fun encrypt(audioFile: File, password: String): File
    fun decrypt(encryptedFile: File, password: String): File
}
```

🔐 **Token de Autenticação**:
```kotlin
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
```

---

## 📚 Recursos Adicionais

### Documentação Oficial

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [ExoPlayer](https://exoplayer.dev/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [OkHttp](https://square.github.io/okhttp/)
- [Material Design 3](https://m3.material.io/)

### Tutoriais Úteis

- [Building a Media Player with ExoPlayer](https://developer.android.com/guide/topics/media/exoplayer)
- [WebSocket with OkHttp](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-web-socket/)
- [Compose State Management](https://developer.android.com/jetpack/compose/state)

### Comunidade

- [Android Developers Slack](https://developer.android.com/community)
- [Kotlin Slack](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up)
- [Stack Overflow - Android](https://stackoverflow.com/questions/tagged/android)

---

## 🤝 Contribuindo

### Como Contribuir

1. Fork o repositório
2. Crie uma branch para sua feature (`git checkout -b feature/MinhaFeature`)
3. Commit suas mudanças (`git commit -m 'Adiciona MinhaFeature'`)
4. Push para a branch (`git push origin feature/MinhaFeature`)
5. Abra um Pull Request

### Code Review Checklist

- [ ] Código segue os padrões do projeto
- [ ] Testes adicionados/atualizados
- [ ] Documentação atualizada
- [ ] Build passa sem erros
- [ ] Sem warnings do lint
- [ ] Performance não degradada

---

## 📄 Licença

Este projeto é proprietário da **vvAi Startup**.

Todos os direitos reservados © 2025 vvAi Startup.

---

## 📞 Suporte

Para dúvidas, sugestões ou suporte:

- **Email**: contato@vvai.com.br
- **GitHub Issues**: [Application_mobile/issues](https://github.com/vvAi-Startup/Application_mobile/issues)
- **Documentação**: Este arquivo

---

## 📝 Changelog

### Versão 1.0.0 (Atual)
- ✅ Gravação de áudio WAV
- ✅ Streaming WebSocket em tempo real
- ✅ Transcrição via Whisper
- ✅ Sistema de playlists
- ✅ Player com controle de velocidade
- ✅ Interface Jetpack Compose
- ✅ Salvamento automático de áudios processados

### Próximas Versões (Planejado)
- 🔜 Autenticação de usuários
- 🔜 Sincronização na nuvem
- 🔜 Modo offline
- 🔜 Compartilhamento de áudios
- 🔜 Editor de áudio básico

---

**Documentação criada em**: 14 de Novembro de 2025  
**Última atualização**: 14 de Novembro de 2025  
**Versão do documento**: 1.0.0
