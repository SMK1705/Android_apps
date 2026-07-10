# Changelog

All notable changes to **TaskMind** — the private, 100% on-device assistant — are documented here.
Versions follow the in-app `versionName`; release tags use `taskmind-v<update>`.

## [5.1] — Update 5.1 (`taskmind-v5.1`)

The reliability-and-reach release. Update 5.0 rebuilt how TaskMind *looks*; 5.1 rebuilds how much
it can *do* and how much you can *trust it to actually land the catch*. It can now answer questions
about your own items, track what other people owe you, run speech-to-text natively on-device, reach
your wrist, and plug into the system Gemini agent — on top of dozens of capture-, scheduling-, and
Gmail-reliability fixes so items and reminders stop slipping through the cracks.

### Added — new ways to act on your items
- **Ask TaskMind** — a private, on-device chat that *retrieves* your saved notes and answers over
  them ("what am I waiting on from Sam?", "what's due this week?"); a query with no clear slot
  degrades to a search instead of dumping the whole list.
- **Waiting-On tracker** — track what other people owe *you*; instead of auto-closing on any contact,
  it asks "did they deliver?" when that person is next in touch. **Person-context reminders** resurface
  an item the moment the relevant person is back in contact.
- **Daily Brief & Weekly Wins** — TaskMind now shows up on a schedule: a morning brief of what's ahead,
  and a streak-free Sunday recap of what actually got done.
- **Reliability Doctor** — a one-screen diagnosis of *why a reminder might not reach you* (notification
  access, battery optimisation, exact-alarm permission) with a fix for each.
- **Task Fade + bankruptcy** — stale, undated to-dos visually fade over time; one tap archives the
  whole faded pile so the list never becomes a graveyard.
- **Magic Breakdown** — split a big task into a checklist, on-device (or via a dedicated cloud schema).
- **Ramble** — segment a single voice brain-dump into many separate items.
- **Bounce-Back** — snooze a message back to yourself later as its original text.
- **Semantic search & safe dedup** — fuzzy, typo-tolerant recall over Notes (lexical word- and
  character-trigram feature hashing, not neural embeddings), and a *non-destructive* near-duplicate
  review that never silently merges.
- **Auto-tags & saved smart filters** — a closed-taxonomy auto-tagger plus saved, reusable filters.
- **Natural-language editing** — parse dates inline in quick capture, and edit any field of a
  suggestion (including by natural language) before approving.
- **"What TaskMind knows about me"** — a dashboard of every learned fact, each reviewable and
  forgettable, closing audit gaps.
- **Priority** — low / normal / high flags, an LLM-suggested priority from urgency cues, and priority
  ordering with Overdue back at the top.
- **Sharper reminders** — nag mode with alarm-grade escalation and smart snooze; an Inbox schedule chip.

### Added — reach & platforms
- **Wear OS companion** — capture by voice from your wrist and a **next-due tile**; the review
  notification is Wear-ready (snooze + voice capture), and the tile re-publishes opportunistically when
  the next due item changes.
- **System Gemini agent (AppFunctions)** — TaskMind exposes `createTask`, `getItemsDueToday`, and
  `snoozeItem` to the OS agent via `androidx.appfunctions`, so the assistant can act on your items.
- **Capture surface pack** — dynamic shortcuts, Direct Share, a clipboard chip, and a lock-screen widget.

### Added — on-device engines
- **Native Whisper second pass** — an optional, off-by-default `whisper.cpp` pass (arm64, via JNI)
  behind a `WhisperEngine` seam, layered on the primary **Vosk** first pass; it adopts Whisper's
  result only when it materially improves the transcript, and no-ops back to Vosk when unavailable.
- **System Gemini Nano** — an on-device understanding engine via the **ML Kit GenAI** Prompt API — one
  of two selectable on-device engines (**MediaPipe Gemma | Gemini Nano**), plus an on-device eval mode.
  (A LiteRT-LM engine seam is scaffolded for a future migration but not yet wired.)
- **Multimodal** — a multimodal-ready `LlmProvider` with screenshot/audio bypass routing; with a cloud
  key, **Gemini reads screenshots directly** (vision), bypassing OCR.
- **In-app model download** — fetch the Whisper and on-device LLM models from inside the app, no `adb`.
- **Two-way calendar** — a trustworthy one-way mirror *and* reflecting Google Calendar edits back onto
  the matching items.
- **Recurrence 2.0** — completion-based repeats and auto-detected recurring patterns.

### Changed
- **Honest engine labels** — the UI shows the *effective* route (on-device vs cloud) rather than a
  hardcoded "on-device"; a screenshot or audio capture that egresses to the cloud is labelled honestly.
- **Forward-only capture** — enabling a source now captures from that moment onward, not the last 24h
  of history, so turning on Notifications doesn't dredge up the day.
- **Gmail reads recent Primary mail whether read or unread**, so an email you open before the next scan
  isn't dropped (still deduped by message id, understanding stays on-device).

### Fixed — capture reliability & data-loss
- **Background scan was silently dead** — the `HiltWorkerFactory` wasn't being used; removed the default
  WorkManager initializer so periodic scans actually run.
- **SMS** — null-body loss, burst drops, and dedup races hardened; recover messages the 24h window
  misses via a bounded `_ID` watermark; fix an SMS-replay and a mid-scan loss.
- **Notifications** — a catch-up sweep plus a crash-guarded listener, MessagingStyle DM capture,
  re-post dedup, and recording a live notification's repost token only after it's handled.
