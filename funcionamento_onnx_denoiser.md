# Arquitetura e Funcionamento do Modelo ONNX (Denoising Offline)

O aplicativo CalmWave conta com um motor de remoção de ruídos (Denoising) embarcado no próprio dispositivo. Ele processa trechos de áudio utilizando transformadas matemáticas, executa uma Rede Neural (U-Net) localmente via ONNX e retorna o sinal de voz limpo.

Neste documento, exploramos detalhadamente as etapas de processamento contidas nos arquivos `LocalAudioDenoiser.kt` e `SignalProcessor.kt`.

---

## 1. Origem Matemática do Modelo (PyTorch -> ONNX)
A arquitetura baseia-se em um modelo exportado para a extensão `.onnx` (`denoiser_model.onnx`). 

**Treinamento em PyTorch:** Originalmente, este modelo de rede convolucional (arquitetura U-Net) foi construído e treinado usando a linguagem Python e o framework **PyTorch**. Após a Inteligência Artificial apreender a distinguir voz humana de ruído na base de dados, a rede foi consolidada (congelamento dos pesos) e exportada do PyTorch para o padrão unificado **ONNX** (Open Neural Network Exchange).

Durante a inicialização da tela de gravação do aplicativo, o sistema inicia o *ONNX Runtime* e carrega a `OrtSession`. Esse ambiente reside na memória RAM do dispositivo móvel e executa inferências fundamentadas na matemática original do PyTorch, operando quase instantaneamente para evitar latência.

## 2. Preparação do Sinal Numérico (PCM ↔ Float)
O microfone gera amostras em formato bruto (*raw*), com números inteiros de 16-bits numa escala de `-32768 a 32767` (PCM).
Como o modelo ONNX requer dados contínuos de ponto flutuante, a primeira etapa ajusta esses valores:
1. **Conversão para Float:** Cada amostra linear é dividida pela amplitude máxima de 16 bits transcrevendo a onda para o intervalo fechado `[-1.0, 1.0]`.
2. **Normalização Total:** O aplicativo encontra o pico de volume mais alto no trecho analisado e divide todo o bloco por essa constante. Isso ajusta a intensidade sonora de forma análoga à normalização ocorrida durante a alimentação de dados no treinamento em PyTorch.

## 3. STFT (Transformada Rápida de Fourier de Curto Termo)
Redes neurais enfrentam grande dificuldade para isolar frequências analisando diretamente o áudio como uma onda linear no domínio do tempo.
Para resolver isso, implementou-se em Kotlin a matemática equivalente à função `torch.stft(window=hann, center=True)`, encontrada dentro de `SignalProcessor.kt`:

- O áudio contínuo de 2 segundos é dividido em centenas de pequenas janelas de tempo com sobreposição (*overlap*). Uma janela *Hann* é aplicada para atenuar as bordas de corte.
- É aplicado o algoritmo de *Cooley-Tukey (Fast Fourier Transform radix-2)*.
- O áudio no domínio do tempo transforma-se no domínio da frequência, gerando duas matrizes bidimensionais:
   - **Magnitude:** A intensidade do sinal detectado em cada faixa de frequência.
   - **Phase (Fases):** O componente temporal que assegura o alinhamento das ondas originais, prevenindo distorções, ecos e vozes "metalizadas".
   
*Parâmetros técnicos espelhados do código Python:* `N_FFT = 512` e `HOP_LENGTH = 128`. Isso gera a exata matriz de entrada esperada pelo modelo: `257 Bins` (frequências) longitudinais por *≈ 251 frames* temporais.

## 4. Compressão Logarítmica e Inferência ONNX (A Rede U-Net)
Por si só, a rede U-Net não separa perfeitamente ruído e fala baseada apenas num espectrograma linear bruto, já que altos picos de volume poderiam causar desbalanceamento nos pesos da predição.
O modelo recebeu um pré-ajuste importante: a compressão logarítmica via **Log1p** (`ln(1.0 + magnitude)`), operação idêntica à famosa instrução `torch.log1p(torch.abs(...))`.
Essa função amplifica diferenças em intensidades sonoras baixas (ruídos sutis ao fundo), deixando o padrão mais nítido para a IA, e atenua crescimentos extremos de volume.

- **Configurando a Entrada (Input Tensor):** A dimensão 4D clássica dos testes em PyTorch é adaptada na API do ONNX Runtime com o formato Exato: Tensor `[1, 1, 257, 251]`.
- A inferência entra em execução utilizando aceleração nativa C++ portada para Android.
- **A Máscara Resultante:** A inteligência artificial não tenta sintetizar uma "voz nova". Ela opera como um "filtro dinâmico". O motor ONNX produz um Tensor de saída (Output) com a mesma dimensão, mas preenchido com decimais oscilando entre `0.0f` e `1.0f`. Chamamos isso de **Máscara** — a rede identifica cada valor da grade indicando o nível de atenuação exigido ali (ex: `0.0` para ruído e `1.0` para fala).

## 5. Aplicação da Máscara (O Corte de Frequências)
O aplicativo obtém tal matriz da máscara e executa o processamento contrário a favor da limpeza:
1. Reverte a ampliação matemática inicial (`exp(log) - 1.0f`), obtendo os valores absolutos de magnitude originais.
2. Realiza a multiplicação elemento a elemento (*element-wise dot-product*) da Grade Original vezes o seu espelho na Grade da Máscara produzida.
3. Se a máscara identificou que a frequência no *Bin 32 / Frame 100* não é voz (digamos, o ronco de uma geladeira), o coeficiente vindo do ONNX ali será mínimo (ex: `0.01`). O volume dessa frequência será escalado à `1%` do seu som natural. Já para uma vogal humana (ex: score `0.99`), ela fica inalterada. Resta apenas silêncio e voz.

## 6. O Caminho de Volta (ISTFT e Reconstrução PCM)
Neste ponto operante, detemos um espectrograma isolado digitalmente da poluição acústica primária.
O aplicativo converte então o resultado à um formato audível:
- Evoca-se a função Inversa: `SignalProcessor.istft(...)`, recriando fielmente `torch.istft(..., center=True)`. Este cálculo mescla as novas Magnitudes silenciadas juntamente às "Fases" intactas que ficaram guardadas no Passo 3. 
- A transformada inversa agrupa tudo em um sinal temporal ondulatório coerente em vetor do tipo contínuo (`FloatArray`). 
- Em seguida, há o escalonamento final (`* 32767`), transformando novamente o cálculo matemático decimal sem falhas pro mundo inteiro de 16-bits original (`ByteArray`).

O processamento termina e os micro-trechos entram nas bibliotecas nativas de stream do Android (`AudioTrack`). A remoção de ruído baseada em U-Net flui continuamente com atraso reduzido e com altíssima taxa processual diretamente no alto-falante principal do usuário.
