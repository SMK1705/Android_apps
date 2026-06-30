# TaskMind — Automated Test Suite Buildout

Companion to [`FUNCTIONAL_TEST_PLAN.md`](FUNCTIONAL_TEST_PLAN.md). That document is the **manual** on-device plan; this one specifies the **automated** regression net to build so the same behavior is locked down in CI.

> **Status:** specification only. No production code, test code, or `build.gradle` edits have been made yet. Each phase below is actionable in a follow-up PR.

---

## 1. Current state

| Layer | Today |
|---|---|
| Pure-logic JVM unit tests | **11 files** in `src/test` — `ExtractionHeuristics`, `RecurrenceUtil`, `RejectionLearner`, `PhoneUtil`, `AppUsageDigest`, `GmailTextExtractor`, `AudioDecoder`, `BackupCrypto`, `LlmModelsParsing`, `Checklist`, + a placeholder. |
| Instrumented tests | **1 placeholder** (`ExampleInstrumentedTest`). |
| ViewModels (6) | **0 tests** |
| Room DAO + migrations | **0 tests** |
| Compose UI | **0 tests** (deps present, unused) |
| Receivers / workers / services | **0 tests** |
| Integration / end-to-end | **0 tests** |

Estimated line coverage ≈ 15–20%, concentrated in pure helpers. The approval pipeline, persistence, scheduling, and every screen are unverified.

Build facts that shape this plan (from [`build.gradle.kts`](../build.gradle.kts) / [`libs.versions.toml`](../../../gradle/libs.versions.toml)): AGP 9.2.1, Kotlin 2.2.10, KSP, Hilt 2.59.2, Room 2.7.0, WorkManager 2.9.0, Compose BOM 2024.09.00, `minSdk 35` / `targetSdk 36`, Java 11, `testInstrumentationRunner = androidx.test.runner.AndroidJUnitRunner`, DB encrypted via **SQLCipher**. Test deps already declared: `junit 4.13.2`, `androidx.test.ext:junit 1.1.5`, `androidx.test:core 1.6.1`, `kotlinx-coroutines-test 1.10.2`, `compose ui-test-junit4`, `espresso-core 3.5.1`, `androidx.test:runner 1.6.2`, `ui-test-manifest`.

---

## 2. Tooling to add

### 2.1 `gradle/libs.versions.toml`

Add under `[versions]`:

```toml
mockk = "1.14.2"
turbine = "1.2.0"
robolectric = "4.14.1"          # SDK 35 support; see §6 caveat for SDK 36
archCoreTesting = "2.2.0"
workTesting = "2.9.0"           # match workRuntime
roomTesting = "2.7.0"          # match room
testRules = "1.6.1"
testOrchestrator = "1.5.1"
kover = "0.9.1"                 # coverage plugin
```

Add under `[libraries]`:

```toml
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
mockk-android = { group = "io.mockk", name = "mockk-android", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-arch-core-testing = { group = "androidx.arch.core", name = "core-testing", version.ref = "archCoreTesting" }
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "workTesting" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "roomTesting" }
androidx-test-rules = { group = "androidx.test", name = "rules", version.ref = "testRules" }
androidx-test-orchestrator = { group = "androidx.test", name = "orchestrator", version.ref = "testOrchestrator" }
hilt-android-testing = { group = "com.google.dagger", name = "hilt-android-testing", version.ref = "hilt" }
```

Add under `[plugins]`:

```toml
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
```

### 2.2 `apps/taskmind/build.gradle.kts`

```kotlin
plugins {
    // …existing…
    alias(libs.plugins.kover)
}

android {
    defaultConfig {
        // run each instrumented test in its own process for isolation
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true   // required by Robolectric
            isReturnDefaultValues = true
        }
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    // JVM unit (src/test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.core)            // androidx.test:core (already present)

    // Instrumented (src/androidTest)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.hilt.android.testing)
    "kspAndroidTest"(libs.hilt.compiler)
    androidTestUtil(libs.androidx.test.orchestrator)
}
```

### 2.3 Enable Room schema export (needed for migration tests)

Room must export per-version schema JSON so `MigrationTestHelper` can replay v1→v5. Add the KSP arg and wire the schema dir as a test asset:

```kotlin
android {
    defaultConfig {
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}
```

Commit the generated `apps/taskmind/schemas/com.rajasudhan.taskmind.data.local.TaskMindDatabase/{1..5}.json` so historical migrations stay testable. (Going forward, every schema bump must commit a new JSON.)

---

## 3. Testability seams (small, behavior-preserving refactors)

The codebase already uses Hilt + constructor injection in most places, which is the main enabler. Before writing tests, audit for hard-to-fake spots and inject these where a class news them up directly:

- **`DAO`** → already provided via Hilt; ensure ViewModels take it by constructor.
- **`SettingsManager`** → wrap reads behind the existing class; fakeable as an interface or via MockK relaxed mock.
- **`LlmProvider`** ([`RoutingLlmProvider`](../src/main/java/com/rajasudhan/taskmind/data/source/understanding/RoutingLlmProvider.kt)) → already an interface; supply a `FakeLlmProvider` returning canned `LlmResponse`.
- **`AlarmScheduler`** / **`GeofenceManager`** → extract a thin interface so `SuggestionApprover` and receivers can be tested without the real `AlarmManager`.
- **Clock / "now"** → inject a `() -> Long` (or `java.time.Clock`) wherever `System.currentTimeMillis()` is read inline (alarms, recurrence, watermark, snooze) so time-dependent cases are deterministic.

