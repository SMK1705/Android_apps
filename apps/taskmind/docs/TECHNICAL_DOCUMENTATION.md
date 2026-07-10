# TaskMind — Technical Architecture & Design Documentation

> A privacy-first, on-device personal assistant for Android that extracts reviewed, actionable items from a user's own signals (messages, calls, email, notifications, recordings, screenshots) — understood locally, saved only on explicit approval.

---

## Table of Contents

- [0. Document Control](#0-document-control)
  - [0.1 Revision History](#01-revision-history)
  - [0.2 Audience](#02-audience)
  - [0.3 Related Documents](#03-related-documents)
  - [0.4 Conventions & Assumptions](#04-conventions--assumptions)
- [1. Executive Summary](#1-executive-summary)
- [2. Background & Business Context](#2-background--business-context)
  - [2.1 Problem Statement](#21-problem-statement)
  - [2.2 Product Goals](#22-product-goals)
  - [2.3 Non-Goals / Scope Boundaries](#23-non-goals--scope-boundaries)
  - [2.4 Stakeholders](#24-stakeholders)
  - [2.5 Business / Distribution Context](#25-business--distribution-context)
- [3. System Overview](#3-system-overview)
  - [3.1 System Context](#31-system-context)
  - [3.2 High-Level Architecture (Container View)](#32-high-level-architecture-container-view)
  - [3.3 Key Quality Attributes](#33-key-quality-attributes)
- [4. Functional Requirements & Features](#4-functional-requirements--features)
  - [4.1 Feature Summary](#41-feature-summary)
  - [4.2 FR-01 — Inbox / Suggestion Review](#42-fr-01--inbox--suggestion-review)
  - [4.3 FR-02 — Notes](#43-fr-02--notes)
  - [4.4 FR-03 — Ask (Retrieval-Augmented Q&A)](#44-fr-03--ask-retrieval-augmented-qa)
  - [4.5 FR-04 — Sources & Permissions](#45-fr-04--sources--permissions)
  - [4.6 FR-05 — Privacy & Data Egress](#46-fr-05--privacy--data-egress)
  - [4.7 FR-06 — Quick Capture, Share Target, Tile & Widget](#47-fr-06--quick-capture-share-target-tile--widget)
  - [4.8 FR-07 — Reminders, Alarms, Geofences & Calendar Mirror](#48-fr-07--reminders-alarms-geofences--calendar-mirror)
  - [4.9 FR-08 — Daily Brief & Weekly Wins](#49-fr-08--daily-brief--weekly-wins)
  - [4.10 FR-09 — Reliability Doctor](#410-fr-09--reliability-doctor)
  - [4.11 FR-10 — Wear OS Companion](#411-fr-10--wear-os-companion)
- [5. Detailed System Design](#5-detailed-system-design)
  - [5.1 Component Architecture](#51-component-architecture)
  - [5.2 Module / Class-Level Design](#52-module--class-level-design)
  - [5.3 API Design](#53-api-design)
  - [5.4 Data Design](#54-data-design)
  - [5.5 Integration Design](#55-integration-design)
  - [5.6 Ingestion & Understanding Pipeline](#56-ingestion--understanding-pipeline)
- [6. Runtime Behavior & Key Scenarios](#6-runtime-behavior--key-scenarios)
  - [6.1 Scenario 1 — Background scan produces an Inbox suggestion](#61-scenario-1--background-scan-produces-an-inbox-suggestion)
  - [6.2 Scenario 2 — Approve a suggestion → Note + reminder + calendar](#62-scenario-2--approve-a-suggestion--note--reminder--calendar)
  - [6.3 Scenario 3 — Connect Gmail (OAuth) and scan mail](#63-scenario-3--connect-gmail-oauth-and-scan-mail)
  - [6.4 Scenario 4 — Ask a question (on-device RAG over saved Notes)](#64-scenario-4--ask-a-question-on-device-rag-over-saved-notes)
  - [6.5 Scenario 5 — Share text (or an image) from another app](#65-scenario-5--share-text-or-an-image-from-another-app)
- [7. Security Architecture](#7-security-architecture)
  - [7.1 Threat model](#71-threat-model)
  - [7.2 Authentication & authorization](#72-authentication--authorization)
  - [7.3 Data protection](#73-data-protection)
  - [7.4 Compliance & auditing (data-subject rights, on-device)](#74-compliance--auditing-data-subject-rights-on-device)
- [8. Deployment & Infrastructure](#8-deployment--infrastructure)
- [9. Observability & Operations](#9-observability--operations)
- [10. Performance & Scalability](#10-performance--scalability)
- [11. Reliability, Fault-Tolerance & Disaster Recovery](#11-reliability-fault-tolerance--disaster-recovery)
- [12. Technical Debt, Risks & Future Work](#12-technical-debt-risks--future-work)
- [13. Glossary](#13-glossary)
- [14. Appendices](#14-appendices)
  - [Appendix A — Diagram Catalog](#appendix-a--diagram-catalog)
  - [Appendix B — Configuration & Environment Variables](#appendix-b--configuration--environment-variables)
  - [Appendix C — References](#appendix-c--references)
  - [Appendix D — Architecture Decision Records (ADRs)](#appendix-d--architecture-decision-records-adrs)
  - [Appendix E — Permissions & Play-Policy Matrix](#appendix-e--permissions--play-policy-matrix)
  - [Appendix F — Non-Functional Target Summary](#appendix-f--non-functional-target-summary)

---

# 0. Document Control

| Field | Value |
|-------|-------|
| **Title** | TaskMind — Technical Architecture & Design Documentation |
| **System / Application** | TaskMind (Android app + Wear OS companion) |
| **Package / Application ID** | `com.rajasudhan.taskmind` |
| **Version (application)** | versionName `5.1` (versionCode `6`) |
| **Document Version** | 1.1 |
| **Status** | Baseline / Approved for internal circulation |
| **Date** | 2026-07-10 |
| **Owner** | TaskMind App Owner (GitHub: `SMK1705`) |
| **Authors / Contributors** | TaskMind Engineering |
| **Classification** | Internal — Engineering |
| **Source of truth** | This document is derived from the `main` branch of the `SMK1705/Android_apps` monorepo (module `:apps:taskmind`). Where the code and this document disagree, the code is authoritative. |

## 0.1 Revision History

| Version | Date | Author | Summary of Changes |
|---------|------|--------|--------------------|
| 1.0 | 2026-07-10 | TaskMind Engineering | Initial comprehensive baseline: system overview, features, detailed design (components, data, pipeline, APIs, integrations), runtime scenarios, security, deployment/operations, non-functional requirements, reliability/DR, technical debt, glossary, appendices. |
| 1.1 | 2026-07-10 | TaskMind Engineering | Corrected facts per codebase cross-check (DB cipher AES-256-CBC + HMAC-SHA512, Room schema v18, multimodal vision routing, cloud transport); synced application version references to 5.1 (`versionCode 6`). |

## 0.2 Audience

| Reader | Why they read this document |
|--------|-----------------------------|
| **New engineers (onboarding)** | Understand the system end-to-end well enough to operate and extend it. Start at §1, §3, then §5–§6. |
| **Architects (architecture review)** | Evaluate the architecture style, quality attributes, and evolution path. See §3, §5, §10–§12. |
| **Security & compliance reviewers** | Assess data protection, authn/authz, egress, and data-subject rights. See §7, §5.4, §9.2. |
| **Operations / on-device reliability** | Understand background execution, alarms, backups, and failure modes. See §8, §9, §11. |
| **Product** | Confirm functional scope and business rules. See §2, §4. |

## 0.3 Related Documents

| Document | Location | Purpose |
|----------|----------|---------|
| Product / feature README | `apps/taskmind/README.md` | User-facing capability summary and Google Cloud OAuth setup. |
| Functional Test Plan | `apps/taskmind/docs/FUNCTIONAL_TEST_PLAN.md` | Manual/functional test cases (TM-* IDs) for every feature and egress path. |
| Extraction Evaluation Report | `apps/taskmind/docs/EVAL_REPORT.md` | Extraction accuracy / confusion matrix from the `tools/prompt_eval` harness. |
| Source repository | GitHub `SMK1705/Android_apps` (module `:apps:taskmind`) | Authoritative implementation. |
| Delivery pipeline | `.github/workflows/android.yml`, `release-apk.yml`, `install-to-phone.yml` | CI build/test and sideload distribution. |
| Engineering roadmap | GitHub Project "TaskMind Roadmap" (board #4), milestone `taskmind-v5` | Issue/PR tracking and planned work. |

## 0.4 Conventions & Assumptions

- **Assumption labelling.** TaskMind is a self-contained on-device application with no first-party backend. Enterprise concepts that presuppose server infrastructure (Kubernetes, load balancers, multi-region failover, centralized telemetry, on-call rotations) are marked **Not applicable (on-device)** and mapped to their on-device equivalents. Any inference beyond the code is prefixed with **Assumption:**.
- **Terminology.** A user-visible extracted candidate awaiting review is a **Suggestion**; an approved, persisted item is a **Note**. The two are distinct entities (see §5.4).
- **Diagrams.** Diagrams are expressed in [Mermaid](https://mermaid.js.org/) and are embedded next to the text they illustrate; Appendix A (§14) consolidates the system-level diagrams.

---

# 1. Executive Summary

**TaskMind** is a privacy-first, on-device personal assistant for Android that turns the everyday signals already flowing through a user's own phone — SMS, notifications, call logs, Gmail, app-usage, voice/call recordings, and screenshots — into reviewed, actionable items (tasks, reminders, notes, waiting-on items, and calendar events). Its distinguishing principle is that **all understanding runs locally by default and nothing is ever persisted or scheduled until the user explicitly approves it**. The application is a native Kotlin / Jetpack Compose (Material 3) app built around a single `MainActivity`, packaged as `com.rajasudhan.taskmind` (versionCode 6, versionName "5.1"), targeting Android 15+ (`minSdk 35`, `targetSdk 36`, `compileSdk 37`), with a companion Wear OS module.

The core value proposition is **zero-effort capture with full user control**. Rather than requiring the user to manually enter every commitment, TaskMind watches opted-in sources, runs an LLM extraction pass, and surfaces each candidate as a *suggestion* in an Inbox. The user approves, edits, rejects, or snoozes each suggestion; only an approved suggestion becomes a saved Note and, where relevant, an exact-alarm reminder, a geofenced location reminder, or a de-duplicated system-calendar event. This "human-in-the-loop by construction" model is what lets the product access highly sensitive personal data responsibly.

Architecturally, TaskMind is deliberately **backend-less**. There is no first-party server; the phone itself is the backend. LLM inference is routed per request by a `RoutingLlmProvider` between an **on-device engine** (MediaPipe LLM Inference running a Gemma `.task`/`.litertlm` model, or ML Kit GenAI / Gemini Nano via AICore) and an optional **cloud engine** (Google Gemini `gemini-2.5-flash` with enforced structured JSON output). Consistent with the project's "honest engine labels" policy, the UI always reports the *effective* route rather than a hardcoded claim. The only outbound network calls the app makes are to Google — the cloud LLM, Gmail read-only OAuth, and Maps — and every such call is recorded (metadata only, never content) in a user-visible **Data Egress** audit screen that normally reads "No data has left this device."

Privacy and security are treated as first-class, verifiable properties rather than marketing claims. Data at rest lives in a **SQLCipher-encrypted Room database** (AES-256-CBC + HMAC-SHA512, Room schema v18) with secrets held in Keystore-backed `EncryptedSharedPreferences`; reads are gated by an optional **biometric app lock** (`BiometricPrompt`) that re-authenticates on every return to the foreground; and an encrypted local backup (`TMBK1`, PBKDF2-HMAC-SHA256 + AES-256-GCM) protects against data loss without ever exposing plaintext off-device. On-device transcription (whisper.cpp via JNI, with a Vosk fallback) and on-device OCR (ML Kit GenAI / Tesseract) ensure that even audio and images can be understood without leaving the device.

Operationally, the app sustains passive capture through a `dataSync` **foreground service** (live SMS and notification watchers), a battery-aware **WorkManager** periodic scan (`DataCollectionWorker`, user-configurable 15 min – 6 h, default ~30 min, `requiresBatteryNotLow`), and **exact alarms** (`AlarmManager`) that are re-armed across reboots, app updates, and timezone/clock changes via a `BootReceiver`. The app additionally exposes itself to the system agent (Gemini/Assistant) through Android **AppFunctions** (`createTask` / `getItemsDueToday` / `snoozeItem`) and to the wrist through the Wear companion.

TaskMind is currently distributed as a personal sideload build — a committed debug keystore plus a rolling `debug-latest` GitHub Release — and is **not yet on Google Play**. The known blockers to public distribution are documented and understood: restricted permissions (`READ_SMS` / `READ_CALL_LOG` are default-handler-only for public Play distribution), the absence of a release signing config, and a cloud LLM key currently sourced from an "express"-style credential. These are positioning constraints for a personal-use product, not defects in the on-device architecture.

**In one line:** TaskMind is a self-hosted-on-your-phone assistant that reads your own signals, understands them locally, and never commits anything to memory or your calendar without your explicit approval.

---

# 2. Background & Business Context

## 2.1 Problem Statement

Modern users receive a continuous stream of implicit commitments across many channels: a text asking to "call the plumber tomorrow," an email confirming an appointment, a missed call that warrants a callback, a screenshot of an event flyer, a voice memo dictating a grocery list. These action items are scattered across apps and are routinely lost, because capturing each one manually into a task manager is high-friction and easily skipped.

The conventional solution — a cloud assistant that ingests all of this data server-side — creates an unacceptable privacy exposure: it centralizes a user's most sensitive personal communications on third-party infrastructure. TaskMind's problem statement is therefore twofold:

1. **Reduce capture friction to near zero** by automatically extracting action items from signals the user already receives.
2. **Do so without surrendering privacy** — keep understanding on the device, make any exception to that explicit and auditable, and never act autonomously on the user's behalf without approval.

## 2.2 Product Goals

| # | Goal | How TaskMind realizes it |
|---|------|--------------------------|
| G1 | Automatic, low-effort capture | Per-source ingestion (SMS, notifications, call log, Gmail, app usage, recordings, screenshots) feeding an LLM extraction pipeline. |
| G2 | Privacy by default | On-device LLM routing, on-device STT/OCR, no first-party backend, SQLCipher encryption at rest, biometric app lock. |
| G3 | User control before persistence | Every extraction lands as an unapproved *Suggestion* in the Inbox; nothing is saved or scheduled until approved. |
| G4 | Transparency / auditability | `EgressLogger` records every outbound call (host + purpose, never content); surfaced in the Data Egress screen. Honest engine labels show the effective route. |
| G5 | Reliability of reminders | Exact `AlarmManager` alarms re-armed across reboot / update / clock change; geofenced location reminders; calendar mirroring with de-duplication. |
| G6 | Reach the user where they are | Share-sheet, Quick Settings tile, home-screen widget, launcher shortcuts, Wear voice capture, and AppFunctions exposure to the system agent. |

## 2.3 Non-Goals / Scope Boundaries

- **No first-party server.** TaskMind does not operate a backend, user accounts, or a multi-tenant service. Classic server-side concerns (load balancing, autoscaling, multi-region failover, Kubernetes) **do not apply**; the on-device equivalents are WorkManager scheduling, a foreground service, and local encrypted storage.
- **No autonomous action.** The app never saves a Note, schedules a reminder, or writes a calendar event without an explicit user approval step.
- **Not a Play Store production release (yet).** Distribution is personal sideload only; restricted permissions, release signing, and the cloud key are known blockers (see §1).
- **On-device LLM is a private fallback, not the quality leader.** Per project policy, the cloud engine is the recommended path for accuracy; the on-device engine trades quality for privacy (slower, less accurate, storage/battery cost) and is honestly labeled as such.

## 2.4 Stakeholders

| Stakeholder | Interest / concern |
|-------------|--------------------|
| **End user (primary, single-user)** | Owns the device and all data; wants effortless capture, control over what is saved, and confidence that data stays private. Is simultaneously the data subject, the data controller, and the operator. |
| **Developer / maintainer** | Owns build, sideload distribution, and the on-device model provisioning workflow; carries the tech-debt backlog (Play blockers). |
| **Google Cloud services (external dependency)** | Sole external counterpart: Gemini API (optional cloud LLM), Gmail read-only OAuth, Maps SDK. Not a partner or backend — an opt-in dependency reached only over TLS and always logged. |
| **Android platform / OEM (Samsung One UI)** | Governs permissions (notification access, usage access, exact alarms, background location), scoped storage behavior, and Doze/battery constraints the app must respect. |
| **System agent (Gemini / Assistant)** | Consumer of the app's AppFunctions surface (`createTask` / `getItemsDueToday` / `snoozeItem`). |

## 2.5 Business / Distribution Context

TaskMind is a **personal, single-user application** sideloaded onto the developer's own device (target reference hardware: Samsung Galaxy S25 Ultra, One UI, Android 15/16). It is signed with a committed debug keystore for stable in-place updates and delivered through a rolling `debug-latest` GitHub Release plus an optional USB-install CI path. There is no monetization, no telemetry, and no analytics — prior ML Kit usage-reporting was explicitly removed. The economic model is simply "the user runs their own assistant on hardware they already own," which is what makes the strict privacy posture feasible: with no server, there is no server-side cost, no server-side breach surface, and no server-side data-retention obligation.

---

# 3. System Overview

## 3.1 System Context

TaskMind runs entirely on the user's Android device. It has exactly one class of human actor (the device owner) and a small, fixed set of external systems — all of them Google services reached over TLS and recorded by the Egress audit. There is no first-party backend and no other network peer.

**External actors and systems**

| Entity | Type | Relationship to TaskMind |
|--------|------|--------------------------|
| Device owner | Human actor | Grants per-source permissions, reviews/approves suggestions, authenticates via biometrics. |
| On-device data sources | Platform surfaces | SMS, notifications, call log, contacts, calendar, app-usage, media (audio/images) — read locally, opt-in per source. |
| Google Gemini API | External (optional) | Cloud LLM (`gemini-2.5-flash`, structured JSON) — used only when the user selects the cloud engine. |
| Gmail (Google OAuth) | External (optional) | `gmail.readonly` fetch of recent Primary email; content is understood on-device. |
| Google Maps SDK / Maps app | External (optional) | Renders the geofence map preview and opens turn-by-turn directions. |
| System agent (Gemini/Assistant) | Platform consumer | Invokes exposed AppFunctions. |
| Wear OS companion | Paired device | Sends wrist voice captures over the Data Layer into the same pipeline. |

```mermaid
graph TD
    user["Device Owner<br/>(reviews & approves)"]

    subgraph device["User's Android Device (the backend)"]
        app["TaskMind App<br/>com.rajasudhan.taskmind<br/>Kotlin · Compose · single Activity"]
        sources["On-device sources<br/>SMS · Notifications · Call Log<br/>Contacts · Calendar · App Usage<br/>Recordings · Screenshots"]
        wear["Wear OS companion<br/>(wrist voice capture)"]
        agent["System Agent<br/>(Gemini / Assistant)<br/>via AppFunctions"]
    end

    subgraph google["Google Cloud (opt-in, TLS, egress-logged)"]
        gemini["Gemini API<br/>cloud LLM (gemini-2.5-flash)"]
        gmail["Gmail API<br/>OAuth gmail.readonly"]
        maps["Maps SDK / Maps app<br/>geofence map + directions"]
    end

    user -->|"grant permissions,<br/>approve / edit / reject"| app
    sources -->|"local read (opt-in)"| app
    wear -->|"Data Layer capture"| app
    agent -->|"createTask / getItemsDueToday /<br/>snoozeItem"| app

    app -.->|"cloud engine only"| gemini
    app -.->|"email fetch"| gmail
    app -.->|"map preview / directions"| maps

    app -->|"suggestions → approve →<br/>encrypted Notes, reminders, calendar"| user

    classDef ext fill:#1f2933,stroke:#8899a6,color:#e6edf3;
    classDef core fill:#0b3d2e,stroke:#2ea043,color:#e6edf3;
    class gemini,gmail,maps ext;
    class app core;
```

*Dashed edges are optional, opt-in, egress-logged network calls to Google; all other edges are local, on-device interactions. The user is both the sole data subject and the approver in the loop.*

## 3.2 High-Level Architecture (Container View)

Because TaskMind is backend-less, its "containers" are the major runtime components **inside the single app process** (plus the paired Wear module), not separately deployed services. The following describes each container and how requests flow between them.

**Container inventory**

| Container | Responsibility | Key components |
|-----------|----------------|----------------|
| **Presentation (UI)** | Single-Activity Compose UI, Navigation NavHost, biometric gate. | `MainActivity`, `TaskMindAppContent`, `LockScreen`, Inbox / Notes / Ask / Sources / Settings screens, `BoldBottomNav`, `GuideOverlay`. |
| **Ingestion & sources** | Read opted-in signals incrementally from a per-source watermark; live + periodic. | `RecentDataScanner`, per-source collectors, `TaskMindNotificationListener`, `TaskMindForegroundService`, `WearCaptureListenerService`, capture surfaces (`ShareTargetActivity`, `QuickTileService`, `QuickAddWidget`, `QuickCaptureActivity`). |
| **Understanding pipeline** | Noise pre-filter → OCR/STT → LLM extraction → JSON parse, dedup, learned-rejection down-rank → Suggestion. | `UnderstandingPipeline`, OCR (Tesseract / ML Kit GenAI), STT (whisper.cpp JNI / Vosk). |
| **LLM routing** | Choose on-device vs cloud per request; expose honest route label. | `RoutingLlmProvider`, on-device MediaPipe / ML Kit GenAI engine, cloud Gemini (Retrofit/OkHttp). |
| **Persistence** | Encrypted structured storage + settings/ledgers. | Room + SQLCipher DB (schema v18: `Note`, `Suggestion`, `NoteEmbedding`, `RejectedPattern`, `SavedFilter`, Tags), `SettingsManager` (EncryptedSharedPreferences), `SourceManager` (DataStore). |
| **Retrieval (Ask)** | RAG Q&A over saved Notes. | `HashingEmbedder` → `NoteEmbedding` cosine similarity + LLM answer. |
| **Scheduling & reliability** | Background scans, exact reminders, geofences, mirroring, self-test, backup. | `WorkManager` (`DataCollectionWorker`, DailyBrief/WeeklyWins/RecurrenceDetector/AutoSnapshot), `AlarmScheduler` + `AlarmReceiver` + `BootReceiver`, `GeofenceManager`, `CalendarMirror`, `ReliabilityChecker`, `BackupManager`/`BackupCrypto`. |
| **Integration / egress** | Outbound Google calls + system-agent surface + audit. | Retrofit/OkHttp client, `EgressLogger`, AppFunctions (`AgentFunctions`), Google OAuth (`GoogleAuthUtil`). |
| **Wear companion** (separate module) | Wrist capture + next-due tile. | `:apps:taskmind-wear`, `WearSyncScheduler`. |

**Primary request flow (capture → approval → action)**

1. **Trigger.** A live watcher (SMS `ContentObserver` / `NotificationListenerService` inside the `dataSync` foreground service), the periodic `DataCollectionWorker`, a manual Inbox refresh, or a capture surface (share sheet, tile, widget, Wear) initiates ingestion.
2. **Scan.** `RecentDataScanner` reads each enabled source forward from its watermark (incremental, deduped against processed-id ledgers in DataStore).
3. **Pre-process.** Images pass through OCR; audio through on-device STT; text through a noise pre-filter.
4. **Understand.** The `UnderstandingPipeline` prompts the LLM via `RoutingLlmProvider`, which selects the on-device or cloud engine; cloud calls are logged by `EgressLogger`.
5. **Stage.** The parsed result becomes a `Suggestion` (typed, confidence-scored) written to the encrypted DB and raised as a single "N suggestions to review" notification.
6. **Approve.** In the Inbox (or from the notification, or quick capture) the user approves / edits / rejects / snoozes. **Only on approval** does the suggestion become a `Note`, and — where applicable — an exact `AlarmManager` reminder, a geofence, and/or a de-duplicated calendar event.
7. **Retrieve.** Approved Notes are embedded (`HashingEmbedder`) for the Ask tab's on-device RAG.

**Application wiring.** `TaskMindApp` (`@HiltAndroidApp`) is the Hilt root and the `WorkManager` `Configuration.Provider` (it installs the `HiltWorkerFactory`; the manifest deliberately removes the default `androidx.startup` WorkManager initializer so every `@HiltWorker` can be constructed). On `onCreate` it eagerly creates the notification channel, publishes capture shortcuts, and (re)schedules the periodic scan and the brief/recap/snapshot/recurrence/Wear-sync jobs with `KEEP` semantics so Doze-deferred runs survive launch. It also implements `AppFunctionConfiguration.Provider` to hand the Hilt-managed `AgentFunctions` to the system agent.

## 3.3 Key Quality Attributes

Targets below are engineering objectives for a personal, single-user on-device app; where no formal SLO exists, the row is marked **Assumption**. Server-oriented attributes (multi-region availability, horizontal scalability) are explicitly reframed to their on-device equivalents.

| Attribute | Target / statement | Mechanism (grounded in code) | Notes |
|-----------|--------------------|------------------------------|-------|
| **Privacy** | Zero data egress by default; every exception is opt-in and auditable. | On-device LLM routing default; on-device STT/OCR; `EgressLogger` (host + purpose, never content); Data Egress screen. | The only network peers are Google (Gemini, Gmail, Maps). No telemetry/analytics. |
| **Confidentiality (at rest)** | All user content encrypted at rest (SQLCipher DB uses AES-256-CBC + HMAC-SHA512; keystore-backed stores and the backup envelope use AES-256-GCM). | SQLCipher Room DB (AES-256-CBC + HMAC-SHA512, schema v18); Keystore-backed `EncryptedSharedPreferences`; encrypted backup (`TMBK1`, PBKDF2-HMAC-SHA256). | Backup is unrecoverable without the passphrase (by design). |
| **Confidentiality (in transit)** | All outbound calls over TLS. | OkHttp/Retrofit TLS to Google endpoints. | — |
| **Access control** | Reads gated by device biometrics; re-auth on every foreground return. | `BiometricPrompt` (BIOMETRIC_STRONG or DEVICE_CREDENTIAL); re-lock on `ON_STOP`, re-check enrollment on `ON_RESUME`. | Lock is optional but DB stays encrypted regardless; lock is not enforced if no credential is enrolled (fail-safe against lockout). |
| **User control / correctness of intent** | Nothing persisted or scheduled without explicit approval. | Suggestion → Inbox approve/edit/reject/snooze → Note; confidence scoring; learned-rejection down-rank (`RejectedPattern`). | Human-in-the-loop is a structural invariant, not a setting. |
| **Reliability (reminders)** | Reminders fire at the exact wall-clock time and survive reboot / update / clock change. | Exact `AlarmManager` (`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`); `BootReceiver` on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET`; `ReliabilityChecker` self-test. | — |
| **Availability (on-device equivalent)** | App is "up" whenever the device is on; capture continues in background. | `dataSync` foreground service for live watchers; WorkManager for periodic scans. **Assumption:** no uptime SLO — no server to be down. | Multi-region / failover not applicable (single device). |
| **Data durability** | No total loss even if the Keystore key is reset. | Daily plain-JSON `AutoSnapshot` to app-private storage; user-initiated encrypted backup/restore; JSON export. | Snapshot is the safety net for an unopenable encrypted DB. |
| **Performance (extraction latency)** | Cloud path returns within a few seconds; on-device is slower with a one-time warm-up. | Structured-output Gemini (cloud) vs MediaPipe/Gemma (on-device). **Assumption:** first on-device inference is notably slower (model warm-up), then fast. | Honest labels warn the user of the on-device tradeoff. |
| **Battery / resource efficiency** | Background work must not run on low battery; scans are incremental. | `DataCollectionWorker` with `requiresBatteryNotLow`; watermark + processed-id dedup avoids re-work; scan interval user-tunable 15 min–6 h (default ~30 min). | — |
| **Scalability (on-device equivalent)** | Handles a single user's realistic signal volume on one device. | Incremental forward-only scans, capped look-back (≤24 h) on manual/periodic refresh, on-device semantic index for Ask. **Assumption:** not designed for multi-user or unbounded corpora. | Horizontal scaling / autoscaling not applicable. |
| **Compatibility** | Runs on Android 15+ with 16 KB memory pages. | `minSdk 35` / `targetSdk 36` / `compileSdk 37`; all bundled native libs 16 KB-page-aligned (whisper.cpp arm64-v8a). | — |
| **Maintainability / testability** | Regressions caught by JVM tests in CI on every push. | ~707 JVM unit tests (JUnit4 + Robolectric + mockk + Turbine + Compose UI test on JVM); GitHub Actions (`android.yml`, JDK 21) runs `testDebugUnitTest` + `assembleDebug`. | Distribution via committed debug keystore + rolling `debug-latest` release. |
| **Interoperability** | Discoverable by the system agent; reachable from many entry points. | AppFunctions (`createTask` / `getItemsDueToday` / `snoozeItem`); share-sheet, Quick Settings tile, widget, launcher shortcuts, Wear capture. | — |

**Known constraints affecting quality attributes:** public Play distribution is blocked by restricted permissions (`READ_SMS` / `READ_CALL_LOG` are default-handler-only), the absence of a release signing config, and a cloud LLM key currently sourced from an "express"-style credential; the native `libwhisper_jni.so` must remain 16 KB-page-aligned. These are tracked as tech debt and do not affect the on-device architecture's correctness for personal use.

---

# 4. Functional Requirements & Features

This section enumerates the functional requirements of TaskMind (`com.rajasudhan.taskmind`, versionCode 6). Every requirement is grounded in the shipped Kotlin/Compose implementation. Because TaskMind is a **privacy-first, on-device assistant with no first-party backend**, all "processing" described below runs on the handset (or, for the LLM step only, optionally against Google Gemini when the user selects the cloud engine); there are no server-side components, request queues, or multi-tenant concerns.

## 4.1 Feature Summary

Priority key: **P0** = core value proposition (the app is not viable without it); **P1** = important, ships in v5; **P2** = supporting / convenience surface.

| Feature ID | Name | Description | Primary Actor | Priority |
|---|---|---|---|---|
| **FR-01** | Inbox / Suggestion Review | Human-in-the-loop review queue of LLM-extracted suggestions; approve, edit, reject, snooze, sweep, before anything is saved. | End User | P0 |
| **FR-02** | Notes | The library of approved items (task / reminder / note / waiting-on) with kind/tag/saved filters, search, completion, reschedule, and lifecycle (Fade/Archive). | End User | P0 |
| **FR-03** | Ask (RAG Q&A) | Conversational retrieval-augmented Q&A over the user's own saved Notes with tappable citations. | End User | P1 |
| **FR-04** | Sources & Permissions | Per-source opt-in toggles (Notifications, SMS, Call Log, Contacts, App Usage, Gmail, Calendar, Audio, Screenshots) each gated by their runtime permission. | End User | P0 |
| **FR-05** | Privacy & Data Egress | Auditable privacy status board — egress log, encryption/app-lock/engine status, "what it knows," and destructive delete-all. | End User / Auditor | P0 |
| **FR-06** | Quick Capture, Share, Tile & Widget | Lock-free manual capture from a bottom sheet, the Android share sheet, a Quick Settings tile, a home-screen widget, and launcher shortcuts. | End User | P1 |
| **FR-07** | Reminders, Alarms, Geofences & Calendar Mirror | Exact reboot-surviving alarms, recurrence, snooze/nag, location geofences, and one-way system-calendar mirroring for dated items. | Background System | P0 |
| **FR-08** | Daily Brief & Weekly Wins | Scheduled morning brief and Sunday-evening completion recap, each composed deterministically and suppressed when empty. | Background System | P2 |
| **FR-09** | Reliability Doctor | Self-diagnostic of the six system conditions that let reminders actually reach the user, each with a deep-link fix. | End User | P1 |
| **FR-10** | Wear OS Companion | Wrist voice capture routed to the phone pipeline over the Data Layer, plus a next-due tile fed by the phone. | Wear User | P2 |

---

## 4.2 FR-01 — Inbox / Suggestion Review

### 4.2.1 Description
The Inbox (`InboxScreen` / `InboxViewModel`) is the app's control point and the embodiment of its "nothing is saved until you approve it" contract. It presents the queue of pending `Suggestion` records produced by the understanding pipeline and lets the user triage each one — **keep, dismiss, snooze, edit, or merge** — with an undoable action for every decision. It is also the primary manual-capture entry point (type or speak) and the honest-engine surface, labelling whether extraction ran on-device or in the cloud.

### 4.2.2 User Stories
- As a **busy professional**, I want incoming action items surfaced as reviewable suggestions so that I can accept the real ones and discard noise without hunting through my messages.
- As a **privacy-conscious user**, I want to approve each item before it is stored so that the app never silently records something I did not sanction.
- As a **user who mis-typed a detail**, I want to correct a suggestion in plain language ("move to Friday 6pm, high priority") so that I do not have to hand-edit each field.
- As a **user with a cluttered queue**, I want to sweep away low-confidence noise in one tap so that only credible items remain.
- As a **user who acted too fast**, I want to undo my last keep/dismiss/snooze so that a swipe mistake is never permanent.

### 4.2.3 Functional Behavior

| Aspect | Detail |
|---|---|
| **Inputs** | `dao.getPendingSuggestions()` (status = pending); manual text (`addManualEntry`); recorded audio (`addVoiceNote`); user gestures (swipe, tap, sheet selections). |
| **Processing** | Pending suggestions are combined with a 30-second `nowTicker` and filtered so that snoozed items (`snoozedUntil` in the future) are hidden until their time passes. A `null` list drives a skeleton; an empty list drives the "Inbox zero" state. Keep → `SuggestionApprover.approve` (see FR-07 for side effects). Dismiss → status `rejected` + `RejectionLearner.recordRejection` (down-ranks that sender); a dismissed auto-recurrence offer instead stores a `RejectedPattern` so it is not re-offered. |
| **Outputs** | An approved suggestion becomes a `Note`; a snoozed one is hidden and re-posted as a notification at its time (Bounce-Back via `SuggestionNotifier.scheduleResurface`); every action raises an "Undo" snackbar. |
| **Errors** | Voice: missing Vosk model → "Add an offline voice model in Settings"; blank transcript → silent no-op; the temp recording file is always deleted in a `finally`. Manual entry / Fix-it failures surface a snackbar or an inline "couldn't apply that — try rephrasing" without persisting. |

Key interaction affordances:
- **Swipe right = Keep, swipe left = Dismiss, tap = edit/expand.** `SwipeToDismissBox` with haptic confirmation; `confirmValueChange` always returns `false` so the row animates rather than physically dismissing.
- **Confidence read-out** per card (`confidence` bar; accent ≥ 0.60, else amber). A **Sweep** banner appears when any suggestion scores `< 0.5`, offering one-tap bulk dismissal of "likely noise."
- **Dated-but-untimed keep** opens the *Add to calendar* sheet (`BoldCalendarSheet`) to pick a time + duration (15/30/45/60/90 min) before promoting the item to a timed reminder — a `waiting_on` item deliberately keeps its type instead of becoming a plain reminder.
- **Snooze sheet** offers In 30 min, In 1 hour, This evening (18:00), Tomorrow morning (09:00), Tomorrow evening (18:00), Next week (09:00).
- **Natural-language "Fix it" (#115)** runs `SuggestionEditor.edit` and shows a before→after **diff dialog** for confirmation — a model edit is never applied silently.
- **Safe de-duplication (#145):** a near-duplicate is flagged (`possibleDuplicateOf`), never auto-dropped; the user chooses **Merge** (no rejection penalty) or **Keep both**.
- **Kind reassignment** (Task/Reminder/Note) and **priority override** (Low/Normal/High) inline; contextual **Call** / **Directions** actions when a number/place resolves.
- Overflow menu: **Add item**, **Keep all** (confirmed), **Dismiss all**.

### 4.2.4 Business Rules
1. **No auto-save.** A suggestion becomes durable data only through an explicit approve (individual, "Keep all," or a notification quick-action).
2. **Every single-item action is undoable**; bulk actions (Keep all / Dismiss all) are not.
3. **Honest engine labels (#197):** the header/tag reads "on-device" only when extraction *and* any enabled media source (Images/Audio) stay local; if a media source would egress to Gemini, the label switches to "cloud." Re-evaluated on every `ON_RESUME`.
4. **Rejection is a soft signal:** approving forgives a prior rejection for that sender; undo does not roll back the learning delta (intentional asymmetry).
5. Snooze arms a resurface alarm that no-ops if the item is later un-snoozed or actioned.

### 4.2.5 Dependencies
`RecentDataScanner` (incremental scan), `SuggestionApprover`, `RejectionLearner`, `UnderstandingPipeline`, `SuggestionEditor`, `SuggestionNotifier`, `RoutingLlmProvider`, `SourceManager`, `VoskTranscriber`, `AudioRecorder`; Room `TaskMindDao`.

### 4.2.6 Example Flow
1. A friend texts "can you send the deck before Fri?"; the background scan extracts a `waiting_on`/reminder suggestion at ~72% confidence.
2. The user opens the Inbox, sees the card with a schedule chip "Fri," and swipes right to Keep.
3. Because the item is dated but has no time, the *Add to calendar* sheet opens; the user sets 15:00, 30 min, and taps **Add to calendar**.
4. `SuggestionApprover` writes the Note, arms an exact alarm, and mirrors a calendar event; an "Added to calendar" snackbar offers Undo.

---

## 4.3 FR-02 — Notes

### 4.3.1 Description
Notes (`NotesScreen` / `NotesViewModel`) is the durable library of everything the user approved. It renders four kinds (`todo`, `reminder`, `note`, `waiting_on`) with an Active/Done segmented view, kind and auto-tag filters, pinnable saved filters, blended lexical+semantic search, inline completion and overdue-reschedule, and a non-destructive lifecycle (Task Fade → Archive → Restore).

### 4.3.2 User Stories
- As a **user with many items**, I want to filter by kind, tag, or a saved combination so that I can focus on one slice (e.g. "work reminders") instantly.
- As a **user searching by memory**, I want to find a note by meaning, not just exact words, so that "flight" surfaces "book the plane tickets."
- As a **user who fell behind**, I want overdue items flagged with one-tap "Today/Tomorrow" reschedule so that triage is frictionless.
- As a **user drowning in stale to-dos**, I want undated tasks I have ignored for weeks to fade and be archivable in bulk so that my list stays honest without deleting anything.
- As a **user waiting on someone**, I want a "Ready to close" prompt when they get back in touch so that I can confirm delivery in one tap.

### 4.3.3 Functional Behavior

| Aspect | Detail |
|---|---|
| **Inputs** | `dao.getActiveNotes()`, `getCompletedNotes()`, `getArchivedNotes()`; search query; kind filter; multi-select tag set; saved filters (`SavedFilterStore`). |
| **Processing** | The displayed list derives from a `combine` of query/segment/kind/tags via `flatMapLatest`. Active view is sorted by `prioritise()` (overdue → soonest due → priority → type → recency). Search blends **substring hits** (always shown, ranked top) with **semantic hits** from `SemanticIndex.scores(q, SEARCH_FLOOR)`. Kind filter supports virtual buckets: `overdue`, `ready_to_close` (`pendingConfirmSince != null`), `fading` (`TaskFade.isFading`), `archived`. |
| **Outputs** | A live, filtered, prioritized card list with per-kind and per-tag count badges; completion strike-through; overdue escalation; checklist progress (`n/m`). |
| **Errors** | Unparseable due dates sort last (`Long.MAX_VALUE`) rather than crashing; a blank/invalid filter save is a no-op. |

Notable behaviors:
- **Completion** (`setCompleted`) stamps `completedAt`; a completion-based recurrence (#124) rolls the item forward via `CompletionRecurrence`, otherwise the mirrored calendar event is deleted (#119).
- **Reschedule** an overdue item re-arms its alarm (preserving a monthly `recurrenceAnchorDay`, #177), moves the mirrored calendar event, and clears any stale nag-firing flag.
- **Task Fade (#125):** the *Fading* shelf offers one-tap "Archive all" (declare bankruptcy); the *Archived* shelf offers "Restore all." Archived items are removed from active/done lists but never deleted.
- **Ready to close** is driven by `WaitingOnResolver`: when a waiting-on counterparty resurfaces (whole-word match via `PersonMatch`), the note is stamped `pendingConfirmSince` and a "did they deliver?" prompt is raised — the app never auto-completes the note; closing it is an explicit user tap.
- Semantic index is **back-filled** on first load so pre-index notes are searchable.

### 4.3.4 Business Rules
1. Switching to **Done** clears both kind and tag filters (it must show every completed item).
2. Tag filter is **AND-ed** with the kind filter and **OR-ed** within the selected tags.
3. **Nothing is ever deleted by the lifecycle** — Fade/Archive are reversible; only the explicit retention purge (FR-07/config) or Delete-all (FR-05) removes data.
4. Only unfinished `reminder`/`todo` items escalate to the overdue (red) state.

### 4.3.5 Dependencies
`AlarmScheduler`, `CalendarMirror`, `CompletionRecurrence`, `RecurrenceUtil`, `SavedFilterStore`, `SemanticIndex`; `Tags` taxonomy; `TaskMindDao`.

### 4.3.6 Example Flow
1. The user taps the **Overdue** chip; three unfinished reminders sort to the top in red with relative "Overdue · 2d" labels.
2. On one, they tap **Tomorrow**; `reschedule` bumps `dueDate`, re-arms the exact alarm, and shifts the calendar event.
3. Later, a "Fading" chip shows five untouched undated tasks; the user opens the shelf and taps **Archive all**, confirming the bankruptcy dialog — the tasks move to Archived, recoverable anytime.

---

## 4.4 FR-03 — Ask (Retrieval-Augmented Q&A)

### 4.4.1 Description
Ask (`AskScreen` / `AskViewModel`) is a conversational interface for recall over the user's own saved Notes. It answers questions like "What's due this weekend?" using an on-device semantic index for retrieval and the routed LLM for the answer, returning tappable result cards as citations.

### 4.4.2 User Stories
- As a **user with a full second brain**, I want to ask questions in natural language so that I can recall commitments without manually filtering.
- As a **user who distrusts black boxes**, I want each answer backed by tappable source notes so that I can verify what it drew on.
- As a **privacy-conscious user**, I want to know whether my question is read locally or in the cloud so that I understand where my words go.

### 4.4.3 Functional Behavior

| Aspect | Detail |
|---|---|
| **Inputs** | A free-text utterance (typed or via an example chip). |
| **Processing** | `AskEngine.ask(text)` retrieves relevant notes from the semantic index and composes an answer via the routed LLM. UI tracks a `thinking` state and auto-scrolls to the latest turn. |
| **Outputs** | An `AskResult` (`answer` + `notes`) rendered as an assistant bubble plus a citation card per note; each card is tappable to open the note. |
| **Errors** | Any exception yields a graceful "Something went wrong — try rephrasing." with `EMPTY` kind; input is ignored while a request is in flight. |

### 4.4.4 Business Rules
1. Answers are grounded **only** in the user's saved Notes — never external knowledge.
2. The empty-state copy is honest about interpretation locus (#197): "nothing leaves the device" on-device, versus "your question is read by your cloud engine (Gemini)" on cloud. Re-read on `ON_RESUME`.
3. The stored corpus is always local; only the utterance is subject to routing.

### 4.4.5 Dependencies
`AskEngine`, `RoutingLlmProvider`, `SemanticIndex` (HashingEmbedder → `NoteEmbedding` cosine similarity).

### 4.4.6 Example Flow
1. The user taps the example "Anything overdue?"; a user bubble appears and a "Thinking…" indicator shows.
2. `AskEngine` retrieves overdue notes and the LLM summarizes them; the answer bubble lists two items with citation cards.
3. Tapping a card opens the corresponding note detail.

---

## 4.5 FR-04 — Sources & Permissions

### 4.5.1 Description
Sources (`SourcesScreen` / `SourcesViewModel`) is the consent surface: each data source is an independent opt-in toggle that acquires its runtime permission (or system-settings grant) on enable. Sources are grouped into **Passive observers**, **Connected accounts**, and **Reactive sensors**.

### 4.5.2 User Stories
- As a **new user**, I want to enable only the sources I trust so that TaskMind watches exactly what I allow and nothing more.
- As a **user cutting noise**, I want to restrict notification monitoring to specific apps so that only meaningful apps are read.
- As a **Gmail user**, I want to connect one or more mailboxes read-only so that action items in email are caught, and disconnect any of them later.
- As a **user of on-device transcription/OCR**, I want to be warned when the required model is not downloaded so that I know why a source is inert.

### 4.5.3 Functional Behavior

| Group | Source | Permission / Grant | Notes |
|---|---|---|---|
| Passive | Notifications | `NotificationListener` access (system settings) | Optional per-app allowlist; empty = watch all apps. |
| Passive | SMS Messages | `READ_SMS` | Toggle reflects permission-granted state. |
| Passive | Call Logs | `READ_CALL_LOG` | Missed calls → "Call back." |
| Passive | Contacts | `READ_CONTACTS` | Name→number resolution for the Call action; default-on but gated by grant. |
| Passive | App Usage | Usage-access (system settings) | Daily screen-time digest. |
| Connected | Email (Gmail) | OAuth `gmail.readonly` via `GmailAuth` | Multi-account: account chooser → `authorize(email)` → consent round-trip; per-account disconnect. |
| Connected | Calendar | `READ_CALENDAR` + `WRITE_CALENDAR` | Dedupe + add events on approve. |
| Reactive | Voice / Call recordings | `READ_MEDIA_AUDIO` + configurable folder paths | Warns if Vosk model absent. |
| Reactive | Screenshots (OCR) | `READ_MEDIA_IMAGES` | Warns if Tesseract model absent. |

- **Processing:** toggles persist through `SourceManager` (DataStore); `refreshModelStatus()` re-checks Vosk/Tesseract presence on entry so a model downloaded in Settings clears the amber warning. Installed-app list for the allowlist is loaded off-main and cached.
- **Errors:** Gmail connection failures produce specific status lines ("Couldn't connect: …", "already connected", "No account selected"); a transient profile-email lookup failure falls back to the chooser-selected email so a successful consent is not thrown away.

### 4.5.4 Business Rules
1. A source is effectively on only when the toggle is on **and** its permission is granted — enabling a denied source immediately launches the permission/settings request.
2. **Ingestion is forward-only** from the moment a source is enabled (watermark-based, per canonical pipeline) — enabling a source does not backfill historical data.
3. Disabling Email disconnects **all** Gmail accounts; disconnecting the last account disables the source.
4. Understanding runs on-device by default; the engine is changed in Privacy/Settings, not here.

### 4.5.5 Dependencies
`SourceManager`, `GmailAuth`/`GmailCollector`, `VoskTranscriber`, `OcrEngine`, Accompanist permissions.

### 4.5.6 Example Flow
1. The user enables **Notifications**; the OS listener-settings screen opens and, on return, they pick Messages + WhatsApp in the allowlist to cut noise.
2. They enable **Email**, choose a Google account, complete the consent screen, and the account appears as "1 account connected."

---

## 4.6 FR-05 — Privacy & Data Egress

### 4.6.1 Description
Privacy (`PrivacyScreen`, backed by `SettingsViewModel`) is an auditable, at-a-glance status board: the egress hero (how much, if anything, has left the device), always-on guarantees (app lock, encryption, engine, no-telemetry), links to "what TaskMind knows" and the Reliability Doctor, and the destructive delete-all action. It is the accountability counterpart to the app's data-collection power.

### 4.6.2 User Stories
- As a **privacy-conscious user**, I want to see a running count of every outbound network call so that I can verify the app's on-device claim.
- As a **security-minded user**, I want confirmation that data is encrypted at rest and gated by biometrics so that a lost phone does not expose my life.
- As a **user changing my mind**, I want a single irreversible "delete everything" so that I can walk away cleanly.

### 4.6.3 Functional Behavior

| Aspect | Detail |
|---|---|
| **Inputs** | `EgressLogger.events` (host + purpose + timestamp, metadata only, capped at 100); app-lock state; effective engine flag. |
| **Processing** | The hero shows "No data has left this device" when the log is empty, else "*N* egress events logged." Status rows render App lock (ON/OFF), Encryption at rest (SQLCipher · AES-256-CBC), Understanding engine (LOCAL Gemma vs CLOUD "calls are logged"), and No telemetry (✓). |
| **Outputs** | The board plus navigation into the full egress log (Settings), the "What TaskMind knows about me" screen, and the Reliability Doctor. |
| **Errors** | Delete-all is guarded by a confirmation dialog; `EgressLogger` load failures degrade to an empty log rather than crashing. |

### 4.6.4 Business Rules
1. The egress log records **metadata only — never content** — and is itself stored in EncryptedSharedPreferences.
2. On-device is the default and the "clean" state; any cloud LLM call is logged and reflected in the engine badge and egress count.
3. Delete-all permanently erases notes, suggestions, source toggles, and saved keys/settings, and cannot be undone.

### 4.6.5 Dependencies
`EgressLogger`, `SettingsManager` (app lock, engine, retention), Room; `KnowsScreen`, `ReliabilityScreen`.

### 4.6.6 Example Flow
1. A user on the on-device engine opens Privacy and sees the reassuring "No data has left this device" hero with "EGRESS LOG · 0 ENTRIES."
2. After switching to the cloud engine and running a scan, the hero updates to "3 egress events logged"; each entry names `generativelanguage.googleapis.com` and its purpose, never the text sent.

---

## 4.7 FR-06 — Quick Capture, Share Target, Tile & Widget

### 4.7.1 Description
A family of **lock-free** manual-capture surfaces that feed the same understanding pipeline as every automatic source, so a user can add an item from anywhere in a second: the in-app Quick Capture sheet (type or speak), the Android share sheet, a Quick Settings tile, a home-screen widget, and launcher long-press shortcuts.

### 4.7.2 User Stories
- As a **user with a fleeting thought**, I want a one-tap capture from the tile or widget so that I can jot it without unlocking the full app.
- As a **user reading elsewhere**, I want to share text or a screenshot into TaskMind so that content from any app becomes an action item.
- As a **user acting on a pending item**, I want the widget to surface the top suggestion with a Call/Directions button so that I can act without opening the app.

### 4.7.3 Functional Behavior

| Surface | Entry | Behavior |
|---|---|---|
| **Quick Capture sheet** (`BoldCaptureSheet`) | Inbox FAB / overflow | Type (with live schedule highlighting via `NaturalDate.parse` + a "Use copied text" clipboard chip) or Speak; both run `pipeline.processText(seedSchedule = true)`. |
| **QuickCaptureActivity** | Tile, widget, launcher shortcut | Lock-free type dialog; `MODE_SPEAK` jumps straight to system dictation, falling back to typing. Submits via `CaptureWorker.enqueue`. |
| **ShareTargetActivity** | Android share sheet | `text/plain` → combined subject+body; `image/*` → copied to cache and OCR'd by the worker; enqueues and finishes invisibly. |
| **QuickTileService** | Quick Settings tile | Opens QuickCaptureActivity via `startActivityAndCollapse`. |
| **QuickAddWidget** | Home screen | Quick-add button + top pending suggestion title + contextual **Call**/**Directions**; refreshed via `refresh()` when the pending set changes. |

- **Errors:** cancelled/empty dictation drops to the type field rather than dumping the user out; a failed image copy aborts silently; all capture surfaces confirm with a "Added to TaskMind for review" toast/snackbar.

### 4.7.4 Business Rules
1. Capture surfaces show **no existing data**, so they intentionally **bypass the biometric lock** — the lock still guards all *reads*.
2. Manually captured items land in the Inbox as pending suggestions like any other source — capture ≠ save.
3. `PendingIntent` request-code namespaces are partitioned (capture, widget action base, alarms, snooze) to prevent collisions.

### 4.7.5 Dependencies
`CaptureWorker`, `UnderstandingPipeline`, `NaturalDate`, `ClipboardCapture`, `resolveCallNumber`, OCR engine; Room DAO (widget entry point).

### 4.7.6 Example Flow
1. Reading a confirmation email in Gmail, the user taps Share → TaskMind; `ShareTargetActivity` enqueues the text and shows a toast.
2. The `CaptureWorker` runs it through the pipeline; a suggestion appears in the Inbox, and the home-screen widget refreshes to show its title with a **Directions** button (a venue was detected).

---

## 4.8 FR-07 — Reminders, Alarms, Geofences & Calendar Mirror

### 4.8.1 Description
The scheduling substrate that makes an approved dated item actually reach the user: exact alarms that survive Doze and reboot, recurrence advancement, snooze/nag re-fires, location geofences, and a one-way mirror into the system calendar. These are invoked at approval (`SuggestionApprover`) and throughout a note's life.

### 4.8.2 User Stories
- As a **forgetful user**, I want reminders to fire at the exact minute even in battery-saver so that I am not reminded late or not at all.
- As a **user with recurring chores**, I want a "daily/weekly/monthly" reminder to roll forward automatically so that I set it once.
- As a **user with location errands**, I want to be reminded when I arrive somewhere so that "buy milk near the store" triggers on arrival.
- As a **calendar user**, I want approved timed items mirrored to my calendar (and kept in step) so that my existing planning tools stay accurate.

### 4.8.3 Functional Behavior

| Component | Behavior |
|---|---|
| **AlarmScheduler** | Keyed by note id; `setExactAndAllowWhileIdle` (falls back to `setAndAllowWhileIdle` without exact-alarm permission — fires in a maintenance window rather than being dropped). Advances a recurring reminder past a stale slot to the next future occurrence (preserving monthly `anchorDay`). Separate request-code namespaces: main alarm (`= noteId`) vs snooze/nag re-fire (`7_000_000 + noteId`). Never fires in the past. |
| **Recurrence / nag** | `AlarmReceiver` reschedules recurring reminders on fire and drives the nag re-fire ladder; boot re-arm via `BootReceiver`. |
| **GeofenceManager** | Registers a `NEVER_EXPIRE`, enter-transition geofence (default radius 150 m) when a place geocodes and `ACCESS_BACKGROUND_LOCATION` is granted; fires `GeofenceBroadcastReceiver`. |
| **CalendarMirror (#119)** | Owns every device-calendar write: `insert` on approval (source-gated + dedup within ±1 day of same-title event), `update` on reschedule/rename, `delete` on complete/delete. Timed events use the system zone; undated → all-day (UTC). Provider errors are swallowed so a calendar hiccup never breaks the note write. |

- **Inputs:** the approved suggestion's `dueDate`/`dueTime`/`recurrence`/`location`/`priority`.
- **Outputs:** an armed alarm returning the actually-armed date (persisted back if advanced), an optional calendar event id stored on the note, an optional geofence.
- **Errors:** unparseable time/date → alarm not armed (returns null); missing `WRITE_CALENDAR` or writable calendar → mirror no-ops.

### 4.8.4 Business Rules
1. A note has **exactly one** main alarm; re-establishing it invalidates any in-flight snooze/nag re-fire to avoid parallel ringing.
2. A recurring reminder is **never dropped** merely because its stored slot has passed — it advances to the next occurrence.
3. The calendar mirror is **one-way** (TaskMind → calendar); `insert` respects the Calendar source toggle, but `update`/`delete` always run for an event TaskMind created, so turning the toggle off cannot strand a stale event.
4. Waiting-on items with a follow-up time get a chase-up nudge but **no** calendar event (nothing is being attended).

### 4.8.5 Dependencies
`SuggestionApprover`, `AlarmReceiver`/`BootReceiver`, `PlaceGeocoder`, `GeofenceBroadcastReceiver`, `RecurrenceUtil`, `SettingsManager` (default duration/calendar id), `SourceManager`; `TaskMindDao`.

### 4.8.6 Example Flow
1. The user approves "Renew insurance on the 31st, monthly." `SuggestionApprover` stores the note with `recurrenceAnchorDay = 31`, arms an exact alarm, and mirrors an all-day event.
2. On fire, `AlarmReceiver` advances the reminder to the next month; because a short month would clamp to the 28th, the anchor keeps it on the 31st where possible (#177), and `CalendarMirror.update` moves the event to match.

---

## 4.9 FR-08 — Daily Brief & Weekly Wins

### 4.9.1 Description
Two scheduled, deterministic digests that make the app's value visible without nagging: a **morning brief** of today's real load and a **Sunday-evening recap** of the week's completed items (which doubles as a trust report on how many wins were auto-caught).

### 4.9.2 User Stories
- As a **user starting the day**, I want a short morning brief of what is overdue/due/queued so that I know where to start.
- As a **user seeking motivation**, I want a weekly recap of what I finished — and how much the app caught for me — so that I feel the value without a fragile streak.

### 4.9.3 Functional Behavior

| Component | Behavior |
|---|---|
| **DailyBriefComposer** | Pure function of `(overdue, dueToday, pending, focus)` → a titled brief; the title leads with the heaviest real load; up to three focus titles are named ("Start with: …"). Returns **null** when nothing is worth surfacing. |
| **DailyBriefScheduler** | 24-hour periodic WorkManager job first landing at the user's chosen time; `CANCEL_AND_REENQUEUE` on an explicit time change, `KEEP` on a launch re-arm so a Doze-deferred run is not destroyed. |
| **WeeklyWinsComposer** | Pure function of the week's completed `WeeklyWin`s → "*N* wins this week 🎉" + "*K* caught from [channels] you'd have forgotten" + up to three named items. Deliberately **streak-free**. Returns **null** for an empty week. |
| **WeeklyWinsScheduler** | 7-day periodic job on `SUNDAY` at the chosen hour, same KEEP/REENQUEUE and DST-safe delay math as the brief. |

- **Errors:** an empty state produces no notification at all (a "nothing to do" ping would be noise).

### 4.9.4 Business Rules
1. **Earn the interruption:** both composers return null rather than emit an empty/negative message.
2. The weekly recap avoids streaks by design (churn spikes when a first streak breaks) and instead reports a plain count plus the capture-source attribution.
3. Delivery times are user-configurable; scheduling is DST-safe (real elapsed millis, not naive wall-clock hours).

### 4.9.5 Dependencies
`DailyBriefWorker`/`WeeklyWinsWorker`, WorkManager, `TaskMindDao` (counts/completions), notification channel.

### 4.9.6 Example Flow
1. At 08:00, `DailyBriefWorker` composes "Good morning — 2 overdue" with body "2 overdue · 1 due today · 3 to review / Start with: Send deck, Call plumber."
2. On Sunday at 18:00, `WeeklyWinsWorker` posts "9 wins this week 🎉 — You finished 9 things this week. 6 caught from a notification, email and SMS you'd have forgotten."

---

## 4.10 FR-09 — Reliability Doctor

### 4.10.1 Description
The Reliability Doctor (`ReliabilityChecker`, surfaced via `ReliabilityScreen`) is a read-only self-diagnostic of the six system conditions that determine whether reminders can actually reach the user. Each failing check carries a deep link into the exact system screen that fixes it — critical on aggressive-battery-management OEMs where background access is silently killed.

### 4.10.2 User Stories
- As a **user relying on reminders**, I want to confirm the app can actually notify me so that a silently revoked permission does not cost me a deadline.
- As a **user on a Xiaomi/OnePlus/Samsung device**, I want a one-tap route to exempt the app from battery optimization so that the background watcher keeps running.

### 4.10.3 Functional Behavior
`ReliabilityChecker.run()` evaluates the live system state (no mutations) and returns `HealthCheck`s sorted worst-status-first:

| Check | Condition | Status | Fix deep link |
|---|---|---|---|
| Notification access | Listener package enabled | FAIL if off | Notification-listener settings |
| Notifications allowed | App notifications enabled | FAIL if off | App notification settings |
| Reminders make a sound | Reminders channel ≥ DEFAULT importance | WARN if quieted | Channel settings |
| Exact alarms | `canScheduleExactAlarms()` | WARN if off | Exact-alarm settings |
| Battery optimization | `isIgnoringBatteryOptimizations()` | WARN if optimized | Battery-optimization list |
| Background watcher | `TaskMindForegroundService.isRunning` | WARN if not running | (reopening restarts it) |

- **Errors:** a not-yet-created reminder channel is treated as healthy (it is created HIGH on first use), avoiding false alarms on fresh installs.

### 4.10.4 Business Rules
1. The checker **only reads** system state; every remediation is an explicit user action launched from the surfaced `Intent`.
2. Ordering floats FAIL and WARN above OK so the most damaging problem is seen first.
3. Battery exemption opens the Play-policy-safe optimization list, not a direct-grant prompt.

### 4.10.5 Dependencies
`NotificationManagerCompat`, `AlarmManager`, `PowerManager`, `TaskMindForegroundService`; `ReliabilityViewModel`/`ReliabilityScreen`, `ReliabilityTestReceiver` (device QA).

### 4.10.6 Example Flow
1. After an OS update silently disables the notification listener, the user opens Reliability and sees a red "Notification access — Off" at the top.
2. Tapping **Grant access** opens the listener-settings screen; on return, the check flips to OK and reminders resume.

---

## 4.11 FR-10 — Wear OS Companion

### 4.11.1 Description
The companion module `:apps:taskmind-wear` provides wrist-first voice capture and an at-a-glance next-due tile. The watch performs no LLM work: it captures raw text and sends it to the paired phone over the Data Layer, where the existing pipeline runs — preserving the on-device privacy claim.

### 4.11.2 User Stories
- As a **user on the move**, I want to raise my wrist, speak a task, and have it land in my phone's Inbox so that I capture it hands-free.
- As a **glanceable-info user**, I want a watch tile showing my next-due item so that I see what is coming without pulling out my phone.

### 4.11.3 Functional Behavior

| Component | Behavior |
|---|---|
| **MainActivity (WearApp)** | Tap → system speech recognizer → raw text sent via `MessageClient` to `WearContract.PATH_CAPTURE`. Targets a **single** phone node advertising `CAPABILITY_PHONE_CAPTURE` (nearby preferred), so a capture lands exactly once and never on a node lacking the app. Status states: IDLE / SENDING / SENT / NO_PHONE / FAILED. |
| **NextDueTileService** | Reads the phone-published `PATH_NEXT_DUE` DataItem (`title` + `when`) and renders it; shows "All clear / Nothing due" when absent. Freshness interval 20 min; the phone owns all due logic. |
| **WearContract** | Duplicated phone↔watch constants (paths, capability, DataMap keys) that must stay identical to the phone copy. |

- **Errors:** no reachable app-bearing phone → "Phone not reachable" (does **not** claim it was added); send failure → "Couldn't send — try again"; empty/cancelled dictation → back to IDLE.

### 4.11.4 Business Rules
1. **No watch-side model:** extraction always runs on the phone, so the on-device privacy claim holds for wrist captures too.
2. A capture is delivered to exactly one node (single-node selection) to avoid duplicate Inbox entries.
3. Success is reported only on confirmed message delivery to an app-bearing phone, not mere transport reachability.

### 4.11.5 Dependencies
Wear Data Layer (`MessageClient`, `CapabilityClient`, `DataClient`), Wear Compose + ProtoLayout/Tiles; phone-side capture handler and next-due publisher.

### 4.11.6 Example Flow
1. On a walk, the user taps "Speak a task" on the watch, says "pick up dry cleaning tomorrow," and sees "✓ Added to your Inbox."
2. The phone receives the text on `PATH_CAPTURE`, runs it through the pipeline, and publishes the resulting soonest item back to `PATH_NEXT_DUE`; the watch tile updates to "Pick up dry cleaning — Tomorrow · 09:00" within its freshness window.

---

# 5. Detailed System Design

This section decomposes the system into its components (§5.1), details the critical modules and algorithms (§5.2), specifies the consumed and exposed interfaces (§5.3), the data design (§5.4), the external integrations (§5.5), and the end-to-end ingestion and understanding pipeline that is the core of the product (§5.6).

---

## 5.1 Component Architecture

### 5.1.1 Decomposition Principle

TaskMind is a single-process Android application. It has no server tier, so the classic enterprise decomposition into web/app/data tiers behind load balancers does not apply; the equivalent axis here is the **Android package boundary**, which the codebase uses as its component seam. Components are grouped into three layers under the root package `com.rajasudhan.taskmind`:

| Layer | Package(s) | Role |
|---|---|---|
| **Presentation** | `ui.*` | Jetpack Compose screens + ViewModels, launcher/share/quick-capture surfaces, theme system. Renders state, collects user intent. |
| **Domain / Sources** | `data.source` and its sub-packages (`understanding`, `transcription`, `ocr`, `embedding`, `email`, `appfunctions`, `wear`) | The "engine room": ingestion, LLM understanding, extraction, scheduling, background workers, and every OS-facing collector. This is where the app's behaviour lives. |
| **Data** | `data.local`, `data.model` | Room/SQLCipher persistence and the entity types. The single owner of durable at-rest state. |
| **Composition Root** | `di` + `TaskMindApp` | Hilt modules that wire the graph; the `Application` subclass that owns process-wide bootstrap. |

Wiring is by **constructor injection through Hilt**, all in the `SingletonComponent`, so nearly every domain component is a process-lifetime singleton (`@Singleton`). Cross-component contracts are Kotlin interfaces (`LlmProvider`, `Embedder`, `WhisperEngine`, `OnDeviceEngine`) bound in `di.NetworkModule`, which lets the concrete engine be swapped without touching callers.

### 5.1.2 Component Diagram (described)

The diagram below reads top-to-bottom as the data-flow spine (raw OS signal → understanding → pending suggestion → approved Note → surfaces), with the DI/composition root and persistence drawn as cross-cutting substrates that every layer binds to.

```
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │  COMPOSITION ROOT                                                              │
 │  TaskMindApp (Application, Hilt @HiltAndroidApp, Configuration.Provider,       │
 │               AppFunctionConfiguration.Provider)                               │
 │  di.DatabaseModule │ di.NetworkModule │ di.WorkerModule   (all SingletonComp.) │
 └───────────────┬───────────────────────────────────────────────┬──────────────┘
                 │ provides singletons                            │ provides
                 ▼                                                ▼
 ┌───────────────────────────────┐   OS content providers /   ┌──────────────────┐
 │  INGESTION (data.source)      │   listeners / MediaStore    │  CONFIG STORES   │
 │                               │◀───────────────────────────▶│  SettingsManager │
 │  RecentDataScanner  ───────┐  │   SMS / CallLog / Gmail /    │  (EncryptedPrefs)│
 │  SmsObserver               │  │   Notifications / Audio /    │  SourceManager   │
 │  TaskMindNotificationListnr│  │   Images / AppUsage          │  (DataStore)     │
 │  GmailCollector/Auth       │  │                             └──────────────────┘
 │  AppUsageCollector         │  │
 │  Transcriber (Vosk/Whisper)│  │        per-source collectors + dedup ledgers
 │  OcrEngine (Tesseract)     │  │
 └───────────┬────────────────┘  │
             │ text / media                                    ┌──────────────────┐
             ▼                                                 │  understanding   │
 ┌───────────────────────────────┐   generate(...)            │  RoutingLlm-     │
 │  UnderstandingPipeline        │──────────────────────────▶ │  Provider        │
 │  (extract → score → dedup →   │                            │   ├ OnDeviceLlm   │
 │   insert pending Suggestion)  │◀────────────────────────── │   │  (MediaPipe/  │
 │  ExtractionHeuristics (pure)  │   JSON items               │   │   LiteRT/Nano) │
 └───────────┬───────────────────┘                            │   └ CloudLlm     │
             │ insertSuggestion()          possibleDuplicate  │      (Gemini)    │
             ▼                             ▲                  └──────────────────┘
 ┌───────────────────────────────┐         │ scores()         ┌──────────────────┐
 │  DATA (data.local)            │         └───────────────── │  embedding       │
 │  TaskMindDatabase (Room +     │                            │  SemanticIndex   │
 │   SQLCipher) → TaskMindDao    │◀─────────────────────────▶ │  HashingEmbedder │
 │  Owns: Note, Suggestion,      │      index()/scores()      │  Vectors         │
 │        NoteEmbedding,         │                            └──────────────────┘
 │        RejectedPattern        │
 └───────────┬───────────────────┘
             │ Flows (pending / notes)          approve()
             ▼                              ┌────────────────────────────────────┐
 ┌───────────────────────────────┐          │  SuggestionApprover  →  Note        │
 │  PRESENTATION (ui.*)          │          │   ├ AlarmScheduler (exact alarms)   │
 │  Inbox │ Notes │ NoteDetail │ │◀────────▶│   ├ CalendarMirror (system cal)     │
 │  Ask │ Sources │ Privacy │    │          │   └ GeofenceManager (location)      │
 │  Settings │ Reliability │Knows│          └────────────────────────────────────┘
 │  + ViewModels (hiltViewModel) │
 │  MainActivity (NavHost,       │          ┌────────────────────────────────────┐
 │   biometric AppLock)          │          │  BACKGROUND / OS TRIGGERS          │
 └───────────────────────────────┘          │  WorkManager Workers, AlarmReceiver │
                                             │  BootReceiver, Geofence RX,         │
 Capture surfaces: QuickCaptureActivity,     │  QuickTileService, QuickAddWidget,  │
 ShareTargetActivity, Wear, QS tile, widget  │  TaskMindForegroundService          │
                                             └────────────────────────────────────┘
```

### 5.1.3 Major Components

### Presentation — `ui.*`

| Attribute | Detail |
|---|---|
| **Purpose** | Render app state and collect user actions (approve/edit/reject, ask, toggle sources, adjust settings). |
| **Responsibilities** | One Compose screen + `@HiltViewModel` per feature: `ui.inbox` (pending suggestion triage), `ui.notes` (`NotesScreen`, `NoteDetailScreen`, `Checklist`), `ui.ask` (RAG Q&A), `ui.sources` (per-source opt-in), `ui.settings` (`PrivacyScreen`, `SettingsScreen`, `ReliabilityScreen`, `KnowsScreen`), `ui.guide` (first-run overlay). `ui.capture` holds the non-navigation capture entry points. `ui.common`/`ui.bold`/`ui.theme` provide the design system (cards, motion, the "Bold" dark-editorial kit, Material3 theming). |
| **Key interfaces (in)** | ViewModels inject domain singletons (`SuggestionApprover`, `TaskMindDao`, `SettingsManager`, `SourceManager`, `AskEngine`, `RoutingLlmProvider`). Screens receive DB state as Kotlin `Flow`/`StateFlow`. |
| **Dependencies (out)** | Data layer (DAO Flows), `data.source` orchestration singletons. No component depends on `ui.*` — it is a strict sink. |
| **Data ownership** | None durable. Owns only transient UI state (Compose `remember`, ViewModel `StateFlow`). |
| **Deployment unit** | Composables hosted by **`MainActivity`** (`AppCompatActivity`, `@AndroidEntryPoint`, LAUNCHER) via a Compose `NavHost` (start destination `inbox`; routes `notes`, `notes/{noteId}`, `ask`, `sources`, `settings`, `settings_all`, `reliability`, `privacy_knows`). `MainActivity` also owns the biometric `AppLock` gate and starts `TaskMindForegroundService` once unlocked. |

### Capture surfaces — `ui.capture`

| Attribute | Detail |
|---|---|
| **Purpose** | Zero-navigation ways to add an item from anywhere in the OS. |
| **Deployment unit** | **`QuickCaptureActivity`** (type/voice), **`ShareTargetActivity`** (Android Share sheet `ACTION_SEND`), **`QuickAddWidget`** (`AppWidgetProvider` `BroadcastReceiver`), plus the launcher long-press shortcuts published by `CaptureShortcuts`. All funnel into the same capture path (`CaptureWorker` → `UnderstandingPipeline.processText(..., seedSchedule=true)`), so a captured item lands as a pending Suggestion like any other. |

### Ingestion & orchestration — `data.source` (top level)

| Attribute | Detail |
|---|---|
| **Purpose** | Watch the user's own signals and drive each into the understanding pipeline; own scheduling, notifications, backup, and reliability. |
| **Responsibilities** | Batch scan (`RecentDataScanner`), live capture (`SmsObserver`, `TaskMindNotificationListener`, foreground-service media observers), per-item scheduling (`AlarmScheduler`/`AlarmReceiver`, `GeofenceManager`, `CalendarMirror`), approve transition (`SuggestionApprover`), review notifications (`SuggestionNotifier`, `NotificationActionReceiver`, `ReminderActionReceiver`), on-device learning (`RejectionLearner`), backup/restore (`BackupManager`, `BackupCrypto`, `AutoSnapshotWorker`), self-test (`ReliabilityChecker`), audit (`EgressLogger`), and the digest/brief/recap/recurrence workers. |
| **Key interfaces (in)** | Injected almost everywhere (UI, workers, receivers, services). |
| **Dependencies (out)** | `understanding` (extraction), `embedding` (dedup/index), `data.local` (persist), `email`/`ocr`/`transcription` (source-specific collectors), Android framework (`ContentResolver`, `AlarmManager`, `NotificationManager`, `MediaStore`). |
| **Data ownership** | Split across `SettingsManager` (secrets/config in `EncryptedSharedPreferences` `secret_shared_prefs`, incl. the SQLCipher `db_key`, scan watermarks) and `SourceManager` (toggles, enabled-at stamps, processed-id ledgers in DataStore `source_settings`). Durable domain rows are delegated to `data.local`. |
| **Deployment unit** | A mix: Hilt singletons (scanner, approver, schedulers), `CoroutineWorker`s, `BroadcastReceiver`s, a `NotificationListenerService`, a foreground `Service`, a `TileService`. Enumerated in §5.1.4. |

### Understanding — `data.source.understanding`

| Attribute | Detail |
|---|---|
| **Purpose** | Turn raw text/media into structured, scored action items via an LLM, and answer "Ask" queries. |
| **Responsibilities** | Prompt assembly + parsing (`UnderstandingPipeline`, `SystemPrompt`, `LlmModels`), engine routing (`RoutingLlmProvider` → `OnDeviceLlmProvider`{`MediaPipeEngine`, `LiteRtLmEngine`, `NanoEngine`} / `CloudLlmProvider`), pure filtering (`ExtractionHeuristics`, `NearDuplicate`), and the Ask stack (`AskEngine`, `AskPrompt`, `AskQuery`, `MagicBreakdown`, `SuggestionEditor`). |
| **Key interfaces** | `LlmProvider` (in: `generate`/`generateList`/`generateIntent`/`generateFromMedia`/`supportsVision`) bound to `RoutingLlmProvider` in `NetworkModule`. `MediaInput` carries a `content://` URI + MIME type for the multimodal seam (#211, currently dark). |
| **Dependencies (out)** | `SettingsManager` (route/key), on-device runtimes (MediaPipe/LiteRT/AICore), cloud Gemini over Retrofit/OkHttp, `embedding.SemanticIndex` (dedup), `data.local` (insert pending), `SuggestionNotifier`. |
| **Data ownership** | None durable; produces pending `Suggestion` rows owned by `data.local`. |
| **Deployment unit** | Hilt singletons; no OS component of its own. |

### Semantic index — `data.source.embedding`

| Attribute | Detail |
|---|---|
| **Purpose** | The meaning layer: near-duplicate detection at capture and relevance ranking for Ask. |
| **Responsibilities** | `SemanticIndex` embeds a note's title+summary via `Embedder`, stores the vector, and computes cosine scores. `HashingEmbedder` (bound to `Embedder`) is a deterministic on-device hashing embedder; `Vectors` handles byte<->float packing + cosine. |
| **Key interfaces** | `Embedder.embed(text): FloatArray?`. |
| **Data ownership** | Vectors persisted as `NoteEmbedding` rows in `data.local` (owns the derivation, not the storage). |
| **Deployment unit** | Hilt singletons. |

### Source collectors — `email`, `ocr`, `transcription`

| Attribute | Detail |
|---|---|
| **Purpose** | Convert one specific signal into plain text for the pipeline. |
| **Responsibilities** | `email`: `GmailAuth` (OAuth `gmail.readonly` via GoogleAuthUtil, token masking), `GmailApi` (Retrofit), `GmailCollector` (fetch recent Primary, `Unauthorized` 401 handling), `GmailTextExtractor`. `ocr`: `OcrEngine` (ML Kit GenAI + Tesseract). `transcription`: `Transcriber` orchestrating `VoskTranscriber` (first pass) + optional native `WhisperTranscriber`/`WhisperEngine` (second pass, #207), with `AudioDecoder`/`AudioRecorder`/`TranscriptDiff`. |
| **Key interfaces** | `WhisperEngine` bound to `NativeWhisperEngine` in `NetworkModule` (a graceful no-op until the JNI engine is linked). `GmailApi` provided by Retrofit in `NetworkModule` (`https://gmail.googleapis.com/gmail/v1/`). |
| **Data ownership** | None durable; `transcription`/`ocr` model presence is filesystem state, Gmail tokens are held by Play Services (never persisted — see `SettingsManager` comment). |
| **Deployment unit** | Hilt singletons; invoked by `RecentDataScanner` and the foreground service. |

### System-agent bridge — `data.source.appfunctions`

| Attribute | Detail |
|---|---|
| **Purpose** | Expose `createTask` / `getItemsDueToday` / `snoozeItem` to the system agent (Gemini/Assistant) via `androidx.appfunctions`. |
| **Responsibilities** | `AgentFunctions`/`TaskMindAppFunctions` route an agent call to the real injected data layer (`UnderstandingPipeline.captureFromAgent(...)` for creates, so an agent-created item is still a pending Suggestion, never a silent Note). |
| **Deployment unit** | Instantiated by the AppFunctions framework via `TaskMindApp.appFunctionConfiguration` (`addEnclosingClassFactory(AgentFunctions::class.java){ agentFunctions }`), so the Hilt-injected instance is used instead of a no-arg constructor. |

### Wear companion bridge — `data.source.wear`

| Attribute | Detail |
|---|---|
| **Purpose** | Receive voice captures from the paired Galaxy Watch and keep its "next due" tile fresh. |
| **Responsibilities** | `WearCaptureListenerService` (`WearableListenerService`, `MESSAGE_RECEIVED`) ingests a watch capture; `WearSync`/`WearSyncScheduler`/`WearSyncWorker` replicate the due set as a DataItem; `WearContract` is the shared message/path contract. |
| **Deployment unit** | `WearCaptureListenerService` (Service) + `WearSyncWorker` (WorkManager). Scheduling armed from `TaskMindApp.onCreate` (`schedule()` + `observeDueChanges()`). |

### Persistence — `data.local` + `data.model`

| Attribute | Detail |
|---|---|
| **Purpose** | The one durable store of user content, encrypted at rest. |
| **Responsibilities** | `TaskMindDatabase` (Room, `@Database` over `Note`, `Suggestion`, `RejectedPattern`, `NoteEmbedding`; `SCHEMA_VERSION = 18` in code — the canonical brief's "v5" refers to the app versionName, not the DB schema — with explicit migrations `MIGRATION_1_2 … MIGRATION_17_18`), `TaskMindDao` (all reads exposed as `Flow`), `DatabaseRecovery` (quarantine-on-unopenable, never delete). `data.model` holds the entities plus `SavedFilter` and the `Tags` taxonomy. |
| **Key interfaces** | `TaskMindDao` (in: insert/update/query suggestions & notes, embeddings, rejected patterns). |
| **Data ownership** | **Sole owner** of `Note`, `Suggestion`, `NoteEmbedding`, `RejectedPattern`. Encryption via `net.zetetic:sqlcipher-android` (`SupportOpenHelperFactory`), key from `provideDatabaseKey` (32 random bytes in `EncryptedSharedPreferences`, Keystore-backed). |
| **Deployment unit** | Hilt-provided singletons from `DatabaseModule`. |

### Composition root — `di` + `TaskMindApp`

| Attribute | Detail |
|---|---|
| **Purpose** | Assemble the object graph and own process-wide bootstrap. |
| **Responsibilities** | `DatabaseModule` (EncryptedSharedPreferences with Keystore-reset recovery, DB key, SQLCipher DB with pending-restore-key promotion, DAO). `NetworkModule` (binds `LlmProvider`→`RoutingLlmProvider`, `Embedder`→`HashingEmbedder`, `WhisperEngine`→`NativeWhisperEngine`; provides Moshi, `OkHttpClient`, `GmailApi`). `WorkerModule` (provides `WorkManager`). `TaskMindApp` supplies the `HiltWorkerFactory` (so `@HiltWorker`s get injected deps), the AppFunctions factory, eager notification-channel creation, and initial scheduling of every periodic worker. |
| **Deployment unit** | `TaskMindApp` (`Application`); the modules are compile-time Hilt code. |

### 5.1.4 Deployment Units (OS-visible components)

Because there is no server, "deployment units" are the Android manifest components and Hilt-managed process objects. From `AndroidManifest.xml`:

| Kind | Component(s) | Trigger / binding |
|---|---|---|
| **Activity** | `MainActivity` (LAUNCHER + `open_note_id`/`open_inbox` deep links), `ShareTargetActivity` (`ACTION_SEND`), `QuickCaptureActivity` | User launch / share / shortcut |
| **NotificationListenerService** | `TaskMindNotificationListener` | `BIND_NOTIFICATION_LISTENER_SERVICE`; live notification capture + boot/reconnect sweep |
| **Foreground Service** | `TaskMindForegroundService` | `FOREGROUND_SERVICE_DATA_SYNC`; live media (recording/screenshot) observers, started once unlocked |
| **TileService** | `QuickTileService` | `BIND_QUICK_SETTINGS_TILE`; QS-tile quick capture |
| **WearableListenerService** | `wear.WearCaptureListenerService` | `MESSAGE_RECEIVED`; watch capture |
| **BroadcastReceivers** | `AlarmReceiver` (reminder fire + recurrence re-arm), `BootReceiver` (`BOOT_COMPLETED`/`MY_PACKAGE_REPLACED`/`TIME(ZONE)_*` → re-arm alarms), `NotificationActionReceiver`, `ReminderActionReceiver`, `WaitingConfirmReceiver`, `GeofenceBroadcastReceiver`, `ReliabilityTestReceiver`, `QuickAddWidget` (`AppWidgetProvider`) | System/exact-alarm/notification-action broadcasts |
| **WorkManager Workers** (`@HiltWorker` `CoroutineWorker`) | `DataCollectionWorker` (periodic scan, default 30 min, `PERIODIC_WORK_NAME="taskmind_periodic_scan"`, `RequiresBatteryNotLow`), `CaptureWorker`, `DailyBriefWorker`, `WeeklyWinsWorker`, `AutoSnapshotWorker`, `RecurrenceDetectorWorker`, `wear.WearSyncWorker` | WorkManager (config from `TaskMindApp.workManagerConfiguration`) |
| **Hilt singletons** (`SingletonComponent`) | `SettingsManager`, `SourceManager`, `RecentDataScanner`, `UnderstandingPipeline`, `RoutingLlmProvider`, `SemanticIndex`, `AlarmScheduler`, `SuggestionApprover`, `GmailAuth`/`GmailCollector`, `Transcriber`, `OcrEngine`, `TaskMindDatabase`/`TaskMindDao`, and the `*Scheduler`s | Constructor injection, process-lifetime |

Cross-cutting substrates every unit binds to: **DI graph** (Hilt `SingletonComponent`), **config stores** (`SettingsManager` EncryptedSharedPreferences / `SourceManager` DataStore), and the **encrypted Room DB**. The equivalent of a server's "shared services" is thus in-process singletons, not network endpoints.

---

## 5.2 Module / Class-Level Design

This section details the five load-bearing modules on the ingest→understand→persist→schedule spine.

### 5.2.1 `RecentDataScanner` (`data.source`)

**Role.** The batch ingestion coordinator. It is the single shared code path behind both the manual Inbox refresh and the periodic `DataCollectionWorker`, scanning every enabled source since a watermark and feeding each item to `UnderstandingPipeline`. `@Singleton`, constructor-injected.

**Key operations.**

```kotlin
suspend fun scanIncremental()               // since lastScanAt, capped/first-run guarded, then advances watermark
suspend fun scanSince(sinceMillis: Long)    // fan-out to per-source scans, each gated on its toggle
suspend fun scanAudioRecent()               // live entry point (last 5 min) — foreground media observer
suspend fun scanImagesRecent()
```

**Design points grounded in the code:**

- **Incremental, forward-only windows.** `scanIncremental()` computes `since` from `SettingsManager.lastScanAt`: first-ever run looks back only `FIRST_RUN_LOOKBACK_MS` (15 min) to avoid an initial flood; otherwise `maxOf(last, now - MAX_LOOKBACK_MS)` caps a long-dormant gap at 24 h. Each source's window is further clamped to its `SourceManager.*EnabledAt` stamp, so enabling a source captures only what arrives *after* opt-in — never backfilling history.
- **Watermark-after-scan.** `lastScanAt` is advanced only *after* `scanSince`, and each source scan is wrapped in `runCatching`, so one source failing does not drop the others and, at worst, causes a re-scan rather than data loss.
- **Per-source dedup ledgers.** SMS/email/audio/image scans skip ids already in the `SourceManager` processed-id sets, so an item is never re-run through the LLM. SMS additionally has a **gap-recovery** pass (`recoverMissedSmsById`) that walks rows above a contiguous `_ID` watermark (`lastProcessedSmsId`), oldest-first, capped at `MAX_SMS_RECOVERY_PER_SCAN=200`, to catch messages older than the 24 h date clamp. The **call log has no ledger**, so per-row failures are swallowed to avoid losing unscanned rows when the watermark advances.
- **Missed-call special case.** A `MISSED_TYPE` row bypasses the LLM via `pipeline.addCallback(cachedName, number)` (the model tends to drop bare missed calls as non-actionable).
- **Multimodal seam.** Audio/image scans call `pipeline.processMedia(...)` first; it returns `false` today (no vision engine), so they fall back to Vosk transcription / Tesseract OCR then `pipeline.processText(...)`.

**Collaborators (out):** `SourceManager`, `SettingsManager`, `UnderstandingPipeline`, `GmailAuth`/`GmailCollector`, `AppUsageCollector`, `Transcriber`, `OcrEngine`, `PersonContextNotifier`, plus `ContentResolver`/`MediaStore`/`Telephony`/`CallLog`. **Data ownership:** none durable — it reads watermarks/ledgers from the config stores and writes pending suggestions through the pipeline. **Deployment:** `@Singleton`, driven by `DataCollectionWorker.doWork()` and the foreground media observers.

### 5.2.2 `UnderstandingPipeline` (`data.source.understanding`)

**Role.** The extraction core. Converts one unit of source text (or media) into zero-or-more scored, deduplicated **pending** `Suggestion` rows. It never writes a `Note` — approval is a separate step. `@Singleton`.

**Key operations.**

```kotlin
suspend fun processText(source, text, seedSchedule=false)        // main path
suspend fun processMedia(source, media: MediaInput): Boolean     // multimodal seam (#211, dark)
suspend fun addCallback(displayName?, number?, source="Missed call")  // deterministic, LLM-bypassing
suspend fun captureFromAgent(title, notes, dueDate?, dueTime?, type, source) // AppFunctions entry
fun supportsMediaCapture(): Boolean
```

**Design points grounded in the code:**

- **Cheap pre-filter before spend.** `ExtractionHeuristics.isLikelyNoise(text)` short-circuits OTPs/promos/opt-outs before any LLM call.
- **Prompt shape + budget.** A cacheable static `SystemPrompt.INSTRUCTION` (with `{{CURRENT_DATETIME}}` substituted) plus a user message carrying the source label and body, truncated to `MAX_INPUT_CHARS=4000` to stay under the on-device 2048-token KV cache.
- **Parse with retry and salvage.** `tryParse` strips code fences and adapts `LlmResponse` via Moshi; on failure `salvageItems` recovers well-formed elements individually so one bad item doesn't discard a good multi-item extraction. A null parse triggers one stronger-nudge retry.
- **Deterministic date override.** For user-typed captures (`seedSchedule=true`), `NaturalDate.parse` runs once; a parsed date **overrides** the LLM's shaky relative-date math, gated to single-item extractions so a parsed date isn't smeared across a brain-dump. If the extractor yields nothing but a clear date was typed, a `fallbackItem` reminder is synthesized at `FALLBACK_CONFIDENCE=0.9`.
- **Concurrency correctness.** A private `Mutex` (`insertMutex`) serializes the **check-then-insert** region (`isDuplicate` → `dao.insertSuggestion`) so the live `SmsObserver` and the periodic scanner can't both pass dedup and insert twins for the same SMS; the **slow LLM call stays outside the lock**. Duplicate keying uses the *effective* stored date (seeded parse or sanitized LLM date), preventing an item from re-inserting its own twin.
- **Scoring + safe near-dedup.** `RejectionLearner.confidencePenalty(source)` down-ranks senders the user keeps rejecting; `ExtractionHeuristics.isAcceptable` gates insertion. `possibleDuplicateOf` (via `SemanticIndex` + `NearDuplicate`) flags a near-duplicate for review but **never drops** it (#145).
- **Non-LLM entry points.** `addCallback` and `captureFromAgent` build suggestions deterministically (same dedup + notify path) so an agent- or call-log-sourced item still flows through the user's Inbox approval, never a silent Note.

**Collaborators (out):** `LlmProvider` (routing), `Moshi`, `TaskMindDao`, `SuggestionNotifier`, `RejectionLearner`, `SemanticIndex`, plus pure helpers `ExtractionHeuristics`/`NearDuplicate`/`NaturalDate`. **Data ownership:** produces pending `Suggestion` rows (owned by `data.local`). **Deployment:** `@Singleton`.

### 5.2.3 `RoutingLlmProvider` (`data.source.understanding`)

**Role.** The strategy selector implementing `LlmProvider`, bound in `NetworkModule`. It chooses per request between the on-device engine and the cloud engine, and is the single source of truth for the "honest engine label" (#197). `@Singleton`.

**Key operations.**

```kotlin
override suspend fun generate/generateList/generateIntent(system, user): String
override fun supportsVision(): Boolean
override suspend fun generateFromMedia(system, user, media): String?
fun isOnDeviceEffective(): Boolean        // text-path honest label
fun mediaEgressesToCloud(): Boolean       // media-path honest label
```

**Routing policy grounded in the code:**

- **On-device by default, cloud on explicit choice.** `SettingsManager.useOnDeviceLlm` defaults `true`. When on-device is selected, `generate` calls `OnDeviceLlmProvider`; on exception (Nano/Gemma unavailable) it falls back to cloud **only if `llmApiKey` is non-blank**, else returns a safe empty payload (`{"items": []}` / `[]` / `{"action":"query"}`) so callers degrade gracefully rather than crash. When cloud is selected, it goes straight to `CloudLlmProvider`.
- **Honest labels, not hardcoded claims.** `isOnDeviceEffective()` is true only when on-device is selected **and** either the model is present (runs locally) **or** there's no cloud key to fall back to (nothing leaves the phone). This is what the UI reads instead of asserting "on-device".
- **Pure, testable vision routing.** `supportsVision()`/`generateFromMedia` delegate to the free function `visionRoute(useOnDevice, onDeviceVision, cloudVision, hasCloudKey)` returning `ON_DEVICE`/`CLOUD`/`NONE`. Because `CloudLlmProvider.supportsVision()` returns `true`, this resolves to `CLOUD` whenever a cloud key is set (cloud selected, or on-device selected with a key), so an image capture egresses to Gemini 2.5 Flash vision; only the on-device vision path is still dark (`onDeviceVision=false`). `mediaEgressesToCloud()` exists because media currently has no on-device model — so an image/audio capture can egress to cloud even while text extraction is on-device — and callers that label the *extraction* engine (Inbox) must use it to avoid mislabelling.

**Collaborators (out):** `OnDeviceLlmProvider` (which itself dispatches to `MediaPipeEngine`/`LiteRtLmEngine`/`NanoEngine` per `SettingsManager.onDeviceEngine`, with MediaPipe as the migration safety-net fallback), `CloudLlmProvider` (Gemini over Retrofit/OkHttp), `SettingsManager`. **Data ownership:** none. **Deployment:** `@Singleton`, bound to the `LlmProvider` interface so all callers are engine-agnostic.

### 5.2.4 `SemanticIndex` (`data.source.embedding`)

**Role.** The app's semantic layer, answering the two questions that need meaning over keywords: "is this capture a near-duplicate?" (dedup) and "which notes are relevant to this query?" (Ask retrieval). `@Singleton`.

**Key operations.**

```kotlin
fun textFor(title, summary?): String                 // canonical embed text: "title. summary"
suspend fun index(noteId, title, summary?)           // embed + upsert vector; no-op if unembeddable
suspend fun backfill()                                // embed notes missing a vector
suspend fun scores(query, floor): Map<Int, Float>     // noteId → cosine ≥ floor
// companion: SEARCH_FLOOR = 0.35f
```

**Design points grounded in the code:**

- **Derivation, not storage.** It computes vectors via `Embedder` (bound to `HashingEmbedder`) and persists them as `NoteEmbedding(noteId, Vectors.toBytes(vec))` through `TaskMindDao.upsertEmbedding`; the rows are owned by `data.local`.
- **Cosine ranking.** `scores()` embeds the query, iterates `dao.getAllEmbeddings()`, and returns `noteId → cosine` for those at/above `floor`. Returns empty when the query can't be embedded or nothing is indexed — a safe degrade.
- **Two consumers.** `UnderstandingPipeline.possibleDuplicateOf` uses `scores(candidate, SEARCH_FLOOR)` as a semantic pre-filter that `NearDuplicate` then lexically confirms; `AskEngine` uses it for retrieval-augmented answers. Because `HashingEmbedder` is deterministic and fast, the dedup lookup is cheap enough to run under the pipeline's `insertMutex` (the code notes a slower neural embedder should move outside the lock).

**Collaborators (out):** `Embedder`, `TaskMindDao`, `Vectors`. **Data ownership:** derives `NoteEmbedding` (stored in the encrypted DB). **Deployment:** `@Singleton`; `backfill()` is a catch-up for notes saved before embedding existed.

### 5.2.5 `AlarmScheduler` (`data.source`)

**Role.** The exact-alarm authority for reminders. Keyed by note id so each note has exactly one main alarm that can be rescheduled (for recurrence) or cancelled, plus a separate snooze/nag namespace. `@Singleton`.

**Key operations.**

```kotlin
fun schedule(noteId, title, dueDate?, dueTime?, recurrence?, anchorDay?=null): String?  // returns armed date
fun cancel(noteId)                                   // main alarm + any re-fire
fun cancelRefire(noteId)                             // only the snooze/nag re-fire
fun snoozeReminder(noteId, title, minutes=60, nagCount=0)
```

**Design points grounded in the code:**

- **Recurrence-safe re-arm.** For a recurring reminder whose stored slot is already past, `schedule` advances to the next future occurrence via `RecurrenceUtil.firstFutureOccurrence(...)`, so an edit/reschedule/boot re-arm can pass a stale date without silently dropping the repeat. It returns the *actually armed* date so callers with DB access can persist it back in step. `anchorDay` preserves a monthly reminder's intended day-of-month (e.g. the 31st) instead of drifting to the 28th through short months (#177).
- **Never fire in the past.** Times parsed tolerantly (`RecurrenceUtil.parseTime`, accepting single-digit hours); a slot `<= now` returns null and arms nothing.
- **Exact with graceful degradation.** Uses `setExactAndAllowWhileIdle(RTC_WAKEUP, …)` when `canScheduleExactAlarms()`; otherwise falls back to `setAndAllowWhileIdle` (Doze-safe, may fire in a maintenance window) rather than dropping the reminder — the app holds `USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM`.
- **PendingIntent namespacing.** The main alarm uses `requestCode = noteId` (so reschedule/cancel target the same alarm; extras aren't part of matching); snooze/nag re-fires live in a separate `SNOOZE_RC_BASE = 7_000_000 + noteId` namespace so a snooze never replaces a recurring reminder's already-scheduled next occurrence, and carries no recurrence extras so it only re-notifies. `(Re)establishing the main alarm calls `cancelRefire` first to invalidate a stale nag loop.

**Collaborators (out):** Android `AlarmManager`, `RecurrenceUtil`, and `AlarmReceiver` (the `BroadcastReceiver` that fires the notification and re-arms recurring/nag alarms). It is invoked by `SuggestionApprover.approve` (arm on approval), `AlarmReceiver` (re-arm), `BootReceiver` (re-arm after reboot/time change), and the NoteDetail "Repeat" control. **Data ownership:** none — alarms are OS-side state keyed by note id; the durable `dueDate`/`recurrence` live on the `Note` row. **Deployment:** `@Singleton` collaborating with the manifest-declared `AlarmReceiver`/`BootReceiver`.

---

**Note on schema version:** the code's single source of truth is `TaskMindDatabase.SCHEMA_VERSION = 18` (migrations `MIGRATION_1_2 … MIGRATION_17_18`); the "v5" in the project brief tracks the app `versionName`, not the Room schema. Section text uses the code value.

Relevant source files (absolute paths): `D:\SMK\Android_apps\apps\taskmind\src\main\java\com\rajasudhan\taskmind\di\{DatabaseModule,NetworkModule,WorkerModule}.kt`, `TaskMindApp.kt`, `MainActivity.kt`, `data\source\RecentDataScanner.kt`, `data\source\understanding\{UnderstandingPipeline,RoutingLlmProvider,LlmProvider,OnDeviceLlmProvider}.kt`, `data\source\embedding\SemanticIndex.kt`, `data\source\AlarmScheduler.kt`, `data\source\{SettingsManager,SourceManager,SuggestionApprover,DataCollectionWorker}.kt`, `data\local\TaskMindDatabase.kt`, and `src\main\AndroidManifest.xml`.

---

## 5.3 API Design

### 5.3.1 Scope and design stance

TaskMind is a **privacy-first, on-device assistant with no first-party backend**. Consequently there are **no first-party HTTP endpoints, no REST resources, no server-side controllers, no OpenAPI surface, and no API gateway** to design. The classic enterprise "API layer" (public/partner/internal REST or gRPC contracts, versioned URIs, rate-limit tiers, API keys issued to callers) does not apply. The on-device equivalent, and the subject of this section, is a **two-directional interface contract**:

| Direction | Meaning | Surfaces |
|---|---|---|
| **Consumed** (outbound) | Third-party APIs the app *calls* to obtain LLM inference, mail, auth tokens, and geocoding. These are the *only* network egress paths. | Gmail REST API, Google Gemini `generateContent`, Google OAuth (`GoogleAuthUtil`), Google Maps directions URL, on-device `Geocoder` |
| **Exposed** (inbound) | Interfaces the app *publishes* to the operating system and paired devices, invoked by the OS agent or the watch — not over IP. | Android **AppFunctions** (`createTask` / `getItemsDueToday` / `snoozeItem`), **Wear Data Layer** (MessageClient / DataClient paths) |

Cross-cutting design rules enforced across every surface:

- **Auditable egress.** Every outbound call is recorded through `EgressLogger.record(host, purpose)` *before* the request is sent — host and purpose only, never content. This drives the in-app "Data Egress" audit screen and is the app's substitute for server-side access logging.
- **Schema-pinned responses.** Where a model reply is parsed, the request pins an exact response schema (Gemini `responseSchema` + `responseMimeType: application/json`) rather than trusting free-form text.
- **Least privilege / read-only.** The Gmail scope is `gmail.readonly`; the Gmail Retrofit interface exposes only `GET` operations. Tokens are short-lived and never persisted.
- **On-demand, per-source consent.** Tokens are fetched at call time; each data source is independently opt-in.

### 5.3.2 Consumed API — Gmail REST API

**Client:** `GmailApi` (Retrofit 2 interface, Moshi converter). **Base URL:** `https://gmail.googleapis.com/gmail/v1/` (wired in `di/NetworkModule.provideGmailApi`). **Transport:** shared `OkHttpClient`. **Auth:** per-request `Authorization: Bearer <token>` header, token from `GmailAuth` (see §5.3.4). **Scope:** `https://www.googleapis.com/auth/gmail.readonly` (a *restricted* scope). **Consumer:** `GmailCollector`.

| Operation | HTTP | Path (relative to base) | Query / Path params |
|---|---|---|---|
| List messages | `GET` | `users/me/messages` | `q`, `maxResults`, `pageToken?` |
| Get message | `GET` | `users/me/messages/{id}` | `format=full` |
| Get profile | `GET` | `users/me/profile` | — |

**Collector behaviour (`GmailCollector.fetchRecentPrimary`).** The query is built as `category:primary after:<epochSeconds>` (read *and* unread Primary mail; deduplication is the caller's processed-id ledger, `skipIds`). It pages via `nextPageToken` until the window is exhausted, bounded by safety caps `MAX_MESSAGES_PER_SCAN = 100`, `MAX_PAGES = 25`, and a default `maxResults = 20` per page. Each returned `GmailMessage` is reduced to an `Email(id, sender, subject, body)`; sender/subject come from MIME headers (`From`/`Subject`) and the body from the decoded (base64url) text part, falling back to `snippet`, with any parsed `.ics` calendar summary prepended. Only the API request is egress; message content is processed on-device.

**Response DTOs (`GmailModels.kt`, Moshi `@JsonClass`).** `GmailMessageList{messages:[GmailRef], resultSizeEstimate, nextPageToken?}`; `GmailRef{id, threadId}`; `GmailMessage{id, threadId, snippet, payload}`; `GmailPayload{mimeType, headers:[GmailHeader], body:GmailBody?, parts:[GmailPayload]}` (recursive MIME tree); `GmailHeader{name, value}`; `GmailBody{size, data /*base64url*/}`; `GmailProfile{emailAddress}`.

**Concrete example — list then fetch.**

Request (list):
```
GET https://gmail.googleapis.com/gmail/v1/users/me/messages?q=category:primary%20after:1720483200&maxResults=20 HTTP/1.1
Authorization: Bearer ya29.a0AfB_byC...redacted
```
Response (200):
```json
{
  "messages": [
    { "id": "18f0c3a9b2d4e6f1", "threadId": "18f0c3a9b2d4e6f1" },
    { "id": "18f0c2ff01aa77bc", "threadId": "18f0c2ff01aa77bc" }
  ],
  "resultSizeEstimate": 2,
  "nextPageToken": null
}
```
Request (fetch one):
```
GET https://gmail.googleapis.com/gmail/v1/users/me/messages/18f0c3a9b2d4e6f1?format=full HTTP/1.1
Authorization: Bearer ya29.a0AfB_byC...redacted
```
Response (200, trimmed to the fields TaskMind reads):
```json
{
  "id": "18f0c3a9b2d4e6f1",
  "threadId": "18f0c3a9b2d4e6f1",
  "snippet": "Reminder: your dentist appointment is Thursday at 3pm",
  "payload": {
    "mimeType": "multipart/alternative",
    "headers": [
      { "name": "From", "value": "Dr. Lee Dental <office@leedental.example>" },
      { "name": "Subject", "value": "Appointment reminder" }
    ],
    "parts": [
      { "mimeType": "text/plain",
        "body": { "size": 74, "data": "UmVtaW5kZXI6IHlvdXIgZGVudGlzdCBhcHBvaW50bWVudCBpcyBUaHVyc2RheSBhdCAzcG0" } }
    ]
  }
}
```
The `body.data` string is **base64url**; the collector decodes it to the plain-text body handed to the understanding pipeline.

**Error handling.** `GmailCollector.apiCall` wraps each call: an HTTP **401** (token accepted by Play Services but rejected by Gmail — stale/revoked) is re-thrown as `GmailCollector.Unauthorized` so the caller can `GmailAuth.invalidate(token)`, re-authorize, and retry once; any other `HttpException`/exception is logged and returns `null`, so one bad message never aborts the batch.

### 5.3.3 Consumed API — Google Gemini (cloud LLM)

**Client:** `CloudLlmProvider` (implements `LlmProvider`). **Important — no Retrofit here:** unlike Gmail, the Gemini calls are issued with **raw OkHttp + `org.json`** (hand-built `JSONObject` bodies), not a Retrofit interface. **Endpoint:** `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=<API_KEY>`. **Model:** `gemini-2.5-flash` (multimodal — `supportsVision() == true`). **Auth:** the API key is passed as the `?key=` query parameter (Google Generative Language "express"/API-key auth), read from `SettingsManager.llmApiKey` (EncryptedSharedPreferences). **Egress host logged:** `generativelanguage.googleapis.com`.

Four call shapes, each pinning a different response schema so the reply cannot drift into the wrong structure:

| `LlmProvider` method | Purpose (egress label) | `responseSchema` | Empty/fallback |
|---|---|---|---|
| `generate` | "Cloud LLM extraction" | `{items:[…]}` object (item `type` ∈ reminder/todo/note/waiting_on; nullable date/time/location/recurrence; bounded `tags` enum; `priority` enum; `confidence` number) | `{"items": []}` |
| `generateList` | "Cloud LLM task breakdown" | bare `ARRAY` of `STRING` (Magic Breakdown steps) | `[]` |
| `generateIntent` | "Cloud LLM ask intent" | flat object, `action` ∈ query/create + nullable slots | `{"action": "query"}` |
| `generateFromMedia` | "Cloud LLM vision extraction" | extraction schema; image sent as base64 `inline_data` part | `null` (fall back to OCR) |

Every request sets `generationConfig.temperature = 0.1`, `responseMimeType = "application/json"`, and `responseSchema`, and carries the prompt as `systemInstruction` + a `user` `contents` turn.

**Concrete example — extraction call.**

Request:
```
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=REDACTED
Content-Type: application/json
```
```json
{
  "systemInstruction": { "parts": { "text": "You extract action items as strict JSON…" } },
  "contents": [
    { "role": "user",
      "parts": [ { "text": "From: office@leedental.example\nAppointment reminder\nReminder: your dentist appointment is Thursday at 3pm" } ] }
  ],
  "generationConfig": {
    "temperature": 0.1,
    "responseMimeType": "application/json",
    "responseSchema": { "type": "OBJECT", "properties": { "items": { "type": "ARRAY", "items": { "...": "item schema" } } }, "required": ["items"] }
  }
}
```
Response (200) — the useful payload is a JSON *string* nested at `candidates[0].content.parts[0].text`:
```json
{
  "candidates": [
    { "content": { "role": "model", "parts": [
        { "text": "{\"items\":[{\"type\":\"reminder\",\"title\":\"Dentist appointment\",\"notes\":\"Dr. Lee Dental\",\"due_date\":\"2026-07-16\",\"due_time\":\"15:00\",\"confidence\":0.92}]}" }
    ] } }
  ]
}
```
The provider navigates that path defensively with `opt*` accessors (`candidates → content → parts → [0] → text`); an empty `candidates` (safety block) or a `parts`-less candidate (MAX_TOKENS truncation) yields the schema-shaped fallback instead of throwing.

**Vision variant.** `generateFromMedia` (built by the pure helper `buildVisionRequestBody`) puts the screenshot first as `{"inline_data":{"mime_type":…,"data":<base64>}}` then the instruction text, sharing the extraction schema. It returns `null` (not empty-items JSON) on a non-image MIME, blank key, unreadable image, or non-2xx reply so `RecentDataScanner` falls back to the Tesseract OCR path.

**Auth / routing note.** `RoutingLlmProvider` decides on-device vs. cloud per request. Cloud is only reached when it is the selected backend, or as a fallback when on-device is selected but unavailable *and* `llmApiKey` is non-blank; a blank key short-circuits to the schema-shaped empty result with **no** network call.

### 5.3.4 Consumed API — Google OAuth via `GoogleAuthUtil`

**Client:** `GmailAuth`. TaskMind deliberately uses the legacy **`GoogleAuthUtil`** token API (not the newer `Identity.getAuthorizationClient().authorize()`, which returned a persistent `INTERNAL_ERROR`/status 8 for the restricted scope on this project). It is **per-account** and matches the app by package name + signing SHA-1 against an **Android OAuth client** in Google Cloud (`com.rajasudhan.taskmind`, SHA-1 `CB:5D:68:…:A3:F4` of the committed debug keystore) — no client-id is embedded.

| Operation | Call | Result |
|---|---|---|
| Acquire token | `GoogleAuthUtil.getToken(ctx, Account(email,"com.google"), "oauth2:https://www.googleapis.com/auth/gmail.readonly")` | `GmailAuthState.Authorized(token)` / `NeedsConsent(intent)` / `Error(msg)` |
| Invalidate (after Gmail 401) | `GoogleAuthUtil.clearToken(ctx, token)` | clears the Play-Services-cached token so the next `authorize` re-fetches |
| Revoke (disconnect) | `clearToken` + `POST https://oauth2.googleapis.com/revoke?token=<token>` | server-side revocation |
| Account chooser | `AccountManager.newChooseAccountIntent(...)` | user picks the mailbox (supports multiple) |

**State model.** `authorize()` returns `Authorized` when a silent grant exists (works in background scans via `silentAccessToken`), `NeedsConsent` with a launchable intent on first use/revoked grant, or `Error`. Hard `GoogleAuthException` failures are classified with a **basic-scope diagnostic probe** (`userinfo.email`) into `OK`/`RECOVERABLE`/`BROKEN`/`UNREACHABLE`, and `authGuidance` turns that into actionable, PII-masked user guidance (e.g. Advanced-Protection / supervised accounts blocking the restricted scope; account removed from device; network unreachable). Egress labels: `oauth2.googleapis.com` for "Gmail access token", "Gmail token invalidate/revoke".

### 5.3.5 Consumed API — Maps directions & on-device geocoding

There is **no Google Maps HTTP/SDK API key** and **no server-side Geocoding API call**. Two distinct mechanisms:

- **Directions (hand-off, not an API call).** `ui/common/ActionIntents.openDirections` builds a Maps Universal URL `https://www.google.com/maps/dir/?api=1&destination=<lat,lng | encoded place name>` and fires an `ACTION_VIEW` intent targeting `com.google.android.apps.maps`, falling back to any handler (browser) if Maps is absent. The same URL is used from `QuickAddWidget` and the suggestion notification (`SuggestionNotifier.directionsIntent`). The dialer uses `ACTION_DIAL` with `tel:` — no call is placed automatically.
- **Geocoding (fully on-device).** `PlaceGeocoder.geocode` uses the platform `android.location.Geocoder` (`getFromLocationName`, async `GeocodeListener`) — **no network API, no key**. It biases results to a ~55 km bounding box around the fused last-known location so a bare name ("Panda Express") resolves to the nearby venue, and returns `null` when unresolved (callers fall back to a Maps name search). Location bias is gated on `ACCESS_FINE/COARSE_LOCATION`.

### 5.3.6 Exposed interface — Android AppFunctions (OS agent surface)

TaskMind publishes three functions to the system agent (Gemini / Assistant) via `androidx.appfunctions`. `AgentFunctions` is the thin `@AppFunction(isDescribedByKDoc = true)` binding (each function's KDoc becomes the agent-facing description); the tested behaviour lives in `TaskMindAppFunctions`, running over the real Room data layer. Requests/responses are `@AppFunctionSerializable` DTOs (`AppFunctionModels.kt`), so the compiler encodes their schema (field KDoc included) for the agent to fill and read back. These are invoked in-process by the OS, **not over IP** — there is no network transport, URL, or bearer token.

| Function | Request schema | Response schema | Behaviour |
|---|---|---|---|
| `createTask` | `CreateTaskRequest{title, dueDate?, dueTime?, type?, notes?}` | `AppFunctionResult{success, message}` | Proposes an item into the **Inbox as a PENDING suggestion** via `UnderstandingPipeline.captureFromAgent` (source `"Gemini"`) — the agent proposes, the user still approves. Dedup-aware ("You already have that"). |
| `getItemsDueToday` | *(none)* | `DueTodayResult{items:[DueItem{id,title,dueTime,type}], count}` | Active notes due today, sorted by parsed time-of-day (undated last). Ids are the handle for snoozing. |
| `snoozeItem` | `SnoozeRequest{id, dueDate, dueTime?}` | `AppFunctionResult{success, message}` | Reschedules the item, re-arms its exact alarm (`AlarmScheduler`), re-anchors a monthly recurrence's day-of-month, and updates any mirrored calendar event (`CalendarMirror`). Validates id existence and date format. |

Formats are agent-friendly: `dueDate` = `YYYY-MM-DD`, `dueTime` = 24-hour `HH:MM`, `type` ∈ `todo | reminder | note | waiting_on` (null → `todo`). Registration is via `TaskMindApp`'s `AppFunctionConfiguration.Provider` (Hilt-provided `AgentFunctions`). Device-side verification is done with `cmd app_function list/execute` (the binding cannot be exercised through the Gemini voice preview until GA).

### 5.3.7 Exposed interface — Wear OS Data Layer

The phone ↔ watch contract is the Google **Wearable Data Layer** (`com.google.android.gms.wearable`), addressed by string *paths*, not URLs. Constants live in `WearContract` and are **duplicated** verbatim in the Wear module's `com.rajasudhan.taskmind.wear.WearContract` (separate Gradle modules, no shared module).

| Path / capability | Mechanism | Direction | Payload |
|---|---|---|---|
| `/taskmind/capture` (`PATH_CAPTURE`) | **MessageClient** `sendMessage` | Watch → Phone | Raw spoken-capture text, UTF-8 bytes |
| `taskmind_phone_capture` (`CAPABILITY_PHONE_CAPTURE`) | **CapabilityClient** (advertised in phone `res/values/wear.xml`) | Watch discovers phone | — (reachability filter) |
| `/taskmind/next_due` (`PATH_NEXT_DUE`) | **DataClient** `putDataItem` (`setUrgent`) | Phone → Watch | `DataMap{title:String, when:String, updated_at:Long}` |

**Capture (watch → phone).** The watch does the speech-to-text locally, then `sendMessage(target.id, PATH_CAPTURE, bytes)` to a **single** phone node chosen by `pickCaptureTarget` (nearby node preferred; capability-filtered) so one utterance lands in exactly one Inbox. On the phone, `WearCaptureListenerService` (`WearableListenerService.onMessageReceived`) trims the UTF-8 text and enqueues it through the *same* `CaptureWorker` → `UnderstandingPipeline` path as every other source — **no model runs on the watch**, preserving the on-device claim.

**Next-due tile (phone → watch).** `WearSync.publishNextDue` reuses `TaskMindAppFunctions.getItemsDueToday`, writes `title` / `when` (e.g. `"Today · 15:00"`, or empty = "All clear") plus an `updated_at` timestamp (bumped so the DataItem's bytes change and actually re-sync even when text is identical). Freshness is driven by `WearSyncScheduler`: a periodic `WearSyncWorker` every **30 minutes** (`ExistingPeriodicWorkPolicy.KEEP`) plus an event-driven `syncNow()` (`ExistingWorkPolicy.REPLACE`, 1.5 s debounce) that observes the active-notes due signature and re-publishes the instant the due set changes. The watch (`NextDueTileService`) only *renders* what is pushed — it computes no due logic.

---

---

## 5.4 Data Design

TaskMind is a single-user, on-device application with **no first-party backend**; consequently there is no server-side data tier, no sharded/multi-region database, and no network-attached storage. The entire "system of record" is the user's own device. All durable state lives in three local stores, each chosen for a distinct access pattern:

| Store | Technology | Purpose | Encryption |
|---|---|---|---|
| Relational database | Room over **SQLCipher** (`net.zetetic:sqlcipher-android`, SQLCipher 4, AES-256) | Structured, queryable domain data (notes, suggestions, embeddings, learning memory) | Whole-file, at rest |
| Preferences key-value | Jetpack **DataStore** (`Preferences`) — two files: `source_settings`, `saved_filters` | Source toggles, forward-only enablement stamps, processed-id dedup ledgers, pinned filters | Not separately encrypted (non-secret operational state) |
| Secure key-value | **EncryptedSharedPreferences** (`secret_shared_prefs`, AES256-SIV keys / AES256-GCM values, Keystore-backed master key) | Secrets and sensitive settings: cloud LLM API key, connected Gmail accounts, DB encryption key, scan/retention/app-lock/theme settings | AES-256-GCM, at rest |

> Assumption: The project shorthand "schema v5" refers to the app's `versionName` "5.1"; the **Room schema version is 18** (`TaskMindDatabase.SCHEMA_VERSION = 18`, verified in code). This section uses the code-accurate value throughout.

### 5.4.1 Conceptual Data Model

Four Room entities plus two supporting key-value constructs make up the model. The only database-enforced relationship is `Note` → `NoteEmbedding` (1:0..1, cascade-on-delete). The `Suggestion` → `Note` relationship is a *derivation* — an approved suggestion is transcribed into a new note in application code (`SuggestionApprover`), **not** a foreign key — because a suggestion and its resulting note have independent lifecycles (the suggestion is later purged; the note persists). `RejectedPattern` is standalone learning memory that *influences* which suggestions are generated, with no row-level linkage.

```mermaid
erDiagram
    NOTES ||--o| NOTE_EMBEDDINGS : "has 0..1 vector (FK, ON DELETE CASCADE)"
    SUGGESTIONS |o..o| NOTES : "approved-into (app-level derivation, not a FK)"
    REJECTED_PATTERNS }o..o| SUGGESTIONS : "down-ranks/suppresses (heuristic, no row link)"

    NOTES {
        int id PK "autoGenerate"
        string title
        string summary "NN default ''"
        string body
        string dueDate "nullable, YYYY-MM-DD"
        string dueTime "nullable, HH:MM"
        string source
        long createdDate
        string type "note|todo|reminder|waiting_on"
        bool completed "NN default 0"
        long completedDate "nullable"
        string recurrence "nullable daily|weekly|monthly"
        string checklist "nullable JSON"
        double locationLat "nullable"
        double locationLng "nullable"
        double locationRadius "nullable, metres"
        string locationLabel "nullable"
        string priority "NN default 'normal'"
        bool nag "NN default 0"
        string counterparty "nullable"
        long pendingConfirmSince "nullable"
        int recurrenceAnchorDay "nullable 1..31"
        bool nagFiring "NN default 0"
        string tags "nullable CSV, 0..2"
        bool archived "NN default 0"
        bool repeatFromCompletion "NN default 0"
        long calendarEventId "nullable"
    }

    SUGGESTIONS {
        int id PK "autoGenerate"
        string source
        string rawSnippet
        string extractedTitle
        string summary "NN default ''"
        string dueDate "nullable"
        string dueTime "nullable"
        string type "note|todo|reminder|waiting_on"
        double confidence
        string status "pending|approved|rejected"
        string counterparty "nullable"
        long snoozedUntil "nullable"
        string location "nullable"
        string recurrence "nullable"
        string priority "NN default 'normal'"
        string tags "nullable CSV"
        string possibleDuplicateOf "nullable"
        bool repeatFromCompletion "NN default 0"
    }

    NOTE_EMBEDDINGS {
        int noteId PK "FK -> NOTES.id, CASCADE"
        blob vector "little-endian float32[256]"
    }

    REJECTED_PATTERNS {
        string kind PK "sender|keyword"
        string value PK "normalised (lowercased)"
        int count
        long updatedAt
    }
```

`SavedFilter` (pinned smart filters) and the `Tags` taxonomy are **not tables**: `SavedFilter` persists as a single JSON blob in the `saved_filters` DataStore, and `Tags` is a pure-Kotlin closed taxonomy with encode/decode helpers for the CSV `tags` column. Both are covered in §5.4.3.

### 5.4.2 Logical Data Model — Relational Store (Room / SQLCipher)

The `@Database` registers exactly four entities — `Note`, `Suggestion`, `RejectedPattern`, `NoteEmbedding` — with `exportSchema = true` (schemas checked into version control for migration testing) and **no** `@TypeConverters`. Room's default column affinities apply: `String`→`TEXT`, `Int`/`Long`/`Boolean`→`INTEGER`, `Double`→`REAL`, `ByteArray`→`BLOB`. Structured values that would otherwise need a converter are stored as encoded strings — `checklist` as a JSON array, `tags` as a comma-separated string from the fixed 7-value taxonomy (`Tags.encode`/`decode`) — deliberately keeping those models plain-JVM unit-testable.

#### 5.4.2.1 `notes` — the durable system of record

**Purpose:** an item the user has approved. Every note originates from an approved `Suggestion` or from a direct capture (quick capture / share sheet / widget). This is the only table users see as their "data".

| Column | Type (SQLite) | Null | Notes / added in |
|---|---|---|---|
| `id` | INTEGER | NN | **PK**, `autoGenerate` |
| `title` | TEXT | NN | |
| `summary` | TEXT | NN `''` | carried from the suggestion (v2) |
| `body` | TEXT | NN | |
| `dueDate` / `dueTime` | TEXT | nullable | `YYYY-MM-DD` / `HH:MM` |
| `source` | TEXT | NN | originating signal (sms, email, notification, …) |
| `createdDate` | INTEGER | NN | epoch ms; primary sort key |
| `type` | TEXT | NN | `note` \| `todo` \| `reminder` \| `waiting_on` |
| `completed` / `completedDate` | INTEGER / INTEGER | NN `0` / nullable | v3 |
| `recurrence` | TEXT | nullable | `daily`\|`weekly`\|`monthly` (v3) |
| `checklist` | TEXT | nullable | JSON `[{text,checked}]` (v3) |
| `locationLat`/`Lng`/`Radius`/`Label` | REAL/REAL/REAL/TEXT | nullable | geofenced location reminder (v3) |
| `priority` | TEXT | NN `'normal'` | `low`\|`normal`\|`high` — secondary sort after due date (v6) |
| `nag` | INTEGER | NN `0` | re-fire until done (v8) |
| `counterparty` | TEXT | nullable | the other party for `waiting_on`/commitments (v9) |
| `pendingConfirmSince` | INTEGER | nullable | awaiting one-tap "did they deliver?" confirmation (v11) |
| `recurrenceAnchorDay` | INTEGER | nullable | intended day-of-month 1–31, anti-drift anchor (v12) |
| `nagFiring` | INTEGER | NN `0` | nag chain active — survives reboot (v13) |
| `tags` | TEXT | nullable | 0–2 auto-tags, CSV (v14) |
| `archived` | INTEGER | NN `0` | Task-Fade/bankruptcy soft-archive (v16) |
| `repeatFromCompletion` | INTEGER | NN `0` | completion-based recurrence (v17) |
| `calendarEventId` | INTEGER | nullable | mirrored device-calendar event id (v18) |

**Indexes:** primary-key index on `id` only. No secondary indexes are declared. This is an intentional on-device trade-off: a single user's note set is small (hundreds to low thousands of rows), the dominant queries are full-list reads already ordered by `createdDate`/`completedDate`, and free-text search is a `LIKE '%term%'` scan that no B-tree index would accelerate anyway. Index maintenance cost is therefore not justified; SQLite full scans at this cardinality are sub-millisecond. Boolean/`type` filters (`completed = 0 AND archived = 0`, `type = 'reminder'`) are likewise evaluated by scan.

#### 5.4.2.2 `suggestions` — the transient Inbox

**Purpose:** an LLM-extracted candidate awaiting the user's decision; the human-in-the-loop gate before anything becomes a note. Rows are short-lived — purged once no longer `pending`.

Key fields beyond the shared ones: `rawSnippet` (the source text the extraction ran on), `extractedTitle`, `confidence` (REAL, drives the `ORDER BY confidence DESC` Inbox sort), `status` (`pending`\|`approved`\|`rejected` — the lifecycle state machine), `snoozedUntil` (epoch ms; snooze is applied against a ticking clock in the ViewModel so an item auto-resurfaces without a table write), `possibleDuplicateOf` (non-destructive "possible duplicate" flag — never used to drop a capture), and the carry-through fields (`priority`, `recurrence`, `tags`, `counterparty`, `location`, `repeatFromCompletion`) copied onto the note on approval. **PK** `id` (`autoGenerate`); no secondary indexes — the only hot query filters `status = 'pending'`.

#### 5.4.2.3 `note_embeddings` — semantic index sidecar

**Purpose:** one L2-normalised semantic vector per note, kept in a **separate table** so the ~1 KB BLOB is never loaded on ordinary note queries — only on demand for Ask-tab retrieval and near-duplicate detection.

| Column | Type | Key | Notes |
|---|---|---|---|
| `noteId` | INTEGER | **PK**, **FK → `notes.id`** `ON DELETE CASCADE` | one row per note |
| `vector` | BLOB | | little-endian packed `float32[256]` (`Vectors.toBytes`) |

The 256-dim vector is produced by `HashingEmbedder` (feature-hashed word tokens + character trigrams), giving a fixed ~1 KB (256 × 4 bytes) footprint per note regardless of text length. Because `noteId` is simultaneously the PK and the FK child column, the foreign key is already uniquely indexed — so no separate `@Index` is needed and Room raises no un-indexed-FK warning. **Cascade delete** guarantees a removed note never orphans its vector. Cosine similarity (`Vectors.cosine`) is computed in-memory over `getAllEmbeddings()`; there is no ANN/vector index — a linear scan is appropriate at single-user scale. The `equals`/`hashCode` overrides make the `ByteArray` compare by content.

#### 5.4.2.4 `rejected_patterns` — on-device learning memory

**Purpose:** records senders/keywords the user has repeatedly rejected, so the pipeline can down-rank or skip future suggestions from the same origin — entirely local personalization with no cloud training.

| Column | Type | Key | Notes |
|---|---|---|---|
| `kind` | TEXT | **PK (composite)** | `sender` \| `keyword` |
| `value` | TEXT | **PK (composite)** | normalised (lowercased) |
| `count` | INTEGER | | rejection tally |
| `updatedAt` | INTEGER | | epoch ms |

The **composite primary key `(kind, value)`** models the natural key directly and enables idempotent upserts (`@Insert(onConflict = REPLACE)` via `upsertRejectedPattern`); the key *is* the uniqueness constraint and index, so no surrogate id or secondary index exists. Content is content-addressable, so an approval can walk a sender's penalty back down and delete the row at zero.

### 5.4.3 Logical Data Model — Key-Value Stores

#### 5.4.3.1 DataStore (`source_settings`) — `SourceManager`

Non-secret operational state for the ingestion pipeline. Notable key groups:

- **Per-source enablement** — boolean keys (`sms_enabled`, `call_log_enabled`, `audio_enabled`, `images_enabled`, `notifications_enabled`, `calendar_enabled`, `app_usage_enabled`, `email_enabled`, `contacts_enabled`; contacts defaults **on** as an enrichment, all others **off** — opt-in).
- **Forward-only enablement stamps** — `*_enabled_at` (epoch ms), stamped only on the OFF→ON transition, so the scanner clamps each source's look-back to when it was enabled and never backfills history.
- **Dedup ledgers (bounded)** — processed-id sets that make ingestion idempotent, each capped to bound growth: `processed_sms_ids` (cap 500, evicts by keeping numerically-highest/most-recent ids), `processed_notification_keys` (cap 300, stores only a content **hash**, never text), `processed_audio_ids`, `processed_image_ids`, `processed_email_ids_<account>` (cap 200, keyed per account, with a legacy single-key read-fallback). `notification_allowlist` (empty = monitor all). `last_app_usage_digest_date` gates the daily digest.

#### 5.4.3.2 DataStore (`saved_filters`) — `SavedFilterStore`

A separate DataStore file holding the user's pinned smart filters as **one JSON array under a single key** (`saved_filters_json`), serialized with the app's Moshi so arbitrary filter names round-trip safely. Each `SavedFilter` is `{ name, kind?, tags[] }`. There are only ever a handful, changed rarely, so a Room table would be over-engineering.

#### 5.4.3.3 EncryptedSharedPreferences (`secret_shared_prefs`) — `SettingsManager` + DI

Secrets and sensitive settings, encrypted with a Keystore-backed master key. Includes the cloud LLM API key (`llm_api_key`), connected Gmail account set (`gmail_accounts`; **OAuth tokens are never stored** — only the address), routing/engine prefs (`use_on_device_llm` default **on**, `on_device_engine`, `on_device_model_path`, `whisper_second_pass`), calendar prefs, `retention_days` (0 = keep forever), `scan_frequency_minutes` (default 30), `app_lock_enabled` (default **on**), `theme_mode`, daily-brief/weekly-wins schedule, and the ingestion **watermarks** `last_scan_at` (epoch ms of the last successful scan) and `last_processed_sms_id` (contiguous high-water SMS `_ID`). Critically, the **32-byte SQLCipher database key** (`db_key`, base64) lives here too — generated once from `SecureRandom` and reused, so the encrypted database and its key share the same Keystore trust anchor.

### 5.4.4 Encryption, Schema Evolution & Recovery

**Encryption at rest.** The Room database file `taskmind_db` is opened through `SupportOpenHelperFactory(dbKey)` (SQLCipher), so the entire file — pages, indexes, WAL — is AES-encrypted; the 256-bit key is held in EncryptedSharedPreferences, itself protected by an AES-256-GCM master key in the Android Keystore. The DI layer loads `libsqlcipher` before opening, and the switch to `net.zetetic:sqlcipher-android` is on-disk-compatible (both SQLCipher 4), which also resolves the 16 KB native page-alignment requirement without a data migration.

**Schema evolution.** Version is a single source of truth (`SCHEMA_VERSION = 18`), also consulted by the backup subsystem to refuse restoring a backup made by a *newer* build (Room cannot downgrade). Seventeen hand-written, additive `Migration` objects (`MIGRATION_1_2` … `MIGRATION_17_18`) are registered in `DatabaseModule`. Every migration is a pure `ALTER TABLE … ADD COLUMN` (plus two `CREATE TABLE` for `rejected_patterns` at v3 and `note_embeddings` at v10, and a one-off backfill `UPDATE` at v12 for monthly-recurrence anchors). The discipline enforced throughout: each `NOT NULL` column's migration `DEFAULT` **exactly mirrors** the entity's `@ColumnInfo(defaultValue = …)` so Room's schema-hash validation passes on upgraded installs; nullable columns are added with no default. `fallbackToDestructiveMigration` is deliberately **not** used — a user's notes are never dropped to satisfy a version gap.

**Non-destructive recovery.** If the stored key can no longer open the file (rotated/lost Keystore key, torn file), the app never silently deletes data. `DatabaseModule` forces the SQLCipher open eagerly so a mismatch surfaces at construction, not on first query; it then (1) tries a `db_key_pending` slot to recover a restore interrupted between swapping the file and committing its key, and only if that also fails (2) calls `DatabaseRecovery.quarantine(...)`, which renames the file and its `-wal`/`-shm`/`-journal` siblings aside to a timestamped `.corrupt-<now>` name (preserving the still-encrypted bytes) before creating a fresh DB. `hasQuarantine` then drives a "your data was reset — restore from a snapshot?" affordance. This codifies a hard lesson from a prior incident that lost a user's notes.

### 5.4.5 Data Lifecycle

| Phase | Mechanism |
|---|---|
| **Creation** | `UnderstandingPipeline` inserts a `Suggestion` (`status = pending`, with `confidence`). On approval `SuggestionApprover` calls `insertNote(...)` (returns the new row id so the approve is one-tap-undoable) and copies carry-through fields; the vector is then written via `upsertEmbedding` (backfill only embeds ids missing from `embeddedNoteIds()`). Rejections upsert a `RejectedPattern`; pinning a filter appends a `SavedFilter`. |
| **Update** | Suggestions transition `pending → approved/rejected`; `snoozedUntil` is a soft, clock-evaluated hide (no row change needed to resurface). Notes are mutated through many **targeted `@Query UPDATE`** statements — completion (which also clears `nagFiring`), archive flag, due date, recurrence + anchor, priority, nag/nagFiring, `pendingConfirmSince`, location, `calendarEventId`, checklist, `repeatFromCompletion` — minimizing write amplification versus full-row updates. |
| **Archival (soft delete)** | `archived = 1` (Task-Fade / bankruptcy) removes an item from the active list and **all** active surfaces (including alarm re-arm) while keeping it fully recoverable from the Archived view — never auto-deleted. Timed reminders are also mirrored one-way to the system calendar (`calendarEventId`), updated/deleted as the note changes. |
| **Deletion** | Explicit: `deleteNote`/`deleteNoteById`/`deleteAllNotes`; suggestion cleanup via `deletePurgeableSuggestions` (`status != 'pending'`); `deleteRejectedPattern`(s). A note delete **cascades** to its `note_embeddings` row. |
| **Retention** | Opt-in, user-configured: `deleteNotesOlderThan(cutoff)` where `cutoff` derives from `SettingsManager.retentionDays`; **0 (default) = keep forever**. Dedup ledgers self-bound via `MAX_*` caps rather than time-based expiry. |
| **Backup/restore** | Bulk `insertNotes(...)` runs in one atomic Room transaction so an interrupted snapshot restore rolls back rather than leaving a partial, duplicate-prone set; restore coordinates the file swap with `db_key_pending` (see §5.4.4). |

The lifecycle enforces two invariants structurally: **human-in-the-loop** (no `Note` exists without an explicit user approval or capture), and **no silent data loss** (deletes are explicit or user-configured; corruption quarantines rather than wipes; archived items are hidden, never destroyed).

---

Source files read to ground this section (all under `D:\SMK\Android_apps\`):
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/model/{Note,Suggestion,NoteEmbedding,RejectedPattern,SavedFilter,Tags}.kt`
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/local/{TaskMindDatabase,TaskMindDao,DatabaseRecovery}.kt`
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/di/DatabaseModule.kt`
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/{SourceManager,SettingsManager,SavedFilterStore}.kt`
- `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/embedding/{Vectors,HashingEmbedder}.kt`

Key correction for cross-section consistency: the Room **schema version is 18** (17 migrations), not "v5" — the "5" in the canonical facts is the app `versionName`, not the database schema version.

---

## 5.5 Integration Design

### 5.5.1 Integration inventory

TaskMind integrates with **six** external/OS surfaces. It has **no message brokers, no service mesh, no ESB, no partner webhooks, and no inbound network integrations** — the phone is the backend, so all network integrations are *outbound* to Google, and all local integrations are OS/IPC-mediated.

| # | Integration | Type | Data exchanged | Auth method | Error handling / retry | Expectation (no server SLA) |
|---|---|---|---|---|---|---|
| 1 | **Gmail REST API** (`gmail.googleapis.com`) | Outbound REST/HTTPS (Retrofit + Moshi) | Read-only: message list, full messages (sender/subject/body), profile email | OAuth2 `Bearer` token (`gmail.readonly`, restricted scope), fetched on demand | Per-call try/catch; **401 → `Unauthorized`** → invalidate + re-auth + retry once; other errors → skip that item; window caps (100 msgs / 25 pages) | Best-effort per background scan; bounded by `after:` watermark; subject to Google's own quotas/availability |
| 2 | **Google Gemini** (`generativelanguage.googleapis.com`) | Outbound REST/HTTPS (raw OkHttp + `org.json`) | Prompt text (and, for vision, a base64 screenshot) out; schema-pinned JSON extraction in | API key as `?key=` query param (from EncryptedSharedPreferences) | Blank key → local schema-shaped empty (no call); non-2xx / IOException / TLS / missing body → fallback empty (`generate`) or `null` (vision); **no automatic retry/backoff** | Synchronous single call per extraction; quality path; latency/availability per Google |
| 3 | **Google OAuth** (`GoogleAuthUtil`, `oauth2.googleapis.com`) | Play Services token API + one HTTPS revoke | Account email in; access token out; token revoke out | Package + signing SHA-1 vs. registered Android OAuth client; per-account, user consent intent | `NeedsConsent` intent on first use; hard failures classified via basic-scope probe → actionable guidance; revoke is best-effort | On-demand; silent when a grant exists (enables background scans) |
| 4 | **Google Maps directions** (`com.google.android.apps.maps`) | Outbound **intent hand-off** (Universal URL) | Destination (lat,lng or place name) via `ACTION_VIEW` | None (no key) | `ActivityNotFoundException` → fall back to any URL handler (browser) | User-initiated; fire-and-forget |
| 5 | **Android AppFunctions** (OS agent) | Exposed in-process interface (`androidx.appfunctions`, KSP) | Agent → app: create/list/snooze requests; app → agent: result/list DTOs | OS-mediated (the platform agent is the caller); no token | Field validation (blank title, bad date, unknown id) → `AppFunctionResult(false, msg)`; create is dedup-aware | Synchronous suspend calls; created items are proposals pending user approval |
| 6 | **Wear Data Layer** (paired watch) | Exposed IPC over GMS Wearable (Message/Data/Capability clients) | Watch→phone: capture text; phone→watch: next-due `DataMap` | GMS node pairing + advertised capability `taskmind_phone_capture` | Capture: no reachable phone → `onNoPhone` (not falsely "added"); send failure → `onError`. Tile: `WearSyncWorker` → `Result.retry()` on exception | Eventually-consistent; tile refreshed ≤30 min (periodic) + on-change (debounced) |

### 5.5.2 Transport, resilience, and cross-cutting concerns

- **Shared HTTP client.** Gmail (Retrofit) and the OAuth revoke both use the single Hilt-provided `OkHttpClient` from `NetworkModule`; Gemini uses that same injected client. **Assumption:** the client is built with `OkHttpClient.Builder().build()` with **no configured timeouts, interceptors, or retry policy**, so OkHttp defaults apply (10 s connect/read/write, `retryOnConnectionFailure = true` for transient connection errors only). `ModelDownloader` is the one exception, using an independent client with explicit 30 s/60 s timeouts. There is no shared circuit breaker; resilience is per-call (fallbacks/skips) rather than transport-level.
- **Retry semantics differ intentionally.** Gmail retries only on the 401 re-auth path; Gemini does **not** retry (a failed call degrades to an empty schema-shaped result so a source scan is never aborted — important because the call log has no dedup ledger and dropped rows are lost); the Wear tile worker retries via WorkManager (`Result.retry()`); capture delivery does not retry (it surfaces a truthful "no phone"/error to the watch UI).
- **Failure isolation.** A guiding principle across §5.5.1 rows 1–2: a single integration failure must not abort a whole background scan. `GmailCollector.apiCall` returns `null` per bad message; `CloudLlmProvider.sendForJson` guards the entire send and returns the schema-shaped fallback rather than propagating an `IOException`.
- **Security in transit and at rest.** All network integrations are HTTPS/TLS (OkHttp). No integration secret is hardcoded except the known-debt cloud Gemini key ("express" key in `SettingsManager`); Gmail uses no embedded client-id (matched by signature). Account emails are masked in logs; the vision path logs that an image left the device but never the pixels.
- **Auditability.** `EgressLogger.record(host, purpose)` precedes every outbound call (Gmail fetch/profile, token acquire/invalidate/revoke, cloud extraction/breakdown/intent/vision), providing an on-device analogue to server access/audit logs, surfaced in the Data Egress screen.

### 5.5.3 Known integration constraints / technical debt

- **Restricted Gmail scope.** `gmail.readonly` is a restricted scope; for public Play distribution the app must be Google-verified, and Advanced-Protection / supervised (Family Link) accounts can block third-party Gmail access entirely — `GmailAuth` detects and explains this rather than failing opaquely.
- **Cloud LLM auth.** The Gemini integration authenticates with an API key on the query string ("express"-style), a stopgap; there is no per-user OAuth for the LLM and the key is a documented liability.
- **AppFunctions maturity.** The `androidx.appfunctions` binding targets an alpha API; end-to-end agent invocation cannot be validated through the Gemini voice preview until GA and is instead verified on-device via `cmd app_function list/execute`.
- **Wear contract duplication.** `WearContract` is copied between the phone and Wear modules by design (no shared module); the two copies must be kept byte-identical or capture/tile sync silently breaks.

---

## 5.6 Ingestion & Understanding Pipeline

This section documents the heart of TaskMind: the path a raw personal signal (an SMS, a call, an email, a screenshot, a voice recording, an app-usage digest) travels from a device content provider, through per-source collectors, on-device transcription/OCR, an LLM extraction stage, and a deterministic sanitization/de-duplication layer, before it lands in the Inbox as a pending **Suggestion**. Nothing in this pipeline writes a **Note**; the pipeline's sole output is an unapproved suggestion carrying a confidence score, and the user's explicit approval in the Inbox is the gate that promotes a suggestion to a saved Note (§ owned elsewhere). The design is deliberately privacy-first: every stage runs on-device except a single, opt-in, per-request egress to Google's Gemini API, and even that is audited.

All package paths below are rooted at `com.rajasudhan.taskmind.data.source`.

### 5.6.1 End-to-end data flow

The canonical flow is **ingestion → understanding → suggestion → note**:

| Stage | Owner | Responsibility |
|---|---|---|
| 1. Scan | `RecentDataScanner` | For each *enabled* source, query the device provider since a per-source watermark; hand raw text/media to the pipeline. |
| 2. Pre-process | `Transcriber`, `OcrEngine`, `GmailTextExtractor` | Turn non-text media into text: audio → transcript (Vosk/Whisper), image → OCR (Tesseract), MIME email → plain text + calendar summary. |
| 3. Pre-filter | `ExtractionHeuristics.isLikelyNoise` | Drop obvious non-actionable noise (OTPs, promos, opt-outs) *before* spending an LLM call. |
| 4. Understand | `UnderstandingPipeline` → `LlmProvider` (`RoutingLlmProvider`) | Prompt the routed LLM (on-device or cloud) with a static system instruction + the source-tagged body; parse strict JSON into `LlmItem`s. |
| 5. Reconcile | `NaturalDate`, `ExtractionHeuristics` | Override the model's shaky relative-date math with a deterministic parse; sanitize every field (date/time/recurrence/priority/tags). |
| 6. Score & dedup | `RejectionLearner`, `ExtractionHeuristics.isDuplicate`, `SemanticIndex` + `NearDuplicate` | Apply a per-sender confidence penalty, drop exact `(title, date)` twins, flag (never drop) near-duplicates. |
| 7. Persist | `TaskMindDao.insertSuggestion` | Insert a `pending` `Suggestion` row; fire a single self-updating review notification. |
| 8. Approve | Inbox UI (out of scope) | User approves/edits/rejects/snoozes; approval creates a `Note` and indexes its embedding via `SemanticIndex.index`. |

Two entry-point families feed stage 1:

- **Batch / incremental** — `RecentDataScanner.scanIncremental()`, shared by the manual Inbox refresh and the periodic `DataCollectionWorker` (WorkManager, ~30 min default). Both advance the same watermark (`SettingsManager.lastScanAt`).
- **Live** — `scanAudioRecent()` / `scanImagesRecent()`, called by the foreground-service media observers with a tight 5-minute look-back so a just-saved recording/screenshot is captured near-real-time.

A structured-capture side channel (`UnderstandingPipeline.captureFromAgent`, used by Android AppFunctions) and a missed-call side channel (`addCallback`) bypass the LLM entirely and insert deterministically.

### 5.6.2 Data sources & collectors

`RecentDataScanner.scanSince(sinceMillis)` gates each source on its toggle (`SourceManager.is*Enabled`) and wraps each in `runCatching`, so one source's failure never aborts the others:

| Source | Provider / API | Toggle & permission | Collector method | What is fed to the pipeline |
|---|---|---|---|---|
| SMS | `Telephony.Sms.Inbox` ContentResolver | `isSmsEnabled` / `READ_SMS` | `scanSms` + `recoverMissedSmsById` | `processText("SMS from $address", body)` |
| Call log | `CallLog.Calls` | `isCallLogEnabled` / `READ_CALL_LOG` | `scanCalls` | missed → `addCallback`; else `processText("Call Log", "$typeStr call with $number lasting $duration seconds.")` |
| Email | Gmail REST via `GmailCollector` | `isEmailEnabled` / OAuth `gmail.readonly` | `scanEmail` | `processText("Email ($account) from ${sender}", "$subject\n\n$body")` |
| Audio recordings | `MediaStore.Audio` + `Transcriber` | `isAudioEnabled` | `scanAudio` | `processText("Recording: $name", transcript)` |
| Screenshots/images | `MediaStore.Images` + `OcrEngine` | `isImagesEnabled` | `scanImages` | `processText("Screenshot: $name", ocrText)` |
| App usage | `UsageStatsManager` | `isAppUsageEnabled` | `AppUsageCollector.generateDailyDigestIfDue` | inserts a `note` Suggestion directly (no LLM) |

Design points worth noting:

- **The `Source:` label is load-bearing.** It is threaded verbatim into the LLM prompt (`"Source: $source\n\nText:\n$body"`) so the model can adapt ("a Voice note may spell digits out in words; a Screenshot may contain OCR noise"). It is also parsed by `RejectionLearner.senderKey` (everything after `" from "`) to attribute rejections to a sender, and it drives the honest engine/route labelling in the Inbox.
- **Missed calls bypass the LLM.** A bare missed call is routinely dropped by the model as "non-actionable", so `scanCalls` calls `pipeline.addCallback(cachedName, number)`, which builds a `"Call back <who>"` todo at confidence 0.95 directly from the log (the Call action later dials `number`). Chat-app missed-call *notifications* (WhatsApp/Telegram) reach the same `addCallback` with only a display name.
- **Person-context.** An incoming call from a known contact (`CACHED_NAME`) triggers `PersonContextNotifier.notifyForContact`, surfacing any open item tied to that person.
- **Recording folder matching** (`scanAudio`) is OEM-robust: `RecordingPaths.scanPatterns` unions the user's configured Call/Voice folders (mapped from absolute to MediaStore volume-relative fragments via `relativePattern`) with a curated `COMMON_RECORDING_PATTERNS` list (Samsung `Recordings/Call`, Xiaomi `MIUI/sound_recorder`, generic `CallRecordings`, …). All matches are constrained by `IS_MUSIC = 0` so music is never swept in, and the pattern set is never empty (which would match nothing) nor `%%` (which would match everything).
- **App-usage digest** is fully deterministic and offline: once per day (date-gated on `SourceManager.lastAppUsageDigestDate`), `AppUsageCollector` builds a top-apps screen-time summary for *yesterday* and inserts it as a `note` Suggestion at confidence 1.0.

### 5.6.3 The scan/watermark model (incremental, forward-only)

`scanIncremental()` computes a look-back window rather than a fixed interval, so nothing in the gap between refreshes is lost:

```
since = last <= 0  ? now - FIRST_RUN_LOOKBACK_MS (15 min)
                    : max(last, now - MAX_LOOKBACK_MS (24 h))
```

- The 24 h clamp (`MAX_LOOKBACK_MS`) prevents a long-dormant app from re-scanning months of history.
- The first-ever run only looks back 15 min (`FIRST_RUN_LOOKBACK_MS`) to avoid an initial flood.
- The watermark (`lastScanAt`) is advanced **after** the scan and unconditionally — because each source swallows its own errors, so we accept re-scanning over dropping data.

**Per-source enabled-at clamping.** Each source window is `max(since, sourceManager.<source>EnabledAt)`, so enabling a source captures *forward-only* from when it was switched on, never backfilling up to 24 h of pre-consent history. A legacy `0` stamp leaves the window unchanged.

**De-duplication ledgers.** Because a source may be re-scanned, most sources keep a processed-id ledger in DataStore (`SourceManager.processedSmsIds`, `processedAudioIds`, `processedImageIds`, and per-account `processedEmailIds(account)`); an id already in the ledger is skipped. The call log is the exception — it has **no** ledger, so any row left unscanned when `lastScanAt` advances is lost for good, which is precisely why `scanCalls` mirrors `consumeSmsRows` and isolates every row in `runCatching`.

**SMS gap recovery.** The 24 h date clamp cannot reach a message that arrived while the app was dead for longer than 24 h (it is older than `since` at scan time). `recoverMissedSmsById` closes this hole: it walks rows above a persisted contiguous `_ID` watermark (`SettingsManager.lastProcessedSmsId`), oldest-first, capped at `MAX_SMS_RECOVERY_PER_SCAN = 200` per scan so a long dormancy cannot flood the LLM, then advances the watermark past everything it saw. On first run it merely seeds the watermark to the current newest id (never mining pre-existing history).

### 5.6.4 On-device transcription

`Transcriber` implements a two-pass, on-device speech-to-text policy behind the `TranscriptionProvider` seam:

1. **First pass — Vosk** (`VoskTranscriber`, primary and the only live-dictation engine). Offline, no network. The model is not bundled; a Vosk model (recommended `vosk-model-small-en-in-0.4`, Indian English) is pushed to `filesDir/vosk-model/` or a zip auto-unpacked on first use (with zip-slip protection). Audio is decoded to 16 kHz mono 16-bit PCM by `AudioDecoder` (a `MediaExtractor` + `MediaCodec` decode, then a pure `pcmDownmixAndResample`), then fed to a `Recognizer` in ~0.25 s (8000-byte) chunks.
2. **Second pass — Whisper** (`WhisperTranscriber` over the native `WhisperEngine`), run only when the user enabled it *and* the model (`filesDir/whisper-model.bin`, quantized ggml) is present *and* the native binding is linked. whisper.cpp is invoked through JNI (`NativeWhisperEngine`, `libwhisper_jni.so`, loaded once at class-load; absence degrades gracefully to `isAvailable()==false`). The pure `pcm16ToFloat` helper converts 16-bit LE PCM to the normalized `[-1,1)` float samples whisper.cpp expects.

**Adoption is gated by materiality.** `Transcriber.transcribe` adopts the Whisper result only when `TranscriptDiff.isMaterialChange(first, second)` is true — i.e. the first pass was empty (second "rescued" it) or the two share **less than `MATERIAL_OVERLAP = 0.7`** content-token overlap (reusing `NearDuplicate.tokenOverlap`). A second pass that merely reshuffles whitespace/case does not trigger a wasted re-extract downstream. `TranscriptResult.secondPassUsed` is surfaced for logging/telemetry.

### 5.6.5 On-device OCR

`OcrEngine` performs offline OCR via Tesseract (`TessBaseAPI`). The trained data (`eng.traineddata`) is not bundled; it is pushed to `filesDir/tessdata/` (Tesseract's `init(dataPath, "eng")` expects a `tessdata/` subfolder, so `dataPath = filesDir`). `recognize(uri|file)` decodes the bitmap off the main thread (`Dispatchers.Default`), runs recognition under a `Mutex` (Tesseract is not reentrant), collapses whitespace, and recycles both the `TessBaseAPI` and the bitmap. Presence is cheap (`trainedFile().exists()`), and `tryLoad()` provides a Settings diagnostic.

### 5.6.6 The Understanding Pipeline

`UnderstandingPipeline` is the wiring between the collectors, the routed LLM, the pure heuristics, and the DAO. Its contract is strict: **it only ever writes pending suggestions.**

**Prompt assembly (`processText`).**
1. Cheap pre-filter: return immediately if `text.isBlank()` or `ExtractionHeuristics.isLikelyNoise(text)`.
2. Build a datetime line (`yyyy-MM-dd'T'HH:mm` + weekday name) and substitute it into `SystemPrompt.INSTRUCTION` at `{{CURRENT_DATETIME}}` — the static instruction is cacheable; only the datetime and the user body vary.
3. Cap the body at `MAX_INPUT_CHARS = 4000` (~1000 tokens at ~4 chars/token) so prompt + JSON output stay under the on-device model's 2048-token KV cache.
4. Compose the user message as `"Source: $source\n\nText:\n$body"` and call `llmProvider.generate(systemInstruction, userMessage)`.

**Parse / salvage / retry.** `tryParse` first strips markdown fences (`ExtractionHeuristics.stripJsonFences`, which trims whitespace *before* removing a trailing ` ``` ` so a common trailing-newline shape doesn't defeat it) and attempts a strict Moshi `LlmResponse` parse. Because that parse is all-or-nothing, `salvageItems` walks the `items` JSON array element-by-element and keeps only the ones that individually parse — but it returns `null` (forcing the stronger-nudge retry) *unless at least one salvaged item is acceptable*, preserving the pre-salvage behaviour that prose/broken payloads still retry. On a null parse the pipeline re-asks once with an appended `"Return ONLY the JSON object described above, nothing else."` nudge (never echoing the bad output back).

**Deterministic date seeding (#116).** For a *user-typed* capture (`seedSchedule = true`, e.g. quick capture and Ask "create"), `NaturalDate.parse(text, now)` runs once. If the extractor produced nothing but the user typed a clear **date**, a `fallbackItem` reminder is synthesized from the parse alone at `FALLBACK_CONFIDENCE = 0.9` (its schedule spans strip the date phrase out for a clean title). A bare time/recurrence with no date is intentionally *not* auto-created (too ambiguous). Seeding is gated to a single item so a parsed date isn't smeared across a multi-task brain-dump.

**Field reconciliation (in `insertItems`).** For each item, the deterministic seed and the LLM fields are merged into one coherent schedule:
- **date**: the parsed date wins over `ExtractionHeuristics.sanitizeDate(item.dueDate)` (deterministic beats the model's relative-date math);
- **time**: the parsed time only overrides when it came *with* a parsed date, so a stray bare time can't clobber the model's coherent `(date, time)` pair;
- **recurrence**: `seeded.recurrence` is applied only onto a schedulable item (`reminder`/`todo`), else falls back to `sanitizeRecurrence(item.recurrence)`;
- **completion-based repeat** (#124) rides only on the deterministic `"!"` marker and only when a repeat actually stuck onto a schedulable item.

**Scoring, de-dup, insert.** Under a single `insertMutex.withLock` (held only around the fast DB check + insert, never around the slow LLM call):
- Confidence is reduced by `RejectionLearner.confidencePenalty(source)` — `PENALTY = 0.3` once a sender crosses `REJECT_THRESHOLD = 3` rejections (a soft down-rank, recoverable via `recordApproval`; never a hard block).
- The item is kept only if `ExtractionHeuristics.isAcceptable` (non-blank title AND `confidence ≥ MIN_CONFIDENCE = 0.6`) **and** it is not an exact `(title, effectiveDate)` duplicate of an existing pending suggestion or approved note. The dedup keys on the *effective* stored date, not the raw LLM date, so an item can't re-insert its own twin.
- A surviving item is written as a `Suggestion(status = "pending", …)` with the sanitized fields plus a non-destructive `possibleDuplicateOf` flag (see §5.6.10).

The mutex is real concurrency protection: the live `SmsObserver` and the periodic `RecentDataScanner` can hand the same SMS to `processText` at once, and a re-posting chat app can hit `addCallback` twice.

**Multimodal seam (`processMedia`, #211/#213).** A vision-capable engine can consume an image/audio `MediaInput` directly, bypassing OCR/transcription. It returns `true` when a vision engine actually ran (so the caller must not also OCR/transcribe) and `false` when none can see. Today the on-device engines report `supportsVision()==false`, so this ships dark for on-device; the cloud engine (Gemini 2.5 Flash) *can* read a screenshot, so images route to the cloud when a key is set (audio still uses transcription).

### 5.6.7 LLM routing: on-device vs cloud

`RoutingLlmProvider` (the injected `LlmProvider`) selects the backend per request from the user's Settings choice, with a key-gated fallback:

- **On-device selected** (`SettingsManager.useOnDeviceLlm == true`): try `OnDeviceLlmProvider`; on any exception fall back to `CloudLlmProvider` **only if `llmApiKey` is non-blank**, else return a schema-shaped empty (`{"items": []}`, `[]`, or `{"action":"query"}`).
- **Cloud selected**: call `CloudLlmProvider` directly.

`OnDeviceLlmProvider` is itself a router over the `OnDeviceEngine` seam introduced by the Gemma 3n migration (#212):

| Engine | Class | Backend | Model | Status |
|---|---|---|---|---|
| MediaPipe (default) | `MediaPipeEngine` | MediaPipe `LlmInference`, GPU backend | Local Gemma `.task`/`.litertlm` in `filesDir` (not bundled) | Working default |
| LiteRT-LM | `LiteRtLmEngine` | LiteRT-LM (Gemma 3n) | `.litertlm` | Scaffold only — throws `UnsupportedOperationException` until the runtime is linked |
| Gemini Nano | `NanoEngine` | ML Kit GenAI Prompt API via AICore | System-provided (zero download) | Device-dependent (throws/`FEATURE_NOT_FOUND` on unsupported devices) |

`OnDeviceLlmProvider` delegates to the user-selected engine and **falls back to `MediaPipeEngine` whenever the chosen engine can't run** (the migration's safety net, doc invariant #1), rethrowing only when even MediaPipe has no model so `RoutingLlmProvider` can take over. Notable engine details: `MediaPipeEngine` caps `setMaxTokens(2048)` (the GPU/OpenCL backend's int4 KV-cache limit) and wraps every call in the Gemma chat-turn template (`<start_of_turn>user … <end_of_turn>\n<start_of_turn>model\n`) under a `Mutex` (not reentrant); it rebuilds the engine if the model path changes. `NanoEngine` caches an async availability flag (starting pessimistic) refreshed by `tryLoad`/`generate`, and clears it on a mid-session AICore failure so the honest label can't report a stale "ready".

`CloudLlmProvider` targets **`gemini-2.5-flash`** over **raw OkHttp** (hand-built `okhttp3.Request` calls against `generativelanguage.googleapis.com`, no Retrofit — Retrofit is used only for the Gmail stack) with `temperature = 0.1`, `responseMimeType = application/json`, and a per-call **`responseSchema`** that pins the exact output shape (this is far more reliable than asking in prose and is what the on-device model *cannot* enforce). Three schemas exist: the extraction `{items:[…]}` shape (§5.6.8), a bare string-array for Magic Breakdown, and a flat intent object for Ask. Every cloud call first records a metadata-only entry via `EgressLogger.record("generativelanguage.googleapis.com", purpose)` — host + purpose, never content — surfaced in the Data Egress audit screen. `sendForJson`/`sendForJsonOrNull` guard the entire send: a network `IOException` or a 200-with-no-usable-text (safety block / `MAX_TOKENS` truncation) yields the schema-shaped fallback (or `null` on the vision path) rather than propagating and aborting the rest of a source's scan.

**Honest engine labels (#197).** Routing is mirrored by two boolean helpers the UI reads instead of hardcoding "on-device":
- `isOnDeviceEffective()` — true only when on-device is selected *and* (the model is present *or* there is no cloud key to fall back to). It flips to false whenever text work would actually reach Gemini.
- `mediaEgressesToCloud()` — true when a screenshot/voice extraction would route to the cloud (on-device has no vision/audio model yet), so image/audio capture isn't mislabelled "on-device" even while text stays local.

The pure `visionRoute(...)` function encodes the multimodal routing decision (on-device vision → cloud-with-key → none) so it is unit-testable without the providers. Because `cloudVision` is `true` (the cloud provider supports vision), it returns `CLOUD` when a cloud key is present and `NONE` only when no key is set and on-device vision is unavailable.

### 5.6.8 Extraction heuristics & prompt engineering

**`SystemPrompt.INSTRUCTION`** is a ~180-line contract that (a) explains the `Source:`/`Text:` framing, (b) injects the current datetime for relative-date resolution, (c) specifies the exact JSON object shape, and (d) encodes the extraction policy: the four item types (`reminder` = date **and** alert time, `todo` = action without alert time, `note` = info to keep, `waiting_on` = someone owes *you*); brain-dump splitting rules for punctuation-less voice transcripts (topic-shift/connector boundaries, self-correction "keep only the corrected version", filler dropping); a closed 7-tag taxonomy (`Money/Health/Family/Work/Shopping/Travel/Home`); a conservative priority policy ("`high` only on explicit urgency", never `low`, "a wrongly-flagged high is worse than a missed one"); and an extensive "informs/advertises ≠ action item" section (social pings, marketing, shipping/order updates, payment confirmations, OTP/security notices all return `{"items": []}`), balanced by explicit carve-outs for genuine meeting invites, bills the user must still pay, personal info to remember, and trips/appointments. Roughly 25 worked examples (anchored to a fixed reference date) pin the desired behaviour.

**`ExtractionHeuristics`** is a pure, dependency-free, JVM-unit-tested safety net that runs regardless of whether the prompt or schema behaved:

| Function | Behaviour |
|---|---|
| `isLikelyNoise` | True only when no `ACTIONABLE_HINTS` (invite/meeting/RSVP/Zoom/Meet/Teams cues) match **and** a `NOISE_PATTERN` does — so a meeting invite under an "unsubscribe" footer still reaches the LLM. |
| `sanitizeDate` | Requires `yyyy-MM-dd` shape *and* a real calendar date (`LocalDate.parse`), rejecting hallucinated `2026-02-30`/`2026-13-01` that would strand a never-firing alarm. |
| `sanitizeTime` | Requires `H:mm`/`HH:mm`; drops a datetime stuffed into `due_time`. |
| `sanitizeRecurrence` | Clamps to `daily`/`weekly`/`monthly`, else null. |
| `sanitizePriority` | Only explicit `high` survives; everything else (including `low`) floors to `normal` (column is NOT NULL). |
| `sanitizeTags` | Canonicalises to the closed `Tags` taxonomy, drops hallucinated tags, de-dupes, caps at `Tags.MAX_TAGS`, encodes comma-separated or null. |
| `stripJsonFences` | Trim-first fence removal (see §5.6.6). |
| `isAcceptable` | Non-blank title AND `confidence ≥ 0.6`. |
| `isDuplicate` | Exact `(title, dueDate)` match against known items. |

The `LlmItem` DTO defaults every field (`type="note"`, `confidence=0.7`, nullable optionals) so a small on-device model that omits fields still parses; the cloud schema mirrors it exactly and pins enums for `type`, `priority`, and `tags`.

### 5.6.9 Deterministic natural-language date parsing (`NaturalDate`)

Because the LLM is unreliable at relative-date arithmetic, dates for user-typed captures are resolved by a rule-based, offline, pure-function parser (`NaturalDate.parse(text, now)` → `ParsedSchedule`). It is deliberately **conservative** — it favours a miss (null, so the model still gets a shot) over a false positive that would stamp a bogus schedule:

- **Recurrence** — `daily`/`weekly`/`monthly`, `every <unit>`, `every <weekday>`; a trailing `!` marks completion-based repeat (#124); a weekday recurrence also seeds the next occurrence as the date.
- **Relative offsets** — `in N hours/minutes` pins both date and time; `in N days/weeks/months` is date-only.
- **Named/weekday/month-day/numeric dates** — `today`/`tonight`/`tomorrow`/`next week|month`; `this|next <weekday>` and bare weekday → next future occurrence; `<month> <day>` / `<day> <month>` → this year or next if past; a **numeric date must carry a year** (`M/D/YY(YY)`), so `2/3 cup` or `1/2 tank` is not misread as a date.
- **Times** — meridiem incl. speech-to-text `p.m.`/`a.m.` (#235); 24-hour `HH:mm`; named (`noon`→12:00, `midnight`→00:00, `morning`→09:00, `afternoon`→15:00, `evening`/`tonight`→20:00); `at N` only when not followed by a word ("at 3 people" is a count), assuming PM for 1–7.

`ParsedSchedule.spans` are the source character ranges each field came from, which lets the capture UI highlight them and lets `stripSchedule` cut the schedule phrases (and trim dangling connectives like "at"/"on") out of the fallback title — turning "call mom tomorrow 5pm" into "call mom".

### 5.6.10 Semantic index & near-duplicate detection

The semantic layer (`embedding` package) serves two questions that need *meaning* rather than keywords: near-duplicate detection and Ask/Notes search.

- **`Embedder` seam** — the shipped `HashingEmbedder` is a dependency-free, near-instant (microseconds), model-free lexical embedder. It hashes word tokens **and** boundary-padded character trigrams into a fixed **256-dim** signed-feature-hashed vector, then L2-normalises it (`WORD_WEIGHT = 1.0`, `GRAM_WEIGHT = 0.35`, `GRAM = 3`). Single-character digits are kept ("buy 2" ≠ "buy 4"), as are most single letters — though `HashingEmbedder.STOPWORDS` also drops the single letters "a" and "i" alongside its multi-character function words. Trigrams give typo/morphology tolerance ("dentist" ≈ "dentists"). It captures *lexical* similarity only — it will not relate "plumber" to "kitchen tap" — and the interface is deliberately swappable for a neural embedder (e.g. EmbeddingGemma) later.
- **`Vectors`** — pure cosine (dot product for unit vectors), in-place `normalize`, and little-endian `toBytes`/`fromBytes` for the `NoteEmbedding` BLOB column (~1 KB/vector).
- **`SemanticIndex`** — embeds a note's `title + summary` (`textFor`), stores it (`index`/`backfill`), and answers `scores(query, floor)` returning `noteId → cosine` for notes at/above `SEARCH_FLOOR = 0.35f`.

**Non-destructive dedup (#145).** Similarity alone is too broad to *drop* a capture, so the pipeline never does. `possibleDuplicateOf` runs a two-stage confirm: `SemanticIndex.scores` pre-filters candidate notes, then the strict lexical guard `NearDuplicate.isLikelyDuplicate` confirms — requiring content-token Jaccard overlap **≥ `TOKEN_OVERLAP = 0.6`** *and* compatible dates (identical, or at least one side undated). Pending suggestions are checked with a pure lexical pass (they aren't embedded). The result is only ever an in-Inbox advisory flag (one tap to dismiss); a false negative just means "no flag". It runs only on items that already cleared the exact `(title, date)` dedup, so a true re-scan is dropped and only a *variant* is ever flagged.

### 5.6.11 Ask TaskMind (retrieval-augmented Q&A)

`AskEngine` answers a natural-language question/command about saved items **fully on-device and safely**: the LLM is used *only* to classify the utterance into a small closed `AskIntent` (`generateIntent`, cloud-pinned to a flat schema; on-device free-form and tolerated even if wrapped in `{items:[…]}`). Everything else — running the intent against Room, ranking, phrasing — is deterministic (`AskQuery`), so a Gemma-class model cannot hallucinate items:

- `action == "create"` with text → reuses `UnderstandingPipeline.processText(..., seedSchedule = true)`, landing the item in the Inbox.
- A real `query` intent → `AskQuery.matches` filters active/completed notes by the set slots (type/tag/`window`/keyword). If the model set **no** slot (`hasAnySlot == false`), the query would match every note, so it degrades to keyword+semantic **search**. Structured misses answer plainly ("No tasks due today — you're all clear"); content misses fall through to search.
- Search (`rank`) blends lexical hits (title/summary/body contains) *first*, then `SemanticIndex` scores above the floor — the same recipe as Notes search — capped at `RESULT_LIMIT = 12`.

Answer phrasing is count-and-label only (`answerFor`/`noun`/`windowPhrase`), never invented content. An unparseable/low-confidence classification degrades to search, so the user always gets a real answer built from real rows — this is exactly what `RoutingLlmProvider`'s `EMPTY_INTENT` (`{"action":"query"}`) fallback relies on.

### 5.6.12 Magic Breakdown

`MagicBreakdown` turns one vague task into a 3–6 step checklist. The prompt (`INSTRUCTION`) asks for a bare JSON array of short strings; the cloud path pins a `stringArraySchema()` so it can't be coerced into the extraction shape (which previously leaked field names into the checklist), while the on-device path leans on the tolerant `parseSteps`. `parseSteps` handles a JSON array, a truncated/unclosed array (detecting a lone `[` so `MAX_TOKENS` cut-offs still yield their complete quoted items), numbered/bulleted lists, or a comma list; it trims, de-bullets, un-escapes JSON, de-duplicates case-insensitively, caps each step at `MAX_LEN = 80`, and limits to `MAX_STEPS = 6`.

### 5.6.13 Reliability & failure-isolation characteristics

The pipeline is engineered so no single failure aborts a batch or silently drops data:

- **Per-row / per-source isolation** — every SMS/call/email row and every source is wrapped in `runCatching`; a cloud `IOException`, a malformed row, or a transient DB read is logged and skipped, not propagated.
- **Ledger-vs-window belt-and-braces** — the date window plus id ledgers plus the SMS `_ID` recovery watermark together guarantee at-least-once processing for ledger-backed sources; the call log's ledger-less design is explicitly compensated by per-row isolation.
- **Schema-shaped fallbacks** — every provider path returns a parser-safe empty result on failure, so a downstream Moshi parse never chokes on a network error.
- **Graceful capability degradation** — a missing Vosk/Whisper/Tesseract/on-device model, an unsupported Nano device, or an unlinked LiteRT-LM runtime each degrades to the next available path (or a no-op scan) rather than crashing.
- **Concurrency safety** — `insertMutex` serialises the dedup-check-then-insert region against the live-observer/periodic-scanner race; engine `Mutex`es serialise non-reentrant MediaPipe/Vosk/Tesseract instances.
- **Auditable, minimal egress** — the only outbound calls are Gemini (extraction/breakdown/intent/vision) and Gmail/OAuth; each is recorded by `EgressLogger` with host + purpose and never content, and email/account PII is masked in logs (`GmailAuth.mask`).

---

*Assumption: the ~30-minute default `DataCollectionWorker` cadence and the "SmsObserver"/foreground-service live wiring are named from their referenced call sites (`scanIncremental`, `scanAudioRecent`, `scanImagesRecent`) and CANONICAL FACTS; their scheduling internals live outside the files in this section's scope (§ Background/Reliability).*

Key files read for this section (all absolute):
- `D:\SMK\Android_apps\apps\taskmind\src\main\java\com\rajasudhan\taskmind\data\source\RecentDataScanner.kt`
- `…\data\source\understanding\{UnderstandingPipeline,RoutingLlmProvider,CloudLlmProvider,OnDeviceLlmProvider,OnDeviceEngine,MediaPipeEngine,NanoEngine,LiteRtLmEngine,ExtractionHeuristics,SystemPrompt,LlmModels,LlmProvider,MediaInput,AskEngine,AskQuery,AskModels,AskPrompt,MagicBreakdown,NearDuplicate}.kt`
- `…\data\source\transcription\{Transcriber,VoskTranscriber,WhisperTranscriber,WhisperEngine,AudioDecoder,TranscriptDiff}.kt`
- `…\data\source\ocr\OcrEngine.kt`
- `…\data\source\embedding\{SemanticIndex,HashingEmbedder,Vectors,Embedder}.kt`
- `…\data\source\{NaturalDate,RejectionLearner,RecordingPaths,AppUsageCollector}.kt`
- `…\data\source\email\{GmailCollector,GmailAuth,GmailTextExtractor,GmailApi,GmailModels}.kt`
- `…\data\model\Suggestion.kt`

---

# 6. Runtime Behavior & Key Scenarios

This section traces five representative end-to-end flows through the real classes that implement them. Every flow shares one invariant that defines TaskMind's posture: **nothing an automated path extracts is ever committed to a `Note` directly.** Extraction produces only a pending `Suggestion` in the Inbox (unapproved, carrying a confidence score); the user's explicit Approve is the sole gate that turns a suggestion into durable state (a `Note`, an alarm, a calendar event). Because there is no backend server, every participant below is an in-process Android component wired by Hilt.

## 6.1 Scenario 1 — Background scan produces an Inbox suggestion

The periodic scan is battery-scheduled work, not a persistent daemon. `TaskMindApp.scheduleScan` enqueues a **unique periodic** `DataCollectionWorker` (`PERIODIC_WORK_NAME = "taskmind_periodic_scan"`) at `SettingsManager.scanFrequencyMinutes` (default `DEFAULT_SCAN_FREQUENCY_MINUTES = 30`), constrained by `setRequiresBatteryNotLow(true)`. When it fires, `RecentDataScanner.scanIncremental()` computes a forward-only window from the `lastScanAt` watermark (capped at 24 h; first-ever run looks back only 15 min), scans each **enabled** source since the later of that window and the source's own `enabledAt` stamp, then advances the watermark. Each source's rows are isolated with `runCatching` so one failure never aborts the batch, and each item flows through `UnderstandingPipeline.processText`, which pre-filters obvious noise (`ExtractionHeuristics.isLikelyNoise`) before spending an LLM call.

```mermaid
sequenceDiagram
    autonumber
    participant WM as WorkManager
    participant W as DataCollectionWorker
    participant RDS as RecentDataScanner
    participant SM as SourceManager (DataStore)
    participant UP as UnderstandingPipeline
    participant LLM as RoutingLlmProvider
    participant EG as EgressLogger
    participant DAO as TaskMindDao (SQLCipher)
    participant N as SuggestionNotifier

    WM->>W: doWork() (battery-not-low)
    W->>RDS: scanIncremental()
    RDS->>SM: read toggles + enabledAt + processed-id ledgers
    loop each enabled source since watermark
        RDS->>RDS: query ContentResolver (SMS/Call/Media) or Gmail/AppUsage
        RDS->>UP: processText(source, body)
        alt isLikelyNoise(body)
            UP-->>RDS: return (skipped, no LLM call)
        else actionable
            UP->>LLM: generate(systemInstruction, userMessage)
            alt cloud route
                LLM->>EG: record("generativelanguage.googleapis.com", purpose)
                LLM-->>UP: JSON items (schema-constrained)
            else on-device route
                LLM-->>UP: JSON items (nothing leaves device)
            end
            UP->>UP: tryParse -> score (rejection penalty) -> dedup [insertMutex]
            UP->>DAO: insertSuggestion(status="pending")
            UP->>N: notifyPending()
        end
    end
    RDS->>SM: lastScanAt = now
    W->>DAO: retention cleanup (purge actioned suggestions, deleteNotesOlderThan)
    W-->>WM: Result.success()
```

Notable correctness details grounded in the code: the check-then-insert region runs under `UnderstandingPipeline.insertMutex` so the live `SmsObserver` and the periodic scan cannot both pass dedup and insert twins for the same SMS; the slow LLM call is deliberately held **outside** the lock. Missed calls bypass the LLM entirely via `addCallback` (the model tends to drop them as non-actionable). The manual "pull to refresh" in `InboxViewModel.refreshRecentData()` calls the *same* `scanIncremental()` and advances the *same* watermark, so manual and background scans never double-process or leave a gap.

## 6.2 Scenario 2 — Approve a suggestion → Note + reminder + calendar

Approval is the one write path that produces durable user state, and it is centralized in `SuggestionApprover.approve` precisely so the Inbox UI, the notification quick-actions, and share-capture all share exactly one definition of "approve." `InboxViewModel.approveSuggestion` invokes it and records an `undo` closure that re-pends the suggestion and deletes the new note.

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant IVM as InboxViewModel
    participant SA as SuggestionApprover
    participant DAO as TaskMindDao (SQLCipher)
    participant SI as SemanticIndex
    participant AS as AlarmScheduler
    participant GEO as GeofenceManager
    participant CM as CalendarMirror
    participant RL as RejectionLearner

    U->>IVM: approveSuggestion(suggestion, addCalendar)
    IVM->>SA: approve(suggestion, durationMinutes, addCalendar)
    SA->>DAO: updateSuggestion(status="approved")
    SA->>RL: recordApproval(source)  %% forgives a prior down-rank
    SA->>DAO: insertNote(Note from suggestion) -> noteId
    SA->>SI: index(noteId, title, summary)  %% embed for Ask + dedup
    opt location present
        SA->>SA: placeGeocoder.geocode(place)
        SA->>DAO: updateNoteLocation(coords, radius)
        opt ACCESS_BACKGROUND_LOCATION granted
            SA->>GEO: add(noteId, lat, lng, 150m)
        end
    end
    alt type=reminder AND dueTime != null
        SA->>AS: schedule(noteId, date, time, recurrence, anchor)
        AS-->>SA: armed occurrence (may advance a stale slot)
        SA->>DAO: updateNoteDueDate(armed)
        opt addCalendar AND Calendar source enabled
            SA->>CM: insert(title, body, armed, time, duration) -> eventId
            SA->>DAO: updateNoteCalendarEventId(eventId)
        end
    else type=todo AND dueDate != null
        opt addCalendar
            SA->>CM: insert(title, body, dueDate, null, duration)
        end
    end
    SA-->>IVM: noteId (for undo)
    IVM->>IVM: lastUndo = re-pend suggestion, then deleteNoteById(noteId)
```

Correctness details: the calendar event is mirrored onto the *same* occurrence the alarm landed on (`schedule()` may advance a recurring reminder past a past-due slot), and `CalendarMirror` gates itself on the Calendar source toggle, so approval never writes to the system calendar unless the user opted that source in. A list-like to-do is decomposed into a persisted checklist (`Checklist.derive`). Geofences are only registered when `ACCESS_BACKGROUND_LOCATION` is granted (see §7).

## 6.3 Scenario 3 — Connect Gmail (OAuth) and scan mail

Gmail uses the legacy per-account `GoogleAuthUtil` token surface (not the newer Identity Authorization API, which returned a persistent `INTERNAL_ERROR` for the restricted `gmail.readonly` scope on this project). The app matches the Google Cloud Android OAuth client by **package + signing SHA-1** — no client secret is embedded. The access token is fetched on demand and **never persisted**; only the connected account email is stored, in EncryptedSharedPreferences.

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant SRC as SourcesScreen
    participant GA as GmailAuth
    participant CH as AccountManager chooser
    participant GAU as GoogleAuthUtil (Play Services)
    participant EG as EgressLogger
    participant SET as SettingsManager (EncryptedSharedPrefs)
    participant RDS as RecentDataScanner
    participant GC as GmailCollector

    U->>SRC: Connect Gmail
    SRC->>GA: accountChooserIntent()
    GA->>CH: newChooseAccountIntent()
    CH-->>U: pick account
    U-->>SRC: chosen email (accountFromChooser)
    SRC->>GA: authorize(email)
    GA->>EG: record("oauth2.googleapis.com", "Gmail access token")
    GA->>GAU: getToken(account, "oauth2:gmail.readonly")
    alt grant exists
        GAU-->>GA: Authorized(accessToken)
        GA->>SET: addGmailAccount(email)
    else consent required
        GAU-->>GA: UserRecoverableAuthException(intent)
        GA-->>SRC: NeedsConsent(intent)
        SRC-->>U: launch consent UI
        U-->>SRC: granted -> re-call authorize(email)
    else hard failure
        GAU-->>GA: GoogleAuthException(status)
        GA->>GA: probeBasicScope() + classify -> authGuidance
        GA-->>SRC: Error(actionable guidance)
    end

    Note over RDS,GC: later, during a scan (source enabled)
    RDS->>GA: silentAccessToken(email)
    GA-->>RDS: token (or null -> skip this account)
    RDS->>GC: fetchRecentPrimary(token, since, processedIds)
    alt token rejected mid-use (Gmail 401)
        GC-->>RDS: Unauthorized
        RDS->>GA: invalidate(token)  %% clears poisoned GMS cache
        RDS->>GA: silentAccessToken(email) -> fresh
        RDS->>GC: fetchRecentPrimary(fresh, since, processedIds) (retry once)
    end
    RDS->>RDS: pipeline.processText per email, addProcessedEmailId
```

The hard-failure path is a deliberate diagnostic: `probeBasicScope` requests a basic (non-restricted) `userinfo.email` scope for the same account and classifies the result (`OK`/`RECOVERABLE`/`BROKEN`/`UNREACHABLE`) to distinguish "the restricted Gmail scope is blocked for this account" (Advanced Protection / supervised / unverified app) from "the device sign-in is broken." All account identifiers are masked (`GmailAuth.mask`) before logging. `disconnect` best-effort clears the local token and POSTs the Google revoke endpoint (also egress-logged).

## 6.4 Scenario 4 — Ask a question (on-device RAG over saved Notes)

Ask is intentionally architected so a small on-device model **cannot hallucinate items**: the LLM's only job is to classify the utterance into a tiny `AskIntent` (`query`/`create`); retrieval, ranking, and answer phrasing are fully deterministic against Room rows. Retrieval is a hybrid of lexical match plus semantic similarity from the on-device `SemanticIndex` (`HashingEmbedder` vectors, cosine similarity above `SEARCH_FLOOR = 0.35`).

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant AVM as AskViewModel
    participant AE as AskEngine
    participant LLM as RoutingLlmProvider
    participant SI as SemanticIndex
    participant EMB as HashingEmbedder
    participant DAO as TaskMindDao (SQLCipher)

    U->>AVM: ask(utterance)
    AVM->>AE: ask(utterance)
    AE->>LLM: generateIntent(AskPrompt, utterance)
    LLM-->>AE: {action, type?, tag?, window?, status?, keyword?, text?}
    alt action=create with text
        AE->>AE: pipeline.processText("Ask TaskMind", text) -> pending Suggestion
        AE-->>AVM: "Added … to your Inbox to review."
    else query with usable slots
        AE->>DAO: getActiveNotes()/getCompletedNotes()
        AE->>AE: filter via AskQuery.matches(note, intent, today)
        AE-->>AVM: deterministic count + matched Notes
    else search (unparseable / textless / no slots)
        AE->>SI: scores(utterance, floor=0.35)
        SI->>EMB: embed(query)  %% on-device, no network
        SI->>DAO: getAllEmbeddings() -> cosine
        SI-->>AE: noteId -> similarity
        AE->>DAO: getActiveNotes()
        AE->>AE: rank = lexical hits first, then semantic
        AE-->>AVM: "Here's what I found:" + real Note rows
    end
    AVM-->>U: answer + result cards
```

When cloud is the selected engine (or on-device falls back to it), only the short utterance is sent for **classification** — never the note corpus, which is retrieved and ranked locally. `AskViewModel.onDeviceEngine` surfaces the honest route so the empty state promises on-device processing only when it is actually true (`RoutingLlmProvider.isOnDeviceEffective()`).

## 6.5 Scenario 5 — Share text (or an image) from another app

The Android Share sheet routes `ACTION_SEND` (`text/plain` or `image/*`) to the invisible `ShareTargetActivity`. Because sharing is a **capture-only** action that never displays existing data, it deliberately bypasses the biometric lock (the lock still guards all *reads*); the shared payload is handed to `CaptureWorker` and processed off the UI thread, landing as a pending Inbox suggestion.

```mermaid
sequenceDiagram
    autonumber
    participant OA as Other app (share sheet)
    participant STA as ShareTargetActivity
    participant CW as CaptureWorker (WorkManager)
    participant OCR as OcrEngine (Tesseract)
    participant UP as UnderstandingPipeline
    participant LLM as RoutingLlmProvider
    participant DAO as TaskMindDao (SQLCipher)
    participant N as SuggestionNotifier

    OA->>STA: ACTION_SEND (text/plain or image/*)
    Note over STA: no reads -> biometric lock intentionally bypassed
    alt text/plain
        STA->>CW: enqueue("Shared", subject+body)
    else image/*
        STA->>STA: copy Uri into app-private cache (holds read grant)
        STA->>CW: enqueueImage("Shared image", file)
    end
    STA-->>OA: Toast "Added to TaskMind for review.", finish()
    CW->>OCR: recognize(image) (image path only, on-device)
    CW->>UP: processText(source, text)
    UP->>LLM: generate(...)  %% on-device or cloud per Settings
    UP->>DAO: insertSuggestion(status="pending") [insertMutex]
    UP->>N: notifyPending()
```

The same `CaptureWorker → UnderstandingPipeline` path backs Quick Capture (type/voice), the Quick Settings tile, the home-screen widget, and wrist captures from the paired watch (`WearCaptureListenerService`) — one ingestion pipeline, one review-and-approve gate.

---

# 7. Security Architecture

TaskMind's security model is shaped by one structural fact: **there is no first-party backend.** The phone is the "backend," so the classic server attack surface — auth servers, session stores, multi-tenant databases, load balancers, an admin plane — simply does not exist, and neither do the controls that defend it. The security investment instead concentrates on (a) protecting a large, sensitive, single-tenant corpus **at rest on one device**, (b) minimizing and auditing the only egress (optional Google calls), and (c) gating local reads behind device authentication.

## 7.1 Threat model

| Asset | Where it lives | Primary threats |
|---|---|---|
| Extracted notes, suggestions, embeddings, rejection-learning | SQLCipher DB (`taskmind_db`) | Device theft/loss; offline disk extraction; another app reading app storage |
| Secrets: cloud LLM key, connected Gmail emails, DB key, egress log | EncryptedSharedPreferences (`secret_shared_prefs`) | Same as above; key exfiltration |
| Source-state: toggles, `enabledAt` stamps, processed-id watermarks | Jetpack DataStore | Tampering to force re-scan / replay |
| Raw signals (SMS, call log, notifications, mail, media) | The OS content providers (read-only) | Over-collection; leakage via egress |
| Data in transit | HTTPS to Google endpoints only | Network eavesdropping / MITM |

**Adversaries considered**

- **Device thief / someone with physical access.** Mitigated by encryption at rest (SQLCipher, §7.3) plus the optional biometric app lock (§7.2). A cold-boot disk image yields only ciphertext; the DB key is a random 32-byte value held in Keystore-backed EncryptedSharedPreferences, not derivable off-device.
- **A malicious co-installed app.** Android's app-sandbox isolates TaskMind's files; the DB and prefs are additionally encrypted, so even a sandbox escape yields ciphertext. TaskMind holds several **restricted permissions** (`READ_SMS`, `READ_CALL_LOG`) — these are its most sensitive inputs and are the reason public Play distribution is gated (they are default-handler-only). Every source is opt-in.
- **Network eavesdropper / MITM.** All outbound calls are HTTPS (§7.3). Content only ever leaves the device on an explicit, user-selected cloud route, and every such departure is recorded in the egress audit log.
- **The cloud LLM provider (Google) as an honest-but-curious processor.** This is the *only* third party that can ever see user content, and only when the user chooses the cloud engine (or an on-device fallback with a configured key). The UI never claims "on-device" when work actually egresses (`isOnDeviceEffective()` / `mediaEgressesToCloud()`), so the user's trust decision is always honestly labeled.

**On-device equivalents of enterprise controls.** Where a server-centric control does not apply, the on-device analogue is: no server sessions → device biometric/credential gate; no WAF/network perimeter → the OS permission model + per-source opt-in toggles; no server-side audit/SIEM → the local `EgressLogger` audit trail; no server DR/multi-region → encrypted local backup (`BackupManager`/`BackupCrypto`) plus a daily plain-JSON auto-snapshot safety net.

## 7.2 Authentication & authorization

**Biometric app lock (local read gate).** The lock is optional (Settings → Security) and is enforced in `MainActivity` via `androidx.biometric`. `promptBiometric` requires `BIOMETRIC_STRONG` **or** `DEVICE_CREDENTIAL` (PIN/pattern/password), so a user without enrolled biometrics can still authenticate. The session re-locks on every `ON_STOP` (`isAuthenticated = false`), so returning to the app always re-authenticates. Two guards keep the lock from being either bypassable or a self-inflicted lockout:

- `canEnforceLock()` (re-checked on every `ON_RESUME`) confirms an authenticator actually exists; if the user removes all credentials while backgrounded, the app must **not** lock, or the off-switch would be stranded behind an unsatisfiable prompt.
- `AppLock.expectResult()` / `shouldKeepUnlockedOnStop()` whitelists the single `ON_STOP` caused by a deliberate SAF document-picker round-trip (backup/restore/export). Without it, re-locking would tear down the picker's result callback and silently drop the write.

The lock gates **reads only**. Capture surfaces that display no existing data (`ShareTargetActivity`, the tile, the widget) intentionally bypass it so capture stays frictionless while the corpus stays protected.

**OAuth (Gmail).** Authorization to read mail uses per-account `GoogleAuthUtil` with the restricted scope `oauth2:https://www.googleapis.com/auth/gmail.readonly`. The app is matched to its Google Cloud OAuth client by **package name + signing SHA-1** (the committed debug keystore's fingerprint) — no client secret ships in the APK. Access tokens are **fetched on demand and never persisted**; only the account email is stored (encrypted). A mid-use `401` triggers a single `invalidate` + refresh + retry, so a revoked/expired token self-heals rather than silently starving the scan.

**No server sessions.** There is nothing to log in *to*. There are no bearer tokens, refresh cookies, CSRF concerns, or session fixation surfaces of a first-party backend. "Authorization" in this app is (1) the Android runtime-permission model for each raw signal, and (2) the app's own **per-source opt-in toggles** with forward-only `enabledAt` watermarks, so enabling a source captures only data arriving *after* consent, never historical backfill.

## 7.3 Data protection

**At rest — SQLCipher (AES-256).** The Room database is opened through `net.zetetic:sqlcipher-android`'s `SupportOpenHelperFactory` (SQLCipher 4, 16 KB-page-aligned native libs). The key is a random 32-byte value from `SecureRandom`, base64-stored under `db_key` in EncryptedSharedPreferences. `DatabaseModule.provideDatabase` forces the SQLCipher open eagerly so a key/DB mismatch surfaces immediately, and — critically — a DB that cannot be opened with any known key is **quarantined** (renamed aside, never deleted) so its bytes survive for recovery, rather than risking silent data loss.

**At rest — EncryptedSharedPreferences.** `secret_shared_prefs` is built on a `MasterKey` (`AES256_GCM`) with `PrefKeyEncryptionScheme.AES256_SIV` for keys and `PrefValueEncryptionScheme.AES256_GCM` for values. It holds the DB key, the cloud LLM key, connected Gmail emails, and the egress log — so **the audit log is itself encrypted at rest.** If the Keystore master key can no longer decrypt the store (e.g. a keystore reset), the DI provider resets the corrupt store + master key and quarantines the now-unreadable DB, keeping the app bootable.

**In transit — TLS.** All network I/O goes through a single OkHttp client (`NetworkModule.provideOkHttpClient`); every endpoint is HTTPS: `oauth2.googleapis.com` (tokens), `gmail.googleapis.com` (mail fetch via Retrofit), `generativelanguage.googleapis.com` (Gemini), and the Maps SDK. There are no cleartext endpoints. Source signals (SMS/call/media/notifications) are read from local OS content providers and never leave the device on the on-device route.

**Egress logging + masking.** `EgressLogger.record(host, purpose)` writes **metadata only** — host, purpose, timestamp — and *never the content that was sent* (capped at the last 100 events). It is invoked at each real departure: `GmailAuth` logs token fetch/invalidate/revoke against `oauth2.googleapis.com`; `CloudLlmProvider` logs each Gemini call (text extraction, task breakdown, ask-intent, vision) against `generativelanguage.googleapis.com`. Account emails are masked (`GmailAuth.mask` → first char + domain) everywhere they touch logs.

**Backups.** `BackupManager` seals the *entire* profile — the SQLCipher DB, its key, the decrypted prefs snapshot, and the DataStore snapshot — inside a single AES-256-GCM envelope keyed by a **PBKDF2-derived passphrase** (`BackupCrypto`). Nothing readable leaves the device without the passphrase, which is not recoverable. Restores stage-and-verify against the bundled key before an atomic swap, with a pending-key slot that makes an interrupted restore recoverable rather than data-losing.

## 7.4 Compliance & auditing (data-subject rights, on-device)

Because the phone is the backend, the user is simultaneously the data subject and the de facto data controller; the only third-party processor that can ever receive content is Google's cloud LLM, and only on an explicit, user-selected, audited route. The app operationalizes GDPR-style rights **locally**, without any server request:

| Right | Implementation | Code |
|---|---|---|
| Access / portability | Export all notes to a user-chosen file as JSON, and as open-format Markdown (a readable "exit ramp" that survives even without TaskMind) | `SettingsViewModel.exportNotesToUri`, `exportNotesAsMarkdownToUri` |
| Erasure ("right to be forgotten") | One action wipes notes, pending suggestions, the on-device rejection-learning, all settings/secrets, and the source-state DataStore | `SettingsViewModel.deleteAllData` → `dao.deleteAllNotes/deleteAllSuggestions/deleteAllRejectedPatterns`, `clearSettings()`, `dataStore.edit { clear() }` |
| Data minimization / storage limitation | Forward-only incremental scan from a watermark (no historical backfill); per-source opt-in; configurable retention that purges old notes and actioned suggestions each cycle | `RecentDataScanner.scanIncremental`, `DataCollectionWorker.runRetentionCleanup` → `deleteNotesOlderThan(cutoff)` |
| Transparency of processing | Honest engine labels (on-device vs cloud) surfaced in the Inbox/Ask/quick-capture UI, never a hardcoded claim | `RoutingLlmProvider.isOnDeviceEffective()`, `mediaEgressesToCloud()` |

**Audit trail.** The "Data Egress" screen renders the `EgressLogger.events` flow — a chronological, metadata-only record of every outbound call (host + purpose + time), giving the user a verifiable, human-readable answer to "what has left my device, and why." Combined with account masking and the metadata-only rule, this is the on-device analogue of a server-side audit log, scoped to the single tenant who owns it. `EgressLogger.clear()` lets the user reset the audit trail as part of an erasure.

**Residual compliance gaps (honest disclosure).** The audit log is bounded to 100 events (older egress ages out); the cloud LLM key is currently a hardcoded/"express" key rather than a per-user secret; and `READ_SMS`/`READ_CALL_LOG` remain restricted permissions that block public Play distribution. These are tracked as known tech debt rather than represented as solved.

---

# 8. Deployment & Infrastructure

### 8.1 Infrastructure model

TaskMind has **no first-party backend infrastructure**. The Android device *is* the backend: ingestion, LLM routing, the encrypted Room/SQLCipher database, the semantic index, schedulers, and every background worker run in-process on the handset. Classic enterprise infrastructure concepts therefore do not apply, and are replaced by on-device equivalents:

| Enterprise concept | Applies? | On-device equivalent in TaskMind |
|---|---|---|
| Kubernetes / container orchestration | No | Android process + Hilt DI graph; WorkManager as the "scheduler" |
| Load balancers / autoscaling | No | Single device, single user; no horizontal scale surface |
| Multi-region / HA replicas | No | One device; the paired Wear OS module is a satellite view, not a replica |
| Application servers | No | `MainActivity` (single-Activity Compose host) + `TaskMindForegroundService` |
| Managed database (RDS, etc.) | No | Room + SQLCipher file at `getDatabasePath("taskmind_db")`, WAL mode |
| Object storage | No | App-private `filesDir` / `noBackupFilesDir` (models, snapshots) |
| Server-side secrets manager | No | Android Keystore-backed `EncryptedSharedPreferences` (`SettingsManager`) |
| CDN | No | Public model hosts (Hugging Face, Vosk, Tesseract) fetched once via `ModelDownloader` |

The only outbound network dependencies — the closest thing to external "infrastructure" — are **Google-hosted APIs**, all optional and opt-in:

| External dependency | Purpose | Invoked by | Failure mode |
|---|---|---|---|
| `generativelanguage.googleapis.com` (Gemini 2.5 Flash) | Cloud LLM extraction / breakdown / intent / vision | `CloudLlmProvider` | Returns schema-shaped empty result; scan continues |
| Google OAuth + Gmail API (`gmail.readonly`) | Email source | `GmailAuth` / `GmailCollector` | Source skipped; 401 triggers one silent token refresh + retry |
| Google Maps SDK | Map tiles for location reminders | Manifest `com.google.android.geo.API_KEY` | Map renders blank if key absent |
| Google Play Services (Location, Wearable, Auth) | Geofences, watch Data Layer, sign-in | `GeofenceManager`, `WearSync` | Feature degrades locally |
| Model hosts (HF / Vosk / Tesseract) | One-time on-device model download | `ModelDownloader` | Download fails; on-device engine stays unavailable |

Every reachable outbound host is recorded (metadata only) by `EgressLogger` — see §9.2.

### 8.2 Environments / build variants

"Environments" map to Gradle build variants and distribution channels, not deployment tiers.

| Variant | Signing | Minify/optimization | Distribution channel | Notes |
|---|---|---|---|---|
| `debug` | Committed `apps/taskmind/debug.keystore` (store/key pass `android`, alias `androiddebugkey`), overriding the per-machine `~/.android/debug.keystore` | Debuggable; no shrinking | Rolling `debug-latest` GitHub pre-release; `installDebug` to a connected phone | The stable, shared signature is what lets in-place updates preserve on-device data |
| `release` | **None configured** — `signingConfigs` only defines `debug`; the release variant is unsigned | `optimization { enable = false }` — R8/shrinking explicitly off | **No channel** — never built for distribution | A hard Play-Store blocker (see §12) |

Key `defaultConfig` facts (from `apps/taskmind/build.gradle.kts`): `applicationId = com.rajasudhan.taskmind`, `versionCode 6` / `versionName "5.1"`, `minSdk 35` (Android 15), `targetSdk 36`, `compileSdk 37`, Java 11 source/target, `ndkVersion 27.0.12077973`, `ndk.abiFilters = ["arm64-v8a"]` only, CMake `-std=c++17`. `buildConfig = true` exposes `VERSION_NAME`/`VERSION_CODE` to the Settings footer.

### 8.3 CI/CD pipeline

CI/CD is **GitHub Actions**; there is no server deploy step — "deploy" means publishing an APK to a GitHub Release or pushing it to a phone over ADB. Three workflows exist under `.github/workflows/`.

**`android.yml` — Android CI (build + test gate).** Trigger: push + PR to `main`. Runner: `ubuntu-latest`. Concurrency `ci-${{ github.ref }}` with `cancel-in-progress`. Permissions: `contents: read`.

| # | Stage | Detail |
|---|---|---|
| 1 | Checkout | `actions/checkout@v4` |
| 2 | JDK 21 | `temurin` via `setup-java@v4` |
| 3 | Android SDK | `android-actions/setup-android@v3` |
| 4 | Gradle | `gradle/actions/setup-gradle@v4` |
| 5 | Cache NDK | key `ndk-${os}-27.0.12077973` (~1 GB first-configure cost) |
| 6 | Cache native build | `apps/taskmind/.cxx`, exact key over `hashFiles(cpp/**, build.gradle.kts)` — no restore-keys, so any native-input change rebuilds cleanly (whisper.cpp compile + FetchContent clone is the slowest step, #240) |
| 7 | `chmod +x ./gradlew` | — |
| 8 | Unit tests | `:apps:taskmind:testDebugUnitTest --stacktrace` (~707 JVM tests) |
| 9 | Assemble | `:apps:taskmind:assembleDebug --stacktrace` (packaging check) |
| 10 | Test report on failure | `upload-artifact@v4`, `if: failure()`, `continue-on-error`, 7-day retention |

CI deliberately **does not** upload the APK as an Actions artifact — the ~100 MB APK on every push+PR churned ~28 GB and exhausted the artifact quota; distribution is handled by the release workflow instead (Release storage, not the artifact quota).

**`release-apk.yml` — Publish debug APK (OTA distribution).** Trigger: push to `main` + `workflow_dispatch`. Permissions: `contents: write`. Concurrency `release-apk`. Builds `assembleDebug`, then deletes and recreates the `debug-latest` tag/pre-release pointing at the current SHA and re-attaches `taskmind-debug.apk`. This keeps a **stable download URL** (`…/releases/download/debug-latest/taskmind-debug.apk`) and, because every build shares the committed debug signature, installs update in place and preserve data.

**`install-to-phone.yml` — Install to phone (self-hosted).** Trigger: `workflow_dispatch` (optional `launch` boolean). Runner: `[self-hosted]` (the developer's laptop). Resolves the Android SDK + `adb`, writes `local.properties` (gitignored), verifies an authorized device is attached, runs `:apps:taskmind:installDebug`, then optionally launches via `monkey`. A phone carrying an install signed with a *different* key fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; the documented fix is back-up-in-app → uninstall → reinstall.

No Dependabot/other automation is configured; `.github/` contains only `workflows/`.

### 8.4 On-device model provisioning

On-device engines are not bundled in the APK — they are fetched on demand by `ModelDownloader` (OkHttp, 30 s connect / 60 s read timeout). It streams into a `.part` temp then atomically renames (a half-download never looks complete), reports 0–100 % progress, supports an optional `Authorization: Bearer` token for license-gated hosts (e.g. Hugging Face Gemma), and **records the download host in the egress ledger** so the privacy guarantee stays honest. Targets: Vosk zip, Tesseract `traineddata`, whisper.cpp GGML model. Per the model constraints on record, the practical on-device text engine is MediaPipe **Gemma 3 1B int4** (`model.task` in internal `filesDir`); cloud Gemini 2.5 Flash remains the quality path.

---

# 9. Observability & Operations

### 9.1 No central telemetry

**There is no central telemetry, crash reporting, APM, or metrics backend.** This is a deliberate consequence of the privacy-first, no-server posture: no Crashlytics/Sentry, no analytics SDK, no remote log sink. All observability is **on-device and user-facing**. The operational trade-off — accepted explicitly — is that the operator (developer) has *zero* fleet-wide visibility into crashes, worker health, or extraction quality on real user devices; every diagnosis happens on a device in hand (logcat) or through the in-app self-diagnostics below.

### 9.2 On-device diagnostics — the Reliability Doctor

`ReliabilityChecker` (`@Singleton`, read-only, no mutations) runs six live health checks and returns them worst-status-first (`FAIL` > `WARN` > `OK`), each carrying a deep-link `Intent` into the exact system screen that fixes it:

| Check id | Title | FAIL/WARN condition | Remediation deep-link |
|---|---|---|---|
| `listener` | Notification access | Listener package not enabled → **FAIL** | `ACTION_NOTIFICATION_LISTENER_SETTINGS` |
| `notifications` | Notifications allowed | App notifications blocked → **FAIL** | `ACTION_APP_NOTIFICATION_SETTINGS` |
| `reminder_channel` | Reminders make a sound | Reminders channel importance downgraded below DEFAULT → **WARN** | `ACTION_CHANNEL_NOTIFICATION_SETTINGS` |
| `exact_alarms` | Exact alarms | `canScheduleExactAlarms()` false → **WARN** | `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |
| `battery` | Battery optimization | Not battery-exempt → **WARN** (biggest reliability lever on Xiaomi/OnePlus/vivo/Samsung) | `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (Play-policy-safe list) |
| `service` | Background watcher | `TaskMindForegroundService.isRunning` false → **WARN** | (reopening the app restarts it) |

The Doctor also schedules a **real exact alarm a few seconds out** targeting `ReliabilityTestReceiver`, which stamps `firedAt`; the Doctor measures the round-trip end-to-end (and, if the alarm never arrives, surfaces that something — usually an OEM battery-killer — is dropping alarms). The branching logic is kept out of the ViewModel specifically to be unit-testable under Robolectric.

### 9.3 Data Egress audit

`EgressLogger` (`@Singleton`) records **every** outbound network call as `{timestamp, host, purpose}` — **metadata only, never content** — persisted in `EncryptedSharedPreferences` (the audit log is itself encrypted at rest), capped at `MAX_EVENTS = 100` (newest-first), and exposed as a `StateFlow` to the in-app "Data Egress" screen. Cloud LLM calls, cloud vision extraction, and even on-device model downloads all write an entry, keeping the "no data has left this device" claim verifiable rather than asserted.

### 9.4 Runtime logging & worker introspection

- **Logcat** is the only low-level trace surface: `RecentDataScanner` tags (`RecentDataScanner`) log per-source counts (emails fetched, chars OCR'd/transcribed, Whisper second-pass usage) and per-row warnings; account emails are masked (`GmailAuth.mask`). Workers `printStackTrace()` on failure before returning `Result.retry()`.
- **WorkManager diagnostics**: worker health can be verified on-device by the WorkManager `REQUEST_DIAGNOSTICS` broadcast (dumps enqueued/running/finished work), the standard way to confirm background scans actually ran given there is no central telemetry.
- **AppFunctions QA**: the exposed `createTask`/`getItemsDueToday`/`snoozeItem` functions are verifiable via `cmd app_function list/execute` on-device.

### 9.5 Operational posture

Because there is no operator console, "operations" is largely user self-service (the Reliability Doctor + Egress screen + Reliability Doctor test alarm) plus developer-side device debugging. There is no on-call, no paging, no SLA instrumentation — appropriate for a single-user on-device app, but it means field failures are effectively invisible until a user reports them (a risk logged in §12).

---

# 10. Performance & Scalability

### 10.1 Scaling model

"Scalability" here is **per-device data volume and background workload**, not concurrent users or request throughput — there is exactly one user and no server. The system scales along three axes: (a) volume of incoming signals to scan per interval, (b) accumulated Notes for the semantic index/Ask, and (c) battery/thermal budget for background work and LLM inference. Server-scaling concepts (autoscaling, connection pools, sharding, rate-limit tiers) are **not applicable**.

### 10.2 Workload bounding — scan caps

Ingestion is explicitly bounded so a long-dormant or high-traffic device cannot flood the LLM or drain the battery:

| Bound | Value | Source | Purpose |
|---|---|---|---|
| Max look-back per scan | `MAX_LOOKBACK_MS` = 24 h | `RecentDataScanner` | A dormant app never re-scans months of history |
| First-ever scan window | `FIRST_RUN_LOOKBACK_MS` = 15 min | `RecentDataScanner` | Avoid an initial flood on install |
| SMS id-recovery per scan | `MAX_SMS_RECOVERY_PER_SCAN` = 200 | `RecentDataScanner` | Cap gap-recovery so a long dormancy can't flood the LLM |
| Live media observer window | last 5 min | `scanAudioRecent`/`scanImagesRecent` | Instant capture without re-scanning history |
| Egress log retention | 100 events | `EgressLogger` | Bounded memory/storage |
| Plaintext note snapshots kept | `MAX_SNAPSHOTS` = 7 | `SnapshotManager` | Bounded disk; rolling window |
| Default scan interval | 30 min (`DEFAULT_SCAN_FREQUENCY_MINUTES`); WorkManager floor 15 min | `SettingsManager` | Battery-friendly cadence, user-configurable |

Dedup ledgers (processed SMS/audio/image/email ids) and forward-only watermarks (`lastScanAt`, `lastProcessedSmsId`) ensure each item is understood **once**, so per-interval LLM cost is proportional to *new* signals, not total history.

### 10.3 Background workers & cadence

| Worker | Cadence / trigger | Unique name | Policy | Constraints / failure |
|---|---|---|---|---|
| `DataCollectionWorker` | Periodic, default 30 min | `taskmind_periodic_scan` | KEEP (launch) / UPDATE (settings change) | `setRequiresBatteryNotLow(true)`; `Result.retry()` on failure; also runs retention cleanup |
| `AutoSnapshotWorker` | Every 24 h, first run 03:00 local | `taskmind_auto_snapshot` | KEEP | Retry once on write failure |
| `DailyBriefWorker` | Every 24 h at user hour:minute (off by default) | `taskmind_daily_brief` | CANCEL_AND_REENQUEUE (settings) / KEEP (launch) | DST-safe initial-delay math |
| `WeeklyWinsWorker` | Every 7 days, Sunday, default 18:00 (off by default) | `taskmind_weekly_wins` | CANCEL_AND_REENQUEUE / KEEP | DST-safe |
| `RecurrenceDetectorWorker` | Every 3 days | `taskmind_recurrence_detector` | KEEP | Habit cadence changes slowly |
| `WearSyncWorker` | Periodic 30 min + one-shot on due-set change | `taskmind_wear_sync` / `taskmind_wear_sync_now` | KEEP / REPLACE | 1.5 s debounce coalesces bursts (e.g. Approve-All) |
| `CaptureWorker` | One-time, on share/quick-capture | (per-request) | enqueue | OCR/pipeline; `Result.retry()`; deletes source image only after consumption (idempotent retry) |

The `KEEP`-on-launch discipline is a deliberate performance/correctness choice: blindly re-enqueuing would cancel a Doze-deferred occurrence and roll it forward (silently skipping today's brief/this week's recap).

### 10.4 Latency & throughput profile

- **Cloud extraction** (`CloudLlmProvider`, Gemini 2.5 Flash, `temperature 0.1`, structured `responseSchema`): the fast + accurate path; latency dominated by a single HTTPS round-trip; per-row failures are isolated so one timeout can't abort a source's whole scan.
- **On-device extraction**: Gemma 3 1B is fast (~5–10 s) but low quality; larger models (Gemma 3n E4B ~2.5 min/extraction on GPU; AICore Gemini Nano not provisioned for third-party apps on the target Samsung) are impractical for background use. Implication: the on-device engine is a *privacy fallback*, and heavy background scanning realistically depends on the cloud route.
- **OCR/transcription** run on-device (Tesseract, Vosk, whisper.cpp second pass) off the main thread on `Dispatchers.IO`/`Default`; capture is a WorkManager job so the UI never blocks on inference.

### 10.5 Data-volume scaling

Notes accumulate in encrypted Room; retention is optional (`retentionDays`, 0 = keep forever, purged by `DataCollectionWorker`). The Ask index is an **on-device** `HashingEmbedder → NoteEmbedding` cosine-similarity search — linear in note count, with no external vector DB. For a personal corpus (thousands of notes) this is well within device budget; there is no sharding or ANN index, which is acceptable at single-user scale and flagged as future work if corpora grow large.

---

# 11. Reliability, Fault-Tolerance & Disaster Recovery

### 11.1 Reliability model

For an always-on, privacy-first assistant, reliability *is* uninterrupted background access — a revoked notification listener, an OEM battery-killer, a silenced channel, or missing exact-alarm permission all silently break the core "catch it before you forget" promise. The system defends this on three fronts: continuous self-diagnosis (§9.1, the Reliability Doctor), resilient background scheduling, and disaster recovery via encrypted backup + a plaintext safety net.

### 11.2 Fault-tolerance patterns

| Pattern | Mechanism | Guarantee |
|---|---|---|
| Per-row failure isolation | `runCatching` around each SMS/call/email/media row in `RecentDataScanner` | One row's throw (network IOException, DB busy) never aborts the batch; call-log rows have no ledger, so this prevents permanent loss |
| Forward-only watermark | `lastScanAt` advanced *after* the scan; each source swallows its own errors | A deferred/failed run covers more ground next time rather than dropping the gap |
| SMS gap recovery | Contiguous `_ID` watermark (`lastProcessedSmsId`) + capped recovery pass | Messages older than the 24 h date clamp (app dead a long time) are still recovered, bounded at 200/scan |
| Idempotent capture | `CaptureWorker` deletes the source image only after `processText` consumes it | A `Result.retry()` re-OCRs the file instead of silently succeeding on a missing file |
| Crash-safe writes | `.part` temp + atomic rename in `ModelDownloader` and `SnapshotManager` | A crash mid-write never leaves a truncated model/snapshot |
| WAL-consistent backup | `BackupManager.checkpointWal()` (`wal_checkpoint(TRUNCATE)` with busy-retry) | The single-file DB copy is a complete snapshot, not stale |
| Atomic DB restore | Stage `.restore` file → verify it opens with the bundled key → `Files.move(ATOMIC_MOVE)` → promote key | A bad restore can never corrupt a working install; the DB on disk is only ever the complete old or complete new file |
| Crash-recoverable restore window | `DB_KEY_PENDING` slot parked before the swap; promoted on next launch by `DatabaseModule` | A process death between swap and key-commit is fully recoverable, not data loss |
| Newer-schema rejection | `newerSchemaRejectionMessage` refuses a backup from a newer app build up front | Room (which can't downgrade) never swaps in an unopenable DB after reporting success |

### 11.3 Background reliability & survivability

- **Foreground service**: `TaskMindForegroundService` returns `START_STICKY` and exposes `isRunning`; live `ContentObserver`s for audio/images and SMS/calendar follow source toggles in real time.
- **Exact alarms over WorkManager for time-critical delivery**: reminders use `AlarmScheduler`/`AlarmManager` exact alarms (not the 15-min-floor, Doze-deferrable WorkManager) so they fire on the minute.
- **Reboot survival**: `BootReceiver` re-arms alarms after reboot; `TaskMindApp.onCreate` eagerly creates the reminder notification channel so an alarm firing after reboot — before the app is reopened — still has a channel to post to, and re-arms all schedulers with `KEEP` so Doze-deferred jobs aren't destroyed.
- **Channel-importance defense**: reminders get a dedicated `IMPORTANCE_HIGH` channel because a channel's importance is fixed at creation and overrides builder priority.

### 11.4 Disaster recovery

Two independent, layered recovery mechanisms — deliberately separated because they survive *different* failures:

| Mechanism | Scope | Storage | Encryption | Trigger |
|---|---|---|---|---|
| Encrypted backup (`BackupManager` + `BackupCrypto`) | Whole profile: SQLCipher DB + its key + all `EncryptedSharedPreferences` (decrypted, re-sealed) + source-state DataStore | User-chosen file (`ACTION_CREATE_DOCUMENT`) | AES-256-GCM under PBKDF2-HMAC-SHA256 (150 000 iterations) from a user passphrase; envelope `TMBK1` | Manual (user-initiated) |
| Auto-snapshot (`SnapshotManager` + `AutoSnapshotWorker`) | **Notes only**, plaintext JSON, rolling last 7 | `noBackupFilesDir` (excluded from Google cloud backup & device transfer) | Plaintext on-device by design | Daily 03:00 |

The auto-snapshot exists specifically to survive the **one failure the encrypted DB cannot**: an Android Keystore reset that destroys the SQLCipher key and leaves the ciphertext permanently unopenable (a snapshot encrypted under that same Keystore would die with it). `restoreLatest()` re-inserts notes (ids reassigned) and best-effort re-arms their alarms/geofences; it never writes an *empty* snapshot (which would rotate away good ones after a wipe).

### 11.5 RTO / RPO (single-device terms)

| Metric | Encrypted backup | Auto-snapshot net |
|---|---|---|
| **RPO** (max data loss) | Time since the last *manual* backup — user-dependent, potentially large | ≤ 24 h (Notes only; suggestions/settings not covered) |
| **RTO** (time to recover) | Seconds: import file → decrypt → atomic swap → one mandatory app restart (`scheduleRestartAndExit`) | Seconds: re-insert notes in one transaction + re-arm |
| Failure covered | Device loss/replacement, accidental wipe, corruption | Keystore reset that bricks the encrypted DB |

Residual DR gaps: RPO for the encrypted backup is unbounded (no scheduled encrypted backup — only the plaintext note snapshot is automatic), and the auto-snapshot excludes suggestions, settings, secrets, and the semantic index. Both are logged as future work in §12.

---

# 12. Technical Debt, Risks & Future Work

### 12.1 Risk register

Severity reflects impact if unaddressed; likelihood reflects how likely it is to bite in the current trajectory.

| # | Risk | Severity | Likelihood | Mitigation / status |
|---|---|---|---|---|
| R1 | **Leaked cloud Gemini key** — an express-style token (`AQ.Ab8…`) was once hardcoded in `SettingsManager.kt` and committed; the default is now `""`, but the same key persists in **git history** and in on-device `EncryptedSharedPreferences` | **High** | High (key is public in history) | **Revoke/rotate in Google Cloud** (not yet done); move to per-user key entry only; consider history rewrite. Runtime already reads the key from encrypted prefs, never a constant |
| R2 | **Restricted permissions** — `READ_SMS` / `READ_CALL_LOG` are default-handler-only for public Play distribution | **High** | High (blocks Play release) | Gate SMS/call sources behind default-handler eligibility, or ship these sources disabled for a Play build; sources are already individually opt-in |
| R3 | **No release signing config** — the `release` variant defines no `signingConfig` and is never built for distribution | **High** | High (blocks any prod release) | Add a real release keystore in a secured secret store + a `release`/Play workflow |
| R4 | **16 KB page alignment unverified** — `libwhisper_jni.so` must be 16 KB-aligned for Android 15+ devices; no `packaging`/`jniLibs` alignment option is set in `build.gradle.kts` | **Medium** | Medium (target is Android 15+) | Verify with `check-elf-alignment`; set NDK/AGP 16 KB packaging; add a CI alignment check |
| R5 | **Single native ABI** — `abiFilters = ["arm64-v8a"]` only; no `armeabi-v7a`/`x86_64` | **Medium** | Medium | Add ABIs for wider device + emulator coverage before a public release (per-ABI whisper.cpp compile is the cost being deferred) |
| R6 | **Committed debug keystore drives distribution** — the shared `debug.keystore` (non-secret, passwords `android`) is what all install/OTA paths trust | **Medium** | Medium | Acceptable for sideload; must not carry into a Play/release identity — a device on the debug signature can't be updated by a differently-signed release without a data-wiping reinstall |
| R7 | **No central crash/telemetry** — field failures are invisible until a user reports them | **Medium** | Medium | Documented trade-off of the privacy posture; consider an opt-in, on-device-scrubbed diagnostic export |
| R8 | **OEM background suppression** — aggressive battery managers freeze the foreground watcher and drop alarms | **Medium** | High on IN-market devices | Reliability Doctor surfaces + deep-links the battery-exemption and exact-alarm fixes; exact alarms + `START_STICKY` + `BootReceiver` re-arm |
| R9 | **On-device LLM impractical for quality** — Gemma 1B low-accuracy, E4B too slow, AICore Nano not provisioned for third-party apps on the target device | **Medium** | High | Cloud Gemini 2.5 Flash is the real quality path; UI already labels the effective route honestly (`isOnDeviceEffective`), avoiding a false "on-device" claim |
| R10 | **Unbounded encrypted-backup RPO** — only the plaintext note snapshot is automatic; a full encrypted backup is manual | **Medium** | Medium | Offer a scheduled encrypted backup to a user-chosen `DocumentFile` tree |
| R11 | **Very high minSdk (35 / Android 15)** — small addressable install base | **Low–Medium** | High | Intentional for the API surface used; lower `minSdk` if broader reach is prioritized |
| R12 | **Auto-snapshot excludes non-note data** — suggestions, settings, secrets, embeddings not in the 7-day net | **Low** | Medium | Documented; encrypted backup covers the rest, but only when the user runs it |

### 12.2 Play-Store readiness blockers (consolidated)

The app is **not** production-releasable today. Blockers, in priority order: **R3** (no release signing) → **R2** (restricted-permission policy) → **R1** (leaked key must be rotated before any wider exposure) → **R4/R5** (16 KB alignment + single ABI for modern-device compatibility). There is currently **no Play production track** — distribution is sideload-only via the `debug-latest` release.

### 12.3 Technical debt

- **Cloud coupling for quality**: despite the on-device framing, useful background extraction leans on the cloud route (R9); the on-device engine is realistically a private fallback, not a peer.
- **Single-module app graph**: `settings.gradle.kts` reserves `core/` for shared modules but everything lives in `:apps:taskmind`; extraction of `core:llm`/`core:security`/`core:designsystem` is deferred until a second app reuses it.
- **Semantic index is linear**: cosine similarity over all `NoteEmbedding`s with no ANN structure — fine at personal scale, debt at large corpora.
- **Release variant has shrinking disabled** (`optimization.enable = false`), so no R8/minification baseline exists for a real release.
- **CI does not build the Wear module or run instrumented tests**: `android.yml` runs only `:apps:taskmind:testDebugUnitTest` + `assembleDebug`; `:apps:taskmind-wear` and `androidTest` (Espresso/Compose-on-device) are unguarded by CI.

### 12.4 Future work

1. **Rotate the leaked key and remove it from history**; require the user to supply their own cloud key.
2. **Add a signed release track + Play workflow**, and split a Play build that ships SMS/call-log sources disabled to clear the restricted-permission policy.
3. **Guarantee 16 KB alignment and add ABIs** (`armeabi-v7a`, `x86_64`) with a CI alignment gate.
4. **Scheduled encrypted backup** to close the RPO gap (R10), extending the auto-snapshot net beyond notes.
5. **Optional, privacy-preserving diagnostics export** to compensate for the absence of central telemetry (R7).
6. **Broaden CI** to build the Wear module and run instrumented/UI tests on an emulator matrix.
7. **Extract shared `core/` modules** once a second app materializes, per the monorepo's stated intent.

---

Files read to ground the above (all absolute): `D:\SMK\Android_apps\.github\workflows\android.yml`, `release-apk.yml`, `install-to-phone.yml`; `D:\SMK\Android_apps\apps\taskmind\build.gradle.kts`; `D:\SMK\Android_apps\settings.gradle.kts`; and under `D:\SMK\Android_apps\apps\taskmind\src\main\java\com\rajasudhan\taskmind\`: `TaskMindApp.kt`, `data\source\DataCollectionWorker.kt`, `ReliabilityChecker.kt`, `ReliabilityTestReceiver.kt`, `ModelDownloader.kt`, `AutoSnapshotScheduler.kt`, `AutoSnapshotWorker.kt`, `DailyBriefScheduler.kt`, `WeeklyWinsScheduler.kt`, `RecurrenceDetectorScheduler.kt`, `WearSyncScheduler.kt`, `BackupManager.kt`, `BackupCrypto.kt`, `SnapshotManager.kt`, `EgressLogger.kt`, `RecentDataScanner.kt`, `CaptureWorker.kt`, `TaskMindForegroundService.kt`, `SettingsManager.kt`, `understanding\CloudLlmProvider.kt`, `understanding\RoutingLlmProvider.kt`, `src\main\cpp\CMakeLists.txt`. The leaked-key detail is corroborated by the on-record Gemini-key constraints note (key `AQ.Ab8…`, removed as a hardcoded default, still in git history + EncryptedSharedPreferences, pending rotation).

---

# 13. Glossary

| Term | Definition |
|------|------------|
| **AICore** | Android system service that hosts on-device foundation models (Gemini Nano) and mediates access for apps. |
| **AppFunctions** | `androidx.appfunctions` framework that lets an app expose typed functions (e.g. `createTask`) to the system agent (Gemini/Assistant) for invocation. |
| **Confidence** | A 0.0–1.0 score attached to each Suggestion indicating the model's certainty; low-confidence items are visually de-emphasised and sweepable. |
| **Collector** | A component that reads one source (SMS, call log, Gmail, etc.) and returns normalised text for the understanding pipeline. |
| **DataStore** | Jetpack `Preferences DataStore` — the app's key-value store for source toggles, enabled-at watermarks, and processed-id ledgers (via `SourceManager`). |
| **Egress** | Any outbound network call leaving the device. Recorded (metadata only) by `EgressLogger` and shown in the Data Egress audit screen. |
| **EncryptedSharedPreferences** | AndroidX Security-Crypto key-value store backed by the Android Keystore; holds secrets and connected-account state (`SettingsManager`). |
| **Exact alarm** | An `AlarmManager` alarm (`setExactAndAllowWhileIdle`) that fires at a precise wall-clock time even under Doze; used for reminders. |
| **Foreground service** | An Android `Service` with a persistent notification (`foregroundServiceType=dataSync`) used to run live media/notification observers. |
| **Geofence** | A location-based trigger (Play Services Location) that fires a reminder when the user enters a saved place. |
| **Gemini (cloud)** | Google's cloud LLM (`gemini-2.5-flash`) invoked over HTTPS for the cloud understanding route. |
| **Gemini Nano / Gemma** | On-device foundation models run via AICore / MediaPipe LLM Inference for the on-device understanding route. |
| **Hilt** | Dagger-Hilt dependency-injection framework used for singletons and worker injection. |
| **Honest engine label** | UI policy that always displays the *effective* understanding route (on-device vs cloud), never a hardcoded claim. |
| **KSP** | Kotlin Symbol Processing — compile-time code generation for Room, Hilt, Moshi, and AppFunctions. |
| **Note** | A persisted, user-approved item (task / reminder / note / waiting-on / event). Distinct from a Suggestion. |
| **NotificationListenerService** | Android service (with `BIND_NOTIFICATION_LISTENER_SERVICE`) that reads posted notification text as a source. |
| **Processed-id ledger** | A per-source set of already-handled item IDs (in DataStore) used to deduplicate ingestion across scans. |
| **RAG** | Retrieval-Augmented Generation — the Ask feature retrieves relevant Notes via the semantic index, then asks the LLM to answer over them. |
| **Room** | Jetpack persistence library over SQLite; here backed by SQLCipher. |
| **RoutingLlmProvider** | The component that selects, per request, between the on-device and cloud LLM engines. |
| **RPO / RTO** | Recovery Point / Time Objective — how much data loss / downtime is tolerable after a failure (see §11). |
| **Semantic index** | On-device vector index (`HashingEmbedder` → `NoteEmbedding`, cosine similarity) enabling meaning-based retrieval for Ask. |
| **SQLCipher** | Transparent AES-256 encryption layer for SQLite; provides the app's encryption at rest. |
| **Suggestion** | An unapproved, extracted candidate shown in the Inbox for user review. Becomes a Note on approval. |
| **Task Fade** | A lifecycle mechanism that visually fades stale, undated to-dos and offers one-tap "bankruptcy" (bulk archive). |
| **Vosk** | An offline speech-recognition toolkit used as the fallback transcriber when whisper.cpp is unavailable. |
| **Waiting-on** | A Note type tracking something owed by another person; auto-resolves when that person is next heard from. |
| **Watermark** | The `lastScanAt` / per-source `enabledAt` timestamp bounding an incremental, forward-only scan. |
| **whisper.cpp** | A C/C++ implementation of OpenAI Whisper, compiled to a native library (`libwhisper_jni.so`, arm64-v8a) and called via JNI for on-device transcription. |
| **WorkManager** | Jetpack background-scheduling library used for periodic scans and deferrable jobs. |

# 14. Appendices

## Appendix A — Diagram Catalog

Section-level diagrams appear next to the text they illustrate: the **context diagram** (§3.1), the **entity–relationship diagram** (§5.4), the **component diagram** (§5.1), and the **sequence diagrams** for key flows (§6). This appendix consolidates the system-level views and adds lifecycle, decision, pipeline, and journey diagrams.

### A.1 Container / Runtime View

```mermaid
graph TB
    subgraph Device["Android Device (single trust boundary)"]
        subgraph UI["Presentation — Jetpack Compose (MainActivity)"]
            Inbox[Inbox]; Notes[Notes]; Ask[Ask]; Sources[Sources]; Privacy[Privacy]
        end
        subgraph Domain["Domain / Data — Hilt singletons"]
            Scanner[RecentDataScanner]
            Pipeline[UnderstandingPipeline]
            Router[RoutingLlmProvider]
            SemIndex[SemanticIndex]
            Alarms[AlarmScheduler]
            Egress[EgressLogger]
        end
        subgraph BG["Background Execution"]
            FGS[TaskMindForegroundService]
            WM[WorkManager: DataCollectionWorker + digests]
            NLS[NotificationListenerService]
            Recv[BroadcastReceivers: Alarm/Boot/Geofence]
        end
        subgraph Store["Storage (encrypted at rest)"]
            Room[(Room + SQLCipher DB v18)]
            DS[(DataStore — toggles/ledgers)]
            ESP[(EncryptedSharedPreferences — secrets)]
        end
        subgraph OnDevice["On-device ML"]
            Nano[Gemini Nano / Gemma via AICore/MediaPipe]
            Whisper[whisper.cpp JNI]; Vosk[Vosk]; OCR[ML Kit / Tesseract]
        end
    end
    subgraph Google["Google Cloud (only external dependency)"]
        Gem[Gemini API]; GmailAPI[Gmail API + OAuth]; Maps[Maps/Geocoding]
    end
    UI --> Domain
    BG --> Scanner
    Scanner --> Pipeline --> Router
    Router --> Nano
    Router -->|opt-in, audited| Gem
    Pipeline --> Room
    Domain --> Store
    Ask --> SemIndex --> Room
    Scanner -->|Gmail source| GmailAPI
    Alarms --> Maps
    Router -. logs .-> Egress
    GmailAPI -. logs .-> Egress
```

### A.2 End-to-End Data Flow

```mermaid
graph LR
    S[Sources: SMS / Notifications / Call log / Gmail / Audio / Images / Usage / Share / Wear] --> C[Collectors]
    C --> P[UnderstandingPipeline]
    P --> R{RoutingLlmProvider}
    R -->|private default| ON[On-device LLM]
    R -->|opt-in| CL[Cloud Gemini]
    ON --> SU[Suggestion — Inbox, unapproved]
    CL --> SU
    SU -->|user approves/edits| N[(Note — encrypted)]
    SU -->|user rejects| RL[RejectedPattern learner]
    N --> IDX[Semantic index]
    N --> AL[Alarm / Geofence / Calendar mirror]
    IDX --> ASK[Ask - RAG]
```

### A.3 Suggestion → Note Lifecycle (state machine)

```mermaid
stateDiagram-v2
    [*] --> Pending: extracted by pipeline
    Pending --> Approved: user approves / edits
    Pending --> Rejected: user rejects
    Pending --> Snoozed: user snoozes
    Snoozed --> Pending: snooze elapses (re-notify)
    Rejected --> [*]: feeds RejectionLearner (RejectedPattern)
    Approved --> Active: persisted as Note
    Active --> Completed: user ticks done
    Active --> Fading: undated & untouched (Task Fade)
    Fading --> Active: user touches it
    Fading --> Archived: bulk "bankruptcy"
    Active --> Archived: user archives
    Archived --> Active: user restores
    Completed --> [*]: retention cleanup
    note right of Active
        Waiting-on Notes auto-resolve
        when the person is next heard from
    end note
```

### A.4 LLM Routing Decision

```mermaid
flowchart TD
    A[processText / processMedia] --> B{Obvious noise?<br/>ExtractionHeuristics.isLikelyNoise}
    B -->|yes| Z[Skip — no LLM call]
    B -->|no| C{User engine preference<br/>+ on-device model present?}
    C -->|on-device available & preferred| D[On-device engine<br/>Gemini Nano / Gemma]
    C -->|cloud selected / on-device unavailable| E{Network available?}
    E -->|yes| F[Cloud Gemini<br/>log Egress + honest label 'cloud']
    E -->|no| G[Defer / retry next scan]
    D --> H[Structured JSON items]
    F --> H
    H --> I[Parse → score → dedup → insert Suggestion]
```

### A.5 CI/CD & Distribution Pipeline

```mermaid
flowchart LR
    Dev[Developer branch] --> PR[Pull request → main]
    PR --> CI{{GitHub Actions: android.yml}}
    CI --> J[Set up JDK 21 + Android SDK]
    J --> K[Restore NDK + native .cxx cache]
    K --> T[gradlew testDebugUnitTest ~707 tests]
    T --> AS[gradlew assembleDebug]
    AS --> Merge{Merged to main?}
    Merge -->|yes| REL[release-apk.yml]
    REL --> GH[Rolling 'debug-latest' GitHub Release<br/>signed w/ committed debug keystore]
    GH --> Side[Sideload / adb install -r on device]
    Merge -->|no| Gate[Required check gates the PR]
```

### A.6 Incremental Scan Window Logic

```mermaid
flowchart TD
    A[scanIncremental] --> B[now = System.currentTimeMillis]
    B --> C{lastScanAt == 0?<br/>first ever run}
    C -->|yes| D[since = now − 15 min]
    C -->|no| E[since = max lastScanAt, now − 24h]
    D --> F
    E --> F[For each ENABLED source]
    F --> G[start = max since, source.enabledAt]
    G --> H[Query rows since 'start']
    H --> I[Skip rows in processed-id ledger]
    I --> J[processText → pipeline]
    J --> K[Add id to ledger]
    K --> F
    F --> L[lastScanAt = now]
```

### A.7 Reminder Firing (sequence)

```mermaid
sequenceDiagram
    autonumber
    participant AP as SuggestionApprover
    participant AS as AlarmScheduler
    participant AM as AlarmManager (OS)
    participant BR as AlarmReceiver
    participant N as Notification
    participant U as User
    participant RA as ReminderActionReceiver
    AP->>AS: schedule(note, dueDateTime)
    AS->>AM: setExactAndAllowWhileIdle(PendingIntent)
    Note over AM: survives Doze, re-armed on reboot via BootReceiver
    AM-->>BR: onReceive() at due time
    BR->>N: post reminder (Done / Snooze actions)
    U->>RA: taps Done or Snooze
    alt Done
        RA->>AP: mark Note completed
    else Snooze
        RA->>AS: reschedule(+SNOOZE_MINUTES)
    end
```

### A.8 Core-Entity Class Diagram

```mermaid
classDiagram
    class Note {
        +int id
        +String type
        +String title
        +String body
        +String? dueDate
        +String? dueTime
        +String? recurrence
        +Double? locationLat
        +Double? locationLng
        +Double? locationRadius
        +String? locationLabel
        +String priority
        +boolean completed
        +boolean archived
        +String source
        +long createdDate
    }
    class Suggestion {
        +int id
        +String type
        +String extractedTitle
        +String summary
        +String rawSnippet
        +double confidence
        +String status
        +String source
        +String? possibleDuplicateOf
    }
    class NoteEmbedding {
        +int noteId
        +byte[] vector
    }
    class RejectedPattern {
        +String kind
        +String value
        +int count
        +long updatedAt
    }
    class SavedFilter {
        +String name
        +String? kind
        +List~String~ tags
    }
    Suggestion ..> Note : approve() creates
    Note "1" --> "1" NoteEmbedding : indexed by
    Suggestion ..> RejectedPattern : reject() learns
```

### A.9 Biometric App-Lock Gate

```mermaid
flowchart TD
    A[App returns to foreground] --> B{App lock enabled?<br/>SettingsManager}
    B -->|no| U[Show content]
    B -->|yes| C[BiometricPrompt]
    C -->|success| U
    C -->|failure / cancel| L[Locked overlay — reads blocked]
    L --> C
    note1[Capture-only entry points e.g. ShareTargetActivity<br/>bypass the lock: they write but never read existing data]
```

### A.10 Primary User Journey

```mermaid
journey
    title Zero-effort capture → reviewed action
    section Passive capture
      Receive SMS / notification / email: 3: TaskMind
      Background scan extracts a candidate: 4: TaskMind
    section Review (human-in-the-loop)
      Open Inbox, read suggestion: 4: User
      Edit / approve / snooze / reject: 5: User
    section Act
      Approved item saved as Note: 5: TaskMind
      Reminder / calendar / geofence set: 5: TaskMind
      Ask recalls it later: 4: User
```

## Appendix B — Configuration & Environment Variables

No real secrets are committed. Configuration is supplied at build time (Gradle / `local.properties`) or held at runtime in encrypted stores.

| Key | Location | Purpose | Notes |
|-----|----------|---------|-------|
| `MAPS_API_KEY` | `local.properties` (gitignored) → `manifestPlaceholders` → manifest `com.google.android.geo.API_KEY` | Google Maps SDK key for the embedded map / geocoding. | Falls back to empty string; the map simply shows no tiles if unset. |
| Debug signing (`storePassword`/`keyPassword` = `android`, alias `androiddebugkey`) | `apps/taskmind/build.gradle.kts` + committed `apps/taskmind/debug.keystore` | Stable, reproducible debug signature enabling in-place updates of the `debug-latest` build. | Standard non-secret debug credentials (see §8.3). |
| Cloud LLM key | Runtime (see `CloudLlmProvider`) | Authenticates the Gemini cloud route. | **Tech debt:** currently an "express"-style key; must move to a user-provided / secured key before public release (see §12). |
| Connected Gmail account(s) | `EncryptedSharedPreferences` (`SettingsManager`) | Which mailboxes are connected; access tokens are fetched on demand, never persisted. | OAuth scope `gmail.readonly`. |
| SQLCipher DB key | `EncryptedSharedPreferences` (Keystore-backed) | Encrypts the Room database at rest. | Generated on first run; never leaves the device. |
| Source toggles, `*_enabledAt` watermarks, processed-id ledgers, `scan_frequency_minutes`, `lastScanAt`, `useOnDeviceLlm` | `DataStore` / `EncryptedSharedPreferences` | Per-source enablement, incremental-scan bounds, dedup, scan cadence, engine preference. | See §5.4. |
| `room.schemaLocation`, `appfunctions:aggregateAppFunctions` | KSP args (`build.gradle.kts`) | Export the current Room schema (v18, `schemas/<db>/18.json`); aggregate AppFunction metadata at build time. | |
| NDK `27.0.12077973`, `abiFilters = arm64-v8a`, `-std=c++17` | `build.gradle.kts` / CMake | Native whisper.cpp toolchain and target ABI. | Single-ABI today (see §12). |

## Appendix C — References

| Reference | Location |
|-----------|----------|
| Application README & OAuth setup | `apps/taskmind/README.md` |
| Functional Test Plan | `apps/taskmind/docs/FUNCTIONAL_TEST_PLAN.md` |
| Extraction Evaluation Report | `apps/taskmind/docs/EVAL_REPORT.md` |
| CI pipeline | `.github/workflows/android.yml` |
| Sideload release pipeline | `.github/workflows/release-apk.yml`, `install-to-phone.yml` |
| Root build configuration | `apps/taskmind/build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml` |
| Native build | `apps/taskmind/src/main/cpp/CMakeLists.txt` |
| Room schema (current export) | `apps/taskmind/schemas/…/18.json` (v5.json is the oldest committed baseline) |
| Engineering roadmap | GitHub Project "TaskMind Roadmap" (#4), milestone `taskmind-v5` |

## Appendix D — Architecture Decision Records (ADRs)

Condensed records of the load-bearing decisions. Format: Context → Decision → Consequences → Status.

### ADR-001 — On-device-first, no first-party backend
- **Context.** The product must access highly sensitive personal signals (messages, calls, email). A server that ingests them centralises risk and liability.
- **Decision.** The phone is the backend. All storage, scheduling, and default inference are local; the only egress is to Google (cloud LLM opt-in, Gmail, Maps).
- **Consequences.** Eliminates server operating cost, breach surface, and multi-tenant complexity; forfeits server-side analytics, cross-device sync, and centralised telemetry. Scaling and DR become per-device concerns (§10–§11).
- **Status.** Accepted (foundational).

### ADR-002 — Human-in-the-loop approval (Suggestion → Note)
- **Context.** An assistant that autonomously writes tasks/calendar entries from inferred intent will act on false positives and erode trust.
- **Decision.** Every extraction is a *Suggestion* requiring explicit approval before it becomes a *Note* or schedules anything. Rejections train a local `RejectionLearner`.
- **Consequences.** Higher user control and trust; a required review step (mitigated by "Approve all", swipe gestures, notification quick-actions). Two distinct entities to model and test.
- **Status.** Accepted.

### ADR-003 — Dual LLM routing with honest engine labels
- **Context.** On-device models maximise privacy but trail cloud models in accuracy/latency; users differ in their privacy/quality trade-off.
- **Decision.** `RoutingLlmProvider` chooses per request; the UI always shows the *effective* route ("on-device"/"cloud") rather than a fixed claim; cloud is opt-in and audited.
- **Consequences.** Honest, verifiable privacy posture; added routing/fallback complexity and two prompt/parse paths to maintain.
- **Status.** Accepted.

### ADR-004 — Encryption at rest via SQLCipher + Keystore-backed secrets
- **Context.** A lost/rooted device must not yield readable personal data.
- **Decision.** Room over SQLCipher (AES-256); the DB key and connected-account state live in Keystore-backed `EncryptedSharedPreferences`; reads gated by an optional biometric app lock.
- **Consequences.** Strong at-rest protection; key management + database recovery paths (`DatabaseRecovery`) required; a lost key means lost data (mitigated by the encrypted backup).
- **Status.** Accepted.

### ADR-005 — Gmail via legacy `GoogleAuthUtil` (not the Identity Authorization API)
- **Context.** The newer Identity Authorization API returned a persistent `INTERNAL_ERROR` for the restricted `gmail.readonly` scope on this project despite correct console config.
- **Decision.** Use the older, battle-tested `GoogleAuthUtil.getToken` per-account token path; map its opaque statuses to actionable guidance (e.g. Advanced-Protection accounts).
- **Consequences.** Reliable token acquisition and multi-mailbox support; reliance on a deprecated surface and manual status interpretation.
- **Status.** Accepted (revisit if the surface is retired).

### ADR-006 — Native whisper.cpp for transcription with a Vosk fallback
- **Context.** On-device speech-to-text must run without network and with acceptable accuracy.
- **Decision.** Compile whisper.cpp (JNI, arm64-v8a) as the primary transcriber; fall back to Vosk when the native model is absent.
- **Consequences.** Good offline accuracy; a native toolchain (NDK, CMake), a single ABI today, and a 16 KB-page-alignment task before Play submission (§12).
- **Status.** Accepted.

### ADR-007 — Committed debug keystore for stable sideload signing
- **Context.** Each machine/CI runner auto-generates a throwaway debug keystore, so successive `debug-latest` builds fail to update in place (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- **Decision.** Commit a fixed, non-secret debug keystore so every build shares one signature.
- **Consequences.** Seamless in-place updates for the sideload channel; not a production signing strategy (a release config is still outstanding — §12).
- **Status.** Accepted (personal-distribution scope).

### ADR-008 — Egress logging as a first-class, auditable feature
- **Context.** "Privacy-first" claims must be verifiable by the user, not taken on faith.
- **Decision.** `EgressLogger` records every outbound call (host + purpose, never content) before it is made; a Data Egress screen surfaces the log and normally reads "No data has left this device."
- **Consequences.** Strong transparency and a debugging aid; a small discipline requirement (every network path must log first).
- **Status.** Accepted.

## Appendix E — Permissions & Play-Policy Matrix

| Permission / capability | Used for | Type | Public-Play status |
|-------------------------|----------|------|--------------------|
| `INTERNET` | Cloud LLM, Gmail, Maps egress | Normal | OK |
| `READ_SMS` | SMS source | Restricted (runtime) | **Blocker** — default SMS handler only |
| `READ_CALL_LOG` | Call-log source | Restricted (runtime) | **Blocker** — default dialer/handler only |
| `READ_CONTACTS` | Resolve a name → number for the Call action | Dangerous (runtime) | Allowed with disclosure |
| `READ_MEDIA_AUDIO` | Call/voice recording ingestion | Dangerous (runtime) | Allowed |
| `READ_MEDIA_IMAGES` | Screenshot/image OCR source | Dangerous (runtime) | Allowed |
| `RECORD_AUDIO` | Voice quick-capture | Dangerous (runtime) | Allowed |
| `READ_CALENDAR` / `WRITE_CALENDAR` | Calendar source + mirror | Dangerous (runtime) | Allowed |
| `ACCESS_FINE_LOCATION` / `ACCESS_BACKGROUND_LOCATION` | Geofenced location reminders | Dangerous + special | Allowed with background-location review |
| `PACKAGE_USAGE_STATS` | App-usage digest source | Special (Settings grant) | Allowed |
| Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`) | Notification source | Special (Settings grant) | Allowed |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` / `SET_ALARM` | Exact reminder alarms | Special | Allowed (justified use) |
| `RECEIVE_BOOT_COMPLETED` | Re-arm alarms after reboot | Normal | OK |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | Live media/notification observers | Normal | OK (typed FGS) |
| `POST_NOTIFICATIONS` | Review + reminder notifications | Runtime | OK |
| `QUERY_ALL_PACKAGES` | "Monitor these apps" picker | Sensitive | Needs Play declaration/justification |

## Appendix F — Non-Functional Target Summary

Consolidated view of the targets defined across §3.3 and §10–§11. Where a metric is not centrally measurable on-device, it is expressed as a design intent.

| Attribute | Target / Intent | Basis |
|-----------|-----------------|-------|
| Interactive UI latency | Frame-accurate scroll/interaction; heavy work off the main thread (coroutines/WorkManager) | §3.3, §10 |
| Extraction latency | On-device: seconds per item; Cloud: sub-second–seconds per call | §5.6, §10 |
| Background scan cadence | Default 30 min (15 min–6 h configurable), `requiresBatteryNotLow` | §6.1, §8 |
| Reminder timing accuracy | Exact wall-clock via `setExactAndAllowWhileIdle`; re-armed across reboot/clock change | §4.8, §6, §11 |
| Availability | Best-effort on-device; degrades gracefully offline (on-device route, deferred cloud) | §3.3, §11 |
| Durability (RPO) | Last successful auto-snapshot / manual encrypted backup | §11 |
| Recovery (RTO) | Reinstall + restore from encrypted backup (`TMBK1`) | §11 |
| Data protection | AES-256 at rest (SQLCipher) + TLS in transit + biometric read-gate | §7 |
| Test coverage gate | ~707 JVM unit tests must pass in CI before merge | §8.3 |

---

*End of document.*
