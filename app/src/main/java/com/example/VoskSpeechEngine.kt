package com.example

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

class VoskSpeechEngine private constructor(private val context: Context) {

    @Volatile
    private var model: Model? = null

    @Volatile
    private var speechService: SpeechService? = null

    @Volatile
    private var isModelLoading = false

    @Volatile
    private var isModelLoaded = false

    private val cancelDownloadFlag = AtomicBoolean(false)
    private val modelLock = Any()

    fun isEngineAvailable(): Boolean {
        return getResolvedModelDirectory() != null
    }

    fun getBaseDirectory(): File {
        return File(context.filesDir, "vosk-model")
    }

    fun getResolvedModelDirectory(): File? {
        val base = getBaseDirectory()
        if (!base.exists() || !base.isDirectory) return null
        if (isValidModelFolder(base)) return base

        // Check if zip extracted with a root subfolder (e.g. vosk-model-small-hi-0.22/)
        val children = base.listFiles() ?: return null
        for (child in children) {
            if (child.isDirectory && isValidModelFolder(child)) {
                return child
            }
        }
        return null
    }

    /**
     * Strict check requiring both acoustic files and configuration/vocabulary.
     */
    private fun isValidModelFolder(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val hasAcoustic = File(dir, "am").exists() ||
                File(dir, "model").exists() ||
                File(dir, "final.mdl").exists() ||
                File(dir, "am/final.mdl").exists()
        val hasConfigOrVocab = File(dir, "conf").exists() ||
                File(dir, "model.conf").exists() ||
                File(dir, "words.txt").exists() ||
                File(dir, "graph").exists()
        return hasAcoustic && hasConfigOrVocab
    }

    /**
     * Preloads model in background thread with thread-safe synchronized guard.
     */
    fun preloadModelAsync(onComplete: (Boolean) -> Unit = {}) {
        if (isModelLoaded && model != null) {
            onComplete(true)
            return
        }
        if (isModelLoading) return

        val dir = getResolvedModelDirectory()
        if (dir == null) {
            Log.d(TAG, "No valid Vosk offline model found on device.")
            onComplete(false)
            return
        }

        isModelLoading = true
        Thread {
            synchronized(modelLock) {
                if (isModelLoaded && model != null) {
                    isModelLoading = false
                    onComplete(true)
                    return@synchronized
                }
                try {
                    Log.d(TAG, "Pre-loading Vosk model from ${dir.absolutePath}...")
                    model = Model(dir.absolutePath)
                    isModelLoaded = true
                    isModelLoading = false
                    Log.i(TAG, "Vosk model successfully loaded into memory.")
                    onComplete(true)
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to load Vosk model: ${t.message}", t)
                    model = null
                    isModelLoaded = false
                    isModelLoading = false
                    onComplete(false)
                }
            }
        }.start()
    }

