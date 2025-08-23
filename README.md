# CalmWave - Aplicativo de Gravação e Processamento de Áudio

## Diagnóstico de Problemas de Conectividade com a API

### Problemas Comuns e Soluções

#### 1. **Servidor não está rodando**
- **Sintoma**: "Servidor não responde" ao clicar em "Testar Conexão"
- **Solução**: Certifique-se de que seu servidor Flask está rodando na porta 5000
- **Comando**: `python app.py` ou `flask run --host=0.0.0.0 --port=5000`

#### 2. **Problema de rede no emulador**
- **Sintoma**: "Falha na conexão com a API" 
- **Solução**: O emulador Android usa `10.0.2.2` para acessar o localhost do host
- **Verificação**: Use o botão "Testar Conexão" primeiro, depois "Testar API"

#### 3. **Configuração de CORS no servidor**
- **Sintoma**: Erro 403 ou 405
- **Solução**: Adicione CORS no seu servidor Flask:
```python
from flask_cors import CORS

app = Flask(__name__)
CORS(app)  # Permite todas as origens
```

#### 4. **Firewall bloqueando conexões**
- **Sintoma**: Timeout ou "Connection refused"
- **Solução**: Verifique se a porta 5000 está liberada no firewall

### Endpoints Configurados

- **Emulador Android**: `http://10.0.2.2:5000/upload`
- **Dispositivo físico**: Use o IP real da sua máquina (ex: `http://192.168.1.100:5000/upload`)

### Logs de Debug

O aplicativo agora fornece logs detalhados no console do Android Studio:
- Teste de conectividade básica
- Teste completo da API
- Detalhes das requisições e respostas

### Como Testar

1. **Primeiro**: Clique em "Testar Conexão" para verificar se o servidor responde
2. **Segundo**: Clique em "Testar API" para verificar se o endpoint `/upload` funciona
3. **Terceiro**: Tente gravar um áudio

### Exemplo de Servidor Flask Básico

```python
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

@app.route('/upload', methods=['POST'])
def upload_audio():
    try:
        # Recebe os dados de áudio
        audio_data = request.get_data()
        
        # Processa o áudio (exemplo simples)
        processed_data = audio_data  # Aqui você faria o processamento real
        
        return processed_data, 200, {'Content-Type': 'audio/wav'}
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/', methods=['GET'])
def health_check():
    return jsonify({'status': 'ok', 'message': 'API funcionando'}), 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
```

## Configurações do Projeto

### Permissões Necessárias
- `INTERNET`: Para conexões de rede
- `RECORD_AUDIO`: Para gravação de áudio
- `BLUETOOTH_CONNECT` e `BLUETOOTH_SCAN`: Para áudio Bluetooth
- `READ_MEDIA_AUDIO`: Para acesso a arquivos de áudio (Android 13+)

### Configuração de Rede
- `android:usesCleartextTraffic="true"`: Permite HTTP (não apenas HTTPS)
- `android:networkSecurityConfig="@xml/network_security_config"`: Configuração de segurança personalizada

## Como Usar

1. **Gravação**: Clique em "Gravar" para iniciar a gravação
2. **Parar**: Clique em "Parar Gravação" para finalizar
3. **Reprodução**: Clique em um arquivo da lista para reproduzir
4. **Controles**: Use os botões de pausar/continuar e parar durante a reprodução
5. **Seek**: Arraste o slider para mudar a posição do áudio
