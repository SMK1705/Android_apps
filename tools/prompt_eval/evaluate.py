#!/usr/bin/env python3
"""
Accuracy harness for TaskMind's extraction prompt.

It reads the *live* system prompt straight out of SystemPrompt.kt (so the harness can never drift
from the app), replays every case in golden_set.jsonl against Gemini using the same model, schema
and message layout the app uses, and checks each result against the case's expectations.

Run:
    GEMINI_API_KEY=... python evaluate.py                 # all cases, PASS/FAIL table
    GEMINI_API_KEY=... python evaluate.py --only call      # cases whose name contains "call"
    GEMINI_API_KEY=... python evaluate.py -v               # also print the model's raw items
    GEMINI_API_KEY=... python evaluate.py --report         # also write EVAL_REPORT.md (matrix + fields)

The key can also live in ../../local.properties as `gemini.api.key=...`.
Makes real (paid) API calls, so it is manual — NOT wired into CI. Exit code is non-zero if any
case fails, so it can gate a prompt change locally.
"""
from __future__ import annotations

import argparse
import collections
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
PROMPT_KT = REPO / "apps/taskmind/src/main/java/com/rajasudhan/taskmind/data/source/understanding/SystemPrompt.kt"
GOLDEN = HERE / "golden_set.jsonl"
REPORT_DEFAULT = HERE / "EVAL_REPORT.md"
MODEL = "gemini-2.5-flash"
ENDPOINT = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent"

# The four classes of the type confusion matrix. "none" = the model extracted nothing for the case.
TYPE_CLASSES = ["reminder", "todo", "note", "none"]

# Mirrors CloudLlmProvider.responseSchema() — keep in sync.
RESPONSE_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "items": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "type": {"type": "STRING", "enum": ["reminder", "todo", "note"]},
                    "title": {"type": "STRING"},
                    "notes": {"type": "STRING"},
                    "due_date": {"type": "STRING", "nullable": True},
                    "due_time": {"type": "STRING", "nullable": True},
                    "location": {"type": "STRING", "nullable": True},
                    "recurrence": {"type": "STRING", "nullable": True},
                    "confidence": {"type": "NUMBER"},
                },
                "required": ["type", "title", "notes", "confidence"],
                "propertyOrdering": ["type", "title", "notes", "due_date", "due_time", "location", "recurrence", "confidence"],
            },
        }
    },
    "required": ["items"],
}


def api_key() -> str:
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if key:
        return key
    props = REPO / "local.properties"
    if props.exists():
        for line in props.read_text(encoding="utf-8").splitlines():
            if line.strip().startswith("gemini.api.key"):
                return line.split("=", 1)[1].strip()
    sys.exit("No API key: set GEMINI_API_KEY or add `gemini.api.key=...` to local.properties.")


def load_instruction() -> str:
    """Extract the INSTRUCTION triple-quoted string from SystemPrompt.kt (the real prompt)."""
    src = PROMPT_KT.read_text(encoding="utf-8")
    m = re.search(r'INSTRUCTION\s*=\s*"""(.*?)"""', src, re.DOTALL)
    if not m:
        sys.exit(f"Could not find INSTRUCTION in {PROMPT_KT}")
    return m.group(1)


def datetime_str(now_iso: str) -> str:
    """Render `now` the way UnderstandingPipeline does: '2026-06-09T09:00. Today is a Tuesday.'"""
    dt = datetime.fromisoformat(now_iso)
    return f"{dt.strftime('%Y-%m-%dT%H:%M')}. Today is a {dt.strftime('%A')}."