    /**
     * Starts continuous listening directly with Vosk AudioRecord.
     * Google's recognizer is NOT used, completely eliminating Google mic opening sound.
     * Catches Throwable to protect against native linkage issues.
     */
    fun startListening(
        names: Collection<String>,
        onResult: (String) -> Unit,
        onPartialHypothesis: ((String) -> Unit)? = null,
        onErrorCallback: (String) -> Unit
    ): Boolean {
        val currentModel = synchronized(modelLock) {
            model ?: run {
                val dir = getResolvedModelDirectory() ?: return false
                try {
                    Model(dir.absolutePath).also {
                        model = it
                        isModelLoaded = true
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Native error instantiating Vosk model: ${t.message}", t)
                    return false
                }
            }
        }

        return try {
            stopListening()

            // Grammar contains ONLY the user's names (+ [unk]) - no built-in names.
            val grammarJson = NameMatcher.buildVoskGrammarJson(names)
            if (NameMatcher.voskNames(names).isEmpty()) {
                // Vosk Hindi model only knows Devanagari words: nothing it could recognize.
                onErrorCallback("No Devanagari name for offline recognition")
                return false
            }

            val recognizer = Recognizer(currentModel, 16000.0f, grammarJson)
            recognizer.setWords(true) // word-level confidence + timing used to reject noise/half words
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    hypothesis?.let {
                        val text = parseHypothesisText(it, isPartial = true)
                        if (text.isNotEmpty() && text != "[unk]") {
                            onPartialHypothesis?.invoke(text)
                        }
                    }
                }

                override fun onResult(hypothesis: String?) {
                    hypothesis?.let {
                        val text = parseFinalText(it)
                        if (text.isNotEmpty() && text != NameMatcher.UNKNOWN_TOKEN) {
                            onResult(text)
                        }
                    }
                }

                override fun onFinalResult(hypothesis: String?) {
                    hypothesis?.let {
                        val text = parseFinalText(it)
                        if (text.isNotEmpty() && text != NameMatcher.UNKNOWN_TOKEN) {
                            onResult(text)
                        }
                    }
                }

                override fun onError(exception: Exception?) {
                    Log.w(TAG, "Vosk listener note: ${exception?.message}")
                    onErrorCallback(exception?.message ?: "Vosk speech error")
                }

                override fun onTimeout() {
                    Log.d(TAG, "Vosk onTimeout")
                }
            })
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Native error starting Vosk listening: ${t.message}", t)
            false
        }
    }

    fun stopListening() {
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping Vosk speech service: ${t.message}")
        } finally {
            speechService = null
        }
    }

    fun closeModel() {
        synchronized(modelLock) {
            stopListening()
            try {
                model?.close()
            } catch (t: Throwable) {
                Log.w(TAG, "Error closing Vosk model: ${t.message}")
            } finally {
                model = null
                isModelLoaded = false
                isModelLoading = false
            }
        }
    }

    /**
     * Extracts a user-provided Vosk model zip file into a temporary staging directory,
     * validates strict model integrity, and atomically swaps it into filesDir/vosk-model.
     */
    fun unpackModelZip(zipUri: Uri): Boolean {
        val dest = getBaseDirectory()
        val staging = File(context.cacheDir, "vosk_model_staging_${System.currentTimeMillis()}")

        return try {
            if (staging.exists()) staging.deleteRecursively()
            staging.mkdirs()

            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val newFile = File(staging, entry.name)
                        // Guard against zip slip vulnerabilities
                        if (!newFile.canonicalPath.startsWith(staging.canonicalPath + File.separator) &&
                            newFile.canonicalPath != staging.canonicalPath) {
                            throw SecurityException("Zip entry is outside target destination: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // Verify staging contents
            val validStagingDir = if (isValidModelFolder(staging)) {
                staging
            } else {
                staging.listFiles()?.firstOrNull { it.isDirectory && isValidModelFolder(it) }
            }

            if (validStagingDir == null) {
                Log.e(TAG, "Extracted zip does not contain valid Vosk model files.")
                staging.deleteRecursively()
                return false
            }

            // Atomically replace destination
            closeModel()
            if (dest.exists()) dest.deleteRecursively()
            dest.mkdirs()

            validStagingDir.copyRecursively(dest, overwrite = true)
            staging.deleteRecursively()
            Log.i(TAG, "Model zip unpacked and verified successfully!")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to unpack model zip: ${t.message}", t)
            staging.deleteRecursively()
            false
        }
    }

    /**
     * Cancels any ongoing model download.
     */
    fun cancelDownload() {
        cancelDownloadFlag.set(true)
    }

    /**
     * Downloads the Vosk Hindi model directly in-app from Alphacephei into temporary storage,
     * validates extracted files strictly, and atomically installs into filesDir/vosk-model.
     */
    fun downloadAndInstallModel(
        modelUrl: String = DEFAULT_HINDI_MODEL_URL,
        onProgress: (progress: Float, downloadedMb: Float, totalMb: Float, status: String) -> Unit
    ): Boolean {
        cancelDownloadFlag.set(false)
        val dest = getBaseDirectory()
        val tempFile = File(context.cacheDir, "vosk_model_temp_${System.currentTimeMillis()}.zip")
        val staging = File(context.cacheDir, "vosk_model_staging_${System.currentTimeMillis()}")

        var connection: HttpURLConnection? = null
        try {
            onProgress(0f, 0f, 0f, "Connecting to download server...")
            val url = URL(modelUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "RadhaReminder-Android/1.0")
            }
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP $responseCode: ${connection.responseMessage}")
                return false
            }

            val totalBytes = connection.contentLength.toLong().let { if (it > 0) it else 44458845L }
            val totalMb = totalBytes.toFloat() / (1024 * 1024)

            if (tempFile.exists()) tempFile.delete()

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var totalRead: Long = 0
                    var lastUpdateMs = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (cancelDownloadFlag.get()) {
                            Log.i(TAG, "Download cancelled by user.")
                            tempFile.delete()
                            onProgress(0f, 0f, 0f, "Download cancelled")
                            return false
                        }

                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateMs >= 150 || totalRead == totalBytes) {
                            lastUpdateMs = now
                            val progress = (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                            val downloadedMb = totalRead.toFloat() / (1024 * 1024)
                            onProgress(
                                progress,
                                downloadedMb,
                                totalMb,
                                String.format("Downloading model: %.1f / %.1f MB (%d%%)", downloadedMb, totalMb, (progress * 100).toInt())
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (cancelDownloadFlag.get()) {
                tempFile.delete()
                return false
            }

            onProgress(1f, totalMb, totalMb, "Verifying and extracting Hindi model...")

            if (staging.exists()) staging.deleteRecursively()
            staging.mkdirs()

            FileInputStream(tempFile).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (cancelDownloadFlag.get()) {
                            staging.deleteRecursively()
                            tempFile.delete()
                            return false
                        }
                        val newFile = File(staging, entry.name)
                        if (!newFile.canonicalPath.startsWith(staging.canonicalPath + File.separator) &&
                            newFile.canonicalPath != staging.canonicalPath) {
                            throw SecurityException("Zip entry is outside target destination: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            tempFile.delete()

            // Verify staging contents
            val validStagingDir = if (isValidModelFolder(staging)) {
                staging
            } else {
                staging.listFiles()?.firstOrNull { it.isDirectory && isValidModelFolder(it) }
            }

            if (validStagingDir == null) {
                Log.e(TAG, "Downloaded model archive is invalid or missing required model files.")
                staging.deleteRecursively()
                return false
            }

            // Atomically swap
            closeModel()
            if (dest.exists()) dest.deleteRecursively()
            dest.mkdirs()

            validStagingDir.copyRecursively(dest, overwrite = true)
            staging.deleteRecursively()

            Log.i(TAG, "Vosk Hindi model downloaded and verified successfully!")
            return isEngineAvailable()
        } catch (t: Throwable) {
            Log.e(TAG, "Error downloading and extracting model: ${t.message}", t)
            tempFile.delete()
            staging.deleteRecursively()
            return false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Final result text. Words the recognizer was unsure about (low confidence) or that were
     * far too short to be a real spoken word are replaced by [unk], so noise or half-spoken
     * names are never counted and never glue two words into a fake full name.
     */
    private fun parseFinalText(json: String): String {
        return try {
            val obj = JSONObject(json)
            val words = obj.optJSONArray("result")
            if (words == null || words.length() == 0) {
                return obj.optString("text", "").trim()
            }
            val parts = ArrayList<String>(words.length())
            for (i in 0 until words.length()) {
                val w = words.optJSONObject(i) ?: continue
                val word = w.optString("word", "").trim()
                if (word.isEmpty()) continue
                val conf = w.optDouble("conf", 1.0)
                val duration = w.optDouble("end", 0.0) - w.optDouble("start", 0.0)
                val hasTiming = w.has("start") && w.has("end")
                val weak = conf < MIN_WORD_CONFIDENCE ||
                    (hasTiming && duration < NameMatcher.minSpokenSeconds(word))
                parts.add(if (weak) NameMatcher.UNKNOWN_TOKEN else word)
            }
            parts.joinToString(" ")
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseHypothesisText(json: String, isPartial: Boolean): String {
        return try {
            val obj = JSONObject(json)
            if (isPartial) {
                obj.optString("partial", "").trim()
            } else {
                obj.optString("text", "").trim()
            }
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        private const val TAG = "VoskSpeechEngine"

        // Tuning knobs for "wrong / half name must not count" (raise to be stricter)
        private const val MIN_WORD_CONFIDENCE = 0.75
        const val DEFAULT_HINDI_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"

        @Volatile
        private var instance: VoskSpeechEngine? = null

        fun getInstance(context: Context): VoskSpeechEngine {
            return instance ?: synchronized(this) {
                instance ?: VoskSpeechEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
