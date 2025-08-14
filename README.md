# CalmWave - Gravador de Áudio

## 📱 Sobre o App

CalmWave é um aplicativo Android para gravação de áudio com suporte a Bluetooth SCO para melhor qualidade de áudio.

## 🎯 Funcionalidades

- **Gravação de Áudio**: Grava áudio em formato WAV (44.1kHz, 16-bit, mono)
- **Suporte Bluetooth**: Usa Bluetooth SCO para melhor qualidade de áudio
- **Salvamento Automático**: Salva gravações no diretório Downloads do dispositivo
- **Nomes Únicos**: Cada gravação recebe um nome único com timestamp
- **Processamento em Tempo Real**: Envia áudio para API e reproduz resposta

## 📁 Local de Salvamento

As gravações são salvas no **diretório Downloads** do dispositivo:

- **Android 10+**: `/storage/emulated/0/Download/`
- **Android 9 e inferior**: `/storage/emulated/0/Download/`

### Formato dos Arquivos
- **Nome**: `calmwave_recording_YYYYMMDD_HHMMSS.wav`
- **Exemplo**: `calmwave_recording_20241201_143052.wav`

## 🔧 Permissões Necessárias

O app solicita automaticamente as seguintes permissões:

- **RECORD_AUDIO**: Para gravação de áudio
- **BLUETOOTH_CONNECT/SCAN**: Para conexão Bluetooth
- **READ_MEDIA_AUDIO** (Android 13+): Para acesso a arquivos de áudio
- **WRITE_EXTERNAL_STORAGE** (Android 9-): Para salvar no Downloads
- **READ_EXTERNAL_STORAGE** (Android 9-): Para ler arquivos salvos

## 🚀 Como Usar

1. **Instalar o App**: Compile e instale o APK no dispositivo
2. **Conceder Permissões**: O app solicitará as permissões necessárias
3. **Conectar Bluetooth** (opcional): Para melhor qualidade de áudio
4. **Gravar**: Toque em "Gravar" para iniciar a gravação
5. **Parar**: Toque em "Parar" para finalizar e salvar

## 📂 Estrutura do Projeto

```
app/src/main/java/com/vvai/calmwave/
├── MainActivity.kt          # Interface principal e lógica de gravação
├── WavRecorder.kt          # Classe para gravação de áudio WAV
├── AudioService.kt         # Serviço de áudio e Bluetooth
└── ui/                     # Componentes de interface
```

## 🔄 Fallback

Se o diretório Downloads não estiver disponível, o app salva no cache interno:
- **Localização**: `/storage/emulated/0/Android/data/com.vvai.calmwave/cache/`

## ⚙️ Configuração da API

Atualize o endpoint da API no arquivo `MainActivity.kt`:

```kotlin
apiEndpoint = "https://your.api.endpoint" // Substitua pela sua API
```

## 📋 Requisitos

- Android 5.0+ (API 21+)
- Permissões de gravação de áudio
- Conexão com internet (para processamento via API)
- Bluetooth (opcional, para melhor qualidade)
