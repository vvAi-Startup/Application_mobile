# 🌊 CalmWave - Aplicativo Android de Gravação e Processamento de Áudio

<div align="center">

![CalmWave Logo](app/src/main/res/drawable/splash.png)

**Aplicativo Android nativo para gravação, processamento em tempo real e transcrição de áudios com interface amigável**

[![Android](https://img.shields.io/badge/Android-24%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09-4285F4?style=for-the-badge&logo=jetpack-compose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge)](LICENSE.md)

</div>

---

## 📋 Índice

- [Sobre o Projeto](#-sobre-o-projeto)
- [Funcionalidades](#-funcionalidades)
- [Tecnologias Utilizadas](#-tecnologias-utilizadas)
- [Pré-requisitos](#-pré-requisitos)
- [Instalação](#-instalação)
- [Configuração](#-configuração)
- [Uso](#-uso)
- [Arquitetura](#-arquitetura)
- [Documentação](#-documentação)
- [Roadmap](#-roadmap)
- [Contribuição](#-contribuição)
- [Licença](#-licença)
- [Contato](#-contato)

---

## 🎯 Sobre o Projeto

**CalmWave** é um aplicativo Android desenvolvido pela **vvAi Startup** que oferece uma solução completa para gravação, processamento e transcrição de áudios. Com foco em experiência infantil amigável e tecnologia de ponta, o app utiliza streaming em tempo real via WebSocket e transcrição inteligente com OpenAI Whisper.

### ✨ Destaques

- 🎙️ **Gravação de Alta Qualidade** - WAV 16kHz mono 16-bit
- 🌐 **Processamento em Tempo Real** - Streaming via WebSocket
- 🤖 **Transcrição Inteligente** - OpenAI Whisper (Faster-Whisper)
- 📱 **Interface Moderna** - Jetpack Compose + Material Design 3
- 🎵 **Player Avançado** - ExoPlayer com controle de velocidade
- 📂 **Sistema de Playlists** - Organização personalizada
- 🎨 **Design Infantil** - Interface colorida e animações suaves

---

## 🚀 Funcionalidades

### Gravação de Áudio
- ✅ Gravação em formato WAV de alta qualidade
- ✅ Visualização de waveform animada em tempo real
- ✅ Pausar e retomar durante a gravação
- ✅ Contador de tempo preciso
- ✅ Suporte a dispositivos Bluetooth (Bluetooth SCO)
- ✅ Chunking automático com overlap para streaming

### Processamento em Tempo Real
- ✅ Streaming de áudio via WebSocket
- ✅ Processamento por chunks de 1 segundo
- ✅ Reprodução simultânea do áudio processado
- ✅ Salvamento automático de áudios originais e processados
- ✅ Sistema de sessões para múltiplas gravações

### Transcrição de Áudio
- ✅ Transcrição automática usando OpenAI Whisper
- ✅ Suporte a 99+ idiomas
- ✅ Detecção automática de idioma
- ✅ Progresso de upload em tempo real
- ✅ Múltiplos tamanhos de modelo (tiny, base, small, medium, large)
- ✅ Funciona offline (servidor local)

### Gerenciamento de Áudios
- ✅ Sistema de playlists personalizáveis
- ✅ Cores customizadas para cada playlist
- ✅ Sistema de favoritos
- ✅ Busca e filtros avançados
- ✅ Organização por data e tipo
- ✅ Identificação de áudios originais vs processados

### Player de Áudio
- ✅ ExoPlayer integrado
- ✅ Controle de velocidade (0.5x - 1.5x)
- ✅ Barra de progresso interativa com seek
- ✅ Controles de anterior/próximo
- ✅ Timer de posição e duração
- ✅ Player modal com interface intuitiva

### Interface
- ✅ Design moderno com Jetpack Compose
- ✅ Material Design 3
- ✅ Animações suaves entre telas
- ✅ Waveform animada durante gravação
- ✅ Tema infantil amigável
- ✅ Navegação inferior intuitiva

---

## 🛠️ Tecnologias Utilizadas

### Core
- **[Kotlin](https://kotlinlang.org/)** `2.0.21` - Linguagem de programação
- **[Android SDK](https://developer.android.com/)** `API 24-36` - Plataforma Android
- **[Jetpack Compose](https://developer.android.com/jetpack/compose)** - UI declarativa moderna
- **[Material Design 3](https://m3.material.io/)** - Design system

### Bibliotecas Principais
- **[ExoPlayer (Media3)](https://exoplayer.dev/)** `1.8.0` - Player de áudio avançado
- **[OkHttp](https://square.github.io/okhttp/)** `4.12.0` - Cliente HTTP/WebSocket
- **[Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)** `1.7.3` - Programação assíncrona
- **[Accompanist System UI Controller](https://google.github.io/accompanist/)** `0.34.0` - Controle de UI do sistema

### APIs Externas
- **OpenAI Whisper (Faster-Whisper)** - Transcrição de áudio
- **WebSocket Server** - Streaming em tempo real

### Ferramentas de Build
- **Gradle** `8.8.0` (Kotlin DSL)
- **Android Gradle Plugin** `8.8.0`
- **Java** `11` (compatibility)

---

## 📦 Pré-requisitos

### Para Desenvolvimento
- **Android Studio** Hedgehog (2023.1.1) ou superior
- **JDK** 11 ou superior
- **Android SDK** API 24-36
- **Git** para controle de versão

### Para Execução
- **Dispositivo Android** com API 24+ (Android 7.0 Nougat ou superior)
- **Permissões**:
  - Microfone (gravação)
  - Armazenamento (salvar arquivos)
  - Internet (comunicação com servidor)
  - Bluetooth (opcional, para dispositivos externos)

### Servidor Backend (Requerido)
- Servidor com API de transcrição Whisper
- Servidor WebSocket para streaming
- Endpoints configurados (ver [Configuração](#-configuração))

---

## 💻 Instalação

### 1. Clone o Repositório

```bash
git clone https://github.com/vvAi-Startup/Application_mobile.git
cd Application_mobile
```

### 2. Configure Variáveis de Ambiente (Opcional)

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

### 3. Sincronize o Projeto

Abra o projeto no Android Studio e aguarde a sincronização automática do Gradle.

### 4. Build do Projeto

#### Via Android Studio
- Clique em **Build > Make Project** (Ctrl+F9)

#### Via Terminal
```bash
./gradlew build
```

### 5. Executar no Dispositivo/Emulador

#### Via Android Studio
- Clique em **Run > Run 'app'** (Shift+F10)

#### Via Terminal
```bash
./gradlew installDebug
```

---

## ⚙️ Configuração

### URLs do Servidor

O app utiliza as seguintes URLs por padrão:

```kotlin
// REST API
API_BASE_URL = "http://10.67.57.104:5000"

// Endpoints
/api/v1/audio/transcricao  // Transcrição Whisper
/health                     // Health check

// WebSocket
WS_BASE_URL = "ws://10.67.57.104:5000"

// Endpoint WebSocket
/api/v1/streaming/ws/audio-streaming
```

### Configurar Servidor Customizado

Edite `app/src/main/java/com/vvai/calmwave/Config.kt`:

```kotlin
object Config {
    val apiBaseUrl: String get() = "http://seu-servidor:porta"
    val wsBaseUrl: String get() = "ws://seu-servidor:porta"
    
    val transcriptionUrl: String get() = "$apiBaseUrl/api/v1/audio/transcricao"
    val wsStreamUrl: String get() = "$wsBaseUrl/api/v1/streaming/ws/audio-streaming"
}
```

### Permissões do Android

O app solicita as seguintes permissões automaticamente:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
```

---

## 📱 Uso

### Iniciar o App

1. **Splash Screen**: Aparece apenas na primeira execução
2. **Tela de Gravação**: Tela inicial principal

### Gravar Áudio

1. Toque no botão **"Iniciar"**
2. Fale no microfone (visualize a waveform animada)
3. Toque em **"Pausar"** para pausar temporariamente (opcional)
4. Toque em **"Encerrar"** para finalizar
5. O áudio será processado e salvo automaticamente

### Gerenciar Playlists

1. Navegue para a aba **"Playlists"**
2. Toque no botão **"+"** para criar uma nova playlist
3. Escolha um nome e cor
4. Organize seus áudios arrastando para playlists

### Reproduzir Áudios

1. Vá para a aba **"Áudios"**
2. Toque em um áudio da lista
3. Use os controles:
   - **Play/Pause**: Reproduzir ou pausar
   - **Seek Bar**: Navegar no áudio
   - **Velocidade**: Ajustar de 0.5x a 1.5x
   - **Anterior/Próximo**: Navegar entre áudios

### Buscar e Filtrar

1. Use a caixa de busca no topo
2. Toque no ícone de filtro **"≡"**
3. Selecione:
   - **Apenas favoritos**
   - **Playlist específica**
4. Aplique os filtros

---

## 🏗️ Arquitetura

### Padrão MVVM (Model-View-ViewModel)

```
┌─────────────────┐
│   View (UI)     │  ← Jetpack Compose Activities
│  GravarActivity │
│  PlaylistActivity
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   ViewModel     │  ← MainViewModel (State Management)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│     Model       │  ← AudioService, WavRecorder, Services
└─────────────────┘
```

### Componentes Principais

- **MainActivity**: Tela de debug e testes
- **GravarActivity**: Interface de gravação com waveform
- **PlaylistActivity**: Gerenciamento de playlists e áudios
- **SplashActivity**: Tela inicial do app
- **MainViewModel**: Lógica de negócio e gerenciamento de estado
- **AudioService**: Reprodução, WebSocket e Bluetooth
- **WavRecorder**: Gravação de áudio WAV
- **AudioUploadService**: Upload e transcrição
- **WebSocketService**: Cliente WebSocket
- **ExoPlayerAudioPlayer**: Player de áudio avançado

### Fluxo de Dados

```
User Input → Activity → ViewModel → Service → API/WebSocket
              ↑                                      ↓
              └────────── State Update ──────────────┘
```

---

## 📚 Documentação

### Documentos Disponíveis

- **[DOCUMENTACAO_COMPLETA.md](DOCUMENTACAO_COMPLETA.md)** - Documentação completa para devs e usuários
- **[ARQUITETURA_TECNICA.md](ARQUITETURA_TECNICA.md)** - Arquitetura detalhada com diagramas
- **[IMPLEMENTACAO_AUDIOS_PROCESSADOS.md](IMPLEMENTACAO_AUDIOS_PROCESSADOS.md)** - Feature de áudios processados

### Tópicos Cobertos

- ✅ Guia de setup e instalação
- ✅ Arquitetura MVVM detalhada
- ✅ Fluxos de dados (Gravação, Transcrição, Reprodução)
- ✅ Padrões de design utilizados
- ✅ APIs e integrações
- ✅ Performance e otimizações
- ✅ Segurança e boas práticas
- ✅ 15+ melhorias sugeridas

---

## 🗺️ Roadmap

### Versão Atual: 1.0.0 ✅

- ✅ Gravação de áudio WAV
- ✅ Streaming WebSocket em tempo real
- ✅ Transcrição via Whisper
- ✅ Sistema de playlists
- ✅ Player com controle de velocidade
- ✅ Interface Jetpack Compose

### Próximas Versões 🔜

#### v1.1.0 - Autenticação e Cloud
- 🔜 Sistema de login/cadastro
- 🔜 Sincronização na nuvem
- 🔜 Backup automático
- 🔜 Perfis de usuário

#### v1.2.0 - Modo Offline
- 🔜 Funcionar sem conexão
- 🔜 Queue de upload automática
- 🔜 Cache inteligente

#### v1.3.0 - Editor de Áudio
- 🔜 Cortar início/fim
- 🔜 Ajustar volume
- 🔜 Aplicar fade in/out
- 🔜 Remover ruído

#### v1.4.0 - Compartilhamento
- 🔜 Compartilhar áudios
- 🔜 Exportar em MP3/AAC
- 🔜 Integração com redes sociais

#### v2.0.0 - Features Avançadas
- 🔜 Controle por voz
- 🔜 Widgets do Android
- 🔜 Android Auto
- 🔜 Modo criança

---

## 🤝 Contribuição

### ⚠️ Projeto Proprietário

Este projeto é **propriedade exclusiva da vvAi Startup**. Contribuições externas não são aceitas no momento.

### Para Membros da Equipe vvAi

1. **Nunca** faça commit diretamente na `main`
2. Crie uma branch para sua feature:
   ```bash
   git checkout -b feature/minha-feature
   ```
3. Commit suas mudanças:
   ```bash
   git commit -m "feat: adiciona minha feature"
   ```
4. Push para o repositório:
   ```bash
   git push origin feature/minha-feature
   ```
5. Abra um Pull Request para revisão

### Code Style

- Siga os padrões Kotlin oficiais
- Use nomes descritivos em português
- Documente funções complexas
- Mantenha commits atômicos e descritivos

---

## 📄 Licença

**Copyright © 2025 vvAi Startup. Todos os direitos reservados.**

Este projeto é **propriedade privada e confidencial** da vvAi Startup. 

**É ESTRITAMENTE PROIBIDO**:
- ❌ Usar este código fora da vvAi Startup
- ❌ Copiar, modificar ou distribuir este software
- ❌ Criar trabalhos derivados
- ❌ Usar para fins comerciais ou pessoais
- ❌ Fazer engenharia reversa

Para mais detalhes, consulte o arquivo [LICENSE.md](LICENSE.md).

### Uso Autorizado

✅ Apenas membros autorizados da equipe vvAi Startup podem acessar e modificar este código.

---

## 📞 Contato

### vvAi Startup

- **Website**: [www.vvai.com.br](https://www.vvai.com.br)
- **Email**: contato@vvai.com.br
- **GitHub**: [@vvAi-Startup](https://github.com/vvAi-Startup)

### Suporte Técnico

Para questões técnicas, entre em contato com a equipe de desenvolvimento:
- **Email de Suporte**: dev@vvai.com.br
- **Issues**: [GitHub Issues](https://github.com/vvAi-Startup/Application_mobile/issues) (apenas para membros da equipe)

---

## 🙏 Agradecimentos

Desenvolvido com ❤️ pela equipe **vvAi Startup**

### Equipe de Desenvolvimento
- Equipe vvAi Startup

### Tecnologias Open Source Utilizadas
- [Kotlin](https://kotlinlang.org/)
- [Android](https://www.android.com/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [ExoPlayer](https://exoplayer.dev/)
- [OkHttp](https://square.github.io/okhttp/)
- [OpenAI Whisper](https://github.com/openai/whisper)

---

<div align="center">

**CalmWave** - Transformando áudio em experiência

© 2025 vvAi Startup. Todos os direitos reservados.

</div>
