# Radha Reminder

Android app (Kotlin + Jetpack Compose) with two features:

1. **Reminder** - plays your reminder audio, listens silently (offline Vosk, no mic beep) for the
   names you added, then plays the confirmation audio.
2. **Naam Jap Counter** - counts every *complete* name you chant (108-bead mala).

## No default names
The app ships with **no built-in names**. Add your own name(s) in:
- Reminder: Settings > *Names to listen for*
- Jap Counter: *Naam / Mantra for Jap* card

Only a fully spoken name counts. Half names ("रा", "राध"), other names and background words never count.
Write names in Devanagari (e.g. राम) for offline recognition; Latin names work only with the Google fallback.

## Build
GitHub Actions builds the debug APK on every push (see `.github/workflows/build.yml`).
Locally: `./gradlew assembleDebug`.
