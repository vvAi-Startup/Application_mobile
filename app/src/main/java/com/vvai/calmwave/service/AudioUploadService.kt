package com.vvai.calmwave.service

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Serviço para upload de arquivos de áudio processados
 */
class AudioUploadService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Faz upload de um arquivo de áudio processado para o servidor
     * 
     * @param uploadUrl URL do endpoint de upload
     * @param audioFile Arquivo de áudio a ser enviado
     * @param sessionId ID da sessão (opcional)
     * @param metadata Metadados adicionais (opcional)
     * @param onProgress Callback para acompanhar o progresso do upload
     * @return Resultado do upload
     */
    suspend fun uploadProcessedAudio(
        uploadUrl: String,
        audioFile: File,
        sessionId: String? = null,
        metadata: Map<String, String> = emptyMap(),
        onProgress: ((bytesUploaded: Long, totalBytes: Long) -> Unit)? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        
        if (!audioFile.exists()) {
            return@withContext UploadResult.Error("Arquivo não encontrado: ${audioFile.absolutePath}")
        }
        
        try {
            // Cria o corpo da requisição multipart
            val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
            
            // Adiciona o arquivo de áudio
            val audioRequestBody = audioFile.asRequestBody("audio/wav".toMediaType())
            val progressRequestBody = if (onProgress != null) {
                ProgressRequestBody(audioRequestBody, onProgress)
            } else {
                audioRequestBody
            }
            
            requestBodyBuilder.addFormDataPart(
                "audio_file",
                audioFile.name,
                progressRequestBody
            )
            
            // Adiciona sessionId se fornecido
            sessionId?.let {
                requestBodyBuilder.addFormDataPart("session_id", it)
            }
            
            // Adiciona metadados adicionais
            metadata.forEach { (key, value) ->
                requestBodyBuilder.addFormDataPart(key, value)
            }
            
            // Adiciona informações do arquivo
            requestBodyBuilder.addFormDataPart("file_size", audioFile.length().toString())
            requestBodyBuilder.addFormDataPart("file_type", "audio/wav")
            requestBodyBuilder.addFormDataPart("upload_timestamp", System.currentTimeMillis().toString())
            
            val requestBody = requestBodyBuilder.build()
            
            // Cria a requisição
            val request = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .addHeader("Content-Type", "multipart/form-data")
                .build()
            
            // Executa a requisição
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                
                continuation.invokeOnCancellation {
                    call.cancel()
                }
                
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        response.use { resp ->
                            if (resp.isSuccessful) {
                                val responseBody = resp.body?.string() ?: ""
                                continuation.resume(UploadResult.Success(responseBody, resp.code))
                            } else {
                                val errorBody = resp.body?.string() ?: "Erro desconhecido"
                                continuation.resume(UploadResult.Error("Upload falhou: ${resp.code} - $errorBody"))
                            }
                        }
                    }
                })
            }
            
        } catch (e: Exception) {
            UploadResult.Error("Erro durante upload: ${e.message}")
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
            
            val bufferedSink = okio.buffer(progressSink)
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