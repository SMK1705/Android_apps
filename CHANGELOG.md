# Changelog

All notable changes to **TaskMind** — the private, 100% on-device assistant — are documented here.
Versions follow the in-app `versionName`; release tags use `taskmind-v<update>`.

## [4.0] — Update 4 (`taskmind-v4`)

Calls, places, and a sharper extraction engine. TaskMind can now actually *make the call* it
suggests, turns missed calls — including chat-app ones — into call-backs, routes you to places named
in your messages, and understands what you say a lot more accurately (including "every Monday").

### Added
- **Call actions** — a one-tap **Call** button on call reminders, plus **Call / Directions** quick
  actions right on Inbox cards.
- **Contacts-aware calling** — when a message names someone but gives no number (a WhatsApp "call
  me", a missed call from a saved contact), TaskMind resolves the name to a number via your
  **Contacts** so the Call button actually dials. New optional permission: `READ_CONTACTS`.
- **Missed-call capture** — missed calls become "Call back" suggestions: **cellular** calls from the
  call log, and **chat-app (WhatsApp / Telegram) missed calls** from their notifications (which never
  reach the call log). Private/unknown callers and email-titled service notifications are skipped.
- **Smart location** — the LLM pulls a place named in a message; a note then shows an **embedded
  geofence map** and a **Get directions** button (opens Google Maps).
- **Recurrence from text** — "every Monday", "monthly rent", "daily standup" are extracted as
  **recurring reminders** (daily / weekly / monthly), not just set by hand.
- **In-app model downloads** — fetch and install the Vosk (speech) and Tesseract (OCR) models from
  inside the app, no `adb` needed.
- **UI overhaul** — theme-aware category colors, source icons and confidence
  pills, haptic swipe, a Sources "cockpit", inline note editing, and checklist drag-to-reorder.
- **Over-the-air installs** — a *Publish debug APK* workflow keeps a rolling `debug-latest` release so
  you can install a fresh build straight from the phone.

### Changed
- **Incremental scans** — manual refresh and the periodic worker now scan **since the last scan**
  (a shared watermark, capped at 24h) instead of a fixed 10-minute window, so an item that arrived
  just before a refresh is no longer missed.
- **More accurate, more reliable extraction** — the prompt was rewritten for the real edge cases
  (phone numbers and call-back titles, named times like noon/EOD, ambiguous weekdays → the next
  future date, cancellations → nothing, no past-dated items, concrete-only locations, confidence
  calibration). The cloud path now uses Gemini **structured output** (an enforced JSON schema), and
  the source label ("Notification from Amma", "Voice note", …) is given to the model as context.

### Fixed
- Startup crash from an undecryptable EncryptedSharedPreferences store (device keystore key
  invalidation) — the store now self-heals on launch and is excluded from cloud/transfer backup.
- A **deleted reminder's alarm** could fire a phantom reminder and reschedule itself every week
  forever; the receiver now no-ops and cancels the alarm when the note is gone.
- No more useless "Call back &lt;email&gt;" or duplicate call-back suggestions.
- The app-lock no longer drops Storage Access Framework document-picker results.

### Internal
- Native libraries aligned for **16 KB page size** (Android 15+/16 devices).
- Database schema **v3 → v5** via in-place migrations (a `location`, then a `recurrence` column on
  suggestions).
- **`tools/prompt_eval/`** — a dependency-free Python harness that reads the live prompt and replays
  a golden set against the model to measure extraction accuracy (manual; not in CI).

### Privacy
- Contacts are read **on-device only** to turn a name into a number for the Call button — nothing
  leaves the phone. The cloud LLM stays opt-in and every call is still logged in **Data Egress**.

## [3.0] — Update 3 (`taskmind-v3`)

The capture-to-action release. TaskMind now pulls content in from anywhere, turns approved
items into real tasks, schedules reminders that repeat or trigger by place, learns from what you
reject, and lets you carry your data to a new device — all without anything leaving the phone.

### Added
- **Quick capture from anywhere** — a share-sheet target (text **and** images), a Quick Settings
  tile, a home-screen widget, and manual text entry in the Inbox. Captured content feeds straight
  into the understanding pipeline; nothing is shown, so the biometric gate on *viewing* stays intact.
- **Faster, reversible Inbox triage** — swipe to approve/reject, snooze an item until later, undo the
  last action, and filter by type or source.
- **Notes that act like tasks** — completion checkboxes with an Active/Completed split, tickable
  checklists for list-like to-dos, in-line deep links (tap a phone number, URL, email, or address),
  and free-text search.
- **Smarter scheduling** — recurring reminders (daily / weekly / monthly) that reschedule themselves
  when they fire, and **location reminders** that trigger via geofence when you arrive at a saved place.
- **New on-device sources** — screenshot **OCR** (Tesseract, fully offline) and a live watcher that
  transcribes new voice/call recordings immediately instead of waiting for the next scan.
- **It learns** — repeatedly rejecting a sender or pattern down-ranks similar future suggestions.
- **Act without opening the app** — Approve / Reject directly from the review notification.
- **Encrypted backup & restore** — export everything (notes, suggestions, and the database) into a
  single file sealed with **AES-256-GCM** under a passphrase you choose (PBKDF2-HMAC-SHA256). The
  backup is unreadable off-device without the passphrase; restore validates, swaps in the data, and
  restarts the app.
- **Scan-frequency control** — choose how often the background scan runs (15 min → 6 hours) to trade
  freshness against battery.

### Changed
- Approval logic is centralised so the Inbox, the notification actions, and quick-capture all create
  notes, alarms, and calendar events identically.
- Background scan rescheduling is now configurable at runtime.

### Privacy
- Every new source and capture surface stays on-device. **Settings → Data Egress** continues to read
  "No data has left this device" by default.
- Location is the only new sensitive permission and is fully optional — a geofence is registered only
  when you attach a place to a reminder.

### Internal
- Database migrated to schema v3 (completion, recurrence, checklist, location, snooze, and a
  learned-rejections table) in a single migration.
- New pure, unit-tested helpers: backup crypto envelope, recurrence maths, checklist parsing, and
  rejection matching.

## [2.0] — Update 2 (`taskmind-v2`)

### Added
- **Inbox:** concise AI summary cards with tap-to-expand; a prompt to set a time when approving a
  dated item that has none.
- **Email:** connect multiple Gmail accounts via the system account chooser.
- **Voice:** add items by dictation, transcribed on-device.
- **Notes:** a full detail view for each approved item.
- **In-app guide:** first-run walkthrough, re-openable from the help button.

### Fixed / Hardened
- Room migration, per-account email de-duplication, off-main-thread IO, and a zip-slip guard.

## [1.0] — Initial release (`v1.0`)

- Private on-device assistant covering SMS, notifications, call logs, calendar, Gmail (OAuth), and an
  app-usage digest, with on-device voice transcription (Vosk).
- On-device LLM understanding with an explicit cloud opt-in, an egress audit, retention/export
  controls, and a permissions panel.
