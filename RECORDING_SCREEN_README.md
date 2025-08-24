# Tela de Gravação - CalmWave App

## Visão Geral

Esta é a nova tela de gravação do aplicativo CalmWave, criada usando Jetpack Compose. A tela foi projetada para ter uma interface moderna e intuitiva, seguindo o design mostrado na imagem de referência.

## Características

### 🎨 Design
- **Header azul claro** com cantos arredondados
- **Título principal** "Calm Wave" em destaque
- **Subtítulo** explicativo "Acompanhe a sua gravação"
- **Botão inteligente** que alterna entre "Iniciar" e "Parar" gravação
- **Timer de gravação** que aparece durante a gravação
- **Ondas de áudio** (equalizador) quando gravando
- **Botões de Pause/Play** funcionais abaixo do equalizador
- **Imagem da menina** fixa na parte inferior, acima da bottom navigation
- **Logo CalmWave** no meio da barra de navegação inferior

### 🚀 Funcionalidades
- **Botão único** para iniciar e parar gravação
- **Mudança visual** do botão (verde para iniciar, vermelho para parar)
- **Ícones dinâmicos** (equalizador para gravar, stop para parar)
- **Botão Pause/Play funcional** integrado com o ViewModel
- **Imagem da menina** fixa na parte inferior com largura total
- Integração com a lógica de gravação existente
- Rastreamento automático do tempo de gravação
- Navegação entre diferentes seções do app
- Interface responsiva e adaptável

## Estrutura dos Arquivos

```
app/src/main/java/com/vvai/calmwave/ui/theme/screen/
├── recording_screen.kt          # Tela principal de gravação
└── recording_screen_demo.kt     # Previews para desenvolvimento

app/src/main/java/com/vvai/calmwave/ui/theme/
└── Color.kt                     # Cores personalizadas da tela

app/src/main/res/values/
└── strings.xml                  # Textos da interface

app/src/main/res/drawable/
├── menina.png                   # Imagem da menina meditando
└── logo.png                     # Logo CalmWave
```

## Como Usar

### 1. Importar a Tela
```kotlin
import com.vvai.calmwave.ui.theme.screen.RecordingScreen
```

### 2. Implementar na MainActivity
```kotlin
RecordingScreen(
    onStartRecording = {
        // Sua lógica de gravação aqui
        viewModel.startRecording(filePath)
    },
    onStopRecording = {
        // Sua lógica para parar gravação
        viewModel.stopRecordingAndProcess(apiEndpoint)
    },
    onPausePlay = {
        // Lógica para pause/play
        if (uiState.isPlaying) {
            viewModel.pausePlayback()
        } else {
            viewModel.resumePlayback()
        }
    },
    onPlaylistsClick = {
        // Navegação para playlists
    },
    onHomeClick = {
        // Navegação para home
    },
    onRecordingClick = {
        // Já estamos na tela de gravação
    },
    isRecording = uiState.isRecording,
    recordingTime = formatRecordingTime(uiState.recordingDuration),
    isPlaying = uiState.isPlaying
)
```

### 3. Estados da Tela
- **isRecording**: Controla se está gravando ou não
- **recordingTime**: Tempo de gravação formatado (HH:MM:SS)
- **isPlaying**: Controla se o áudio está reproduzindo ou pausado

### 4. Comportamento dos Botões
- **Botão Iniciar/Parar**: 
  - Estado inicial: Botão verde com texto "Iniciar" e ícone 🔊
  - Durante gravação: Botão vermelho com texto "Parar" e ícone ⏹️
  - Funcionalidade: Um clique inicia, outro clique para a gravação

- **Botão Pause/Play**:
  - Aparece apenas após iniciar a gravação
  - Posicionado abaixo do equalizador (ondas de áudio)
  - Funcional: Integrado com o ViewModel para controlar reprodução
  - Ícones: ▶️ (play) e ⏸️ (pause)

### 5. Posicionamento da Imagem
- **Imagem da menina**: Fixa na parte inferior, acima da bottom navigation
- **Largura**: Total da tela (fillMaxWidth)
- **Altura**: 120dp
- **Posicionamento**: Usando Box com align(Alignment.BottomCenter) e offset

## Cores Personalizadas

As seguintes cores foram adicionadas ao tema:

```kotlin
val LightBlue = Color(0xFFE3F2FD)        // Header
val DarkTeal = Color(0xFF00695C)         // Texto principal
val TealGreen = Color(0xFF00BFA5)        // Botão iniciar e pause/play
val LightTealGreen = Color(0xFF80CBC4)   // Ondas de áudio
val DarkGray = Color(0xFF424242)         // Texto secundário
val NavigationGray = Color(0xFF2C2C2C)   // Barra de navegação
val CloudBlue = Color(0xFFB3E5FC)        // Contorno das nuvens
// Botão parar: Color(0xFFE57373) - Vermelho claro
```

## Modificações no ViewModel

### Novas Propriedades
- `recordingDuration`: Duração da gravação em milissegundos
- `recordingStartTime`: Timestamp de início da gravação

### Funções Atualizadas
- `startRecording()`: Inicializa o tempo de gravação
- `stopRecordingAndProcess()`: Para o rastreamento do tempo
- `startPlaybackMonitor()`: Monitora o tempo de gravação em tempo real
- `pausePlayback()`: Pausa a reprodução
- `resumePlayback()`: Retoma a reprodução

## Navegação

A tela inclui uma barra de navegação inferior com:

1. **Playlists** (📋) - Lista de playlists
2. **Logo CalmWave** (🏷️) - Logo no meio (Home)
3. **Gravação** (🎤) - Tela atual (destacada)

## Personalização

### Modificar Cores
Edite o arquivo `Color.kt` para alterar as cores da interface.

### Alterar Textos
Modifique o arquivo `strings.xml` para personalizar os textos.

### Ajustar Layout
Edite o arquivo `recording_screen.kt` para modificar o layout e componentes.

### Ajustar Posicionamento da Imagem
Para modificar a posição da imagem da menina, ajuste:
```kotlin
Box(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(120.dp) // Altura da imagem
        .offset(y = (-80).dp) // Distância da bottom navigation
) {
    // Imagem da menina
}
```

## Compatibilidade

- **Android**: API 21+ (Android 5.0+)
- **Jetpack Compose**: Versão mais recente
- **Kotlin**: 1.8+

## Próximos Passos

1. **Testar funcionalidade** dos botões pause/play
2. **Ajustar posicionamento** da imagem se necessário
3. Implementar navegação real entre as telas
4. Adicionar animações para as ondas de áudio
5. Implementar funcionalidade de playlists
6. Adicionar mais opções de personalização

## Suporte

Para dúvidas ou problemas, consulte a documentação do Jetpack Compose ou entre em contato com a equipe de desenvolvimento. 