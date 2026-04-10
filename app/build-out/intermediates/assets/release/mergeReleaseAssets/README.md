## Modelo ONNX para Denoising Local

Este diretório deve conter o arquivo `denoiser_model.onnx` convertido do modelo PyTorch.

### Como gerar o modelo

1. Acesse o diretório do API Gateway:
   ```bash
   cd API_gateway
   ```

2. Execute o script de conversão:
   ```bash
   python convert_model_to_onnx.py
   ```

3. Copie o arquivo gerado para este diretório:
   ```bash
   cp denoiser_model.onnx ../Application_mobile/app/src/main/assets/
   ```

### Requisitos para conversão
- Python 3.8+
- PyTorch (`pip install torch`)
- O arquivo `best_denoiser_model.pth` no diretório API_gateway

### Dimensões do modelo
- **Input**: `(1, 1, 257, 251)` — espectrograma log-magnitude
- **Output**: `(1, 1, 257, 251)` — máscara de denoising (valores 0–1)
- **Parâmetros**: n_fft=512, hop_length=128, segment_length=32000, sample_rate=16000
