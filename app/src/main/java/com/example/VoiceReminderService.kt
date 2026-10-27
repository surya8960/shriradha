package com.example

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class VoiceReminderService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var notificationManager: NotificationManager? = null

    private var masterTimeoutJob: Job? = null
    private var autoStopJob: Job? = null
    private var intervalWaitJob: Job? = null
    private val isRecognizedInCurrentCycle = AtomicBoolean(false)
    private val listeningSessionId = AtomicLong(0L)

    private var isCycleActive = false
    private var isServiceStopping = false
    private var isJapModeActive = false
    private var japNames: List<String> = emptyList()
    private var preferOfflineMode = true
    private val mutedStreams = mutableSetOf<Int>()
    private var audioPlayRetryCount = 0

    private lateinit var audioPrefs: AudioPreferenceManager
    private lateinit var voskEngine: VoskSpeechEngine

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceReminderService onCreate")
        audioPrefs = AudioPreferenceManager(this)
        voskEngine = VoskSpeechEngine.getInstance(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RadhaReminder:ServiceWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START -> {
                // Double-start protection: guard against duplicate activations
                if (isCycleActive) {
                    Log.w(TAG, "Service cycle is already active. Ignoring duplicate start request.")
                    return START_NOT_STICKY
                }
                isServiceStopping = false
                if (NameMatcher.cleanNames(audioPrefs.getExtraKeywords()).isEmpty()) {
                    handleError("Pehle koi naam add karein (Settings > Names to listen for).")
                    return START_NOT_STICKY
                }
                if (!startForegroundSafely()) {
                    return START_NOT_STICKY
                }
                startReminderCycle()
            }
            ACTION_STOP -> {
                stopReminderCycle(keepErrorState = false)
                stopSelf()
            }
            ACTION_REMIND_NOW -> {
                if (isCycleActive && _statusFlow.value.phase == ReminderPhase.WAITING_NEXT_INTERVAL) {
                    Log.d(TAG, "ACTION_REMIND_NOW received: skipping wait and playing reminder now.")
                    cancelAlarmWakeup()
                    intervalWaitJob?.cancel()
                    intervalWaitJob = null
                    playNextReminder()
                }
            }
            ACTION_ALARM_CYCLE_WAKEUP -> {
                Log.d(TAG, "ACTION_ALARM_CYCLE_WAKEUP received from AlarmManager deep Doze wakeup.")
                if (isCycleActive && _statusFlow.value.phase == ReminderPhase.WAITING_NEXT_INTERVAL) {
                    cancelAlarmWakeup()
                    intervalWaitJob?.cancel()
                    intervalWaitJob = null
                    playNextReminder()
                }
            }
            ACTION_START_JAP -> {
                val names = audioPrefs.getJapNames()
                if (names.isEmpty()) {
                    Log.w(TAG, "Cannot start Jap mode: no name added")
                    stopSelf()
                    return START_NOT_STICKY
                }
                stopReminderCycle(keepErrorState = false)
                startJapMode(names)
            }
            ACTION_STOP_JAP -> {
                stopJapMode()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundSafely(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            handleError("Microphone permission is missing or revoked.")
            return false
        }

        return try {
            val notification = buildNotification("Initializing Radha Reminder...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            refreshWakeLock()
            true
        } catch (e: SecurityException) {
            handleError("Cannot start microphone foreground service: ${e.message}")
            false
        }
    }

    private fun refreshWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            // Acquire wake lock with generous timeout covering intervals + window + buffer
            val intervalMinutes = audioPrefs.cycleIntervalMinutes
            val timeoutMs = (intervalMinutes * 60L + audioPrefs.listeningWindowSeconds + 120L) * 1000L
            wakeLock?.acquire(timeoutMs.coerceAtLeast(10 * 60 * 1000L))
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock: ${e.message}")
        }
    }

    private fun startReminderCycle() {
        // Pre-flight check: verify mic permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            handleError("Microphone permission is missing or revoked.")
            return
        }

        val reminderAudio = audioPrefs.getReminderAudio()
        if (reminderAudio == null) {
            handleError("Reminder audio file not selected or missing.")
            return
        }

        val confirmAudio = audioPrefs.getConfirmationAudio()
        if (confirmAudio == null) {
            handleError("Confirmation audio file not selected or missing.")
            return
        }

        // Pre-flight check: verify audio URIs are readable
        try {
            contentResolver.openFileDescriptor(reminderAudio.uri, "r")?.close()
        } catch (e: Exception) {
            handleError("Reminder audio is unreadable. Please choose audio again.")
            return
        }
        try {
            contentResolver.openFileDescriptor(confirmAudio.uri, "r")?.close()
        } catch (e: Exception) {
            handleError("Confirmation audio is unreadable. Please choose audio again.")
            return
        }

        isCycleActive = true
        isServiceStopping = false
        audioPlayRetryCount = 0
        val windowSec = audioPrefs.listeningWindowSeconds

        _statusFlow.update {
            it.copy(
                isServiceRunning = true,
                cycleCount = 0,
                recognizedCount = 0,
                errorMessage = "",
                totalListeningWindowSeconds = windowSec,
                secondsRemaining = windowSec
            )
        }

        initAutoStopTimer()
        playNextReminder()
    }

    private fun initAutoStopTimer() {
        autoStopJob?.cancel()
        val minutes = audioPrefs.autoStopMinutes
        if (minutes > 0) {
            autoStopJob = serviceScope.launch {
                val totalSeconds = minutes * 60L
                for (secRemaining in totalSeconds downTo 1L) {
                    _statusFlow.update { it.copy(autoStopSecondsRemaining = secRemaining) }
                    delay(1000)
                }
                Log.i(TAG, "Auto-stop timer expired after $minutes minutes. Stopping cycle.")
                stopReminderCycle(keepErrorState = false)
                stopSelf()
            }
        } else {
            _statusFlow.update { it.copy(autoStopSecondsRemaining = 0L) }
        }
    }

    private fun stopReminderCycle(keepErrorState: Boolean = false) {
        isCycleActive = false
        isServiceStopping = true
        listeningSessionId.incrementAndGet()

        cancelAlarmWakeup()

        autoStopJob?.cancel()
        autoStopJob = null

        masterTimeoutJob?.cancel()
        masterTimeoutJob = null

        intervalWaitJob?.cancel()
        intervalWaitJob = null

        stopAndReleaseSpeechRecognizer()
        stopAndReleaseMediaPlayer()
        unmuteAudioCues()

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock: ${e.message}")
        }

        _statusFlow.update {
            it.copy(
                phase = if (keepErrorState) ReminderPhase.ERROR else ReminderPhase.IDLE,
                isServiceRunning = false,
                secondsRemaining = audioPrefs.listeningWindowSeconds,
                speechRmsDb = 0f,
                autoStopSecondsRemaining = 0L,
                errorMessage = if (keepErrorState) it.errorMessage else ""
            )
        }

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling notification: ${e.message}")
        }
    }

    /**
     * STEP 1: Play Reminder Audio (~5 seconds) to completion.
     * Strictly no microphone listening while this audio is playing.
     */
    private fun playNextReminder() {
        if (!isCycleActive || isServiceStopping) return

        cancelAlarmWakeup()
        intervalWaitJob?.cancel()
        intervalWaitJob = null

        refreshWakeLock()
        stopAndReleaseSpeechRecognizer()

        // Preload offline Vosk model in background while reminder audio plays
        voskEngine.preloadModelAsync()

        val reminderAudio = audioPrefs.getReminderAudio()
        if (reminderAudio == null) {
            handleError("Reminder audio file not selected or missing.")
            return
        }

        val currentCycle = _statusFlow.value.cycleCount + 1
        val windowSec = audioPrefs.listeningWindowSeconds

        _statusFlow.update {
            it.copy(
                phase = ReminderPhase.PLAYING_REMINDER,
                cycleCount = currentCycle,
                currentAudioTitle = reminderAudio.displayName,
                totalListeningWindowSeconds = windowSec,
                secondsRemaining = windowSec,
                nextCycleSecondsRemaining = 0,
                speechRmsDb = 0f
            )
        }
        updateNotification("Cycle #$currentCycle: Playing Reminder...")

        playAudio(
            uri = reminderAudio.uri,
            onComplete = {
                audioPlayRetryCount = 0
                Log.d(TAG, "Reminder playback finished completely. Transitioning to silent listening.")
                startSilentListening()
            },
            onError = { err ->
                Log.e(TAG, "Error playing reminder audio: $err")
                audioPlayRetryCount++
                if (audioPlayRetryCount >= 3) {
                    handleError("Reminder audio playback failed 3 times. Please re-select audio file.")
                } else {
                    serviceScope.launch {
                        delay(2000)
                        if (isCycleActive && !isServiceStopping) playNextReminder()
                    }
                }
            }
        )
    }

    /**
     * STEP 2: Silent Microphone Listening for user-configured window duration (default 15s).
     * App plays ZERO sound. Strictly evaluates completed utterances.
     */
    private fun startSilentListening() {
        if (!isCycleActive || isServiceStopping) return

        stopAndReleaseMediaPlayer()
        isRecognizedInCurrentCycle.set(false)

        val currentSession = listeningSessionId.incrementAndGet()
        val windowSec = audioPrefs.listeningWindowSeconds

        _statusFlow.update {
            it.copy(
                phase = ReminderPhase.LISTENING_SILENT,
                totalListeningWindowSeconds = windowSec,
                secondsRemaining = windowSec,
                speechRmsDb = 0f
            )
        }
        updateNotification("Cycle #${_statusFlow.value.cycleCount}: Listening silently for your name (${windowSec}s)")

        // If offline Vosk engine is available, use it directly (Google recognizer is bypassed, ZERO mic beep)
        if (voskEngine.isEngineAvailable()) {
            Log.i(TAG, "Offline Vosk active for session $currentSession: Google recognizer bypassed, ZERO mic beep.")
            val started = voskEngine.startListening(
                names = audioPrefs.getExtraKeywords(),
                onResult = { detectedText ->
                    if (currentSession == listeningSessionId.get() && _statusFlow.value.phase == ReminderPhase.LISTENING_SILENT) {
                        Log.d(TAG, "Vosk detected completed speech in session $currentSession: $detectedText")
                        processSpeechMatches(listOf(detectedText), currentSession)
                    } else {
                        Log.d(TAG, "Discarded stale Vosk result: $detectedText")
                    }
                },
                onPartialHypothesis = { _ -> },
                onErrorCallback = { err ->
                    Log.w(TAG, "Vosk listener note: $err")
                }
            )
            if (!started) {
                initAndStartSpeechRecognizer(currentSession)
            }
        } else {
            initAndStartSpeechRecognizer(currentSession)
        }

        masterTimeoutJob?.cancel()
        masterTimeoutJob = serviceScope.launch {
            val startElapsed = SystemClock.elapsedRealtime()
            val endElapsed = startElapsed + windowSec * 1000L

            while (SystemClock.elapsedRealtime() < endElapsed) {
                val secRemaining = (((endElapsed - SystemClock.elapsedRealtime()) + 999) / 1000L).toInt().coerceIn(0, windowSec)
                _statusFlow.update { it.copy(secondsRemaining = secRemaining) }
                updateNotification("Listening silently for your name (${secRemaining}s remaining)")
                delay(1000)
            }

            if (!isRecognizedInCurrentCycle.get() && isCycleActive && !isServiceStopping &&
                currentSession == listeningSessionId.get() && _statusFlow.value.phase == ReminderPhase.LISTENING_SILENT) {
                Log.d(TAG, "$windowSec-second silent listening window ended without recognition match.")
                handleSilentTimeout()
            }
        }
    }

    private fun muteAudioCues() {
        if (!audioPrefs.muteMicBeep || voskEngine.isEngineAvailable()) return
        try {
            audioManager?.let { am ->
                val streams = listOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)
                for (stream in streams) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                        } else {
                            @Suppress("DEPRECATION")
                            am.setStreamMute(stream, true)
                        }
                        mutedStreams.add(stream)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot mute stream $stream: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not mute audio cues: ${e.message}")
        }
    }

    private fun unmuteAudioCues() {
        if (mutedStreams.isEmpty()) return
        try {
            audioManager?.let { am ->
                for (stream in mutedStreams) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                        } else {
                            @Suppress("DEPRECATION")
                            am.setStreamMute(stream, false)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not unmute stream $stream: ${e.message}")
                    }
                }
                mutedStreams.clear()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not unmute audio cues: ${e.message}")
        }
    }

    private fun initAndStartSpeechRecognizer(sessionId: Long) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "SpeechRecognizer not available on this device!")
            _statusFlow.update { it.copy(errorMessage = "Speech recognition service not available on device.") }
            return
        }

        try {
            stopAndReleaseSpeechRecognizer()

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        serviceScope.launch {
                            delay(250)
                            unmuteAudioCues()
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        unmuteAudioCues()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        _statusFlow.update { it.copy(speechRmsDb = rmsdB.coerceAtLeast(0f)) }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        unmuteAudioCues()
                    }

                    override fun onError(error: Int) {
                        val errorDesc = speechErrorToString(error)
                        Log.w(TAG, "SpeechRecognizer onError: $errorDesc ($error)")
                        unmuteAudioCues()

                        if (preferOfflineMode && (error == SpeechRecognizer.ERROR_SERVER || error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_CLIENT)) {
                            Log.i(TAG, "Offline recognition failed. Switching to online Hindi recognition.")
                            preferOfflineMode = false
                        }

                        if ((error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) &&
                            !isRecognizedInCurrentCycle.get() && isCycleActive && !isServiceStopping &&
                            sessionId == listeningSessionId.get() &&
                            _statusFlow.value.phase == ReminderPhase.LISTENING_SILENT
                        ) {
                            serviceScope.launch {
                                delay(600)
                                if (!isRecognizedInCurrentCycle.get() && isCycleActive && !isServiceStopping &&
                                    sessionId == listeningSessionId.get() &&
                                    _statusFlow.value.phase == ReminderPhase.LISTENING_SILENT
                                ) {
                                    startListeningIntent()
                                }
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        unmuteAudioCues()
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        Log.d(TAG, "SpeechRecognizer onResults: $matches")
                        val matched = processSpeechMatches(matches, sessionId)
                        if (!matched && !isRecognizedInCurrentCycle.get() && isCycleActive && !isServiceStopping &&
                            sessionId == listeningSessionId.get() &&
                            _statusFlow.value.phase == ReminderPhase.LISTENING_SILENT
                        ) {
                            serviceScope.launch {
                                delay(200)
                                if (!isRecognizedInCurrentCycle.get() && isCycleActive && !isServiceStopping &&
                                    sessionId == listeningSessionId.get() &&
                                    _statusFlow.value.phase == ReminderPhase.LISTENING_SILENT
                                ) {
                                    startListeningIntent()
                                }
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        // Strictly ignore partial results to prevent premature 'ra' triggers
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            startListeningIntent()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SpeechRecognizer: ${e.message}", e)
        }
    }

    private fun startListeningIntent() {
        try {
            muteAudioCues()

            val windowMs = audioPrefs.listeningWindowSeconds * 1000L

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOfflineMode)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, windowMs)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, windowMs)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, windowMs)
            }
            speechRecognizer?.startListening(intent)

            serviceScope.launch {
                delay(600)
                unmuteAudioCues()
            }
        } catch (e: Exception) {
            unmuteAudioCues()
            Log.e(TAG, "Exception in startListening: ${e.message}")
        }
    }

    private fun processSpeechMatches(matches: List<String>?, sessionId: Long): Boolean {
        if (matches == null || matches.isEmpty()) return false
        if (sessionId != listeningSessionId.get() || _statusFlow.value.phase != ReminderPhase.LISTENING_SILENT) return false
        if (isRecognizedInCurrentCycle.get() || isServiceStopping) return false

        val names = audioPrefs.getExtraKeywords()
        val matchedPhrase = NameMatcher.firstMatchingPhrase(matches, names)
        if (matchedPhrase != null) {
            if (isRecognizedInCurrentCycle.compareAndSet(false, true)) {
                Log.d(TAG, "Target word successfully recognized: $matchedPhrase")
                handleRadhaRecognized(matchedPhrase)
                return true
            }
        }
        return false
    }

    /**
     * STEP 3: If "Radha" is recognized:
     * - Immediately stop listening.
     * - Cancel timer.
     * - Trigger short haptic vibration if enabled.
     * - Increment persistent stats.
     * - Play Confirmation Audio.
     * - Once finished, immediately restart cycle with Reminder Audio.
     */
    private fun handleRadhaRecognized(detectedWord: String) {
        listeningSessionId.incrementAndGet()
        masterTimeoutJob?.cancel()
        masterTimeoutJob = null
        stopAndReleaseSpeechRecognizer()

        triggerHapticVibration()
        audioPrefs.incrementRecognitionStats()

        val newRecognizedCount = _statusFlow.value.recognizedCount + 1
        _statusFlow.update {
            it.copy(
                phase = ReminderPhase.RADHA_RECOGNIZED,
                recognizedCount = newRecognizedCount,
                detectedText = detectedWord,
                secondsRemaining = 0,
                speechRmsDb = 0f
            )
        }
        updateNotification("Word '$detectedWord' recognized! ($newRecognizedCount times)")

        val confirmAudio = audioPrefs.getConfirmationAudio()
        if (confirmAudio == null) {
            Log.w(TAG, "Confirmation audio missing, scheduling next reminder.")
            scheduleNextCycleOrWait()
            return
        }

        _statusFlow.update {
            it.copy(
                phase = ReminderPhase.PLAYING_CONFIRMATION,
                currentAudioTitle = confirmAudio.displayName
            )
        }

        playAudio(
            uri = confirmAudio.uri,
            onComplete = {
                audioPlayRetryCount = 0
                Log.d(TAG, "Confirmation audio completed. Scheduling next cycle (interval: ${audioPrefs.cycleIntervalMinutes}m).")
                if (isCycleActive && !isServiceStopping) {
                    scheduleNextCycleOrWait()
                }
            },
            onError = { err ->
                Log.e(TAG, "Error playing confirmation audio: $err")
                if (isCycleActive && !isServiceStopping) {
                    scheduleNextCycleOrWait()
                }
            }
        )
    }

    private fun triggerHapticVibration() {
        if (!audioPrefs.vibrateOnRecognition) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(200)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not trigger vibration: ${e.message}")
        }
    }

    /**
     * STEP 4: If "Radha" is NOT recognized after listening window:
     * - Stop listening cleanly.
     * - Schedule next reminder after configured interval (or restart immediately if 0).
     */
    private fun handleSilentTimeout() {
        listeningSessionId.incrementAndGet()
        stopAndReleaseSpeechRecognizer()

        _statusFlow.update {
            it.copy(
                phase = ReminderPhase.CYCLE_TIMEOUT,
                secondsRemaining = 0,
                speechRmsDb = 0f
            )
        }
        updateNotification("Listening timeout ended. Waiting for next interval...")

        if (isCycleActive && !isServiceStopping) {
            scheduleNextCycleOrWait()
        }
    }

    /**
     * STEP 5: Interval Waiting between cycles (e.g. 4 minutes, up to 30 minutes).
     * Microphone is completely OFF and released during this waiting interval to conserve battery.
     * Uses AlarmManager.setExactAndAllowWhileIdle to guarantee wakeup even during Android deep Doze.
     */
    private fun scheduleNextCycleOrWait() {
        cancelAlarmWakeup()
        intervalWaitJob?.cancel()
        intervalWaitJob = null
        stopAndReleaseSpeechRecognizer()
        stopAndReleaseMediaPlayer()

        val intervalMinutes = audioPrefs.cycleIntervalMinutes
        if (intervalMinutes <= 0) {
            if (isCycleActive && !isServiceStopping) {
                playNextReminder()
            }
            return
        }

        val totalSec = intervalMinutes * 60
        _statusFlow.update {
            it.copy(
                phase = ReminderPhase.WAITING_NEXT_INTERVAL,
                nextCycleSecondsRemaining = totalSec,
                totalIntervalSeconds = totalSec,
                secondsRemaining = 0,
                speechRmsDb = 0f
            )
        }

        val m = totalSec / 60
        val s = totalSec % 60
        updateNotification("Next reminder in ${m}m ${String.format("%02d", s)}s (Mic sleeping)")

        // Schedule AlarmManager exact alarm for deep Doze survival
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + totalSec * 1000L
        val pendingAlarm = getAlarmWakeupPendingIntent()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingAlarm)
            } else {
                alarmManager?.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingAlarm)
            }
            Log.d(TAG, "Scheduled AlarmManager exact wakeup in $totalSec seconds.")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set exact alarm, falling back to setAndAllowWhileIdle: ${e.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingAlarm)
            }
        }

        // Coroutine countdown for UI updates based on SystemClock.elapsedRealtime
        intervalWaitJob = serviceScope.launch {
            val startElapsed = SystemClock.elapsedRealtime()
            val endElapsed = startElapsed + totalSec * 1000L
            var lastNotificationMin = -1

            while (SystemClock.elapsedRealtime() < endElapsed) {
                val secRemaining = (((endElapsed - SystemClock.elapsedRealtime()) + 999) / 1000L).toInt().coerceAtLeast(0)
                _statusFlow.update { it.copy(nextCycleSecondsRemaining = secRemaining) }

                val minLeft = secRemaining / 60
                val secLeft = secRemaining % 60

                // Throttle notification updates: update on minute change or every 30s
                if (minLeft != lastNotificationMin || secLeft == 30 || secLeft == 0) {
                    lastNotificationMin = minLeft
                    updateNotification("Next reminder in ${minLeft}m ${String.format("%02d", secLeft)}s (Mic sleeping)")
                }

                delay(1000)
            }

            if (isCycleActive && !isServiceStopping) {
                Log.d(TAG, "$intervalMinutes-minute interval elapsed. Playing next reminder.")
                playNextReminder()
            }
        }
    }

    private fun getAlarmWakeupPendingIntent(): PendingIntent {
        val alarmIntent = Intent(this, VoiceReminderService::class.java).apply {
            action = ACTION_ALARM_CYCLE_WAKEUP
        }
        return PendingIntent.getService(
            this,
            ALARM_PENDING_INTENT_REQ,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelAlarmWakeup() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(getAlarmWakeupPendingIntent())
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling alarm wakeup: ${e.message}")
        }
    }

    private fun playAudio(uri: Uri, onComplete: () -> Unit, onError: (String) -> Unit) {
        stopAndReleaseMediaPlayer()

        try {
            requestAudioFocus()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@VoiceReminderService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setOnPreparedListener { mp ->
                    mp.start()
                }
                setOnCompletionListener {
                    abandonAudioFocus()
                    stopAndReleaseMediaPlayer()
                    onComplete()
                }
                setOnErrorListener { _, what, extra ->
                    abandonAudioFocus()
                    stopAndReleaseMediaPlayer()
                    onError("MediaPlayer error what=$what extra=$extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            abandonAudioFocus()
            stopAndReleaseMediaPlayer()
            onError("Exception preparing audio: ${e.message}")
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    { /* No-op AudioFocusChangeListener */ },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not request audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not abandon audio focus: ${e.message}")
        }
    }

    private fun stopAndReleaseMediaPlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    private fun stopAndReleaseSpeechRecognizer() {
        voskEngine.stopListening()
        unmuteAudioCues()
        try {
            speechRecognizer?.let {
                it.stopListening()
                it.cancel()
                it.destroy()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    private fun handleError(message: String) {
        Log.e(TAG, "Service error: $message")
        _statusFlow.update {
            it.copy(
                phase = ReminderPhase.ERROR,
                errorMessage = message,
                isServiceRunning = false
            )
        }
        stopReminderCycle(keepErrorState = true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radha Reminder Active Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time status of the Radha reminder listening cycle"
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceReminderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Radha Reminder")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_radha)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)

        if (_statusFlow.value.phase == ReminderPhase.WAITING_NEXT_INTERVAL) {
            val remindNowIntent = Intent(this, VoiceReminderService::class.java).apply {
                action = ACTION_REMIND_NOW
            }
            val remindNowPendingIntent = PendingIntent.getService(
                this,
                2,
                remindNowIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Remind Now", remindNowPendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification(text: String) {
        if (isServiceStopping || !isCycleActive) return
        try {
            notificationManager?.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: Exception) {
            Log.w(TAG, "Error updating notification: ${e.message}")
        }
    }

    private fun speechErrorToString(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout (silence)"
            else -> "Speech error $error"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Jap Foreground Mode (Screen-Off Background Meditation) ---

    private fun japTitle(): String = japNames.joinToString(" / ")

    private fun startJapMode(names: List<String>) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            handleError("Microphone permission missing for Jap counter")
            return
        }
        japNames = names
        isJapModeActive = true
        _isJapServiceActiveFlow.value = true

        val initialBead = audioPrefs.japCurrentBead
        val initialMalas = audioPrefs.japCompletedMalas
        val notification = buildJapNotification(japTitle(), initialBead, initialMalas)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(12 * 3600 * 1000L) // Safe 12-hour wake lock for Jap meditation
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquire error in Jap mode: ${e.message}")
        }

        startContinuousJapRecognition()
    }

    private fun startContinuousJapRecognition() {
        // Offline Vosk can only recognize Devanagari names; Latin-only names use the Google fallback.
        if (voskEngine.isEngineAvailable() && NameMatcher.voskNames(japNames).isNotEmpty()) {
            val started = voskEngine.startListening(
                names = japNames,
                onResult = { text ->
                    if (isJapModeActive) {
                        handleJapSpeechResult(text)
                    }
                },
                onPartialHypothesis = null, // partial text can be half a name: never counted
                onErrorCallback = { err ->
                    Log.w(TAG, "Vosk Jap mode error: $err")
                }
            )
            if (!started) {
                startAndroidSpeechJap()
            }
        } else {
            startAndroidSpeechJap()
        }
    }

    /** Counts only COMPLETE names. Half names / other words give 0. */
    private fun handleJapSpeechResult(text: String) {
        val count = NameMatcher.countMatches(text, japNames)
        if (count > 0) {
            val (newBead, newMalas) = audioPrefs.incrementJap(count)
            if (audioPrefs.japVibrateOnChant) {
                triggerHapticVibration()
            }
            updateJapNotification(japTitle(), newBead, newMalas)
            _japChantEventFlow.tryEmit(Pair(japTitle(), count))
        }
    }

    private fun startAndroidSpeechJap() {
        if (!isJapModeActive) return
        serviceScope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@VoiceReminderService).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) {
                            if (isJapModeActive) {
                                serviceScope.launch {
                                    delay(300)
                                    if (isJapModeActive) {
                                        startAndroidSpeechJapIntent()
                                    }
                                }
                            }
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            matches?.firstOrNull()?.let { phrase ->
                                handleJapSpeechResult(phrase)
                            }
                            if (isJapModeActive) {
                                serviceScope.launch {
                                    delay(100)
                                    if (isJapModeActive) {
                                        startAndroidSpeechJapIntent()
                                    }
                                }
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
                startAndroidSpeechJapIntent()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Android speech recognizer for Jap: ${e.message}")
            }
        }
    }

    private fun startAndroidSpeechJapIntent() {
        if (!isJapModeActive) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startAndroidSpeechJapIntent error: ${e.message}")
        }
    }

    private fun buildJapNotification(targetName: String, bead: Int, malas: Int): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceReminderService::class.java).apply {
            action = ACTION_STOP_JAP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            11,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Jap: $targetName")
            .setContentText("Bead $bead / 108 • Completed: $malas Malas")
            .setSmallIcon(R.drawable.ic_stat_radha)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .addAction(android.R.drawable.ic_media_pause, "Pause Jap", stopPendingIntent)
            .build()
    }

    private fun updateJapNotification(targetName: String, bead: Int, malas: Int) {
        if (!isJapModeActive) return
        try {
            notificationManager?.notify(NOTIFICATION_ID, buildJapNotification(targetName, bead, malas))
        } catch (e: Exception) {
            Log.w(TAG, "updateJapNotification error: ${e.message}")
        }
    }

    private fun stopJapMode() {
        isJapModeActive = false
        _isJapServiceActiveFlow.value = false
        voskEngine.stopListening()
        stopAndReleaseSpeechRecognizer()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock release error: ${e.message}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "VoiceReminderService onDestroy")
        if (isJapModeActive) {
            stopJapMode()
        }
        stopReminderCycle(keepErrorState = true)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VoiceReminderService"
        const val ACTION_START = "com.example.radharemind.ACTION_START"
        const val ACTION_STOP = "com.example.radharemind.ACTION_STOP"
        const val ACTION_REMIND_NOW = "com.example.radharemind.ACTION_REMIND_NOW"
        const val ACTION_ALARM_CYCLE_WAKEUP = "com.example.radharemind.ACTION_ALARM_CYCLE_WAKEUP"
        const val ACTION_START_JAP = "com.example.radharemind.ACTION_START_JAP"
        const val ACTION_STOP_JAP = "com.example.radharemind.ACTION_STOP_JAP"
        const val EXTRA_JAP_TARGET_NAME = "extra_jap_target_name"
        private const val CHANNEL_ID = "radha_reminder_channel"
        private const val NOTIFICATION_ID = 1008
        private const val ALARM_PENDING_INTENT_REQ = 3003

        private val _isJapServiceActiveFlow = MutableStateFlow(false)
        val isJapServiceActiveFlow: StateFlow<Boolean> = _isJapServiceActiveFlow.asStateFlow()

        private val _japChantEventFlow = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 16)
        val japChantEventFlow: SharedFlow<Pair<String, Int>> = _japChantEventFlow.asSharedFlow()

        private val _statusFlow = MutableStateFlow(VoiceReminderStatus())
        val statusFlow: StateFlow<VoiceReminderStatus> = _statusFlow.asStateFlow()

        fun clearErrorState() {
            if (_statusFlow.value.phase == ReminderPhase.ERROR) {
                _statusFlow.update {
                    it.copy(
                        phase = ReminderPhase.IDLE,
                        errorMessage = ""
                    )
                }
            }
        }
    }
}
