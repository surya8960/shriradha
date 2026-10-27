package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

enum class DiagnosticStatus {
    PASSED,
    WARNING,
    FAILED,
    PENDING
}

data class DiagnosticItem(
    val id: String,
    val title: String,
    val description: String,
    val status: DiagnosticStatus,
    val detail: String
)

class DiagnosticTestSuite(private val context: Context) {

    fun runAllDiagnostics(): List<DiagnosticItem> {
        return listOf(
            checkPermissions(),
            checkVoskOfflineEngine(),
            checkSpeechRecognitionService(),
            checkOfflineRecognitionCapability(),
            checkScreenOffBackgroundSupport()
        )
    }

    private fun checkVoskOfflineEngine(): DiagnosticItem {
        val voskEngine = VoskSpeechEngine.getInstance(context)
        return if (voskEngine.isEngineAvailable()) {
            DiagnosticItem(
                id = "vosk_engine",
                title = "Offline Vosk Keyword Spotter",
                description = "Direct AudioRecord stream targeting only the names you add",
                status = DiagnosticStatus.PASSED,
                detail = "Vosk model is active. Google recognizer is completely bypassed; ZERO mic beep sound."
            )
        } else {
            DiagnosticItem(
                id = "vosk_engine",
                title = "Offline Vosk Model Required",
                description = "Download Hindi model in-app for 100% beep-free offline listening",
                status = DiagnosticStatus.WARNING,
                detail = "Vosk model not yet downloaded. Tap 'Download Hindi Model' to activate zero-beep continuous listening."
            )
        }
    }

    private fun checkPermissions(): DiagnosticItem {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return if (hasMic && hasNotification) {
            DiagnosticItem(
                id = "permissions",
                title = "Permissions (Mic & Notification)",
                description = "Microphone & Foreground Notification access",
                status = DiagnosticStatus.PASSED,
                detail = "RECORD_AUDIO is granted. POST_NOTIFICATIONS is granted."
            )
        } else if (!hasMic) {
            DiagnosticItem(
                id = "permissions",
                title = "Permissions (Mic Missing)",
                description = "RECORD_AUDIO is required to listen for your name",
                status = DiagnosticStatus.FAILED,
                detail = "Grant Microphone permission to allow voice recognition."
            )
        } else {
            DiagnosticItem(
                id = "permissions",
                title = "Notification Permission Missing",
                description = "POST_NOTIFICATIONS is required for Android 13+ foreground service",
                status = DiagnosticStatus.WARNING,
                detail = "Notification permission not granted. Service may be killed in background."
            )
        }
    }

    private fun checkSpeechRecognitionService(): DiagnosticItem {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        return if (available) {
            DiagnosticItem(
                id = "speech_service",
                title = "Speech Recognition Engine",
                description = "System speech-to-text availability",
                status = DiagnosticStatus.PASSED,
                detail = "Android SpeechRecognizer is active and available."
            )
        } else {
            DiagnosticItem(
                id = "speech_service",
                title = "Speech Recognition Engine",
                description = "System speech-to-text engine missing",
                status = DiagnosticStatus.FAILED,
                detail = "No SpeechRecognizer found. Please install Google Speech Services from Play Store."
            )
        }
    }

    private fun checkOfflineRecognitionCapability(): DiagnosticItem {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        var onDeviceSupported = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                onDeviceSupported = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            } catch (e: Throwable) {
                onDeviceSupported = false
            }
        }

        return when {
            onDeviceSupported -> {
                DiagnosticItem(
                    id = "offline_speech",
                    title = "Offline Hindi Speech Recognition",
                    description = "On-device speech recognition model",
                    status = DiagnosticStatus.PASSED,
                    detail = "Device supports on-device speech recognition without network requirement."
                )
            }
            isConnected -> {
                DiagnosticItem(
                    id = "offline_speech",
                    title = "Network Speech Recognition (Online)",
                    description = "Internet active for Hindi speech processing",
                    status = DiagnosticStatus.PASSED,
                    detail = "Connected to internet. If offline Hindi model is missing, Google Speech Services uses online recognition."
                )
            }
            else -> {
                DiagnosticItem(
                    id = "offline_speech",
                    title = "Internet Disconnected / Offline Model Needed",
                    description = "Device is offline without guaranteed on-device engine",
                    status = DiagnosticStatus.WARNING,
                    detail = "Offline. Recognition might fail if offline Hindi language pack is not downloaded in Google app settings."
                )
            }
        }
    }

    private fun checkScreenOffBackgroundSupport(): DiagnosticItem {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }

        return if (isIgnoringBatteryOptimizations) {
            DiagnosticItem(
                id = "screen_off",
                title = "Screen-Off & Background Execution",
                description = "WakeLock + Battery Optimization bypass",
                status = DiagnosticStatus.PASSED,
                detail = "Foreground Service with WakeLock configured. Battery saver bypass is enabled."
            )
        } else {
            DiagnosticItem(
                id = "screen_off",
                title = "Screen-Off Battery Optimization Warning",
                description = "Aggressive battery managers may restrict mic while screen is locked",
                status = DiagnosticStatus.WARNING,
                detail = "Some OEMs (Xiaomi, Samsung) restrict microphone when screen turns off. Exclude Radha Reminder from battery optimization for 100% reliability."
            )
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }
}
