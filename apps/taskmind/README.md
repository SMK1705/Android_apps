# TaskMind

TaskMind is a **private, on-device personal assistant** for Android. It watches your own data
sources (SMS, notifications, call logs, Gmail, app usage, voice/call recordings, and screenshots),
uses an **on-device LLM** to extract action items, and — **only after you approve each one** — saves
them as notes / to-dos or schedules reminders (one-off, recurring, or location-based).

It is a personal app you sideload on your own device (not for the Play Store). All understanding
runs on-device by default; nothing leaves the phone unless you explicitly enable a cloud provider.

- **Version:** Update 3 — latest release (tag `taskmind-v3`, `versionName` 3.0)
- **Target device:** Samsung Galaxy S25 Ultra, One UI, Android 16
- **Package:** `com.rajasudhan.taskmind`
- **Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room + SQLCipher (`net.zetetic:sqlcipher-android`,
  16 KB page-size ready), DataStore, WorkManager, MediaPipe LLM Inference (on-device Gemma),
  Vosk (on-device speech-to-text), Tesseract (on-device OCR), Retrofit/OkHttp (optional cloud fallback)
- **SDK:** compileSdk 37 · minSdk 35 · targetSdk 36
- **16 KB page size:** all bundled native libraries are aligned for Android 15+/16 16 KB-page devices

---

## How it works

```
Live:      SMS (ContentObserver) + Notifications (NotificationListenerService)
           + new voice/call recordings (transcribed immediately)
Periodic:  WorkManager scan of SMS / call logs / Gmail / recordings / screenshots
           (configurable 15 min → 6 h, battery-not-low)
On-demand: Inbox 🎤 voice note · ↻ refresh · ＋ manual entry
Capture:   share-sheet (text + images) · Quick Settings tile · home-screen widget
        │
        ▼
  Noise pre-filter  ──►  OCR (Tesseract) / STT (Vosk) for images & audio
        │
        ▼
  On-device LLM (Gemma via MediaPipe)  ──►  JSON parse + dedup + learned-rejection down-rank
        │                  (cloud only if you choose it)
        ▼
  Pending "suggestion"  ──►  Inbox (Approve / Edit / Reject / Snooze, Undo, filter)  ──►  encrypted Notes DB
   (or Approve/Reject straight from the notification)                                 └►  exact AlarmManager reminder
                                                                                      │   (one-off, recurring, or geofenced)
                                                                                      └►  calendar event (de-duplicated)
```

Nothing is written to the Notes DB or scheduled until **you approve it** — in the Inbox, from the
notification, or via quick capture.

## Security & privacy

- **App lock:** BiometricPrompt (fingerprint/face/PIN) required on launch **and on every return**
  from the background.
- **Encryption at rest:** Notes/suggestions in a SQLCipher-encrypted Room DB; keys/settings in
  EncryptedSharedPreferences.
- **On-device by default:** understanding runs locally (Gemma). The cloud LLM is opt-in.
- **No telemetry:** ML Kit (which phoned usage stats to Google) was removed. No analytics.
- **Egress is auditable:** Settings → **Data Egress** logs every time data leaves the device
  (metadata only — host, purpose, time; never content). Normally reads "No data has left this device."
- **Encrypted backup:** Settings → **Encrypted Backup & Restore** seals your notes, suggestions, and the
  database key into one file with **AES-256-GCM** under a passphrase you choose (PBKDF2-HMAC-SHA256, magic
  `TMBK1`). The file is unreadable off-device without the passphrase — there's no recovery if you lose it.
  Restore validates, swaps in the data, and restarts the app.

---

## Build & install

Run from the monorepo root (`Android_apps/`):

```powershell
# Build  (APK: apps\taskmind\build\outputs\apk\debug\taskmind-debug.apk)
.\gradlew.bat :apps:taskmind:assembleDebug

# Install (preserves app data)
adb install -r apps\taskmind\build\outputs\apk\debug\taskmind-debug.apk

# …or build + install to a connected device in one step:
.\gradlew.bat :apps:taskmind:installDebug
```

Open the app and authenticate with biometrics/PIN on every launch.

### Install from your phone (GitHub Actions)

The **Install to phone** workflow (manual `workflow_dispatch`) builds and `adb install`s a fresh debug
build, so you can push a new build without touching the terminal. It checks out the branch you pick in
*Run workflow* (default `main`) and runs `installDebug`, so it always installs the latest **merged**
code — not unpushed local work.

