# 📊 Implementação de Banco de Dados para Análise - CalmWave

**Data de criação:** 19 de Fevereiro de 2026  
**Versão:** 1.0  
**Status:** Proposta de Implementação

---

## 📋 O que este documento explica

1. [Por que precisamos de um banco de dados](#por-que-precisamos-de-um-banco-de-dados)
2. [Estrutura do banco de dados](#estrutura-do-banco-de-dados)
3. [Dados úteis para análise](#dados-úteis-para-análise)
4. [Sistema de login e autenticação](#sistema-de-login-e-autenticação)
5. [Perfil de usuário](#perfil-de-usuário)
6. [Como coletar os dados](#como-coletar-os-dados)
7. [Gráficos e análises possíveis](#gráficos-e-análises-possíveis)
8. [Implementação técnica](#implementação-técnica)
9. [Privacidade e segurança](#privacidade-e-segurança)

---

## 🎯 Por que precisamos de um banco de dados

### Situação Atual

Atualmente, o CalmWave:
- ✅ Grava e limpa áudios
- ✅ Organiza em pastas
- ✅ Salva configurações básicas (SharedPreferences)
- ❌ **Não tem** login de usuário
- ❌ **Não coleta** dados de uso
- ❌ **Não gera** análises ou estatísticas
- ❌ **Não sincroniza** entre dispositivos

### O que vamos ganhar

Com um banco de dados estruturado, teremos:

1. **Sistema de Usuários**
   - Login e cadastro
   - Perfil personalizado
   - Segurança dos dados

2. **Análise de Uso**
   - Quanto tempo o usuário grava por dia
   - Quais funcionalidades mais usa
   - Padrões de comportamento

3. **Estatísticas**
   - Total de áudios gravados
   - Tempo total gravado
   - Eficiência da limpeza de ruído
   - Horários de maior uso

4. **Melhorias no App**
   - Entender o que os usuários mais fazem
   - Identificar problemas
   - Personalizar experiência

5. **Sincronização**
   - Usar em vários celulares
   - Backup automático na nuvem
   - Não perder dados

---

## 🗄️ Estrutura do banco de dados

### Visão geral das tabelas

O banco de dados terá **7 tabelas principais**:

```
┌─────────────────────────────────────────────────────────────┐
│                    BANCO DE DADOS                            │
│                      CalmWave DB                             │
└─────────────────────────────────────────────────────────────┘

┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   USUÁRIOS   │────▶│   ÁUDIOS     │────▶│  PLAYLISTS   │
│              │     │              │     │              │
│ • ID         │     │ • ID         │     │ • ID         │
│ • Email      │     │ • Usuário ID │     │ • Nome       │
│ • Senha      │     │ • Arquivo    │     │ • Cor        │
│ • Nome       │     │ • Duração    │     │ • Usuário    │
│ • Foto       │     │ • Data       │     └──────────────┘
│ • Data       │     │ • Processado │
└──────┬───────┘     │ • Tamanho    │
       │             └──────────────┘
       │
       ├─────────┐
       │         │
       ▼         ▼
┌──────────────┐ ┌──────────────┐
│   SESSÕES    │ │ ESTATÍSTICAS │
│              │ │              │
│ • ID         │ │ • Usuário ID │
│ • Usuário    │ │ • Data       │
│ • Token      │ │ • Gravações  │
│ • Expiração  │ │ • Tempo      │
└──────────────┘ └──────────────┘

       │
       ▼
┌──────────────┐
│   EVENTOS    │
│              │
│ • ID         │
│ • Usuário    │
│ • Ação       │
│ • Data/Hora  │
│ • Detalhes   │
└──────────────┘

       │
       ▼
┌──────────────┐
│ CONFIGURAÇÕES│
│              │
│ • Usuário ID │
│ • Chave      │
│ • Valor      │
└──────────────┘
```

---

## 📊 Tabelas Detalhadas

### 1. Tabela: USUÁRIOS (users)

**O que guarda:** Informações de cada pessoa que usa o app

| Campo | Tipo | O que é | Exemplo |
|-------|------|---------|---------|
| `id` | Inteiro | Número único do usuário | 1, 2, 3... |
| `email` | Texto | Email para login | usuario@email.com |
| `senha_hash` | Texto | Senha criptografada | $2a$10$... |
| `nome` | Texto | Nome da pessoa | João Silva |
| `foto_perfil_url` | Texto | Caminho da foto | /fotos/user1.jpg |
| `data_criacao` | Data/Hora | Quando criou a conta | 2026-02-19 10:30:00 |
| `data_ultimo_acesso` | Data/Hora | Última vez que usou | 2026-02-19 14:25:00 |
| `ativo` | Verdadeiro/Falso | Conta ativa? | true |
| `tipo_conta` | Texto | Tipo de usuário | gratuito/premium |

**Por que cada campo:**
- `id` - Identificar cada usuário único
- `email` - Para fazer login
- `senha_hash` - Senha segura (nunca salvamos a senha real!)
- `nome` - Personalizar o app ("Olá, João!")
- `foto_perfil_url` - Avatar do usuário
- `data_criacao` - Saber quando entrou
- `data_ultimo_acesso` - Ver se está usando
- `ativo` - Poder desativar conta sem deletar
- `tipo_conta` - Diferenciar planos

---

### 2. Tabela: ÁUDIOS (audios)

**O que guarda:** Cada áudio que o usuário grava

| Campo | Tipo | O que é | Exemplo |
|-------|------|---------|---------|
| `id` | Inteiro | Número único do áudio | 1001 |
| `usuario_id` | Inteiro | Quem gravou | 1 |
| `nome_arquivo` | Texto | Nome do arquivo | calmwave_20260219.wav |
| `caminho_arquivo` | Texto | Onde está salvo | /Downloads/... |
| `duracao_segundos` | Inteiro | Quanto tempo dura | 120 (2 minutos) |
| `tamanho_bytes` | Inteiro | Tamanho do arquivo | 1048576 (1 MB) |
| `data_gravacao` | Data/Hora | Quando foi gravado | 2026-02-19 14:30:00 |
| `processado` | Verdadeiro/Falso | Foi limpo? | true |
| `caminho_processado` | Texto | Arquivo limpo | /Downloads/...processed.wav |
| `tempo_processamento_ms` | Inteiro | Quanto demorou limpar | 250 (0.25 segundos) |
| `transcrito` | Verdadeiro/Falso | Foi transcrito? | false |
| `texto_transcricao` | Texto | O que foi dito | "Olá, como vai?" |
| `favorito` | Verdadeiro/Falso | É favorito? | true |
| `playlist_id` | Inteiro | Em qual pasta está | 5 |
| `dispositivo_origem` | Texto | Qual celular gravou | Samsung Galaxy S23 |

**Por que cada campo:**
- Identificar cada áudio
- Saber de quem é
- Localizar o arquivo
- Calcular estatísticas (tempo total)
- Análise de performance (tempo de processamento)
- Buscar por texto
- Organização

---

### 3. Tabela: PLAYLISTS (playlists)

**O que guarda:** Pastas de organização

| Campo | Tipo | O que é | Exemplo |
|-------|------|---------|---------|
| `id` | Inteiro | Número da playlist | 1 |
| `usuario_id` | Inteiro | Dono da pasta | 1 |
| `nome` | Texto | Nome dado | "Reuniões" |
| `cor` | Texto | Cor escolhida | "#6FAF9E" |
| `data_criacao` | Data/Hora | Quando criou | 2026-02-19 10:00:00 |
| `total_audios` | Inteiro | Quantos áudios tem | 15 |
| `ordem` | Inteiro | Posição na lista | 1 |

---

### 4. Tabela: SESSÕES (sessions)

**O que guarda:** Controle de quem está logado

| Campo | Tipo | O que é | Exemplo |
|-------|------|---------|---------|
| `id` | Inteiro | Número da sessão | 1 |
| `usuario_id` | Inteiro | Qual usuário | 1 |
| `token` | Texto | Código secreto | abc123xyz789... |
| `data_criacao` | Data/Hora | Quando fez login | 2026-02-19 10:00:00 |
| `data_expiracao` | Data/Hora | Quando expira | 2026-03-19 10:00:00 |
| `ativo` | Verdadeiro/Falso | Ainda válido? | true |
| `dispositivo_info` | Texto | Informações do celular | "Samsung Galaxy S23, Android 14" |

**Por que:**
- Manter usuário logado
- Segurança (tokens expiram)
- Saber quantos dispositivos usam

---

### 5. Tabela: ESTATÍSTICAS (statistics)

**O que guarda:** Resumo diário de uso

| Campo | Tipo | O que é | Exemplo |
|-------|------|---------|---------|
| `id` | Inteiro | Número do registro | 1 |
| `usuario_id` | Inteiro | Qual usuário | 1 |
| `data` | Data | Dia do registro | 2026-02-19 |
| `total_gravacoes` | Inteiro | Quantos áudios gravou | 5 |
| `tempo_total_gravado_segundos` | Inteiro | Segundos gravados | 600 (10 minutos) |
| `tempo_total_processamento_ms` | Inteiro | Tempo de limpeza | 1500 (1.5 segundos) |
| `audios_transcritos` | Inteiro | Quantos transcreveu | 2 |
| `tempo_total_uso_app_segundos` | Inteiro | Tempo no app | 1200 (20 minutos) |
| `playlists_criadas` | Inteiro | Pastas novas | 1 |
| `audios_excluidos` | Inteiro | Áudios deletados | 0 |

**Por que:**
- Fácil ver resumo do dia
- Gerar gráficos rápido
- Comparar dias diferentes

---

### 6. Tabela: EVENTOS (events)

**O que guarda:** Cada ação que o usuário faz

| Campo | Tipo | O que é | Exemplo |
|-------|------|---------|---------|
| `id` | Inteiro | Número do evento | 1 |
| `usuario_id` | Inteiro | Quem fez | 1 |
| `tipo_evento` | Texto | O que fez | "gravacao_iniciada" |
| `data_hora` | Data/Hora | Quando | 2026-02-19 14:30:15 |
| `detalhes_json` | Texto | Informações extras | {"duracao": 120} |
| `tela` | Texto | Onde estava | "GravarActivity" |

**Tipos de evento:**
- `app_aberto` - Abriu o aplicativo
- `gravacao_iniciada` - Começou a gravar
- `gravacao_pausada` - Pausou gravação
- `gravacao_finalizada` - Terminou gravação
- `audio_reproduzido` - Ouviu um áudio
- `audio_transcrito` - Pediu transcrição
- `playlist_criada` - Criou pasta nova
- `audio_adicionado_favoritos` - Marcou favorito
- `audio_removido_favoritos` - Desmarcou favorito
- `audio_excluido` - Deletou áudio
- `configuracao_alterada` - Mudou configuração
- `app_fechado` - Saiu do app

**Por que:**
- Entender como usuário usa
- Ver quais funcionalidades são populares
- Identificar problemas (muitos usuários fechando em uma tela específica)

---

### 7. Tabela: CONFIGURAÇÕES (settings)

**O que guarda:** Preferências do usuário

| Campo | Tipo | O que é | Exemplo |
|-------|------|---------|---------|
| `usuario_id` | Inteiro | Qual usuário | 1 |
| `chave` | Texto | Nome da configuração | "tema_escuro" |
| `valor` | Texto | Valor escolhido | "true" |
| `data_atualizacao` | Data/Hora | Última mudança | 2026-02-19 10:00:00 |

**Configurações possíveis:**
- `tema_escuro` - Tema claro ou escuro
- `notificacoes_ativas` - Receber notificações
- `qualidade_audio` - Alta/Média/Baixa
- `auto_limpeza` - Limpar ruído automaticamente
- `idioma_transcricao` - Português, Inglês, etc.
- `backup_automatico` - Fazer backup na nuvem

---

## 💡 Dados úteis para análise

### Métricas de Uso do Aplicativo

#### 1. Uso Diário
- **Total de usuários ativos** por dia
- **Tempo médio** de uso do app
- **Horários de pico** (quando mais gravam)
- **Dias da semana** mais usados

#### 2. Gravações
- **Total de gravações** por dia/semana/mês
- **Duração média** das gravações
- **Áudios mais longos** vs **mais curtos**
- **Taxa de conclusão** (quantos finalizam a gravação)
- **Taxa de pausa** (quantos pausam durante gravação)

#### 3. Processamento
- **Tempo médio de limpeza** de ruído
- **Taxa de sucesso** do processamento
- **Performance por dispositivo** (celulares mais rápidos/lentos)
- **Uso de processamento offline** vs **online**

#### 4. Transcrição
- **Quantos áudios** são transcritos
- **Idiomas** mais usados
- **Taxa de sucesso** da transcrição
- **Tempo médio** de transcrição

#### 5. Organização
- **Quantas playlists** cada usuário tem
- **Média de áudios** por playlist
- **Cores** mais populares
- **Uso de favoritos**

#### 6. Retenção
- **Quantos voltam** no dia seguinte
- **Quantos voltam** na semana seguinte
- **Taxa de abandono** (param de usar)
- **Tempo até primeiro áudio**

---

## 🔐 Sistema de login e autenticação

### Fluxo de Cadastro

```
USUÁRIO ABRE O APP PELA PRIMEIRA VEZ
          │
          ▼
┌─────────────────────────────┐
│  TELA DE BEM-VINDO          │
│                             │
│  [Criar Conta] [Entrar]     │
└─────────────────────────────┘
          │
          │ (escolhe Criar Conta)
          ▼
┌─────────────────────────────┐
│  FORMULÁRIO DE CADASTRO     │
│                             │
│  Nome: ___________          │
│  Email: __________          │
│  Senha: __________          │
│  Confirmar: _______          │
│                             │
│  [ ] Aceito os termos       │
│                             │
│  [Criar Conta]              │
└─────────────────────────────┘
          │
          ▼
   VALIDAÇÕES NO APP
   ✓ Email válido?
   ✓ Senha forte? (min 8 caracteres)
   ✓ Senhas coincidem?
   ✓ Termos aceitos?
          │
          ▼
   ENVIA PARA SERVIDOR
   POST /api/auth/register
   {
     "nome": "João",
     "email": "joao@email.com",
     "senha": "senhaSegura123"
   }
          │
          ▼
   SERVIDOR PROCESSA
   • Verifica se email já existe
   • Criptografa senha (bcrypt)
   • Cria usuário no banco
   • Gera token de sessão
          │
          ▼
   RETORNA PARA O APP
   {
     "sucesso": true,
     "usuario": {...},
     "token": "abc123..."
   }
          │
          ▼
   APP SALVA LOCALMENTE
   • Token em SharedPreferences
   • Info do usuário em cache
          │
          ▼
   REDIRECIONA PARA APP
   ✅ Usuário logado!
```

### Fluxo de Login

```
USUÁRIO ABRE O APP
          │
          ▼
   TEM TOKEN SALVO?
       │        │
       SIM      NÃO
       │        │
       ▼        ▼
   VALIDA    TELA DE
   TOKEN     LOGIN
       │        │
       ▼        │
   VÁLIDO?      │
    │    │      │
   SIM  NÃO     │
    │    │      │
    │    └──────┘
    │           │
    ▼           ▼
  APP     FAZ LOGIN
PRINCIPAL  (email + senha)
              │
              ▼
         RECEBE TOKEN
              │
              ▼
         SALVA LOCAL
              │
              ▼
         APP PRINCIPAL
```

### Segurança

**O que fazemos:**

1. **Senhas nunca guardadas em texto puro**
   - Usamos bcrypt para criptografar
   - Exemplo: `senha123` vira `$2a$10$N9qo8uLOickgx2ZMRZoMye...`

2. **Tokens de sessão**
   - Gerados aleatoriamente
   - Expiram em 30 dias
   - Renovados automaticamente

3. **HTTPS obrigatório**
   - Toda comunicação criptografada
   - Ninguém consegue interceptar

4. **Validação dupla**
   - No app (rápido)
   - No servidor (seguro)

---

## 👤 Perfil de usuário

### Informações do Perfil

```
┌─────────────────────────────────────┐
│         PERFIL DO USUÁRIO           │
├─────────────────────────────────────┤
│                                     │
│        [  FOTO  ]                   │
│                                     │
│  Nome: João Silva                   │
│  Email: joao@email.com              │
│  Membro desde: 19/02/2026           │
│                                     │
├─────────────────────────────────────┤
│         ESTATÍSTICAS                │
├─────────────────────────────────────┤
│  🎙️  Total de Gravações: 47        │
│  ⏱️  Tempo Gravado: 2h 35min        │
│  🎵  Áudios Limpos: 47              │
│  📝  Transcrições: 12               │
│  📁  Playlists: 5                   │
│  ⭐ Favoritos: 8                    │
│                                     │
├─────────────────────────────────────┤
│         CONQUISTAS                  │
├─────────────────────────────────────┤
│  🏆 Primeira Gravação               │
│  🎖️ 10 Gravações                    │
│  🔥 7 Dias Seguidos                 │
│  ⚡ Gravação Longa (30min+)         │
│                                     │
└─────────────────────────────────────┘
```

### Campos Editáveis

O usuário pode mudar:
- ✏️ Nome
- 📸 Foto de perfil
- 🔔 Preferências de notificação
- 🎨 Tema (claro/escuro)
- 🌍 Idioma preferido
- 🔊 Qualidade de áudio padrão

Não pode mudar:
- ❌ Email (seria muito complexo)
- ❌ Data de criação
- ❌ Estatísticas (são calculadas)

---

## 📈 Como coletar os dados

### Coleta Automática

O app vai coletar dados automaticamente em momentos específicos:

#### 1. Quando o app abre
```kotlin
fun onAppOpened() {
    // Registra evento
    salvarEvento(
        tipo = "app_aberto",
        tela = "SplashActivity",
        detalhes = mapOf(
            "versao_app" to "1.0",
            "dispositivo" to Build.MODEL
        )
    )
}
```

#### 2. Durante a gravação
```kotlin
fun onGravacaoIniciada(arquivo: String) {
    salvarEvento(
        tipo = "gravacao_iniciada",
        detalhes = mapOf(
            "arquivo" to arquivo,
            "hora_inicio" to System.currentTimeMillis()
        )
    )
}

fun onGravacaoFinalizada(duracao: Int, tamanho: Long) {
    // Salva o áudio no banco
    salvarAudio(
        nome = arquivo,
        duracao = duracao,
        tamanho = tamanho,
        processado = false
    )
    
    // Registra evento
    salvarEvento(
        tipo = "gravacao_finalizada",
        detalhes = mapOf(
            "duracao_segundos" to duracao,
            "tamanho_bytes" to tamanho
        )
    )
    
    // Atualiza estatísticas do dia
    atualizarEstatisticasDia(
        totalGravacoes = +1,
        tempoGravado = +duracao
    )
}
```

#### 3. Após limpeza de áudio
```kotlin
fun onAudioProcessado(audioId: Int, tempoMs: Long) {
    // Atualiza registro do áudio
    atualizarAudio(
        id = audioId,
        processado = true,
        tempoProcessamento = tempoMs
    )
    
    // Registra evento
    salvarEvento(
        tipo = "audio_processado",
        detalhes = mapOf(
            "audio_id" to audioId,
            "tempo_processamento_ms" to tempoMs
        )
    )
    
    // Atualiza estatísticas
    atualizarEstatisticasDia(
        tempoProcessamento = +tempoMs
    )
}
```

#### 4. Quando ouve um áudio
```kotlin
fun onAudioReproduzido(audioId: Int) {
    salvarEvento(
        tipo = "audio_reproduzido",
        detalhes = mapOf(
            "audio_id" to audioId,
            "velocidade" to velocidadeReproducao
        )
    )
}
```

### Coleta com Privacidade

**O que NÃO coletamos:**
- ❌ Conteúdo dos áudios
- ❌ Texto das transcrições (apenas que foi feito)
- ❌ Localização GPS
- ❌ Contatos
- ❌ Outras informações pessoais

**O que coletamos:**
- ✅ Quanto tempo usou
- ✅ Quais funcionalidades usou
- ✅ Performance (tempo de processamento)
- ✅ Erros que aconteceram

**Sempre:**
- 🔒 Dados criptografados em trânsito
- 🔒 Anonimização quando possível
- 🔒 Usuário pode ver seus dados
- 🔒 Usuário pode deletar conta e dados

---

## 📊 Gráficos e análises possíveis

### Para o Usuário Ver (no app)

#### 1. Gráfico de Uso Semanal
```
Gravações por Dia

15 │     ███
10 │ ███ ███ ███
 5 │ ███ ███ ███ ███
 0 │ ███ ███ ███ ███ ███ 
   └──────────────────────
     Seg Ter Qua Qui Sex
```

#### 2. Tempo Total Gravado
```
📊 Esta Semana: 2h 35min
📈 +15% em relação à semana passada

Por dia:
Seg ████████░░ 45min
Ter ██████░░░░ 30min
Qua ████████░░ 40min
Qui ░░░░░░░░░░  0min
Sex ████░░░░░░ 20min
```

#### 3. Eficiência do Processamento
```
⚡ Velocidade Média de Limpeza
   0.25 segundos por segundo de áudio

📈 Seu celular está 20% mais rápido
   que a média dos usuários!
```

#### 4. Distribuição de Uso
```
🎙️ Tipos de Uso

Gravações Rápidas (<1min)  ████████░░ 40%
Gravações Médias (1-5min)  ██████████ 50%
Gravações Longas (>5min)   ██░░░░░░░░ 10%
```

### Para os Desenvolvedores (dashboard web)

#### 1. Usuários Ativos
- DAU (Daily Active Users) - Usuários por dia
- WAU (Weekly Active Users) - Usuários por semana
- MAU (Monthly Active Users) - Usuários por mês

#### 2. Retenção
- Dia 1: 80% voltam
- Dia 7: 50% voltam
- Dia 30: 30% voltam

#### 3. Funcionalidades Mais Usadas
```
Gravar Áudio          ████████████████████ 100%
Limpar Ruído         ██████████████████░░  90%
Ouvir Áudio          ████████████████░░░░  80%
Criar Playlist       ██████████░░░░░░░░░░  50%
Transcrever          ████████░░░░░░░░░░░░  40%
Marcar Favorito      ██████░░░░░░░░░░░░░░  30%
```

#### 4. Performance por Dispositivo
```
Dispositivo          | Tempo Médio Limpeza
---------------------|--------------------
Samsung S23          | 0.15s
Xiaomi Redmi Note 11 | 0.25s
Motorola Moto G8     | 0.45s
```

---

## 💻 Implementação técnica

### Tecnologias Recomendadas

#### Para Android (Local)

**Room Database** - Banco SQLite do Android

```kotlin
// Adicionar no build.gradle.kts
dependencies {
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")
}
```

**Por que Room:**
- ✅ Oficial do Android
- ✅ Fácil de usar
- ✅ Integração com Kotlin
- ✅ Suporte a Coroutines
- ✅ Migrations automáticas

#### Para Backend (Servidor)

**PostgreSQL** - Banco de dados relacional

**Por que PostgreSQL:**
- ✅ Gratuito e open-source
- ✅ Muito confiável
- ✅ Suporta JSON (para detalhes de eventos)
- ✅ Ótima performance
- ✅ Usado por grandes empresas

**Alternativas:**
- MySQL - Também boa opção
- MongoDB - Se preferir NoSQL
- Firebase - Solução completa do Google

### Estrutura de Código

#### 1. Definir Entidades (Room)

```kotlin
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "email")
    val email: String,
    
    @ColumnInfo(name = "password_hash")
    val passwordHash: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "profile_photo_url")
    val profilePhotoUrl: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_access")
    val lastAccess: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "active")
    val active: Boolean = true,
    
    @ColumnInfo(name = "account_type")
    val accountType: String = "free" // "free" ou "premium"
)
```

```kotlin
@Entity(tableName = "audios")
data class Audio(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "user_id")
    val userId: Int,
    
    @ColumnInfo(name = "filename")
    val filename: String,
    
    @ColumnInfo(name = "file_path")
    val filePath: String,
    
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,
    
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "processed")
    val processed: Boolean = false,
    
    @ColumnInfo(name = "processed_path")
    val processedPath: String? = null,
    
    @ColumnInfo(name = "processing_time_ms")
    val processingTimeMs: Long? = null,
    
    @ColumnInfo(name = "transcribed")
    val transcribed: Boolean = false,
    
    @ColumnInfo(name = "transcription_text")
    val transcriptionText: String? = null,
    
    @ColumnInfo(name = "favorite")
    val favorite: Boolean = false,
    
    @ColumnInfo(name = "playlist_id")
    val playlistId: Int? = null,
    
    @ColumnInfo(name = "device_origin")
    val deviceOrigin: String = Build.MODEL
)
```

#### 2. Criar DAOs (Data Access Objects)

```kotlin
@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Int): User?
    
    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): User?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long
    
    @Update
    suspend fun updateUser(user: User)
    
    @Query("UPDATE users SET last_access = :timestamp WHERE id = :userId")
    suspend fun updateLastAccess(userId: Int, timestamp: Long)
}
```

```kotlin
@Dao
interface AudioDao {
    @Query("SELECT * FROM audios WHERE user_id = :userId ORDER BY recorded_at DESC")
    suspend fun getAudiosByUser(userId: Int): List<Audio>
    
    @Query("SELECT * FROM audios WHERE user_id = :userId AND favorite = 1")
    suspend fun getFavoriteAudios(userId: Int): List<Audio>
    
    @Query("SELECT * FROM audios WHERE user_id = :userId AND playlist_id = :playlistId")
    suspend fun getAudiosByPlaylist(userId: Int, playlistId: Int): List<Audio>
    
    @Insert
    suspend fun insertAudio(audio: Audio): Long
    
    @Update
    suspend fun updateAudio(audio: Audio)
    
    @Delete
    suspend fun deleteAudio(audio: Audio)
    
    @Query("SELECT COUNT(*) FROM audios WHERE user_id = :userId")
    suspend fun getTotalAudios(userId: Int): Int
    
    @Query("SELECT SUM(duration_seconds) FROM audios WHERE user_id = :userId")
    suspend fun getTotalDuration(userId: Int): Int?
}
```

#### 3. Criar Database

```kotlin
@Database(
    entities = [
        User::class,
        Audio::class,
        Playlist::class,
        Session::class,
        Statistic::class,
        Event::class,
        Setting::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CalmWaveDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun audioDao(): AudioDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun sessionDao(): SessionDao
    abstract fun statisticDao(): StatisticDao
    abstract fun eventDao(): EventDao
    abstract fun settingDao(): SettingDao
    
    companion object {
        @Volatile
        private var INSTANCE: CalmWaveDatabase? = null
        
        fun getDatabase(context: Context): CalmWaveDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CalmWaveDatabase::class.java,
                    "calmwave_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

#### 4. Repository Pattern

```kotlin
class AudioRepository(private val audioDao: AudioDao) {
    
    suspend fun salvarAudio(
        userId: Int,
        filename: String,
        filePath: String,
        durationSeconds: Int,
        sizeBytes: Long
    ): Long {
        val audio = Audio(
            userId = userId,
            filename = filename,
            filePath = filePath,
            durationSeconds = durationSeconds,
            sizeBytes = sizeBytes
        )
        return audioDao.insertAudio(audio)
    }
    
    suspend fun marcarComoProcessado(
        audioId: Int,
        processedPath: String,
        processingTimeMs: Long
    ) {
        val audio = audioDao.getAudioById(audioId)
        audio?.let {
            val updated = it.copy(
                processed = true,
                processedPath = processedPath,
                processingTimeMs = processingTimeMs
            )
            audioDao.updateAudio(updated)
        }
    }
    
    suspend fun obterAudiosDoUsuario(userId: Int): List<Audio> {
        return audioDao.getAudiosByUser(userId)
    }
    
    suspend fun obterEstatisticas(userId: Int): AudioStatistics {
        val totalAudios = audioDao.getTotalAudios(userId)
        val totalDuration = audioDao.getTotalDuration(userId) ?: 0
        val totalProcessed = audioDao.getTotalProcessed(userId)
        
        return AudioStatistics(
            totalAudios = totalAudios,
            totalDurationSeconds = totalDuration,
            totalProcessed = totalProcessed
        )
    }
}

data class AudioStatistics(
    val totalAudios: Int,
    val totalDurationSeconds: Int,
    val totalProcessed: Int
)
```

#### 5. Integrar no ViewModel

```kotlin
class MainViewModel(
    private val audioService: AudioService,
    private val wavRecorder: WavRecorder,
    private val context: Context,
    private val audioRepository: AudioRepository, // NOVO
    private val eventRepository: EventRepository  // NOVO
) : ViewModel() {
    
    // Usuário atual (após login)
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    fun onGravacaoFinalizada(
        filename: String,
        filePath: String,
        durationSeconds: Int,
        sizeBytes: Long
    ) {
        viewModelScope.launch {
            // Salvar no banco
            val audioId = audioRepository.salvarAudio(
                userId = _currentUser.value?.id ?: 0,
                filename = filename,
                filePath = filePath,
                durationSeconds = durationSeconds,
                sizeBytes = sizeBytes
            )
            
            // Registrar evento
            eventRepository.registrarEvento(
                userId = _currentUser.value?.id ?: 0,
                tipo = "gravacao_finalizada",
                detalhes = mapOf(
                    "audio_id" to audioId,
                    "duracao" to durationSeconds,
                    "tamanho" to sizeBytes
                )
            )
            
            // Atualizar estatísticas do dia
            statisticRepository.atualizarEstatisticasDia(
                userId = _currentUser.value?.id ?: 0,
                totalGravacoes = 1,
                tempoGravado = durationSeconds
            )
        }
    }
}
```

---

## 🔒 Privacidade e segurança

### Conformidade com LGPD (Lei Geral de Proteção de Dados)

**O que a lei exige:**

1. **Consentimento claro**
   - ✅ Usuário deve concordar explicitamente
   - ✅ Explicar o que coletamos e por quê
   - ✅ Termos de uso simples e claros

2. **Direito de acesso**
   - ✅ Usuário pode ver todos seus dados
   - ✅ Pode baixar seus dados
   - ✅ Pode corrigir informações erradas

3. **Direito ao esquecimento**
   - ✅ Usuário pode deletar conta
   - ✅ Todos os dados são removidos
   - ✅ Backups também são limpos

4. **Segurança**
   - ✅ Dados criptografados
   - ✅ Acesso restrito
   - ✅ Logs de acesso

### Implementação de Privacidade

```kotlin
// Tela de Termos e Privacidade
class TermsActivity : ComponentActivity() {
    @Composable
    fun TermsScreen() {
        Column {
            Text("O que coletamos:")
            Text("• Informações de uso do app")
            Text("• Estatísticas de gravação")
            Text("• Performance do dispositivo")
            
            Text("\nO que NÃO coletamos:")
            Text("• Conteúdo dos áudios")
            Text("• Localização GPS")
            Text("• Contatos")
            
            Text("\nSeus direitos:")
            Text("• Ver seus dados")
            Text("• Baixar seus dados")
            Text("• Deletar sua conta")
            
            Checkbox(
                checked = acceptedTerms,
                onCheckedChange = { acceptedTerms = it }
            )
            Text("Aceito os termos e política de privacidade")
            
            Button(
                onClick = { createAccount() },
                enabled = acceptedTerms
            ) {
                Text("Criar Conta")
            }
        }
    }
}

// Função para exportar dados do usuário
suspend fun exportUserData(userId: Int): File {
    val userDao = database.userDao()
    val audioDao = database.audioDao()
    val eventDao = database.eventDao()
    
    val user = userDao.getUserById(userId)
    val audios = audioDao.getAudiosByUser(userId)
    val events = eventDao.getEventsByUser(userId)
    
    val data = mapOf(
        "usuario" to user,
        "audios" to audios,
        "eventos" to events
    )
    
    val json = Json.encodeToString(data)
    
    // Salva em arquivo
    val file = File(context.getExternalFilesDir(null), "meus_dados_calmwave.json")
    file.writeText(json)
    
    return file
}

// Função para deletar conta
suspend fun deleteUserAccount(userId: Int) {
    // 1. Deletar todos os arquivos de áudio
    val audios = audioDao.getAudiosByUser(userId)
    audios.forEach { audio ->
        File(audio.filePath).delete()
        audio.processedPath?.let { File(it).delete() }
    }
    
    // 2. Deletar registros do banco
    audioDao.deleteAllByUser(userId)
    playlistDao.deleteAllByUser(userId)
    eventDao.deleteAllByUser(userId)
    statisticDao.deleteAllByUser(userId)
    settingDao.deleteAllByUser(userId)
    sessionDao.deleteAllByUser(userId)
    
    // 3. Deletar usuário
    userDao.deleteUser(userId)
    
    // 4. Notificar servidor
    api.deleteUserAccount(userId)
    
    // 5. Limpar preferências locais
    SharedPreferences.clear()
    
    // 6. Sair do app
    exitApp()
}
```

---

## 📝 Resumo da Implementação

### Passo a Passo

#### Fase 1: Setup Básico (1-2 semanas)
1. ✅ Adicionar Room Database ao projeto
2. ✅ Criar entidades (User, Audio, Playlist, etc.)
3. ✅ Criar DAOs
4. ✅ Criar Database class
5. ✅ Testar localmente

#### Fase 2: Backend (2-3 semanas)
1. ✅ Configurar servidor (PostgreSQL + API)
2. ✅ Criar endpoints de autenticação
3. ✅ Criar endpoints de sincronização
4. ✅ Implementar segurança (JWT, HTTPS)
5. ✅ Testar APIs

#### Fase 3: Login e Cadastro (1-2 semanas)
1. ✅ Criar telas de login/cadastro
2. ✅ Integrar com backend
3. ✅ Implementar persistência de sessão
4. ✅ Tela de perfil
5. ✅ Recuperação de senha

#### Fase 4: Coleta de Dados (1-2 semanas)
1. ✅ Integrar salvamento de áudios
2. ✅ Registrar eventos de uso
3. ✅ Atualizar estatísticas
4. ✅ Sincronizar com servidor

#### Fase 5: Análises e Gráficos (2-3 semanas)
1. ✅ Criar queries de análise
2. ✅ Implementar gráficos no app
3. ✅ Dashboard web para desenvolvedores
4. ✅ Exportação de dados

#### Fase 6: Privacidade e Compliance (1 semana)
1. ✅ Tela de termos de uso
2. ✅ Política de privacidade
3. ✅ Função de exportar dados
4. ✅ Função de deletar conta

#### Fase 7: Testes e Ajustes (1-2 semanas)
1. ✅ Testes de performance
2. ✅ Testes de segurança
3. ✅ Correção de bugs
4. ✅ Otimizações

**Total: 9-15 semanas de desenvolvimento**

---

## 🎯 Benefícios Esperados

### Para os Usuários

1. **Experiência Personalizada**
   - Ver progresso e estatísticas
   - Conquistas e gamificação
   - Recomendações baseadas em uso

2. **Sincronização**
   - Usar em múltiplos dispositivos
   - Não perder dados
   - Backup automático

3. **Segurança**
   - Dados protegidos
   - Login seguro
   - Controle sobre informações

### Para a Empresa (vvAi Startup)

1. **Entender Usuários**
   - Como usam o app
   - Quais funcionalidades são populares
   - Onde têm dificuldades

2. **Melhorar Produto**
   - Decisões baseadas em dados
   - Priorizar funcionalidades certas
   - Corrigir problemas reais

3. **Crescimento**
   - Métricas de retenção
   - Identificar usuários mais engajados
   - Estratégias de marketing

4. **Monetização Futura**
   - Plano premium baseado em uso
   - Recursos que valem a pena pagar
   - Modelo de negócio sustentável

---

## 📞 Próximos Passos

1. **Revisão deste documento**
   - Equipe deve revisar e aprovar
   - Ajustar o que for necessário
   - Definir prioridades

2. **Prototipação**
   - Criar protótipo do login
   - Testar fluxo de usuário
   - Validar com usuários reais

3. **Desenvolvimento**
   - Começar pela Fase 1 (Setup Básico)
   - Implementar incrementalmente
   - Testar cada fase antes de avançar

4. **Lançamento Gradual**
   - Beta com usuários selecionados
   - Coletar feedback
   - Ajustar antes do lançamento geral

---

**Documento criado por:** Equipe vvAi Startup  
**Versão:** 1.0  
**Data:** 19 de Fevereiro de 2026  
**Status:** Proposta para Discussão

---

*Este documento é uma proposta inicial e deve ser revisado e ajustado pela equipe técnica antes da implementação.*
