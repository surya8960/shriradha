package com.example

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SelectedAudio(
    val uriString: String,
    val displayName: String,
    val durationMs: Long
) {
    val uri: Uri
        get() = Uri.parse(uriString)

    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}

class AudioPreferenceManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveReminderAudio(uri: Uri): SelectedAudio? {
        return persistAndSave(uri, KEY_REMINDER_URI, KEY_REMINDER_NAME, KEY_REMINDER_DURATION)
    }

    fun saveConfirmationAudio(uri: Uri): SelectedAudio? {
        return persistAndSave(uri, KEY_CONFIRM_URI, KEY_CONFIRM_NAME, KEY_CONFIRM_DURATION)
    }

    fun getReminderAudio(): SelectedAudio? {
        return loadAudio(KEY_REMINDER_URI, KEY_REMINDER_NAME, KEY_REMINDER_DURATION)
    }

    fun getConfirmationAudio(): SelectedAudio? {
        return loadAudio(KEY_CONFIRM_URI, KEY_CONFIRM_NAME, KEY_CONFIRM_DURATION)
    }

    // --- Settings ---

    var cycleIntervalMinutes: Int
        get() = prefs.getInt(KEY_CYCLE_INTERVAL_MINUTES, 4)
        set(value) = prefs.edit().putInt(KEY_CYCLE_INTERVAL_MINUTES, value).apply()

    var listeningWindowSeconds: Int
        get() = prefs.getInt(KEY_LISTENING_WINDOW, 15)
        set(value) = prefs.edit().putInt(KEY_LISTENING_WINDOW, value).apply()

    var autoStopMinutes: Int
        get() = prefs.getInt(KEY_AUTO_STOP_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_STOP_MINUTES, value).apply()

    var muteMicBeep: Boolean
        get() = prefs.getBoolean(KEY_MUTE_BEEP, true)
        set(value) = prefs.edit().putBoolean(KEY_MUTE_BEEP, value).apply()

    var vibrateOnRecognition: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    /** Names the Reminder listens for. Empty by default: there are NO built-in names. */
    fun getExtraKeywords(): Set<String> {
        return prefs.getStringSet(KEY_EXTRA_KEYWORDS, emptySet()) ?: emptySet()
    }

    fun addExtraKeyword(keyword: String) {
        val updated = NameMatcher.cleanNames(getExtraKeywords() + keyword).toSet()
        prefs.edit().putStringSet(KEY_EXTRA_KEYWORDS, updated).apply()
    }

    fun removeExtraKeyword(keyword: String) {
        val current = getExtraKeywords().toMutableSet()
        current.remove(keyword.trim())
        prefs.edit().putStringSet(KEY_EXTRA_KEYWORDS, current).apply()
    }

    // --- Recognition Statistics ---

    fun incrementRecognitionStats() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastDate = prefs.getString(KEY_LAST_STAT_DATE, "")

        val currentToday = if (todayStr == lastDate) {
            prefs.getInt(KEY_TODAY_STATS, 0) + 1
        } else {
            1
        }
        val currentTotal = prefs.getInt(KEY_TOTAL_STATS, 0) + 1

        prefs.edit()
            .putString(KEY_LAST_STAT_DATE, todayStr)
            .putInt(KEY_TODAY_STATS, currentToday)
            .putInt(KEY_TOTAL_STATS, currentTotal)
            .apply()
    }

    fun getTodayRecognitions(): Int {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastDate = prefs.getString(KEY_LAST_STAT_DATE, "")
        return if (todayStr == lastDate) {
            prefs.getInt(KEY_TODAY_STATS, 0)
        } else {
            0
        }
    }

    fun getTotalRecognitions(): Int {
        return prefs.getInt(KEY_TOTAL_STATS, 0)
    }

    fun resetStats() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        prefs.edit()
            .putString(KEY_LAST_STAT_DATE, todayStr)
            .putInt(KEY_TODAY_STATS, 0)
            .putInt(KEY_TOTAL_STATS, 0)
            .apply()
    }

    // --- Naam Jap Counter Persistence ---

    /** Names counted by the Jap Counter. Empty by default: there are NO built-in names. */
    fun getJapNames(): List<String> {
        val stored = prefs.getStringSet(KEY_JAP_NAMES, null)
        if (stored != null) return NameMatcher.cleanNames(stored.sorted())
        // One-time migration from the old single "target name" field
        val legacy = prefs.getString(KEY_JAP_TARGET_NAME, "")?.trim().orEmpty()
        return NameMatcher.cleanNames(if (legacy.isEmpty()) emptyList() else listOf(legacy))
    }

    private fun saveJapNames(names: Collection<String>) {
        prefs.edit()
            .putStringSet(KEY_JAP_NAMES, NameMatcher.cleanNames(names).toSet())
            .remove(KEY_JAP_TARGET_NAME)
            .apply()
    }

    /** Returns false if the name is too short / already added. */
    fun addJapName(name: String): Boolean {
        val current = getJapNames()
        val updated = NameMatcher.cleanNames(current + name)
        if (updated.size == current.size) return false
        saveJapNames(updated)
        return true
    }

    fun removeJapName(name: String) {
        val key = NameMatcher.tokens(name)
        saveJapNames(getJapNames().filter { NameMatcher.tokens(it) != key })
    }

    var japCurrentBead: Int
        get() = prefs.getInt(KEY_JAP_CURRENT_BEAD, 0)
        set(value) = prefs.edit().putInt(KEY_JAP_CURRENT_BEAD, value.coerceIn(0, 107)).apply()

    var japCompletedMalas: Int
        get() = prefs.getInt(KEY_JAP_COMPLETED_MALAS, 0)
        set(value) = prefs.edit().putInt(KEY_JAP_COMPLETED_MALAS, value.coerceAtLeast(0)).apply()

    var japTargetMalaGoal: Int
        get() = prefs.getInt(KEY_JAP_TARGET_MALA_GOAL, 16)
        set(value) = prefs.edit().putInt(KEY_JAP_TARGET_MALA_GOAL, value.coerceAtLeast(1)).apply()

    var japLifetimeChants: Long
        get() = prefs.getLong(KEY_JAP_LIFETIME_CHANTS, 0L)
        set(value) = prefs.edit().putLong(KEY_JAP_LIFETIME_CHANTS, value.coerceAtLeast(0L)).apply()

    var japVibrateOnChant: Boolean
        get() = prefs.getBoolean(KEY_JAP_VIBRATE, false)
        set(value) = prefs.edit().putBoolean(KEY_JAP_VIBRATE, value).apply()

    /**
     * Increments the Jap bead count by [amount].
     * If the bead reaches 108, the completed malas count increments,
     * and bead resets to 0.
     * Returns a Pair(newBead, newCompletedMalas).
     */
    fun incrementJap(amount: Int = 1): Pair<Int, Int> {
        if (amount <= 0) return Pair(japCurrentBead, japCompletedMalas)

        var bead = japCurrentBead + amount
        var malas = japCompletedMalas
        val lifetime = japLifetimeChants + amount

        while (bead >= 108) {
            bead -= 108
            malas++
        }

        prefs.edit()
            .putInt(KEY_JAP_CURRENT_BEAD, bead)
            .putInt(KEY_JAP_COMPLETED_MALAS, malas)
            .putLong(KEY_JAP_LIFETIME_CHANTS, lifetime)
            .apply()

        return Pair(bead, malas)
    }

    fun resetCurrentMala() {
        prefs.edit().putInt(KEY_JAP_CURRENT_BEAD, 0).apply()
    }

    fun resetAllJap() {
        prefs.edit()
            .putInt(KEY_JAP_CURRENT_BEAD, 0)
            .putInt(KEY_JAP_COMPLETED_MALAS, 0)
            .putLong(KEY_JAP_LIFETIME_CHANTS, 0L)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun persistAndSave(
        uri: Uri,
        uriKey: String,
        nameKey: String,
        durationKey: String
    ): SelectedAudio? {
        try {
            // Take persistable permission if available from SAF
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: SecurityException) {
                Log.w(TAG, "Persistable permission could not be taken: ${e.message}")
            }

            val displayName = queryFileName(uri) ?: "audio_${System.currentTimeMillis()}.mp3"
            val durationMs = queryDuration(uri)

            prefs.edit()
                .putString(uriKey, uri.toString())
                .putString(nameKey, displayName)
                .putLong(durationKey, durationMs)
                .apply()

            return SelectedAudio(uri.toString(), displayName, durationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist audio: ${e.message}", e)
            return null
        }
    }

    private fun loadAudio(uriKey: String, nameKey: String, durationKey: String): SelectedAudio? {
        val uriStr = prefs.getString(uriKey, null) ?: return null
        val name = prefs.getString(nameKey, "Audio File") ?: "Audio File"
        val duration = prefs.getLong(durationKey, 0L)
        return SelectedAudio(uriStr, name, duration)
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        return cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying file name: ${e.message}")
            }
        }
        return uri.lastPathSegment
    }

    private fun queryDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract duration: ${e.message}")
            0L
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    companion object {
        private const val TAG = "AudioPreferenceManager"
        private const val PREFS_NAME = "radha_reminder_audio_prefs"
        private const val KEY_REMINDER_URI = "reminder_uri"
        private const val KEY_REMINDER_NAME = "reminder_name"
        private const val KEY_REMINDER_DURATION = "reminder_duration"

        private const val KEY_CONFIRM_URI = "confirm_uri"
        private const val KEY_CONFIRM_NAME = "confirm_name"
        private const val KEY_CONFIRM_DURATION = "confirm_duration"

        private const val KEY_CYCLE_INTERVAL_MINUTES = "key_cycle_interval_minutes"
        private const val KEY_LISTENING_WINDOW = "key_listening_window"
        private const val KEY_AUTO_STOP_MINUTES = "key_auto_stop_minutes"
        private const val KEY_MUTE_BEEP = "key_mute_beep"
        private const val KEY_VIBRATE = "key_vibrate"
        private const val KEY_EXTRA_KEYWORDS = "key_extra_keywords"

        private const val KEY_LAST_STAT_DATE = "key_last_stat_date"
        private const val KEY_TODAY_STATS = "key_today_stats"
        private const val KEY_TOTAL_STATS = "key_total_stats"

        private const val KEY_JAP_TARGET_NAME = "key_jap_target_name"
        private const val KEY_JAP_NAMES = "key_jap_names"
        private const val KEY_JAP_CURRENT_BEAD = "key_jap_current_bead"
        private const val KEY_JAP_COMPLETED_MALAS = "key_jap_completed_malas"
        private const val KEY_JAP_TARGET_MALA_GOAL = "key_jap_target_mala_goal"
        private const val KEY_JAP_LIFETIME_CHANTS = "key_jap_lifetime_chants"
        private const val KEY_JAP_VIBRATE = "key_jap_vibrate"
    }
}
