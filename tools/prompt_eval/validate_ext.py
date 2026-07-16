#!/usr/bin/env python3
"""Validate LLM-authored eval scenarios before they're trusted as gold.

Input: a JSON file {"cases":[...golden-set case objects...], "relabels":{name:{type,due_date}}}.
Gates: (1) schema lint; (2) never-past-date invariant + calendar validity; (3) author-vs-blind-relabeler
agreement on type and due_date. Emits a cleaned golden_set_ext.jsonl (all lint+date-valid cases — the
frontier is allowed to be hard) and a validation report flagging disagreements for review.
"""
import argparse, json, sys, datetime, collections

TYPES = {"reminder", "todo", "note", "waiting_on"}
TAGS = {"Money", "Health", "Family", "Work", "Shopping", "Travel", "Home"}
MATCHER_KEYS = {"type", "title_contains", "notes_contains", "location_contains", "counterparty_contains",
                "location", "due_date", "due_time", "recurrence", "priority", "tags_contains", "tags", "min_confidence"}
CASE_KEYS = {"name", "source", "text", "now", "expect", "category", "max_items", "mode", "current"}


def expected_type(expect):
    if isinstance(expect, dict) and expect.get("items") == 0:
        return "none"
    if isinstance(expect, list) and expect:
        for m in expect:
            if isinstance(m, dict) and "type" in m:
                return m["type"]
        return "untyped"
    return "?"


def expected_due(expect):
    if isinstance(expect, list) and expect and isinstance(expect[0], dict):
        return expect[0].get("due_date")
    return None


