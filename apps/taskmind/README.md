# TaskMind

TaskMind is a **private, on-device personal assistant** for Android. It watches your own data
sources (SMS, notifications, call logs), uses an **on-device LLM** to extract action items, and —
**only after you approve each one** — saves them as notes / to-dos or schedules in-app reminders.

It is a personal app you sideload on your own device (not for the Play Store). All understanding
runs on-device by default; nothing leaves the phone unless you explicitly enable a cloud provider.

- **Target device:** Samsung Galaxy S25 Ultra, One UI, Android 16
- **Package:** `com.rajasudhan.taskmind`
- **Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room + SQLCipher, DataStore, WorkManager,
  MediaPipe LLM Inference (on-device Gemma), Retrofit/OkHttp (optional cloud fallback)
- **SDK:** compileSdk 37 · minSdk 35 · targetSdk 36

---

## How it works

```
Live: SMS (ContentObserver) + Notifications (NotificationListenerService)
Periodic: WorkManager scan of recent SMS / call logs (every 30 min, battery-not-low)
        │
        ▼
  Noise pre-filter  ──►  On-device LLM (Gemma via MediaPipe)  ──►  JSON parse + dedup
        │                         (cloud only if you choose it)
        ▼
  Pending "suggestion"  ──►  Inbox (you Approve / Edit / Reject)  ──►  encrypted Notes DB
                                                                   └►  exact AlarmManager reminder
                                                                   └►  calendar event (de-duplicated)
```

Nothing is written to the Notes DB or scheduled until **you approve it** in the Inbox.

## Security & privacy

- **App lock:** BiometricPrompt (fingerprint/face/PIN) required on launch **and on every return**
  from the background.
- **Encryption at rest:** Notes/suggestions in a SQLCipher-encrypted Room DB; keys/settings in
  EncryptedSharedPreferences.
- **On-device by default:** understanding runs locally (Gemma). The cloud LLM is opt-in.
- **No telemetry:** ML Kit (which phoned usage stats to Google) was removed. No analytics.
- **Egress is auditable:** Settings → **Data Egress** logs every time data leaves the device
  (metadata only — host, purpose, time; never content). Normally reads "No data has left this device."

---

## Build & install

Run from the monorepo root (`Android_apps/`):

```powershell
# Build  (APK: apps\taskmind\build\outputs\apk\debug\taskmind-debug.apk)
.\gradlew.bat :apps:taskmind:assembleDebug

# Install (preserves app data)
adb install -r apps\taskmind\build\outputs\apk\debug\taskmind-debug.apk
```

Open the app and authenticate with biometrics/PIN on every launch.

---

## On-device LLM setup (required for free, offline understanding)