Keep each refactor minimal and reviewed under the existing behavior (no functional change). Prefer constructor params with Hilt `@Provides` defaults.

---

## 4. Layered test targets

### 4.1 JVM unit (`src/test`) — fast, no device

- **Extend existing pure tests** with the edge cases enumerated in the manual plan: more noise-filter inputs, all date/time/recurrence malformed forms, confidence boundary at the acceptance threshold, dedup collisions, phone-number international/domestic boundaries, backup tamper/truncation.
- **ViewModel tests** (Turbine + `kotlinx-coroutines-test` + fakes) for all six: [`InboxViewModel`](../src/main/java/com/rajasudhan/taskmind/ui/inbox/InboxViewModel.kt), [`NotesViewModel`](../src/main/java/com/rajasudhan/taskmind/ui/notes/NotesViewModel.kt), `NoteDetailViewModel`, [`SourcesViewModel`](../src/main/java/com/rajasudhan/taskmind/ui/sources/SourcesViewModel.kt), [`SettingsViewModel`](../src/main/java/com/rajasudhan/taskmind/ui/settings/SettingsViewModel.kt), [`GuideViewModel`](../src/main/java/com/rajasudhan/taskmind/ui/guide/GuideViewModel.kt). Assert emitted `StateFlow` states for: filter/search derivation (Notes), pending-list shaping + snooze hiding (Inbox), toggle→permission state (Sources), live setting flows (Settings).
- **Pipeline & approval logic** with a `FakeLlmProvider` + fake DAO: [`UnderstandingPipeline`](../src/main/java/com/rajasudhan/taskmind/data/source/understanding/UnderstandingPipeline.kt) (pre-filter → sanitize → threshold → dedup → rejection penalty → insert) and [`SuggestionApprover`](../src/main/java/com/rajasudhan/taskmind/data/source/SuggestionApprover.kt) (note created + alarm scheduled + calendar event, via fake scheduler).
- **`ContactResolver`** under **Robolectric** (needs a `ContentResolver`): seed a fake Contacts provider and assert the exact→prefix→substring tier order + `%`/`_` escaping + null on no-permission.

### 4.2 Room DAO + migrations

- **DAO tests:** build an **in-memory Room DB** and exercise every query in [`TaskMindDao`](../src/main/java/com/rajasudhan/taskmind/data/local/TaskMindDao.kt): pending suggestions Flow, active/completed/search notes, `getReminderNotes`, `deleteNotesOlderThan`, `deletePurgeableSuggestions`, rejected-pattern upsert/decrement/delete, completion + due-date + recurrence + checklist + location updates.
- **Migration tests:** `MigrationTestHelper` replaying **v1→2→3→4→5** plus a full open-from-v1, asserting the columns/tables each migration adds (`summary`; then `completed`/`completedDate`/`recurrence`/`checklist`/`location*` + `snoozedUntil` + `rejected_patterns`; then suggestion `location`; then suggestion `recurrence`). Schemas from §2.3.

### 4.3 Compose UI (`src/androidTest`)

Per-screen tests with the ViewModel faked/stubbed so state is deterministic. Use `createAndroidComposeRule` + `onNodeWithText`/semantics + `ui-test-manifest`.

- **Inbox** — render cards; swipe-right approves / swipe-left rejects; snooze menu options; the **time-picker dialog appears when approving a dated item with no time**; empty ("All clear.") and skeleton states; bulk Keep-all/Dismiss-all; noise-sweep banner.
- **Notes** — filter chips switch lists + counts; search filters; complete-toggle moves to Completed.
- **Note detail** — inline edit title/summary; checklist check + reorder; Call button visibility; recurrence dropdown.
- **Sources** — toggle reflects state; allowlist search; height-capped picker keeps lower toggles reachable.
- **Settings / Guide** — egress hero; theme chips; guide pager next/back/skip + "Get started".

### 4.4 Integration / workers / receivers

- **End-to-end approval:** capture text → `UnderstandingPipeline` (fake LLM) → suggestion row → approve → assert a `Note` row exists **and** the fake `AlarmScheduler` got the right schedule **and** the calendar path was invoked.
- **Backup ⇄ restore roundtrip:** seal an in-memory dataset with a passphrase via [`BackupManager`](../src/main/java/com/rajasudhan/taskmind/data/source/BackupManager.kt)/[`BackupCrypto`](../src/main/java/com/rajasudhan/taskmind/data/source/BackupCrypto.kt), restore it, assert data round-trips; assert wrong passphrase / truncated file → `Failure` with the live DB untouched.
- **Workers:** `TestListenableWorkerBuilder` for [`DataCollectionWorker`](../src/main/java/com/rajasudhan/taskmind/data/source/DataCollectionWorker.kt) and [`CaptureWorker`](../src/main/java/com/rajasudhan/taskmind/data/source/CaptureWorker.kt) — watermark advances, retention purge runs, `Result.retry()` on a thrown source.
- **Receivers (Robolectric):** [`AlarmReceiver`](../src/main/java/com/rajasudhan/taskmind/data/source/AlarmReceiver.kt) (fire posts notification; recurring advances + reschedules; **stale note → cancels, no notification**), [`BootReceiver`](../src/main/java/com/rajasudhan/taskmind/data/source/BootReceiver.kt) (re-arms; recurring advanced to future; past one-shot dropped), [`NotificationActionReceiver`](../src/main/java/com/rajasudhan/taskmind/data/source/NotificationActionReceiver.kt) (approve/reject without launching the Activity; notifier refreshed), `GeofenceBroadcastReceiver`.

