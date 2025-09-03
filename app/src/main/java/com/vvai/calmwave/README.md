# Estrutura de Pacotes - CalmWave

## 📁 **Pacote `service`** - BACKEND
Contém as classes que gerenciam serviços e funcionalidades específicas do sistema.

### Arquivos:
- **`AudioService.kt`** - BACKEND: Gerencia reprodução de áudio, Bluetooth SCO e comunicação com API
  -  MANTER: Core do sistema de áudio
  -  MANTER: Comunicação com API externa
  -  MANTER: Gerenciamento de Bluetooth SCO
  -  MANTER: Sistema de seek e buffer de áudio

- **`WavRecorder.kt`** - BACKEND: Gerencia gravação de áudio em formato WAV e envio de chunks
  -  MANTER: Sistema de gravação de áudio
  -  MANTER: Envio de chunks em tempo real
  -  MANTER: Formatação WAV

## 📁 **Pacote `models`** - BACKEND
Contém as classes de dados e modelos da aplicação.

### Arquivos:
- **`UiState.kt`** - BACKEND: Estado da interface do usuário
  -  MANTER: Estrutura de dados para estado da UI
  -  MANTER: Modelo de dados para comunicação entre camadas
  -  MANTER: **NOVO**: Suporte a gravações temporárias e diálogo de renomeação

- **`AudioChunk.kt`** - BACKEND: Representa chunks de áudio para envio em tempo real
  -  MANTER: Modelo de dados para chunks de áudio
  -  MANTER: Estrutura para comunicação com API

- **`RecordingSession.kt`** - BACKEND: Gerencia sessões de gravação com status e metadados
  -  MANTER: Controle de sessões de gravação
  -  MANTER: Rastreamento de status e progresso

- **`AudioFile.kt`** - BACKEND: Representa arquivos de áudio com informações formatadas
  -  MANTER: Modelo de dados para arquivos de áudio
  -  MANTER: Formatação de informações para exibição

- **`TempRecording.kt`** - BACKEND: **NOVO** - Gravações temporárias para renomeação
  -  MANTER: Modelo para gravações em processo de finalização
  -  MANTER: Suporte a renomeação antes de salvar

## 📁 **Pacote `controller`** - BACKEND
Contém as classes que controlam a lógica de negócio e coordenação entre camadas.

### Arquivos:
- **`MainViewModel.kt`** - BACKEND: ViewModel principal que gerencia o estado da UI
  -  MANTER: Lógica de negócio principal
  -  MANTER: Coordenação entre serviços
  -  MANTER: Gerenciamento de estado da aplicação
  -  MANTER: **NOVO**: Funções de renomeação e confirmação de gravações

- **`MainViewModelFactory.kt`** - BACKEND: Factory para criação do MainViewModel
  -  MANTER: Injeção de dependências
  -  MANTER: Criação de ViewModels

- **`AudioController.kt`** - BACKEND: Centraliza a lógica de controle de áudio
  -  MANTER: Controle centralizado de áudio
  -  MANTER: Gerenciamento de sessões de gravação
  -  MANTER: Interface unificada para operações de áudio

- **`NetworkController.kt`** - BACKEND: Gerencia chamadas de API e conectividade
  -  MANTER: Comunicação com APIs externas
  -  MANTER: Testes de conectividade
  -  MANTER: Upload de arquivos

- **`RecordingController.kt`** - BACKEND: **NOVO** - Gerencia gravações temporárias
  -  MANTER: Controle de gravações em processo de finalização
  -  MANTER: Sistema de renomeação antes de salvar
  -  MANTER: Gerenciamento de arquivos temporários

## 📁 **Pacote `ui`** - FRONTEND
Contém os componentes de interface do usuário.

### Arquivos:
- **`theme/`** - FRONTEND: Temas e estilos da aplicação
  -  MANTER: Estilos visuais da aplicação
  -  MANTER: Temas e cores

- **`RenameDialog.kt`** - FRONTEND: **NOVO** - Diálogo de renomeação
  -  MANTER: Interface para renomear gravações
  -  MANTER: Exibição de detalhes da gravação
  -  MANTER: Campo de texto para nome personalizado

## 📁 **Arquivos na Raiz** - MISTO
- **`MainActivity.kt`** - FRONTEND + BACKEND: Activity principal
  -  MANTER: Interface principal do usuário
  -  MANTER: Navegação e composables
  - ⚠️ REVISAR: Lógica de negócio que pode ser movida para controllers

## 🔄 **Fluxo de Dados**
```
UI (FRONTEND) → Controller (BACKEND) → Service (BACKEND) → Models (BACKEND)
  ↑                                    ↓
  ←─────────── State (BACKEND) ←─────────────────┘
```

## 📋 **Responsabilidades**

### **Service Layer (BACKEND):**
- Gerenciamento de hardware (áudio, Bluetooth)
- Comunicação com APIs externas
- Operações de arquivo

### **Models Layer (BACKEND):**
- Estruturas de dados
- Validações
- Formatação de dados

### **Controller Layer (BACKEND):**
- Lógica de negócio
- Coordenação entre serviços
- Gerenciamento de estado
- Comunicação com ViewModels

### **UI Layer (FRONTEND):**
- Componentes visuais
- Temas e estilos
- Interação com usuário

## 🎯 **RECOMENDAÇÕES DE REFATORAÇÃO:**

### **FRONTEND (MANTER):**
-  `ui/theme/` - Estilos e temas
-  `MainActivity.kt` - Interface principal (mas limpar lógica de negócio)

### **BACKEND (MANTER):**
-  `service/` - Todos os serviços
-  `models/` - Todos os modelos de dados
-  `controller/` - Todos os controladores

