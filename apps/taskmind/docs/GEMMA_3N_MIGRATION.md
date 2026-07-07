# Gemma 3n multimodal engine migration — design & staged plan (#129)

> **Status: design / spike.** This document is the deliverable for #129. It does **not** change the
> working engine. The migration itself is broken into device-verifiable, eval-gated phases, each filed
> as its own follow-up issue, because the core — a new inference runtime (LiteRT-LM), a multi-GB Gemma 3n
> model, and device-specific multimodal APIs — cannot be built or verified in CI / a non-device dev
> environment. Swapping a *working* on-device engine with zero ability to test inference would be
> reckless; this plan de-risks it instead.

## Why migrate

- **MediaPipe LLM Inference is maintenance-only.** Google explicitly recommends moving on-device LLM
  work to **LiteRT-LM**. Staying on `com.google.mediapipe:tasks-genai` is accumulating tech debt.
- **Multimodal unlocks the weakest link.** Screenshots today go through Tesseract OCR
  (`OcrEngine`), which breaks on layout, fonts, and dark mode. A multimodal model (Gemma 3n E2B) reads
  the screenshot *directly* — a poster/invite screenshot can yield a fully-formed event. Audio can be
  understood directly too, without a transcription pass.
- **ML Kit GenAI (system Gemini Nano) is a zero-download third engine.** On supported devices (e.g.
  Galaxy S25 — the daily driver) it needs no model download at all, behind the same interface.

Rank #24/25 · impact **high** · effort **large** · differentiator **yes**.

---

## Current architecture (what the migration touches)

The on-device stack is a clean, already-abstracted seam — the migration is mostly *adding engines behind
it*, not rewiring callers.