---

## 5. Test fakes & data strategy

- Build a small **`testfixtures`** package: `FakeTaskMindDao` (in-memory maps backing the Flows), `FakeLlmProvider`, `FakeAlarmScheduler`, `FakeSettingsManager`, plus `NoteBuilder` / `SuggestionBuilder` for terse construction.
- Reuse the **golden extraction set** in [`tools/prompt_eval`](../../../tools/prompt_eval/README.md) as canned pipeline inputs so manual and automated coverage share fixtures.
- Prefer fakes over deep MockK stubbing for the DAO/providers (clearer failures, real Flow semantics); use MockK for leaf collaborators (e.g. `AlarmManager`, `NotificationManager`).

---

## 6. SQLCipher & SDK caveats

- **SQLCipher in tests:** the production DB opens through a SQLCipher `SupportFactory` with a per-install key ([`DatabaseModule`](../src/main/java/com/rajasudhan/taskmind/di/DatabaseModule.kt)). For DAO/migration tests, build the Room DB **without** the SQLCipher factory (plain in-memory/file DB) — the DAO SQL and migrations are identical; encryption is orthogonal and is covered separately by `BackupCryptoTest`. Do **not** ship the real key into tests.
- **Robolectric SDK:** `minSdk 35` means Robolectric must support SDK 35 (4.14+). If a test must run on SDK 36 and Robolectric lags, pin `@Config(sdk = [35])` for JVM runs and route the few 36-specific behaviors to `connectedAndroidTest` on an API 36 emulator.
- **Hilt instrumented tests** need a custom `HiltTestRunner` (extends `AndroidJUnitRunner`, swaps in `HiltTestApplication`); set it as the `testInstrumentationRunner` for the androidTest variant.

---

## 7. CI wiring (`.github/workflows/android.yml`)

Keep the existing single job's `testDebugUnitTest` + `assembleDebug` + APK upload. Extend it:

1. **Unit job (existing, ubuntu, JDK 21):** now also runs the new Robolectric-backed unit + DAO + migration + ViewModel tests under `testDebugUnitTest`. Add a Kover report step + artifact upload.
2. **Instrumented job (new):** Compose UI + integration on an emulator via `reactivecircus/android-emulator-runner@v2` (API **35**, then optionally **36**), running `:apps:taskmind:connectedDebugAndroidTest`. Gate it on the unit job; allow manual `workflow_dispatch` so it doesn't slow every push if flaky.

```yaml
  instrumented:
    needs: build
    runs-on: ubuntu-latest
    strategy:
      matrix:
        api-level: [ 35 ]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
      - name: Instrumented tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          arch: x86_64
          script: ./gradlew :apps:taskmind:connectedDebugAndroidTest --stacktrace
      - name: Upload androidTest report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: androidTest-report-api${{ matrix.api-level }}
          path: apps/taskmind/build/reports/androidTests/
          if-no-files-found: ignore
```

3. **Coverage ratchet:** add a Kover verification rule (start at the current ~15–20% baseline, ratchet up each PR) so coverage can't regress. Upload the HTML/XML report as an artifact.

---

## 8. Rollout (priority order)

| Phase | Scope | Why first |
|---|---|---|
| **1** | Tooling + schema export + fakes scaffolding; **DAO + migration tests** | Persistence + migrations are the highest-risk, lowest-cost-to-test layer; protects user data on upgrade. |
| **2** | **Pipeline + SuggestionApprover + RejectionLearner** integration | The approval gate is the product's core contract; verify extract→approve→note+alarm+calendar. |
| **3** | **ViewModel tests** (all six) | Locks down state/filter/snooze/search logic cheaply on the JVM. |
| **4** | **Receiver + worker tests** | Scheduling/reboot/notification-action correctness (history of phantom-alarm bugs). |
| **5** | **Compose UI tests** + CI instrumented job | Slowest/flakiest; add once the faster layers are green. |

## 9. Definition of done

- New deps + schema export merged; `./gradlew :apps:taskmind:testDebugUnitTest` green locally and in CI.
- Migration test passes the full **v1→v5** path; DAO suite covers every query.
- Pipeline/approval integration + backup-restore roundtrip green.
- All six ViewModels have state tests; key receivers/workers covered.
- `connectedDebugAndroidTest` green on an API 35 emulator in CI; Kover report produced with a non-decreasing threshold.
