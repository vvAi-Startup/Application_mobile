package com.vvai.calmwave.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log

private const val PERF_TAG = "PerformanceTuning"

fun isLowRamDevice(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return activityManager?.isLowRamDevice == true
}

fun getPlaybackUiPollingMs(context: Context): Long {
    return if (isLowRamDevice(context)) 120L else 50L
}

fun getPlaybackMonitorPollingMs(context: Context): Long {
    return if (isLowRamDevice(context)) 800L else 500L
}

fun logDevicePerformanceProfile(context: Context) {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memoryClassMb = activityManager?.memoryClass ?: -1
    val lowRam = activityManager?.isLowRamDevice == true

    Log.i(
        PERF_TAG,
        "device=${Build.MANUFACTURER} ${Build.MODEL}, android=${Build.VERSION.SDK_INT}, lowRam=$lowRam, memoryClassMb=$memoryClassMb"
    )
}
