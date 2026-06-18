# Android_apps

A monorepo for my Android apps. Each app is a self-contained Gradle module under `apps/`;
shared code (when more than one app needs it) lives under `core/`.

## Layout

```
Android_apps/
├─ settings.gradle.kts        # includes every app/module
├─ build.gradle.kts           # top-level plugins (apply false)
├─ gradle/libs.versions.toml  # ONE version catalog shared by all apps
├─ apps/
│  └─ taskmind/               # TaskMind — private on-device assistant (see its README)
└─ core/                      # shared library modules — added when a 2nd app reuses code
   # e.g. core/llm, core/security, core/designsystem
```

## Apps

| App | Module | What it is |
|-----|--------|------------|
| **TaskMind** | `:apps:taskmind` | Private, on-device personal assistant. See [`apps/taskmind/README.md`](apps/taskmind/README.md). |

### TaskMind

**TaskMind** is a private, on-device personal assistant for Android. It monitors the data sources you
choose — SMS, notifications, call logs, Gmail, app usage, call/voice recordings, and screenshots — and
uses an on-device LLM to extract action items. Nothing is saved or scheduled until you approve it.

- **Capture** — items are collected live, on a configurable background scan, or on demand: a voice-note
  button (transcribed on-device), a share-sheet target for text and images, a Quick Settings tile, a
  home-screen widget, and manual entry. Screenshots are read with on-device OCR; new voice/call
  recordings are transcribed the moment they appear.
- **Review** — each suggestion is a concise summary card in the Inbox: approve, edit, reject, or snooze
  it, undo the last action, and filter by type or source. Approving a dated item schedules a reminder
  and a calendar event, prompting for a time when none was detected. You can approve or reject straight
  from the notification, and repeatedly rejecting a sender down-ranks similar future suggestions.
- **Act** — call or get directions in one tap: a **Call** button that resolves a named contact ("call
  Amma") to a number via your Contacts, and **Get directions** to a place named in the message. Missed
  calls — cellular *and* chat-app (WhatsApp/Telegram) — become "Call back" suggestions automatically.
- **Keep** — approved items live in Notes with completion checkboxes (Active/Completed split), tickable
  checklists, tappable deep links (phone, URL, email, address), full-text search, and a detail view.
- **Schedule** — reminders can recur (daily / weekly / monthly — extracted from "every Monday" or set
  by hand) or trigger by **location** (a geofence fires when you arrive at a saved place).
- **Private by design** — understanding runs locally by default; data is encrypted at rest (SQLCipher),
  the app is locked behind biometrics, and every network egress is auditable in-app. A cloud LLM is
  available but strictly opt-in. Carry your data to a new device with an **encrypted, passphrase-sealed
  backup** (AES-256-GCM).
- **Guided** — a first-run in-app walkthrough, re-openable from the help button, introduces the flow.

Current release: **Update 4** (`taskmind-v4`). Full setup, permissions, and model instructions are in
[`apps/taskmind/README.md`](apps/taskmind/README.md); per-release history is in
[`CHANGELOG.md`](CHANGELOG.md).

## Build / install

```powershell
# Build a specific app
.\gradlew.bat :apps:taskmind:assembleDebug

# Install it (APK at apps\taskmind\build\outputs\apk\debug\taskmind-debug.apk)
adb install -r apps\taskmind\build\outputs\apk\debug\taskmind-debug.apk

# Or build + install to a connected device in one step
.\gradlew.bat :apps:taskmind:installDebug
```

### CI / CD

| Workflow | Runs on | Trigger | What it does |
|---|---|---|---|
| **Android CI** (`android.yml`) | GitHub-hosted `ubuntu-latest` | every push / PR to `main` | unit tests + `assembleDebug`, uploads the APK as the `taskmind-debug-apk` artifact |
| **Publish debug APK** (`release-apk.yml`) | GitHub-hosted `ubuntu-latest` | every push to `main` + manual | builds the APK and publishes it to the rolling **`debug-latest`** pre-release for **over-the-air install** |
| **Install to phone** (`install-to-phone.yml`) | **self-hosted** runner (your laptop) | manual (`workflow_dispatch`) | `installDebug` over `adb` to a phone **plugged into that laptop** |

> **Installing on the phone — two paths:**
> - **Over-the-air (works from anywhere):** open **Releases → "TaskMind debug build (latest)"** on the
>   phone, download `taskmind-debug.apk`, and open it to install (allow "install unknown apps"). The
>   **Publish debug APK** workflow keeps this current on every push to `main`. Steps are in the
>   [TaskMind README](apps/taskmind/README.md#install-from-your-phone-github-actions).
> - **Tethered (`Install to phone`):** runs on your laptop's self-hosted runner and installs to a phone
>   **plugged into that laptop** by USB. Triggering it from the GitHub mobile app only *starts* the job;
>   if the phone isn't plugged in it fails with **"No authorized phone detected."** Use the OTA path when
>   you're away from the laptop.

`main` is protected — changes land via PR.

## Adding a new app

1. Create `apps/<newapp>/` (its own `build.gradle.kts` + `src/`), reusing `libs.versions.toml`.
2. Add `include(":apps:<newapp>")` to `settings.gradle.kts`.
3. Give it a unique `applicationId` so it installs alongside the others.

When two apps need the same code, extract it into a `core/<name>` library module and depend on it
with `implementation(project(":core:<name>"))` — don't copy-paste.

## Conventions

- **Secrets** (keystores, `keystore.properties`, API keys) are git-ignored — never commit them.
- **On-device models** (`*.task` / `*.litertlm`) are git-ignored; they're pushed to the device, not versioned.
- Branch off `main`; tag releases per app (e.g. `taskmind-v2`).
