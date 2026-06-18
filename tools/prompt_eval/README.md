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

## Run

```bash
cd tools/prompt_eval
GEMINI_API_KEY=your_key python evaluate.py        # all cases
GEMINI_API_KEY=your_key python evaluate.py -v     # also print the model's raw items
GEMINI_API_KEY=your_key python evaluate.py --only call   # only cases whose name contains "call"
```

The key may instead live in `local.properties` (repo root, gitignored) as `gemini.api.key=...`.
Requires Python 3.9+ and network access. No `pip install` needed.

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

## Keep in sync

`RESPONSE_SCHEMA` in `evaluate.py` mirrors `CloudLlmProvider.responseSchema()`. If you change the
item shape in one, change it in the other.