> ⚠️ **It's tethered, not over-the-air.** The job runs on a **self-hosted runner (your laptop)** and
> installs to a phone **plugged into that laptop** by USB. Triggering it from the GitHub mobile app only
> *starts* the job remotely — the install still happens laptop→USB→phone. So it needs, at trigger time:
> the laptop's runner **online**, and the phone **connected with USB debugging authorized**. If you run
> it while holding the phone (unplugged, away from the laptop) it fails at the connection check with
> **"No authorized phone detected."**
>
> **To install while away from the laptop:** open the latest **Android CI** run → download the
> `taskmind-debug-apk` artifact → unzip → sideload the APK (enable "install unknown apps" for your
> browser/Files app). Same debug signature, so it updates in place and keeps your data.

---

## Quick start (how to use)

1. **Unlock** — open the app and authenticate (fingerprint / face / PIN). Required on every launch.
2. **Pick sources** — open the **Sources** tab and toggle on what TaskMind should watch (SMS,
   Notifications, Gmail, Call Logs, App Usage, Voice/Call Recordings). Grant each permission when
   prompted. Email and voice transcription need a little one-time setup (sections below).
3. **Get understanding working** — set up the on-device model (below) so extraction is free and
   offline. Items then arrive on their own; tap **↻** in the Inbox header to scan the last 10 minutes
   right now.
4. **Review the Inbox** — every suggestion is a card with a short **summary + source**; tap it to
   expand the full original text. **Approve** (✓), **Edit** (✎), or **Reject** (✗) each — or
   **Approve all** / **Reject all**. Approving a dated item with no time asks you to pick one (or keep
   it all-day). Tap the **🎤** button to add an item by speaking.
5. **Find what you kept in Notes** — approved items live here, color-coded by type; tap any note to
   open its full detail (body, source, due date).
6. **Stay private** — everything runs on-device by default; open **Settings → Data Egress** to confirm
   nothing has left the phone.

> New here? Tap the **?** in the top bar anytime to replay the in-app guide.

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
| Email (Gmail) | Google OAuth (`gmail.readonly`) | Reads **unread Primary** emails. Connect **multiple accounts** via the system account chooser; each gets its own row with a per-account **Disconnect**. Needs a one-time Google Cloud setup (below). Understanding stays on-device — email content never leaves the phone |
| Voice/Call Recordings | `READ_MEDIA_AUDIO` | **On-device transcription (Vosk)** of recordings → suggestions. New recordings are transcribed **immediately** (live watcher), not just on the periodic scan. Needs a Vosk model pushed (below); audio never leaves the phone |
| Screenshots (OCR) | `READ_MEDIA_IMAGES` | **On-device OCR (Tesseract)** of new screenshots → suggestions. Needs an `eng.traineddata` model pushed (below); images never leave the phone |

Also needed: `POST_NOTIFICATIONS` (the "N suggestions to review" alert + its Approve/Reject actions),
`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` (reminders), `RECORD_AUDIO` (the Inbox voice-note button),
`READ_MEDIA_IMAGES` (screenshot OCR), `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` (geofenced
location reminders — requested only when you attach a place to a reminder), `QUERY_ALL_PACKAGES` (to
list apps in the picker). The share-sheet target, Quick Settings tile, and home-screen widget need no
extra permission.

### Gmail setup (one-time, Google Cloud)

