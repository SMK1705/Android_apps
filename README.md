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
choose — SMS, notifications, call logs, Gmail, app usage, and call/voice recordings — and uses an
on-device LLM to extract action items. Nothing is saved or scheduled until you approve it.

- **Capture** — items are collected live, on a periodic background scan, or on demand, including a
  voice-note button that transcribes your speech on-device.
- **Review** — each suggestion appears in the Inbox as a concise summary card you can expand, then
  approve, edit, or reject. Approving a dated item schedules a reminder and a calendar event, prompting
  for a time when none was detected.
- **Keep** — approved items are organised in Notes, each with a full detail view.
- **Private by design** — understanding runs locally by default; data is encrypted at rest (SQLCipher),
  the app is locked behind biometrics, and every network egress is auditable in-app. A cloud LLM is
  available but strictly opt-in.
- **Guided** — a first-run in-app walkthrough, re-openable from the help button, introduces the flow.

Current release: **Update 2** (`taskmind-v2`). Full setup, permissions, and model instructions are in
[`apps/taskmind/README.md`](apps/taskmind/README.md).

## Build / install

```powershell
# Build a specific app
.\gradlew.bat :apps:taskmind:assembleDebug

# Install it (APK at apps\taskmind\build\outputs\apk\debug\taskmind-debug.apk)
adb install -r apps\taskmind\build\outputs\apk\debug\taskmind-debug.apk

# Or build + install to a connected device in one step
.\gradlew.bat :apps:taskmind:installDebug
```

CI builds and unit-tests every app on each PR; the **Install to phone** workflow installs a build on a
USB-connected device via a self-hosted runner. `main` is protected — changes land via PR.

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
