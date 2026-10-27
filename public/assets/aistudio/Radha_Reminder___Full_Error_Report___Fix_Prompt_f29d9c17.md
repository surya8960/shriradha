# Radha Reminder — Full Error Report + Fix Prompt

Source: radha-reminder.zip (Kotlin + Compose, Vosk + Google SpeechRecognizer) · Review date: 26 Oct 2026 · Type: static code review (sandbox mein Android SDK/Maven nahi tha, isliye build run nahi hua)

## 1. Seedha verdict

UI aur flow acchi tarah likha hai, par 3 badi problems ki wajah se aapke main goals (beep nahi + 15s continuous mic) reliably kaam nahi karte:

1. **Default engine broken hai.** Vosk model optional hai, isliye by default Google recognizer chalta hai. Android 11+ par manifest mein `<queries>` nahi hai, to wo start hi nahi hota. Start ho bhi jaye to Google 15s continuous mic aur no-beep ki guarantee nahi deta.
2. **GitHub Actions build fail ho sakta hai** (gradlew missing, debug.keystore missing, JDK version).
3. **Reliability bugs:** error screen se turant gayab ho jata hai, screen-off mein timer/wake-lock toot-ta hai, alarm stream mute ho jati hai, stale Vosk events galat time par trigger hote hain.

Total **23 issues**: 3 Critical, 6 High, 7 Medium, 7 Low. Neeche table, phir copy-paste fix prompt.

## 2. Critical

| ID | Kahan | Problem | Fix |
| --- | --- | --- | --- |
| C1 | AndroidManifest.xml | Android 11+ par `<queries>` ke bina `SpeechRecognizer.isRecognitionAvailable()` false deta hai. Google fallback start hi nahi hota (VoiceReminderService.kt 398-403) aur Diagnostics galat 'Speech engine missing' dikhata hai (DiagnosticTestSuite.kt 104). UI par error nahi aata, bas timer chalta rehta hai: app 'sunta hai par sunta nahi'. | Manifest mein `<queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>` add karo. |
| C2 | VoiceReminderService.kt 321-339, 434-459 | Vosk model optional hai (manual download). Model na ho to Google recognizer use hota hai: wo silence-timeout extras aksar ignore karta hai, ERROR\_NO\_MATCH / SPEECH\_TIMEOUT par restart nahi hota (baaki window mein mic off), aur BUSY/CLIENT par restart hota hai (beep wapas). Yani aapki dono requirements sirf Vosk path mein poori ho sakti hain. | Vosk ko primary banao: first-run par model download, Start se pehle zaroori. Google sirf visible warning ke saath fallback. |
| C3 | Gradle / CI | (a) zip mein `gradlew`, `gradlew.bat`, `gradle-wrapper.jar` nahi hain, sirf wrapper.properties. (b) debug signingConfig `debug.keystore` maangta hai par wo `.gitignore` mein hai, to CI par 'Keystore file not found'. (c) AGP 9.1.1 / Gradle 9.3.1 ko JDK 17+ chahiye. Agar aapke repo/workflow mein ye pehle se hain to ignore karo. | Wrapper commit karo (ya workflow mein setup-gradle), debug signing conditional banao, workflow mein JDK 17. |

## 3. High

| ID | Kahan | Problem | Fix |
| --- | --- | --- | --- |
| H1 | Service 792-803, 891-896 | `handleError()` ERROR set karke `stopSelf()` karta hai, fir `onDestroy()` mein `stopReminderCycle(false)` phase ko IDLE aur errorMessage ko khaali kar deta hai. 'Mic permission missing' / 'audio missing' error UI mein kabhi dikhta nahi. | `onDestroy` ERROR state overwrite na kare. |
| H2 | Service 356-379 | `muteAudioCues()` **STREAM\_ALARM** bhi mute karta hai: listening ke time aapka alarm clock nahi bajega. Agar pehla mute chal jaye aur doosra SecurityException (DND access) de, to `wasSystemMuted` set nahi hota aur `unmute` kabhi nahi chalta: stream permanently mute reh sakti hai. | ALARM hatao, per-stream state track karo, exact wahi unmute karo jo mute kiya. Vosk active ho to mute skip. |
| H3 | Service 126-136, 342-347, 669-676 + MainActivity 1133-1143 | Wake lock sirf 5 min ka aur cycle start par hi refresh hota hai, jabki interval 30 min tak jata hai. Wait coroutine `delay` par hai (uptime clock, deep sleep mein ruk jata hai). Screen off par reminders late ya miss ho sakte hain. | Poore cycle ke liye wake lock (timeout = interval + window + 60s), countdown elapsedRealtime se, aur next cycle ke liye `AlarmManager.setExactAndAllowWhileIdle` safety. |
| H4 | Service 515-527 + VoskSpeechEngine 180-189 | Vosk `stop()` ek final result main thread par post karta hai. `processSpeechMatches` sirf `isRecognized` / `isServiceStopping` check karta hai, phase nahi. Purane session ka 'radha' agle wait/phase mein recognition + confirmation audio trigger kar sakta hai. | Session id + `phase == LISTENING_SILENT` guard. |
| H5 | VoskSpeechEngine 59-112 | Preload thread aur `startListening` dono alag `Model()` bana sakte hain (race, fields `@Volatile` nahi, purana native model leak). Model kabhi `close()` nahi hota. 3 alag engine instances (Service, ViewModel, Diagnostics). Fallback mein `Model()` main thread par load hota hai (jank/ANR). | Ek singleton engine, synchronized loading, `awaitModel()` background mein, close on replace/destroy. |
| H6 | VoskSpeechEngine (sab `catch`) | `catch (e: Exception)` native errors (`UnsatisfiedLinkError`, `NoClassDefFoundError` for JNA/libvosk) nahi pakadta: service crash. Vosk README JNA `@aar` explicitly add karne ko kehta hai, build.gradle mein sirf vosk-android hai. | `Throwable` pakdo; `net.java.dev.jna:jna:5.13.0@aar` add karo. |

