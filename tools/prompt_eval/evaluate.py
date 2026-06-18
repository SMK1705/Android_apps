#!/usr/bin/env python3
"""
Accuracy harness for TaskMind's extraction prompt.

It reads the *live* system prompt straight out of SystemPrompt.kt (so the harness can never drift
from the app), replays every case in golden_set.jsonl against Gemini using the same model, schema
and message layout the app uses, and checks each result against the case's expectations.

Run:
    GEMINI_API_KEY=... python evaluate.py            # all cases
    GEMINI_API_KEY=... python evaluate.py --only call # cases whose name contains "call"
    GEMINI_API_KEY=... python evaluate.py -v          # also print the model's raw items

The key can also live in ../../local.properties as `gemini.api.key=...`.
Makes real (paid) API calls, so it is manual — NOT wired into CI. Exit code is non-zero if any
case fails, so it can gate a prompt change locally.
"""
from __future__ import annotations

import argparse
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
MODEL = "gemini-2.5-flash"
ENDPOINT = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent"

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


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="run only cases whose name contains this substring")
    ap.add_argument("-v", "--verbose", action="store_true", help="print raw model items")
    args = ap.parse_args()

    key = api_key()
    instruction = load_instruction()
    cases = [json.loads(l) for l in GOLDEN.read_text(encoding="utf-8").splitlines() if l.strip()]
    if args.only:
        cases = [c for c in cases if args.only in c["name"]]

    passed = 0
    for c in cases:
        system = instruction.replace("{{CURRENT_DATETIME}}", datetime_str(c["now"]))
        user = f"Source: {c['source']}\n\nText:\n{c['text']}"
        try:
            result = call_gemini(key, system, user)
            items = result.get("items", [])
            ok, reason = check(c["expect"], items)
        except Exception as e:  # noqa: BLE001 - surface any failure as a case failure
            ok, reason, items = False, f"error: {e}", []
        passed += ok
        mark = "PASS" if ok else "FAIL"
        print(f"  [{mark}] {c['name']}" + ("" if ok else f"  -> {reason}"))
        if args.verbose:
            print("         items: " + json.dumps(items, ensure_ascii=False))
        time.sleep(0.4)  # be gentle on rate limits

    total = len(cases)
    print(f"\n{passed}/{total} passed.")
    return 0 if passed == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
