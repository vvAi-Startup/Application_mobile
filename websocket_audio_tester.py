"""
Interface GUI para testar WebSocket de streaming de áudio em tempo real
Desenvolvida com tkinter para testar a conexão e processamento de áudio via WebSocket
"""

import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import asyncio
import json
import base64
import threading
import time
import uuid
import wave
import io
import logging
from datetime import datetime
from tkinter import filedialog

# Tentativa de importar as bibliotecas de áudio
try:
    import sounddevice as sd
    import numpy as np
    AUDIO_AVAILABLE = True
except ImportError:
    try:
        import pyaudio
        AUDIO_AVAILABLE = True
    except ImportError:
        AUDIO_AVAILABLE = False

try:
    import websockets
    WEBSOCKET_AVAILABLE = True
except ImportError:
    WEBSOCKET_AVAILABLE = False

# Configuração de logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class AudioWebSocketTester:
    def __init__(self, root):
        self.root = root
        self.root.title("🎤 CalmWave Audio WebSocket Tester")
        self.root.geometry("800x700")
        
        # Variáveis de estado
        self.websocket = None
        self.is_connected = False
        self.is_recording = False
        self.session_id = None
        self.chunk_counter = 0
        self.loop = None
        self.thread = None
        
        # Configurações de áudio - Otimizado para WAV
        self.sample_rate = 44100
        self.channels = 1
        self.chunk_duration = 5.0  # 5 segundos por chunk
        self.chunk_size = int(self.sample_rate * self.chunk_duration)  # samples para 5 segundos
        self.audio_format = np.float32 if AUDIO_AVAILABLE else None
        self.bits_per_sample = 16  # 16-bit para compatibilidade máxima
        
        # Buffer de áudio
        self.audio_buffer = []
        self.processed_audio_buffer = []
        self.current_chunk_buffer = []
        
        # Sistema de reprodução em tempo real (como live streaming)
        self.live_audio_queue = []
        self.is_playing_live = False
        self.live_player_thread = None
        self.final_audio_path = None
        
        self.create_widgets()
        self.update_status()
        
        # Verifica dependências
        if not WEBSOCKET_AVAILABLE:
            self.log_message("❌ ERRO: websockets não está instalado. Execute: pip install websockets")
        if not AUDIO_AVAILABLE:
            self.log_message("❌ ERRO: sounddevice ou pyaudio não está instalado.")
    
    def create_widgets(self):
        """Cria os widgets da interface"""
        
        # Frame principal
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # Configuração do WebSocket
        websocket_frame = ttk.LabelFrame(main_frame, text="🔌 Configuração WebSocket", padding="10")
        websocket_frame.grid(row=0, column=0, columnspan=2, sticky=(tk.W, tk.E), pady=(0, 10))
        
        ttk.Label(websocket_frame, text="URL do WebSocket:").grid(row=0, column=0, sticky=tk.W)
        self.url_var = tk.StringVar(value="ws://localhost:5000/api/v1/streaming/ws/audio-streaming")
        url_entry = ttk.Entry(websocket_frame, textvariable=self.url_var, width=60)
        url_entry.grid(row=0, column=1, padx=(10, 0), sticky=(tk.W, tk.E))
        
        # Botões de conexão
        connection_frame = ttk.Frame(websocket_frame)
        connection_frame.grid(row=1, column=0, columnspan=2, pady=(10, 0))
        
        self.connect_btn = ttk.Button(connection_frame, text="🔌 Conectar", command=self.connect_websocket)
        self.connect_btn.pack(side=tk.LEFT, padx=(0, 10))
        
        self.disconnect_btn = ttk.Button(connection_frame, text="🔌 Desconectar", command=self.disconnect_websocket, state=tk.DISABLED)
        self.disconnect_btn.pack(side=tk.LEFT, padx=(0, 10))
        
        self.ping_btn = ttk.Button(connection_frame, text="🏓 Ping", command=self.send_ping, state=tk.DISABLED)
        self.ping_btn.pack(side=tk.LEFT)
        
        # Status da conexão
        self.connection_status = ttk.Label(websocket_frame, text="❌ Desconectado", foreground="red")
        self.connection_status.grid(row=2, column=0, columnspan=2, pady=(10, 0))
        
        # Frame de controle de áudio
        audio_frame = ttk.LabelFrame(main_frame, text="🎤 Controle de Áudio", padding="10")
        audio_frame.grid(row=1, column=0, columnspan=2, sticky=(tk.W, tk.E), pady=(0, 10))
        
        # Configurações de áudio
        config_frame = ttk.Frame(audio_frame)
        config_frame.grid(row=0, column=0, columnspan=2, sticky=(tk.W, tk.E))
        
        ttk.Label(config_frame, text="Sample Rate:").grid(row=0, column=0, sticky=tk.W)
        self.sample_rate_var = tk.StringVar(value="16000")  # Mudou para 16kHz como padrão
        sample_rate_combo = ttk.Combobox(config_frame, textvariable=self.sample_rate_var, values=["16000", "44100", "22050"], width=10)
        sample_rate_combo.grid(row=0, column=1, padx=(10, 0))
        
        # Label de recomendação
        ttk.Label(config_frame, text="(16kHz = IA otimizada)", font=("TkDefaultFont", 8), foreground="blue").grid(row=0, column=2, padx=(5, 0), sticky=tk.W)
        
        ttk.Label(config_frame, text="Chunk Duration (s):").grid(row=0, column=3, padx=(20, 0), sticky=tk.W)
        self.chunk_duration_var = tk.StringVar(value="5.0")
        ttk.Combobox(config_frame, textvariable=self.chunk_duration_var, values=["2.0", "5.0", "10.0"], width=10).grid(row=0, column=4, padx=(10, 0))
        
        # Indicador de formato
        ttk.Label(config_frame, text="Formato:", font=("TkDefaultFont", 9, "bold")).grid(row=1, column=0, sticky=tk.W, pady=(5, 0))
        self.format_label = ttk.Label(config_frame, text="🎵 WAV 16-bit PCM (16kHz otimizado)", foreground="green")
        self.format_label.grid(row=1, column=1, columnspan=3, sticky=tk.W, pady=(5, 0))
        
        # Botões de gravação
        recording_frame = ttk.Frame(audio_frame)
        recording_frame.grid(row=1, column=0, columnspan=2, pady=(10, 0))
        
        self.start_recording_btn = ttk.Button(recording_frame, text="🔴 Iniciar Gravação", command=self.start_recording, state=tk.DISABLED)
        self.start_recording_btn.pack(side=tk.LEFT, padx=(0, 10))
        
        self.stop_recording_btn = ttk.Button(recording_frame, text="⏹️ Parar Gravação", command=self.stop_recording, state=tk.DISABLED)
        self.stop_recording_btn.pack(side=tk.LEFT, padx=(0, 10))
        
        self.live_play_btn = ttk.Button(recording_frame, text="📻 Reprodução Live", command=self.toggle_live_playback, state=tk.DISABLED)
        self.live_play_btn.pack(side=tk.LEFT, padx=(0, 10))
        
        self.play_final_btn = ttk.Button(recording_frame, text="▶️ Reproduzir Final", command=self.play_final_audio, state=tk.DISABLED)
        self.play_final_btn.pack(side=tk.LEFT, padx=(0, 10))
        
        self.save_final_btn = ttk.Button(recording_frame, text="💾 Salvar Final", command=self.save_final_audio, state=tk.DISABLED)
        self.save_final_btn.pack(side=tk.LEFT, padx=(0, 10))
        
        self.test_wav_btn = ttk.Button(recording_frame, text="🧪 Testar WAV", command=self.test_wav_format, state=tk.NORMAL)
        self.test_wav_btn.pack(side=tk.LEFT)
        
        # Informações da sessão
        session_frame = ttk.LabelFrame(main_frame, text="📊 Informações da Sessão", padding="10")
        session_frame.grid(row=2, column=0, columnspan=2, sticky=(tk.W, tk.E), pady=(0, 10))
        
        self.session_info = ttk.Label(session_frame, text="Sessão: Nenhuma ativa")
        self.session_info.pack(anchor=tk.W)
        
        self.chunk_info = ttk.Label(session_frame, text="Chunks enviados: 0")
        self.chunk_info.pack(anchor=tk.W)
        
        self.processed_info = ttk.Label(session_frame, text="Chunks processados: 0")
        self.processed_info.pack(anchor=tk.W)
        
        self.live_status = ttk.Label(session_frame, text="📻 Live: Inativo")
        self.live_status.pack(anchor=tk.W)
        
        self.buffer_status = ttk.Label(session_frame, text="🔊 Buffer: 0 chunks")
        self.buffer_status.pack(anchor=tk.W)
        
        self.processing_status = ttk.Label(session_frame, text="⚙️ Processamento: 0/0 chunks")
        self.processing_status.pack(anchor=tk.W)
        
        # Log de mensagens
        log_frame = ttk.LabelFrame(main_frame, text="📝 Log de Mensagens", padding="10")
        log_frame.grid(row=3, column=0, columnspan=2, sticky=(tk.W, tk.E, tk.N, tk.S), pady=(0, 10))
        
        self.log_text = scrolledtext.ScrolledText(log_frame, height=15, wrap=tk.WORD)
        self.log_text.pack(fill=tk.BOTH, expand=True)
        
        # Frame de controles do log
        log_controls = ttk.Frame(log_frame)
        log_controls.pack(fill=tk.X, pady=(10, 0))
        
        clear_log_btn = ttk.Button(log_controls, text="🧹 Limpar Log", command=self.clear_log)
        clear_log_btn.pack(side=tk.LEFT)
        
        # Indicador de atividade
        self.activity_label = ttk.Label(log_controls, text="💤 Inativo")
        self.activity_label.pack(side=tk.RIGHT)
        
        # Configurar expansão
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)
        main_frame.columnconfigure(0, weight=1)
        main_frame.rowconfigure(3, weight=1)
        websocket_frame.columnconfigure(1, weight=1)
    
    def log_message(self, message):
        """Adiciona mensagem ao log"""
        timestamp = datetime.now().strftime("%H:%M:%S")
        log_entry = f"[{timestamp}] {message}\n"
        
        self.log_text.insert(tk.END, log_entry)
        self.log_text.see(tk.END)
        logger.info(message)
    
    def clear_log(self):
        """Limpa o log de mensagens"""
        self.log_text.delete(1.0, tk.END)
    
    def update_status(self):
        """Atualiza o status da interface"""
        if self.is_connected:
            self.connection_status.config(text="✅ Conectado", foreground="green")
            self.connect_btn.config(state=tk.DISABLED)
            self.disconnect_btn.config(state=tk.NORMAL)
            self.ping_btn.config(state=tk.NORMAL)
            self.start_recording_btn.config(state=tk.NORMAL)
        else:
            self.connection_status.config(text="❌ Desconectado", foreground="red")
            self.connect_btn.config(state=tk.NORMAL)
            self.disconnect_btn.config(state=tk.DISABLED)
            self.ping_btn.config(state=tk.DISABLED)
            self.start_recording_btn.config(state=tk.DISABLED)
            self.stop_recording_btn.config(state=tk.DISABLED)
        
        if self.session_id:
            self.session_info.config(text=f"Sessão: {self.session_id}")
        else:
            self.session_info.config(text="Sessão: Nenhuma ativa")
        
        self.chunk_info.config(text=f"Chunks enviados: {self.chunk_counter}")
        self.processed_info.config(text=f"Chunks processados: {len(self.processed_audio_buffer)}")
        
        # Atualiza status do live player
        if self.is_playing_live:
            self.live_status.config(text="📻 Live: Ativo", foreground="green")
            self.live_play_btn.config(text="⏸️ Pausar Live")
        else:
            self.live_status.config(text="📻 Live: Inativo", foreground="red")
            self.live_play_btn.config(text="📻 Reprodução Live")
        
        self.buffer_status.config(text=f"🔊 Buffer: {len(self.live_audio_queue)} chunks")
        self.processing_status.config(text=f"⚙️ Processamento: {len(self.processed_audio_buffer)}/{self.chunk_counter} chunks")
        
        # Habilita botões conforme necessário
        if len(self.processed_audio_buffer) > 0:
            self.live_play_btn.config(state=tk.NORMAL)
            self.play_final_btn.config(state=tk.NORMAL)
            self.save_final_btn.config(state=tk.NORMAL)
    
    def connect_websocket(self):
        """Conecta ao WebSocket"""
        if not WEBSOCKET_AVAILABLE:
            messagebox.showerror("Erro", "websockets não está instalado!")
            return
        
        url = self.url_var.get()
        if not url:
            messagebox.showerror("Erro", "URL do WebSocket é obrigatória!")
            return
        
        self.log_message(f"🔌 Tentando conectar ao WebSocket: {url}")
        
        # Inicia thread para conexão WebSocket
        self.thread = threading.Thread(target=self.run_websocket_client, args=(url,))
        self.thread.daemon = True
        self.thread.start()
    
    def disconnect_websocket(self):
        """Desconecta do WebSocket"""
        self.log_message("🔌 Desconectando do WebSocket...")
        self.is_connected = False
        
        if self.is_recording:
            self.stop_recording()
        
        if self.websocket:
            asyncio.run_coroutine_threadsafe(self.websocket.close(), self.loop)
        
        self.websocket = None
        self.session_id = None
        self.chunk_counter = 0
        self.update_status()
    
    def run_websocket_client(self, url):
        """Executa o cliente WebSocket em thread separada"""
        try:
            # Cria novo loop de eventos para esta thread
            self.loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self.loop)
            
            # Conecta ao WebSocket
            self.loop.run_until_complete(self.websocket_client(url))
        except Exception as e:
            self.log_message(f"❌ Erro na conexão WebSocket: {str(e)}")
            self.is_connected = False
            self.root.after(0, self.update_status)
    
    async def websocket_client(self, url):
        """Cliente WebSocket assíncrono"""
        try:
            async with websockets.connect(url) as websocket:
                self.websocket = websocket
                self.is_connected = True
                self.root.after(0, self.update_status)
                self.root.after(0, lambda: self.log_message("✅ Conectado ao WebSocket!"))
                
                # Escuta mensagens do servidor
                async for message in websocket:
                    await self.handle_websocket_message(message)
                    
        except websockets.exceptions.ConnectionClosed:
            self.root.after(0, lambda: self.log_message("🔌 Conexão WebSocket fechada"))
        except Exception as e:
            error_msg = f"❌ Erro WebSocket: {str(e)}"
            self.root.after(0, lambda: self.log_message(error_msg))
        finally:
            self.is_connected = False
            self.websocket = None
            self.root.after(0, self.update_status)
    
    async def handle_websocket_message(self, message):
        """Processa mensagens recebidas do WebSocket"""
        try:
            data = json.loads(message)
            msg_type = data.get("type", "unknown")
            
            if msg_type == "connection_established":
                self.root.after(0, lambda: self.log_message("🎉 Conexão estabelecida com sucesso!"))
            
            elif msg_type == "session_started":
                session_id = data.get("session_id")
                self.root.after(0, lambda: self.log_message(f"🎯 Sessão iniciada: {session_id}"))
            
            elif msg_type == "audio_processed":
                status = data.get("status", "unknown")
                message = data.get("message", "")
                processed_data = data.get("processed_audio_data")
                
                self.root.after(0, lambda: self.log_message(f"📨 Resposta do servidor: {status} - {message}"))
                
                if processed_data:
                    try:
                        # Decodifica áudio processado
                        audio_bytes = base64.b64decode(processed_data)
                        
                        # Adiciona ao buffer principal
                        self.processed_audio_buffer.append(audio_bytes)
                        
                        # Adiciona à fila de reprodução live
                        self.live_audio_queue.append(audio_bytes)
                        
                        chunk_num = len(self.processed_audio_buffer)
                        self.root.after(0, lambda: self.log_message(f"🎵 Chunk {chunk_num} processado ({len(audio_bytes)} bytes) - Adicionado à fila live"))
                        
                        # Atualiza indicador de atividade
                        self.root.after(0, lambda: self.activity_label.config(text="🟢 Processando", foreground="green"))
                        
                    except Exception as e:
                        self.root.after(0, lambda: self.log_message(f"❌ Erro ao decodificar áudio: {str(e)}"))
                else:
                    if status == "processing":
                        self.root.after(0, lambda: self.log_message(f"⏳ Chunk recebido pelo servidor, aguardando processamento..."))
                    elif status == "error":
                        self.root.after(0, lambda: self.log_message(f"❌ Erro no servidor: {message}"))
                    else:
                        self.root.after(0, lambda: self.log_message(f"ℹ️  Status: {status} - {message}"))
                
                self.root.after(0, self.update_status)
            
            elif msg_type == "error":
                error_msg = data.get("message", "Erro desconhecido")
                self.root.after(0, lambda: self.log_message(f"❌ Erro do servidor: {error_msg}"))
            
            elif msg_type == "pong":
                self.root.after(0, lambda: self.log_message("🏓 Pong recebido"))
            
            else:
                self.root.after(0, lambda: self.log_message(f"📨 Mensagem recebida: {msg_type}"))
                
        except json.JSONDecodeError:
            self.root.after(0, lambda: self.log_message(f"❌ Erro JSON na mensagem: {message}"))
        except Exception as e:
            self.root.after(0, lambda: self.log_message(f"❌ Erro ao processar mensagem: {str(e)}"))
    
    def start_recording(self):
        """Inicia a gravação de áudio"""
        if not AUDIO_AVAILABLE:
            messagebox.showerror("Erro", "Biblioteca de áudio não disponível!")
            return
        
        if not self.is_connected:
            messagebox.showerror("Erro", "Conecte-se ao WebSocket primeiro!")
            return
        
        # Cria nova sessão
        self.session_id = str(uuid.uuid4())
        self.chunk_counter = 0
        self.audio_buffer = []
        self.processed_audio_buffer = []
        self.current_chunk_buffer = []
        self.live_audio_queue = []
        
        # Para reprodução live se estiver ativa
        if self.is_playing_live:
            self.stop_live_playback()
        
        # Atualiza configurações de áudio
        self.sample_rate = int(self.sample_rate_var.get())
        self.chunk_duration = float(self.chunk_duration_var.get())
        self.chunk_size = int(self.sample_rate * self.chunk_duration)
        
        # Atualiza indicador de formato
        self.update_format_indicator()
        
        # Envia mensagem de início de sessão
        start_message = {
            "type": "start_session",
            "session_id": self.session_id
        }
        
        asyncio.run_coroutine_threadsafe(
            self.websocket.send(json.dumps(start_message)), 
            self.loop
        )
        
        self.log_message(f"🎤 Iniciando gravação - Sessão: {self.session_id}")
        self.log_message(f"📊 Sample Rate: {self.sample_rate}Hz, Chunk Duration: {self.chunk_duration}s, Chunk Size: {self.chunk_size} samples")
        
        # Aviso sobre compatibilidade com modelo IA
        if self.sample_rate != 16000:
            self.log_message(f"⚠️ AVISO: Modelo IA usa 16kHz. Seu áudio ({self.sample_rate}Hz) será convertido temporariamente.")
            self.log_message(f"📝 Para melhor qualidade, considere usar 16000Hz como Sample Rate.")
        
        self.is_recording = True
        self.start_recording_btn.config(state=tk.DISABLED)
        self.stop_recording_btn.config(state=tk.NORMAL)
        
        # Inicia gravação em thread separada
        self.recording_thread = threading.Thread(target=self.record_audio)
        self.recording_thread.daemon = True
        self.recording_thread.start()
        
        self.update_status()
    
    def stop_recording(self):
        """Para a gravação de áudio"""
        self.log_message("⏹️ Parando gravação...")
        self.is_recording = False
        
        # Processa chunk final se houver áudio restante
        if hasattr(self, 'current_chunk_buffer') and self.current_chunk_buffer:
            try:
                # Combina o áudio restante
                remaining_audio = np.concatenate(self.current_chunk_buffer)
                if len(remaining_audio) > 0:
                    # Envia como chunk final
                    audio_bytes = self.numpy_to_wav_bytes(remaining_audio)
                    self.send_audio_chunk(audio_bytes, is_final=True)
                    self.log_message(f"📤 Chunk final enviado ({len(audio_bytes)} bytes) - {len(remaining_audio)/self.sample_rate:.1f}s de áudio")
            except Exception as e:
                self.log_message(f"❌ Erro ao processar chunk final: {str(e)}")
        
        # Envia mensagem de fim de sessão
        if self.websocket and self.session_id:
            stop_message = {
                "type": "stop_session",
                "session_id": self.session_id
            }
            
            asyncio.run_coroutine_threadsafe(
                self.websocket.send(json.dumps(stop_message)), 
                self.loop
            )
        
        self.start_recording_btn.config(state=tk.NORMAL)
        self.stop_recording_btn.config(state=tk.DISABLED)
        
        # Mensagem informativa sobre finalização
        self.log_message("✅ Gravação finalizada - aguarde o processamento dos chunks restantes...")
        
        self.update_status()
    
    def record_audio(self):
        """Grava áudio usando sounddevice com chunks de 5 segundos"""
        try:
            # Buffer para acumular áudio
            self.current_chunk_buffer = []
            
            def audio_callback(indata, frames, time, status):
                if self.is_recording:
                    # Adiciona os dados ao buffer atual
                    self.current_chunk_buffer.append(indata[:, 0].copy())  # Mono
                    
                    # Verifica se temos áudio suficiente para um chunk de 5 segundos
                    total_samples = sum(len(chunk) for chunk in self.current_chunk_buffer)
                    if total_samples >= self.chunk_size:
                        # Combina todos os dados do buffer
                        combined_audio = np.concatenate(self.current_chunk_buffer)
                        
                        # Pega exatamente o que precisamos para o chunk
                        chunk_audio = combined_audio[:self.chunk_size]
                        
                        # Converte para bytes WAV e envia
                        audio_bytes = self.numpy_to_wav_bytes(chunk_audio)
                        self.send_audio_chunk(audio_bytes)
                        
                        # Guarda o restante para o próximo chunk
                        remaining_audio = combined_audio[self.chunk_size:]
                        self.current_chunk_buffer = [remaining_audio] if len(remaining_audio) > 0 else []
            
            # Inicia stream de áudio com buffer menor para responsividade
            buffer_size = 1024  # Buffer pequeno para baixa latência
            
            with sd.InputStream(
                samplerate=self.sample_rate,
                channels=self.channels,
                dtype=self.audio_format,
                blocksize=buffer_size,
                callback=audio_callback
            ):
                while self.is_recording:
                    time.sleep(0.1)
                    
        except Exception as e:
            self.root.after(0, lambda: self.log_message(f"❌ Erro na gravação: {str(e)}"))
            self.is_recording = False
            self.root.after(0, self.update_status)
    
    def numpy_to_wav_bytes(self, audio_data):
        """Converte array numpy para bytes WAV com formato padrão"""
        try:
            # Normaliza e converte para int16 (16-bit PCM)
            if audio_data.dtype == np.float32:
                # Clamp valores entre -1 e 1
                audio_data = np.clip(audio_data, -1.0, 1.0)
                audio_int16 = (audio_data * 32767).astype(np.int16)
            else:
                audio_int16 = audio_data.astype(np.int16)
            
            # Cria arquivo WAV padrão em memória
            buffer = io.BytesIO()
            with wave.open(buffer, 'wb') as wav_file:
                wav_file.setnchannels(self.channels)  # Mono = 1
                wav_file.setsampwidth(2)  # 16-bit = 2 bytes
                wav_file.setframerate(self.sample_rate)  # 44100 Hz
                wav_file.setcomptype('NONE', 'not compressed')  # PCM não comprimido
                wav_file.writeframes(audio_int16.tobytes())
            
            wav_bytes = buffer.getvalue()
            
            # Validação básica do WAV
            if len(wav_bytes) < 44:  # Cabeçalho WAV mínimo
                raise ValueError("WAV gerado é muito pequeno")
            
            # Verifica se começa com 'RIFF'
            if not wav_bytes.startswith(b'RIFF'):
                raise ValueError("Cabeçalho WAV inválido")
            
            return wav_bytes
            
        except Exception as e:
            self.root.after(0, lambda: self.log_message(f"❌ Erro ao criar WAV: {str(e)}"))
            return None
    
    def send_audio_chunk(self, audio_bytes, is_final=False):
        """Envia chunk de áudio WAV via WebSocket com validação"""
        if not self.websocket:
            return
        
        if not audio_bytes:
            self.root.after(0, lambda: self.log_message(f"❌ Áudio vazio não enviado"))
            return
        
        try:
            self.chunk_counter += 1
            
            # Validação do formato WAV
            if not audio_bytes.startswith(b'RIFF'):
                self.root.after(0, lambda: self.log_message(f"⚠️ Aviso: Chunk {self.chunk_counter} pode não ser WAV válido"))
            
            # Codifica áudio em base64
            audio_b64 = base64.b64encode(audio_bytes).decode('utf-8')
            
            # Cria mensagem com informações do formato
            message = {
                "type": "audio_chunk",
                "session_id": self.session_id,
                "chunk_id": f"chunk_{self.chunk_counter}",
                "audio_data": audio_b64,
                "is_final": is_final,
                "format": "wav",  # Especifica que é WAV
                "sample_rate": self.sample_rate,
                "channels": self.channels,
                "bits_per_sample": self.bits_per_sample
            }
            
            # Envia via WebSocket
            asyncio.run_coroutine_threadsafe(
                self.websocket.send(json.dumps(message)), 
                self.loop
            )
            
            # Atualiza UI - log detalhado
            duration_mb = len(audio_bytes) / (1024 * 1024)  # Tamanho em MB
            duration_s = len(audio_bytes) / (self.sample_rate * self.channels * 2)  # Duração estimada
            
            self.root.after(0, lambda: self.log_message(f"📤 WAV Chunk {self.chunk_counter} enviado:"))
            self.root.after(0, lambda: self.log_message(f"   📊 Tamanho: {len(audio_bytes)} bytes ({duration_mb:.2f}MB)"))
            self.root.after(0, lambda: self.log_message(f"   ⏱️ Duração: ~{duration_s:.1f}s | {self.sample_rate}Hz | {self.channels}ch | {self.bits_per_sample}bit"))
            
            self.root.after(0, self.update_status)
                
        except Exception as e:
            self.root.after(0, lambda: self.log_message(f"❌ Erro ao enviar chunk WAV: {str(e)}"))
    
    def toggle_live_playback(self):
        """Liga/desliga reprodução live"""
        if self.is_playing_live:
            self.stop_live_playback()
        else:
            self.start_live_playback()
    
    def start_live_playback(self):
        """Inicia reprodução live (como streaming)"""
        if not AUDIO_AVAILABLE:
            messagebox.showerror("Erro", "Biblioteca de áudio não disponível!")
            return
        
        self.is_playing_live = True
        self.log_message("📻 Iniciando reprodução live - áudio será reproduzido conforme processado")
        
        # Inicia thread do reprodutor live
        self.live_player_thread = threading.Thread(target=self.live_player_worker)
        self.live_player_thread.daemon = True
        self.live_player_thread.start()
        
        self.update_status()
    
    def stop_live_playback(self):
        """Para reprodução live"""
        self.is_playing_live = False
        self.log_message("⏸️ Reprodução live parada")
        self.update_status()
    
    def live_player_worker(self):
        """Worker thread para reprodução live"""
        try:
            while self.is_playing_live:
                # Verifica se há áudio na fila
                if self.live_audio_queue:
                    # Pega o próximo chunk da fila
                    audio_bytes = self.live_audio_queue.pop(0)
                    
                    try:
                        # Carrega o áudio WAV do buffer
                        audio_buffer = io.BytesIO(audio_bytes)
                        
                        # Lê o arquivo WAV
                        with wave.open(audio_buffer, 'rb') as wav_file:
                            frames = wav_file.readframes(wav_file.getnframes())
                            audio_data = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32767
                        
                        # Reproduz o chunk
                        sd.play(audio_data, samplerate=self.sample_rate)
                        sd.wait()  # Aguarda terminar de tocar
                        
                        self.root.after(0, lambda: self.log_message("🔊 Chunk reproduzido em live"))
                        self.root.after(0, self.update_status)
                        
                    except Exception as e:
                        self.root.after(0, lambda: self.log_message(f"❌ Erro ao reproduzir chunk live: {str(e)}"))
                
                else:
                    # Não há áudio na fila, aguarda um pouco
                    time.sleep(0.1)
                    
        except Exception as e:
            self.root.after(0, lambda: self.log_message(f"❌ Erro no reprodutor live: {str(e)}"))
        finally:
            self.is_playing_live = False
            self.root.after(0, self.update_status)
    
    def play_final_audio(self):
        """Reproduz todo o áudio processado final"""
        if not self.processed_audio_buffer:
            messagebox.showinfo("Info", "Nenhum áudio processado disponível!")
            return
        
        self.log_message("▶️ Reproduzindo áudio final completo...")
        
        try:
            # Para reprodução live se estiver ativa
            if self.is_playing_live:
                self.stop_live_playback()
            
            # Thread para reprodução do áudio final
            def play_final_thread():
                try:
                    for i, audio_bytes in enumerate(self.processed_audio_buffer):
                        # Carrega o áudio WAV do buffer
                        audio_buffer = io.BytesIO(audio_bytes)
                        
                        # Lê o arquivo WAV
                        with wave.open(audio_buffer, 'rb') as wav_file:
                            frames = wav_file.readframes(wav_file.getnframes())
                            audio_data = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32767
                        
                        # Reproduz o chunk
                        sd.play(audio_data, samplerate=self.sample_rate)
                        sd.wait()  # Aguarda terminar de tocar
                        
                        self.root.after(0, lambda i=i: self.log_message(f"🎵 Reproduzindo chunk {i+1}/{len(self.processed_audio_buffer)}"))
                    
                    self.root.after(0, lambda: self.log_message("✅ Reprodução final concluída!"))
                    
                except Exception as e:
                    self.root.after(0, lambda: self.log_message(f"❌ Erro na reprodução final: {str(e)}"))
            
            # Inicia thread
            final_thread = threading.Thread(target=play_final_thread)
            final_thread.daemon = True
            final_thread.start()
            
        except Exception as e:
            self.log_message(f"❌ Erro ao iniciar reprodução final: {str(e)}")
    
    def save_final_audio(self):
        """Salva o áudio processado final em arquivo"""
        if not self.processed_audio_buffer:
            messagebox.showinfo("Info", "Nenhum áudio processado disponível!")
            return
        
        try:
            # Gera nome do arquivo com timestamp
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            default_filename = f"audio_processado_final_{timestamp}.wav"
            
            # Abre dialog para escolher onde salvar
            filename = filedialog.asksaveasfilename(
                defaultextension=".wav",
                filetypes=[("WAV files", "*.wav"), ("All files", "*.*")],
                initialvalue=default_filename,
                title="Salvar áudio processado final"
            )
            
            if not filename:  # Usuário cancelou
                return
            
            # Combina todos os chunks de áudio processado
            combined_audio_data = []
            
            for audio_bytes in self.processed_audio_buffer:
                # Carrega cada chunk WAV
                audio_buffer = io.BytesIO(audio_bytes)
                with wave.open(audio_buffer, 'rb') as wav_file:
                    frames = wav_file.readframes(wav_file.getnframes())
                    combined_audio_data.append(frames)
            
            # Combina todos os frames
            final_frames = b''.join(combined_audio_data)
            
            # Salva como arquivo WAV
            with wave.open(filename, 'wb') as final_wav:
                final_wav.setnchannels(1)  # Mono
                final_wav.setsampwidth(2)  # 16-bit
                final_wav.setframerate(self.sample_rate)
                final_wav.writeframes(final_frames)
            
            self.final_audio_path = filename
            duration = len(final_frames) / (self.sample_rate * 2)  # 2 bytes por sample
            
            self.log_message(f"💾 Áudio final salvo: {filename}")
            self.log_message(f"📊 Duração: {duration:.1f}s, Tamanho: {len(final_frames)} bytes")
            
            messagebox.showinfo("Sucesso", f"Áudio salvo como:\n{filename}\n\nDuração: {duration:.1f}s")
            
        except Exception as e:
            self.log_message(f"❌ Erro ao salvar áudio: {str(e)}")
            messagebox.showerror("Erro", f"Erro ao salvar áudio:\n{str(e)}")
    
    def send_ping(self):
        """Envia ping para testar conexão"""
        if not self.websocket or not self.is_connected:
            return
        
        ping_message = {
            "type": "ping",
            "timestamp": time.time()
        }
        
        try:
            asyncio.run_coroutine_threadsafe(
                self.websocket.send(json.dumps(ping_message)), 
                self.loop
            )
            self.log_message("🏓 Ping enviado para o servidor")
        except Exception as e:
            self.log_message(f"❌ Erro ao enviar ping: {str(e)}")
    
    def test_wav_format(self):
        """Testa a geração de formato WAV"""
        if not AUDIO_AVAILABLE:
            messagebox.showerror("Erro", "Biblioteca de áudio não disponível!")
            return
        
        try:
            self.log_message("🧪 Testando geração de formato WAV...")
            
            # Gera 1 segundo de áudio de teste (tom senoidal)
            duration = 1.0  # 1 segundo
            frequency = 440  # Lá central (440 Hz)
            t = np.linspace(0, duration, int(self.sample_rate * duration), False)
            test_audio = np.sin(2 * np.pi * frequency * t).astype(np.float32) * 0.5
            
            # Converte para WAV
            wav_bytes = self.numpy_to_wav_bytes(test_audio)
            
            if wav_bytes:
                # Validações
                size_mb = len(wav_bytes) / (1024 * 1024)
                is_riff = wav_bytes.startswith(b'RIFF')
                has_wave = b'WAVE' in wav_bytes[:12]
                has_fmt = b'fmt ' in wav_bytes[:100]
                has_data = b'data' in wav_bytes
                
                self.log_message(f"✅ WAV gerado com sucesso:")
                self.log_message(f"   📊 Tamanho: {len(wav_bytes)} bytes ({size_mb:.3f}MB)")
                self.log_message(f"   🔍 RIFF header: {'✅' if is_riff else '❌'}")
                self.log_message(f"   🔍 WAVE format: {'✅' if has_wave else '❌'}")
                self.log_message(f"   🔍 fmt chunk: {'✅' if has_fmt else '❌'}")
                self.log_message(f"   🔍 data chunk: {'✅' if has_data else '❌'}")
                
                # Testa reprodução
                self.log_message("🔊 Reproduzindo tom de teste (440Hz por 1s)...")
                
                def play_test_audio():
                    try:
                        sd.play(test_audio, samplerate=self.sample_rate)
                        sd.wait()
                        self.root.after(0, lambda: self.log_message("✅ Teste de áudio concluído!"))
                    except Exception as e:
                        self.root.after(0, lambda: self.log_message(f"❌ Erro na reprodução: {str(e)}"))
                
                # Reproduz em thread separada
                test_thread = threading.Thread(target=play_test_audio)
                test_thread.daemon = True
                test_thread.start()
                
            else:
                self.log_message("❌ Falha na geração do WAV")
                
        except Exception as e:
            self.log_message(f"❌ Erro no teste WAV: {str(e)}")
    
    def update_format_indicator(self):
        """Atualiza o indicador de formato conforme as configurações"""
        sample_rate = int(self.sample_rate_var.get()) if hasattr(self, 'sample_rate_var') else self.sample_rate
        
        if sample_rate == 16000:
            self.format_label.config(
                text="🎵 WAV 16-bit PCM (16kHz - IA OTIMIZADO)", 
                foreground="green"
            )
        elif sample_rate == 44100:
            self.format_label.config(
                text="🎵 WAV 16-bit PCM (44kHz - CONVERSÃO AUTOMÁTICA)", 
                foreground="orange"
            )
        else:
            self.format_label.config(
                text=f"🎵 WAV 16-bit PCM ({sample_rate}Hz - CONVERSÃO AUTOMÁTICA)", 
                foreground="orange"
            )


def main():
    """Função principal"""
    # Verifica dependências
    missing_deps = []
    
    if not WEBSOCKET_AVAILABLE:
        missing_deps.append("websockets")
    
    if not AUDIO_AVAILABLE:
        missing_deps.append("sounddevice ou pyaudio")
    
    if missing_deps:
        print("❌ Dependências faltando:")
        for dep in missing_deps:
            print(f"   - {dep}")
        print("\n📦 Para instalar:")
        print("   pip install websockets sounddevice numpy")
        
        # Cria GUI mesmo sem dependências para mostrar erro
    
    # Cria e executa GUI
    root = tk.Tk()
    app = AudioWebSocketTester(root)
    
    try:
        root.mainloop()
    except KeyboardInterrupt:
        print("\n👋 Encerrando aplicação...")


if __name__ == "__main__":
    main()