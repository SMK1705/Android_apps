# Prompt accuracy harness

A small, dependency-free (stdlib-only) Python harness that measures how well TaskMind's
extraction prompt turns text into items, so prompt changes can be checked before they ship.

## What it does

- Reads the **live** system prompt straight out of
  `apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/understanding/SystemPrompt.kt`
  (regex-extracts the `INSTRUCTION` string) — so the harness can never drift from the app.
- Replays every case in [`golden_set.jsonl`](golden_set.jsonl) against Gemini using the **same
  model, response schema, temperature and `Source:/Text:` message layout** the app uses
  (`CloudLlmProvider` / `UnderstandingPipeline`).
- Checks each result against the case's `expect` and prints a PASS/FAIL table + a score.
  Exit code is non-zero if any case fails.

It makes **real (paid) API calls**, so it is **manual — not wired into CI**.

## Dataset

[`golden_set.jsonl`](golden_set.jsonl) holds 166 labelled cases spanning genuine reminders / to-dos /
notes and — heavily — the **noise that must produce nothing**: social pings, promos & fake urgency,
shipping/order updates, payment/account/subscription notices, OTP/security alerts, and app/system/news
notifications. Many are drawn from real-world notification wording. Roughly half are `none`, so the
report's **`none` recall** is the key "don't nag me" metric, and `todo`/`reminder` **precision**
measures how often the model invents an item from noise.

```bash
cd tools/prompt_eval
GEMINI_API_KEY=your_key python evaluate.py        # all cases
GEMINI_API_KEY=your_key python evaluate.py -v     # also print the model's raw items
GEMINI_API_KEY=your_key python evaluate.py --only call   # only cases whose name contains "call"
GEMINI_API_KEY=your_key python evaluate.py --report      # also write EVAL_REPORT.md
```

The key may instead live in `local.properties` (repo root, gitignored) as `gemini.api.key=...`.
Requires Python 3.9+ and network access. No `pip install` needed.

## Report

`--report [PATH]` writes a Markdown report (default [`EVAL_REPORT.md`](EVAL_REPORT.md)) with:

- a **type confusion matrix** — expected `type` (rows) vs. the `type` Gemini returned (columns),
  with `none` as a class for over/under-extraction, plus per-type **recall** and **precision**;
- **field accuracy** for `due_date` / `due_time` / `recurrence` / `location`, graded on the
  correctly-identified item; and
- overall pass rate + a table of the failing cases.

The checked-in `EVAL_REPORT.md` is a point-in-time snapshot; regenerate it after a prompt change.
At `temperature 0.1` the model is near- but not fully deterministic, so a case on a genuinely
ambiguous phrasing may flip between runs.

## Add a case

Append one JSON object per line to `golden_set.jsonl`:

```json
{"name": "short_id", "source": "Notification from Amma", "text": "call me tonight",
 "now": "2026-06-09T09:00", "expect": [{"title_contains": "Call"}]}
```

- `now` is ISO `YYYY-MM-DDTHH:MM`; the harness renders it the way the app does
  (`… . Today is a <weekday>.`).
- `expect` is either `{"items": 0}` (the model must extract nothing) **or** a list of matchers;
  each matcher must be satisfied by at least one returned item. Supported matcher keys:
  `type`, `title_contains`, `notes_contains`, `location_contains`, `location` (exact, incl.
  `null`), `due_date`, `due_time`, `recurrence`, `min_confidence`.

Keep assertions loose where the wording is the model's choice (`title_contains`, not an exact
title) and tight where correctness matters (dates, times, recurrence, the no-item cases).

### Natural-language edit cases (#115)

Add `"mode": "edit"` plus a `"current"` item to test the NL-edit prompt (`EditPrompt.kt`) instead of
extraction. `text` is the edit instruction; `current` is the item being edited; the reply is checked
with the same matchers (they assert the patched fields):

```json
{"name": "edit_friday_6pm_high", "mode": "edit", "source": "Inbox edit",
 "text": "make it friday 6pm and high priority", "now": "2026-06-09T09:00",
 "current": {"title": "Pay rent", "type": "reminder", "due_date": "2026-06-01", "priority": "normal"},
 "expect": [{"due_date": "2026-06-12", "due_time": "18:00", "priority": "high"}]}
```

`current` keys: `title`, `type`, `due_date`, `due_time`, `location`, `recurrence`, `priority` (any
omitted default to null / note / normal). The harness sends the same "Current item + Instruction"
message `SuggestionEditor` does; the cloud reply is schema-pinned to `{"items":[…]}`, so matchers
score `items[0]`.

Edit-mode measures the **raw LLM patch only** — it does NOT run `NaturalDate`, which in production
OVERRIDES the model for `due_date`/`due_time`/`recurrence` (deterministic beats the model's relative-date
math). So assert edit cases on the fields the LLM actually owns (`priority`, `type`, `location`), not on
model-computed relative dates.

## Keep in sync

`RESPONSE_SCHEMA` in `evaluate.py` mirrors `CloudLlmProvider.responseSchema()`. If you change the
item shape in one, change it in the other.