## 4. Medium

| ID | Kahan | Problem | Fix |
| --- | --- | --- | --- |
| M1 | Service 289-295, Manifest allowBackup | Reminder audio fail ho to har 2s par infinite retry. Reinstall/backup-restore ke baad SharedPreferences ke SAF URI wapas aate hain par permission nahi, to yahi loop lagta hai. Backup rules khaali hain (42 MB model bhi backup quota maarega). | Max 3 retry, phir error. Start par URI readable check. `allowBackup=false` ya rules. |
| M2 | ViewModel 235-242, Vosk 236-336, 48-53 | Download 'Cancel' sirf UI badalta hai: blocking IO band nahi hota, model chupke install ho jata hai. Koi size/zip validation nahi, direct final folder mein extract. `isValidModelFolder` ka OR check adhoori/corrupt folder ko bhi valid maan leta hai. | Temp mein download, temp folder mein extract, strict validation (`am/final.mdl`, `conf/model.conf`, `graph/`), atomic rename, real cancel. |
| M3 | Vosk 194-230, 207, 307 | `unpackModelZip` extract se pehle chalta hua model delete kar deta hai (galat zip chuna to model gaya). Zip-slip check `startsWith(dest.canonicalPath)` bina separator ke (`vosk-model-evil` bypass). | Pehle temp mein validate, fir swap. Check `dest.canonicalPath + File.separator`. |
| M4 | Vosk 118-133 | Grammar JSON string concat se banta hai: keyword mein `"` ho to grammar toot jata hai aur silently Google fallback par chala jata hai. Grammar sirf keywords + `[unk]` hai, to noise par partial result mein 'radha' aa sakta hai (false trigger). Romanized words Hindi vocab mein nahi hote. | `JSONArray` use karo. Partial/final strict match, optional Sensitivity setting. |
| M5 | Service 330-332 | Vosk `onError` sirf log hota hai. Call aane ya doosri app mic le le to window ka baaki hissa bina mic ke guzarta hai. | Window mein 3s+ bache ho to ek retry, warna visible note. |
| M6 | RadhaMatcher 28-33, 40-45 | Latin extra keywords `contains` se match hote hain ('ram' 'program' mein match). `radhe-radhe` dead entry (split '-' par hota hai). Devanagari 'राधिका' missing. Vosk grammar aur Matcher ki keyword lists alag-alag. | Ek shared keyword list, Latin keywords whole-word. |
| M7 | Service 342-347, 669-676 | Har second notification + StateFlow update, 4-30 min wait ke dauran bhi. Battery/notification churn. | Wait mein 30s ya minute change par update; UI end-timestamp se khud tick kare. |

## 5. Low

| ID | Kahan | Problem | Fix |
| --- | --- | --- | --- |
| L1 | Service 85-93, 112-124 | `startForeground(TYPE_MICROPHONE)` mic permission check se pehle. Android 14+ par permission revoke ho to SecurityException. | try/catch + error state + `stopSelf()`. |
| L2 | Service 842 | Notification small icon colorful adaptive launcher foreground hai: status bar mein blob dikhta hai. | Monochrome `ic_stat_radha.xml`. |
| L3 | DiagnosticTestSuite, MainActivity | Settings se wapas aane par diagnostics refresh nahi hote. 'Offline Hindi PASSED' bina Hindi pack check ke. Main thread par file/network check. | ON\_RESUME refresh, Vosk model se tie karo, IO dispatcher. |
| L4 | MainActivity 140, 310, 480, themes.xml | Fixed 36dp/32dp spacers + `WindowInsets(0.dp)`: nav bar/cutout ke neeche content. Theme parent dark-based hai: light mode mein startup par dark flash. | systemBars/displayCutout insets, window background match. |
| L5 | app/build.gradle.kts, libs.versions.toml | Firebase AI/AppCheck, Room, Retrofit, Moshi, OkHttp, KSP, secrets, google-services: code mein 0 usage. APK bhaari, build ke failure points zyada. | Sab hatao. |
| L6 | README, metadata.json, .aistudio/, .env.example | AI Studio/Gemini boilerplate, app Gemini use nahi karta. `.gitignore` mein `*.jks` nahi. | Hatao, real README likho, `*.jks` ignore. |
| L7 | Service 735-740 | API 24-25 par `requestAudioFocus(null, ...)` null listener (try/catch mein, to crash nahi, par focus na mile). Unverified. | No-op listener do. |

