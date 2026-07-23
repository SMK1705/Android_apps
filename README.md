<h1 align="center">🧠 TaskMind</h1>

<p align="center">
  <b>A private, 100% on-device assistant that reads your messages and turns them into action items —<br/>without anything leaving your phone.</b>
</p>

<p align="center">
  <a href="https://github.com/SMK1705/Android_apps/releases"><img src="https://img.shields.io/github/v/release/SMK1705/Android_apps?sort=semver&label=release&color=6C7A2E" alt="Latest release"/></a>
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform: Android"/>
  <img src="https://img.shields.io/badge/minSdk-26-blue" alt="minSdk 26"/>
  <a href="https://github.com/SMK1705/Android_apps/actions/workflows/android.yml"><img src="https://img.shields.io/github/actions/workflow/status/SMK1705/Android_apps/android.yml?branch=main&label=build" alt="Build"/></a>
  <img src="https://img.shields.io/badge/AI-on--device-8A2BE2" alt="On-device AI"/>
  <img src="https://img.shields.io/badge/license-All%20Rights%20Reserved-red" alt="License: All Rights Reserved"/>
</p>

---

> 🔒 **Understanding runs on-device by default.** A cloud model is strictly opt-in, data is encrypted at
> rest (SQLCipher), the app sits behind a biometric lock, and every byte that leaves the phone is
> auditable in **Settings → Data Egress** — which reads *"No data has left this device"* out of the box.

## ✨ What is TaskMind?

Your phone already knows what you need to do — it's buried in your SMS, notifications, call log, email,
recordings, and screenshots. **TaskMind** watches the sources *you* choose, uses an on-device LLM to
pull out the real action items, and shows each one as a card you approve, edit, or reject. Nothing is
saved or scheduled until you say so.

It's built on one stubborn principle: **your data stays on your phone.** The whole understanding
pipeline runs locally; the cloud is an explicit, logged opt-in you never have to touch.

## ⚡ What it does

| | |
|---|---|
| 📥 **Capture** | Collected live, on a background scan, or on demand — a **+ quick-capture sheet** (type or speak), a **share-sheet** target (text *and* images), a **Quick Settings tile**, a **home-screen widget**, and manual entry. Screenshots are read with on-device **OCR**; new voice/call recordings are **transcribed** the moment they land. |
| ✅ **Review** | Each suggestion is a concise **Inbox** card: approve, edit, reject, or **snooze**; swipe to triage; **undo** the last action; filter by kind or source. Approving a dated item schedules a reminder + calendar event (prompting for a time if none was found). Rejecting a sender repeatedly **down-ranks** its future suggestions. |
| 📞 **Act** | One tap to **Call** — resolving a named contact ("call Amma") to a number via your Contacts — or **Get directions** to a place named in the message. **Missed calls** (cellular *and* WhatsApp/Telegram) become "Call back" suggestions. Both actions also live on the **widget** and the **review notification**. |
| 🗂️ **Keep** | Approved items live in **Notes** — Active/Completed split, completion checkboxes, tickable checklists, tappable deep links (phone/URL/email/address), kind filters, and full-text search. |
| ⏰ **Schedule** | Reminders can **recur** (daily/weekly/monthly — pulled from "every Monday" or set by hand) or fire by **location** (a geofence when you arrive at a saved place). |
| 🔎 **Ask & recall** | **Ask TaskMind** answers questions over your own saved items — backed by **semantic search**, with **multi-turn follow-ups** ("anything overdue?" → "just the Work ones?"), a **persistent** thread that survives a restart, and results you can **act on in place** (done, reschedule, add-to-calendar, reopen). Recall even spans **completed** items, so a task closed by mistake is still findable. Opt in and the cloud model will answer in words, **grounded strictly in your notes**. **Waiting-On** tracks what others owe you and resurfaces it when they're next in touch. |
| 📣 **Show up** | A morning **Daily Brief**, a Sunday **Weekly Wins** recap, a **Reliability Doctor** that checks your reminders can actually reach you, and **Task Fade** — a stale list can declare bankruptcy in one tap. Reach your wrist with the **Wear OS** companion. |

## 🔒 Private by design

| Guarantee | How |
|---|---|
| **On-device understanding** | The extraction LLM runs locally — **MediaPipe Gemma** or the system **Gemini Nano** (ML Kit GenAI); the cloud model (Gemini) is opt-in only, and the UI always shows the *effective* route. |
| **Encrypted at rest** | The database is **SQLCipher** (AES-256-CBC + HMAC-SHA512); secrets live in EncryptedSharedPreferences. |
| **Locked** | Biometric lock on launch and on every return to the app. |
| **Auditable** | Every network egress is logged, metadata-only, in **Data Egress** — content never is. |
| **Portable** | Move to a new phone with an **encrypted, passphrase-sealed backup** (AES-256-GCM, PBKDF2). |

## 🧩 Sources

Every source is **off by default** and asks for its own permission when you turn it on.

| | | |
|---|---|---|
| 💬 SMS | 🔔 Notifications | ☎️ Call log |
| 👥 Contacts *(for the Call button)* | 📅 Calendar | 📊 App-usage digest |
| 🎙️ Voice / call recordings *(Vosk; optional Whisper)* | 🖼️ Screenshots — OCR *(Tesseract)* | 📧 Gmail *(OAuth, read-only)* |

