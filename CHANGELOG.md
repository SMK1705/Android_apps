# Changelog

All notable changes to **TaskMind** — the private, 100% on-device assistant — are documented here.
Versions follow the in-app `versionName`; release tags use `taskmind-v<update>`.

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