Gmail uses **your own** Google Cloud OAuth client (read-only). The app embeds no client-id/secret —
Google matches it by package name + signing SHA-1. In [console.cloud.google.com](https://console.cloud.google.com):

1. Create/select a project → **enable the Gmail API**.
2. **OAuth consent screen:** External; add scope `.../auth/gmail.readonly`; add your Google account as
   a **Test user** (leave it in *Testing* — no verification needed for personal use).
3. **Credentials → Create credentials → OAuth client ID → Android:** package `com.rajasudhan.taskmind`,
   SHA-1 of your debug cert (`gradlew :apps:taskmind:signingReport`). No secret to paste back.
4. In the app: **Sources → Email (Gmail) → on** → pick an account in the chooser → grant consent. Use
   **Add another account** to connect more mailboxes; each gets its own **Disconnect** (which revokes
   that account's token). Every Gmail fetch appears in **Settings → Data Egress**.

### Transcription model setup (on-device Vosk)

Call/voice transcription **and the Inbox voice-note button** run **fully on-device** via
[Vosk](https://alphacephei.com/vosk/models) — no audio leaves the phone. The model isn't bundled.

> **Easiest:** run `python tools/setup_vosk_model.py` (see [`tools/README.md`](../../tools/README.md)) —
> it downloads a model and installs it into app storage on a connected device. The manual steps below
> do the same thing by hand.

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
   transcribed **as they appear** (a live folder watcher) and become Inbox suggestions.

### OCR model setup (on-device Tesseract)

Screenshot OCR runs **fully on-device** via [Tesseract](https://github.com/tesseract-ocr/tessdata_fast)
— no image leaves the phone. The model isn't bundled.

> **Easiest:** run `python tools/setup_tesseract_model.py` (see [`tools/README.md`](../../tools/README.md)) —
> it downloads `eng.traineddata` (~12 MB) and installs it into app storage on a connected device. The
> manual steps below do the same thing by hand.

1. Download `eng.traineddata` from `tesseract-ocr/tessdata_fast` (or `tessdata`).
2. Push it into the app's internal `tessdata/` folder (Tesseract expects a `tessdata` subdirectory):
   ```powershell
   adb push eng.traineddata /sdcard/Android/data/com.rajasudhan.taskmind/files/eng.traineddata
   adb shell run-as com.rajasudhan.taskmind mkdir -p /data/data/com.rajasudhan.taskmind/files/tessdata
   adb shell run-as com.rajasudhan.taskmind cp /storage/emulated/0/Android/data/com.rajasudhan.taskmind/files/eng.traineddata /data/data/com.rajasudhan.taskmind/files/tessdata/eng.traineddata
   ```
3. **Settings → Screenshot OCR → Check OCR model** → expect **"✓ Tesseract model loaded — screenshot OCR runs offline."**
4. Enable **Sources → Screenshots (OCR)**. New screenshots are read on-device and become Inbox suggestions.

---

## Using it

- **Inbox** — pending suggestions (color-coded by type; soonest/important first). Each card shows a
  short **summary + source**; tap to expand the full original message. **Approve / Edit / Reject /
  Snooze** each (or swipe), **Undo** the last action, and **filter** by type or source — or
  **Approve all** / **Reject all**. Approving a dated item with **no time** prompts you to set one
  (or keep it all-day). The **🎤 button** records a quick voice note (on-device); **＋** adds an item by
  typing; the **↻** in the header scans on demand. You can also **Approve/Reject from the notification**
  without opening the app.
- **Notes** — everything you approved, consolidated and color-coded, with **full-text search** and an
  **Active / Completed** split. Tick items complete; list-like to-dos get a **checklist**. **Tap a
  note** for its detail (summary, body, source, due date, recurrence, location); **deep links** (phone,
  URL, email, address) in the text are tappable. Delete from the list or the detail screen.
- **Reminders** — one-off, **recurring** (daily / weekly / monthly, rescheduled when they fire), or
  **location** (a geofence triggers when you arrive at a saved place).
- **Capture from anywhere** — share text/images to TaskMind, the **Quick Settings tile**, or the
  **home-screen widget**; captured content feeds the pipeline without being shown (the biometric gate
  on *viewing* stays intact).
- **Sources** — per-source toggles + the per-app notification picker.
- **Settings** — provider choice + model setup (LLM / Vosk / OCR), calendar event duration/target,
  **scan frequency** (15 min → 6 h), note **retention**, **Export Notes (JSON)**, **Encrypted Backup &
  Restore**, Data Egress audit, Test Extraction box, a permissions panel, and **Delete All Private Data**.

Passive behavior: a foreground service keeps the live watchers alive; a WorkManager job scans on your
chosen interval (default 30 min); new items raise a single "N suggestions to review" notification.

---

## Not yet implemented

- Gemma **3n** is supported but not yet loaded by default
- Cloud STT fallback for transcription (the `TranscriptionProvider` seam exists)

## Compilation requirements

- Android Studio (recent), JDK 17+ (CI builds on JDK 21)
- Gemma `.task`/`.litertlm` model placed in internal storage (see above) for on-device mode
