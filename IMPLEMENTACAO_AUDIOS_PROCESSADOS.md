# Implementação: Salvamento de Áudios Processados

## Resumo das Alterações

Este documento descreve as modificações implementadas para permitir salvar os áudios tratados que vêm de resposta do WebSocket, em vez de apenas os áudios brutos gravados.

## Funcionalidades Implementadas

### 1. Salvamento Automático de Áudios Processados
- Quando uma sessão de gravação é finalizada, o áudio processado recebido via WebSocket é automaticamente salvo no diretório Downloads
- O arquivo é nomeado com o padrão: `calmwave_processed_YYYYMMDD_HHMMSS.wav`

### 2. Listagem de Áudios Originais e Processados
- A lista de arquivos agora mostra tanto os áudios originais quanto os processados
- Cada arquivo é identificado com um ícone:
  - 🎤 Arquivos originais (gravados)
  - 🎵 Arquivos processados (tratados via WebSocket)

### 3. Botão Manual para Salvar Áudio Processado
- Adicionado botão "Salvar Processado" na interface
- Permite salvar manualmente o último áudio processado da sessão atual

### 4. Identificação Visual dos Tipos de Arquivo
- Os arquivos são claramente identificados como "(Original)" ou "(Processado)"
- A lista é ordenada por data de modificação (mais recentes primeiro)

## Arquivos Modificados

### MainActivity.kt
- `listProcessedWavFiles()`: Lista arquivos processados do diretório interno da app
- `saveProcessedAudioToDownloads()`: Copia arquivo processado para Downloads
- `listAllWavFiles()`: Combina arquivos originais e processados
- Interface atualizada para mostrar botão "Salvar Processado"
- Callback configurado para salvamento automático

### MainViewModel.kt
- `saveProcessedAudio()`: Obtém caminho do arquivo processado atual
- `setProcessedAudioSaveCallback()`: Configura callback para salvamento automático
- Lógica de salvamento automático integrada ao `stopRecordingAndProcess()`

### AudioService.kt
- `getLatestProcessedFile()`: Retorna referência ao arquivo processado atual
- `getCurrentProcessedFilePath()`: Retorna caminho do arquivo processado

## Fluxo de Funcionamento

1. **Durante a Gravação:**
   - Áudio é gravado normalmente e salvo no Downloads
   - Chunks são enviados via WebSocket para processamento
   - Resposta processada é recebida e reproduzida em tempo real
   - Resposta processada é salva em arquivo temporário interno

2. **Ao Finalizar a Gravação:**
   - Arquivo de gravação original permanece no Downloads
   - Arquivo processado é automaticamente copiado para Downloads
   - Ambos os arquivos aparecem na lista da interface
   - Toast confirma o salvamento do arquivo processado

3. **Na Interface:**
   - Lista mostra todos os arquivos (originais e processados)
   - Usuário pode reproduzir qualquer arquivo da lista
   - Botão "Salvar Processado" permite salvamento manual adicional
   - Botão "Recarregar" atualiza a lista completa

## Localização dos Arquivos

- **Arquivos Originais:** Downloads/calmwave_recording_YYYYMMDD_HHMMSS.wav
- **Arquivos Processados:** Downloads/calmwave_processed_YYYYMMDD_HHMMSS.wav
- **Temporários Processados:** getExternalFilesDir(null)/processed_TIMESTAMP.wav

## Benefícios

1. **Conveniência:** Salvamento automático dos áudios processados
2. **Organização:** Clara distinção entre áudios originais e processados
3. **Flexibilidade:** Opção de salvar manualmente quando necessário
4. **Acessibilidade:** Todos os arquivos ficam no Downloads, acessível por outros apps
5. **Preservação:** Tanto o áudio original quanto o processado são mantidos
