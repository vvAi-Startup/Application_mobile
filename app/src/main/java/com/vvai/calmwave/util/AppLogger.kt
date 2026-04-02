package com.vvai.calmwave.util

import android.util.Log

object AppLogger {
    fun d(tag: String, code: String, message: String) {
        Log.d(tag, "[$code] $message")
    }

    fun i(tag: String, code: String, message: String) {
        Log.i(tag, "[$code] $message")
    }

    fun w(tag: String, code: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, "[$code] $message", throwable)
        } else {
            Log.w(tag, "[$code] $message")
        }
    }

    fun e(tag: String, code: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, "[$code] $message", throwable)
        } else {
            Log.e(tag, "[$code] $message")
        }
    }
}