## 📱 Install

**From your phone (over-the-air):** open **[Releases](https://github.com/SMK1705/Android_apps/releases)**,
grab the latest `taskmind-debug.apk`, and open it to install (allow "install unknown apps"). A rolling
`debug-latest` pre-release is kept current on every push to `main`.

**Build from source:**

```powershell
# Build + install to a connected device in one step
.\gradlew.bat :apps:taskmind:installDebug

# …or build the APK and install it yourself
.\gradlew.bat :apps:taskmind:assembleDebug
adb install -r apps\taskmind\build\outputs\apk\debug\taskmind-debug.apk
```

Full setup — permissions, the on-device model files, and the optional `MAPS_API_KEY` — is in the
[**TaskMind README**](apps/taskmind/README.md). For the architecture, data model, ingestion pipeline,
and security design, see the [**technical documentation**](apps/taskmind/docs/TECHNICAL_DOCUMENTATION.md)
([PDF](apps/taskmind/docs/TECHNICAL_DOCUMENTATION.pdf)).

## 🛠️ Built with

**Kotlin** · **Jetpack Compose** (Material 3) · **Hilt** · **Room + SQLCipher** · **WorkManager** ·
**DataStore** · on-device LLM via **MediaPipe** Gemma **or** the system **Gemini Nano** (ML Kit GenAI) ·
native **whisper.cpp** + **Vosk** (speech) · **Tesseract** (OCR) · **Gemini** 2.5 Flash (opt-in cloud,
incl. vision) · **AppFunctions** (system agent) · a **Wear OS** companion · biometric + geofencing + calendar.

## 🧪 Quality

- **Automated tests** — DAO & Room migrations, the extraction pipeline, approval + rejection learning,
  every ViewModel, receivers & workers, and JVM/Robolectric **Compose UI** tests for the redesigned
  screens. Unit tests + `assembleDebug` run on every PR.
- **Extraction eval** — [`tools/prompt_eval/`](tools/prompt_eval/) replays a **166-case, web-grounded**
  golden set through the live prompt and reports a per-type **confusion matrix**, recall/precision, and
  field accuracy to [`EVAL_REPORT.md`](tools/prompt_eval/EVAL_REPORT.md). Hardening the prompt against
  notification noise took cloud-model accuracy from **85% → 98%** and noise rejection from **82% → 99%**.
- **Manual QA** — a 27-suite / 325-case [functional test plan](apps/taskmind/docs/) backs each release.

## 🗺️ Roadmap

- [ ] Execute the full 325-case manual functional pass (device QA) and log defects.
- [ ] Lift **on-device (1B) model quality** toward the cloud numbers.
- [ ] Widen the eval's non-`none` coverage and add a fast smoke subset.

## 🏗️ Repo layout

A monorepo — each app is a self-contained Gradle module under `apps/`; shared code (when a second app
needs it) goes under `core/`.

```
Android_apps/
├─ apps/taskmind/            # TaskMind — the app (see its README)
├─ tools/prompt_eval/        # extraction-accuracy harness + golden set
├─ gradle/libs.versions.toml # one version catalog for every module
└─ core/                     # shared library modules, added on demand
```

## 🔧 Build & CI

| Workflow | Runs on | Trigger | What it does |
|---|---|---|---|
| **Android CI** (`android.yml`) | `ubuntu-latest` | push / PR to `main` | unit tests + `assembleDebug` (test report uploads on failure only; the APK ships via the release workflow, not as a CI artifact) |
| **Publish debug APK** (`release-apk.yml`) | `ubuntu-latest` | push to `main` + manual | builds and publishes to the rolling **`debug-latest`** pre-release for over-the-air install |
| **Install to phone** (`install-to-phone.yml`) | **self-hosted** | manual | `installDebug` over `adb` to a phone plugged into that runner |

`main` is protected — changes land via PR. **Release** signing keys, API keys, and on-device model
files (`*.task` / `*.litertlm`) are git-ignored and never committed. The **debug** keystore
(`apps/taskmind/debug.keystore`) *is* committed — it holds the standard, non-secret debug credentials,
so every local, CI, and OTA build shares one stable signature and can update an existing install in
place instead of forcing an uninstall.

**Gmail OAuth:** Gmail sign-in (Google Identity Services) has no embedded client-id — Google matches
the app by package + signing SHA-1 against an **Android OAuth client** you register in the Google
Cloud project:

- package `com.rajasudhan.taskmind`
- SHA-1 `CB:5D:68:D2:EA:B0:E1:DD:10:35:F6:F9:1C:35:54:8F:09:81:A3:F4` — the committed debug keystore's
  fingerprint (re-derive with `keytool -list -v -keystore apps/taskmind/debug.keystore -storepass android`)

If the consent flow cancels immediately or throws `DEVELOPER_ERROR`, that client is missing or its
SHA-1/package doesn't match the installed build.

## 📜 License

**© 2026 SMK1705. All Rights Reserved.** This software is published for viewing and reference only; no
use, copying, modification, or distribution is permitted without prior written permission. See
[`LICENSE`](LICENSE).
