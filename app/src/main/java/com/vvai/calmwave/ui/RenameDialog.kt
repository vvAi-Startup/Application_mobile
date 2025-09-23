package com.vvai.calmwave.ui

// ========================================
// FRONTEND: RENAME DIALOG COMPOSABLE
// ========================================
// Este arquivo contém o diálogo para renomear gravações
//  MANTER: Interface para renomeação de gravações

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vvai.calmwave.models.TempRecording

// ========================================
// FRONTEND: Diálogo de renomeação
// ========================================
//  MANTER: Interface para o usuário renomear gravações
@Composable
fun RenameRecordingDialog(
    tempRecording: TempRecording,
    currentText: String,
    isSaving: Boolean,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Renomear Gravação",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ========================================
                // FRONTEND: Informações da gravação
                // ========================================
                //  MANTER: Exibe detalhes da gravação para o usuário
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Detalhes da Gravação:",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Duração: ${formatDuration(tempRecording.duration)}",
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Tamanho: ${formatFileSize(tempRecording.fileSize)}",
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Data: ${formatTimestamp(tempRecording.startTime)}",
                            fontSize = 12.sp
                        )
                    }
                }
                
                // ========================================
                // FRONTEND: Campo de texto para renomeação
                // ========================================
                //  MANTER: Campo para o usuário inserir o novo nome
                OutlinedTextField(
                    value = currentText,
                    onValueChange = onTextChange,
                    label = { Text("Nome do arquivo") },
                    placeholder = { Text("Digite o nome desejado...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving
                )
                
                // ========================================
                // FRONTEND: Nome sugerido
                // ========================================
                //  MANTER: Mostra o nome sugerido como referência
                if (tempRecording.suggestedName.isNotEmpty()) {
                    Text(
                        text = "Nome sugerido: ${tempRecording.suggestedName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            // ========================================
            // FRONTEND: Botão de confirmação
            // ========================================
            //  MANTER: Botão para confirmar o nome e salvar
            Button(
                onClick = onConfirm,
                enabled = currentText.trim().isNotEmpty() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isSaving) "Salvando..." else "Salvar")
            }
        },
        dismissButton = {
            // ========================================
            // FRONTEND: Botão de cancelamento
            // ========================================
            //  MANTER: Botão para cancelar a gravação
            TextButton(
                onClick = onCancel,
                enabled = !isSaving
            ) {
                Text("Cancelar Gravação")
            }
        }
    )
}

// ========================================
// FRONTEND: Funções de formatação
// ========================================
//  MANTER: Funções para formatar informações para exibição

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

private fun formatFileSize(sizeBytes: Long): String {
    return when {
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
        else -> "${sizeBytes / (1024 * 1024)} MB"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val formatter = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    return formatter.format(date)
}
