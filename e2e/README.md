# TaskMind end-to-end tests (Momentic)

AI-driven mobile UI tests for the TaskMind Android app, run with
[Momentic](https://momentic.ai) on Momentic's **cloud** Android emulators.

## What's here

- `momentic.config.yaml` — project config (test globs, auto-grant runtime permissions).
- `smoke.test.yaml` — installs the freshly built debug APK, launches `com.rajasudhan.taskmind`,
  and asserts the app opens to one of its own screens without crashing.

## CI

[`.github/workflows/momentic.yml`](../.github/workflows/momentic.yml) builds the debug APK and runs
the tests on every PR that touches `apps/taskmind/**` or `e2e/**` (and on manual **Run workflow**).
It authenticates with the `MOMENTIC_API_KEY` repository secret
(Settings → Secrets and variables → Actions). Each run uploads the freshly built APK to Momentic's
cloud emulator.

## Run locally

Prereqs: Node 22.12+ / 24 / 26, JDK 24+, the Android SDK, and a Momentic API key.

```bash
cd e2e
npm install
export MOMENTIC_API_KEY=...          # or run: npx momentic-mobile login
( cd .. && ./gradlew :apps:taskmind:assembleDebug )   # build the APK the test installs
npx momentic-mobile lint             # validate tests/config (offline)
npx momentic-mobile run              # run against the cloud emulator
```

Author or edit tests interactively with `npx momentic-mobile app`.

## Notes

- Cloud emulators are **x86_64**, so the APK must ship x86_64 native libs (the debug APK does).
- The debug APK is large (~260 MB) because it bundles native libs; each cloud run uploads it. A
  release or x86_64-only build would be lighter if upload time becomes a concern.
