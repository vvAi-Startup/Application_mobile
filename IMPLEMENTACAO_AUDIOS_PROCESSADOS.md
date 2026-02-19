# Como Funciona o Sistema de Áudios Limpos

## O que este documento explica

Este documento conta como o CalmWave salva os áudios depois de limpar os ruídos, e como você pode acessá-los.

## O que foi implementado

### 1. Salvamento Automático dos Áudios Limpos

Quando você termina uma gravação, acontece o seguinte automaticamente:

- **O áudio limpo é salvo** no seu celular (pasta Downloads)
- **O nome do arquivo** segue o padrão: `calmwave_processed_AAAAMMDD_HHMMSS.wav`
  - Exemplo: `calmwave_processed_20260219_143500.wav`
  - Isso significa: áudio processado de 19/02/2026 às 14:35:00

**Ou seja:** Você não precisa fazer nada! O app salva automaticamente.

### 2. Lista Mostra os Dois Tipos de Áudio

Na lista de áudios, você vê:

- 🎤 **Áudios Originais** - Aqueles que você gravou (podem ter ruído)
- 🎵 **Áudios Limpos** - Aqueles que foram processados (sem ruído)

**Identificação visual:**
- Cada arquivo mostra se é "(Original)" ou "(Processado)"
- Os mais recentes aparecem primeiro

### 3. Botão para Salvar Manualmente

Além do salvamento automático, você também pode:

- Tocar no botão **"Salvar Processado"**
- Isso salva manualmente o último áudio limpo novamente
- Útil se você quiser fazer uma segunda cópia

### 4. Identificação Clara

Na interface, tudo está bem identificado:

- **Ícones diferentes** para originais e processados
- **Nomes claros** mostram "(Original)" ou "(Processado)"
- **Ordem por data** - mais novos aparecem primeiro
- **Fácil de encontrar** o que você procura

## Arquivos que Foram Modificados

Estes arquivos do código foram alterados para fazer tudo isso funcionar:

### MainActivity.kt (Tela Principal)
- `listProcessedWavFiles()` - Lista os áudios processados guardados no app
- `saveProcessedAudioToDownloads()` - Copia o áudio limpo para Downloads
- `listAllWavFiles()` - Junta os originais e processados numa lista só
- Interface atualizada - Adicionou o botão "Salvar Processado"
- Salvamento automático - Configurado para salvar sozinho quando você termina

### MainViewModel.kt (Cérebro do App)  
- `saveProcessedAudio()` - Busca onde está o áudio limpo atual
- `setProcessedAudioSaveCallback()` - Define o que fazer quando terminar de limpar
- Lógica automática - Integrado no `stopRecordingAndProcess()`

### AudioService.kt (Serviço de Áudio)
- `getLatestProcessedFile()` - Retorna o último áudio que foi limpo
- `getCurrentProcessedFilePath()` - Mostra onde está salvo o áudio limpo

## Como Tudo Funciona na Prática

### 1. Durante a Gravação:

```
Você está falando no microfone
          ↓
Áudio é gravado normalmente
          ↓
Áudio vai sendo salvo (original) em Downloads
          ↓
Ao mesmo tempo, pedacinhos são enviados para limpeza
          ↓
Você já ouve o resultado limpo em tempo real
          ↓
O áudio limpo vai sendo salvo numa pasta temporária
```

### 2. Quando Você Termina de Gravar:

```
Você toca em "Encerrar"
          ↓
Arquivo original FICA em Downloads
          ↓
Arquivo limpo é COPIADO automaticamente para Downloads
          ↓
Agora você tem DOIS arquivos em Downloads:
  • calmwave_recording_20260219_143500.wav (original)
  • calmwave_processed_20260219_143500.wav (limpo)
          ↓
Lista é atualizada mostrando ambos
          ↓
Mensagem confirma: "Áudio processado salvo!"
```

### 3. Na Interface do App:

- **Lista completa** mostra todos os arquivos (originais e limpos)
- **Você pode tocar** qualquer um para ouvir
- **Botão "Salvar Processado"** permite salvar manualmente se quiser
- **Botão "Recarregar"** atualiza a lista completa

## Onde os Arquivos Ficam Salvos

### Arquivos Originais (os que você gravou):
- **Localização**: Downloads do celular
- **Nome**: `calmwave_recording_ANO+MES+DIA_HORA+MIN+SEG.wav`
- **Exemplo**: `calmwave_recording_20260219_143500.wav`
- **Quando**: Salvos durante a gravação

### Arquivos Limpos (processados):
- **Localização**: Downloads do celular
- **Nome**: `calmwave_processed_ANO+MES+DIA_HORA+MIN+SEG.wav`
- **Exemplo**: `calmwave_processed_20260219_143500.wav`
- **Quando**: Salvos quando você encerra a gravação

### Arquivos Temporários:
- **Localização**: Pasta interna do app (não é visível)
- **Nome**: `processed_TIMESTAMP.wav`
- **Quando**: Durante o processamento
- **Depois**: São copiados para Downloads

## Benefícios Deste Sistema

1. **Conveniência** 
   - Salvamento automático - você não precisa lembrar de salvar
   
2. **Organização** 
   - Clara distinção entre originais e limpos
   - Fácil de identificar cada tipo
   
3. **Flexibilidade** 
   - Opção de salvar manualmente quando quiser
   - Você controla suas cópias
   
4. **Acessibilidade** 
   - Todos os arquivos em Downloads
   - Outros aplicativos também podem acessá-los
   - Fácil compartilhar com amigos
   
5. **Preservação** 
   - Original E limpo são mantidos
   - Você nunca perde nada
   - Pode comparar os dois

## Resumo Simples

**Antes:** Só gravava e salvava o original
**Agora:** Grava, limpa, E salva os dois!

**O que você ganha:**
- ✅ Áudio limpo salvo automaticamente
- ✅ Áudio original também guardado
- ✅ Fácil de identificar qual é qual  
- ✅ Tudo organizado em Downloads
- ✅ Pode compartilhar facilmente
