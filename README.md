# SMK-Android

A monorepo for my Android apps. Each app is a self-contained Gradle module under `apps/`;
shared code (when more than one app needs it) lives under `core/`.

## Layout

```
SMK-Android/
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

## Build / install

```powershell
# Build a specific app
.\gradlew.bat :apps:taskmind:assembleDebug

# Install it (APK at apps\taskmind\build\outputs\apk\debug\taskmind-debug.apk)
adb install -r apps\taskmind\build\outputs\apk\debug\taskmind-debug.apk
```

## Adding a new app

1. Create `apps/<newapp>/` (its own `build.gradle.kts` + `src/`), reusing `libs.versions.toml`.
2. Add `include(":apps:<newapp>")` to `settings.gradle.kts`.
3. Give it a unique `applicationId` so it installs alongside the others.

When two apps need the same code, extract it into a `core/<name>` library module and depend on it
with `implementation(project(":core:<name>"))` — don't copy-paste.

## Conventions

- **Secrets** (keystores, `keystore.properties`, API keys) are git-ignored — never commit them.
- **On-device models** (`*.task` / `*.litertlm`) are git-ignored; they're pushed to the device, not versioned.
- Branch off `main`; tag releases per app (e.g. `taskmind-v1.0`).