The model is **not bundled** (it's ~0.5–4 GB). You provide it once.

### 1. Get a MediaPipe-compatible Gemma model
- **Gemma 3 1B** (works today, fast): Hugging Face `litert-community/Gemma3-1B-IT` →
  **`gemma3-1b-it-int4.task`** (~555 MB). Use the plain build, **not** a `-web` one
  (`-web` = browser/WebGPU and will NOT load on Android).
- **Gemma 3n** (smarter, multimodal; license-gated on HF): an `E2B`/`E4B` **`.litertlm`** file.
  Supported by the bundled MediaPipe 0.10.35. The loader accepts `model.task` or `model.litertlm`.
- ⚠️ Gemma 3 **4B** has **no Android build** in `litert-community` (web-only) — don't use it.

### 2. Put it in the app's INTERNAL storage
> Files `adb push`-ed into `Android/data/<pkg>/files` are **not visible to the running app** on
> Samsung/Android (scoped-storage FUSE quirk). Copy into internal storage instead:

```powershell
# Push to a staging spot, then copy into internal storage as the app's UID:
adb push gemma3-1b-it-int4.task /sdcard/Android/data/com.rajasudhan.taskmind/files/llm/model.task
adb shell run-as com.rajasudhan.taskmind cp `
  /storage/emulated/0/Android/data/com.rajasudhan.taskmind/files/llm/model.task `
  /data/data/com.rajasudhan.taskmind/files/model.task
adb shell am force-stop com.rajasudhan.taskmind
```
The default model path is `/data/data/com.rajasudhan.taskmind/files/model.task` (or `model.litertlm`).
You can override it in **Settings → Understanding Engine → Model .task path**.

### 3. Confirm
**Settings → Check on-device model** → expect **"✓ On-device Gemma model loaded."**
(First inference is slow — model warm-up — then fast.)

> Tip: **Settings → Test Extraction (debug)** — paste any text, tap **Run extraction**, and a
> suggestion appears in the Inbox. Use this to verify the pipeline without sending real SMS.

## Cloud LLM (optional fallback)

Settings → Understanding Engine → **Cloud LLM** → paste an API key. Uses Google Gemini
(`gemini-2.5-flash`). Every call is recorded in the Data Egress panel. Leave on **On-Device** to
keep everything local.

---

## Sources & permissions

Toggle sources in the **Sources** tab; each requests its permission when enabled:

| Source | Permission / access | Notes |
|---|---|---|
| Notifications | Notification access (system settings) | **Per-app picker** below the toggle — check specific apps (Messages, WhatsApp, Gmail…) to cut noise; leave all unchecked = monitor every app |
| SMS | `READ_SMS` | Live + periodic; takes effect immediately |
| Call Logs | `READ_CALL_LOG` | Scanned on refresh / periodically |
| Calendar | `READ_CALENDAR` + `WRITE_CALENDAR` | Read to dedup; writes events on approval (no duplicates) |
| App Usage | Usage access (system settings) | **Daily screen-time digest** (total + top apps) → a note you can approve. Once per day, on-device |
| Email (Gmail) | Google OAuth (`gmail.readonly`) | Reads **unread Primary** emails; connect/disconnect in Sources. Needs a one-time Google Cloud setup (below). Understanding stays on-device — email content never leaves the phone |
| Voice/Call Recordings | `READ_MEDIA_AUDIO` | **On-device transcription (Vosk)** of recordings → suggestions. Needs a Vosk model pushed (below); audio never leaves the phone |

Also needed: `POST_NOTIFICATIONS` (for the "N suggestions to review" alert),
`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` (reminders), `QUERY_ALL_PACKAGES` (to list apps in the picker).

### Gmail setup (one-time, Google Cloud)

Gmail uses **your own** Google Cloud OAuth client (read-only). The app embeds no client-id/secret —
Google matches it by package name + signing SHA-1. In [console.cloud.google.com](https://console.cloud.google.com):

1. Create/select a project → **enable the Gmail API**.
2. **OAuth consent screen:** External; add scope `.../auth/gmail.readonly`; add your Google account as
   a **Test user** (leave it in *Testing* — no verification needed for personal use).
3. **Credentials → Create credentials → OAuth client ID → Android:** package `com.rajasudhan.taskmind`,
   SHA-1 of your debug cert (`gradlew :apps:taskmind:signingReport`). No secret to paste back.
4. In the app: **Sources → Email (Gmail) → on** → grant consent. The connected account shows under the
   toggle; **Disconnect** revokes the token. Every Gmail fetch appears in **Settings → Data Egress**.

### Transcription model setup (on-device Vosk)

Call/voice transcription runs **fully on-device** via [Vosk](https://alphacephei.com/vosk/models) — no
audio leaves the phone. The model isn't bundled; push one once:

1. Download a small Vosk model, e.g. **`vosk-model-small-en-in-0.4`** (Indian English, ~36 MB) — or
   `vosk-model-small-en-us-0.15`.
2. Zip it and push to internal storage as `vosk-model.zip` (the app unpacks it on first use):
   ```powershell
   adb push vosk-model-small-en-in-0.4.zip /sdcard/Android/data/com.rajasudhan.taskmind/files/vm.zip
   adb shell run-as com.rajasudhan.taskmind cp /storage/emulated/0/Android/data/com.rajasudhan.taskmind/files/vm.zip /data/data/com.rajasudhan.taskmind/files/vosk-model.zip
   ```
   (Or push the unpacked folder to `…/files/vosk-model/`.)
3. **Settings → Transcription → Check transcription model** → expect **"✓ Vosk model loaded"**.
4. Enable **Sources → Voice/Call Recordings**. New recordings in your Recordings/Call folders are
   transcribed on the periodic scan and become Inbox suggestions.

---

## Using it

- **Inbox** — pending suggestions (color-coded by type; soonest/important first). Approve / Edit /
  Reject each, or **Approve all** / **Reject all**. The ↻ button scans the last 10 minutes on demand.
- **Notes** — everything you approved, consolidated and color-coded; swipe-free delete.
- **Sources** — per-source toggles + the per-app notification picker.
- **Settings** — provider choice + model setup, calendar event duration/target, Data Egress audit,
  Test Extraction box, and **Delete All Private Data**.

Passive behavior: a foreground service keeps the live watchers alive; a WorkManager job scans every
30 minutes; new items raise a single "N suggestions to review" notification.

---

## Not yet implemented

- **Live** audio folder watcher (transcription currently runs on the periodic scan, not instantly)
- Gemma **3n** is supported but not yet loaded by default
- Cloud STT fallback for transcription (the `TranscriptionProvider` seam exists)

## Compilation requirements

- Android Studio (recent), JDK 11+
- Gemma `.task`/`.litertlm` model placed in internal storage (see above) for on-device mode
