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

[`golden_set.jsonl`](golden_set.jsonl) holds 207 labelled cases spanning genuine reminders / to-dos /
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
  `null`), `due_date`, `due_time`, `recurrence`, `tags` (exact array), `tags_contains` (substring
  in any tag), `min_confidence`.

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

`ask_eval.py`'s `context_for` mirrors `AskAnswerPrompt.contextFor`, and its `call_gemini` mirrors
`CloudLlmProvider.generateAnswer`'s `generationConfig` (including `thinkingBudget: 0`). Same rule.

## Ask answer layer (`ask_eval.py`, #313 / #315)

A second harness, for a different prompt with a different risk. Extraction can be judged against a
gold label; the Ask answer layer has to be judged against *the notes it was given* — its failure mode
is a fluent sentence that isn't in them.

```bash
python ask_eval.py                                              # matchers only, all cases
python ask_eval.py --only inj_ -v                               # one category, print raw answers
python ask_eval.py --runs 3 --judge --report ASK_EVAL_REPORT.md # the full run: matchers + judge + report
```

Cases live in `ask_golden.jsonl` (~124): `notes` + `question` + `expect`. Categories are chosen for the
ways a grounded-answer layer actually fails, and the set is weighted toward the traps a deterministic
check can't see — `fact` (states the detail), `absent` (refuses when the notes are silent), `outside`
(refuses rather than answering from world knowledge), **`distract`** (picks the right note among
near-identical ones, i.e. leaks no *other* note's name/place), **`multi`** (combines two notes without
inventing a link), **`partial`** (doesn't over-claim from a related-but-silent note), `inj` (note text
is content, never instructions), `fmt`.

### Three grading layers

1. **Matchers** (the gate) — `contains_any` / `contains_all` / `not_contains` / `refuse` / `max_chars`.
2. **Number grounding** — every non-refusal answer fails if it contains a number absent from the notes
   *and* the question. Prices, gate codes, seat numbers, dates: an invented figure is caught without
   anyone anticipating the case.
3. **LLM-judge panel** (`--judge`) — a gold-blind panel re-reads `(question, notes, answer)` and votes
   on whether every claim is grounded. This is the only layer that catches a **non-numeric**
   hallucination: a wrong name, a wrong place, an invented link. Advisory, not the gate. Its highest-
   value output is the **disagreement**: a case the matchers PASS but the judge calls ungrounded is a
   hole the deterministic checks missed; the reverse flags an over-strict matcher or a bad gold label.
   The report has a dedicated section for both.

### Judge independence

`gemini-2.5-pro` is **gone on the express key** (404), so `--judge` defaults to `gemini-2.5-flash` —
the same family as the system under test, which shares its blind spots. Two ways to get a genuinely
independent judge:

- pass `--judge-model <model>` if a fuller key is available, or
- run the **cross-family Claude Workflow** `ask_judge.wf.js` (preferred — a different model family
  doesn't inherit the SUT's blind spots). It emits the same verdict JSONL that `--judge-in` consumes:

```bash
# in-harness (same-family) judge, self-contained:
python ask_eval.py --judge --judge-out ask_judge_verdicts.jsonl --report ASK_EVAL_REPORT.md
# or feed verdicts produced by ask_judge.wf.js (Claude) instead of calling Gemini:
python ask_eval.py --judge-in ask_judge_verdicts.jsonl --report ASK_EVAL_REPORT.md
```

### CI wiring — deliberately manual

Like `evaluate.py`, this makes **real paid API calls** (~124 answers + ~370 judge calls per full run),
so it is **not wired into CI** — a per-PR gate would bill on every push and flake at `temperature 0.1`.
It's a **manual pre-merge / periodic run**; the fresh numbers get committed in `ASK_EVAL_REPORT.md`. If
this is ever automated it should be a **scheduled `workflow_dispatch`** with the key as a secret, not a
`pull_request` trigger. The `AskEngineTest` unit suite is the fast gate that *does* run on every push.

## Extended comprehensive suite

`golden_set_ext.jsonl` (329 cases) is a harder, adversarial-weighted companion corpus that probes the
gaps the base set thins out: prompt-injection, non-English/code-switching, recurrence variety,
waiting-on vs commitment, long inputs, OCR noise, and noise-rejection traps. Authored + blind-relabeled
+ judged with a capable model, then gold-validated by `validate_ext.py` (schema lint + never-past-date
invariant + author-vs-relabeler agreement).

```bash
python evaluate.py --golden golden_set_ext.jsonl --jsonl-out ext_raw.jsonl --report EVAL_EXT.md
python analyze_comprehensive.py --ext ext_raw.jsonl --baseline base_raw.jsonl --judge judge_verdicts.json \
       --md EVAL_COMPREHENSIVE.md --html EVAL_dashboard.html
```

New `evaluate.py` flags: `--golden PATH` (run an alternate corpus), `--model NAME` (SUT model override),
`--jsonl-out PATH` (dump per-case raw results for downstream grading). The generation + judge harnesses
are `gen_scenarios.wf.js` / `judge.wf.js`. See `EVAL_COMPREHENSIVE.md` for the latest run (baseline 96%,
extended 93% gold-adjusted, with a documented prompt-injection weakness).
