package com.vvai.calmwave.service

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okio.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Serviço para upload e transcrição de arquivos de áudio usando OpenAI Whisper
 * 
 * Este serviço se conecta à API de transcrição que oferece:
 * ✅ Funciona offline (sem internet)
 * ✅ Suporte a 99+ idiomas
 * ✅ Mais preciso e robusto que Google Speech Recognition
 * ✅ Sem limites de uso
 * ✅ Detecta idioma automaticamente
 * ✅ Faster-Whisper é 4-5x mais rápido
 */
class AudioUploadService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS) // 3 minutos para transcrição
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Faz upload de um arquivo de áudio processado para transcrição
     * 
     * @param uploadUrl URL do endpoint de transcrição (default: 10.67.57.104:5000/api/v1/audio/transcricao)
     * @param audioFile Arquivo de áudio a ser enviado
     * @param language Código do idioma (pt, en, es, fr, etc.) ou "auto" para detecção automática
     * @param modelSize Tamanho do modelo Whisper (tiny, base, small, medium, large)
     * @param highQuality Se true, usa configurações otimizadas para melhor qualidade
     * @param sessionId ID da sessão (opcional)
     * @param metadata Metadados adicionais (opcional)
     * @param onProgress Callback para acompanhar o progresso do upload
     * @return Resultado do upload
     */
    suspend fun uploadProcessedAudio(
        uploadUrl: String = "http://10.67.57.104:5000/api/v1/audio/transcricao",
        audioFile: File,
        language: String = "pt",
        modelSize: String = "medium",
        highQuality: Boolean = true,
        sessionId: String? = null,
        metadata: Map<String, String> = emptyMap(),
        onProgress: ((bytesUploaded: Long, totalBytes: Long) -> Unit)? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        
        println("=== AUDIOUPLOADSERVICE - INICIANDO TRANSCRIÇÃO ===")
        println("URL: $uploadUrl")
        println("Arquivo: ${audioFile.absolutePath}")
        println("Tamanho: ${audioFile.length()} bytes")
        println("Idioma: $language")
        println("Modelo: $modelSize")
        println("Alta qualidade: $highQuality")
        
        if (!audioFile.exists()) {
            println("❌ Arquivo não encontrado!")
            return@withContext UploadResult.Error("Arquivo não encontrado: ${audioFile.absolutePath}")
        }
        
        try {
            // Cria o corpo da requisição multipart
            val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
            
            // Determina o tipo MIME baseado na extensão do arquivo
            val mimeType = when (audioFile.extension.lowercase()) {
                "wav" -> "audio/wav"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "flac" -> "audio/flac"
                "ogg" -> "audio/ogg"
                else -> "audio/wav" // Default para WAV
            }
            println("📋 Tipo MIME detectado: $mimeType")
            
            // Adiciona o arquivo de áudio
            val audioRequestBody = audioFile.asRequestBody(mimeType.toMediaType())
            val progressRequestBody = if (onProgress != null) {
                ProgressRequestBody(audioRequestBody, onProgress)
            } else {
                audioRequestBody
            }
             // Adiciona o arquivo de áudio com o nome correto esperado pela API
            requestBodyBuilder.addFormDataPart(
                "audio",
                audioFile.name,
                progressRequestBody
            )

            // Adiciona sessionId se fornecido (como metadado adicional)
            sessionId?.let {
                requestBodyBuilder.addFormDataPart("session_id", it)
            }

            // Adiciona metadados adicionais
            metadata.forEach { (key, value) ->
                requestBodyBuilder.addFormDataPart(key, value)
            }
            
            val requestBody = requestBodyBuilder.build()
            
            // Constrói a URL com os parâmetros de query
            val finalUrl = try {
                val url = uploadUrl.toHttpUrl().newBuilder()
                    .addQueryParameter("language", language)
                    .addQueryParameter("model_size", modelSize)
                    .addQueryParameter("high_quality", highQuality.toString())
                    .build()
                println("URL final construída: $url")
                url
            } catch (e: IllegalArgumentException) {
                println("❌ Erro ao construir URL: ${e.message}")
                throw IllegalArgumentException("URL inválida: $uploadUrl", e)
            }
            
            // Cria a requisição
            val request = Request.Builder()
                .url(finalUrl)
                .post(requestBody)
                .addHeader("Content-Type", "multipart/form-data")
                .addHeader("Accept", "application/json")
                .build()
            
            // Executa a requisição
            println("🚀 Enviando requisição para: $finalUrl")
            suspendCancellableCoroutine<UploadResult> { continuation ->
                val call = client.newCall(request)
                
                continuation.invokeOnCancellation {
                    println("❌ Requisição cancelada")
                    call.cancel()
                }
                
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        println("❌ Falha na requisição: ${e.message}")
                        continuation.resumeWithException(e)
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        println("📨 Resposta recebida - Código: ${response.code}")
                        response.use { resp ->
                            if (resp.isSuccessful) {
                                val responseBody = resp.body?.string() ?: ""
                                println("✅ Transcrição bem-sucedida!")
                                println("Resposta: $responseBody")
                                continuation.resume(UploadResult.Success(responseBody, resp.code))
                            } else {
                                val errorBody = resp.body?.string() ?: "Erro desconhecido"
                                println("❌ Erro na transcrição - Código: ${resp.code}")
                                println("Corpo do erro: $errorBody")
                                val errorMessage = when (resp.code) {
                                    422 -> "Erro de validação: verifique se o arquivo de áudio é válido - $errorBody"
                                    400 -> "Requisição inválida: $errorBody"
                                    500 -> "Erro interno do servidor de transcrição: $errorBody"
                                    else -> "Transcrição falhou: ${resp.code} - $errorBody"
                                }
                                continuation.resume(UploadResult.Error(errorMessage))
                            }
                        }
                    }
                })
            }
            
        } catch (e: Exception) {
            println("❌ Exceção durante transcrição: ${e.message}")
            e.printStackTrace()
            return@withContext UploadResult.Error("Erro durante transcrição: ${e.message}")
        }
    }
    
    /**
     * Método conveniente para upload e transcrição de áudio com parâmetros padrão otimizados
     * 
     * @param audioFile Arquivo de áudio a ser transcrito
     * @param onProgress Callback para acompanhar o progresso do upload
     * @return Resultado da transcrição
     */
    suspend fun transcribeAudio(
        audioFile: File,
        onProgress: ((bytesUploaded: Long, totalBytes: Long) -> Unit)? = null
    ): UploadResult {
        return uploadProcessedAudio(
            audioFile = audioFile,
            language = "pt", // Português como padrão
            modelSize = "medium", // Bom equilíbrio entre velocidade e qualidade
            highQuality = true, // Melhor qualidade
            onProgress = onProgress
        )
    }

    /**
     * Testa a conectividade com o endpoint de transcrição
     */
    suspend fun testTranscriptionEndpoint(
        transcriptionUrl: String = "http://10.67.57.104:5000/api/v1/audio/transcricao"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            println("🧪 Testando endpoint de transcrição: $transcriptionUrl")
            
            val healthUrl = transcriptionUrl.replace("/api/v1/audio/transcricao", "/health")
            val request = Request.Builder()
                .url(healthUrl)
                .get()
                .build()
                
            val response = client.newCall(request).execute()
            response.use { resp ->
                val success = resp.isSuccessful
                println("📊 Teste de conectividade - Status: ${resp.code} - ${if (success) "✅ OK" else "❌ FALHOU"}")
                if (!success) {
                    println("Corpo da resposta: ${resp.body?.string()}")
                }
                success
            }
        } catch (e: Exception) {
            println("❌ Erro no teste de conectividade: ${e.message}")
            false
        }
    }

    /**
     * RequestBody que monitora o progresso do upload
     */
    private class ProgressRequestBody(
        private val requestBody: RequestBody,
        private val onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit
    ) : RequestBody() {
        
        override fun contentType(): MediaType? = requestBody.contentType()
        
        override fun contentLength(): Long = requestBody.contentLength()
        
        override fun writeTo(sink: okio.BufferedSink) {
            val totalBytes = contentLength()
            var bytesUploaded = 0L
            
            val progressSink = object : okio.ForwardingSink(sink) {
                override fun write(source: okio.Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    bytesUploaded += byteCount
                    onProgress(bytesUploaded, totalBytes)
                }
            }
            
            val bufferedSink = progressSink.buffer()
            requestBody.writeTo(bufferedSink)
            bufferedSink.flush()
        }
    }
    
    /**
     * Resultado do upload
     */
    sealed class UploadResult {
        data class Success(val response: String, val statusCode: Int) : UploadResult()
        data class Error(val message: String) : UploadResult()
    }
}