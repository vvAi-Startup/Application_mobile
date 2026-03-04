# Sistema de Analytics e Sincronização Offline

Este documento descreve o sistema de analytics implementado no aplicativo CalmWave Android.

## 📊 Visão Geral

O sistema de analytics coleta métricas de uso do aplicativo e as envia automaticamente para o backend quando há conexão de rede. Quando offline, os dados são armazenados localmente e sincronizados automaticamente quando a conexão é restaurada.

## 🏗️ Arquitetura

### Componentes Principais

1. **AnalyticsRepository** (`data/repository/AnalyticsRepository.kt`)
   - Gerencia coleta e sincronização de eventos
   - Armazena eventos localmente no banco Room
   - Envia eventos para o backend via API REST
   - Faz upload de arquivos de áudio processados

2. **AppDatabase** (`data/local/AppDatabase.kt`)
   - Banco de dados Room para cache offline
   - Armazena eventos não sincronizados
   - Garante persistência dos dados

3. **SyncAnalyticsWorker** (`workers/SyncAnalyticsWorker.kt`)
   - Worker do WorkManager para sincronização em background
   - Executa periodicamente (a cada 1 hora)
   - Sincroniza imediatamente quando volta online
   - Requer conexão de rede

4. **NetworkMonitor** (`util/NetworkMonitor.kt`)
   - Monitora estado de conectividade em tempo real
   - Dispara sincronização automática quando online
   - Usa Flow para observabilidade reativa

5. **CalmWaveApplication** (`CalmWaveApplication.kt`)
   - Inicializa serviços globais
   - Configura sincronização periódica
   - Observa mudanças de conectividade

## 📈 Métricas Coletadas

### Processamento de Áudio

```kotlin
AudioProcessingMetrics(
    filename: String,                  // Nome do arquivo
    processingTimeMs: Long,            // Tempo de processamento em ms
    recordingDurationMs: Long,         // Duração da gravação em ms
    originalFileSizeBytes: Long,       // Tamanho do arquivo original
    processedFileSizeBytes: Long,      // Tamanho do arquivo processado
    sampleRate: Int = 16000,           // Taxa de amostragem
    deviceOrigin: String = "Android",  // Plataforma
    wasOfflineProcessed: Boolean,      // Processado offline?
    processedAt: Long,                 // Timestamp do processamento
    userId: Long?,                     // ID do usuário (se autenticado)
    modelVersion: String = "1.0",      // Versão do modelo ONNX
    errorOccurred: Boolean = false,    // Houve erro?
    errorMessage: String? = null       // Mensagem de erro
)
```

### Eventos de Uso

- **AUDIO_RECORDED**: Gravação de áudio concluída
- **AUDIO_PROCESSED**: Processamento de áudio concluído
- **AUDIO_PLAYED**: Reprodução de áudio
- **SCREEN_VIEW**: Navegação entre telas
- **AUDIO_SYNCED_OFFLINE**: Sincronização de dados offline

## 🔄 Fluxo de Sincronização

### 1. Coleta de Dados (Online/Offline)

```kotlin
// Registrar evento de processamento
analyticsRepository.logAudioProcessingMetrics(metrics)

// Registrar evento de gravação
analyticsRepository.logAudioRecorded(durationMs, fileSizeBytes)

// Registrar reprodução
analyticsRepository.logAudioPlayed(audioId, filename, isProcessed)
```

### 2. Armazenamento Local

- Eventos são salvos no banco Room imediatamente
- Marcados como `synced = false`
- Persistem mesmo se o app for fechado

### 3. Sincronização Automática

#### Quando Online:
```kotlin
// Tenta sincronizar imediatamente após registrar evento
analyticsRepository.logEvent(...) // Registra e tenta sync
```

#### Sincronização Periódica (WorkManager):
- Executa a cada 1 hora automaticamente
- Requer conexão de rede (NetworkType.CONNECTED)
- Retry automático em caso de falha (até 3 tentativas)

#### Sincronização ao Conectar:
```kotlin
// NetworkMonitor detecta conexão e dispara sync imediato
networkMonitor.isOnline.collectLatest { isOnline ->
    if (isOnline) {
        SyncAnalyticsWorker.scheduleImmediate(context)
    }
}
```

### 4. Envio ao Backend