def call_gemini(key: str, system: str, user: str) -> dict:
    body = {
        "systemInstruction": {"parts": {"text": system}},
        "contents": [{"role": "user", "parts": [{"text": user}]}],
        "generationConfig": {
            "temperature": 0.1,
            "responseMimeType": "application/json",
            "responseSchema": RESPONSE_SCHEMA,
        },
    }
    req = urllib.request.Request(
        f"{ENDPOINT}?key={key}",
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
            text = payload["candidates"][0]["content"]["parts"][0]["text"]
            return json.loads(text)
        except urllib.error.HTTPError as e:
            if e.code in (429, 503) and attempt < 3:
                time.sleep(2 * (attempt + 1))
                continue
            raise
    return {"items": []}


def matches(item: dict, m: dict) -> bool:
    def contains(field, needle):
        return needle.lower() in str(item.get(field) or "").lower()

    if "type" in m and item.get("type") != m["type"]:
        return False
    if "title_contains" in m and not contains("title", m["title_contains"]):
        return False
    if "notes_contains" in m and not contains("notes", m["notes_contains"]):
        return False
    if "location_contains" in m and not contains("location", m["location_contains"]):
        return False
    if "location" in m and item.get("location") != m["location"]:  # exact, incl. null
        return False
    if "due_date" in m and item.get("due_date") != m["due_date"]:
        return False
    if "due_time" in m and item.get("due_time") != m["due_time"]:
        return False
    if "recurrence" in m and item.get("recurrence") != m["recurrence"]:
        return False
    if "min_confidence" in m and float(item.get("confidence") or 0) < m["min_confidence"]:
        return False
    return True


def check(expect, items: list) -> tuple[bool, str]:
    # "none" expectation: the model must extract nothing.
    if isinstance(expect, dict) and expect.get("items") == 0:
        return (len(items) == 0, "expected no items" if items else "")
    # otherwise expect is a list of matchers; each must be satisfied by some item.
    unmet = [m for m in expect if not any(matches(it, m) for it in items)]
    if unmet:
        return False, "unmet: " + json.dumps(unmet)
    if isinstance(expect, list) and len(items) < len(expect):
        return False, f"expected >= {len(expect)} items, got {len(items)}"
    return True, ""


# --------------------------------------------------------------------------------------------------
# Type confusion matrix + field accuracy (for EVAL_REPORT.md)
# --------------------------------------------------------------------------------------------------

def expected_type(expect):
    """The case's canonical expected type for the matrix, or None when the case doesn't pin a type."""
    if isinstance(expect, dict) and expect.get("items") == 0:
        return "none"
    if isinstance(expect, list) and expect and "type" in expect[0]:
        return expect[0]["type"]
    return None


IDENTITY_KEYS = ("title_contains", "notes_contains")


def identity_match(item: dict, m: dict) -> bool:
    """True if `item` is the thing the matcher identifies — by title/notes only, ignoring type and the
    graded date/time/recurrence/location fields. Used to line up the model's item with the expectation
    so classification and each field can be scored independently. Matchers with no identity key match
    the first item (these cases expect a single item)."""
    ident = {k: v for k, v in m.items() if k in IDENTITY_KEYS}
    return matches(item, ident) if ident else True


def returned_type(expect, items: list) -> str:
    """The type the model assigned to the item that answers the primary expectation (found by
    identity, not type), or 'none' when nothing was extracted."""
    if isinstance(expect, dict) and expect.get("items") == 0:
        return items[0].get("type", "note") if items else "none"
    primary = expect[0] if isinstance(expect, list) and expect else {}
    for it in items:
        if identity_match(it, primary):
            return it.get("type", "note")
    return items[0].get("type", "note") if items else "none"


def field_scores(expect, items: list) -> dict:
    """(correct, applicable) per structured field for the primary expectation, graded on the
    identity-matching item so a wrong type or a different wrong field doesn't fail the others."""
    if not (isinstance(expect, list) and expect):
        return {}
    primary = expect[0]
    match = next((it for it in items if identity_match(it, primary)), None)
    out = {}
    for f in ("due_date", "due_time", "recurrence"):
        if f in primary:
            out[f] = (1 if match is not None and match.get(f) == primary[f] else 0, 1)
    if "location" in primary:
        out["location"] = (1 if match is not None and match.get("location") == primary["location"] else 0, 1)
    elif "location_contains" in primary:
        need = primary["location_contains"].lower()
        out["location"] = (1 if match is not None and need in str(match.get("location") or "").lower() else 0, 1)
    return out


def pct(n: int, d: int) -> str:
    return f"{100 * n / d:.0f}%" if d else "—"


def render_report(results: list, passed: int, ncases: int, runs: int) -> str:
    total = len(results)
    typed = [r for r in results if r["exp_type"] is not None]
    cm = {e: collections.Counter() for e in TYPE_CLASSES}
    for r in typed:
        cm[r["exp_type"]][r["ret_type"]] += 1
    diag = sum(cm[t][t] for t in TYPE_CLASSES)
    ntyped = len(typed)

    scope = f"{ncases} cases" if runs == 1 else f"{ncases} cases × {runs} runs = {total} observations"
    unit = "Cases" if runs == 1 else "Observations"
    out: list = []
    w = out.append
    w("# TaskMind Extraction — Evaluation Report")
    w("")
    w(f"_Model `{MODEL}` · {scope} · generated by `tools/prompt_eval/evaluate.py --report`._  ")
    w(f"_Run: {datetime.now().strftime('%Y-%m-%d %H:%M')}._")
    w("")
    w("## Overall")
    w("")
    w(f"- **{unit} passed:** {passed}/{total} ({pct(passed, total)})")
    w(f"- **Type-classification accuracy:** {diag}/{ntyped} ({pct(diag, ntyped)}) over type-labelled {unit.lower()}")
    w("")
    w("## Type confusion matrix")
    w("")
    w("Expected type (rows) vs. the `type` Gemini returned (columns); `none` = nothing extracted. "
      "The **diagonal** is correct — off-diagonal cells are the confusions.")
    w("")
    w("| expected ↓ / returned → | " + " | ".join(TYPE_CLASSES) + " | total | recall |")
    w("|" + "---|" * (len(TYPE_CLASSES) + 3))
    for e in TYPE_CLASSES:
        row_total = sum(cm[e].values())
        cells = [(f"**{cm[e][c]}**" if e == c and cm[e][c] else str(cm[e][c])) for c in TYPE_CLASSES]
        w(f"| **{e}** | " + " | ".join(cells) + f" | {row_total} | {pct(cm[e][e], row_total)} |")
    prec = []
    for c in TYPE_CLASSES:
        col_total = sum(cm[e][c] for e in TYPE_CLASSES)
        prec.append(pct(cm[c][c], col_total))
    w("| **precision** | " + " | ".join(prec) + " | | |")
    w("")

    fagg: dict = {}
    for r in results:
        for f, (cor, app) in r["fields"].items():
            c0, a0 = fagg.get(f, (0, 0))
            fagg[f] = (c0 + cor, a0 + app)
    w("## Field accuracy")
    w("")
    w("Structured fields graded on the correctly-identified item (so a type mismatch isn't also "
      "counted as a field miss).")
    w("")
    w("| field | correct / total | accuracy |")
    w("|---|---|---|")
    for f in ("due_date", "due_time", "recurrence", "location"):
        if f in fagg:
            cor, app = fagg[f]
            w(f"| {f} | {cor}/{app} | {pct(cor, app)} |")
    w("")

    fails = [r for r in results if not r["ok"]]
    w(f"## Failures ({len(fails)})")
    w("")
    if not fails:
        w("_None — every case passed._")
    else:
        w("| case | expected type | returned type | reason |")
        w("|---|---|---|---|")
        for r in fails:
            reason = (r["reason"] or "").replace("|", "\\|")
            reason = reason[:80] + "…" if len(reason) > 81 else reason
            w(f"| `{r['name']}` | {r['exp_type']} | {r['ret_type']} | {reason} |")
    w("")

    unspec = [r for r in results if r["exp_type"] is None]
    if unspec:
        w(f"> {len(unspec)} case(s) pin no explicit expected `type`, so they're excluded from the "
          f"confusion matrix (still counted in the pass rate): "
          + ", ".join(f"`{r['name']}`" for r in unspec) + ".")
        w("")
    return "\n".join(out)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="run only cases whose name contains this substring")
    ap.add_argument("-v", "--verbose", action="store_true", help="print raw model items")
    ap.add_argument("--runs", type=int, default=1, metavar="N",
                    help="run each case N times and aggregate every metric over the runs — evens out the "
                         "model's run-to-run non-determinism at low temperature (default 1)")
    ap.add_argument(
        "--report", nargs="?", const=str(REPORT_DEFAULT), default=None, metavar="PATH",
        help="also write a Markdown report (type confusion matrix + field accuracy) to PATH "
             "(default tools/prompt_eval/EVAL_REPORT.md)",
    )
    args = ap.parse_args()
    runs = max(1, args.runs)

    key = api_key()
    instruction = load_instruction()
    cases = [json.loads(l) for l in GOLDEN.read_text(encoding="utf-8").splitlines() if l.strip()]
    if args.only:
        cases = [c for c in cases if args.only in c["name"]]

    results, passed = [], 0
    for c in cases:
        system = instruction.replace("{{CURRENT_DATETIME}}", datetime_str(c["now"]))
        user = f"Source: {c['source']}\n\nText:\n{c['text']}"
        case_ok, last_reason = 0, ""
        for _ in range(runs):
            try:
                result = call_gemini(key, system, user)
                items = result.get("items", [])
                ok, reason = check(c["expect"], items)
            except Exception as e:  # noqa: BLE001 - surface any failure as a case failure
                ok, reason, items = False, f"error: {e}", []
            passed += ok
            case_ok += ok
            last_reason = reason
            results.append({
                "name": c["name"],
                "exp_type": expected_type(c["expect"]),
                "ret_type": returned_type(c["expect"], items),
                "ok": ok,
                "reason": reason,
                "fields": field_scores(c["expect"], items),
            })
            if args.verbose:
                print("         items: " + json.dumps(items, ensure_ascii=False))
            time.sleep(0.4)  # be gentle on rate limits
        if runs == 1:
            print(f"  [{'PASS' if case_ok else 'FAIL'}] {c['name']}"
                  + ("" if case_ok else f"  -> {last_reason}"))
        else:
            tag = "PASS" if case_ok == runs else ("FAIL" if case_ok == 0 else "FLAK")
            print(f"  [{tag} {case_ok}/{runs}] {c['name']}"
                  + ("" if case_ok == runs else f"  -> {last_reason}"))

    total = len(results)
    print(f"\n{passed}/{total} passed ({len(cases)} cases × {runs} run(s)).")

    if args.report is not None:
        Path(args.report).write_text(render_report(results, passed, len(cases), runs) + "\n", encoding="utf-8")
        print(f"Wrote report: {args.report}")

    return 0 if passed == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
