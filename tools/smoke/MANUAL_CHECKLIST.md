# TaskMind — manual P0 checklist

The human-judgment P0 cases that [`smoke.py`](smoke.py) **can't** automate (biometric prompts, permission
dialogs, file pickers, real SMS/calls, visual/extraction judgment). Run the smoke script first for the
mechanical gate, then work through this by hand before a release. The full 27-suite / 325-case plan is
in [`apps/taskmind/docs/`](../../apps/taskmind/docs/); this is the launch-blocking subset.

Legend: ☐ not run · ✅ pass · ❌ fail (file an issue). Note device + build (`versionName` / commit).

## Security & lock
- ☐ **Cold-launch gate** — fresh launch shows the biometric prompt before any data.
- ☐ **Re-lock on return** — background the app, return → prompted again.
- ☐ **Picker doesn't re-lock** — Settings → Export/Backup opens the file picker and returning does **not** re-prompt.
- ☐ **No credential** — on a device with no enrolled fingerprint/PIN, the lock auto-disables (no lockout).
- ☐ **Lock off** — Settings → App lock off → next launch opens straight to the Inbox.

## Sources & permissions
- ☐ Enable each source (SMS, Notifications, Call log, Contacts, Calendar, App-usage, Audio, Images, Gmail) → **correct runtime permission prompt** → state persists after relaunch.
- ☐ **Deny** a permission → the toggle stays off / shows a rationale, no crash.
- ☐ Notification-listener deep link opens the system settings screen; Gmail account chooser connects an account.
- ☐ Audio / Screenshots rows show the amber **"Model not downloaded"** hint until the Vosk / Tesseract model is fetched (Settings → download), then clear.

## Capture (real data)
- ☐ **Share text** from another app (subject + body) → "Added…" toast → appears in the Inbox.
- ☐ **Share an image** with text → OCR'd → appears in the Inbox.
- ☐ Quick Settings tile and home-screen widget both open quick-capture; the widget's **Call/Directions** work for a pending item.
- ☐ Capture **bypasses the lock** (no biometric to capture) but **viewing** the Inbox still requires it.

## Extraction quality (the v5 focus)
- ☐ Settings → **Test Extraction**: a clear task ("dentist tomorrow at 3pm") → a correctly-typed suggestion with the right date/time.
- ☐ **Noise is dropped** — paste a shipping update ("your order ships Tuesday"), an autopay receipt, a promo ("50% off ends tonight"), and an OTP → **no suggestion** for any of them.
- ☐ A real **meeting invite** and a **bill you must pay** ("electric bill due the 20th, not on autopay") **are** kept.

## Triage, schedule, act
- ☐ Approve / Reject / **Snooze** (1h / this evening / tomorrow); swipe-approve and swipe-reject; **Undo** the last action.
- ☐ Approve a **dated item with no time** → the time picker appears (set vs keep all-day).
- ☐ Approving a dated item writes a **calendar event** (de-duplicated; honors the target calendar).
- ☐ A **one-off reminder fires** at its time; a **recurring** one advances to the next occurrence; after a **reboot** active reminders re-arm; deleting a note **cancels** its alarm (no phantom).
- ☐ A **location reminder** fires on arrival at the saved place (emulator mock-location is fine).
- ☐ **Call** dials a resolved contact; **Get directions** opens Maps; a **missed call** (cellular *and* WhatsApp/Telegram) becomes a "Call back".
- ☐ **Approve / Reject from the notification** without opening the app; it refreshes to the next item.

## Backup, data, privacy
- ☐ **Backup** → passphrase → file created; **restore** with the correct passphrase → data present after restart.
- ☐ **Wrong passphrase** → restore fails and the **live database is untouched**.
- ☐ Export Notes JSON via the file picker; retention dropdown purges old notes; **Delete all private data** wipes notes/suggestions but the app stays usable.
- ☐ **Data Egress** reads *"No data has left this device"* on-device; enabling **Cloud LLM** logs a **metadata-only** entry (host/purpose/time — never content) per call.

## Accessibility
- ☐ TalkBack reads the cards, buttons, and icon-only controls (mic/FAB, swipe actions, overflow).
- ☐ Screen titles are announced as headings; touch targets are ≥ 48dp; large-font layout stays intact.