#### Batch Sync (Preferencial):
```kotlin
// Envia até 50 eventos por vez
POST /api/events/batch
{
    "events": [
        {
            "user_id": 1,
            "event_type": "AUDIO_PROCESSED",
            "details": { ... },
            "screen": "GravarActivity",
            "level": "info"
        },
        ...
    ]
}
```

#### Individual Sync (Fallback):
```kotlin
// Se batch falhar, tenta individual
POST /api/events/
{
    "user_id": 1,
    "event_type": "AUDIO_PROCESSED",
    "details": { ... }
}
```

### 5. Upload de Áudio

```kotlin
// Faz upload do arquivo de áudio processado
POST /api/audios/upload
- file: MultipartBody.Part (arquivo WAV)
- device_origin: "Android"
- processing_time_ms: Long
- metadata: JSON com métricas
```

## 🔧 Configuração

### Dependências (build.gradle.kts)

```kotlin
// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// WorkManager
implementation("androidx.work:work-runtime-ktx:2.9.0")

// Retrofit
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
```

### AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<application
    android:name=".CalmWaveApplication"
    ...>
```

## 📝 Uso

### Registrar Evento Customizado

```kotlin
val analyticsRepository = AnalyticsRepository(context)

analyticsRepository.logEvent(
    eventType = "CUSTOM_EVENT",
    details = mapOf(
        "key1" to "value1",
        "key2" to 123
    ),
    screen = "CustomActivity",
    level = "info",
    userId = 1L
)
```

### Verificar Status de Sincronização

```kotlin
// Contador de eventos pendentes
val pendingCount = analyticsRepository.getUnsyncedEventCount()

// Observar eventos pendentes (Flow)
analyticsRepository.observeUnsyncedEvents().collect { events ->
    println("Eventos pendentes: ${events.size}")
}
```

### Forçar Sincronização Manual

```kotlin
// Sincroniza todos os eventos pendentes
val syncedCount = analyticsRepository.syncPendingEvents()
println("Sincronizados: $syncedCount eventos")
```

### Limpeza de Dados Antigos

```kotlin
// Remove eventos sincronizados com mais de 30 dias
analyticsRepository.cleanupOldSyncedEvents()
```

## 🔒 Privacidade e Segurança

- **JWT Authentication**: Todas as requisições usam token Bearer
- **Retry Logic**: Máximo de 3 tentativas por evento
- **Batch Processing**: Reduz número de requisições
- **Data Cleanup**: Remove dados antigos automaticamente
- **Offline First**: Funciona sem conexão de rede

## 📊 Endpoints do Backend

### Eventos

- `POST /api/events/` - Criar evento individual
- `POST /api/events/batch` - Criar múltiplos eventos
- `GET /api/events/?page=1&per_page=50` - Listar eventos

### Áudios

- `POST /api/audios/upload` - Upload de áudio processado
- `GET /api/audios?processed=true` - Listar áudios

### Estatísticas

- `GET /api/stats/dashboard` - Dashboard geral
- `GET /api/stats/analytics` - Analytics detalhados

## 🐛 Debug e Troubleshooting

### Logs

```kotlin
// Tag: AnalyticsRepository
Log.d("AnalyticsRepository", "Evento registrado: $eventType")

// Tag: SyncAnalyticsWorker
Log.d("SyncAnalyticsWorker", "Sincronizados $count eventos")

// Tag: NetworkMonitor
Log.d("NetworkMonitor", "Status: ${if (isOnline) "ONLINE" else "OFFLINE"}")
```

### Verificar WorkManager

```bash
# Via adb logcat
adb logcat | grep SyncAnalyticsWorker
```

### Verificar Banco de Dados

```kotlin
// Via debug ou logs
val allEvents = analyticsDao.getAllEvents()
println("Total de eventos: ${allEvents.size}")
```

## 🚀 Melhorias Futuras

- [ ] Autenticação de usuário (user_id real)
- [ ] Compressão de eventos antes de enviar
- [ ] Criptografia de dados sensíveis
- [ ] Dashboard in-app com estatísticas
- [ ] Configurações de privacidade do usuário
- [ ] Analytics offline para gráficos locais
- [ ] Suporte a eventos personalizados por feature

## 📚 Referências

- [Room Database](https://developer.android.com/training/data-storage/room)
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- [Retrofit](https://square.github.io/retrofit/)
- [Network Monitor](https://developer.android.com/training/monitoring-device-state/connectivity-status-type)