| Piece | File | What it does |
|---|---|---|
| `LlmProvider` (interface) | `data/source/understanding/LlmProvider.kt:3` | `generate` / `generateList` / `generateIntent`, **text-only** (`String` in, `String` out). `generateList`/`generateIntent` default to `generate`. |
| `OnDeviceLlmProvider` | `.../OnDeviceLlmProvider.kt:24` | MediaPipe `LlmInference` over a Gemma `.task`/`.litertlm` in `filesDir`; GPU (Adreno) backend; **2048-token** KV-cache cap; a `Mutex` (MediaPipe is not reentrant); Gemma chat-turn template at line 89. |
| `CloudLlmProvider` | `.../CloudLlmProvider.kt` | Gemini `gemini-2.5-flash`, **schema-constrained** (`responseSchema` / `stringArraySchema` / `intentSchema`) so the reply can't drift from the `LlmItem` shape. |
| `RoutingLlmProvider` | `.../RoutingLlmProvider.kt:14` | Picks on-device vs cloud per `settingsManager.useOnDeviceLlm`; falls back on-device→cloud when on-device throws **and** a cloud key is set; `isOnDeviceEffective()` (#197) labels the engine honestly. Bound as `LlmProvider` in `NetworkModule`. |
| `ModelDownloader` | `data/source/ModelDownloader.kt:26` | Generic `download(url, dest, onProgress)` → streamed `.part` file; logs egress via `EgressLogger`. Engine-agnostic — **reusable as-is**. |
| Screenshot path | `RecentDataScanner.scanImages` (`:252`, OCR at `:274`) | `ocrEngine.recognize(uri)` → `pipeline.processText("Screenshot: …", text)`. Gated on `ocrEngine.isModelPresent()`. **This is the primary multimodal bypass seam.** |
| Audio path | `RecentDataScanner.scanAudio` (`:201`) | `transcriber.transcribe(uri)` (Vosk + optional Whisper, #126) → `processText("Recording: …", text)`. Secondary bypass seam. |
| Eval harness | `tools/prompt_eval/evaluate.py` + `golden_set.jsonl` (**207 cases**) | Replays the golden set against **Gemini only** (`MODEL = "gemini-2.5-flash"`, REST) with the app's real instruction + schema; emits a type **confusion matrix** + field accuracy to `EVAL_REPORT.md`. |

### Invariants the migration must not break
1. **A working engine is always available.** MediaPipe stays selectable until LiteRT-LM is proven on a
   real device — never a hard cutover.
2. **Serialize inference.** MediaPipe `LlmInference` is not reentrant (the `Mutex` at
   `OnDeviceLlmProvider.kt:91`). Verify LiteRT-LM's reentrancy before removing it.
3. **2048-token KV-cache cap** on the GPU backend for int4 Gemma; `UnderstandingPipeline` caps input at
   `MAX_INPUT_CHARS = 4000` to leave output headroom. Re-calibrate if LiteRT-LM's limit differs.
4. **The Gemma chat-turn template** (`<start_of_turn>user … <end_of_turn>`) is model-specific — Gemma 3n
   may differ; verify against the golden set.
5. **Structured output is not universal.** Only the cloud engine enforces a `responseSchema`; on-device
   relies on tolerant parsing + `salvageItems`. LiteRT-LM / Nano likely can't pin a schema either — the
   tolerant path must hold.
6. **The anti-noise contract is sacred.** The two golden-set metrics that gate everything: **`none`
   recall** (don't invent tasks / don't nag-bomb) and **`high`-priority precision** (don't fabricate
   urgency). A new engine must not regress these.

---

## Target architecture

Three engines behind one interface, chosen per input modality and the user's setting, all honestly
labelled (#197):

```
                         RoutingLlmProvider
        text ─────────────────┬───────────────── image / audio
                              │
   on-device (LiteRT-LM, Gemma 3n E2B) ── multimodal ✔  ← preferred when present
   system Nano (ML Kit GenAI Prompt API) ── multimodal ✔  ← zero-download, device-gated
   cloud (Gemini 2.5-flash) ── multimodal ✔ (base64 parts) ← fallback / when selected
```

The screenshot path sends the **image straight to a vision-capable engine**, bypassing Tesseract; if no
vision engine is effective, it falls back to today's OCR→text path. Same for audio.

### Interface design (multimodal, backwards-compatible)

Extend `LlmProvider` with an opt-in vision capability so text-only callers and providers are untouched:

```kotlin
interface LlmProvider {
    suspend fun generate(systemMessage: String, userMessage: String): String
    suspend fun generateList(systemMessage: String, userMessage: String): String = generate(...)
    suspend fun generateIntent(systemMessage: String, userMessage: String): String = generate(...)

    /** True only when this provider can read an image/audio input. Default false → text-only. */
    fun supportsVision(): Boolean = false

    /** Multimodal extract: the media plus a text instruction. Null if unsupported (caller falls back to OCR/transcribe). */
    suspend fun generateFromMedia(systemMessage: String, userMessage: String, media: MediaInput): String? = null
}
```

- `MediaInput` = a `Uri`/`ByteArray` + a MIME type (image/png, audio/*). Kept minimal so it can thread
  through `RoutingLlmProvider` unchanged for text calls.
- **Blast radius is small.** Only the screenshot/audio scan paths opt in; `UnderstandingPipeline`,
  `AskEngine`, and Magic Breakdown stay text-only. `RoutingLlmProvider.generateFromMedia` routes to the
  first effective vision engine, else returns null → the caller uses the existing OCR/transcribe path.
- `RoutingLlmProvider` gains a 3-way effective-engine selection and extends `isOnDeviceEffective()` to
  label which of {Gemma 3n on-device, system Nano, Gemini cloud} actually ran (#197 honesty).

### MediaPipe → LiteRT-LM mapping (verify each on-device)

| MediaPipe (today) | LiteRT-LM (target) | Notes |
|---|---|---|
| `com.google.mediapipe:tasks-genai:0.10.35` | LiteRT-LM artifact (`com.google.ai.edge…`) | **VERIFY** exact coordinates/version at build time. |
| `LlmInference.createFromOptions(ctx, opts)` | LiteRT-LM session create | rebuild `createEngine()` (`:57`). |
| `LlmInferenceOptions.builder().setModelPath().setMaxTokens(2048).setPreferredBackend(GPU)` | LiteRT-LM session config | GPU may be implicit; **VERIFY** token cap for Gemma 3n E2B. |
| `engine.generateResponse(prompt)` | LiteRT-LM generate | keep the same JSON-string contract; **VERIFY** the chat template. |
| `.task` / `.litertlm` file (already preferred at `:43`) | `.litertlm` bundle | model resolution + `ModelDownloader` reuse as-is. |

Everything outside `OnDeviceLlmProvider` (routing, settings, model download, the Settings check/download
UX) stays. The class is the migration's blast radius for the text path.

### ML Kit GenAI (system Gemini Nano) — third engine

- New `MlKitGenAiProvider : LlmProvider` alongside the others (Play Services `…mlkit-genai…`).
- **Zero download** — the model is system-provided; gate on a runtime availability check + device
  support (S25 listed). `supportsVision() = true` (Nano is multimodal).
- Reuses the hardened anti-noise instruction nearly verbatim; output goes through the same Moshi
  `LlmResponse`/`LlmItem` adapter + tolerant parsing (no `responseSchema`).
- Routing order (a new setting): system Nano (if available) → on-device Gemma 3n → cloud Gemini.

---

## The eval-gated migration (the safety net)

The `tools/prompt_eval` harness is the regression gate — **but today it only targets Gemini cloud**
(`evaluate.py:39`). To gate an on-device migration it must be able to run the *same 207 golden cases*
against the on-device engine. Two options:

1. **On-device eval mode** — a debug entry point (an instrumented test or a hidden Settings action) that
   runs the golden set through `RoutingLlmProvider` on the device and dumps a report the harness scores.
2. **Per-engine golden reports** — capture each engine's output on the golden set, score with the same
   matrix, and diff against the Gemini baseline.

**Acceptance per phase:** the type confusion matrix, and especially **`none` recall** and
**`high`-priority precision**, must not regress vs the current baseline (`EVAL_REPORT.md`). A phase that
regresses the anti-noise metrics does not ship — better to keep MediaPipe than to nag-bomb the user.

---

## Staged plan (each a follow-up issue, each device-gated)

Ordered so every step is independently shippable and the working engine is never at risk:

- **Phase 0 — multimodal-ready interface (no engine).** Add `supportsVision()` + `generateFromMedia()`
  (default no-op) to `LlmProvider`; route the screenshot/audio scan through it with a fallback to
  today's OCR/transcribe. Fully unit-testable; ships dark (no vision engine yet). *This is the only
  phase safely buildable without a device.*
- **Phase 1 — LiteRT-LM on-device provider (text parity).** Reimplement `OnDeviceLlmProvider` on
  LiteRT-LM behind a selectable engine setting, keeping MediaPipe as an alternative. **Gate:** golden-set
  parity vs MediaPipe/Gemini on a device.
- **Phase 2 — multimodal image (screenshot OCR-bypass).** `supportsVision()=true`; `scanImages` sends
  the image to the model. **Gate:** golden set + a screenshot→event set; must beat OCR text.
- **Phase 3 — multimodal audio.** `scanAudio` passes audio directly, bypassing Vosk/Whisper. **Gate:**
  audio extraction quality vs the transcribe path.
- **Phase 4 — ML Kit GenAI (Nano) engine + 3-way routing + honest labels (#197).**
- **Phase 5 — retire MediaPipe** once LiteRT-LM is proven across target devices.

## Risks & rollback

- **Regressing a working engine** → mitigated by keeping MediaPipe selectable, the per-phase engine
  setting, and the eval gate. No hard cutover until Phase 5.
- **Model size / download** → Gemma 3n E2B `.litertlm` is large; reuse `ModelDownloader` + `EgressLogger`
  + the Settings download UX; verify storage + first-load time on-device.
- **Device variance** → GPU backend, KV-cache cap, and Nano availability differ by device; every engine
  phase is verified on the physical S25 before it becomes the default.
- **Unverifiable in dev** → this is *why* the work is staged and device-gated rather than shipped blind.