- **Never silently delete the database** — an unopenable DB is *quarantined* (renamed aside) for
  recovery, and a restore refuses a newer-schema backup instead of wiping into an empty DB.
- Delete a captured **image** only after the pipeline consumes it.
- Match common **cross-OEM recorder folders** so recording capture isn't Samsung-only.
- **Wear** capture targets the phone by capability (no silent loss or duplicate items).
- Re-index a note's **embedding** when its title/summary is edited, so Ask/search stay fresh.
- Learn **group chats** via a stable rejection key derived from the notification title.

### Fixed — Gmail authentication
- Sign-in moved to `GoogleAuthUtil` and now **surfaces the real error** with actionable guidance
  instead of a blanket "cancelled"; **self-heals a 401** mid-scan (invalidate token + retry once);
  **probes a basic scope** on a hard failure to classify the cause; classifies *needs-consent* as
  recoverable; paginates and decodes bodies in their declared charset (no dropped mail, no mojibake);
  and **masks account emails** in scan logs.

### Fixed — scheduling & reminders
- Recurring-edit drop, lost-on-update, and an exact-alarm fallback in reminder scheduling.
- Keep **monthly** reminders on their anchor day through reschedule/snooze (no drift to the 28th).
- **Re-arm** reminders for the new wall-clock time on a timezone/clock change; resume recurring and
  waiting-on **nag chains after reboot**.
- Brief/recap fires at the right wall-clock time across **DST** and survives an app relaunch.
- Parse **"p.m."/"a.m."** (not just "pm"/"am"); date/time hardening (9:30 overdue, calendar-invalid
  dates, a stronger dedup key); harden model-output parsing against silent data loss.

### Internal
- **Comprehensive technical documentation** — a ~30k-word architecture & design document (17 Mermaid
  diagrams, ADRs, a permissions matrix) plus a rendered PDF, cross-checked against the source; the
  in-app "Encryption at rest" label and the docs were corrected to **AES-256-CBC + HMAC-SHA512**
  (the SQLCipher-4 default) — AES-256-GCM is used for the backup envelope, the Keystore master key, and
  EncryptedSharedPreferences values.
- **Backup** — a daily auto-snapshot JSON safety net (a wipe is never total) and a complete encrypted
  backup that now includes settings, keys, and source watermarks, plus a Markdown export.
- **CI** — cache the NDK + native `whisper.cpp` build; stop uploading the debug APK artifact (it was
  exhausting the Actions storage quota); sign debug builds with a committed keystore so `debug-latest`
  updates in place; a regression guard for the dead-`@HiltWorker` bug; a device smoke script + P0
  checklist.

### Privacy
- Speech-to-text (Vosk, with an optional off-by-default native-Whisper second pass) and screenshot OCR
  (Tesseract) stay **on-device**; a
  screenshot is sent to Gemini vision **only** when you've set a cloud key — and that route is labelled
  honestly and logged in **Data Egress**.
- **"What TaskMind knows about me"** makes every learned fact reviewable and forgettable.

## [5.0] — Update 5 (`taskmind-v5`)

A ground-up visual redesign and a much quieter, sharper understanding engine. TaskMind now looks
like an editorial app — and, just as importantly, stops turning shipping updates, receipts, promos,
and verification codes into to-dos.

### Added
- **"Bold" editorial redesign** — every screen rebuilt on a new design system (Instrument Serif
  headlines, Hanken Grotesk body, JetBrains Mono labels): Inbox, Notes, Note detail, Sources,
  Settings, Privacy, and the first-run guide, with a live light/dark toggle.
- **Quick-capture sheet** — a single **+** button opens a TYPE / SPEAK sheet: paste or jot text and
  "Analyse on-device", or record a voice note transcribed on the phone.
- **Bottom sheets** for the common actions — **snooze**, **add to calendar** (with an event
  duration), and **set a reminder** (one-off, repeating, or location-based).
- **Act from outside the app** — **Call** and **Get-directions** actions now appear on the
  **home-screen widget** (which surfaces the top item to review) and on the **review notification**.
- **Notes kind filter** — All / Tasks / Reminders / Notes chips, with live counts, are back above the
  active list.
- **"Needs setup" hint** — the audio and screenshot sources flag when their on-device model (Vosk /
  Tesseract) hasn't been downloaded yet.

### Changed
- **Far less noise** — the extraction prompt was rewritten around a single principle: extract only
  when *you* must act. Automated, informational, and promotional notifications — shipping/delivery
  updates, payment and autopay receipts, statements, subscription renewals, low-balance and
  transaction alerts, sign-in/security notices, marketing and "last chance" deadlines, verification
  codes — no longer become suggestions, even when they carry a date or an amount. Genuine invites,
  bills you must pay yourself, info to keep, and trips you'll attend are preserved.

### Internal
- **Extraction eval harness** — `tools/prompt_eval/` grew to a 166-case, web-grounded golden set and
  now reports a per-type **confusion matrix**, recall/precision, and field accuracy to
  `EVAL_REPORT.md` (measured on the cloud model: overall pass 85% → 98%, noise-rejection recall
  82% → 99%).
- **Automated test suite** — unit tests for the DAO and Room migrations, the extraction pipeline,
  approval and rejection learning, every ViewModel, the receivers and workers, plus JVM/Robolectric
  **Compose UI tests** for the redesigned screens.
- License changed to **All Rights Reserved** (proprietary); the unused Momentic E2E harness was removed.

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