### **REVISAR/MOVER:**
- ⚠️ Lógica de negócio do `MainActivity.kt` → `controller/`
- ⚠️ Funções de utilidade → `service/` ou `controller/`
- ⚠️ Lógica de permissões → `controller/`

### **POSSÍVEL REMOÇÃO:**
- ❓ Código duplicado entre `AudioController` e `MainViewModel`
- ❓ Funções não utilizadas
- ❓ Imports desnecessários

## 🆕 **NOVA FUNCIONALIDADE: RENOMEAÇÃO DE GRAVAÇÕES**

### **Descrição:**
Agora o usuário pode renomear suas gravações de áudio logo após parar de gravar, antes de salvar o arquivo.

### **Fluxo de Funcionamento:**
1. **Iniciar Gravação** → Usuário inicia gravação normalmente
2. **Parar Gravação** → Ao parar, cria-se uma gravação temporária
3. **Diálogo de Renomeação** → Aparece diálogo com:
   - Detalhes da gravação (duração, tamanho, data)
   - Nome sugerido baseado na data/hora
   - Campo para nome personalizado
4. **Confirmação** → Usuário confirma o nome e salva
5. **Arquivo Final** → Arquivo é movido para localização final com nome personalizado

### **Benefícios:**
- ✅ **Organização**: Usuário pode dar nomes significativos aos arquivos
- ✅ **Flexibilidade**: Nome sugerido automático + opção de personalização
- ✅ **Controle**: Usuário decide se salva ou cancela a gravação
- ✅ **Feedback**: Visualização de detalhes antes de salvar

### **Arquivos Envolvidos:**
- **`TempRecording.kt`** - Modelo de dados para gravações temporárias
- **`RecordingController.kt`** - Lógica de controle de gravações temporárias
- **`RenameDialog.kt`** - Interface do usuário para renomeação
- **`MainViewModel.kt`** - Coordenação do fluxo de renomeação
- **`UiState.kt`** - Estado da UI para diálogo de renomeação

## 🆕 **NOVA FUNCIONALIDADE: ARQUITETURA WEBSOCKET**

### **Descrição:**
A aplicação foi completamente refatorada para usar WebSocket em vez de HTTP, permitindo comunicação bidirecional e em tempo real com o servidor.

### **Benefícios da Nova Arquitetura:**
- ✅ **Tempo Real**: Comunicação instantânea entre cliente e servidor
- ✅ **Bidirecional**: Servidor pode enviar respostas e notificações
- ✅ **Eficiente**: Conexão persistente sem overhead de reconexão
- ✅ **Robusto**: Reconexão automática e sistema de heartbeat
- ✅ **Escalável**: Suporte a múltiplas sessões simultâneas

### **Componentes WebSocket:**

#### **1. Modelos de Mensagem (`WebSocketMessage.kt`)**
- **Tipos de Mensagem**: Chunks de áudio, início/fim de transmissão, respostas processadas
- **Serialização**: Suporte completo a JSON com kotlinx.serialization
- **Metadados**: Informações de dispositivo, configurações de gravação, status de conexão

#### **2. Estado da Conexão (`WebSocketState.kt`)**
- **Status da Conexão**: Conectando, conectado, desconectado, reconectando, erro
- **Métricas**: Latência, duração da conexão, tentativas de reconexão
- **Estatísticas**: Mensagens enviadas/recebidas, bytes transferidos

#### **3. Serviço WebSocket (`WebSocketService.kt`)**
- **Gerenciamento de Conexão**: Conexão, desconexão, reconexão automática
- **Sistema de Heartbeat**: Mantém conexão ativa e monitora saúde
- **Envio de Áudio**: Chunks em tempo real com metadados completos
- **Callbacks**: Sistema de notificação para diferentes tipos de mensagem

### **Fluxo de Funcionamento WebSocket:**

1. **Conexão Inicial**
   - Cliente conecta ao servidor WebSocket
   - Troca de informações de dispositivo e capacidades
   - Estabelecimento de heartbeat

2. **Transmissão de Áudio**
   - Sinalização de início de transmissão
   - Envio de chunks em tempo real (100ms cada)
   - Metadados de qualidade e configurações
   - Sinalização de fim de transmissão

3. **Processamento e Resposta**
   - Servidor processa áudio em tempo real
   - Envia confirmações de processamento
   - Retorna áudio processado ou análises
   - Notificações de status e progresso

4. **Manutenção da Conexão**
   - Heartbeat automático a cada 30 segundos
   - Reconexão automática em caso de falha
   - Backoff exponencial para tentativas de reconexão

### **Configuração do Servidor:**
- **URL Padrão**: `ws://10.0.2.2:8080/ws`
- **Protocolo**: WebSocket (WS/WSS)
- **Porta**: 8080 (configurável)
- **Formato**: Mensagens JSON estruturadas

### **Arquivos Envolvidos:**
- **`models/WebSocketMessage.kt`** - Estruturas de mensagem WebSocket
- **`models/WebSocketState.kt`** - Estado e controle de conexão
- **`service/WebSocketService.kt`** - Serviço principal WebSocket
- **`service/WavRecorder.kt`** - Atualizado para usar WebSocket
- **`controller/RecordingController.kt`** - Integração com WebSocket
- **`controller/MainViewModel.kt`** - Gerenciamento de estado WebSocket

### **Compatibilidade:**
- **Android**: API 21+ (Android 5.0+)
- **Dependências**: OkHttp WebSocket, kotlinx.serialization
- **Protocolo**: WebSocket RFC 6455
- **Formato**: JSON UTF-8