## 6. Fix prompt (copy-paste)

Ise AI Studio / Claude / kisi bhi coding AI mein project zip ke saath paste karo.

```text
You are a senior Android engineer (Kotlin, Jetpack Compose, foreground services, on-device speech). Fix the project 'Radha Reminder' (package com.example, applicationId com.aistudio.radharemind.vkzqmr, minSdk 24, targetSdk 36, AGP 9.1.1, Gradle 9.3.1). Do not rewrite the app from scratch: keep the UI and flow, fix the issues below.

App flow per cycle: play reminder audio -> listen for the word 'Radha' (राधा / राधे) for N seconds (default 15) in ONE continuous mic session -> if heard, vibrate + play confirmation audio -> wait interval (0-30 min) -> repeat. It runs in a microphone foreground service (VoiceReminderService). The APK is built on GitHub Actions.

HARD REQUIREMENTS (must not regress):
R1. No mic start/stop beep.
R2. One continuous mic session for the whole listening window, never restarted.
R3. Works with screen off for hours, intervals up to 30 min.
R4. Builds on a clean GitHub Actions runner with nothing local.

Work in this order. After each phase list the changed files.

PHASE 1 - Build / CI
1.1 Add gradlew, gradlew.bat and gradle/wrapper/gradle-wrapper.jar for Gradle 9.3.1. Add .github/workflows/build.yml: JDK 17 (temurin), actions/setup-java, gradle/actions/setup-gradle, chmod +x gradlew, ./gradlew assembleDebug, upload app/build/outputs/apk/debug/*.apk as an artifact.
1.2 In app/build.gradle.kts use the 'debugConfig' signing only if rootDir/debug.keystore exists, otherwise fall back to the default debug signing. Keep the release config working without env vars for debug builds.
1.3 Remove unused dependencies/plugins: Firebase (bom, ai, appcheck), Room (+ksp), Retrofit, Moshi (+codegen), OkHttp, logging-interceptor, secrets plugin, google-services plugin, KSP plugin. Clean libs.versions.toml. Keep Compose, lifecycle, activity-compose, coroutines, core-ktx. Move vosk-android 0.3.47 into the version catalog and add net.java.dev.jna:jna:5.13.0@aar next to it. Keep INTERNET and ACCESS_NETWORK_STATE.
1.4 Delete AI Studio boilerplate (README Gemini steps, metadata.json, .aistudio/, .env.example). Write a short real README. Add *.jks and *.keystore to .gitignore.

PHASE 2 - Manifest
2.1 Add <queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>.
2.2 Set allowBackup=false (or backup rules excluding shared prefs and files/vosk-model).
2.3 Fix theme/window background so there is no dark flash in light mode.

PHASE 3 - Make Vosk the primary engine
3.1 One process-wide VoskSpeechEngine singleton (remove the separate instances in Service, ViewModel, DiagnosticTestSuite). Thread-safe model loading (@Volatile + Mutex/synchronized, a single loading job). Provide a suspend awaitModel() that loads on Dispatchers.Default, never on Main. close() the Model when replaced/removed and when the service is destroyed.
3.2 Catch Throwable (UnsatisfiedLinkError, NoClassDefFoundError) around every native Vosk call; surface a visible error and fall back visibly.
3.3 Model install: download to cacheDir temp (check HTTP 200 and size), extract to a temp folder, validate required entries (am/final.mdl, conf/model.conf, graph/), then atomically rename to vosk-model. Never delete a working model before the new one is valid. Fix zip-slip by comparing against dest.canonicalPath + File.separator. Make Cancel real (check ensureActive()/an AtomicBoolean in the read loop, close the connection, delete temp files, report honestly). Replace the loose OR in isValidModelFolder with the strict check. Block unpack/delete while the service is running.
3.4 If no model is installed, show a prominent card on first launch ('Download Hindi model ~42 MB, required for beep-free continuous listening') and make Start prompt for the download first. Google SpeechRecognizer stays only as an explicit degraded fallback with a visible warning that beeps/restarts may occur.
3.5 Build the Vosk grammar with org.json.JSONArray (quotes escaped). Create ONE shared keyword list (e.g. object RadhaKeywords) used by both the Vosk grammar and RadhaMatcher. Include राधा, राधे, राधारानी, राधिका and romanized variants. Remove the dead 'radhe-radhe' entry. Latin extra keywords must match whole words only; Devanagari may use contains.
3.6 False positives: trigger only when the trimmed text matches a keyword and is not empty or '[unk]'. Add a Settings option 'Sensitivity' (Normal = partial results, Strict = final result only).
3.7 Vosk onError: if the recorder fails mid-window and more than 3 seconds remain, retry once after 500 ms; otherwise end the window with a visible note.

PHASE 4 - Service correctness
4.1 Add a listeningSessionId incremented on every start and stop of listening. Vosk/Google callbacks carry the id and are ignored if stale. processSpeechMatches must also require phase == LISTENING_SILENT.
4.2 Do not reset an ERROR state in onDestroy. handleError -> stopSelf -> onDestroy must keep phase = ERROR and the message so the UI shows it.
4.3 muteAudioCues: remove STREAM_ALARM entirely. Track mute state per stream and unmute exactly what was muted, even if a later call throws. Handle SecurityException (DND access). Skip muting entirely when Vosk is active.
4.4 Timing/wake lock: hold a PARTIAL_WAKE_LOCK for the whole cycle (timeout = interval + window + reminder duration + 60 s), release on stop. Compute countdowns from SystemClock.elapsedRealtime() end timestamps instead of counting delay(1000) loops. Also schedule the next cycle with AlarmManager.setExactAndAllowWhileIdle (fall back to setAndAllowWhileIdle if exact alarms are not permitted), delivered to the service through a PendingIntent action, so Doze cannot delay it. Keep the battery-optimization guide.
4.5 Notification: update at most every 1 s while listening and about every 30 s (or on minute change) while waiting. Use a new monochrome vector icon res/drawable/ic_stat_radha.xml as the small icon.
4.6 Reminder-audio failure: retry at most 3 times (2 s apart), then handleError('Reminder audio unreadable, please re-select'). At ACTION_START verify that both audio URIs are readable (persisted permission + openFileDescriptor) before starting.
4.7 Check RECORD_AUDIO first and wrap startForeground in try/catch(SecurityException); on failure set ERROR state and stopSelf().
4.8 API < 26 audio focus: pass a no-op OnAudioFocusChangeListener instead of null.

PHASE 5 - UI / misc
5.1 Replace the fixed 36dp/32dp spacers and WindowInsets(0.dp) with proper systemBars + displayCutout insets.
5.2 Diagnostics: refresh on ON_RESUME; show 'offline speech ready' only when the Vosk model is installed; run file/network checks on Dispatchers.IO.
5.3 Move the MediaMetadataRetriever duration query off the main thread.

PHASE 6 - Tests
- Update ExampleUnitTest: 'ram' must NOT match 'program'; 'राधिका' must match; a keyword containing a quote must not break the grammar JSON (extract the grammar builder as a pure function).
- Unit-test the session-id guard if extracted as a pure function.
- Keep ExampleRobolectricTest passing.

DELIVERABLE: complete updated files (not diffs) plus the final file tree ready to zip, and a short list of anything you could not verify. Do not change application behavior beyond what is listed.
```

