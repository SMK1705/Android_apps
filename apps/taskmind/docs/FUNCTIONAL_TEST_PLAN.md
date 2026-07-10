# TaskMind — Comprehensive Functional Test Plan

Manual, on-device functional test plan for **TaskMind** (`com.rajasudhan.taskmind`), Update 5.1 (`versionName` 5.1). It exercises every user-facing flow and system behavior by hand on a real device. The companion [`AUTOMATED_TEST_PLAN.md`](AUTOMATED_TEST_PLAN.md) specifies the automated regression suite that locks the same behavior down in CI.

> Cases are **grounded in the current source** — each suite cites its source-of-truth file(s), and a few cases carry a `> Note:` flagging where the code differs from older docs/expectations. Re-verify against code after any refactor.

## How to use this plan

- Run the **P0 smoke set** on every build; run the **full regression** per release tag. See [Execution model](#execution-model).
- Execute suites top-to-bottom — earlier suites (lock, sources, pipeline) set up state that later suites rely on.
- For each case, record **Pass / Fail / Blocked**, the device + build (`versionName` / commit), and attach a screenshot or `logcat` excerpt on failure. Use the [Defect log](#defect-log) template.
- Many flows don't need real SMS/email — drive them through **Settings → Test Extraction** and the capture surfaces (see [Test-injection tooling](#test-injection-tooling)).

## Scope & objectives

- **In scope:** functional correctness of all screens, capture surfaces, the extraction/approval pipeline, scheduling (alarms/recurrence/geofence), notifications, persistence, backup/restore, privacy/egress, settings, permissions, resilience, and functional accessibility.
- **Out of scope (here):** unit/UI automation (see the automated plan), security penetration testing, formal performance benchmarking, and localization — though basic large-font/rotation resilience is covered in suite 26–27.
- **Objective:** before each release, achieve 100% P0 pass with no open P0/P1 defects, every suite executed at least once.

## Test environment & device matrix

| Role | Device / target | Why |
|---|---|---|
| **Primary** | Samsung Galaxy S25 Ultra, One UI, **Android 16** (1440×3120) | Target hardware; biometrics, geofence, real alarms, contacts. |
| Secondary | Emulator **API 35** (minSdk) and **API 36** (targetSdk) | No-biometric / permission-denial / lifecycle variants; mock-location geofence triggers. |

Build & install (repo root, PowerShell):

```powershell
./gradlew :apps:taskmind:assembleDebug -Dorg.gradle.jvmargs="-Xmx5g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8"
adb install -r apps/taskmind/build/outputs/apk/debug/taskmind-debug.apk   # -r preserves app data
```

`adb` is at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` (not on PATH). Launch: `adb shell monkey -p com.rajasudhan.taskmind -c android.intent.category.LAUNCHER 1`. Screenshot: `adb exec-out screencap -p > out.png`.

## Prerequisites checklist

Some suites are **blocked** without one-time setup — complete what each suite needs first:

- [ ] **On-device LLM:** a Gemma `.task`/`.litertlm` model pushed to internal storage; *Settings → Check on-device model* reads "✓ … loaded". (Blocks live extraction in suites 6, 16, 19–20.)
- [ ] **Vosk model** pushed → *Settings → Transcription* reads loaded. (Suite 17.)
- [ ] **Tesseract `eng.traineddata`** pushed → *Settings → Screenshot OCR* reads loaded. (Suite 18, image capture in 5.)
- [ ] **Gmail OAuth** client + test user configured in Google Cloud. (Gmail sub-cases in suites 4, 23.)
- [ ] **`MAPS_API_KEY`** in `local.properties`, rebuilt. (Map preview in suite 10/12; *Get directions* works without it.)
- [ ] A few **Contacts** seeded with names + numbers. (Suite 15–16.)
- [ ] A **fingerprint/face/PIN enrolled** (and a way to remove it) on the primary device. (Suite 1.)
- [ ] Cloud LLM **API key** available if testing the cloud path / egress logging. (Suite 6, 23.)

## Test-case schema

Every case below uses:

**`TM-<AREA>-NN`** · *short title* — **priority** · *type* — then **Preconditions**, **Steps**, **Expected**.

- **AREA** — the suite's short code (see the [Suite index](#suite-index)); **NN** restarts at 01 per area.
- **Priority** — `P0` launch-blocker / core happy path · `P1` important · `P2` minor/cosmetic.
- **Type** — `positive` / `negative` / `edge`.

## Test-injection tooling

So you rarely need to send real messages:

- **Settings → Test Extraction (debug)** — paste arbitrary text, **Run extraction**, and it goes through the live pipeline to an Inbox suggestion. The primary driver for pipeline/Inbox/notification suites.
- **Capture surfaces** — share-sheet (text **and** image), Quick-Settings tile, home-screen widget, and the Inbox `+` / 🎤 — all feed the pipeline. (`ShareTargetActivity`, `QuickCaptureActivity`, `QuickAddWidget`, `QuickTileService`.)
- **adb simulation** — `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.rajasudhan.taskmind` (reboot re-arm); emulator extended-controls **SMS send**; push a file into the Recordings/Screenshots MediaStore folder to trip the live media observers; **mock location** route for geofence ENTER; device clock change for recurrence/late-fire.
- **Golden text set** — reuse the curated inputs in `tools/prompt_eval/` as canned extraction fixtures.

## Suite index

27 suites · **325 cases**. Click a suite to jump.

| # | Suite | Area | Cases |
|---:|---|---|---:|
| 1 | [App lifecycle & security](#1-app-lifecycle--security) | `SEC` | 13 |
| 2 | [Onboarding / Guide](#2-onboarding--guide) | `GUIDE` | 10 |
| 3 | [Navigation & theming](#3-navigation--theming) | `NAV` | 11 |
| 4 | [Sources toggles & permissions](#4-sources-toggles--permissions) | `SRC` | 15 |
| 5 | [Quick-capture surfaces](#5-quick-capture-surfaces) | `CAP` | 12 |
| 6 | [Understanding / extraction pipeline](#6-understanding--extraction-pipeline) | `PIPE` | 19 |
| 7 | [Inbox triage](#7-inbox-triage) | `INBOX` | 17 |
| 8 | [Rejection learning](#8-rejection-learning) | `REJ` | 11 |
| 9 | [Notes list](#9-notes-list) | `NOTES` | 12 |
| 10 | [Note detail](#10-note-detail) | `DETAIL` | 16 |
| 11 | [Reminders & alarms](#11-reminders--alarms) | `ALARM` | 11 |
| 12 | [Geofence / location reminders](#12-geofence--location-reminders) | `GEO` | 9 |
| 13 | [Calendar integration](#13-calendar-integration) | `CAL` | 11 |
| 14 | [Notifications](#14-notifications) | `NOTIF` | 14 |
| 15 | [Contact resolution](#15-contact-resolution) | `CONTACT` | 12 |
| 16 | [Missed-call capture](#16-missed-call-capture) | `MISSCALL` | 11 |
| 17 | [Transcription (Vosk)](#17-transcription-vosk) | `STT` | 12 |
| 18 | [OCR (Tesseract)](#18-ocr-tesseract) | `OCR` | 10 |
| 19 | [App-usage digest](#19-app-usage-digest) | `USAGE` | 8 |
| 20 | [Background scan & WorkManager](#20-background-scan--workmanager) | `SCAN` | 12 |
| 21 | [Data management](#21-data-management) | `DATA` | 11 |
| 22 | [Backup & restore](#22-backup--restore) | `BACKUP` | 11 |
| 23 | [Privacy / Data Egress](#23-privacy--data-egress) | `EGRESS` | 9 |
| 24 | [Settings persistence](#24-settings-persistence) | `SETTINGS` | 11 |
| 25 | [Permissions panel & denial behavior](#25-permissions-panel--denial-behavior) | `PERM` | 11 |
| 26 | [Resilience & edge](#26-resilience--edge) | `EDGE` | 14 |
| 27 | [Accessibility (functional)](#27-accessibility-functional) | `A11Y` | 12 |

## Test suites

### 1. App lifecycle & security
_Scope: biometric app-lock gate + foreground-service startup + first-launch scan scheduling. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/MainActivity.kt` (with `AppLock.kt`, `TaskMindApp.kt`)._

#### TM-SEC-01 · Cold-launch biometric gate appears and unlocks — `P0` · positive
- **Preconditions:** App lock ON (default; `appLockEnabled=true`). Device has fingerprint/face/PIN enrolled (`canEnforceLock()` true). App fully killed (swiped from recents).
- **Steps:** 1. Launch TaskMind from the launcher. 2. Observe the lock screen. 3. Complete biometric/credential auth on the system prompt.
- **Expected:** Before auth, `LockScreen` shows: pulsing lock emblem, title "TaskMind is locked", subtitle "Your data stays private behind biometrics.", and an "Unlock" button with a fingerprint icon. The system `BiometricPrompt` auto-appears on launch (titled "Unlock TaskMind", subtitle "Authenticate to access your private data"). On success the lock screen is replaced by the Inbox tab content (`startDestination = "inbox"`).

#### TM-SEC-02 · Re-lock on background → return — `P0` · positive
- **Preconditions:** App lock ON, credential enrolled, app already unlocked and showing Inbox.
- **Steps:** 1. Press Home (or switch to another app) to send TaskMind to the background (fires `ON_STOP`). 2. Re-open TaskMind from recents/launcher (fires `ON_RESUME`).
- **Expected:** On return, `isAuthenticated` was reset to `false` at `ON_STOP`, so the lock screen is shown again and the `BiometricPrompt` re-appears. App content is not visible until re-auth succeeds.

#### TM-SEC-03 · Manual Lock action re-locks immediately — `P1` · positive
- **Preconditions:** App lock ON, credential enrolled, app unlocked (so `onLock` is non-null).
- **Steps:** 1. On any main tab, tap the Lock icon (contentDescription "Lock app") in the top app bar. 
- **Expected:** `isAuthenticated` flips to false; the `LockScreen` is shown immediately. (Note: tapping the manual Lock action does not itself auto-fire the prompt — the prompt fires on the lock screen's `LaunchedEffect(Unit)` for the launch path; tap "Unlock" to re-prompt.)

> Note: The manual Lock icon is only rendered when `lockEnabled && canLock` (MainActivity.kt:159). With the lock off or no credential enrolled, the Lock icon is absent — only the "?" help icon shows in the top bar.

#### TM-SEC-04 · Document-picker round-trip does NOT re-lock — `P0` · positive
- **Preconditions:** App lock ON, credential enrolled, unlocked. On the Privacy/Settings screen with a backup/restore/export action that opens the system SAF picker (`AppLock.expectResult()` is called immediately before launch).
- **Steps:** 1. Tap an action that opens the system document picker (e.g. encrypted backup / restore / JSON export). 2. The picker UI appears (TaskMind goes to `ON_STOP`). 3. Choose/confirm a file and let the picker return to TaskMind.
- **Expected:** Because `shouldKeepUnlockedOnStop()` returns true for this one background, the `ON_STOP` does NOT set `isAuthenticated=false`. On return there is NO lock screen and NO re-prompt; the Settings subtree stays composed and the picker's result callback runs (the write/read completes — no silently empty 0-byte file). The `awaitingResult` flag is consumed (one-shot).

#### TM-SEC-05 · Picker flag is one-shot — second background re-locks — `P1` · edge
- **Preconditions:** Same as TM-SEC-04; you have just completed one picker round-trip (TM-SEC-04) without re-locking.
- **Steps:** 1. After the picker returns (no re-lock), press Home to background the app a second time without launching another picker. 2. Return to TaskMind.
- **Expected:** `shouldKeepUnlockedOnStop()` now returns false (flag was cleared during the picker round-trip, and `AppLock.reset()` runs on every `ON_RESUME`), so this ordinary background DOES re-lock. Lock screen + prompt appear.

#### TM-SEC-06 · No credential enrolled → lock auto-disabled (no lockout) — `P0` · edge
- **Preconditions:** App lock ON in settings (`appLockEnabled=true`), but the device has NO biometric and NO device credential enrolled (`canEnforceLock()` returns false / not `BIOMETRIC_SUCCESS`). Remove the screen lock in device Settings before launching.
- **Steps:** 1. Cold-launch TaskMind.
- **Expected:** Because `unlocked = isAuthenticated || !lockEnabled || !canLock` and `canLock` is false, the app opens straight to Inbox content with NO lock screen and NO prompt — the user is never stranded behind an unenforceable lock. The Settings → Security section additionally shows the red warning "No screen lock is set up on this device, so the app can't lock yet. Add a fingerprint, face unlock, or PIN in your device's security settings." Since `onLock` is null here, no Lock icon appears in the top bar.

#### TM-SEC-07 · Credential enrolled while backgrounded is picked up on resume — `P1` · edge
- **Preconditions:** App lock ON, but device starts with NO credential (app open, unlocked, `canLock=false`).
- **Steps:** 1. With TaskMind open, go to device security settings and enroll a PIN/fingerprint (backgrounds TaskMind). 2. Return to TaskMind.
- **Expected:** `ON_RESUME` re-runs `canEnforceLock()` and sets `canLock=true`. Because the `ON_STOP` while leaving did not re-lock (`canLock` was false at that moment), the session is still authenticated on the first return; the lock now becomes enforceable on the NEXT background/return. (Mirror case: removing the only credential while backgrounded makes `canLock=false` on resume so the user is not stranded on an unsucceedable lock screen.)

#### TM-SEC-08 · Lock toggled OFF opens straight to Inbox — `P0` · positive
- **Preconditions:** App lock currently ON, unlocked, credential enrolled.
- **Steps:** 1. Go to Settings → Security, turn OFF "App lock (biometric)". 2. Background the app and return. 3. Cold-kill and relaunch.
- **Expected:** With `lockEnabled=false`, `unlocked` is always true; no lock screen, no prompt, on both return and cold launch — app opens directly to Inbox. The Lock icon in the top bar disappears (`onLock` becomes null).

#### TM-SEC-09 · Flipping lock ON does not lock the current session — `P1` · edge
- **Preconditions:** App lock OFF, app open and unlocked, credential enrolled.
- **Steps:** 1. Go to Settings → Security and turn "App lock (biometric)" ON. 2. Observe the current screen (do not background). 3. Now background and return.
- **Expected:** Turning the lock on does NOT instantly lock the current session (`LaunchedEffect` keeps `isAuthenticated=true` while you were in with the lock off). Step 2: stays on the current screen, no prompt. Step 3: the lock now takes effect on the next background → return (lock screen + prompt appear).

#### TM-SEC-10 · Auth error path shows toast and stays locked — `P1` · negative
- **Preconditions:** App lock ON, credential enrolled, on the lock screen with the prompt visible.
- **Steps:** 1. Trigger an authentication ERROR (e.g. cancel the prompt, or lockout after too many attempts — `onAuthenticationError`).
- **Expected:** A short toast "Authentication error: <errString>" appears (e.g. "Authentication error: Authentication canceled"). `onSuccess` is NOT called; remains on the lock screen. Tapping "Unlock" re-invokes the prompt.

#### TM-SEC-11 · Auth-failed path (wrong finger) shows toast, no unlock — `P1` · negative
- **Preconditions:** App lock ON, credential enrolled, lock screen with prompt visible.
- **Steps:** 1. Present an unrecognized fingerprint/face so the matcher fails but does not error out (`onAuthenticationFailed`).
- **Expected:** A short toast "Authentication failed" appears; the prompt stays up for retry; app stays locked (no `onSuccess`). A subsequent correct credential unlocks normally.

#### TM-SEC-12 · Foreground service starts after unlock without crash (Android 12+) — `P0` · positive
- **Preconditions:** Samsung S25 Ultra / Android 16. App lock ON, credential enrolled. Logcat capturing.
- **Steps:** 1. Cold-launch and complete biometric unlock. 2. Watch the live-watcher persistent notification appear. 3. Inspect logcat.
- **Expected:** Right after `unlocked` becomes true, `ContextCompat.startForegroundService(...TaskMindForegroundService...)` runs inside `runCatching` from a valid foreground moment, so the service starts and posts its persistent notification. NO `ForegroundServiceStartNotAllowedException` in logcat. (Also verify the lock-off path: with lock disabled, the service still starts on cold launch since `unlocked` is true immediately.)

#### TM-SEC-13 · WorkManager periodic scan scheduled on first launch — `P1` · positive
- **Preconditions:** Fresh install (or cleared data). Scan frequency default = 30 min (`DEFAULT_SCAN_FREQUENCY_MINUTES`).
- **Steps:** 1. Launch the app once (process start runs `TaskMindApp.onCreate`). 2. Dump WorkManager state: `adb shell dumpsys jobscheduler | grep -i taskmind` or use the WorkManager inspector for unique work name `taskmind_periodic_scan`.
- **Expected:** A unique periodic `DataCollectionWorker` is enqueued under name "taskmind_periodic_scan" with a 30-minute interval and a `requiresBatteryNotLow` constraint, using `ExistingPeriodicWorkPolicy.KEEP` (existing schedule preserved on relaunch — `replace=false`). The notification channel is created eagerly in `onCreate` regardless of unlock state.

### 2. Onboarding / Guide
_Scope: first-run walkthrough overlay, pager controls, mark-seen persistence, re-open from "?". Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/ui/guide/GuideOverlay.kt` (with `GuideViewModel.kt`)._

#### TM-GUIDE-01 · First-run auto-show — `P0` · positive
- **Preconditions:** Fresh install / cleared data (`hasSeenGuide=false`). App unlocked to content.
- **Steps:** 1. Launch and reach the Inbox.
- **Expected:** The `GuideOverlay` dialog auto-appears (`showGuide = manual || !seen`, true when not seen). It is a near-fullscreen Surface (92% width, 80% height), starting on page 1 "Welcome to TaskMind" with a waving-hand icon and body beginning "Your private, on-device assistant…". A "Skip" text button sits top-right; "Back" (disabled) and "Next" at the bottom.

#### TM-GUIDE-02 · Page count and indicator dots — `P1` · positive
- **Preconditions:** Guide open on page 1.
- **Steps:** 1. Count the indicator dots. 2. Note which dot is highlighted.
- **Expected:** Exactly 6 dots (pages: Welcome / Choose your sources / Review your Inbox / Add by voice / Find it in Notes / Private by design). The current page's dot is larger (10dp vs 8dp) and uses the primary/accent color; others use outlineVariant.

#### TM-GUIDE-03 · Next advances through all pages to "Get started" — `P0` · positive
- **Preconditions:** Guide open on page 1.
- **Steps:** 1. Tap "Next" repeatedly, watching the title and dots. 2. Continue to the last page.
- **Expected:** Each tap animates to the next page; titles progress Welcome → Choose your sources → Review your Inbox → Add by voice → Find it in Notes → Private by design. On the LAST page (index 5) the primary button label changes from "Next" to "Get started". The last page body mentions "Settings → Data Egress" and "Reopen this guide from the help icon in the top bar."

#### TM-GUIDE-04 · Back navigates and is disabled on first page — `P1` · positive/edge
- **Preconditions:** Guide open.
- **Steps:** 1. On page 1, observe the "Back" button. 2. Tap "Next" once, then tap "Back".
- **Expected:** On page 1 "Back" is disabled (`enabled = currentPage > 0`). After advancing then tapping Back, the pager animates back to the previous page; the highlighted dot moves accordingly. Back never scrolls below page 0 (coerced to ≥ 0).

#### TM-GUIDE-05 · Swipe gesture also pages — `P2` · positive
- **Preconditions:** Guide open on page 1.
- **Steps:** 1. Horizontally swipe left across the content area, then swipe right.
- **Expected:** The `HorizontalPager` advances on left-swipe and goes back on right-swipe; the "Next/Get started" label and dots stay in sync with the current page (e.g. swiping to the last page shows "Get started").

#### TM-GUIDE-06 · "Get started" dismisses and marks seen — `P0` · positive
- **Preconditions:** Guide open, advanced to the last page.
- **Steps:** 1. Tap "Get started". 2. Background and relaunch the app (or kill and relaunch).
- **Expected:** `onDismiss` runs → overlay closes (`_manualOpen=false`, `setHasSeenGuide(true)` persisted to DataStore). On relaunch the guide does NOT auto-appear (`!seen` is false).

#### TM-GUIDE-07 · "Skip" from any page dismisses and marks seen — `P1` · positive
- **Preconditions:** Fresh install, guide auto-shown on page 1 (or any mid page).
- **Steps:** 1. Tap "Skip" (top-right). 2. Relaunch the app.
- **Expected:** Same as "Get started" — overlay closes and `setHasSeenGuide(true)` is persisted; guide does not reappear on relaunch. (Skip and Get started share the same `onDismiss`.)

#### TM-GUIDE-08 · Dismiss by tapping outside / system back marks seen — `P2` · edge
- **Preconditions:** Guide auto-shown (first run).
- **Steps:** 1. Tap outside the dialog surface or press the system Back gesture (`onDismissRequest`). 2. Relaunch.
- **Expected:** `onDismissRequest` is wired to the same `onDismiss`, so dismissing this way also marks seen; guide does not reappear on relaunch.

#### TM-GUIDE-09 · Re-open from the "?" top-bar action — `P0` · positive
- **Preconditions:** Guide already seen (`hasSeenGuide=true`), on any main tab.
- **Steps:** 1. Tap the help icon (contentDescription "How to use TaskMind") in the top app bar. 2. Read the first page, then dismiss with "Skip"/"Get started".
- **Expected:** `guideViewModel.open()` sets `_manualOpen=true`, so `showGuide` becomes true and the overlay re-appears at page 1 even though it was already seen. Dismissing sets `_manualOpen=false` again (and re-persists seen=true); it does not auto-show on next launch.

#### TM-GUIDE-10 · Guide is reachable only after unlock — `P1` · edge
- **Preconditions:** App lock ON, fresh install, locked.
- **Steps:** 1. Observe the lock screen before authenticating. 2. Authenticate.
- **Expected:** The "?" action and the auto-shown guide live inside `TaskMindAppContent`, which only renders when `unlocked`. On the lock screen there is no help icon and no guide overlay; the guide auto-appears only after a successful unlock.

### 3. Navigation & theming
_Scope: 4-tab bottom nav + Notes detail back-stack + live theme switching. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/ui/bold/BoldKit.kt`, `ui/theme/Theme.kt`, `MainActivity.kt`._

#### TM-NAV-01 · Four bottom-nav tabs switch screens — `P0` · positive
- **Preconditions:** App unlocked, on Inbox.
- **Steps:** 1. Tap each bottom-nav item in turn: Inbox, Notes, Sources, Privacy. 2. Observe the active highlight and destination.
- **Expected:** Exactly 4 tabs render in order INBOX / NOTES / SOURCES / PRIVACY with uppercased labels "INBOX", "NOTES", "SOURCES", "PRIVACY" and icons (Inbox / Layers / Tune / Shield). Tapping each navigates to its route (`inbox`, `notes`, `sources`, `settings` — note PRIVACY maps to the `settings` route). The active item's icon pill gets the `surface2` background and accent tint; inactive items use `ink3`.

> Note: The 4th tab is labeled "PRIVACY" but its route is `settings` and it renders `SettingsScreen` (BoldKit.kt:210). The suite's "Privacy[=Settings]" naming matches the code.

#### TM-NAV-02 · Tab bar reselect is single-top (no stacking) — `P1` · edge
- **Preconditions:** Unlocked, on Inbox.
- **Steps:** 1. Tap Notes, then tap Notes again several times. 2. Press the system Back gesture once.
- **Expected:** `launchSingleTop = true` plus `popUpTo(startDestination){saveState=true}` mean repeated taps don't push duplicate destinations. After re-tapping Notes, a single system Back returns to the start destination (Inbox) rather than popping repeated Notes copies.

#### TM-NAV-03 · Notes → detail → Back preserves stack — `P0` · positive
- **Preconditions:** Unlocked. At least one approved note exists in Notes (use Settings → Test Extraction to create and Approve a suggestion first, so a note row is present).
- **Steps:** 1. Open Notes. 2. Tap a note row to open its detail (`notes/{noteId}`). 3. Observe the top bar. 4. Tap the back arrow (or system Back).
- **Expected:** Detail navigates to route `notes/{id}` with the top bar now showing the title "Note" and a back arrow (contentDescription "Back"); the bottom nav remains. Tapping back pops to the Notes list with scroll/selection state preserved. Entering uses a slide-in-from-Start animation; popping uses slide-in-from-End.

#### TM-NAV-04 · Cross-tab state restoration — `P1` · positive
- **Preconditions:** Unlocked, several notes/suggestions present so lists are scrollable.
- **Steps:** 1. On Notes, scroll down. 2. Switch to Sources, then Privacy, then back to Notes.
- **Expected:** `restoreState = true` / `saveState = true` restore each tab's saved state, so Notes returns at its prior scroll position rather than resetting to the top.

#### TM-NAV-05 · Top bar shows title/back only on note detail — `P1` · edge
- **Preconditions:** Unlocked.
- **Steps:** 1. Observe the top bar on Inbox, Notes, Sources, Privacy. 2. Open a note detail and observe again.
- **Expected:** On the four main tabs the `CenterAlignedTopAppBar` shows NO title and NO back arrow (`isNoteDetail` false) — only the "?" help action (and the Lock action when the lock is on/enforceable). Each main screen renders its own in-screen serif header. On a note detail (`route` starts with "notes/"), the bar shows the "Note" title and a back arrow.

#### TM-NAV-06 · Light/Dark/System theme applies live to all screens — `P0` · positive
- **Preconditions:** Unlocked. Device currently in Light mode.
- **Steps:** 1. Go to Privacy → Appearance → Theme. 2. Tap "Dark". 3. Tap "Light". 4. Tap "System". 5. After each, switch across Inbox/Notes/Sources tabs.
- **Expected:** The `themeModeFlow` change re-themes immediately without restart (`darkTheme` recomputes: LIGHT→false, DARK→true, SYSTEM→follows OS). Selecting "Dark" switches every screen to the Bold dark palette; "Light" back to the light palette; "System" follows the OS day-night setting. The selected chip is highlighted (accent fill, `BoldOnAccent` text). Persisted across relaunch.

#### TM-NAV-07 · "System" theme follows OS day-night toggle live — `P1` · positive
- **Preconditions:** Unlocked, Theme set to "System".
- **Steps:** 1. From the device quick settings, toggle the OS Dark mode on, then off, with TaskMind in the foreground.
- **Expected:** With `ThemeMode.SYSTEM`, `isSystemInDarkTheme()` drives `darkTheme`; the app flips between dark and light palettes in step with the OS toggle, no manual chip change needed.

#### TM-NAV-08 · Status/nav-bar icon contrast follows the chosen theme — `P1` · edge
- **Preconditions:** Unlocked. Device OS in LIGHT mode.
- **Steps:** 1. Set Theme = "Dark" (force dark while the OS itself is light). 2. Observe the status bar and navigation bar icons. 3. Switch Theme = "Light" and observe again.
- **Expected:** `LaunchedEffect(darkTheme)` sets `isAppearanceLightStatusBars = !darkTheme` and `isAppearanceLightNavigationBars = !darkTheme`. Forced Dark → light (white) system-bar icons over the dark app background, even though the OS is in light mode. Forced Light → dark system-bar icons. Contrast always tracks the in-app theme, not the OS setting.

#### TM-NAV-09 · Material You / dynamic color does NOT re-theme the app — `P1` · negative
- **Preconditions:** Unlocked.
- **Steps:** 1. Open Privacy/Settings and look for any "Material You" / dynamic-color toggle in the Appearance section. 2. Change the device wallpaper to one with a strikingly different accent color and return to TaskMind.
- **Expected:** There is NO dynamic-color/Material You control in the Appearance section (only the System/Light/Dark Theme chips). The app keeps its fixed Bold brand palette regardless of wallpaper; the accent (brand violet) does not change.

> Note: The suite scope lists a "Material-You (dynamic color) toggle [that] re-themes live", but the code does NOT support this. `TaskMindTheme` explicitly ignores the `dynamicColor` parameter (Theme.kt:71 `@Suppress("UNUSED_PARAMETER")`, comment: "wallpaper-based dynamic color is intentionally not applied"), and `SettingsScreen` exposes no dynamic-color toggle (a `dynamicColorFlow`/`dynamicColor` setter exists in `SettingsManager` but is never wired to UI and has no visible effect). Test the documented behavior: dynamic color is intentionally inert.

#### TM-NAV-10 · Theme choice survives background/return and relaunch — `P2` · positive
- **Preconditions:** Unlocked.
- **Steps:** 1. Set Theme = "Dark". 2. Background and return. 3. Kill and relaunch.
- **Expected:** `themeMode` is persisted in encrypted prefs (`KEY_THEME_MODE`) and read back on the StateFlow init, so the app re-opens in Dark on both return and cold launch (default if never set is SYSTEM).

#### TM-NAV-11 · Bottom nav stays visible across all main tabs and detail — `P2` · positive
- **Preconditions:** Unlocked.
- **Steps:** 1. Visit Inbox, Notes, Sources, Privacy. 2. Open a note detail.
- **Expected:** `BoldBottomNav` is rendered in the Scaffold `bottomBar` for all destinations, including the note-detail route; a 1dp top hairline divider (`line` color) sits above the 74dp-tall nav row on every screen.

- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\MainActivity.kt`
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\AppLock.kt`
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\TaskMindApp.kt`
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\ui\guide\GuideOverlay.kt`
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\ui\guide\GuideViewModel.kt`
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\ui\bold\BoldKit.kt`
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\ui\theme\Theme.kt`
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\data\source\SettingsManager.kt` (defaults: scan 30 min, lock ON, theme SYSTEM, dynamicColor OFF/inert)
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\data\source\SourceManager.kt` (`hasSeenGuide` persistence)
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\ui\settings\SettingsScreen.kt` (Appearance/Security strings; no dynamic-color toggle)

### 4. Sources toggles & permissions
_Scope: per-source enable + runtime permission prompts, persistence, allowlist, deep links. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/ui/sources/SourcesScreen.kt`._

> Note: The hero shows **"N of 8 sources active"** (`totalCount = 8`) and `activeCount` is computed only from the 8 persisted toggles — Notifications, SMS, Call Log, App Usage, Email, Calendar, Audio, Images. **Contacts is NOT counted** and has **no persisted toggle**: its Switch reflects only the live `READ_CONTACTS` grant, so it can never push the count past the other 8. Treat the suite's "8 sources" as those 8, not 9.

#### TM-SRC-01 · SMS enable grants permission and persists — `P0` · positive
- **Preconditions:** Fresh install; `READ_SMS` not yet granted; on Sources tab.
- **Steps:** 1. Tap the **SMS Messages** switch to ON. 2. In the system permission dialog, tap **Allow**. 3. Tap the switch again to ON (the first tap only launched the prompt). 4. Force-stop and relaunch the app; return to Sources.
- **Expected:** First tap shows the OS `READ_SMS` permission dialog (toggle does not flip yet). After Allow + second toggle, the SMS switch is ON, its icon tint turns to accent color, and the hero count increments by 1. After relaunch the SMS switch is still ON (persisted in `source_settings` DataStore).

#### TM-SRC-02 · SMS deny leaves toggle OFF — `P0` · negative
- **Preconditions:** `READ_SMS` not granted; SMS source OFF.
- **Steps:** 1. Tap **SMS Messages** switch ON. 2. In the system dialog tap **Deny**. 3. Observe the switch and hero count.
- **Expected:** The OS prompt appears; on Deny the SMS switch stays OFF and `toggleSms(true)` is never called (the `if (it && !granted)` branch only launches the request, the `else` that persists is not reached). Hero count unchanged. No crash.

#### TM-SRC-03 · Call Log enable → permission → persist — `P1` · positive
- **Preconditions:** `READ_CALL_LOG` not granted; on Sources tab under "Passive observers".
- **Steps:** 1. Tap **Call Logs** switch ON. 2. Allow `READ_CALL_LOG`. 3. Tap the switch ON again. 4. Toggle it OFF.
- **Expected:** First tap shows `READ_CALL_LOG` dialog; after Allow + second toggle the switch is ON and counted. Toggling OFF persists immediately (no permission re-prompt on the OFF path). Icon tint returns to muted (`ink3`) when OFF.

#### TM-SRC-04 · Contacts is permission-only, not a counted source — `P1` · edge
- **Preconditions:** `READ_CONTACTS` not granted.
- **Steps:** 1. Note the current "N of 8 sources active" value. 2. Tap the **Contacts** switch ON. 3. Allow `READ_CONTACTS`. 4. Observe the switch and the hero count. 5. Revoke `READ_CONTACTS` in OS Settings, return to app.
- **Expected:** Switch reflects the grant: ON once `READ_CONTACTS` is granted, but the **hero count does NOT change** (Contacts is absent from the `activeCount` list). After OS revoke + return, the Contacts switch shows OFF again (no persisted state — it is driven purely by `contactsPermissionState.status.isGranted`). The meta text reads "Match a name in a message to a number so \"Call\" can dial".

#### TM-SRC-05 · Notifications enable opens listener system settings — `P0` · positive
- **Preconditions:** Notification-listener access not granted; Notifications source OFF.
- **Steps:** 1. Tap the **Notifications** switch ON. 2. Observe the screen that opens. 3. Grant TaskMind notification access in that system screen, then back out to the app.
- **Expected:** Tapping ON immediately launches the OS **Notification access** settings screen (`ACTION_NOTIFICATION_LISTENER_SETTINGS`) AND calls `toggleNotifications(true)` (unlike SMS, both happen on the single tap). On return the Notifications switch is ON, the per-app allowlist card appears below it, and the hero count is incremented.

#### TM-SRC-06 · Per-app allowlist search + check persists — `P1` · positive
- **Preconditions:** Notifications source ON (allowlist card visible); several launchable apps installed.
- **Steps:** 1. In the card, read the helper text. 2. Type "what" into **Search apps**. 3. Check the box next to WhatsApp. 4. Clear the search. 5. Force-stop, relaunch, reopen Sources, expand the card.
- **Expected:** Helper text reads "Leave all unchecked to watch every app, or pick specific apps (e.g. Messages, WhatsApp, Gmail) to cut noise." Typing filters the list by label or package substring (case-insensitive). Checking WhatsApp adds its package to `KEY_NOTIFICATION_ALLOWLIST`; the box stays checked after relaunch. TaskMind itself never appears in the list (filtered out via `it.packageName != context.packageName`), and only launchable apps appear.

#### TM-SRC-07 · Allowlist height cap keeps other toggles reachable — `P2` · edge
- **Preconditions:** Notifications ON; device with many (>10) launchable apps so the list overflows.
- **Steps:** 1. Expand the allowlist card. 2. Try to scroll within the inner app list. 3. Scroll the outer Sources page past the card.
- **Expected:** The inner app `LazyColumn` is height-capped at `max = 240.dp` and scrolls internally; the SMS/Call Log/App Usage rows below remain reachable by scrolling the outer list (the card does not consume the whole screen).

#### TM-SRC-08 · Uncheck app removes it from allowlist — `P2` · positive
- **Preconditions:** WhatsApp currently checked in the allowlist.
- **Steps:** 1. Expand the allowlist card. 2. Uncheck WhatsApp. 3. Relaunch and re-expand.
- **Expected:** Unchecking calls `setAppMonitored(pkg, false)`, removing the package from the set; box stays unchecked after relaunch. With all boxes unchecked the empty-set semantics mean "monitor every app" (per `notificationAllowlist` docs).

#### TM-SRC-09 · App Usage enable opens usage-access settings — `P1` · positive
- **Preconditions:** Usage access not granted; App Usage OFF.
- **Steps:** 1. Tap the **App Usage** switch ON. 2. Observe the system screen. 3. Grant usage access, back out.
- **Expected:** Tap immediately launches `ACTION_USAGE_ACCESS_SETTINGS` AND calls `toggleAppUsage(true)` on the same tap. Switch persists ON and is counted in the hero. (Note: like Notifications, the toggle flips ON regardless of whether the user actually grants usage access in the system screen — the state tracks intent, not the special-access grant.)

#### TM-SRC-10 · Calendar enable requests READ+WRITE together — `P1` · positive
- **Preconditions:** Calendar permissions not granted; Calendar OFF (under "Connected accounts").
- **Steps:** 1. Tap **Calendar** switch ON. 2. In the OS dialog(s) Allow calendar access. 3. Tap the switch ON again. 4. Toggle OFF then back ON.
- **Expected:** First tap launches a **multiple-permissions** request for both `READ_CALENDAR` and `WRITE_CALENDAR` (`launchMultiplePermissionRequest`). Only when `allPermissionsGranted` is true does the second tap persist ON. Meta reads "Read to prevent duplicates; write to add events on approve". Switch persists across relaunch.
> Note: There is **no in-app calendar-selector UI** on this screen (the scope mentions one). Enabling Calendar only requests permissions and persists the toggle; no calendar-account chooser appears in `SourcesScreen.kt`.

#### TM-SRC-11 · Audio enable reveals editable recording paths — `P1` · positive
- **Preconditions:** `READ_MEDIA_AUDIO` not granted; Audio OFF (under "Reactive sensors").
- **Steps:** 1. Tap **Voice / Call recordings** switch ON. 2. Allow `READ_MEDIA_AUDIO`. 3. Tap switch ON again. 4. Observe the two path fields. 5. Edit "Call recording path", relaunch, return.
- **Expected:** First tap requests `READ_MEDIA_AUDIO`; after grant + second toggle the switch is ON and two `OutlinedTextField`s appear pre-filled with defaults `/storage/emulated/0/Recordings/Call/` and `/storage/emulated/0/Recordings/Voice Recorder/`. An edited path persists across relaunch. The two fields hide again when the source is toggled OFF.

#### TM-SRC-12 · Screenshots (OCR) enable → media-images permission — `P1` · positive
- **Preconditions:** `READ_MEDIA_IMAGES` not granted; Images OFF.
- **Steps:** 1. Tap **Screenshots (OCR)** switch ON. 2. Allow `READ_MEDIA_IMAGES`. 3. Tap switch ON again.
- **Expected:** First tap requests `READ_MEDIA_IMAGES`; after grant + second toggle the switch persists ON and is counted. Meta reads "Read text from new screenshots on-device. Needs a Tesseract model (Settings)." No path fields appear for this source.

#### TM-SRC-13 · Gmail enable launches account chooser then consent — `P1` · positive
- **Preconditions:** Email OFF; no Gmail account connected; at least one Google account on device.
- **Steps:** 1. Tap **Email (Gmail)** switch ON. 2. Pick an account in the Google account chooser. 3. Complete the OAuth consent screen. 4. Observe the row.
- **Expected:** Because `connectedAccounts` is empty, enabling triggers `addGmailAccount()` → account-chooser intent, then (if consent needed) the OAuth consent intent. Status text shows "Connecting <email>…" during the round-trip. On success the source persists ON, the meta switches to "1 account connected", the account email is listed with a **Disconnect** button, and an **Add another account** button appears. (Email toggle does NOT itself prompt a runtime permission — it is OAuth-gated.)

#### TM-SRC-14 · Gmail cancel / duplicate account handled — `P2` · negative
- **Preconditions:** Email enabling in progress, or one account already connected.
- **Steps:** 1. Start Gmail connect, then cancel at the account chooser (no account selected). 2. Separately, tap **Add another account** and pick the *already-connected* account. 3. Separately, cancel at the OAuth consent screen.
- **Expected:** No account selected → status "No account selected." Re-picking a connected account → status "<email> is already connected." Cancelling consent → status "Gmail connection cancelled." In all three the connected-account list is unchanged and the source's enabled state is not corrupted.

#### TM-SRC-15 · Disconnect last Gmail account disables source — `P1` · edge
- **Preconditions:** Email ON with exactly one connected account.
- **Steps:** 1. Expand the Email row. 2. Tap **Disconnect** next to the only account. 3. Observe the switch, meta, and hero count.
- **Expected:** `disconnectGmailAccount` removes it; since `remaining` is empty it calls `setSourceEnabled(KEY_EMAIL_ENABLED, false)`. The switch flips OFF, hero count decrements, meta reverts to "Primary inbox, read-only". Toggling Email OFF directly (instead of disconnecting) disconnects **all** accounts (`disconnectAll()`), clears the list and status, and persists OFF.

### 5. Quick-capture surfaces
_Scope: capture entry points feed the same understanding pipeline; capture bypasses the lock but reads stay gated. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/ui/capture/ShareTargetActivity.kt`._

#### TM-CAP-01 · Share text (subject + body merged) → Inbox — `P0` · positive
- **Preconditions:** App installed; pipeline/model ready; an app that shares `text/plain` with both EXTRA_SUBJECT and EXTRA_TEXT (e.g. share a Gmail message, or `adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.SUBJECT "Lunch" --es android.intent.extra.TEXT "Meet Sam Friday 1pm"`).
- **Steps:** 1. Share text to **TaskMind** from the share sheet. 2. Observe the toast. 3. Open TaskMind, unlock if needed, go to Inbox.
- **Expected:** A short toast "Added to TaskMind for review." appears, and the share activity finishes immediately (invisible, translucent). Subject and body are merged as `subject\n\nbody` and enqueued via `CaptureWorker.enqueue(..., "Shared", text)`. A suggestion derived from that text appears in the Inbox after the worker runs.

#### TM-CAP-02 · Share blank/whitespace text → no capture — `P1` · negative
- **Preconditions:** Same as TM-CAP-01.
- **Steps:** 1. Share `text/plain` with empty SUBJECT and empty/whitespace TEXT to TaskMind (e.g. `--es android.intent.extra.TEXT "   "`). 2. Observe.
- **Expected:** `handleText()` returns false (merged text is blank after `filter { it.isNotBlank() }`), so **no toast** is shown and nothing is enqueued. The activity still finishes silently. Inbox gains no new suggestion.

#### TM-CAP-03 · Share image → OCR → Inbox — `P0` · positive
- **Preconditions:** A Tesseract OCR model configured (per Sources note); share an image containing readable text via `image/*` share (e.g. share a screenshot from Gallery to TaskMind).
- **Steps:** 1. Share an image to **TaskMind**. 2. Observe the toast. 3. Open Inbox after the worker completes.
- **Expected:** Toast "Added to TaskMind for review." The image is copied into `cacheDir` as `share_<ts>.img` (read grant held now), then `CaptureWorker.enqueueImage(..., "Shared image", dest)` runs OCR on-device, deletes the temp file, and feeds recognized text to `pipeline.processText("Shared image", ocr)`. A suggestion from the OCR'd text appears in Inbox. (Image branch is taken whenever `intent.type` starts with `image/`.)

#### TM-CAP-04 · Share image that can't be read → no capture — `P1` · negative
- **Preconditions:** A share intent whose `image/*` stream URI is missing or unreadable.
- **Steps:** 1. Trigger an `image/*` SEND to TaskMind with no EXTRA_STREAM (or a revoked URI). 2. Observe.
- **Expected:** `handleImage()` returns false (null URI, or the copy `runCatching` yields false) → **no toast**, nothing enqueued, activity finishes. No crash.

#### TM-CAP-05 · OCR yields empty text → pipeline not invoked — `P2` · edge
- **Preconditions:** Share an `image/*` with NO recognizable text (e.g. a blank or photo-only image).
- **Steps:** 1. Share a textless image to TaskMind. 2. Observe toast and Inbox.
- **Expected:** Toast "Added to TaskMind for review." still shows (handleImage copied the file successfully, so it returns true at the activity level), but in the worker `ocr` is blank, so `if (!text.isNullOrBlank())` is false and `pipeline.processText` is **not** called. No Inbox suggestion is created. (Demonstrates the toast confirms *acceptance*, not that a suggestion will result.)

#### TM-CAP-06 · QS tile opens lock-free quick-add — `P0` · positive
- **Preconditions:** TaskMind QS tile added to the Quick Settings panel; app lock ENABLED with a biometric/credential enrolled; app currently locked/backgrounded.
- **Steps:** 1. Pull down Quick Settings and tap the **TaskMind** tile. 2. Observe what appears. 3. Type "Buy milk", tap **Add**.
- **Expected:** `QuickTileService.onClick` calls `startActivityAndCollapse` → `QuickCaptureActivity` opens **directly to the "Add to TaskMind" dialog with NO biometric prompt** (capture-only, lock-free). Dialog title "Add to TaskMind", field hint "Type a note, task or reminder". Tapping **Add** enqueues via `CaptureWorker.enqueue(..., "Quick add", "Buy milk")`, shows toast "Added to TaskMind for review.", and finishes. The note appears in Inbox only after a separate authenticated launch.

#### TM-CAP-07 · Home-screen widget opens quick-add — `P0` · positive
- **Preconditions:** TaskMind **Quick add** widget placed on the home screen.
- **Steps:** 1. Tap the widget body (or its add button). 2. Observe. 3. Add a note.
- **Expected:** Both `R.id.widget_root` and `R.id.widget_add_button` are wired to the same PendingIntent launching `QuickCaptureActivity` (FLAG_ACTIVITY_NEW_TASK). The lock-free dialog opens with no biometric prompt; adding works identically to TM-CAP-06.

#### TM-CAP-08 · Add disabled until text entered — `P1` · edge
- **Preconditions:** Quick-add dialog open (from tile or widget).
- **Steps:** 1. Leave the field empty and try to tap **Add**. 2. Type only spaces. 3. Type real text.
- **Expected:** The **Add** button is disabled while the field is blank or whitespace-only (`enabled = text.isNotBlank()`); no enqueue or toast occurs. Once non-blank text is present, Add is enabled; the captured value is `text.trim()` (leading/trailing whitespace removed before enqueue).

#### TM-CAP-09 · Cancel / dismiss quick-add captures nothing — `P1` · negative
- **Preconditions:** Quick-add dialog open.
- **Steps:** 1. Type "draft note". 2. Tap **Cancel** (or tap outside the dialog to dismiss). 3. Open Inbox.
- **Expected:** Both **Cancel** and dismiss-by-tap-outside call `finish()` with no enqueue and no toast. No suggestion is created from the typed text.

#### TM-CAP-10 · Capture bypasses lock but Inbox stays gated — `P0` · positive
- **Preconditions:** App lock ENABLED with biometric/credential enrolled; app fully backgrounded (re-locked on ON_STOP).
- **Steps:** 1. Without unlocking, capture via the QS tile (or share text) and add a note. 2. Now launch TaskMind from the launcher icon. 3. Observe.
- **Expected:** Step 1 succeeds with **no auth prompt** (QuickCaptureActivity/ShareTargetActivity are separate activities that never read existing data). Step 2 lands on the **"TaskMind is locked"** LockScreen with an **Unlock** button and an immediate biometric prompt ("Unlock TaskMind" / "Authenticate to access your private data"); the Inbox and its existing suggestions are NOT visible until authentication succeeds. This proves capture writes are lock-free while reads remain gated.

#### TM-CAP-11 · Captured item routes through the same pipeline as live sources — `P1` · positive
- **Preconditions:** App unlocked; use **Settings → Test Extraction (debug)** as the comparison baseline.
- **Steps:** 1. In Settings → Test Extraction, paste "Call the dentist tomorrow at 3" and tap **Run extraction**; note the resulting Inbox suggestion. 2. Separately, capture the identical text via the QS tile quick-add. 3. Compare the two Inbox suggestions.
- **Expected:** Both paths call `pipeline.processText(...)` (Test Extraction directly; quick-add via `CaptureWorker` with source label "Quick add"). The extracted action item is equivalent, confirming capture surfaces are not a parallel/diverging code path. The captured item shows source "Quick add" (vs the test tool's own label).

#### TM-CAP-12 · Capture survives the launching surface finishing — `P2` · edge
- **Preconditions:** Pipeline/model present.
- **Steps:** 1. Share text (or tap Add in quick-add), then immediately swipe the (already-finishing) capture surface away / lock the phone before the model would finish. 2. Later unlock and open Inbox.
- **Expected:** Because capture is enqueued as a **WorkManager** OneTimeWorkRequest (`CaptureWorker`), the on-device understanding runs independently of the finished activity; the suggestion still appears in Inbox. On a transient pipeline error the worker returns `Result.retry()` rather than dropping the capture.

### 6. Understanding / extraction pipeline
_Scope: noise filter, sanitization, thresholds, routing. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/understanding/UnderstandingPipeline.kt`._

> Note: The `MIN_CONFIDENCE` acceptance bar is `0.6`, the noise pre-filter only drops noise when **no** ACTIONABLE_HINT is present, recurrence must normalize to lowercase `daily|weekly|monthly`, and input is truncated at `MAX_INPUT_CHARS = 4000`. Test-Extraction text is passed through with `source` = the debug label (which contains no `" from "` segment), so rejection-penalty does not apply to these cases.

> Note: A blank input is dropped before the LLM (`text.isBlank()` guard), so the Run button on an empty box produces no suggestion — there is no separate validation toast in this file.

#### TM-PIPE-01 · Scheduling text produces a pending suggestion (happy path) — `P0` · positive
- **Preconditions:** App unlocked; Inbox empty; on-device LLM available (default).
- **Steps:** 1. Go to Settings (Privacy tab) -> Test Extraction (debug). 2. Paste: `Dentist appointment on 2026-07-02 at 14:30`. 3. Tap Run. 4. Open the Inbox tab.
- **Expected:** A single pending suggestion appears with a non-blank title (e.g. "Dentist appointment"), dueDate `2026-07-02`, dueTime `14:30`. A single self-updating review notification is posted (`notifier.notifyPending()`). Item is pending only — nothing written to Notes.

#### TM-PIPE-02 · OTP / verification-code text is dropped before the LLM — `P0` · negative
- **Preconditions:** Inbox empty.
- **Steps:** 1. Open Test Extraction. 2. Paste: `Your verification code is 482913. Do not share it with anyone.` 3. Tap Run. 4. Check Inbox.
- **Expected:** No suggestion created (matches both the `(verification…)code` and `do not share` NOISE_PATTERNS, and there is no ACTIONABLE_HINT, so `isLikelyNoise` returns true and `processText` returns early). No new review notification.

#### TM-PIPE-03 · Promo / opt-out marketing text is dropped — `P1` · negative
- **Preconditions:** Inbox empty.
- **Steps:** 1. Open Test Extraction. 2. Paste: `Mega SALE — 50% off everything! Reply STOP to unsubscribe.` 3. Tap Run. 4. Check Inbox.
- **Expected:** No suggestion (matches `% off`, `\bsale\b`, and `reply stop|unsubscribe`; no actionable hint present). Pipeline returns early.

#### TM-PIPE-04 · Invite footer rescues a meeting email past the opt-out filter — `P0` · edge
- **Preconditions:** Inbox empty.
- **Steps:** 1. Open Test Extraction. 2. Paste: `You're invited to a project sync meeting on 2026-07-05 at 10:00. Unsubscribe from these emails here.` 3. Tap Run. 4. Open Inbox.
- **Expected:** Text is NOT pre-filtered (ACTIONABLE_HINTS matches `invit…`/`meeting`, so `isLikelyNoise` is false despite the `unsubscribe` noise pattern); the LLM runs and a pending meeting/reminder suggestion is created with dueDate `2026-07-05`.

#### TM-PIPE-05 · Malformed date is dropped, valid time kept — `P1` · edge
- **Preconditions:** Inbox empty.
- **Steps:** 1. Open Test Extraction. 2. Paste text that nudges the model toward a non-ISO date, e.g. `Submit the report by 07/02/2026 at 9:30 am`. 3. Tap Run. 4. Open the suggestion detail.
- **Expected:** If the model returns a non-`yyyy-MM-dd` `due_date`, `sanitizeDate` returns null so the saved suggestion has **no** dueDate; a `9:30`-style `due_time` matching `\d{1,2}:\d{2}` is kept as dueTime. (The suggestion is still created as long as title is non-blank and confidence ≥ 0.6.)

#### TM-PIPE-06 · Datetime stuffed into due_time is dropped — `P2` · edge
- **Preconditions:** Inbox empty.
- **Steps:** 1. Open Test Extraction. 2. Paste: `Standup is scheduled for 2026-07-03T09:00`. 3. Tap Run. 4. Open the suggestion detail.
- **Expected:** `sanitizeTime` requires the whole value to match `\d{1,2}:\d{2}` (uses `matches`, not `containsMatchIn`), so a `2026-07-03T09:00` due_time fails and dueTime is null. The `yyyy-MM-dd` date portion, if returned in due_date, is kept.

#### TM-PIPE-07 · Recurrence normalization to lowercase daily/weekly/monthly — `P1` · edge
- **Preconditions:** Inbox empty.
- **Steps:** 1. Open Test Extraction. 2. Paste: `Take vitamin D every day at 8:00`. 3. Tap Run. 4. Open the suggestion and inspect repeat.
- **Expected:** If the model emits `Daily`/`DAILY`, `sanitizeRecurrence` lowercases and accepts it -> recurrence `daily`. A model value outside `{daily, weekly, monthly}` (e.g. `every 2 days`, `yearly`) is normalized to null (no repeat).

#### TM-PIPE-08 · Low-confidence item is rejected by the acceptance bar — `P1` · negative
- **Preconditions:** Inbox empty.
- **Steps:** 1. Open Test Extraction. 2. Paste vague, non-committal text, e.g. `maybe we could possibly hang out sometime, not sure`. 3. Tap Run. 4. Check Inbox.
- **Expected:** If the model's `confidence` for the item is `< 0.6` (`MIN_CONFIDENCE`), `isAcceptable` is false and no suggestion is inserted. A blank-title item is also rejected. No review notification if nothing inserted.

#### TM-PIPE-09 · (title, dueDate) dedup vs pending suggestions — `P0` · edge
- **Preconditions:** Inbox empty.
- **Steps:** 1. Open Test Extraction, paste `Pay rent on 2026-07-01`, Run (creates one pending item). 2. Without approving/rejecting, open Test Extraction again, paste the same `Pay rent on 2026-07-01`, Run. 3. Open Inbox.
- **Expected:** Only ONE "Pay rent" pending suggestion exists; the second run is suppressed because `isDuplicate` matches title+dueDate against existing pending suggestions. (Dedup compares the raw model `item.dueDate`, before sanitization.)

#### TM-PIPE-10 · (title, dueDate) dedup vs approved Notes — `P1` · edge
- **Preconditions:** Inbox empty; Notes empty.
- **Steps:** 1. Create a pending item via Test Extraction (`Call plumber on 2026-07-04`), then Approve it in the Inbox so it becomes a Note. 2. Open Test Extraction, paste `Call plumber on 2026-07-04` again, Run. 3. Check Inbox.
- **Expected:** No new pending suggestion; `isDuplicate` also checks against `dao.getAllNotes()` (`pending + notes`), so the already-approved note blocks the duplicate.

#### TM-PIPE-11 · Same title, different dueDate is NOT a duplicate — `P2` · edge
- **Preconditions:** One pending "Team meeting" with dueDate `2026-07-05` exists.
- **Steps:** 1. Open Test Extraction, paste `Team meeting on 2026-07-12`, Run. 2. Open Inbox.
- **Expected:** A second "Team meeting" suggestion is created (dueDate `2026-07-12`); dedup keys on the (title, dueDate) pair, so a differing date is distinct.

#### TM-PIPE-12 · Location extraction is captured and blank-trimmed to null — `P2` · positive
- **Preconditions:** Inbox empty.
- **Steps:** 1. Open Test Extraction. 2. Paste: `Pick up package at the Westend Post Office on 2026-07-06`. 3. Tap Run. 4. Open the suggestion detail.
- **Expected:** The `location` field is populated (trimmed) when the model returns one; if the model returns blank/whitespace, `location?.trim()?.ifBlank { null }` stores null rather than an empty string. `summary` is the trimmed `notes`.

#### TM-PIPE-13 · Oversized input is truncated to 4000 chars — `P2` · boundary
- **Preconditions:** Inbox empty.
- **Steps:** 1. Open Test Extraction. 2. Paste a >4000-char block whose actionable instruction (e.g. `Email Sara on 2026-07-08`) sits in the **first** 4000 chars, padded with filler afterward. 3. Tap Run. 4. Check Inbox.
- **Expected:** Pipeline truncates the body to `text.take(4000)` before the prompt; the early actionable line is still extracted. (If the actionable line is placed beyond char 4000, it is cut and not extracted — useful contrast run.) The saved `rawSnippet` retains the full original text.

#### TM-PIPE-14 · On-device-default routing (no cloud key) — `P0` · positive
- **Preconditions:** Settings -> "Use on-device LLM" = ON (default); cloud API key empty; on-device model available.
- **Steps:** 1. Open Test Extraction, paste `Renew passport on 2026-07-09`, Run. 2. Monitor the Egress/Privacy audit log.
- **Expected:** Extraction succeeds via on-device path; **no** egress record to `generativelanguage.googleapis.com` is written (`egressLogger.record` is only called in `CloudLlmProvider.generate`). Data stays on device.

#### TM-PIPE-15 · On-device unavailable + no key returns empty (no crash, no suggestion) — `P1` · negative
- **Preconditions:** "Use on-device LLM" = ON but the on-device model is not downloaded/unsupported; cloud API key empty.
- **Steps:** 1. Open Test Extraction, paste any actionable text, Run. 2. Check Inbox.
- **Expected:** `onDevice.generate` throws; with `llmApiKey` blank the router returns `{"items": []}`, which parses to zero items -> no suggestion, no notification, no crash.

#### TM-PIPE-16 · Cloud routing path logs egress (toggle off / fallback) — `P1` · positive
- **Preconditions:** "Use on-device LLM" = OFF (or on-device unavailable AND a valid cloud key configured); valid Gemini key set.
- **Steps:** 1. Open Test Extraction, paste `Book flight on 2026-07-10`, Run. 2. Open the Egress/Privacy audit log.
- **Expected:** One egress entry recorded: host `generativelanguage.googleapis.com`, reason `Cloud LLM extraction` (metadata only, no content). Suggestion created from the Gemini schema-constrained JSON.

#### TM-PIPE-17 · "Call back" dedup for repeated missed-call triggers — `P0` · edge
- **Preconditions:** Inbox empty; ability to trigger `addCallback` twice for the same caller (e.g. a chat app re-posting the same missed-call notification, or two call-log rescans).
- **Steps:** 1. Trigger a missed call from "Amma" (e.g. emulator/adb missed-call notification, or repost the chat-app missed-call notification twice in quick succession). 2. Open Inbox.
- **Expected:** Exactly ONE "Call back Amma" pending card. The `callbackMutex` serializes check-then-insert and `isDuplicate(title, null, pending+notes)` blocks the twin; confidence is `0.95`, type `todo`, dueDate/dueTime null.

#### TM-PIPE-18 · Missed call with no usable name or number is ignored — `P2` · negative
- **Preconditions:** Inbox empty.
- **Steps:** 1. Trigger `addCallback` with a displayName that is an email (`you@gmail.com`) and no number (e.g. a service missed-call notification titled with the account email). 2. Check Inbox.
- **Expected:** No "Call back" card; the name is rejected because it contains `@`, and there is no dialable number (`< 5` digits / null), so `addCallback` returns before inserting. A bare number with fewer than 5 digits is likewise rejected.

#### TM-PIPE-19 · Prose reply triggers a single JSON retry — `P2` · edge
- **Preconditions:** On-device model that occasionally answers in prose (best-effort to observe).
- **Steps:** 1. Open Test Extraction, paste actionable text likely to elicit a chatty reply. 2. Tap Run once.
- **Expected:** If the first `generate` output fails `tryParse` (after `stripJsonFences`), the pipeline re-asks once with `"Return ONLY the JSON object described above, nothing else."`. If the retry also fails to parse, `items` is null and nothing is inserted (no crash, no duplicate notification).

### 7. Inbox triage
_Scope: the approval gate — review, approve, reject, snooze, edit, undo, filter, bulk, noise-sweep, time-picker-on-approve, voice/empty/skeleton states, and notification quick-actions. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/ui/inbox/InboxScreen.kt`._

> Note: The suite scope asks for "filter by type/source" and a "loading skeleton" + per-card "confidence chip". The code shows a confidence chip (`BoldConfidenceChip`) and a skeleton, but there is **no filter-by-type/source UI** anywhere in `InboxScreen.kt` (the overflow menu only has Add item / Refresh / Keep all / Dismiss all). Filter cases below are reframed as the only built-in triage filter that exists: the confidence-driven noise sweep (`confidence < 0.5`). 

> Note: Snooze labels in code are **"In 1 hour" / "This evening" / "Tomorrow"** (not "1 hour / this evening / tomorrow"). "This evening" resolves to 18:00 today if before 18:00, else 18:00 tomorrow; "Tomorrow" is 09:00 next day. Undo snackbar texts are **"Kept" / "Dismissed" / "Snoozed" / "N noise item(s) swept"** — not longer sentences. There is no per-card "filter" control.

#### TM-INBOX-01 · Card anatomy: source pill, confidence chip, kind dot/chip, due detail — `P0` · positive
- **Preconditions:** App unlocked, on Inbox tab; at least one pending suggestion exists (use Settings → Test Extraction with text like "Pay electricity bill tomorrow 6pm" to seed one).
- **Steps:** 1. Open the Inbox tab. 2. Observe the top card without tapping it.
- **Expected:** Card shows, in order: a meta row with the source pill (text = `suggestion.source`, e.g. "Manual entry") and a confidence chip (`BoldConfidenceChip` reflecting `suggestion.confidence`) on the left, a kind dot pinned right; below that the `extractedTitle`; then a kind chip + a mono due-detail line. Due-detail reads `dueDate · dueTime` when both present (e.g. "<date> · 18:00"), the date alone if no time, or **"no date set"** when `dueDate` is null/blank. Header shows the pending `count` over the label **"TO REVIEW"** and eyebrow **"Today · On-device"**.

#### TM-INBOX-02 · Tap title to expand full original + actions — `P0` · positive
- **Preconditions:** A pending card is visible whose `summary`/`rawSnippet` is non-empty.
- **Steps:** 1. Tap the card title text. 2. Observe the expanded region. 3. Tap the title again.
- **Expected:** On first tap the card expands (animated): a divider, the original text shown in italic quotes `"<summary or rawSnippet>"` (uses `summary`, falling back to `rawSnippet` when summary is blank), three kind-picker chips (Task / Reminder / Note with the current type highlighted in accent), a Dismiss + Keep button row, and a footer action row (Snooze, optionally Call/Directions, Edit). Tapping the title again collapses it. Only the title is the tap target — tapping elsewhere on the collapsed card does nothing.

#### TM-INBOX-03 · Approve a no-date / timed item via Keep button → "Kept" + Undo — `P0` · positive
- **Preconditions:** A pending suggestion that is either undated (`dueDate == null`) OR a `reminder` already having a `dueTime` (so the time-picker is NOT triggered). Seed via Test Extraction with "Buy milk" (undated).
- **Steps:** 1. Expand the card. 2. Tap **Keep**.
- **Expected:** Card disappears from the pending list (status → "approved", a Note is created via `SuggestionApprover.approve`). A snackbar shows exactly **"Kept"** with an **Undo** action. Pending count in the header decrements by 1.

#### TM-INBOX-04 · Approve a dated item with no time → time-picker dialog (Set time vs Keep as all-day) — `P0` · positive/edge
- **Preconditions:** A pending suggestion with `dueDate != null` AND `dueTime == null`. Seed via Test Extraction with "Dentist appointment on <a date>" (no time).
- **Steps:** 1. Tap **Keep** (or swipe-right) on the dated, time-less card. 2. In the **"Set a time?"** dialog, first tap **Set time** with the picker left at its default (09:00). (Repeat the setup for the second branch and tap **Keep as all-day** instead.)
- **Expected:** Approving does NOT immediately complete; the `ApproveTimePickerDialog` appears titled **"Set a time?"** with body "No time was detected…", a time input defaulting to 9:00, **Set time** and **Keep as all-day** buttons. **Set time** approves a copy with `dueTime="09:00"` and `type="reminder"` (so it schedules an alarm + timed calendar event), shows **"Kept"** + Undo. **Keep as all-day** approves the suggestion unchanged (all-day calendar entry, no alarm), shows **"Kept"** + Undo. Dismissing the dialog (tap outside) cancels and leaves the item pending.

#### TM-INBOX-05 · Reject via Dismiss button → "Dismissed" + Undo, learns rejection — `P0` · positive
- **Preconditions:** A pending card visible and expanded.
- **Steps:** 1. Tap **Dismiss**.
- **Expected:** Card leaves the pending list (status → "rejected") and `RejectionLearner.recordRejection` is invoked (sender down-ranked). Snackbar shows exactly **"Dismissed"** with **Undo**. Count decrements by 1.

#### TM-INBOX-06 · Swipe-right keep / swipe-left dismiss with colored reveal + haptic — `P1` · positive
- **Preconditions:** At least two pending undated cards.
- **Steps:** 1. Slowly drag a card to the right and observe the revealed background, then release. 2. On another card, drag left and observe, then release.
- **Expected:** Right drag reveals a keep background (`keepBg`) with a left-aligned accent ✓ and **"Keep"** label; on release a LongPress haptic fires and the item is approved → **"Kept"** snackbar (or the time-picker if it's a dated/time-less item). Left drag reveals a skip background (`skipBg`) with a right-aligned **"Dismiss"** + ✗; on release a haptic fires and the item is rejected → **"Dismissed"**. The card itself never stays off-screen (confirmValueChange returns false — the row is removed by data change, not by the swipe settling).

#### TM-INBOX-07 · Snooze options and labels; snoozed item auto-resurfaces — `P1` · positive/edge
- **Preconditions:** One pending undated card; device clock note current hour.
- **Steps:** 1. Expand the card, tap **Snooze**, observe the dropdown options. 2. Pick **In 1 hour**. 3. (Separately) snooze another item and wait past its time (or set a near-future target).
- **Expected:** Dropdown lists exactly three options in order: **In 1 hour**, **This evening**, **Tomorrow**. Choosing one hides the card (sets `snoozedUntil`) and shows snackbar **"Snoozed"** + Undo. "This evening" targets 18:00 today if the current hour < 18, otherwise 18:00 tomorrow; "Tomorrow" targets 09:00 next day. A snoozed item reappears in the list automatically within ~30s of its `snoozedUntil` passing (the 30s nowTicker re-filters).

#### TM-INBOX-08 · Undo restores the exact action — `P0` · positive
- **Preconditions:** Perform an approve, then separately a reject, then separately a snooze (one at a time).
- **Steps:** 1. Approve a card; while the **"Kept"** snackbar is visible tap **Undo**. 2. Repeat: reject → tap Undo on **"Dismissed"**. 3. Repeat: snooze → tap Undo on **"Snoozed"**.
- **Expected:** Undo on approve re-pends the suggestion and deletes the just-created Note (item returns to Inbox; no orphan note/reminder). Undo on reject re-pends it (back to status "pending", `snoozedUntil` cleared). Undo on snooze clears `snoozedUntil` so it shows immediately. In every case the item reappears in the list. (Per code, the rejection-learning increment/decrement is deliberately NOT rolled back — acceptable.)

#### TM-INBOX-09 · Inline edit of title/date/time → Save persists, Cancel discards — `P1` · positive
- **Preconditions:** A pending card, expanded.
- **Steps:** 1. Tap **Edit** in the footer. 2. Change the **Title**, set **Date (YYYY-MM-DD)** and **Time (HH:MM)** fields. 3. Tap **Save**. 4. Re-open another card's edit, change a field, tap **Cancel**.
- **Expected:** Edit mode replaces the card body with "From <source>", a Title field, and Date/Time fields prefilled from the current values. **Save** writes the edited copy (blank date/time fields become null) and exits edit mode; the card now reflects the new title/date/time. **Cancel** exits edit mode with no change persisted. The item stays pending (editing does not approve it).

#### TM-INBOX-10 · Reassign kind via picker chips — `P1` · positive
- **Preconditions:** A pending card, expanded.
- **Steps:** 1. Note which of Task/Reminder/Note is highlighted. 2. Tap a different kind chip (e.g. **Reminder**).
- **Expected:** Tapping a chip immediately persists `type` ("todo"/"reminder"/"note") via `updateSuggestion`; the tapped chip becomes accent-highlighted and the card's kind dot/chip update accordingly. The item remains pending. (Changing to "reminder" only schedules an alarm later, at approve time, and only if a `dueTime` exists.)

#### TM-INBOX-11 · Noise-sweep banner appears for low-confidence items and SWEEP dismisses them — `P1` · positive/boundary
- **Preconditions:** Seed several pending suggestions, at least one with `confidence < 0.5` and at least one with `confidence >= 0.5`. (Low-confidence/noisy text via Test Extraction, e.g. promotional spam.)
- **Steps:** 1. Open Inbox; observe the banner above the cards. 2. Tap the banner (**SWEEP →**).
- **Expected:** A banner shows "<N> likely noise" (bold) + " detected", where N = count of pending with `confidence < 0.5`; the banner is hidden when N == 0. Tapping it rejects exactly those N items (status → "rejected", each recorded as a rejection) and shows snackbar **"<N> noise item swept"** (singular) or **"<N> noise items swept"** (plural) with **Undo**. Items with `confidence >= 0.5` remain. The boundary is strict `< 0.5`: an item at exactly 0.5 is NOT swept and does NOT count toward N.

#### TM-INBOX-12 · Keep-all (bulk) confirmation dialog — `P1` · positive
- **Preconditions:** Multiple pending suggestions.
- **Steps:** 1. Tap the header overflow (⋮) → **Keep all** (or "Keep all" in the dialog). 2. In the dialog read the title/body, then tap **Keep all**.
- **Expected:** Dialog titled **"Keep all <N>?"** (N = current pending size) with body "This saves every pending suggestion and schedules any reminders/calendar events." **Keep all** approves every pending item (each through `SuggestionApprover.approve`), the list becomes empty → "All clear." state. **No Undo snackbar** appears (bulk approve sets `lastUndo = null`). **Cancel** closes the dialog with nothing changed.

#### TM-INBOX-13 · Dismiss-all (bulk) rejects everything, no undo — `P1` · negative
- **Preconditions:** Multiple pending suggestions.
- **Steps:** 1. Open header overflow (⋮) → **Dismiss all**.
- **Expected:** Every pending item is rejected immediately (no confirmation dialog — unlike Keep all), each recorded via `RejectionLearner`. List empties to the "All clear." empty state. No Undo snackbar (bulk reject clears `lastUndo`).

#### TM-INBOX-14 · Empty state, skeleton, and refresh — `P1` · edge
- **Preconditions:** (a) Inbox with zero pending items for the empty case; (b) cold app start to catch the skeleton.
- **Steps:** 1. Cold-launch and watch the Inbox for the first frame before data loads. 2. With no pending items, view the empty state. 3. Tap **Refresh** in the empty state.
- **Expected:** Before the first DB result arrives (`pendingSuggestions == null`) a `SkeletonList` placeholder shows (not the empty state). When the list is empty (non-null, size 0) the empty state shows the sparkle icon, title **"All clear."**, the "You've triaged everything…" body, and **Refresh** / **Add item** pill buttons. Tapping **Refresh** runs `scanner.scanIncremental()`; while running the button label reads **"Refreshing…"** and the overflow spinner shows.

#### TM-INBOX-15 · Voice-capture FAB error/empty snackbars — `P2` · negative/edge
- **Preconditions:** App on Inbox tab. Test variants: (a) RECORD_AUDIO permission previously denied; (b) permission granted but no offline voice model installed; (c) permission granted, model present, but recorder produces no file (e.g. tap Stop instantly).
- **Steps:** 1. Tap the mic FAB; if prompted, deny the permission (variant a). 2. Grant permission, tap FAB, tap **Stop** immediately in the "Listening…" dialog (variant c). 3. With no Vosk model installed, record a short phrase and tap Stop (variant b).
- **Expected:** (a) Denying the runtime permission shows **"Microphone permission is needed for voice input."**; if `recorder.start()` itself fails, **"Couldn't start recording."**. The recording dialog is titled **"Listening…"** with Stop/Cancel. (c) If `recorder.stop()` returns no file, snackbar **"Didn't catch that — please try again."**. (b) With no model present, after Stop the snackbar reads **"Add an offline voice model in Settings to use voice input."**; a successful transcription instead yields **"Added to your inbox for review."** and the FAB shows a spinner while processing. The temp recording file is always deleted.

#### TM-INBOX-16 · Manual "Add an item" routes through the live pipeline — `P2` · positive
- **Preconditions:** Inbox visible.
- **Steps:** 1. Open overflow (⋮) → **Add item** (or the empty-state **Add item**). 2. Type a line in **"Type a note, task or reminder"**. 3. Tap **Add**.
- **Expected:** The **Add** button is disabled while the field is blank. On Add, the dialog closes and the trimmed text is processed via `UnderstandingPipeline.processText("Manual entry", …)`; on success a snackbar reads **"Added to your inbox for review."** and a new pending card with source pill "Manual entry" appears after extraction; on failure **"Couldn't add that — please try again."**. **Cancel** clears the field and closes with no entry added.

#### TM-INBOX-17 · Approve / Reject straight from the review notification — `P1` · positive
- **Preconditions:** At least one pending suggestion so the review notification (id 42) is posted. Trigger `SuggestionNotifier.notifyPending()` by running an extraction (Test Extraction) and pulling down the shade. (Or `adb shell am broadcast -a com.rajasudhan.taskmind.action.APPROVE --ei suggestion_id <id> -n com.rajasudhan.taskmind/.data.source.NotificationActionReceiver`.)
- **Steps:** 1. Expand the TaskMind notification; note title ("1 suggestion to review" or "N suggestions to review") and body (top item's `extractedTitle`). 2. Tap **Approve**. 3. Re-trigger with multiple pending and tap **Reject** on the top item.
- **Expected:** Tapping **Approve** approves the top pending suggestion through the same `SuggestionApprover.approve` path (Note created, alarm/calendar if dated+timed) — without opening the app. Tapping **Reject** sets it rejected and records the rejection. After either action the single notification (NOTIFICATION_ID 42) refreshes to the next pending item (count title updates); when none remain the notification is cancelled. An already-approved/rejected id is ignored (guarded by `status == "pending"`). Tapping the notification body opens the app on the Inbox.

- Notes list: eyebrow shows `"${counts["all"] ?: 0} kept items"`; chips All/Tasks/Reminders/Notes have counts, "Done" has NO count; All selected only when `kindFilter==null && !showCompleted`; selecting Done clears kindFilter; selecting a kind clears showCompleted; checklist card only shows complete-square when NOT a checklist and type is todo/reminder; prioritise sorts reminder→todo→note then by due; empty states have 3 variants; skeleton when `notes==null`.
- Note detail: "Note not found." only renders when `n==null && !deleting`; Call button via `resolveCallNumber`; recurrence dropdown only for type=="reminder", options None/Daily/Weekly/Monthly; checklist derived for todo if list-like; location dialog → "Use current location" → pendingLabel → permission → `setLocationReminder`; map + 150m geofence circle; Get directions; Remove clears location.

### 8. Rejection learning
_Scope: down-rank repeatedly-rejected senders. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/RejectionLearner.kt`._

> Note: Exact constants from code — `REJECT_THRESHOLD = 3`, `PENALTY = 0.3`. The penalty is applied as `confidence - 0.3` (coerced ≥ 0) **only when** the stored reject count is `>= 3`. One approval forgives exactly one rejection (`countAfterApproval = (count-1) coerced ≥ 0`), and the row is deleted at zero. `senderKey` keys off the substring after `" from "` (lowercased, trimmed); sources without `" from "` return null and are never penalized.

> Note: The penalty reduces an item's confidence and can push it below the `MIN_CONFIDENCE = 0.6` acceptance bar — that is how a penalized sender's borderline items stop reaching the Inbox. A high-confidence item (e.g. 0.95) survives the 0.3 penalty (0.65 ≥ 0.6) and still appears, just down-ranked.

#### TM-REJ-01 · First and second rejection apply no penalty — `P0` · positive
- **Preconditions:** No `RejectedPattern` row for the test sender. Use a Test-Extraction `source` containing a sender, e.g. set/simulate source `SMS from +15551234567` (or reject two real items from the same sender).
- **Steps:** 1. Create a suggestion from sender "SMS from +15551234567" and Reject it (count -> 1). 2. Create another from the same sender and Reject it (count -> 2). 3. Create a third borderline item (confidence ~0.62) from that sender and observe.
- **Expected:** After rejections 1 and 2, `confidencePenalty` returns `0.0` (count `< 3`), so the third item's confidence is unchanged and it still appears in the Inbox.

#### TM-REJ-02 · Threshold (3) reached → penalty on future items — `P0` · edge
- **Preconditions:** Sender "SMS from +15551234567" with no prior row.
- **Steps:** 1. Reject three items from that sender (count -> 3). 2. Create a new item from the same sender whose model confidence is ~0.62 (just above the bar). 3. Check Inbox.
- **Expected:** `confidencePenalty` now returns `0.3` (count `>= 3`); scored confidence = `0.62 - 0.3 = 0.32`, which is `< 0.6`, so `isAcceptable` is false and the item is NOT inserted. The penalty applies to that sender's future items only.

#### TM-REJ-03 · High-confidence item survives the penalty but is down-ranked — `P1` · edge
- **Preconditions:** Sender at count `>= 3` (penalty active).
- **Steps:** 1. From the penalized sender, create a strong item (model confidence ~0.95). 2. Open Inbox and inspect the stored confidence.
- **Expected:** Scored confidence = `0.95 - 0.3 = 0.65`, still `>= 0.6`, so the suggestion IS inserted with stored confidence `0.65` (down-ranked, not blocked — penalty is soft).

#### TM-REJ-04 · Penalty is sender-scoped, not global — `P1` · negative
- **Preconditions:** Sender A ("SMS from +15551111111") at count `>= 3`; Sender B ("SMS from +15552222222") with no row.
- **Steps:** 1. Create a borderline (~0.62) item from Sender B. 2. Check Inbox.
- **Expected:** Sender B's `confidencePenalty` is `0.0` (its own count is 0); the 0.62 item is accepted. Sender A's penalty does not affect Sender B.

#### TM-REJ-05 · One approval decrements the count and lifts the penalty below threshold — `P0` · edge
- **Preconditions:** Sender at count `= 3` (penalty active).
- **Steps:** 1. Create an item from that sender strong enough to appear despite the penalty (≥0.95) and Approve it -> `recordApproval` sets count to `countAfterApproval(3) = 2`. 2. Now create a borderline (~0.62) item from the same sender. 3. Check Inbox.
- **Expected:** With count back to `2` (`< 3`), `confidencePenalty` returns `0.0`; the 0.62 item is no longer penalized and IS accepted. Penalty lifts as soon as the count drops below 3.

#### TM-REJ-06 · Row removed at zero (count walks back to 0) — `P1` · edge
- **Preconditions:** Sender with count `= 1` (one prior rejection).
- **Steps:** 1. Create and Approve an item from that sender -> `countAfterApproval(1) = 0`. 2. Inspect the rejection state for that sender (re-run a borderline item or check DB/debug).
- **Expected:** Because `next <= 0`, the row is deleted (`deleteRejectedPattern`), not stored as 0. Subsequent `confidencePenalty` for that sender returns `0.0` (no row). Approving a sender that has no row is a no-op (`recordApproval` returns early when `existing == null`).

#### TM-REJ-07 · Approval never drives the count negative — `P2` · boundary
- **Preconditions:** Sender with count `= 0`/no row (or just deleted).
- **Steps:** 1. Approve another item from that sender.
- **Expected:** `recordApproval` returns early because there is no existing row (and `countAfterApproval` is `coerceAtLeast(0)` regardless), so no negative count is ever written; state stays "no row / penalty 0.0".

#### TM-REJ-08 · Recordings / Screenshots are never penalized (no `" from "`) — `P0` · negative
- **Preconditions:** Push a file into the Recordings MediaStore folder (e.g. `adb push` into the watched Recordings dir) so a suggestion with source like `Recording: <file>` is created.
- **Steps:** 1. Reject suggestions from a `Recording:` (or `Screenshot:`) source three or more times. 2. Create another suggestion from a `Recording:` source.
- **Expected:** `senderKey("Recording: …")` returns null (no `" from "` segment), so `recordRejection` is a no-op and `confidencePenalty` is always `0.0`. No down-ranking ever applies; the new item is unaffected.

#### TM-REJ-09 · App-Usage / Manual / Call-Log / Shared sources are never penalized — `P1` · negative
- **Preconditions:** Generate suggestions from sources lacking `" from "`, e.g. `App Usage`, `Manual entry`, `Call Log`, `Shared`.
- **Steps:** 1. Reject 3+ items from one such source. 2. Create another item from the same source.
- **Expected:** `senderKey` returns null for each (no `" from "`), so the rejection count is never recorded and no penalty is ever applied. These sources are exempt by design.

#### TM-REJ-10 · Sender key is case-insensitive and trimmed — `P2` · edge
- **Preconditions:** No row for the sender.
- **Steps:** 1. Reject 3 items whose source is `Notification from WhatsApp` (count -> 3). 2. Create a borderline item whose source is `Notification from  whatsapp ` (different case / extra spaces).
- **Expected:** Both resolve to the same key `whatsapp` (lowercased + trimmed after `" from "`), so the existing count of 3 applies and the borderline item is penalized. Sender identity is normalized by case and surrounding whitespace.

#### TM-REJ-11 · Source with a trailing/blank sender after "from" is treated as no-sender — `P2` · boundary
- **Preconditions:** No row.
- **Steps:** 1. Trigger an item whose source ends in `… from ` (blank after the segment, e.g. `Email () from `). 2. Reject it 3+ times. 3. Create another from the same source.
- **Expected:** `senderKey` returns null because the substring after `" from "` is blank (`ifBlank { null }`), so the source is never penalized — same exemption as the no-sender sources.

- `D:\SMK\Android_apps\apps\taskmind\src\main\java\com\rajasudhan\taskmind\data\source\understanding\UnderstandingPipeline.kt`
- `D:\SMK\Android_apps\apps\taskmind\src\main\java\com\rajasudhan\taskmind\data\source\understanding\ExtractionHeuristics.kt`
- `D:\SMK\Android_apps\apps\taskmind\src\main\java\com\rajasudhan\taskmind\data\source\understanding\RoutingLlmProvider.kt`
- `D:\SMK\Android_apps\apps\taskmind\src\main\java\com\rajasudhan\taskmind\data\source\understanding\CloudLlmProvider.kt`
- `D:\SMK\Android_apps\apps\taskmind\src\main\java\com\rajasudhan\taskmind\data\source\RejectionLearner.kt`

### 9. Notes list
_Scope: kept items list with color-coded cards, kind filters, search, complete/checklist toggles, delete, empty/loading states. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/ui/notes/NotesViewModel.kt`._

#### TM-NOTES-01 · Header count + color-coded cards render — `P0` · positive
- **Preconditions:** At least one approved item of each type exists — a `todo`, a `reminder` with no due date, a `reminder` with a due date, and a `note`. (Approve a few via Settings → Test Extraction or pre-seed the Inbox, then Keep them.)
- **Steps:** 1. Open the **Notes** tab. 2. Read the eyebrow line above the "Notes" title. 3. Inspect the kind dot + chip on each card.
- **Expected:** Eyebrow reads "**N KEPT ITEMS**" (uppercased) where N = total active notes. Each card shows a colored square dot + a soft-background chip labelled per `boldKindFor`: `todo`→**Task**, `note`→**Note**, `reminder` **with** a due date→**Event**, `reminder` **without** a due date→**Reminder**. Cards with a `dueDate` also show "`dueDate dueTime`" meta in the header row.

#### TM-NOTES-02 · Filter chips show per-kind counts and switch the list — `P0` · positive
- **Preconditions:** Active items spanning ≥2 kinds (e.g. 2 todos, 1 reminder, 1 note).
- **Steps:** 1. Open Notes. 2. Note the trailing count on each of **All / Tasks / Reminders / Notes**. 3. Tap **Tasks**.
- **Expected:** All=total active, Tasks=count of `type=="todo"`, Reminders=`type=="reminder"`, Notes=`type=="note"` (counts come from `kindCounts` over active notes only). The **Done** chip shows **no** count number. After tapping Tasks, only todo cards remain and the **Tasks** chip is highlighted (accent background, on-accent text); All is no longer highlighted.

#### TM-NOTES-03 · "All" selection state is exclusive with Done — `P1` · positive
- **Preconditions:** Both active and completed items exist.
- **Steps:** 1. Open Notes (All is selected by default). 2. Tap **Done**. 3. Tap **All**.
- **Expected:** On launch **All** is highlighted (since `kindFilter==null && !showCompleted`). Tapping **Done** highlights Done, un-highlights All, and shows completed items only. Tapping **All** returns to the active list and re-highlights All. Selecting **Done** also clears any kind filter (`setShowCompleted(true)` resets `kindFilter`); selecting a kind clears the Done view (`setKindFilter` resets `showCompleted`).

#### TM-NOTES-04 · Active list ordering (important first) — `P1` · positive
- **Preconditions:** Active set containing a `note`, a `todo` with a due date, a `reminder` due tomorrow, and a `reminder` due today (earlier). Use Test Extraction to create dated reminders.
- **Steps:** 1. Open Notes with **All** selected.
- **Expected:** Order is reminders → todos → notes (`typeRank` 0/1/2); within a type, soonest `dueDate`+`dueTime` first; items with no/invalid due date sort last (`dueSortKey` = `Long.MAX_VALUE`). So today's reminder precedes tomorrow's reminder, both precede the dated todo, which precedes the note.

#### TM-NOTES-05 · Full-text search filters within the current segment — `P0` · positive
- **Preconditions:** An active item whose title/body contains a distinctive word (e.g. "dentist") and at least one item that does not.
- **Steps:** 1. Open Notes. 2. Type "dentist" into the **Search everything kept…** field.
- **Expected:** List narrows to rows matching `%dentist%` via `searchNotes`, filtered to the current segment (`completed==false` while on the active view) and current kind filter. Active results are re-prioritised (reminders→todos→notes). Clearing the box restores the full active list.

#### TM-NOTES-06 · Complete-toggle moves an item to Completed — `P0` · positive
- **Preconditions:** An active `todo` or `reminder` **without** an inline checklist (so the leading check-square is shown — it only appears when `!hasChecklist && completable`).
- **Steps:** 1. On the active list, tap the square checkbox to the left of the item title. 2. Tap the **Done** filter chip.
- **Expected:** The item disappears from the active list immediately (animated). Its title now renders strikethrough in muted ink. Under **Done** the item appears (most-recently-completed first). `setCompleted` writes `completed=true` and a `completedAt` timestamp. Toggling it back (square in Done view) returns it to the active list with `completedAt=null`.

#### TM-NOTES-07 · Inline checklist toggle from the card persists — `P1` · positive
- **Preconditions:** An item whose `checklist` decodes to ≥1 item (e.g. a list-like todo "Milk, eggs, bread" kept from the Inbox).
- **Steps:** 1. On the card, tap one checklist row (or its small square). 2. Navigate away and back to Notes.
- **Expected:** That row's square fills (accent + check) and its text goes strikethrough/muted; other rows unchanged. Persists via `updateNoteChecklist` (`Checklist.toggleEncoded`). A card with a checklist shows **no** top-level complete square (the per-item checkboxes replace it).

> Note: For a checklist card, `completable` is irrelevant — the leading whole-note complete square is suppressed whenever `hasChecklist`, so there is no card-level "mark complete" on checklist items; completion of a checklist note is only reachable from the detail screen.

#### TM-NOTES-08 · Delete from the list row — `P1` · positive
- **Preconditions:** ≥1 active item.
- **Steps:** 1. Tap the trash (outline) icon at the top-right of a card. 
- **Expected:** The card animates out and the row is hard-deleted (`deleteNote` → `dao.deleteNote`). The eyebrow "N kept items" and the chip counts decrement by 1. No confirmation dialog is shown (delete is immediate from the list).

#### TM-NOTES-09 · Empty state — search variant — `P1` · edge
- **Preconditions:** Items exist, but none match a nonsense query.
- **Steps:** 1. Open Notes. 2. Type "zzzqqq".
- **Expected:** Centered empty state with a SearchOff glyph, title "**Nothing matches that.**", subtitle "**Try a different word.**" (chosen because `query.isNotBlank()`), regardless of which segment/kind is active.

#### TM-NOTES-10 · Empty state — Completed variant — `P2` · edge
- **Preconditions:** No completed items (and search box empty).
- **Steps:** 1. Open Notes. 2. Tap **Done**.
- **Expected:** Empty state title "**Nothing completed yet.**", subtitle "**Items you tick off collect here.**" (chosen because `showCompleted && query.isBlank()`).

#### TM-NOTES-11 · Empty state — no items variant — `P2` · edge
- **Preconditions:** Database has zero kept items; search empty; Done not selected.
- **Steps:** 1. Open Notes on a fresh install (or after deleting all items).
- **Expected:** Title "**No items yet.**", subtitle "**Keep suggestions in the Inbox and they land here.**" Eyebrow reads "**0 KEPT ITEMS**".

#### TM-NOTES-12 · Loading skeleton before first emission — `P2` · edge
- **Preconditions:** Cold open with biometric/DB unlock so the first query result is briefly pending.
- **Steps:** 1. Launch the app and go straight to Notes; observe the body before data loads.
- **Expected:** While `notes` is still `null` (no result delivered yet) a 5-row shimmer `SkeletonList` is shown — distinct from the empty state, which only appears once the flow emits an actually-empty list.

### 10. Note detail
_Scope: full note view/edit, call shortcut, deep links, recurrence, checklist reorder, location reminder + map + directions, delete. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/ui/notes/NoteDetailScreen.kt`._

#### TM-DETAIL-01 · Open detail; header badge + due meta — `P0` · positive
- **Preconditions:** An approved `reminder` with a `dueDate` and `dueTime`.
- **Steps:** 1. On Notes, tap the reminder card. 2. Inspect the top row.
- **Expected:** A category badge renders (`categoryFor`: overdue reminder/todo→Overdue, else Reminder/Todo/Note). When `dueDate != null`, "`dueDate dueTime`" is shown next to the badge in the category accent color. Below: the title (bold, with a pencil **Edit** hint icon) and "from `source`".

#### TM-DETAIL-02 · Inline-edit title persists on Done and reschedules alarm — `P0` · positive
- **Preconditions:** A timed `reminder` (`type=="reminder"`, `dueTime != null`).
- **Steps:** 1. Open it. 2. Tap the title; the borderless editor appears with the keyboard. 3. Change the text. 4. Tap the IME **Done** action.
- **Expected:** Editor collapses back to a label showing the new title; `updateTitle` trims and writes it (`dao.updateNote`) and, because it's a timed reminder, calls `alarmScheduler.schedule(...)` so the pending notification text stays in sync. A blank or unchanged title is a no-op (early return — no DB write).

#### TM-DETAIL-03 · Inline-edit saves on focus loss — `P1` · positive
- **Preconditions:** Any note with a non-blank summary.
- **Steps:** 1. Open it. 2. Tap the **summary** to edit. 3. Type a change. 4. Tap elsewhere on the screen / dismiss the keyboard (do NOT press Done).
- **Expected:** `onFocusChanged` fires after first focus, so leaving focus triggers `onSave(draft)` → `updateSummary` (trimmed; no-op if unchanged). The edited summary is persisted and shown. Same focus-loss save applies to the title editor.

#### TM-DETAIL-04 · Call button appears for a number stated in the text — `P0` · positive
- **Preconditions:** A note whose title/summary/body contains a dialable number, e.g. body "Call Gauh, number: +31644016988". Create via Test Extraction.
- **Steps:** 1. Open the note. 2. Tap **Call +31644016988**.
- **Expected:** A tonal **Call <number>** button shows under "from <source>" (`resolveCallNumber` returns the directly-extracted number — no contacts needed). Tapping opens the system **dialer pre-filled** via `ACTION_DIAL` `tel:` (no call is auto-placed).

#### TM-DETAIL-05 · Call button resolves a named contact (no number in text) — `P1` · positive
- **Preconditions:** READ_CONTACTS granted; a device contact "John" with a number saved. A note with `source` = "Notification from John" and a call-intent body (e.g. "wants you to call"), with **no** number in the text.
- **Steps:** 1. Open the note. 2. Observe the Call button. 3. Tap it.
- **Expected:** Because there's no direct number but it `isCallIntent` and `personName(source,...)`→"John", `ContactResolver.lookupNumber` matches the contact (exact→prefix→substring tier) and the button reads **Call <John's number>**. Tapping pre-fills the dialer. If READ_CONTACTS is denied, lookup returns null and **no** Call button appears.

#### TM-DETAIL-06 · Call button hidden when nothing is dialable — `P1` · negative
- **Preconditions:** A plain `note` with no number and no call verb, e.g. title "Meeting at 4pm".
- **Steps:** 1. Open it.
- **Expected:** No Call button (`resolveCallNumber` → null: no extractable number, not a call intent). Confirm a date-like "2026-06-15" in the body is **not** treated as a phone number (DATE_LIKE guard) and does not surface a Call button.

#### TM-DETAIL-07 · Deep links in the body are tappable by type — `P1` · positive
- **Preconditions:** A note whose body contains, on separate lines: a URL `https://example.com`, a bare `www.test.org`, an email `a@b.com`, a 10-digit number `4079017892`, and an address line.
- **Steps:** 1. Open the note and scroll to **Details**. 2. Tap each linkified token.
- **Expected:** `linkifyNoteBody` underlines/colors each match and routes via the platform UriHandler: `http(s)://…` opens as-is; `www.…` opens as `http://www.…`; `a@b.com` opens `mailto:`; the 10-digit/`+intl` number opens `tel:` (digits/`+` only). A `2026-06-15` date is **not** linkified (no 10 consecutive digits). No action auto-fires — each requires a tap.

#### TM-DETAIL-08 · Recurrence dropdown sets repeat and reschedules — `P0` · positive
- **Preconditions:** A `reminder` (the Repeat control renders only for `type=="reminder"`).
- **Steps:** 1. Open the reminder; under **Reminder**, open the **Repeat** dropdown. 2. Select **Weekly**.
- **Expected:** Dropdown lists exactly **None / Daily / Weekly / Monthly** (`RecurrenceUtil.OPTIONS`). The field shows the current value capitalized (default "None"). Selecting Weekly → `updateRecurrence("Weekly")` stores "weekly" and re-arms the alarm; selecting **None** stores null (no repeat). 

> Note: A `todo` shows the **Reminder** section (divider + location button) but **no** Repeat dropdown — recurrence UI is gated to `type=="reminder"` only.

#### TM-DETAIL-09 · Checklist check + drag-to-reorder persist — `P1` · positive
- **Preconditions:** A `todo` whose summary is list-like (e.g. "Milk, eggs, bread") OR has a stored `checklist`. (`Checklist.derive` needs ≥2 comma/line tokens.)
- **Steps:** 1. Open the todo; a **Checklist** section shows the items. 2. Tick one item. 3. Long-press the drag handle on another row and move it to a new position; release. 4. Leave and reopen the note.
- **Expected:** Ticking strikes the row through and persists via `updateChecklist(Checklist.encode(...))`. Drag (`ReorderableColumn.onSettle`) reorders and persists the new order. Both checked-state and order survive reopen. A non-list todo (single token) shows **no** Checklist section.

#### TM-DETAIL-10 · Add location reminder → place dialog → map + geofence + directions — `P0` · positive
- **Preconditions:** A `reminder` with no location yet; fine + background location available to grant; device has a fix (or use a mock-location route).
- **Steps:** 1. Open the reminder; tap **Remind me at a place**. 2. In the dialog, type label "Office"; tap **Use current location**. 3. Grant ACCESS_FINE_LOCATION (then background) when prompted.
- **Expected:** Dialog text: "Saves your current location; you'll be reminded when you return here." After granting, `captureCurrentLocation` fixes the spot and `setLocationReminder(lat,lng,"Office")` saves it (radius 150 m) and registers a geofence. UI now shows a Place row "Office" with **Remove**, a 180dp embedded Google Map (marker + a geofence **circle of 150 m radius** in the category accent), and a full-width **Get directions** button.

#### TM-DETAIL-11 · Blank label defaults to "Saved location" — `P2` · edge
- **Preconditions:** Same as TM-DETAIL-10 but leave the label field empty.
- **Steps:** 1. Tap **Remind me at a place**. 2. Leave Label blank; tap **Use current location**; grant permission.
- **Expected:** The saved place is labelled "**Saved location**" (`locationLabel.trim().ifBlank { "Saved location" }`).

#### TM-DETAIL-12 · Location capture failure shows a toast, saves nothing — `P1` · negative
- **Preconditions:** Location services off / no obtainable fix (test indoors with GPS denied to the fused provider).
- **Steps:** 1. Tap **Remind me at a place**; enter a label; tap **Use current location**; grant permission. 2. Wait for the fix attempt to resolve.
- **Expected:** On null location: toast "**Couldn't get your location — try again outside.**"; on failure: toast "**Couldn't get your location.**" No Place row, map, or geofence is created (`setLocationReminder` never runs).

#### TM-DETAIL-13 · Get directions opens Maps to the venue — `P1` · positive
- **Preconditions:** A reminder with a saved location (label + lat/lng).
- **Steps:** 1. Open it; tap **Get directions**.
- **Expected:** `openDirections` launches Google Maps directions to `lat,lng` (coords preferred over name) via `https://www.google.com/maps/dir/?api=1&destination=…`, targeting the Maps app; if Maps isn't installed it falls back to any handler (e.g. browser). With coords missing it would fall back to the URL-encoded place name.

#### TM-DETAIL-14 · Remove location clears geofence and map — `P1` · positive
- **Preconditions:** A reminder with a saved location.
- **Steps:** 1. Open it; in the Place row tap **Remove**.
- **Expected:** `clearLocationReminder` removes the geofence and nulls lat/lng/radius/label. The Place row, map, and Get-directions disappear; the **Remind me at a place** button returns.

#### TM-DETAIL-15 · Delete pops back without flashing "not found" — `P0` · positive
- **Preconditions:** Any note open in detail.
- **Steps:** 1. Scroll to the bottom; tap **Delete**.
- **Expected:** `deleting=true` is set before deletion, so when the note flow emits `null` the screen renders **nothing** (not the error) and `deleteNote` cancels the alarm, removes the geofence, deletes the row, then invokes `onBack` to pop. The list no longer contains the item.

#### TM-DETAIL-16 · "Note not found." for an invalid/missing id — `P2` · edge
- **Preconditions:** Navigate to detail with a `noteId` that doesn't exist (e.g. deep-link/back-stack to a since-deleted id; `noteId` defaults to -1 if absent).
- **Steps:** 1. Reach the detail route for the missing id.
- **Expected:** Since `note` is `null` and `deleting` is false, the screen shows centered text "**Note not found.**" (bodyLarge) and nothing else.

- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\ui\notes\NotesScreen.kt`
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\ui\notes\NotesViewModel.kt`
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\ui\notes\NoteDetailScreen.kt`
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\ui\notes\NoteDetailViewModel.kt`
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\ui\notes\NoteLinks.kt`
- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\ui\notes\Checklist.kt`
- Helpers grounding call/directions/recurrence/color: `...\data\source\ContactResolver.kt`, `...\data\source\PhoneUtil.kt`, `...\data\source\RecurrenceUtil.kt`, `...\ui\common\ActionIntents.kt`, `...\ui\common\Category.kt`, `...\ui\bold\BoldKit.kt`, `...\ui\common\Skeleton.kt`.

### 11. Reminders & alarms
_Scope: exact alarms, recurrence, reboot rearm. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/AlarmScheduler.kt`._

#### TM-ALARM-01 · One-off reminder fires once — `P0` · positive
- **Preconditions:** App installed; "Alarms & reminders" (exact-alarm) permission and notification permission granted. Device clock set so you can pick a near-future minute.
- **Steps:** 1. Settings -> Test Extraction; paste text that yields a dated reminder ~2 min out, e.g. `Reminder: Call plumber today at <HH:MM 2 minutes from now>`. 2. Run, then approve the resulting suggestion in the Inbox (type `reminder`, with a dueTime). 3. Leave the device idle until the target minute passes.
- **Expected:** Exactly one heads-up notification appears at the scheduled minute with title `Reminder: Call plumber` (the `extractedTitle`) and body `It's time for your task.`; small icon `ic_notification`; tapping opens MainActivity and the notification auto-cancels. No second/repeat notification fires (no recurrence).

#### TM-ALARM-02 · Daily recurrence advances and reschedules on fire — `P1` · positive
- **Preconditions:** Exact-alarm + notification permissions granted.
- **Steps:** 1. Via Test Extraction, create a reminder with a daily repeat due ~2 min out (set Repeat = Daily on the note if extraction doesn't infer it). 2. Approve. 3. Wait for the alarm to fire. 4. Open the note detail and check the due date.
- **Expected:** Notification `Reminder: <title>` fires once. After firing, the note's `dueDate` is advanced to the next future occurrence (tomorrow's date via `RecurrenceUtil.next` + `firstFutureOccurrence`) and a new alarm is armed for that slot. The same calendar day does not re-fire.

#### TM-ALARM-03 · Weekly/monthly recurrence steps by correct period — `P1` · positive
- **Preconditions:** As above.
- **Steps:** 1. Create one Weekly and one Monthly reminder, each due ~2 min out. 2. Approve both, let both fire. 3. Inspect each note's new `dueDate`.
- **Expected:** Weekly note's `dueDate` advances by exactly 7 days (`plusWeeks(1)`); Monthly advances by one calendar month (`plusMonths(1)`). Each re-arms for its next slot. Notifications use `Reminder: <title>`.

#### TM-ALARM-04 · Late delivery skips forward to a future slot (no past-armed recurrence) — `P1` · edge
- **Preconditions:** Exact-alarm permission granted. A Daily reminder whose stored `dueDate` is several days in the past (simulate by editing the note's due date back, or by leaving the device off across the slot).
- **Steps:** 1. Have a Daily reminder with a stored date 3 days ago. 2. Trigger the alarm receiver (or boot — see TM-ALARM-06). 3. Check the note's `dueDate` after processing.
- **Expected:** The receiver advances at least one period past the fired date, then keeps stepping until the slot is strictly after `LocalDateTime.now()` (`firstFutureOccurrence`). The note lands on the next future occurrence, not a past one — recurrence is not silently broken. No alarm is armed in the past (scheduler drops past instants).

#### TM-ALARM-05 · Past computed time is silently dropped — `P1` · negative
- **Preconditions:** Exact-alarm permission granted.
- **Steps:** 1. Via Test Extraction, create a one-off reminder whose dueDate/dueTime is in the past (e.g. today at a time 5 minutes ago). 2. Approve.
- **Expected:** `AlarmScheduler.schedule` returns early because `timeMillis <= System.currentTimeMillis()`; no alarm is armed and no notification ever fires. The note is still created (approval succeeds), but it has no pending alarm.

#### TM-ALARM-06 · Reboot re-arms all active reminders — `P0` · positive
- **Preconditions:** Two active reminders exist — one one-off due in the future, one Daily. Exact-alarm permission granted.
- **Steps:** 1. Confirm both reminders are scheduled. 2. Reboot the device, OR `adb shell su` is unavailable so simulate with `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n com.rajasudhan.taskmind/.data.source.BootReceiver`. 3. Wait for both target times to pass.
- **Expected:** `BootReceiver` iterates `getReminderNotes()` (completed=0, type='reminder', dueDate & dueTime non-null) and re-arms each. The future one-off fires at its time; the Daily one is first advanced via `firstFutureOccurrence` to its next future slot (and `updateNoteDueDate` persists it only if changed) before re-arming. Both fire correctly post-reboot.

#### TM-ALARM-07 · Reboot drops a past one-off but resumes recurrence — `P1` · edge
- **Preconditions:** A one-off reminder whose time already passed while the device was "off", plus a Daily reminder whose stored date is now in the past.
- **Steps:** 1. Set up both. 2. Broadcast BOOT_COMPLETED (see TM-ALARM-06). 3. Observe.
- **Expected:** The past one-off is re-armed "as stored" but the scheduler drops it (past instant) — it does not fire and no catch-up notification appears, which is correct. The Daily one advances to its next future slot and fires there.

#### TM-ALARM-08 · Deleted-note alarm no-ops and cancels itself — `P0` · edge
- **Preconditions:** A Daily reminder armed and about to fire.
- **Steps:** 1. Create + approve a Daily reminder due ~2 min out. 2. Delete the note before the alarm fires. 3. Wait past the scheduled minute.
- **Expected:** When `AlarmReceiver.onReceive` runs, `dao.getNoteByIdNow(noteId)` is null, so it calls `alarmScheduler.cancel(noteId)` and returns without notifying. No `Reminder:` notification appears, and the recurrence does not keep rescheduling itself (no phantom weekly-forever alarm).

#### TM-ALARM-09 · Single-digit hour parses as zero-padded — `P1` · edge
- **Preconditions:** Exact-alarm permission granted.
- **Steps:** 1. Via Test Extraction, create a reminder whose dueTime stores as `9:30` (single-digit hour) for ~2 min out (use a phrasing that yields 9:30 AM or set the device clock so 9:30 is near). 2. Approve and wait.
- **Expected:** `RecurrenceUtil.parseTime("9:30")` returns `LocalTime.of(9,30)` (same as `09:30`); the alarm is armed and fires. The time is not rejected/dropped by a strict HH parser.

#### TM-ALARM-10 · Malformed time is rejected — no alarm — `P1` · negative
- **Preconditions:** Exact-alarm permission granted; a way to inject a bad time (e.g. a note whose stored dueTime is `25:00`, `9`, or `9:60`).
- **Steps:** 1. Create a reminder whose dueTime is malformed (`9` — no colon, or `25:00`). 2. Approve / let boot processing run.
- **Expected:** `parseTime` returns null (`parts.size != 2`, or `LocalTime.of` throws and is caught), so `schedule` returns early — no alarm armed. On reboot, `BootReceiver` skips this note entirely (`parseTime(time) == null` -> continue) and does not advance/persist a midnight-fallback date.

#### TM-ALARM-11 · Exact-alarm permission revoked — alarm not armed — `P1` · negative
- **Preconditions:** Settings -> Apps -> TaskMind -> Alarms & reminders toggled OFF (so `canScheduleExactAlarms()` is false).
- **Steps:** 1. Revoke the exact-alarm permission. 2. Via Test Extraction, create + approve a future reminder. 3. Wait for the target time.
- **Expected:** `schedule` reaches the `canScheduleExactAlarms()` guard, which is false, so `setExactAndAllowWhileIdle` is never called — no notification fires. (The note is still created.) Re-granting the permission and rebooting/re-approving re-arms it.

> Note: The fired-reminder notification id is `System.currentTimeMillis().toInt()` (a fresh id each time), so repeated fires stack as separate notifications rather than replacing one another — there is no per-note stable reminder notification id.

### 12. Geofence / location reminders
_Scope: place reminders. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/GeofenceManager.kt`._

#### TM-GEO-01 · Geofence registered on approval, keyed by note id — `P0` · positive
- **Preconditions:** ACCESS_FINE_LOCATION + ACCESS_BACKGROUND_LOCATION both granted; location services on; Geocoder available; a known last location near the test place.
- **Steps:** 1. Via Test Extraction, paste text naming a nearby place, e.g. `Pick up prescription at Walgreens, <your city>`. 2. Run and approve. 3. Inspect the note (map should show a pin) / check logs for geofence registration.
- **Expected:** On approve, `SuggestionApprover` geocodes the place, stores lat/lng + radius (`LOCATION_RADIUS_METERS = 150.0`) + label, and — because background location is granted — calls `geofenceManager.add(noteId, lat, lng, 150f)`. The geofence `requestId` equals the note id (string), expiration `NEVER_EXPIRE`, transition type ENTER only.

#### TM-GEO-02 · ENTER transition fires place notification — `P0` · positive
- **Preconditions:** TM-GEO-01 done; a registered geofence for a known location. Emulator or device able to mock location.
- **Steps:** 1. Start well outside the geofence. 2. Mock-move into the place using the emulator extended-controls "Location" route, or `adb emu geo fix <lng> <lat>` to a point inside the 150 m radius. 3. Observe the notification shade.
- **Expected:** `GeofenceBroadcastReceiver` receives a GEOFENCE_TRANSITION_ENTER event, looks up the note, and posts a notification with content title `You're near <locationLabel>` and content text = note title; small icon `ic_notification`; HIGH priority; auto-cancel; tapping opens MainActivity. Notification id is `100000 + noteId`.

#### TM-GEO-03 · Already-inside initial trigger fires immediately — `P1` · edge
- **Preconditions:** Permissions granted; device's mocked current location already inside the target place's radius.
- **Steps:** 1. Mock location to a point inside the venue first. 2. Then create + approve the place reminder for that venue (registering the geofence while already inside).
- **Expected:** Because the request uses `setInitialTrigger(INITIAL_TRIGGER_ENTER)`, the system fires an ENTER immediately on registration; the `You're near <place>` notification appears without needing to move.

#### TM-GEO-04 · Notification label falls back when no stored label — `P1` · edge
- **Preconditions:** A geofenced note whose `locationLabel` is null (e.g. directly nulled in DB), but geofence still registered.
- **Steps:** 1. Trigger ENTER for that note id (mock location). 2. Observe notification.
- **Expected:** Content title is `You're near a saved place` (the `?: "a saved place"` fallback in the receiver); content text is the note title.

#### TM-GEO-05 · No background-location -> no geofence registered — `P1` · negative
- **Preconditions:** ACCESS_FINE_LOCATION granted but ACCESS_BACKGROUND_LOCATION denied (set to "Allow only while using the app").
- **Steps:** 1. Via Test Extraction, create + approve a place reminder for a nearby venue. 2. Inspect the note and attempt to trigger ENTER by mock-moving into the venue.
- **Expected:** Geocoding still runs and the note stores lat/lng/radius/label (map pin shows), but `hasBackgroundLocation()` is false so `geofenceManager.add(...)` is never called — no geofence exists and no `You're near` notification ever fires when entering.

#### TM-GEO-06 · Geocoding biases to last known location — `P1` · positive
- **Preconditions:** FINE or COARSE location granted; a last known location set near city A; a chain-store place name that exists in many cities.
- **Steps:** 1. Mock last location to city A. 2. Via Test Extraction, approve a reminder naming a bare chain name (e.g. `Panda Express`) with no city. 3. Inspect the resolved coordinates on the note's map.
- **Expected:** `PlaceGeocoder` resolves using a ~55 km bounding box (`d = 0.5` deg) around the last location, so the pin lands on the city-A branch, not a far-away one.

#### TM-GEO-07 · Geocoder returns null on unresolvable place — `P1` · negative
- **Preconditions:** Location permission granted.
- **Steps:** 1. Via Test Extraction, approve a reminder with a nonsense place string, e.g. `meet at zzqqxx-not-a-real-place-9999`. 2. Inspect the note.
- **Expected:** `geocode` resolves to null (empty address list); the note still saves with the place label but lat/lng/radius null (no map pin), and no geofence is registered (`coords != null` guard fails). Directions can still fall back to a Maps name search.

#### TM-GEO-08 · No location permission -> geocode skips location bias / returns null path — `P2` · negative
- **Preconditions:** All location permissions denied.
- **Steps:** 1. Deny location entirely. 2. Approve a place reminder.
- **Expected:** `lastLocation()` returns null (permission check fails), so geocoding runs without a bounding box (unbiased). If the place can't resolve, `geocode` returns null; no geofence registered (background location also absent). App does not crash.

#### TM-GEO-09 · Geofence removed/blank place no-ops — `P2` · edge
- **Preconditions:** A suggestion whose `location` is blank/whitespace only.
- **Steps:** 1. Via Test Extraction, approve a reminder where the location field is empty or whitespace.
- **Expected:** `place = suggestion.location?.trim()?.ifBlank { null }` is null, so no geocode, no location stored, no geofence — the approval proceeds as a plain note with no location side effects.

> Note: `GeofenceManager.add` wraps `client.addGeofences` in `runCatching`, so a Play-services or permission failure is swallowed silently — there is no user-facing error toast if registration fails. Verify success via the actual ENTER notification, not a UI confirmation.

### 13. Calendar integration
_Scope: event creation on approval. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/SuggestionApprover.kt`._

#### TM-CAL-01 · Approving a timed reminder writes a calendar event — `P0` · positive
- **Preconditions:** WRITE_CALENDAR granted; at least one writable (contributor+) calendar exists; calendar target setting = Auto.
- **Steps:** 1. Via Test Extraction, create a dated reminder with a time, e.g. `Dentist appointment tomorrow at 14:30`. 2. Approve. 3. Open the device Calendar app on that date.
- **Expected:** A timed event titled `<extractedTitle>` is created at the parsed start time; end = start + `eventDurationMinutes` (default 60) minutes; timezone = device default; not all-day. Description = the note body (`Extracted from <source>:\n\n<rawSnippet>`).

#### TM-CAL-02 · Approving a dated to-do writes an all-day event — `P1` · positive
- **Preconditions:** WRITE_CALENDAR granted; writable calendar exists.
- **Steps:** 1. Via Test Extraction, create a to-do with a date but no time, e.g. `Submit tax forms by 2026-07-15` (type todo, dueDate set, dueTime null). 2. Approve. 3. Check the calendar on that date.
- **Expected:** An all-day event titled `<extractedTitle>` on `dueDate`; start = start-of-day UTC, end = start + 24h, timezone `UTC`, `ALL_DAY = 1`. (Path: `type == "todo" && dueDate != null` -> `addToCalendar(..., dueTime = null)`.)

#### TM-CAL-03 · Duplicate event is de-duplicated (no twin) — `P0` · edge
- **Preconditions:** WRITE_CALENDAR granted; TM-CAL-01 already created an event.
- **Steps:** 1. Approve a second reminder with the SAME title and a start time within ±1 day of the first. 2. Check the calendar.
- **Expected:** `calendarEventExists` finds a same-title event with DTSTART within ±24h and returns true, so `addToCalendar` returns before inserting — exactly one event remains, no twin.

#### TM-CAL-04 · Same title, start >1 day apart creates a second event — `P1` · boundary
- **Preconditions:** WRITE_CALENDAR granted; an event titled "Standup" exists on day D.
- **Steps:** 1. Approve another reminder titled "Standup" timed on day D+2 (more than 24h from the first). 2. Check the calendar.
- **Expected:** The ±24h window does not match, so a distinct second "Standup" event is created on D+2. (Confirms de-dup is time-windowed, not global by title.)

#### TM-CAL-05 · Honors configured target calendar — `P1` · positive
- **Preconditions:** WRITE_CALENDAR granted; at least two writable calendars; Settings calendar target set to a specific (non-Auto) calendar that is still writable.
- **Steps:** 1. Set the target calendar in Settings to calendar B (not the primary). 2. Approve a timed reminder. 3. Check which calendar the event landed in.
- **Expected:** `getWritableCalendarId` returns the configured id (because `configured != CALENDAR_ID_AUTO && id == configured`); the event appears in calendar B, not the primary.

#### TM-CAL-06 · Auto target picks primary/first writable — `P1` · positive
- **Preconditions:** WRITE_CALENDAR granted; calendar target = Auto (`CALENDAR_ID_AUTO = -1`); a primary writable calendar exists.
- **Steps:** 1. Set target = Auto. 2. Approve a timed reminder. 3. Check the calendar.
- **Expected:** Query is ordered `IS_PRIMARY DESC`; the first contributor+ calendar (the primary) is used as `fallback`. Event lands in the primary calendar.

#### TM-CAL-07 · Configured-but-unwritable calendar falls back — `P2` · edge
- **Preconditions:** WRITE_CALENDAR granted; target set to a calendar id whose access level is below CONTRIBUTOR (read-only) or that no longer exists.
- **Steps:** 1. Configure target to the read-only/missing id. 2. Approve a timed reminder. 3. Check the calendar.
- **Expected:** The configured id is skipped (read-only rows `continue` before the match; missing id never matches), and the first writable calendar (`fallback`) is used instead — the event is still created somewhere writable.

#### TM-CAL-08 · Honors event-duration setting — `P1` · positive
- **Preconditions:** WRITE_CALENDAR granted; Settings event duration changed from default 60 to e.g. 30 minutes.
- **Steps:** 1. Set event duration = 30. 2. Approve a timed reminder at e.g. 10:00. 3. Open the event in the calendar.
- **Expected:** Event end = start + 30 min (10:00–10:30), reflecting `settingsManager.eventDurationMinutes`. (All-day to-dos are unaffected — they always span 24h.)

#### TM-CAL-09 · No WRITE_CALENDAR permission -> no event, approval still succeeds — `P0` · negative
- **Preconditions:** WRITE_CALENDAR denied.
- **Steps:** 1. Revoke Calendar permission for TaskMind. 2. Via Test Extraction, approve a timed reminder. 3. Check the calendar and the Inbox/Notes.
- **Expected:** `addToCalendar` returns at the permission check — no event written and no crash. The note is still created and the alarm still scheduled (calendar write is best-effort, separate from note creation).

#### TM-CAL-10 · No writable calendar -> no event — `P2` · negative
- **Preconditions:** WRITE_CALENDAR granted but no account with a contributor+ calendar (all read-only or none).
- **Steps:** 1. Ensure no writable calendar exists. 2. Approve a timed reminder.
- **Expected:** `getWritableCalendarId()` returns null, so `addToCalendar` returns before inserting — no event, no crash, note still created.

#### TM-CAL-11 · Untimed reminder (no dueTime) does not reach calendar as timed — `P2` · negative
- **Preconditions:** WRITE_CALENDAR granted.
- **Steps:** 1. Create a suggestion of type `reminder` with a dueDate but null dueTime. 2. Approve.
- **Expected:** `isReminder` is false (requires `dueTime != null`), and it is not a `todo`, so neither calendar branch runs — no calendar event is created. The note is saved as type `reminder`'s fallback (`suggestion.type`) with no alarm. 

> Note: The calendar branch for reminders is gated on `isReminder` (`type == "reminder" && dueTime != null`); a reminder with a date but no time writes neither an alarm nor a calendar event. Only dated `todo`s without a time reach the all-day calendar path.

- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/AlarmScheduler.kt`
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/AlarmReceiver.kt`
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/RecurrenceUtil.kt`
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/BootReceiver.kt`
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/GeofenceManager.kt`
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/GeofenceBroadcastReceiver.kt`
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/PlaceGeocoder.kt`
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/SuggestionApprover.kt`
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/local/TaskMindDao.kt` (query filters)
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/SettingsManager.kt` (`CALENDAR_ID_AUTO = -1`, `DEFAULT_EVENT_DURATION_MINUTES = 60`)

### 14. Notifications
_Scope: review + reminder + service + geofence notifications and channels. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/SuggestionNotifier.kt`._

> Note: The scope lists a separate "geofence channel," but the code uses only **two** channels: `taskmind_suggestions` (IMPORTANCE_DEFAULT, in `SuggestionNotifier`) and `taskmind_service_channel` (IMPORTANCE_LOW, in `TaskMindForegroundService.ensureNotificationChannel`). Both reminder notifications (`AlarmReceiver`) and geofence notifications (`GeofenceBroadcastReceiver`) post to the shared `taskmind_service_channel`; there is no dedicated reminder or geofence channel.

> Note: There is no in-app runtime request launcher for `POST_NOTIFICATIONS` (no `rememberPermissionState`/`requestPermissions` call anywhere). It is declared in the manifest and only surfaced as a read-only status row ("Post notifications") in Settings/Privacy. On Android 13+ the OS shows the grant prompt automatically the first time the app posts to a channel; the in-app surface is status-only.

#### TM-NOTIF-01 · Single self-updating review notification with id 42 — `P0` · positive
- **Preconditions:** Notifications enabled (POST_NOTIFICATIONS granted); Inbox has 0 pending suggestions; app foreground service running.
- **Steps:**
  1. Open Settings -> Test Extraction (debug); paste `Dentist appointment tomorrow at 3pm` and tap Run.
  2. Wait for the suggestion to land in the Inbox.
  3. Pull down the status bar and inspect the TaskMind review notification.
  4. Run Test Extraction again with a second distinct actionable text, e.g. `Pay electricity bill on Friday`.
  5. Re-inspect the status bar.
- **Expected:** After step 3 exactly one notification appears titled **"1 suggestion to review"** with content text equal to the top suggestion's extracted title, monochrome spark small-icon, and **Approve** / **Reject** action buttons. After step 5 the SAME notification (not a second one) updates in place to **"2 suggestions to review"**; only one TaskMind review notification is ever present (single fixed `NOTIFICATION_ID = 42`).

#### TM-NOTIF-02 · Approve action triages without opening the app and advances — `P0` · positive
- **Preconditions:** Two pending suggestions exist (per TM-NOTIF-01); app is in the background or screen locked (do NOT bring the app to foreground).
- **Steps:**
  1. From the lock screen / shade, expand the "2 suggestions to review" notification.
  2. Tap **Approve**.
  3. Observe the notification without launching the app.
  4. Open the app afterward and check Notes/Inbox.
- **Expected:** The app does NOT launch. The top suggestion is approved through the shared approve path (same as Inbox approval) and saved as a Note. The notification refreshes in place to **"1 suggestion to review"**, now showing the next pending item's title. The approved item is gone from the Inbox pending list and present in Notes.

#### TM-NOTIF-03 · Reject action records rejection and advances — `P0` · positive
- **Preconditions:** At least two pending suggestions; app backgrounded.
- **Steps:**
  1. Expand the review notification.
  2. Tap **Reject**.
  3. Inspect the notification, then open the Inbox.
- **Expected:** App does not launch. The rejected suggestion's status becomes `rejected` (not present in pending Inbox, not in Notes) and the rejection is recorded for on-device learning. The notification updates in place to the next pending item / decremented count.

#### TM-NOTIF-04 · Auto-dismiss when zero pending remain — `P0` · positive
- **Preconditions:** Exactly ONE pending suggestion; review notification showing "1 suggestion to review"; app backgrounded.
- **Steps:**
  1. Tap **Approve** (or **Reject**) on the notification.
  2. Inspect the status bar.
- **Expected:** After the action runs, `notifyPending()` finds zero non-snoozed pending items and calls `cancel()`; the review notification (id 42) is removed from the shade entirely. No empty "0 suggestions" notification is left behind.

#### TM-NOTIF-05 · Tapping the body opens the Inbox and auto-cancels — `P1` · positive
- **Preconditions:** At least one pending suggestion; review notification visible.
- **Steps:**
  1. Tap the notification body (not the action buttons).
- **Expected:** MainActivity launches (Inbox). Because the builder sets `setAutoCancel(true)`, the notification is dismissed on body tap.

#### TM-NOTIF-06 · Snoozed items are hidden from the review notification — `P1` · edge
- **Preconditions:** Ability to snooze a pending suggestion in the Inbox to a future time; create one pending item and snooze it, plus keep a second non-snoozed pending item.
- **Steps:**
  1. Snooze suggestion A to a future timestamp; leave suggestion B pending.
  2. Trigger a re-post of the notification (run Test Extraction once more or approve/reject to force `notifyPending()`).
  3. Inspect the notification title and content.
- **Expected:** The count reflects only non-snoozed items — the notification reads **"1 suggestion to review"** featuring B; the snoozed A is filtered out (`snoozedUntil == null || snoozedUntil <= now`). If A is the only pending item and is snoozed to the future, the notification is cancelled entirely.

#### TM-NOTIF-07 · Snooze window expiry re-surfaces the item — `P2` · edge
- **Preconditions:** One pending suggestion snoozed to ~1–2 minutes in the future; no other pending items (notification currently absent).
- **Steps:**
  1. Wait until the snooze timestamp passes.
  2. Cause `notifyPending()` to run again (e.g. run a Test Extraction that produces a duplicate-suppressed item, or approve/reject another item — any path that re-evaluates pending).
- **Expected:** Once `snoozedUntil <= now`, the item is included again and the "1 suggestion to review" notification reappears featuring it. (Note: re-surfacing is only triggered when something invokes `notifyPending()`; the notifier has no internal timer.)

#### TM-NOTIF-08 · Foreground-service notification + channel (IMPORTANCE_LOW) — `P0` · positive
- **Preconditions:** At least one data source enabled so the foreground service starts.
- **Steps:**
  1. With a source enabled, confirm the persistent notification appears.
  2. Read its title/text.
  3. Go to Android Settings -> Apps -> TaskMind -> Notifications and inspect channels.
- **Expected:** A persistent (ongoing) notification titled **"TaskMind is active"** / text **"Monitoring enabled data sources"** with the monochrome spark icon, on channel **"TaskMind Service Channel"** at **Low** importance (silent, no peek). Tapping it opens MainActivity.

#### TM-NOTIF-09 · Reminder notification posts on the service channel — `P1` · positive
- **Preconditions:** Create a Note with a one-off reminder ~2 minutes out (exact-alarm permission granted).
- **Steps:**
  1. Wait for the alarm to fire (keep screen off to also test wakeup).
  2. Inspect the notification.
- **Expected:** A heads-up notification (PRIORITY_HIGH) titled **"Reminder: <note title>"** / text **"It's time for your task."**, monochrome icon, on the **"TaskMind Service Channel"** (no separate reminder channel). `setAutoCancel(true)` — it clears on tap. Each reminder uses a unique id (`System.currentTimeMillis().toInt()`), so multiple reminders stack rather than replace each other.

#### TM-NOTIF-10 · Reminder channel survives reboot before app reopen — `P1` · edge
- **Preconditions:** Note with a recurring/one-off reminder scheduled for shortly after a reboot.
- **Steps:**
  1. Reboot the device (`adb reboot`, or simulate BOOT_COMPLETED with `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.rajasudhan.taskmind`).
  2. Do NOT open the app.
  3. Wait for the reminder alarm to fire.
- **Expected:** The reminder notification still posts — `ensureNotificationChannel` is also called from `TaskMindApp` on startup, so the `taskmind_service_channel` exists even though the foreground service may not have run yet. (Without it Android O+ would silently drop the notification.)

#### TM-NOTIF-11 · Geofence notification id offset and channel — `P1` · positive
- **Preconditions:** A Note with a saved location and a registered geofence (fine + background location granted).
- **Steps:**
  1. Drive/mock entry into the geofenced region (mock location route into the saved place).
  2. Inspect the notification.
- **Expected:** A PRIORITY_HIGH notification titled **"You're near <locationLabel or 'a saved place'>"** / text = note title, on **"TaskMind Service Channel"**, with id `100_000 + noteId` (so geofence notifications never collide with the service id 1 or the review id 42). `setAutoCancel(true)`.

#### TM-NOTIF-12 · Monochrome status-bar icon (no white square) — `P2` · positive
- **Preconditions:** Any TaskMind notification present (review, service, or reminder).
- **Steps:**
  1. Look at the small icon in the status bar (not the expanded shade).
- **Expected:** The status-bar small icon is the white/transparent four-point spark from `R.drawable.ic_notification` (a vector tinted by the system), NOT the colored launcher mipmap and NOT a white square. Confirms `setSmallIcon(R.drawable.ic_notification)` is used everywhere.

#### TM-NOTIF-13 · Android 13+ POST_NOTIFICATIONS prompt on first post — `P0` · positive
- **Preconditions:** Fresh install on Android 16 (S25 Ultra); POST_NOTIFICATIONS not yet granted.
- **Steps:**
  1. Enable a data source so the foreground service starts and posts its notification (or run a Test Extraction that posts the review notification).
  2. Observe the system prompt.
  3. Open Settings/Privacy and find the "Post notifications" status row.
- **Expected:** The OS shows the standard "Allow TaskMind to send you notifications?" runtime prompt (framework-driven on first channel post; the app has no custom rationale dialog). The "Post notifications" row reflects Granted/Denied accordingly. If denied, notifications are suppressed by the OS but the app does not crash.

#### TM-NOTIF-14 · Approve/Reject on a stale (already-handled) item is a no-op — `P2` · negative
- **Preconditions:** A review notification showing item X; then approve/reject X from the Inbox UI so its status is no longer `pending`, leaving the (now stale) notification still visible.
- **Steps:**
  1. Tap **Approve** (or **Reject**) on the stale notification.
- **Expected:** The receiver loads the suggestion, finds `status != "pending"`, performs no approve/reject, then calls `notifyPending()` which re-syncs the notification to the actual pending set (refresh or auto-dismiss). No duplicate Note, no crash.

### 15. Contact resolution
_Scope: name→number resolution for the Call button. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/ContactResolver.kt` (+ `PhoneUtil.kt`)._

> Note: The Call button is shown only when `resolveCallNumber` returns non-null. For a name-only (no number) item it returns null unless `PhoneUtil.isCallIntent(...)` is true AND `personName(...)` yields a name AND a contact matches. The scope's phrasing "shows the Call button when intent present but no number" is only true when a matching contact is also found; with no matching contact the button stays hidden.

#### TM-CONTACT-01 · Exact tier beats prefix ("Sam" → exact Sam, not Samuel) — `P0` · positive
- **Preconditions:** READ_CONTACTS granted. Device contacts contain both a contact named exactly **"Sam"** (number e.g. `+1 (650) 111-1111`) and **"Samuel"** (different number).
- **Steps:**
  1. In Settings -> Test Extraction, paste `Call Sam` and Run; approve so a "Call …" item with the Call button appears (or trigger via a missed-call/notification path that names "Sam").
  2. Tap the **Call** button and read the prefilled dialer number.
- **Expected:** The dialer opens with **Sam's** number (`+16501111111`), not Samuel's. `lookupNumber` tries exact `LIKE 'Sam'` first (case-insensitive) and returns it before falling back to `'Sam%'` (prefix) or `'%Sam%'` (substring).

#### TM-CONTACT-02 · Prefix tier when no exact match — `P1` · positive
- **Preconditions:** READ_CONTACTS granted. Contacts contain **"Samuel"** but NO contact named exactly "Sam".
- **Steps:**
  1. Produce a Call item for the name `Sam`.
  2. Tap **Call**.
- **Expected:** Exact `LIKE 'Sam'` returns nothing, prefix `LIKE 'Sam%'` matches "Samuel"; the dialer opens with Samuel's normalized number.

#### TM-CONTACT-03 · Substring tier as last resort — `P2` · positive
- **Preconditions:** READ_CONTACTS granted. Contacts contain **"Dr. Sammy Lee"** but nothing starting with / equal to "Sam".
- **Steps:**
  1. Produce a Call item for the name `Sam`.
  2. Tap **Call**.
- **Expected:** Exact and prefix miss; substring `LIKE '%Sam%'` matches "Dr. Sammy Lee"; dialer opens with that contact's number.

#### TM-CONTACT-04 · `%` in name treated as literal (LIKE escape) — `P1` · edge
- **Preconditions:** READ_CONTACTS granted. Create a contact whose display name literally contains a percent sign, e.g. **"50% Off Pizza"** with a number; ensure other contacts exist that a wildcard `%` would otherwise match.
- **Steps:**
  1. Produce a Call item naming `50% Off Pizza`.
  2. Tap **Call** (or verify no wrong-contact match occurs).
- **Expected:** `escapeLike` converts `%` to `\%` with `ESCAPE '\'`, so the `%` matches a literal percent, NOT the SQL "any sequence" wildcard. The query resolves to the "50% Off Pizza" contact only and does not return an arbitrary first contact.

#### TM-CONTACT-05 · `_` in name treated as literal — `P2` · edge
- **Preconditions:** READ_CONTACTS granted. Contact named **"a_b"** plus a decoy contact **"axb"** (single char where `_` would match).
- **Steps:**
  1. Produce a Call item naming `a_b`.
  2. Tap **Call**.
- **Expected:** `_` is escaped to `\_`, so it matches a literal underscore and resolves "a_b", not "axb" (which an unescaped `_` single-char wildcard would have matched).

#### TM-CONTACT-06 · Blank / whitespace name → null, no crash — `P1` · negative
- **Preconditions:** READ_CONTACTS granted.
- **Steps:**
  1. Drive `resolveCallNumber`/`lookupNumber` with a name that is empty or only spaces (e.g. a malformed "Notification from   " or a call-intent title that strips to blank).
- **Expected:** `lookupNumber` trims and returns `null` immediately for a blank term (`if (term.isBlank()) return null`); no contacts query runs; the Call button is hidden; no crash.

#### TM-CONTACT-07 · READ_CONTACTS denied → null, no crash — `P0` · negative
- **Preconditions:** READ_CONTACTS **revoked** for TaskMind (Android Settings -> Apps -> TaskMind -> Permissions -> Contacts -> Deny).
- **Steps:**
  1. Produce a name-only Call-intent item (e.g. a WhatsApp "call me" naming a contact).
  2. Inspect whether a Call button appears.
- **Expected:** `lookupNumber` checks `checkSelfPermission(READ_CONTACTS)` and returns `null` when not granted, so `resolveCallNumber` returns null and the Call button is hidden. No SecurityException / crash.

#### TM-CONTACT-08 · Number normalization for tel: URI — `P0` · positive
- **Preconditions:** READ_CONTACTS granted. Contact "Office" stored as **"+1 (650) 253-0000"**.
- **Steps:**
  1. Produce a Call item naming `Office`.
  2. Tap **Call** and read the dialer number.
- **Expected:** The resolved number is normalized by `PhoneUtil.normalize` to **`+16502530000`** (leading `+` kept, all separators/spaces/parens stripped). The dialer is launched with that clean value.

#### TM-CONTACT-09 · Direct number in text bypasses contact lookup — `P1` · positive
- **Preconditions:** Any state (READ_CONTACTS may even be denied).
- **Steps:**
  1. Produce a Call item whose text contains a number, e.g. `Call the plumber at 650-253-0000`.
  2. Tap **Call**.
- **Expected:** `resolveCallNumber` returns the in-text number via `PhoneUtil.extractFirst` (checked across title/summary/rawSnippet/source) BEFORE any contact lookup; dialer opens with `6502530000`. No READ_CONTACTS needed.

#### TM-CONTACT-10 · Call-intent regex gates name-only lookup — `P1` · edge
- **Preconditions:** READ_CONTACTS granted; contact "Priya" exists.
- **Steps:**
  1. Produce a name-only item whose text contains a call verb, e.g. title `Ring Priya` (or `dial Priya` / `call Priya`) with no number.
  2. Then produce a name-only item with NO call verb, e.g. `Lunch with Priya`.
  3. Compare whether the Call button appears in each.
- **Expected:** Case 1 — `isCallIntent` matches `\b(call|calling|ring|dial)\b` (case-insensitive), `personName` strips the verb to "Priya", contact resolves → Call button shown, dials Priya. Case 2 — no call verb, `isCallIntent` is false, `resolveCallNumber` returns null → Call button hidden.

#### TM-CONTACT-11 · Call intent present but no contact match → button hidden — `P2` · negative
- **Preconditions:** READ_CONTACTS granted; NO contact named "Zephyrina".
- **Steps:**
  1. Produce a name-only call-intent item: `Call Zephyrina` (no number, no such contact).
  2. Inspect for the Call button.
- **Expected:** `isCallIntent` true and `personName` → "Zephyrina", but all three LIKE tiers miss, so `lookupNumber` returns null and the Call button is hidden (never offers a call it can't place).

#### TM-CONTACT-12 · "Notification from <name>" sender preferred over title — `P2` · edge
- **Preconditions:** READ_CONTACTS granted; contact "Amma" exists with a number.
- **Steps:**
  1. Simulate a chat notification whose source is `Notification from Amma` and whose body has a call verb but no number.
  2. Tap **Call**.
- **Expected:** `personName` extracts "Amma" from the `notification from ` prefix (sender preferred over a title-derived name) and resolves it; dialer opens with Amma's number. A sender that is itself a number (`extractFirst != null`) is rejected as a name.

### 16. Missed-call capture
_Scope: missed calls → "Call back" suggestions from the call log and chat-app notifications. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/RecentDataScanner.kt`, `TaskMindNotificationListener.kt`, `PhoneUtil.kt`._

> Note: Call-back items are created via `UnderstandingPipeline.addCallback`, which bypasses the LLM, sets `confidence = 0.95`, `type = "todo"`, and a title of `"Call back <who>"`. Dedup is by **title** across both pending suggestions and existing Notes. The bare missed-call card has no date/time. The call-log scan only runs when the **Call log** source toggle is enabled and READ_CALL_LOG is granted; the chat-app path requires **Notifications** enabled + notification-listener access.

#### TM-MISSCALL-01 · Cellular missed call → dialable "Call back" — `P0` · positive
- **Preconditions:** Call log source enabled; READ_CALL_LOG granted; a recent **missed** cellular call from a known number exists in the call log (within the scan window).
- **Steps:**
  1. Place/simulate a missed call so it lands in the call log (`emulator: emu call <number>` then cancel, or use a real missed call; on hardware ensure it's within the incremental scan lookback).
  2. Trigger a scan (Inbox pull-to-refresh, or wait for the periodic worker).
  3. Open the resulting Inbox suggestion and inspect it.
- **Expected:** A pending suggestion titled **"Call back <cached name or number>"**, summary **"Missed call · <normalized number>"**, type `todo`, confidence `0.95`. The **Call** button is present and dials the real number (from `CallLog.Calls.NUMBER`, normalized), because the number came straight from the log, not the LLM.

#### TM-MISSCALL-02 · Chat-app missed-call notification → "Call back" (never in call log) — `P0` · positive
- **Preconditions:** Notifications source enabled; notification-listener access granted; READ_CONTACTS granted with the caller in Contacts; allowlist empty or includes the chat app.
- **Steps:**
  1. Generate a WhatsApp/Telegram **missed call** so the OS posts a "Missed voice call" notification (title = caller name, e.g. "Aarav"; not from a dialer package).
  2. Trigger/await processing.
  3. Inspect the Inbox.
- **Expected:** `TaskMindNotificationListener` detects it via `PhoneUtil.missedCallName` (matches `^…missed (voice|video|group|audio)? calls?`) BEFORE the `isRelevant` CATEGORY_CALL drop, and calls `addCallback(displayName = "Aarav", number = null)`. A "Call back Aarav" suggestion appears; its Call button resolves "Aarav" via Contacts (name-only, no number in the notification).

#### TM-MISSCALL-03 · Dialer-package missed call notification is skipped (no dupe) — `P0` · negative
- **Preconditions:** Notifications + Call log both enabled.
- **Steps:**
  1. Receive a real cellular missed call so BOTH the Samsung dialer posts a "Missed call" notification AND the call log records it.
  2. Trigger a scan.
  3. Inspect the Inbox.
- **Expected:** Exactly ONE "Call back" suggestion (from the call log, with the real number). The notification from `com.samsung.android.dialer` (in `PHONE_PACKAGES`) is short-circuited: `missedCaller` is forced to null for phone packages, and the CATEGORY_CALL notification is then dropped by `isRelevant`. No name-only duplicate.

#### TM-MISSCALL-04 · Private / unknown caller skipped — `P1` · negative
- **Preconditions:** Call log enabled; READ_CALL_LOG granted; a missed call from a withheld/unknown number (blank or non-dialable `NUMBER`) in the log.
- **Steps:**
  1. Simulate a missed call with no caller id.
  2. Trigger a scan.
  3. Inspect the Inbox.
- **Expected:** No "Call back" suggestion. `addCallback` requires `named != null || dialable != null`; a null/blank name and a number with `< 5` digits both fail (`dialable` requires `>= 5` digits), so it returns early — nothing we could dial back is created.

#### TM-MISSCALL-05 · Email-titled service "missed call" notification skipped — `P1` · negative
- **Preconditions:** Notifications enabled; notification-listener access granted.
- **Steps:**
  1. Simulate a notification whose title/body is a missed-call phrase but whose caller resolves to an email, e.g. title `Missed call` / body `from you@gmail.com` (a Google-services style notification).
  2. Inspect the Inbox.
- **Expected:** No "Call back you@gmail.com" item. `missedCallName` rejects the caller via `isLookupableName` (contains `@`), returning null; and if the path ever reached `addCallback`, its `named` filter (`!it.contains('@')`) would also reject it. An email can't be dialed or looked up.

#### TM-MISSCALL-06 · No duplicate call-backs (title dedup) — `P0` · edge
- **Preconditions:** Call log enabled; one missed call from "Mom" already produced a pending "Call back Mom" suggestion.
- **Steps:**
  1. Trigger a second scan over an overlapping window (re-refresh the Inbox) without any new call.
  2. Optionally have the chat app re-post the same missed-call notification.
  3. Count "Call back Mom" items.
- **Expected:** Still exactly one "Call back Mom" pending suggestion. `addCallback` holds `callbackMutex` and checks `ExtractionHeuristics.isDuplicate(title, null, pending + notes)`; the matching title is found and the insert is skipped. The mutex also prevents two simultaneous re-posts from both passing the check.

#### TM-MISSCALL-07 · Already-approved call-back not recreated — `P1` · edge
- **Preconditions:** A "Call back Mom" suggestion was approved earlier (now a Note); the same missed call is still within the scan window.
- **Steps:**
  1. Trigger a scan that re-reads the same missed call.
  2. Inspect Inbox and Notes.
- **Expected:** No new pending "Call back Mom". Dedup compares the title against `pending + notes` (approved notes included), so an already-approved call-back is not re-suggested.

#### TM-MISSCALL-08 · Missed-call phrase detection is start-anchored — `P1` · edge
- **Preconditions:** Notifications enabled; notification-listener access granted.
- **Steps:**
  1. Simulate a normal chat message notification whose body reads `Sorry I missed your call earlier` (a sentence, not a missed-call header), title = a contact name.
  2. Inspect the Inbox.
- **Expected:** No "Call back" suggestion is created from the missed-call path. `MISSED_CALL` is anchored at start (`^\s*\W*missed …`), so "Sorry I missed your call" does NOT match; the notification instead flows through the normal relevance/LLM path as an ordinary message.

#### TM-MISSCALL-09 · "Missed video/group call" variants recognized — `P2` · positive
- **Preconditions:** Notifications + listener access; contact "Ria" in Contacts.
- **Steps:**
  1. Simulate a chat notification title `Missed group video call` with sender "Ria" (or body `Missed video call from Ria`).
  2. Inspect the Inbox.
- **Expected:** Recognized as a missed call (`(voice|video|group|audio){0,2} calls?` matches "group video call"); a "Call back Ria" suggestion is created, Ria resolved from Contacts.

#### TM-MISSCALL-10 · Non-missed call-log entries do not create call-backs — `P2` · negative
- **Preconditions:** Call log enabled; READ_CALL_LOG granted; a recent **incoming/answered** and an **outgoing** call (non-missed) in the log, no missed calls.
- **Steps:**
  1. Trigger a scan.
  2. Inspect the Inbox.
- **Expected:** No "Call back" suggestions from these. Only `CallLog.Calls.MISSED_TYPE` rows call `addCallback`; incoming/outgoing rows are sent through the LLM path as `"<Incoming/Outgoing> call with <number> lasting <n> seconds."`, which the model typically drops as non-actionable (no call-back card).

#### TM-MISSCALL-11 · Call log source disabled → no capture — `P1` · negative
- **Preconditions:** Call log source toggle **OFF** in Sources; a missed cellular call present in the log.
- **Steps:**
  1. Trigger a scan.
  2. Inspect the Inbox.
- **Expected:** No "Call back" suggestion from the call log. `scanSince` runs `scanCalls` only when `isCallLogEnabled` is true; with the toggle off the call log is never read.

### 17. Transcription (Vosk)
_Scope: on-device speech-to-text — model check/download, voice-note capture, live recording observer, id-dedup, and null guards. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/transcription/VoskTranscriber.kt`._

#### TM-STT-01 · Check with no model installed — `P1` · negative
- **Preconditions:** Fresh install; no `filesDir/vosk-model/` dir and no `filesDir/vosk-model.zip`. Settings tab open.
- **Steps:** 1. Open Settings -> "Transcription (Audio)" card. 2. Tap **Check**.
- **Expected:** Status text flashes "Checking transcription model…" then resolves to "Model not ready: No Vosk model at <path>", where `<path>` equals the `filesDir/vosk-model` absolute path shown in the card body. (`tryLoad()` returns `IllegalStateException` when `isModelPresent()` is false.)

#### TM-STT-02 · In-app download shows live progress then verifies — `P0` · positive
- **Preconditions:** Network available; no model present. Settings -> Transcription card.
- **Steps:** 1. Tap **Download model**. 2. Watch the status line. 3. Wait for completion.
- **Expected:** Status starts at "Downloading model… 0%" and updates monotonically ("Downloading model… 1%"…"100%") — each distinct percent is emitted once (per `lastPct` dedup in `ModelDownloader`). The **Download model** button is disabled while status starts with "Downloading". On success it shows "Installing…", then `checkTranscriptionModel()` runs and the line ends as "✓ Vosk model loaded — transcription runs offline." (URL fetched: `vosk-model-small-en-in-0.4.zip`.)

#### TM-STT-03 · Download failure surfaces error, button re-enables — `P1` · negative
- **Preconditions:** Disable network (airplane mode) before download, OR point to an unreachable host.
- **Steps:** 1. Tap **Download model**. 2. Wait for the request to time out (connect 30s / read 60s).
- **Expected:** Status ends as "Download failed: <message>" (e.g. a UnknownHostException/timeout message, or "HTTP <code>" for a non-2xx). No `vosk-model/` is unpacked; the `.part` temp is never promoted to `vosk-model.zip`. **Download model** becomes enabled again (status no longer starts with "Downloading").

#### TM-STT-04 · Check after a valid model is present — `P0` · positive
- **Preconditions:** Push an unpacked model so `filesDir/vosk-model/<.../conf>` exists (adb push the unzipped `vosk-model-small-en-in-0.4` into `vosk-model/`), OR complete TM-STT-02.
- **Steps:** 1. Settings -> Transcription. 2. Tap **Check**.
- **Expected:** "✓ Vosk model loaded — transcription runs offline." `findModelRoot` locates `conf/` even when nested one folder deep (walkTopDown).

#### TM-STT-05 · Voice-note mic → on-device transcript → Inbox suggestion — `P0` · positive
- **Preconditions:** Vosk model installed (TM-STT-04). Mic permission grantable. Inbox tab open.
- **Steps:** 1. Tap the floating **mic** FAB (contentDescription "Add by voice"). 2. Grant RECORD_AUDIO if prompted. 3. In the "Listening…" dialog speak a clear actionable line (e.g. "remind me to call the dentist tomorrow"). 4. Tap **Stop**.
- **Expected:** Dialog dismisses; FAB shows a CircularProgressIndicator while `isProcessingVoice`. The recording (cache `voice_note_*.m4a`) is decoded to 16 kHz mono PCM and transcribed on-device, then `pipeline.processText("Voice note", transcript)` runs. Snackbar "Added to your inbox for review." appears and a new pending suggestion (source "Voice note") shows in the Inbox. The temp file is deleted.

#### TM-STT-06 · Mic permission denied — `P1` · negative
- **Preconditions:** RECORD_AUDIO not yet granted; deny on the system prompt.
- **Steps:** 1. Tap the mic FAB. 2. On the OS permission dialog, tap **Deny**.
- **Expected:** No recording starts; snackbar "Microphone permission is needed for voice input." No suggestion created.

#### TM-STT-07 · Stop almost immediately (no frames) — `P1` · edge
- **Preconditions:** Vosk model installed; mic granted.
- **Steps:** 1. Tap mic FAB. 2. In the "Listening…" dialog tap **Stop** instantly (sub-second).
- **Expected:** `recorder.stop()` throws internally and returns null (partial file deleted). Snackbar "Didn't catch that — please try again." No suggestion, no "processing" hang (FAB returns to mic icon).

#### TM-STT-08 · Silent / unintelligible recording — `P1` · edge
- **Preconditions:** Vosk model installed; mic granted; quiet room.
- **Steps:** 1. Tap mic FAB. 2. Record ~3s of silence. 3. Tap **Stop**.
- **Expected:** Transcription returns null/blank (trimmed result `ifBlank { null }`), so `addVoiceNote` returns early without inserting. Snackbar shows the default "Didn't catch that — please try again." No suggestion created. (PCM decoded but blank text → no pipeline call.)

#### TM-STT-09 · Voice note with model missing — `P1` · negative
- **Preconditions:** No Vosk model installed; mic granted.
- **Steps:** 1. Tap mic FAB. 2. Record a few seconds. 3. Tap **Stop**.
- **Expected:** `addVoiceNote` detects `!isModelPresent()` and reports snackbar "Add an offline voice model in Settings to use voice input." No transcription attempted; temp file deleted; no suggestion.

#### TM-STT-10 · New recording auto-transcribed by live observer — `P0` · positive
- **Preconditions:** Vosk model installed. "Voice/Call Recordings" source toggle ON in Sources (so the foreground service registers the audio MediaStore observer). App in background.
- **Steps:** 1. Push/record a speech audio file into a MediaStore folder whose RELATIVE_PATH contains "Recordings" or "Call" with `IS_MUSIC=0` (e.g. `adb push clip.m4a /sdcard/Recordings/` then trigger a media scan). 2. Wait for the ContentObserver to fire.
- **Expected:** `scanAudioRecent()` runs (last 5-min window), finds the new id, transcribes on-device, and `pipeline.processText("Recording: <displayName>", transcript)` produces a pending Inbox suggestion. Logcat tag `RecentDataScanner` shows "Transcribed <name> -> N chars".

#### TM-STT-11 · Same file not re-processed (id-dedup) — `P1` · edge
- **Preconditions:** TM-STT-10 already processed a recording (its id is in `processedAudioIds`).
- **Steps:** 1. Touch the same audio file to bump DATE_MODIFIED (or trigger another media-change). 2. Let the observer fire / run a manual Inbox Refresh.
- **Expected:** The cursor row whose `id.toString() in processed` is skipped (`continue`) — no second transcription, no duplicate suggestion. Note: the id is recorded via `addProcessedAudioId` BEFORE checking the transcript, so even a file that yielded null is never retried.

#### TM-STT-12 · Audio source ON but no model — `P2` · negative
- **Preconditions:** "Voice/Call Recordings" ON; no Vosk model installed.
- **Steps:** 1. Drop a new recording into `/sdcard/Recordings/`. 2. Trigger observer / Refresh.
- **Expected:** `scanAudio` returns early (logcat "Audio enabled but no Vosk model present; skipping"); no transcription, no suggestion, and no id added to `processedAudioIds`.

### 18. OCR (Tesseract)
_Scope: on-device screenshot text — model check/download, shared-image OCR, live screenshot observer, id-dedup, null guards. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/ocr/OcrEngine.kt`._

#### TM-OCR-01 · Check with no model — `P1` · negative
- **Preconditions:** Fresh install; no `filesDir/tessdata/eng.traineddata`. Settings -> "Screenshot OCR" card.
- **Steps:** 1. Tap **Check**.
- **Expected:** Status flashes "Checking OCR model…" then "Model not ready: No OCR model at <path>", where `<path>` is the `filesDir/tessdata/eng.traineddata` absolute path shown in the card. (`tryLoad()` short-circuits on `!isModelPresent()`.)

#### TM-OCR-02 · In-app download with progress, then verify — `P0` · positive
- **Preconditions:** Network available; no model present.
- **Steps:** 1. Settings -> Screenshot OCR. 2. Tap **Download model**. 3. Watch status.
- **Expected:** Status moves "Downloading model… 0%" → … → "100%" (each percent once), **Download model** disabled while downloading, then auto-runs `checkOcrModel()` ending at "✓ Tesseract model loaded — screenshot OCR runs offline." File written to `filesDir/tessdata/eng.traineddata` (~4 MB, from `tessdata_fast/raw/main/eng.traineddata`).
> Note: Unlike the Vosk path, the OCR download does NOT show an "Installing…" intermediate string — it goes straight from the percent line into `checkOcrModel()`.

#### TM-OCR-03 · Download failure — `P1` · negative
- **Preconditions:** Airplane mode on before tapping.
- **Steps:** 1. Tap **Download model**. 2. Wait for failure.
- **Expected:** Status ends "Download failed: <message>". No `eng.traineddata` promoted from the `.part` temp; **Download model** re-enables.

#### TM-OCR-04 · Check after model present — `P0` · positive
- **Preconditions:** `eng.traineddata` pushed to `filesDir/tessdata/` (or TM-OCR-02 done).
- **Steps:** 1. Tap **Check**.
- **Expected:** "✓ Tesseract model loaded — screenshot OCR runs offline." (`tess.init(filesDir, "eng")` succeeds; dataPath is filesDir, which contains `tessdata/`.)

#### TM-OCR-05 · Shared image is OCR'd into a suggestion — `P0` · positive
- **Preconditions:** OCR model installed. A photo/screenshot containing legible text (e.g. an event flyer with a date).
- **Steps:** 1. From Gallery/Photos, **Share** the image to TaskMind. 2. Wait for the CaptureWorker job.
- **Expected:** Image copied into app storage and `CaptureWorker` runs `ocrEngine.recognize(file)`; the recognized text (whitespace-collapsed, trimmed) is fed to `pipeline.processText(source, text)`, producing a pending Inbox suggestion. The temp image file is deleted after OCR (`file.delete()`).

#### TM-OCR-06 · Shared image with no model — `P1` · negative
- **Preconditions:** No OCR model installed.
- **Steps:** 1. Share an image to TaskMind.
- **Expected:** `recognizeBitmap` hits `!isModelPresent()` → returns null; `CaptureWorker` sees blank text and does NOT call the pipeline; `Result.success()` (job not retried). No suggestion. The shared temp file is still deleted.

#### TM-OCR-07 · New screenshot auto-OCR'd by media observer — `P0` · positive
- **Preconditions:** OCR model installed. "Screenshots (OCR)" source ON (foreground service registers the Images observer). App backgrounded.
- **Steps:** 1. Take a screenshot containing text (or `adb push` a PNG into `/sdcard/Pictures/Screenshots/` and trigger a media scan so RELATIVE_PATH contains "Screenshots"). 2. Wait for the observer.
- **Expected:** `scanImagesRecent()` runs (5-min window), finds the new id, OCRs on-device, and `pipeline.processText("Screenshot: <name>", text)` creates a pending suggestion. Logcat `RecentDataScanner` "OCR'd <name> -> N chars".

#### TM-OCR-08 · Image with no readable text — `P1` · edge
- **Preconditions:** OCR model installed; source ON.
- **Steps:** 1. Add a blank/solid-color or photo-only screenshot to the Screenshots folder. 2. Let the observer fire.
- **Expected:** `getUTF8Text()` is blank → `ifBlank { null }` → no pipeline call, no suggestion. The id is still added to `processedImageIds` (recorded before the null check), so it won't be retried. Logcat shows "OCR'd <name> -> 0 chars".

#### TM-OCR-09 · Same screenshot not re-processed (id-dedup) — `P1` · edge
- **Preconditions:** TM-OCR-07 processed a screenshot (its id in `processedImageIds`).
- **Steps:** 1. Re-touch the file to bump DATE_MODIFIED (or re-trigger a media change). 2. Let the observer fire or run Inbox **Refresh**.
- **Expected:** Row with `id.toString() in processed` is skipped (`continue`); no duplicate OCR, no duplicate suggestion.

#### TM-OCR-10 · Non-Screenshots image ignored — `P2` · negative
- **Preconditions:** OCR model installed; source ON.
- **Steps:** 1. Add a text-bearing image to a non-Screenshots folder, e.g. `/sdcard/DCIM/Camera/`. 2. Let the observer fire / Refresh.
- **Expected:** The `RELATIVE_PATH LIKE '%Screenshots%'` selection excludes it; not OCR'd, no suggestion. (Only the Screenshots folder is scanned for OCR.)

### 19. App-usage digest
_Scope: once-per-day screen-time note built from YESTERDAY's data, inserted as a note-type suggestion at confidence 1.0. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/AppUsageCollector.kt`._

#### TM-USAGE-01 · Daily digest generated and inserted — `P0` · positive
- **Preconditions:** "App Usage" source ON; Usage Access granted to TaskMind (Settings > Special access > Usage data access); yesterday had foreground usage; `lastAppUsageDigestDate` != today (clear app data or wait to a fresh day).
- **Steps:** 1. Trigger a scan (Inbox **Refresh**, or let the periodic worker run). 2. Open Inbox.
- **Expected:** One new pending suggestion: source "App Usage", `type` = "note", `confidence` = 1.0, status "pending". Title = "Screen time — <MMM d>" for yesterday's date (e.g. "Screen time — Jun 29"). Body starts "Total screen time: <Xh Ym>." then "Top apps:" with up to 5 apps ranked descending by foreground time. A pending notification is posted (`notifier.notifyPending()`).

#### TM-USAGE-02 · Only generated once per day (date gate) — `P0` · edge
- **Preconditions:** TM-USAGE-01 already produced today's digest (`lastAppUsageDigestDate` == today).
- **Steps:** 1. Tap Inbox **Refresh** again (and/or wait for another periodic tick). 2. Inspect Inbox.
- **Expected:** No second "App Usage" suggestion is created — the first line `if (lastAppUsageDigestDate == today) return` short-circuits before querying UsageStats. The original single digest remains.

#### TM-USAGE-03 · Uses yesterday's window, not today — `P1` · edge
- **Preconditions:** Usage Access granted; distinct app usage yesterday vs today.
- **Steps:** 1. Generate a fresh digest. 2. Compare body totals to the device's own "Digital Wellbeing" figures for **yesterday**.
- **Expected:** Totals reflect the interval `[yesterday 00:00, today 00:00)` in the system default zone (queryUsageStats `INTERVAL_DAILY` over yesterday), and the title date label is yesterday's. Today's in-progress usage is excluded.

#### TM-USAGE-04 · TaskMind excludes itself — `P1` · edge
- **Preconditions:** Use TaskMind heavily yesterday so it would otherwise rank in the top apps.
- **Steps:** 1. Generate the digest. 2. Read the "Top apps" list.
- **Expected:** `com.rajasudhan.taskmind` never appears (filtered by `it.packageName != context.packageName`); its time is not counted in the entries shown.

#### TM-USAGE-05 · No-op when Usage Access denied — `P0` · negative
- **Preconditions:** "App Usage" source ON, but Usage Access NOT granted to TaskMind; `lastAppUsageDigestDate` != today.
- **Steps:** 1. Tap Inbox **Refresh**. 2. Inspect Inbox.
- **Expected:** `queryUsageStats` returns empty → `if (raw.isEmpty()) return` fires BEFORE the date is marked, so no digest is inserted and `lastAppUsageDigestDate` is NOT advanced (a later grant on the same day can still produce the digest). No crash.

#### TM-USAGE-06 · No usable data → marked done, no insert — `P1` · edge
- **Preconditions:** Usage Access granted, but yesterday has only zero-foreground entries (e.g. emulator with no real usage) so `stats` ends empty after the `totalTimeInForeground > 0` filter.
- **Steps:** 1. Refresh to run the collector. 2. Refresh again.
- **Expected:** First run sets `lastAppUsageDigestDate = today` then `if (stats.isEmpty()) return` — no suggestion inserted, no notification. The second refresh is gated out by the date. (Distinct from TM-USAGE-05: here the date IS advanced because raw was non-empty.)

#### TM-USAGE-07 · Approving the digest moves it to Notes — `P1` · positive
- **Preconditions:** A pending "App Usage" digest exists in Inbox.
- **Steps:** 1. In Inbox, approve/keep the digest. 2. Open the Notes tab.
- **Expected:** Since `type` = "note", it lands in Notes as a screen-time note (title "Screen time — <MMM d>"), with the body intact. (No date/time → no calendar write.)

#### TM-USAGE-08 · Duration formatting boundaries — `P2` · edge
- **Preconditions:** Inspect `AppUsageDigest.humanDuration` output via a generated digest (or unit test on JVM).
- **Steps:** 1. Compare formatted strings for representative totals.
- **Expected:** <1h shows "Ym" only (e.g. 45 min → "45m"); ≥1h shows "Xh Ym" (90 min → "1h 30m"); sub-minute rounds down to "0m". Top-apps list is capped at 5 (`topN = 5`).

### 20. Background scan & WorkManager
_Scope: periodic + incremental scanning, manual refresh, reschedule on frequency change, retention purge, and watermark advancement. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/RecentDataScanner.kt` + `TaskMindApp.kt`._

#### TM-SCAN-01 · Periodic job scheduled at default 30 min — `P0` · positive
- **Preconditions:** Fresh install; default `scanFrequencyMinutes` = 30.
- **Steps:** 1. Launch the app once. 2. Inspect WorkManager: `adb shell dumpsys jobscheduler | grep taskmind`, or use the Background Task Inspector.
- **Expected:** A unique periodic work named `taskmind_periodic_scan` (DataCollectionWorker) exists with a 30-minute interval and a `requiresBatteryNotLow` constraint. On launch it's enqueued with `ExistingPeriodicWorkPolicy.KEEP` (existing schedule preserved, not replaced).

#### TM-SCAN-02 · Periodic scan runs the incremental gap — `P0` · positive
- **Preconditions:** App installed; at least one source ON (e.g. SMS). Note current `lastScanAt`.
- **Steps:** 1. Force-run the worker: `adb shell cmd jobscheduler run -f com.rajasudhan.taskmind <job-id>` (or use Inspector "Run"). 2. Inspect Inbox + `lastScanAt`.
- **Expected:** `doWork` calls `scanner.scanIncremental()` — scans every enabled source since the prior watermark, runs `runRetentionCleanup()`, returns `Result.success()`. `lastScanAt` advances to ~now. New items in the window become pending suggestions.

#### TM-SCAN-03 · Battery-not-low constraint defers the scan — `P1` · edge
- **Preconditions:** Periodic job scheduled.
- **Steps:** 1. Put device into low-battery (`adb shell dumpsys battery set level 5` and unplug, or use the emulator battery controls). 2. Wait for the next scheduled window.
- **Expected:** The periodic run is deferred while battery is low (constraint `setRequiresBatteryNotLow(true)`); it executes once battery recovers. Because the window is "since lastScanAt", the deferred run still covers the skipped gap — nothing is dropped.

#### TM-SCAN-04 · Manual Refresh scans the same gap as periodic — `P0` · positive
- **Preconditions:** Several minutes since the last scan; SMS source ON. Send/inject 2 SMS in the gap (`adb emu sms send 555 "Pay rent Friday"`), or use Settings -> Test Extraction to avoid real SMS.
- **Steps:** 1. Inbox -> overflow menu -> **Refresh** (or the empty-state **Refresh** pill). 2. Observe.
- **Expected:** The toolbar shows a spinner / pill reads "Refreshing…" while `isRefreshing`. `refreshRecentData()` calls the SAME `scanner.scanIncremental()` as the worker — both advance and share `lastScanAt`. New items appear as pending suggestions; spinner clears when done.

#### TM-SCAN-05 · First-ever scan looks back only 15 min — `P1` · edge
- **Preconditions:** Fresh install, `lastScanAt` == 0. An SMS arrived 20 min ago and another 5 min ago.
- **Steps:** 1. Trigger the first scan (Refresh). 2. Inspect which messages produced suggestions.
- **Expected:** With `last <= 0`, `since = now - 15min` (FIRST_RUN_LOOKBACK_MS). Only the 5-min-old SMS is in range; the 20-min-old one is NOT scanned. Avoids an initial flood. `lastScanAt` set to now.

#### TM-SCAN-06 · Long-dormant app caps lookback at 24h — `P1` · edge
- **Preconditions:** App not scanned for >24h (set `lastScanAt` to 3 days ago via a long idle period, or simulate). SMS source ON.
- **Steps:** 1. Trigger a scan.
- **Expected:** `since = maxOf(last, now - 24h)` → the window is clamped to the last 24h, NOT the full 3-day gap (`MAX_LOOKBACK_MS`). Messages older than 24h are not re-scanned.

#### TM-SCAN-07 · Watermark advances even when a source fails — `P1` · edge
- **Preconditions:** Multiple sources ON; arrange one to fail (e.g. Email ON but Gmail token revoked / no account; or Audio ON with no Vosk model).
- **Steps:** 1. Trigger a scan. 2. Inspect `lastScanAt` and logcat.
- **Expected:** Each source is wrapped in `runCatching` and `appUsageCollector`/scanners swallow their own errors, so `scanIncremental` reaches `settingsManager.lastScanAt = now`. The failed source logs a skip (e.g. "Email enabled but no Gmail account connected; skipping") but the watermark still advances; healthy sources still produced suggestions.

#### TM-SCAN-08 · Changing scan frequency reschedules (UPDATE) — `P0` · positive
- **Preconditions:** Default 30-min job running.
- **Steps:** 1. Settings -> scroll to "Scan frequency" dropdown. 2. Select "Hourly" (60). 3. Inspect WorkManager.
- **Expected:** `updateScanFrequency(60)` persists the value, updates the dropdown label to "Hourly", and calls `scheduleScan(replace = true)` → the unique work `taskmind_periodic_scan` is re-enqueued with `ExistingPeriodicWorkPolicy.UPDATE` at a 60-minute interval (no duplicate job). Options available: 15 min, 30 min, Hourly, Every 3 hours, Every 6 hours.

#### TM-SCAN-09 · Selecting 15 min hits the WorkManager floor — `P1` · boundary
- **Preconditions:** Job running.
- **Steps:** 1. Settings -> Scan frequency -> select "Every 15 min". 2. Inspect the scheduled interval.
- **Expected:** Job rescheduled at 15 minutes — the minimum WorkManager allows for periodic work (floor 15 min). Selecting it does not error; label reads "Every 15 min".

#### TM-SCAN-10 · Retention purge runs during scan (actioned suggestions) — `P1` · positive
- **Preconditions:** Have at least one approved and one rejected suggestion (status != "pending") plus a pending one.
- **Steps:** 1. Trigger a scan (periodic run or Refresh that drives the worker; note retention cleanup runs in `DataCollectionWorker`, so force-run that worker). 2. Inspect the suggestions table / Inbox.
- **Expected:** `runRetentionCleanup()` calls `deletePurgeableSuggestions()` (`DELETE FROM suggestions WHERE status != 'pending'`) — approved/rejected rows are removed; the pending suggestion remains in the Inbox.

#### TM-SCAN-11 · Notes older than retention are deleted — `P2` · edge
- **Preconditions:** Set retention to a small value (e.g. 1 day) in Settings; create a note with `createdDate` older than the cutoff and one newer.
- **Steps:** 1. Force-run DataCollectionWorker. 2. Inspect Notes.
- **Expected:** With `retentionDays > 0`, notes with `createdDate < (now - days*24h)` are deleted (`deleteNotesOlderThan`); newer notes survive. With the default `retentionDays = 0`, the cutoff branch is skipped and notes are kept forever.

#### TM-SCAN-12 · Worker exception triggers retry — `P2` · negative
- **Preconditions:** Induce a failure inside `doWork` that escapes the inner `runCatching` (e.g. DB locked during the cleanup path is itself wrapped; force a scanner-level throw not swallowed — best simulated in instrumentation).
- **Steps:** 1. Trigger the worker under the fault. 2. Inspect WorkManager state.
- **Expected:** `doWork` catch returns `Result.retry()`; WorkManager re-enqueues with backoff rather than dropping the run. (Per-source errors are swallowed and do NOT cause a retry — only an exception outside the source-level `runCatching`.)

- `apps\taskmind\src\main\java\com\rajasudhan\taskmind\data\source\transcription\VoskTranscriber.kt`
- `...\data\source\transcription\AudioRecorder.kt`, `...\TranscriptionProvider.kt`
- `...\data\source\ModelDownloader.kt`, `...\ocr\OcrEngine.kt`
- `...\data\source\AppUsageCollector.kt`, `...\AppUsageDigest.kt`
- `...\data\source\DataCollectionWorker.kt`, `...\CaptureWorker.kt`, `...\RecentDataScanner.kt`
- `...\TaskMindApp.kt`, `...\data\source\TaskMindForegroundService.kt`, `...\data\source\SettingsManager.kt`, `...\data\source\SourceManager.kt`, `...\data\local\TaskMindDao.kt`
- UI: `...\ui\settings\SettingsScreen.kt`, `...\SettingsViewModel.kt`, `...\ui\inbox\InboxScreen.kt`, `...\InboxViewModel.kt`

- **Retention** runs only inside `DataCollectionWorker.runRetentionCleanup()` (background scan), using `createdDate < cutoff` where `cutoff = now - days*24h`. `days=0` keeps forever. Suggestions purge only when `status != 'pending'`.
- **Permissions panel** (`loadPermissionStatuses`) is loaded once on `LaunchedEffect(Unit)` — NOT refreshed on `ON_RESUME` (only `canEnforceLock` is). It's read-only with no deep links. Deep links live in the Sources screen. This is a discrepancy to flag for the PERM suite.
- Export status text: `"✓ Exported $count note(s)."` / `"Export failed."` Filename suggested: `taskmind-notes.json`. SAF: `CreateDocument("application/json")`.
- Backup magic `TMBK1`, version byte 1, salt 16, iv 12, PBKDF2-HMAC-SHA256 150k iters, AES-256-GCM. Min passphrase 6. Backup filename `taskmind-backup-<yyyyMMdd>.tmbk`, MIME `application/octet-stream`. Restore picks `*/*`.
- Egress empty-state: `"✓ No data has left this device. Understanding runs on-device by default."` Each event row: `<MMM d, HH:mm> · <host> — <purpose>`, capped display 15, store cap 100, newest-first.

### 21. Data management
_Scope: export, retention, wipe. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/ui/settings/SettingsViewModel.kt`._

#### TM-DATA-01 · Export notes to JSON via SAF picker — `P0` · positive
- **Preconditions:** App unlocked; at least 3 approved notes exist in the Notes tab (approve a few via Test Extraction first).
- **Steps:** 1. Open Privacy (Settings) tab, scroll to "Data Management". 2. Tap "Export Notes (JSON)". 3. In the system Create-document sheet, the suggested filename is `taskmind-notes.json`; choose a location and confirm. 4. Return to the app.
- **Expected:** A status line under the button reads `✓ Exported 3 note(s).` (count = number of rows from `dao.getNotesList()`). The created file is valid pretty-printed JSON (2-space indent) — an array of Note objects ordered by `createdDate DESC`. App lock does NOT trigger on return (export path calls `AppLock.expectResult()`).

#### TM-DATA-02 · Export with zero notes — `P1` · edge
- **Preconditions:** No approved notes exist (run Delete All Private Data first, or fresh install with nothing approved).
- **Steps:** 1. Data Management → "Export Notes (JSON)". 2. Confirm a filename in the picker.
- **Expected:** Status reads `✓ Exported 0 note(s).` and the file contains an empty JSON array `[]` (the indented adapter still writes valid JSON). No crash.

#### TM-DATA-03 · Export cancelled in the picker — `P2` · negative
- **Preconditions:** Any number of notes.
- **Steps:** 1. Tap "Export Notes (JSON)". 2. In the SAF sheet, press Back / cancel without choosing a destination.
- **Expected:** No file written. `exportStatus` stays unchanged (the launcher callback gets a null uri and `exportNotesToUri` is never called). No status line appears / no toast. No crash.

#### TM-DATA-04 · Export to an unwritable target fails gracefully — `P1` · negative
- **Preconditions:** Notes exist; pick a provider/location where the write stream can't be opened (e.g. a read-only mount or a provider that returns no output stream).
- **Steps:** 1. Tap "Export Notes (JSON)" and select the problem destination.
- **Expected:** Status reads `Export failed.` (the `runCatching{}` returns null when `openOutputStream` is null or write throws). App does not crash.

#### TM-DATA-05 · Retention dropdown persists the selection — `P1` · positive
- **Preconditions:** App unlocked.
- **Steps:** 1. Data Management → open the "Keep notes for" dropdown. 2. Confirm options are exactly: Keep forever / 30 days / 90 days / 1 year. 3. Select "90 days". 4. Reopen the dropdown.
- **Expected:** Field shows "90 days"; `retentionDays = 90` is stored in EncryptedSharedPreferences. The label maps from the stored int (90 → "90 days").

#### TM-DATA-06 · Retention deletes notes older than the window on next scan — `P0` · positive
- **Preconditions:** Retention set to "30 days". At least one note whose `createdDate` is older than 30 days (insert/approve, then move device clock forward >30 days, OR seed an old note) and one note created today.
- **Steps:** 1. Trigger a background scan (wait for the WorkManager periodic run, or force it: `adb shell cmd jobscheduler run -f com.rajasudhan.taskmind <jobId>`, or set scan frequency to 15 min and wait). 2. Open the Notes tab.
- **Expected:** `DataCollectionWorker.runRetentionCleanup()` computes `cutoff = now − 30·24·60·60·1000` and runs `DELETE FROM notes WHERE createdDate < :cutoff`. The >30-day-old note is gone; today's note remains.
  > Note: Retention is enforced ONLY by the background `DataCollectionWorker`, never immediately when the dropdown changes. Changing the dropdown alone deletes nothing until the next scan runs.

#### TM-DATA-07 · "Keep forever" deletes nothing — `P1` · edge
- **Preconditions:** Retention set to "Keep forever" (`retentionDays = 0`, the default). Notes of various ages exist, including very old ones.
- **Steps:** 1. Trigger a background scan. 2. Check the Notes tab.
- **Expected:** No notes are deleted. `runRetentionCleanup` skips `deleteNotesOlderThan` entirely because the `days > 0` guard is false. (Actioned/non-pending suggestions are still purged regardless — that branch is unconditional.)

#### TM-DATA-08 · Retention boundary — note exactly at the cutoff survives — `P2` · boundary
- **Preconditions:** Retention "30 days". A note whose `createdDate` equals the computed cutoff to the millisecond (seed precisely).
- **Steps:** 1. Run a scan.
- **Expected:** The boundary note is NOT deleted — the query is strict `createdDate < cutoff`, so a note exactly at the cutoff is retained.

#### TM-DATA-09 · Delete All Private Data wipes notes + suggestions, app stays usable — `P0` · positive
- **Preconditions:** Several approved notes, several pending Inbox suggestions, a non-default LLM API key, app lock off, theme = Dark.
- **Steps:** 1. Scroll to the bottom of Privacy and tap "Delete All Private Data". 2. Confirm the dialog (title "Delete all data?", body warns it erases notes, suggestions, source toggles, and saved keys/settings) by tapping "Delete". 3. Navigate to Notes and Inbox.
- **Expected:** Dialog dismisses. Notes tab and Inbox are empty (`deleteAllNotes`, `deleteAllSuggestions`, `deleteAllRejectedPatterns` all ran). Settings reset to defaults: API key blank, `useOnDeviceLlm = true`, calendar = Automatic, model path blank, theme back to System, app lock back ON (secure default re-asserted in `clearSettings`). The app remains fully usable — the DB encryption key is intentionally kept, so the emptied DB still opens with no restart.

#### TM-DATA-10 · Cancel the Delete-All dialog is a no-op — `P1` · negative
- **Preconditions:** Notes and suggestions exist.
- **Steps:** 1. Tap "Delete All Private Data". 2. In the dialog tap "Cancel" (or tap outside to dismiss).
- **Expected:** Dialog closes; nothing is deleted; all notes, suggestions, keys, and toggles are intact (`deleteAllData` never called).

#### TM-DATA-11 · Test Extraction → suggestion count reflected in status — `P1` · positive
- **Preconditions:** On-device model loaded (Check on-device model shows the loaded message).
- **Steps:** 1. Data Management area is separate — use "Test Extraction (debug)". 2. Paste an actionable line, e.g. "Pay the electricity bill by Friday". 3. Tap "Run extraction".
- **Expected:** Button shows "Running…" then status reads `✓ Created 1 suggestion(s) — check the Inbox.` (delta of `getPendingSuggestions().size` before/after). A non-actionable/duplicate input instead yields `No action item found (non-actionable, low confidence, or duplicate).` This confirms the pipeline that retention/export operate on.

### 22. Backup & restore
_Scope: encrypted, passphrase-sealed transfer. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/BackupManager.kt`._

#### TM-BACKUP-01 · Create an encrypted backup under a passphrase — `P0` · positive
- **Preconditions:** App unlocked; some notes/suggestions exist; a DB encryption key is present (`db_key` pref, true on any normal install).
- **Steps:** 1. Privacy → "Encrypted Backup & Restore" → "Back up (encrypted)". 2. In the dialog "Set a backup passphrase", enter a ≥6-char passphrase (e.g. `correct horse`). 3. Tap "Continue". 4. In the SAF Create-document sheet the suggested name is `taskmind-backup-<yyyyMMdd>.tmbk` (octet-stream); confirm a location.
- **Expected:** Status reads `✓ Encrypted backup saved. Keep the passphrase safe — it can't be recovered.` The written file begins with ASCII magic `TMBK1`, then version byte `0x01`, then 16-byte salt + 12-byte IV + AES-GCM ciphertext (verify first 5 bytes via `adb pull` + hexdump). App lock does NOT fire on return (`AppLock.expectResult()` called before launch).

#### TM-BACKUP-02 · Passphrase shorter than 6 chars is rejected before any file write — `P1` · negative
- **Preconditions:** App unlocked.
- **Steps:** 1. "Back up (encrypted)". 2. Enter a 5-char passphrase. 3. The dialog's "Continue" is enabled (only blank is disabled), so tap it and pick a destination.
- **Expected:** Status reads `Use a passphrase of at least 6 characters.` No encryption runs and the picked file is left empty/zero-length (guard returns before `backupManager.backup`).
  > Note: The length check is in the ViewModel AFTER the SAF round-trip, so the file picker still appears; the file is created by SAF but never written to.

#### TM-BACKUP-03 · Blank passphrase can't be submitted — `P2` · boundary
- **Preconditions:** Backup dialog open.
- **Steps:** 1. Leave the passphrase field empty.
- **Expected:** "Continue" is disabled (`enabled = passphraseInput.isNotBlank()`); the picker can't be launched.

#### TM-BACKUP-04 · Restore with the CORRECT passphrase → validate → swap → restart → data present — `P0` · positive
- **Preconditions:** A valid `.tmbk` from TM-BACKUP-01 and its passphrase. Current install has DIFFERENT data than the backup (e.g. delete all data first so the difference is obvious).
- **Steps:** 1. "Restore from backup" → enter the correct passphrase → "Continue". 2. In the Open-document picker select the `.tmbk` file. 3. Wait; when the "Restore complete" dialog appears tap "Restart now". 4. After relaunch, unlock and open Notes/Inbox.
- **Expected:** During restore the staged file `taskmind_db.restore` is written, validated with `opensWithKey` (read-only SQLCipher open + `SELECT count(*) FROM sqlite_master`), then atomically `Files.move`d over `taskmind_db`; the bundled `db_key` is committed. Status `✓ Restore complete.`, then a non-dismissable dialog "Restore complete / TaskMind needs to restart to load the restored data." After restart the backed-up notes/suggestions are present (replacing the prior data).

#### TM-BACKUP-05 · Restore with the WRONG passphrase → Failure, live DB untouched — `P0` · negative
- **Preconditions:** A valid `.tmbk`; the live install has known current data.
- **Steps:** 1. "Restore from backup" → enter an INCORRECT passphrase → "Continue". 2. Select the valid `.tmbk`.
- **Expected:** Status reads `Wrong passphrase, or the backup is corrupt.` (GCM tag mismatch → `BadBackupException`). No "restart" dialog. No restart. The live DB and `db_key` are unchanged — the current notes/suggestions are still exactly as before (failure happens during decrypt, well before any staging or swap).

#### TM-BACKUP-06 · Corrupted / truncated backup file → Failure, live DB untouched — `P1` · negative
- **Preconditions:** Take a valid `.tmbk` and corrupt it: truncate it to ~20 bytes, OR flip bytes inside the ciphertext, OR strip the first 5 bytes so the magic is wrong.
- **Steps:** 1. "Restore from backup" → enter any passphrase → select the corrupted file.
- **Expected:** Status reads either `Not a TaskMind backup file.` (bad/missing `TMBK1` magic or too short), `Unsupported backup version.` (version byte ≠ 1), or `Wrong passphrase, or the backup is corrupt.` (tampered ciphertext / truncated GCM). No staging, no swap, no restart. Live DB unchanged.

#### TM-BACKUP-07 · Restore an empty / non-backup file → Failure — `P2` · negative
- **Preconditions:** A 0-byte file or an unrelated file (e.g. a photo) accessible via SAF.
- **Steps:** 1. "Restore from backup" → enter a passphrase → select the non-backup file.
- **Expected:** Status reads `Not a TaskMind backup file.` (envelope shorter than `5 + 1 + 16 + 12` bytes or magic mismatch). No crash; live DB untouched.

#### TM-BACKUP-08 · WAL/SHM siblings are cleaned after a successful restore — `P1` · edge
- **Preconditions:** Generate write activity so `taskmind_db-wal` and `taskmind_db-shm` exist before restore (approve several items). Have a valid `.tmbk` + passphrase. (Requires a debuggable build / rooted access via `run-as` to inspect `/data/data/com.rajasudhan.taskmind/databases/`.)
- **Steps:** 1. Confirm `taskmind_db-wal` / `-shm` exist (`adb shell run-as com.rajasudhan.taskmind ls databases`). 2. Perform a successful restore (TM-BACKUP-04) and restart. 3. List the databases dir again.
- **Expected:** After the atomic swap the old `taskmind_db-wal` and `taskmind_db-shm` are deleted (best-effort `runCatching`). No `.restore` staging file remains (deleted in `finally`). The new `taskmind_db` is the restored file.

#### TM-BACKUP-09 · DB encryption key survives the backup→restore round-trip — `P0` · positive
- **Preconditions:** Fresh install A with data; create a backup. Wipe (Delete All) so install state differs, OR install on device B.
- **Steps:** 1. Back up on A. 2. On the target, restore that backup with the correct passphrase and restart. 3. Browse Notes/Inbox; add a new note.
- **Expected:** The restored DB opens normally after restart — the `db_key` bundled in the ZIP (`ENTRY_KEY`) is committed to `db_key` pref so the restored SQLCipher file opens with the matching key. No "database locked/corrupt" error; new writes succeed. (If the key didn't survive, the restored DB would be unreadable.)

#### TM-BACKUP-10 · Backup before any DB key exists — `P2` · edge
- **Preconditions:** A state where `db_key` pref is absent (extremely early first-run before the DB is initialized — hard to hit normally; document as a guard test).
- **Steps:** 1. Attempt "Back up (encrypted)" with a valid passphrase.
- **Expected:** Status reads `No database key found — nothing to back up yet.` No file content written. No crash.

#### TM-BACKUP-11 · Path-traversal entries in a crafted ZIP are ignored — `P2` · negative/security
- **Preconditions:** A maliciously crafted `.tmbk` whose inner ZIP contains entries like `../../evil` plus the two valid basenames `db_key` and `taskmind_db`, sealed with a known passphrase.
- **Steps:** 1. Restore it with the correct passphrase.
- **Expected:** Only entries whose `substringAfterLast('/')` equals `db_key` or `taskmind_db` are read; stray/traversal entries are ignored. Restore proceeds using just the two recognized basenames (no file written outside the databases dir).

### 23. Privacy / Data Egress
_Scope: the egress audit. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/EgressLogger.kt`._

#### TM-EGRESS-01 · On-device default shows the "nothing left" state — `P0` · positive
- **Preconditions:** Fresh install or after Clear log; Understanding Engine = On-Device (default); no cloud/Gmail calls have occurred.
- **Steps:** 1. Open Privacy. 2. Read the "Data Egress (privacy)" card and the hero at the top.
- **Expected:** The card shows `✓ No data has left this device. Understanding runs on-device by default.` and NO "Clear log" button (empty list branch). The Privacy hero shows `0` with "Nothing left your device today" and "0 outbound · 0 trackers".

#### TM-EGRESS-02 · Enabling Cloud LLM logs one metadata-only entry per Gemini call — `P0` · positive
- **Preconditions:** A valid LLM API key set; Understanding Engine switched to "Cloud (higher accuracy — data leaves device)".
- **Steps:** 1. Use "Test Extraction (debug)": paste an actionable message and tap "Run extraction" (routes through `CloudLlmProvider.generate`). 2. Scroll to the Data Egress card.
- **Expected:** Exactly one new row per cloud call: `<MMM d, HH:mm> · generativelanguage.googleapis.com — Cloud LLM extraction`. The row contains ONLY host/purpose/time — never the pasted text. Today's hero count increments by 1 per call. Running again adds another row (newest first).
  > Note: If the API key is blank, `CloudLlmProvider` short-circuits and returns `{"items":[]}` BEFORE `egressLogger.record(...)`, so NO egress row is logged. Set a key to exercise this case.

#### TM-EGRESS-03 · Egress entries never contain message content — `P0` · negative/security
- **Preconditions:** Cloud LLM enabled with a key; a distinctive secret string in the test text (e.g. "ACCOUNT-9988-SECRET").
- **Steps:** 1. Run that text through Test Extraction (cloud path). 2. Inspect the egress rows on screen. 3. (Optional, debuggable build) dump the `egress_log` value from EncryptedSharedPreferences.
- **Expected:** The logged `EgressEvent` has only `timestamp`, `host`, `purpose` (per the data class) — the secret string appears nowhere in the log row or the persisted JSON. Purpose text is the fixed `"Cloud LLM extraction"`.

#### TM-EGRESS-04 · Gmail fetch is logged — `P1` · positive
- **Preconditions:** A Gmail account connected (Sources tab); recent Primary mail available.
- **Steps:** 1. Trigger a Gmail fetch (manual refresh / background scan that runs `GmailCollector.fetchRecentPrimary`). 2. Open Data Egress.
- **Expected:** A row `… · gmail.googleapis.com — Gmail fetch (recent primary)` is recorded once per fetch call (logged before the API call, so it's recorded even if the API later returns nothing/fails). Email bodies are NOT in the log.

#### TM-EGRESS-05 · Gmail profile lookup (OAuth connect) is logged — `P1` · positive
- **Preconditions:** Connecting a Gmail account that triggers `profileEmail`.
- **Steps:** 1. Connect/refresh a Gmail account so the profile lookup runs.
- **Expected:** A row `… · gmail.googleapis.com — Gmail profile lookup` appears. Metadata only.

#### TM-EGRESS-06 · Clear log empties the audit and returns the "nothing left" state — `P1` · positive
- **Preconditions:** At least one egress row exists (run TM-EGRESS-02 first).
- **Steps:** 1. In the Data Egress card tap "Clear log".
- **Expected:** The list empties immediately; the card flips back to `✓ No data has left this device. …` and the "Clear log" button disappears. The `egress_log` key is removed from EncryptedSharedPreferences (persists across restart).
  > Note: The top Privacy hero counts only events with `timestamp >= start of today`. Clearing removes all rows so the hero also returns to 0 — but note the hero counts "today" specifically, so yesterday's rows never counted toward it even before clearing.

#### TM-EGRESS-07 · Cleared log survives app restart — `P2` · edge
- **Preconditions:** Just performed Clear log.
- **Steps:** 1. Fully kill and relaunch the app. 2. Open Data Egress.
- **Expected:** Still shows the empty state (`load()` returns empty list because the key was removed). No stale rows reappear.

#### TM-EGRESS-08 · Log is capped at 100 entries, newest first — `P2` · boundary
- **Preconditions:** Cloud LLM enabled with a key.
- **Steps:** 1. Run Test Extraction (cloud) more than 100 times (script via repeated taps, or lower-effort: confirm ordering with ~3 calls and reason about the cap). 2. Observe the card (shows up to 15) and, on a debuggable build, the persisted JSON length.
- **Expected:** New entries are prepended (newest first); the persisted list is truncated to the most recent 100 (`take(MAX_EVENTS)`). The UI card renders at most the first 15 rows (`egressEvents.take(15)`).

#### TM-EGRESS-09 · Corrupt persisted log degrades to empty, no crash — `P2` · negative
- **Preconditions:** Debuggable build; write garbage into the `egress_log` pref value.
- **Steps:** 1. Corrupt the stored JSON. 2. Relaunch and open Data Egress.
- **Expected:** `load()` catches the parse exception and returns an empty list — the card shows the "nothing left" state instead of crashing.

### 24. Settings persistence
_Scope: durable preferences. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/SettingsManager.kt`._

#### TM-SETTINGS-01 · All settings survive a full app restart — `P0` · positive
- **Preconditions:** App unlocked.
- **Steps:** 1. Set non-default values: theme = Dark, dynamic color = on, app lock = off, Understanding Engine = Cloud + a Cloud LLM API key, event duration = 90 min, retention = 90 days, scan frequency = Hourly, model `.task` path = custom string. 2. Force-stop the app (Recents swipe + `adb shell am force-stop com.rajasudhan.taskmind`). 3. Relaunch and reopen Privacy.
- **Expected:** Every value is exactly as set (all persisted in EncryptedSharedPreferences). Theme/lock/dynamic-color are honored from first frame; engine radio = Cloud; duration dropdown "90 min"; retention "90 days"; scan "Hourly"; model path field shows the custom string.

#### TM-SETTINGS-02 · Defaults on a fresh install match code — `P1` · positive
- **Preconditions:** Fresh install / after Delete All Private Data.
- **Steps:** 1. Open Privacy and inspect each control.
- **Expected:** App lock ON (secure default), theme = System, dynamic color OFF, Understanding Engine = On-Device, event duration 60 min, calendar = Automatic (primary) (`CALENDAR_ID_AUTO = -1`), retention "Keep forever" (0), scan frequency "Every 30 min", API keys blank, model path blank, no Gmail connected.

#### TM-SETTINGS-03 · API key is masked and stored — `P1` · positive
- **Preconditions:** Understanding Engine = Cloud (the key field shows only in the Cloud branch).
- **Steps:** 1. Type an API key into "Cloud LLM API Key". 2. Observe the field. 3. Restart the app, return to the Cloud branch.
- **Expected:** Input is rendered with `PasswordVisualTransformation` (dots, not plaintext). The value persists across restart (`llmApiKey` in EncryptedSharedPreferences) and is reused by `CloudLlmProvider`. The STT key field behaves the same.

#### TM-SETTINGS-04 · Model `.task` path persists and triggers a fresh check — `P1` · positive
- **Preconditions:** Understanding Engine = On-Device.
- **Steps:** 1. Enter a custom path in "Model .task path (blank = default)". 2. Tap "Check on-device model" and note the result. 3. Edit the path again.
- **Expected:** `onDeviceModelPath` is persisted; editing it clears `_onDeviceStatus` (the prior check result disappears until re-checked) so a stale "loaded" message isn't shown for a new path. Blank path falls back to the default location shown in the hint.

#### TM-SETTINGS-05 · Calendar config persists — `P1` · positive
- **Preconditions:** Calendar permission granted and Calendar source enabled so the picker lists calendars.
- **Steps:** 1. Calendar Events → set Event duration = 30 min and Target calendar = a specific named calendar. 2. Restart; reopen.
- **Expected:** Duration shows "30 min" (`eventDurationMinutes = 30`); target calendar shows the chosen name (`calendarId` = that id). If the chosen calendar later disappears from the device, the picker falls back to "Automatic (primary)" label.

#### TM-SETTINGS-06 · StateFlow settings apply live without restart (theme) — `P0` · positive
- **Preconditions:** App open on any tab.
- **Steps:** 1. Privacy → Appearance → tap "Dark" (then "Light", then "System").
- **Expected:** The whole app re-themes immediately on each tap — no restart needed (`themeModeFlow` is a StateFlow MainActivity collects). The selected chip is highlighted.

#### TM-SETTINGS-07 · App lock toggle applies live — `P0` · positive
- **Preconditions:** A device credential/biometric IS enrolled; app lock currently ON.
- **Steps:** 1. Security → toggle "App lock (biometric)" OFF. 2. Leave the app and return (or lock/unlock the device). 3. Toggle it back ON and return again.
- **Expected:** `appLockEnabledFlow` flips live. With lock OFF, returning opens straight to data (no biometric prompt). With lock ON, returning shows the biometric/credential prompt. No restart needed.

#### TM-SETTINGS-08 · No dynamic-color (Material You) control — `P2` · negative
- **Preconditions:** Android 12+ (S25 Ultra qualifies).
- **Steps:** 1. Open Privacy → Appearance and inspect the controls.
- **Expected:** There is NO dynamic-color / Material You toggle (only the System/Light/Dark theme chips). The app keeps its fixed Bold brand palette regardless of wallpaper. (The dead `dynamicColor` plumbing was removed in #52.)

#### TM-SETTINGS-09 · App-lock "ON but no credential" warning — `P1` · edge
- **Preconditions:** A device with NO screen lock / biometric enrolled; app lock toggle ON.
- **Steps:** 1. Open Security. 2. Read below the switch. 3. Go to device settings, add a PIN, return to the app.
- **Expected:** With no enrolled credential, an error-colored line appears: "No screen lock is set up on this device, so the app can't lock yet…". After enrolling a credential and returning (ON_RESUME re-queries `deviceCanEnforceLock`), the warning disappears.

#### TM-SETTINGS-10 · Gmail multi-account migration from the legacy single-account key — `P1` · edge
- **Preconditions:** Debuggable build where you can seed the legacy pref. Set only `gmail_account` = `alice@gmail.com` and ensure `gmail_accounts` set is absent.
- **Steps:** 1. Launch and read `gmailAccounts` effect: open Permissions panel ("Gmail connected" row) or Sources. 2. Add a second account `bob@gmail.com` via the normal connect flow. 3. Remove `alice@gmail.com`.
- **Expected:** On first read, `gmailAccounts` seeds from the legacy key → `{alice@gmail.com}` so "Gmail connected" shows ✓ Granted. After adding bob, the set is `{alice, bob}` stored under `gmail_accounts`. After removing alice, the legacy `gmail_account` key is also cleared so the migration can't resurrect her on next launch — final set `{bob}`.

#### TM-SETTINGS-11 · Delete-All resets StateFlow-backed settings immediately — `P1` · positive
- **Preconditions:** Theme = Dark, app lock = OFF, dynamic color = ON.
- **Steps:** 1. "Delete All Private Data" → Delete.
- **Expected:** Without restart, `clearSettings()` pushes new StateFlow values: theme → System, app lock → ON, dynamic color → OFF — the UI re-themes and the lock switch flips ON live. Other prefs (keys, calendar, retention, scan) are removed and fall back to their getter defaults.

### 25. Permissions panel & denial behavior
_Scope: permissions summary + graceful degradation. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/ui/settings/SettingsViewModel.kt`._

#### TM-PERM-01 · Panel reflects current grant state across all 9 rows — `P0` · positive
- **Preconditions:** Mixed grants — e.g. SMS granted, Call log denied, Calendar granted, Notification access off, Usage access off, Exact alarms on, Gmail not connected.
- **Steps:** 1. Open Privacy → "Permissions" card.
- **Expected:** Exactly nine rows in this order: SMS, Call log, Calendar, Audio files, Post notifications, Notification access, Usage access, Exact alarms, Gmail connected. Each shows green `✓ Granted` or error-colored `✗ Not granted` matching the actual OS state. "Notification access" reflects `NotificationManagerCompat.getEnabledListenerPackages`, "Usage access" reflects the `GET_USAGE_STATS` app-op, "Exact alarms" reflects `canScheduleExactAlarms()`, "Gmail connected" reflects whether `gmailAccounts` is non-empty.

#### TM-PERM-02 · Panel is computed on entry — `P1` · positive
- **Preconditions:** App freshly navigated to Privacy.
- **Steps:** 1. Navigate to the Privacy tab.
- **Expected:** `loadPermissionStatuses()` runs from `LaunchedEffect(Unit)` on first composition, so the rows are populated immediately (not empty).
  > Note: The panel is loaded once per composition entry via `LaunchedEffect(Unit)` and is NOT refreshed on `ON_RESUME` (only the Security card's `canEnforceLock` re-queries on resume). So toggling a permission in system settings and returning may show STALE rows until you leave and re-enter the Privacy tab. See TM-PERM-03.

#### TM-PERM-03 · Granting a permission externally updates the panel after re-entry — `P1` · edge
- **Preconditions:** SMS currently denied → shows `✗ Not granted`.
- **Steps:** 1. Confirm SMS row is `✗ Not granted`. 2. Grant READ_SMS via system App-info → Permissions (or `adb shell pm grant com.rajasudhan.taskmind android.permission.READ_SMS`). 3. Return to the app — note the row may still read stale. 4. Switch to another tab and back to Privacy.
- **Expected:** After leaving and re-entering Privacy (re-running `LaunchedEffect(Unit)`), the SMS row flips to `✓ Granted`. (Confirms the panel is recomputed on tab re-entry, consistent with the Note in TM-PERM-02.)

#### TM-PERM-04 · No in-panel deep links to system settings — `P1` · negative
- **Preconditions:** Any state.
- **Steps:** 1. Tap each "Permissions" row and its status text.
- **Expected:** Rows are pure read-only `Text` — tapping does nothing; there is NO deep link / button in the Permissions card.
  > Note: The suite scope mentions "deep links to system settings", but per the CODE those live on the **Sources** screen (`SourcesScreen.kt` launches `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` and `Settings.ACTION_USAGE_ACCESS_SETTINGS`), not in this Permissions panel. The panel here is display-only. Deep-link verification belongs to the Sources suite.

#### TM-PERM-05 · Usage-access check never crashes the panel — `P2` · edge
- **Preconditions:** A device/ROM where the `GET_USAGE_STATS` app-op query could throw.
- **Steps:** 1. Open the Permissions card on such a device.
- **Expected:** The "Usage access" row renders `✗ Not granted` rather than crashing — `unsafeCheckOpNoThrow` is wrapped in `runCatching{…}.getOrDefault(false)`.

#### TM-PERM-06 · SMS source degrades gracefully when READ_SMS is revoked after enablement — `P0` · negative
- **Preconditions:** SMS source enabled and previously working (READ_SMS granted).
- **Steps:** 1. Revoke READ_SMS via system settings (or `adb shell pm revoke com.rajasudhan.taskmind android.permission.READ_SMS`) — note Android may kill the process; relaunch. 2. Trigger a background/manual scan. 3. Use the app normally; check the Permissions panel after re-entering Privacy.
- **Expected:** No crash. The SMS scan path yields no SMS-derived suggestions (read fails/empty) and the app continues with other sources. Permissions panel shows SMS `✗ Not granted` after re-entry. Other features (Inbox, Notes, Test Extraction) remain fully functional.

#### TM-PERM-07 · Call-log source degrades gracefully when READ_CALL_LOG is revoked — `P1` · negative
- **Preconditions:** Call log source enabled; READ_CALL_LOG previously granted.
- **Steps:** 1. Revoke READ_CALL_LOG; relaunch if the OS killed the process. 2. Run a scan; open Privacy.
- **Expected:** No crash; no call-log-derived suggestions are produced; panel shows Call log `✗ Not granted`. App stays usable.

#### TM-PERM-08 · Calendar source degrades + picker empties when READ_CALENDAR is revoked — `P1` · negative
- **Preconditions:** Calendar permission granted; the Calendar Events picker previously listed calendars; a specific calendar was selected.
- **Steps:** 1. Revoke READ_CALENDAR. 2. Re-enter Privacy.
- **Expected:** `loadCalendars()` early-returns an empty list when the permission isn't granted, so the picker shows "Automatic (primary)" and the hint "Enable the Calendar source (Sources tab) to choose a specific calendar." appears. Permissions panel shows Calendar `✗ Not granted`. No crash; approving a dated item later just uses the default calendar.

#### TM-PERM-09 · Notification-access revocation reflected, no crash — `P1` · negative
- **Preconditions:** Notification listener access previously enabled.
- **Steps:** 1. Disable TaskMind's notification access in system settings (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`). 2. Re-enter Privacy.
- **Expected:** "Notification access" row shows `✗ Not granted` (`getEnabledListenerPackages` no longer contains the package). The notification-watching source produces nothing further; app does not crash.

#### TM-PERM-10 · Usage-access revocation reflected — `P2` · negative
- **Preconditions:** Usage access previously granted.
- **Steps:** 1. Revoke Usage access (`Settings.ACTION_USAGE_ACCESS_SETTINGS`). 2. Re-enter Privacy.
- **Expected:** "Usage access" row shows `✗ Not granted`; the app-usage digest source yields nothing; no crash.

#### TM-PERM-11 · Exact-alarms revocation reflected — `P2` · edge
- **Preconditions:** Exact alarms previously allowed.
- **Steps:** 1. Revoke "Alarms & reminders" for TaskMind in system settings. 2. Re-enter Privacy.
- **Expected:** "Exact alarms" row shows `✗ Not granted` (`canScheduleExactAlarms()` false). The panel renders without crashing. (Reminder scheduling degradation is covered by the reminders suite; here only the panel reflection is asserted.)

### 26. Resilience & edge
_Scope: app stays usable through corrupted keystore/DB, large lists, offline, config changes, process death, and rapid input. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/di/DatabaseModule.kt`._

#### TM-EDGE-01 · Undecryptable EncryptedSharedPreferences self-heals on launch — `P0` · edge
- **Preconditions:** App installed, launched once so `secret_shared_prefs` + `taskmind_db` exist with at least one saved Note. Device has a fast way to invalidate the keystore master key (clear the Keystore entry / restore prefs from a different device's backup, or as a proxy push a deliberately corrupted `secret_shared_prefs` xml so `EncryptedSharedPreferences.create` throws).
- **Steps:** 1. Force-stop TaskMind. 2. Invalidate/corrupt the keystore master key so the existing `secret_shared_prefs` can no longer be decrypted. 3. Cold-launch the app.
- **Expected:** App launches without an ANR/crash. The `build()` catch path deletes the master-key alias, `secret_shared_prefs`, and `taskmind_db`, then rebuilds prefs successfully. App opens to a clean state: settings revert to defaults (on-device LLM ON, app-lock ON, theme SYSTEM, dynamic color OFF) and the Inbox/Notes are empty (old encrypted DB dropped). No repeated crash on a second launch.

#### TM-EDGE-02 · Stored DB key can't open on-disk database — fresh DB created — `P0` · edge
- **Preconditions:** App with saved Notes. Ability to leave `secret_shared_prefs` (and thus the stored `db_key`) intact while replacing/corrupting `taskmind_db` so the key no longer opens it (simulates an interrupted restore that swapped the DB file but not the key).
- **Steps:** 1. Force-stop the app. 2. Replace `taskmind_db` with a file the stored key cannot decrypt (or corrupt its header). 3. Cold-launch.
- **Expected:** No crash-loop. `provideDatabase` forces `db.openHelper.readableDatabase`, catches the failure, closes the handle, deletes `taskmind_db`, and rebuilds. App opens with an empty Notes list and an empty Inbox; settings (still readable from prefs) are preserved. Subsequent saves persist normally.

#### TM-EDGE-03 · Large Inbox list scrolls smoothly — `P1` · edge
- **Preconditions:** App unlocked, Inbox tab. Generate ~50+ pending suggestions (repeatedly run Settings → Test Extraction (debug) with distinct text, or let several sources accumulate).
- **Steps:** 1. Open Inbox. 2. Fling-scroll from top to bottom and back several times. 3. Expand/collapse a few cards mid-scroll.
- **Expected:** `LazyColumn` keeps scrolling fluid (items keyed by `it.id`, so recycling is stable). The "$count TO REVIEW" header count matches the list size. The footer hint "swipe → keep · ← dismiss · tap to edit" appears once at the end. No dropped frames severe enough to stutter, no duplicated/blank cards.

#### TM-EDGE-04 · Large Notes list scrolls smoothly — `P1` · edge
- **Preconditions:** Approve many Inbox items (or run Test Extraction repeatedly and Keep each) so Notes holds 50+ entries.
- **Steps:** 1. Open the Notes tab. 2. Fling-scroll the full list repeatedly. 3. Open a note detail and return.
- **Expected:** List scrolls smoothly with no jank or content flashing; returning from note detail restores the prior scroll position (nav `restoreState`).

#### TM-EDGE-05 · Offline — on-device pipeline unaffected — `P0` · positive
- **Preconditions:** App unlocked. Settings → on-device LLM is ON (the default, `useOnDeviceLlm` defaults true). Airplane mode ON (no Wi-Fi/cellular).
- **Steps:** 1. Enable airplane mode. 2. Settings → Test Extraction (debug), paste e.g. "Pick up dry cleaning tomorrow at 5pm", Run.
- **Expected:** A suggestion is produced and lands in the Inbox while fully offline — the on-device path needs no network. No network-error snackbar.

#### TM-EDGE-06 · Offline — cloud provider fails closed to empty items — `P1` · negative
- **Preconditions:** App unlocked. Settings → switch provider to cloud (on-device LLM OFF) with a Gemini key configured. Airplane mode ON.
- **Steps:** 1. Turn off on-device LLM (use cloud). 2. Enable airplane mode. 3. Run Test Extraction (debug) with any text.
- **Expected:** The cloud call fails closed — it yields zero extracted items rather than crashing or hanging. The Inbox gains no new suggestion from this run (and the user can re-run once back online). No partial/garbage suggestion is saved.
  > Note: exact failure surface (silent no-op vs. a snackbar) lives in the extraction/ViewModel layer, not the assigned files; verify the observable outcome is "no new item, no crash."

#### TM-EDGE-07 · Rotation preserves Inbox state — `P1` · edge
- **Preconditions:** App unlocked on Inbox with several suggestions; expand one card and tap Edit so its inline editor is showing.
- **Steps:** 1. Rotate the device portrait → landscape → portrait. 2. Observe the expanded/edit card and scroll position.
- **Expected:** Layout reflows without clipping; the list and header remain intact. (Note: card `expanded`/`isEditing` use plain `remember`, so an expanded card may collapse on rotation — that is acceptable; the requirement is no crash and no layout breakage. Flag if a card's in-progress edit text loss is considered a defect.)

#### TM-EDGE-08 · Split-screen layout integrity — `P2` · edge
- **Preconditions:** App unlocked, Inbox populated.
- **Steps:** 1. Enter One UI split-screen with TaskMind in the top/bottom half. 2. Drag the divider to shrink/grow TaskMind's pane. 3. Visit each bottom-nav tab.
- **Expected:** Bottom nav (4 items: Inbox/Notes/Sources/Privacy) stays usable and labels remain legible; cards and the editorial header reflow to the narrower width without horizontal clipping or overlap. The mic FAB stays on-screen.

#### TM-EDGE-09 · Font scaling at 200% doesn't break layout — `P1` · edge
- **Preconditions:** One UI Settings → Display → set Font size to maximum (and Screen zoom large).
- **Steps:** 1. Open Inbox (populated), Notes, Sources, Privacy. 2. Expand a suggestion card.
- **Expected:** Text scales up; the header eyebrow/title, "$count TO REVIEW", and card titles wrap rather than truncate critical content. Keep/Dismiss/Snooze/Edit row actions remain tappable. No control is pushed entirely off-screen.

#### TM-EDGE-10 · Process death + state restore (nav + auth) — `P0` · edge
- **Preconditions:** App-lock ON and enforceable (device has biometric/PIN enrolled). App unlocked; navigate to the Notes tab (not the default Inbox).
- **Steps:** 1. Background the app. 2. From Developer Options, "Don't keep activities" ON (or use `adb shell am kill com.rajasudhan.taskmind` while backgrounded) to force process death. 3. Re-launch from Recents.
- **Expected:** The activity is recreated fresh, so `isAuthenticated` resets to false → the Lock screen ("TaskMind is locked") shows and the biometric prompt fires automatically. After authenticating, the app returns; because the rebuilt activity restarts at `startDestination = "inbox"`, the Inbox tab is shown (auth is correctly re-required; nav back-stack within a recreated process is not expected to deep-restore to Notes).
  > Note: nav `saveState`/`restoreState` preserve per-tab state across in-process tab switches, not across full process death; the load-bearing guarantee here is "re-auth is enforced," which it is.

#### TM-EDGE-11 · Re-auth required after backgrounding — `P0` · positive
- **Preconditions:** App-lock ON and enforceable; app currently unlocked on any tab.
- **Steps:** 1. Press Home (sends `ON_STOP`). 2. Re-open the app.
- **Expected:** On `ON_STOP`, `isAuthenticated` is set false (lock enabled + enforceable + not a SAF round-trip), so on return the Lock screen + biometric prompt appear. Cancelling auth keeps content hidden behind the lock.

#### TM-EDGE-12 · Rapid double-tap on Keep creates no duplicate note — `P0` · edge
- **Preconditions:** Inbox with a suggestion that has NO due date (so Keep approves immediately without the time-picker dialog). Expand the card.
- **Steps:** 1. Double-tap the "Keep" button as fast as possible. 2. Check the "Kept" snackbar and the Notes tab.
- **Expected:** The item is removed from the pending list after the first approve (it no longer exists in the keyed list, so the second tap has nothing to act on). Exactly one note is created — no duplicate. A single "Kept" undo snackbar behavior is acceptable.

#### TM-EDGE-13 · Rapid double-tap on Dismiss creates no duplicate action — `P1` · edge
- **Preconditions:** Inbox with at least one suggestion; expand the card (or use the swipe-left gesture twice quickly).
- **Steps:** 1. Double-tap "Dismiss" as fast as possible. 2. Observe the list and the "Dismissed" snackbar.
- **Expected:** The suggestion is removed once; no duplicate reject is recorded and no crash. Tapping "Undo" on the snackbar restores exactly one item.

#### TM-EDGE-14 · Rapid swipe both directions on the same card — `P2` · edge
- **Preconditions:** Inbox with one dateless suggestion.
- **Steps:** 1. Swipe the card right (keep) and immediately left (dismiss) in quick succession.
- **Expected:** Because `confirmValueChange` always returns false, the box never visually "stays dismissed"; the first committed gesture triggers its action (haptic + approve/reject) and removes the item by id, so the second gesture has no target. Exactly one action fires; no duplicate note and no crash.

### 27. Accessibility (functional)
_Scope: TalkBack reaches every control with a meaningful label, headings are exposed, and large-font/large-target usability holds. Source of truth: `apps/taskmind/src/main/java/com/rajasudhan/taskmind/ui/inbox/InboxScreen.kt`._

#### TM-A11Y-01 · Mic FAB announces "Add by voice" — `P0` · positive
- **Preconditions:** TalkBack ON. App unlocked on the Inbox tab.
- **Steps:** 1. Swipe to / focus the floating action button (bottom-right).
- **Expected:** TalkBack announces "Add by voice, button" (the FAB icon's `contentDescription = "Add by voice"`). While a voice note is processing the FAB shows a spinner; double-tapping it while processing is a no-op (`onMicClick` returns if `isProcessingVoice`).

#### TM-A11Y-02 · Inbox icon-only header controls are labelled — `P0` · positive
- **Preconditions:** TalkBack ON. Inbox with ≥1 suggestion (header visible).
- **Steps:** 1. Focus the overflow ("⋮") icon in the Inbox header. 2. Open it and focus each menu row. 3. Focus the top-bar "?" and lock actions.
- **Expected:** Overflow announces "More actions, button". The dropdown rows read "Add item", "Refresh", "Keep all", "Dismiss all". The app-bar help action announces "How to use TaskMind"; the lock action announces "Lock app" (present only when app-lock is on and enforceable).

#### TM-A11Y-03 · Suggestion card content is reachable and operable via TalkBack — `P0` · positive
- **Preconditions:** TalkBack ON. Inbox with a suggestion; card collapsed.
- **Steps:** 1. Focus the card title and double-tap to expand. 2. Navigate the expanded actions: Task/Reminder/Note kind chips, Dismiss, Keep, Snooze, Edit.
- **Expected:** The title is focusable and double-tap toggles expansion. The "Keep" and "Dismiss" buttons are reachable and announce their visible text ("Keep" / "Dismiss"). Kind chips and "Snooze"/"Edit"/"Call"/"Directions" row actions are each focusable and actionable. No control is skipped by the screen reader.

#### TM-A11Y-04 · Swipe-action reveal is not the only path to approve/reject — `P1` · positive
- **Preconditions:** TalkBack ON. Inbox with a suggestion.
- **Steps:** 1. Attempt to operate the card without the swipe gesture (which is hard to perform under TalkBack). 2. Use the expanded card's Keep/Dismiss buttons instead.
- **Expected:** Keep/Dismiss are fully achievable through the in-card buttons, so the swipe gesture is not the sole route. (Note: in `SwipeReveal` the Check/Close icons use `contentDescription = null`, but they are decorative background reveals paired with visible "Keep"/"Dismiss" text and are not the primary action surface — acceptable. Flag only if TalkBack users have no non-swipe way to triage, which they do.)

#### TM-A11Y-05 · In-screen titles exposed as accessibility headings — `P1` · positive
- **Preconditions:** TalkBack ON.
- **Steps:** 1. On Inbox, use TalkBack's heading navigation (e.g. swipe up-then-right, or set granularity to Headings) to jump to the screen title. 2. Repeat on Notes/Sources/Privacy.
- **Expected:** Each screen's serif title (rendered via `BoldScreenHeader`, which applies `Modifier.semantics { heading() }`) is announced as a heading, so heading navigation lands on "Inbox" (and the equivalent titles on other tabs). The accent eyebrow is read as normal text, not a heading.

#### TM-A11Y-06 · Empty-state controls are labelled and operable — `P1` · positive
- **Preconditions:** TalkBack ON. Inbox empty (triage everything / fresh state).
- **Steps:** 1. Swipe through the "All clear." empty state. 2. Focus the "Refresh" and "Add item" buttons and activate "Add item".
- **Expected:** Title "All clear." and the supporting line are read. The two pill buttons announce "Refresh" and "Add item" respectively and are double-tap activatable; "Add item" opens the "Add an item" dialog. (Note: the empty-state hero icon — and the generic `EmptyState` icon — use `contentDescription = null`; correct, as they are decorative.)

#### TM-A11Y-07 · Bottom nav items announce label + selected state — `P0` · positive
- **Preconditions:** TalkBack ON. App unlocked.
- **Steps:** 1. Focus each bottom-nav item: Inbox, Notes, Sources, Privacy. 2. Double-tap one to switch tabs.
- **Expected:** Each item's icon carries `contentDescription = tab.label`, so TalkBack announces "Inbox", "Notes", "Sources", "Privacy". The visible label text (uppercased) is also present. Activating an item navigates to that tab. The active tab is exposed via `semantics { selected = active; role = Role.Tab }` (added in #53), so TalkBack announces it as **"selected"**.

#### TM-A11Y-08 · Manual-add dialog is fully navigable under TalkBack — `P1` · positive
- **Preconditions:** TalkBack ON. Inbox; open overflow → "Add item" (or empty-state "Add item").
- **Steps:** 1. Focus the text field, type a note. 2. Focus and activate "Add"; verify "Cancel" is reachable.
- **Expected:** The field announces its label "Type a note, task or reminder". "Add" is disabled (and announced as such) while the field is blank and becomes enabled after text entry; activating it submits and dismisses the dialog. "Cancel" dismisses and clears the field.

#### TM-A11Y-09 · Lock screen is reachable and the Unlock button is labelled — `P1` · positive
- **Preconditions:** App-lock ON and enforceable; app freshly launched so the Lock screen shows.
- **Steps:** 1. With TalkBack ON, swipe through the lock screen. 2. Focus the "Unlock" button.
- **Expected:** TalkBack reads "TaskMind is locked" and "Your data stays private behind biometrics." The lock/fingerprint emblem icons are decorative (`contentDescription = null`) and skipped. The "Unlock" button is focusable and announces "Unlock"; double-tapping re-invokes the biometric prompt.

#### TM-A11Y-10 · Minimum ~48dp touch targets — `P1` · edge
- **Preconditions:** Accessibility Scanner installed (or manual measurement). Inbox with ≥1 suggestion.
- **Steps:** 1. Run Accessibility Scanner on the Inbox screen (header + a card). 2. Note any "touch target" findings.
- **Expected:** Primary actions (mic FAB, Keep/Dismiss buttons, bottom-nav items) meet ~48dp. The Inbox header overflow `IconButton` now uses its default 48dp interactive size (the `Modifier.size(28.dp)` override was removed) and each bottom-nav item fills the 74dp nav row, so both clear the 48dp target (fixed in #53).

#### TM-A11Y-11 · Large-font (200%) does not clip critical controls — `P0` · edge
- **Preconditions:** One UI Display → Font size at max. TalkBack optional.
- **Steps:** 1. Open Inbox (populated) and expand a card. 2. Open the manual-add dialog and the "Keep all N?" confirm dialog. 3. Visit Privacy/Settings.
- **Expected:** At 200% scale, the Keep/Dismiss/Snooze/Edit actions, dialog confirm/dismiss buttons ("Keep all"/"Cancel", "Add"/"Cancel"), and bottom-nav labels remain on-screen and tappable. Long titles wrap/ellipsize rather than shoving buttons off the layout. No critical action is fully clipped or overlapped.

#### TM-A11Y-12 · Sweep banner is announced and operable — `P2` · positive
- **Preconditions:** TalkBack ON. Inbox contains ≥1 suggestion with confidence < 0.5 (run Test Extraction (debug) with vague/noisy text to get a "likely noise" item).
- **Steps:** 1. Confirm the "N likely noise detected" banner appears above the cards. 2. Focus and activate it.
- **Expected:** TalkBack reads the banner text ("N likely noise detected" / "SWEEP →") and it is a single actionable card (`onClick = onSweep`). Activating it sweeps the low-confidence items and shows the "N noise item(s) swept" undo snackbar with correct singular/plural ("item" when N==1, "items" otherwise).

## Execution model

### Smoke set (P0) — run on every build

A fast end-to-end pass over the critical path. Stop and triage on any failure before deeper testing:

1. **TM-SEC-01/02/08** — lock gate + re-lock + lock-off.
2. **TM-SRC** — enable SMS + Notifications; permissions granted and persist.
3. **TM-PIPE** (Test Extraction) — a dated task with a time → suggestion appears with the right fields.
4. **TM-INBOX-01/…/approve** — approve it; **TM-NOTIF** — approve from the notification works.
5. **TM-NOTES / TM-DETAIL** — the approved item appears in Notes; detail opens; complete-toggle works.
6. **TM-ALARM-01** — a near-future reminder fires its notification.
7. **TM-BACKUP** — create a backup, restore it (correct passphrase) → data intact after restart.
8. **TM-EGRESS-01** — on-device default reads "No data has left this device."

### Full regression

All 27 suites, every case, per release tag. Execute suites in order; respect each suite's preconditions and the [Prerequisites checklist](#prerequisites-checklist) (skip-with-reason any blocked by missing model/key/hardware, and note it in the run record).

### Entry / exit criteria

- **Entry:** the debug APK installs and launches on the primary device; prerequisites for the suites in scope are met.
- **Exit:** 100% of P0 cases Pass; **no open P0/P1 defects**; every suite executed at least once (or explicitly skipped-with-reason); the run record + defect log are filed.

### Run record

Track each cycle in a simple table — `Case ID · Result (Pass/Fail/Blocked/Skipped) · Device · Build (versionName+commit) · Notes/Artifact`. Keep one row per executed case; link screenshots/logcat for every Fail.

## Defect log

| ID | Severity | Suite / Case | Repro steps | Expected | Actual | Build | Status |
|----|----------|--------------|-------------|----------|--------|-------|--------|
| _BUG-001_ | P0/P1/P2 | TM-XXX-NN | … | … | … | 4.0 (`<sha>`) | Open/Fixed |

File confirmed bugs as GitHub issues and cross-reference the BUG id. Suspected behavior-vs-doc mismatches flagged inline with `> Note:` in the suites below should be triaged as either a code bug or a doc fix.

## Known code/UI mismatches to verify first

The drafting pass flagged these against current source — confirm whether each is a bug or an intended/doc change, since they affect several cases:

- **Material You / dynamic color — RESOLVED (#52):** the Bold redesign intentionally drops wallpaper-based dynamic color, so the dead `dynamicColor` plumbing (`SettingsManager` flow, `SettingsViewModel` accessor, `MainActivity` wiring, `TaskMindTheme` param) and the false CHANGELOG "Material You toggle" claim were removed. The app keeps a fixed brand palette in both light and dark; see TM-NAV-09 / TM-SETTINGS-08.
- Additional per-case `> Note:` flags appear throughout (e.g. accessibility gaps in suite 27, self-heal paths in suite 26). Each should be triaged before sign-off.

## Traceability

- **Automation:** the equivalent regression coverage and the path to build it are in [`AUTOMATED_TEST_PLAN.md`](AUTOMATED_TEST_PLAN.md). As automated tests land, annotate the corresponding manual cases that can be retired from the per-build smoke set.
- **Feature history:** [`../../../CHANGELOG.md`](../../../CHANGELOG.md) maps suites to the releases that introduced them.

---

_325 functional test cases across 27 suites. Regenerate or extend by re-running the drafting workflow when features change._
