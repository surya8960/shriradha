package com.example

enum class ReminderPhase {
    IDLE,
    PLAYING_REMINDER,
    LISTENING_SILENT,
    RADHA_RECOGNIZED,
    PLAYING_CONFIRMATION,
    WAITING_NEXT_INTERVAL,
    CYCLE_TIMEOUT,
    ERROR
}

data class VoiceReminderStatus(
    val phase: ReminderPhase = ReminderPhase.IDLE,
    val cycleCount: Int = 0,
    val recognizedCount: Int = 0,
    val secondsRemaining: Int = 15,
    val totalListeningWindowSeconds: Int = 15,
    val nextCycleSecondsRemaining: Int = 0,
    val totalIntervalSeconds: Int = 0,
    val currentAudioTitle: String = "",
    val detectedText: String = "",
    val errorMessage: String = "",
    val isServiceRunning: Boolean = false,
    val speechRmsDb: Float = 0f,
    val autoStopSecondsRemaining: Long = 0L,
    val lastStateTimestamp: Long = System.currentTimeMillis()
) {
    val phaseDisplayName: String
        get() = when (phase) {
            ReminderPhase.IDLE -> "Stopped (Idle)"
            ReminderPhase.PLAYING_REMINDER -> "Playing Reminder"
            ReminderPhase.LISTENING_SILENT -> "Listening Silently ($secondsRemaining s)"
            ReminderPhase.RADHA_RECOGNIZED -> "Radha Recognized!"
            ReminderPhase.PLAYING_CONFIRMATION -> "Playing Confirmation"
            ReminderPhase.WAITING_NEXT_INTERVAL -> {
                val m = nextCycleSecondsRemaining / 60
                val s = nextCycleSecondsRemaining % 60
                "Next Reminder in $m:${String.format("%02d", s)}"
            }
            ReminderPhase.CYCLE_TIMEOUT -> "Restarting Cycle"
            ReminderPhase.ERROR -> "Error Occurred"
        }
}
