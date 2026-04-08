package com.vvai.calmwave.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Rastreia operações de sincronização ativas para feedback visual na UI.
 */
object SyncStatusTracker {
    private val activeOperations = AtomicInteger(0)
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun beginSync() {
        val count = activeOperations.incrementAndGet()
        _isSyncing.value = count > 0
    }

    fun endSync() {
        val count = activeOperations.updateAndGet { current ->
            if (current <= 0) 0 else current - 1
        }
        _isSyncing.value = count > 0
    }
}
