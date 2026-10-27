package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ModelDownloadProgress(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedMb: Float = 0f,
    val totalMb: Float = 0f,
    val statusText: String = "",
    val error: String? = null
)

class VoiceReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val audioPrefs = AudioPreferenceManager(application)
    private val diagnosticsSuite = DiagnosticTestSuite(application)
    val voskEngine = VoskSpeechEngine.getInstance(application)

    private val _isVoskReady = MutableStateFlow(voskEngine.isEngineAvailable())
    val isVoskReady: StateFlow<Boolean> = _isVoskReady.asStateFlow()

    private val _requireModelPrompt = MutableStateFlow(false)
    val requireModelPrompt: StateFlow<Boolean> = _requireModelPrompt.asStateFlow()

    private val _requireNamePrompt = MutableStateFlow(false)
    val requireNamePrompt: StateFlow<Boolean> = _requireNamePrompt.asStateFlow()

    fun dismissNamePrompt() {
        _requireNamePrompt.value = false
    }

    private val _isImportingModel = MutableStateFlow(false)
    val isImportingModel: StateFlow<Boolean> = _isImportingModel.asStateFlow()

    private val _modelDownloadProgress = MutableStateFlow(ModelDownloadProgress())
    val modelDownloadProgress: StateFlow<ModelDownloadProgress> = _modelDownloadProgress.asStateFlow()

    private var downloadJob: Job? = null

    private val _reminderAudio = MutableStateFlow<SelectedAudio?>(null)
    val reminderAudio: StateFlow<SelectedAudio?> = _reminderAudio.asStateFlow()

    private val _confirmationAudio = MutableStateFlow<SelectedAudio?>(null)
    val confirmationAudio: StateFlow<SelectedAudio?> = _confirmationAudio.asStateFlow()

    private val _previewPlayingUri = MutableStateFlow<String?>(null)
    val previewPlayingUri: StateFlow<String?> = _previewPlayingUri.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<DiagnosticItem>>(emptyList())
    val diagnostics: StateFlow<List<DiagnosticItem>> = _diagnostics.asStateFlow()

    // --- Settings & Stats States ---
    private val _cycleIntervalMinutes = MutableStateFlow(audioPrefs.cycleIntervalMinutes)
    val cycleIntervalMinutes: StateFlow<Int> = _cycleIntervalMinutes.asStateFlow()

    private val _listeningWindowSeconds = MutableStateFlow(audioPrefs.listeningWindowSeconds)
    val listeningWindowSeconds: StateFlow<Int> = _listeningWindowSeconds.asStateFlow()

    private val _autoStopMinutes = MutableStateFlow(audioPrefs.autoStopMinutes)
    val autoStopMinutes: StateFlow<Int> = _autoStopMinutes.asStateFlow()

    private val _muteMicBeep = MutableStateFlow(audioPrefs.muteMicBeep)
    val muteMicBeep: StateFlow<Boolean> = _muteMicBeep.asStateFlow()

    private val _vibrateOnRecognition = MutableStateFlow(audioPrefs.vibrateOnRecognition)
    val vibrateOnRecognition: StateFlow<Boolean> = _vibrateOnRecognition.asStateFlow()

    private val _extraKeywords = MutableStateFlow(audioPrefs.getExtraKeywords())
    val extraKeywords: StateFlow<Set<String>> = _extraKeywords.asStateFlow()

    private val _todayStats = MutableStateFlow(audioPrefs.getTodayRecognitions())
    val todayStats: StateFlow<Int> = _todayStats.asStateFlow()

    private val _totalStats = MutableStateFlow(audioPrefs.getTotalRecognitions())
    val totalStats: StateFlow<Int> = _totalStats.asStateFlow()

    val serviceStatus: StateFlow<VoiceReminderStatus> = VoiceReminderService.statusFlow

    // --- Naam Jap Counter State ---
    private val initialJapNames = audioPrefs.getJapNames()
    private val _japState = MutableStateFlow(
        NaamJapState(
            currentBead = audioPrefs.japCurrentBead,
            completedMalas = audioPrefs.japCompletedMalas,
            targetMalaGoal = audioPrefs.japTargetMalaGoal,
            lifetimeChants = audioPrefs.japLifetimeChants,
            vibrateOnChant = audioPrefs.japVibrateOnChant,
            names = initialJapNames,
            targetName = initialJapNames.joinToString(" / "),
            statusText = if (initialJapNames.isEmpty()) "Add your holy name to begin" else "Ready to chant ${initialJapNames.joinToString(" / ")}"
        )
    )
    val japState: StateFlow<NaamJapState> = _japState.asStateFlow()

    private var previewMediaPlayer: MediaPlayer? = null

    init {
        loadAudios()
        refreshDiagnostics()
        refreshStats()
        observeJapService()
    }

    private fun observeJapService() {
        viewModelScope.launch {
            VoiceReminderService.isJapServiceActiveFlow.collect { active ->
                _japState.update {
                    it.copy(
                        isVoiceActive = active,
                        isBackgroundActive = active,
                        statusText = if (active) {
                            "Voice chanting active (Screen-off supported)"
                        } else if (it.targetName.isBlank()) {
                            "Add your holy name to begin"
                        } else {
                            "Ready to chant ${it.targetName}"
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            VoiceReminderService.japChantEventFlow.collect { (word, count) ->
                triggerJapAnimation(word)
                _japState.update {
                    it.copy(
                        currentBead = audioPrefs.japCurrentBead,
                        completedMalas = audioPrefs.japCompletedMalas,
                        lifetimeChants = audioPrefs.japLifetimeChants,
                        lastChantedWord = word,
                        statusText = "Chanted: $word (+$count)"
                    )
                }
            }
        }
    }

    fun loadAudios() {
        _reminderAudio.value = audioPrefs.getReminderAudio()
        _confirmationAudio.value = audioPrefs.getConfirmationAudio()
    }

    fun setReminderAudio(uri: Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                audioPrefs.saveReminderAudio(uri)
            }
            _reminderAudio.value = saved
        }
    }

    fun setConfirmationAudio(uri: Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                audioPrefs.saveConfirmationAudio(uri)
            }
            _confirmationAudio.value = saved
        }
    }

    fun setCycleInterval(minutes: Int) {
        audioPrefs.cycleIntervalMinutes = minutes
        _cycleIntervalMinutes.value = minutes
    }

    fun skipWaitAndRemindNow(context: Context) {
        val intent = Intent(context, VoiceReminderService::class.java).apply {
            action = VoiceReminderService.ACTION_REMIND_NOW
        }
        context.startService(intent)
    }

    fun setListeningWindow(seconds: Int) {
        audioPrefs.listeningWindowSeconds = seconds
        _listeningWindowSeconds.value = seconds
    }

    fun setAutoStop(minutes: Int) {
        audioPrefs.autoStopMinutes = minutes
        _autoStopMinutes.value = minutes
    }

    fun setMuteMicBeep(enabled: Boolean) {
        audioPrefs.muteMicBeep = enabled
        _muteMicBeep.value = enabled
    }

    fun setVibrateOnRecognition(enabled: Boolean) {
        audioPrefs.vibrateOnRecognition = enabled
        _vibrateOnRecognition.value = enabled
    }

    fun addExtraKeyword(keyword: String) {
        audioPrefs.addExtraKeyword(keyword)
        _extraKeywords.value = audioPrefs.getExtraKeywords()
    }

    fun addKeyword(keyword: String) = addExtraKeyword(keyword)

    fun removeExtraKeyword(keyword: String) {
        audioPrefs.removeExtraKeyword(keyword)
        _extraKeywords.value = audioPrefs.getExtraKeywords()
    }

    fun removeKeyword(keyword: String) = removeExtraKeyword(keyword)

    fun refreshStats() {
        _todayStats.value = audioPrefs.getTodayRecognitions()
        _totalStats.value = audioPrefs.getTotalRecognitions()
    }

    fun resetStats() {
        audioPrefs.resetStats()
        _todayStats.value = 0
        _totalStats.value = 0
    }

    fun clearError() {
        VoiceReminderService.clearErrorState()
    }

    fun importVoskModelZip(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isImportingModel.value = true
            val success = withContext(Dispatchers.IO) {
                voskEngine.unpackModelZip(uri)
            }
            _isImportingModel.value = false
            _isVoskReady.value = voskEngine.isEngineAvailable()
            refreshDiagnostics()
            onResult(success)
        }
    }

    fun removeVoskModel() {
        cancelModelDownload()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                voskEngine.getBaseDirectory().deleteRecursively()
            }
            _isVoskReady.value = voskEngine.isEngineAvailable()
            refreshDiagnostics()
        }
    }

    fun startInAppModelDownload(onComplete: (Boolean) -> Unit = {}) {
        if (_modelDownloadProgress.value.isDownloading) return

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _modelDownloadProgress.value = ModelDownloadProgress(
                isDownloading = true,
                statusText = "Connecting to download server..."
            )

            val success = withContext(Dispatchers.IO) {
                voskEngine.downloadAndInstallModel { progress, downloadedMb, totalMb, status ->
                    _modelDownloadProgress.value = ModelDownloadProgress(
                        isDownloading = true,
                        progress = progress,
                        downloadedMb = downloadedMb,
                        totalMb = totalMb,
                        statusText = status
                    )
                }
            }

            _isVoskReady.value = voskEngine.isEngineAvailable()
            refreshDiagnostics()

            if (success) {
                _modelDownloadProgress.value = ModelDownloadProgress(
                    isDownloading = false,
                    progress = 1f,
                    statusText = "Vosk Hindi model installed successfully!"
                )
            } else {
                _modelDownloadProgress.value = ModelDownloadProgress(
                    isDownloading = false,
                    progress = 0f,
                    error = "Download or extraction failed. Please check internet connection."
                )
            }
            onComplete(success)
        }
    }

    fun cancelModelDownload() {
        voskEngine.cancelDownload()
        downloadJob?.cancel()
        downloadJob = null
        _modelDownloadProgress.value = ModelDownloadProgress(
            isDownloading = false,
            statusText = "Download cancelled"
        )
    }

    fun dismissModelPrompt() {
        _requireModelPrompt.value = false
    }

    fun startService(context: Context) {
        // Double-start protection in ViewModel
        if (serviceStatus.value.isServiceRunning) {
            Log.w("VoiceReminderVM", "Service already running, ignoring start request.")
            return
        }

        // No default names: the user must add at least one name to listen for
        if (NameMatcher.cleanNames(audioPrefs.getExtraKeywords()).isEmpty()) {
            _requireNamePrompt.value = true
            return
        }

        // Primary Engine Requirement: Offline Vosk model is required for beep-free continuous listening
        if (!voskEngine.isEngineAvailable()) {
            _requireModelPrompt.value = true
            return
        }

        stopPreview()
        val intent = Intent(context, VoiceReminderService::class.java).apply {
            action = VoiceReminderService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopService(context: Context) {
        val intent = Intent(context, VoiceReminderService::class.java).apply {
            action = VoiceReminderService.ACTION_STOP
        }
        context.startService(intent)
        refreshStats()
    }

    fun togglePreview(context: Context, audio: SelectedAudio) {
        if (_previewPlayingUri.value == audio.uriString) {
            stopPreview()
        } else {
            startPreview(context, audio)
        }
    }

    private fun startPreview(context: Context, audio: SelectedAudio) {
        stopPreview()
        try {
            previewMediaPlayer = MediaPlayer().apply {
                setDataSource(context, audio.uri)
                setOnPreparedListener {
                    it.start()
                    _previewPlayingUri.value = audio.uriString
                }
                setOnCompletionListener {
                    stopPreview()
                }
                setOnErrorListener { _, _, _ ->
                    stopPreview()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("VoiceReminderVM", "Error starting preview: ${e.message}")
            stopPreview()
        }
    }

    fun stopPreview() {
        try {
            previewMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            Log.w("VoiceReminderVM", "Error stopping preview: ${e.message}")
        } finally {
            previewMediaPlayer = null
            _previewPlayingUri.value = null
        }
    }

    // --- Naam Jap Counter Methods ---

    private fun publishJapNames(status: String? = null) {
        val names = audioPrefs.getJapNames()
        val title = names.joinToString(" / ")
        _japState.update {
            it.copy(
                names = names,
                targetName = title,
                statusText = status ?: if (names.isEmpty()) "Add your holy name to begin" else "Ready to chant $title"
            )
        }
    }

    /** Adds a name the user wants counted. There are no default names. */
    fun addJapName(name: String) {
        if (name.isBlank()) return
        if (audioPrefs.addJapName(name)) {
            publishJapNames()
        } else {
            publishJapNames("Name too short or already added")
        }
    }

    fun removeJapName(name: String) {
        audioPrefs.removeJapName(name)
        publishJapNames()
    }

    fun showJapNameRequired() {
        _japState.update { it.copy(statusText = "Please add your holy name first") }
    }

    fun startVoiceJap(context: Context) {
        if (audioPrefs.getJapNames().isEmpty()) {
            showJapNameRequired()
            return
        }
        // Exact whole-name counting needs the offline Hindi model (Google cannot tell "रा" from "राधा")
        if (!voskEngine.isEngineAvailable()) {
            _requireModelPrompt.value = true
            return
        }

        // If reminder cycle is active, stop it so mic is dedicated to Jap meditation
        if (serviceStatus.value.isServiceRunning) {
            stopService(context)
        }

        val serviceIntent = Intent(context, VoiceReminderService::class.java).apply {
            action = VoiceReminderService.ACTION_START_JAP
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun stopVoiceJap(context: Context) {
        val serviceIntent = Intent(context, VoiceReminderService::class.java).apply {
            action = VoiceReminderService.ACTION_STOP_JAP
        }
        context.startService(serviceIntent)
        _japState.update {
            it.copy(
                isVoiceActive = false,
                isBackgroundActive = false,
                statusText = "Voice chanting paused"
            )
        }
    }

    private fun triggerJapAnimation(word: String) {
        viewModelScope.launch {
            _japState.update { it.copy(isIncrementAnimating = true) }
            delay(280)
            _japState.update { it.copy(isIncrementAnimating = false) }
        }
    }

    fun resetCurrentMala() {
        audioPrefs.resetCurrentMala()
        _japState.update { it.copy(currentBead = 0) }
    }

    fun resetAllJap() {
        audioPrefs.resetAllJap()
        _japState.update {
            it.copy(
                currentBead = 0,
                completedMalas = 0,
                lifetimeChants = 0L,
                lastChantedWord = ""
            )
        }
    }

    fun setTargetMalaGoal(goal: Int) {
        audioPrefs.japTargetMalaGoal = goal
        _japState.update { it.copy(targetMalaGoal = goal) }
    }

    fun setJapVibrate(enabled: Boolean) {
        audioPrefs.japVibrateOnChant = enabled
        _japState.update { it.copy(vibrateOnChant = enabled) }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                diagnosticsSuite.runAllDiagnostics()
            }
            _diagnostics.value = items
        }
    }

    override fun onCleared() {
        stopPreview()
        super.onCleared()
    }
}
