# TaskMind device smoke test

A fast, `adb`-driven gate that checks the app is fundamentally alive on a real device — it launches,
every tab renders, the capture surfaces fire, the privacy board reads clean, and the background service
is running — capturing a screenshot for each step. It's the **mechanical slice** of the 27-suite
[functional test plan](../../apps/taskmind/docs/); the human-judgment P0 cases are in
[`MANUAL_CHECKLIST.md`](MANUAL_CHECKLIST.md).

## Run

The device must be connected (`adb devices`) and **unlocked** past the biometric gate.

```bash
python tools/smoke/smoke.py                     # auto-detects the only connected device
python tools/smoke/smoke.py --serial R5CYXXXXXX # or pick a device
# explicit adb path (Windows):
python tools/smoke/smoke.py --adb "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
```

Screenshots + a `report.md` land in `runs/<timestamp>/` (or `--out DIR`). Exit code is non-zero if any
case fails. Stdlib-only Python 3.9+ — no `pip install`.

## What it checks (mechanical P0)

| ID | Case |
|---|---|
| `TM-SMOKE-01` | app installed (reports `versionName`) |
| `TM-SMOKE-02` | launches to the Inbox (doubles as the "unlocked?" check) |
| `TM-SMOKE-03` | Notes tab renders |
| `TM-SMOKE-04` | Sources tab renders |
| `TM-SMOKE-05` | Privacy tab renders |
| `TM-SMOKE-06` | share-text capture surface fires |
| `TM-SMOKE-07` | background service / notification channels registered |

> Egress-clean, the widget/QS-tile/quick-capture surfaces, and other exported=false or
> config-dependent flows are in [`MANUAL_CHECKLIST.md`](MANUAL_CHECKLIST.md).

## What it can't

Biometric lock, permission dialogs, backup/restore file pickers, extraction *quality*, calendar writes,
and geofencing need a human — they're the checklist in
[`MANUAL_CHECKLIST.md`](MANUAL_CHECKLIST.md), not a script.