def lint(c):
    errs = []
    for k in c:
        if k not in CASE_KEYS:
            errs.append(f"unknown case key {k!r}")
    for k in ("name", "source", "text", "now", "expect"):
        if k not in c:
            errs.append(f"missing {k!r}")
    now = c.get("now", "")
    try:
        ndt = datetime.datetime.strptime(now[:16], "%Y-%m-%dT%H:%M")
    except Exception:
        return errs + [f"bad now {now!r}"], None
    exp = c.get("expect")
    if isinstance(exp, dict):
        if exp.get("items") != 0:
            errs.append(f"dict expect must be {{'items':0}}, got {exp}")
    elif isinstance(exp, list):
        if not exp:
            errs.append("empty expect list")
        for m in exp:
            if not isinstance(m, dict):
                errs.append("matcher not an object"); continue
            for k in m:
                if k not in MATCHER_KEYS:
                    errs.append(f"unknown matcher key {k!r}")
            if m.get("type") and m["type"] not in TYPES:
                errs.append(f"bad type {m['type']!r}")
            if "priority" in m and m["priority"] not in ("normal", "high", "low"):
                errs.append(f"bad priority {m['priority']!r}")
            for tk in ("tags_contains",):
                if tk in m and m[tk] not in TAGS:
                    errs.append(f"tag {m[tk]!r} not in taxonomy")
            if isinstance(m.get("tags"), list):
                for t in m["tags"]:
                    if t not in TAGS:
                        errs.append(f"tags[] {t!r} not in taxonomy")
            dd = m.get("due_date")
            if dd is not None:
                try:
                    d = datetime.date.fromisoformat(dd)
                    if d < ndt.date():
                        errs.append(f"PAST due_date {dd} < now {ndt.date()}")
                except Exception:
                    errs.append(f"bad due_date {dd!r}")
            dt = m.get("due_time")
            if dt is not None:
                try:
                    datetime.datetime.strptime(dt, "%H:%M")
                except Exception:
                    errs.append(f"bad due_time {dt!r}")
            if m.get("recurrence") not in (None, "daily", "weekly", "monthly"):
                errs.append(f"bad recurrence {m['recurrence']!r}")
        if c.get("mode") != "edit":  # edit cases legitimately assert only the changed field (no type/anchor)
            if not any(isinstance(m, dict) and "type" in m for m in exp):
                errs.append("no matcher pins a type (needed for the confusion matrix)")
            first = exp[0] if isinstance(exp[0], dict) else {}
            if not any(k in first for k in ("title_contains", "notes_contains", "location_contains", "counterparty_contains")):
                errs.append("first matcher lacks a *_contains identity anchor")
    else:
        errs.append(f"expect must be a list or {{'items':0}}")
    # edit cases
    if c.get("mode") == "edit" and "current" not in c:
        errs.append("edit case missing 'current'")
    # name/prefix
    cat = c.get("category", "")
    if cat and not str(c.get("name", "")).startswith(cat):
        errs.append(f"name {c.get('name')!r} does not start with category {cat!r}")
    return errs, ndt


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="inp", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--report", required=True)
    a = ap.parse_args()

    data = json.load(open(a.inp, encoding="utf-8"))
    cases = data.get("cases", [])
    relabels = data.get("relabels", {})

    seen, kept, dropped, type_disagree, date_disagree = set(), [], [], [], []
    by_cat = collections.Counter()
    for c in cases:
        name = c.get("name", "")
        errs, ndt = lint(c)
        if name in seen:
            errs.append("duplicate name")
        seen.add(name)
        if errs:
            dropped.append((name, c.get("category", ""), errs))
            continue
        # author vs blind relabeler
        et = expected_type(c["expect"])
        rl = relabels.get(name)
        if rl:
            rt = rl.get("type")
            # collapse untyped author cases (can't compare type)
            if et not in ("untyped", "?") and rt and rt != et:
                type_disagree.append((name, c.get("category", ""), et, rt, c.get("text", "")[:80]))
            ed = expected_due(c["expect"])
            rd = (rl.get("due_date") or "").strip()
            if ed and rd and ed != rd:
                date_disagree.append((name, et, ed, rd, c.get("text", "")[:80]))
        kept.append(c)
        by_cat[c.get("category", "?")] += 1

    with open(a.out, "w", encoding="utf-8", newline="\n") as f:
        for c in kept:
            f.write(json.dumps(c, ensure_ascii=False) + "\n")

    L = []
    L.append(f"# Ext scenario validation\n")
    L.append(f"- authored (parseable): **{len(cases)}**")
    L.append(f"- kept (lint + date-valid): **{len(kept)}**")
    L.append(f"- dropped (lint/date fail): **{len(dropped)}**")
    L.append(f"- type disagreements (author vs blind relabeler): **{len(type_disagree)}** — REVIEW")
    L.append(f"- due_date disagreements: **{len(date_disagree)}** — REVIEW")
    L.append(f"\n## Kept by category\n")
    for cat, n in sorted(by_cat.items()):
        L.append(f"- `{cat}` {n}")
    if dropped:
        L.append(f"\n## Dropped ({len(dropped)})\n")
        for name, cat, errs in dropped[:120]:
            L.append(f"- `{name}` ({cat}): {'; '.join(errs)}")
    if type_disagree:
        L.append(f"\n## Type disagreements ({len(type_disagree)}) — author vs blind relabeler\n")
        for name, cat, et, rt, txt in type_disagree[:120]:
            L.append(f"- `{name}` ({cat}): author=**{et}** relabeler=**{rt}** — \"{txt}\"")
    if date_disagree:
        L.append(f"\n## due_date disagreements ({len(date_disagree)})\n")
        for name, et, ed, rd, txt in date_disagree[:120]:
            L.append(f"- `{name}` ({et}): author={ed} relabeler={rd} — \"{txt}\"")
    open(a.report, "w", encoding="utf-8", newline="\n").write("\n".join(L) + "\n")
    print(f"kept {len(kept)}/{len(cases)}; dropped {len(dropped)}; type-disagree {len(type_disagree)}; date-disagree {len(date_disagree)}")
    print(f"wrote {a.out} and {a.report}")


if __name__ == "__main__":
    main()