## 7. Real-device test checklist

1. Clean install of the GitHub Actions APK on Android 11+ and Android 14+: Diagnostics must not show 'Speech engine missing'.
2. Download the Hindi model: card shows Active. Start: during the full window the mic indicator stays on continuously and there is no beep.
3. Say 'Radha' at t=1 s and again in a separate cycle at t=14 s: each is recognized once and the confirmation audio plays once.
4. Say nothing: window ends exactly at the configured seconds, then the wait starts.
5. Lock the screen, interval 10 min, let 3 cycles run: all fire on time.
6. Set an alarm to ring during a listening window: it must ring (alarm stream untouched).
7. Revoke mic permission or remove the audio file: the error banner stays visible.
8. Cancel the model download midway: no model gets installed, UI says cancelled, re-download works.
9. Add 'ram' as an extra keyword and say 'program': must not trigger.

## 8. Jo verify nahi ho paya

- Gradle/Android build yahan run nahi hua (SDK aur Maven access nahi). MainActivity ke imports, icons aur composable definitions static check mein consistent mile, par compile ka final saboot aapke CI run se hi milega.
- Device/OEM par verify karo: C1 ka asar (Android 11+), Doze mein timer behaviour (H3), Vosk Hindi small model ka false-positive rate (M4), aur vosk-android ke saath JNA transitively aata hai ya nahi (H6, explicit add karna safe hai).
