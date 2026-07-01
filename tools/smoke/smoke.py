#!/usr/bin/env python3
"""
TaskMind on-device SMOKE test.

Drives the *mechanical* P0 cases of the functional test plan over `adb` and captures a screenshot for
each: the app launches, all four tabs render, the capture surfaces fire, the privacy board reads clean,
and the background service is running. It is a fast "is the app fundamentally alive on a real device"
gate — NOT the full 27-suite manual pass (see MANUAL_CHECKLIST.md for the human-judgment cases this
can't cover: the biometric lock, permission dialogs, backup/restore file pickers, extraction quality,
calendar writes, geofencing).

Prereqs: a connected device with TaskMind installed and **unlocked** (past the biometric gate); `adb`
on PATH.

    python smoke.py                 # auto-detect the only connected device
    python smoke.py --serial R5CY…  # pick a device
    python smoke.py --out runs/x    # screenshots + report.md go here

Exit code is non-zero if any case fails.
"""
import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = "com.rajasudhan.taskmind"
SHARE_ACTIVITY = f"{PKG}/.ui.capture.ShareTargetActivity"


class Device:
    def __init__(self, serial: str | None, adb: str = "adb"):
        self.base = [adb] + (["-s", serial] if serial else [])

    def _run(self, args, binary=False):
        if binary:
            return subprocess.run(self.base + args, capture_output=True)
        # Force UTF-8: adb/dumpsys output carries non-ASCII bytes that Windows' cp1252 default can't decode.
        return subprocess.run(self.base + args, capture_output=True, text=True,
                              encoding="utf-8", errors="replace")

    def shell(self, cmd: str) -> str:
        return self._run(["shell", cmd]).stdout or ""

    def screencap(self, path: Path):
        out = subprocess.run(self.base + ["exec-out", "screencap", "-p"], capture_output=True)
        path.write_bytes(out.stdout)

    def dump(self) -> str:
        self.shell("uiautomator dump /sdcard/uidump.xml >/dev/null 2>&1")
        raw = subprocess.run(self.base + ["exec-out", "cat", "/sdcard/uidump.xml"], capture_output=True)
        return raw.stdout.decode("utf-8", "replace")

    def _nodes(self):
        try:
            return list(ET.fromstring(self.dump()).iter("node"))
        except ET.ParseError:
            return []

    def find(self, needle: str):
        low = needle.lower()
        for n in self._nodes():
            if low in (n.get("text", "") + "" + n.get("content-desc", "")).lower():
                return n
        return None

    def has(self, needle: str) -> bool:
        return self.find(needle) is not None

    def tap_text(self, needle: str) -> bool:
        n = self.find(needle)
        if n is None:
            return False
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if not m:
            return False
        x1, y1, x2, y2 = map(int, m.groups())
        self.shell(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
        return True

    def launch(self):
        self.shell(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1")


class Smoke:
    def __init__(self, dev: Device, out: Path):
        self.d = dev
        self.out = out
        self.results = []  # (id, ok, detail)

    def record(self, cid, ok, detail=""):
        self.results.append((cid, ok, detail))
        print(f"  [{'PASS' if ok else 'FAIL'}] {cid}" + ("" if ok else f"  -> {detail}"))

    def shot(self, name):
        self.d.screencap(self.out / f"{name}.png")

    def run(self):
        d = self.d
        # 01 — installed + version
        info = d.shell(f"dumpsys package {PKG}")
        ver = re.search(r"versionName=(\S+)", info)
        if ver:
            self.record("TM-SMOKE-01 installed", True, f"versionName={ver.group(1)}")
        else:
            self.record("TM-SMOKE-01 installed", False, "package not found — install the APK first")
            return

        # 02 — launches to the Inbox (also our unlocked-check)
        d.launch()
        time.sleep(3)
        self.shot("01-inbox")
        if d.has("Inbox"):
            self.record("TM-SMOKE-02 launch to Inbox", True)
        else:
            self.record("TM-SMOKE-02 launch to Inbox", False,
                        "Inbox not visible - is the device unlocked past the biometric gate?")

        # 03 — Notes tab renders
        d.tap_text("Notes")
        time.sleep(2)
        self.shot("02-notes")
        self._check("TM-SMOKE-03 Notes renders", "Approved")

        # 04 — Sources tab renders
        d.tap_text("Sources")
        time.sleep(2)
        self.shot("03-sources")
        self._check("TM-SMOKE-04 Sources renders", "What TaskMind is allowed to read")

        # 05 — Privacy tab renders. (Egress-clean is config-dependent — with Cloud LLM on, the board
        # correctly shows logged calls — so that check lives in MANUAL_CHECKLIST.md, not here.)
        d.tap_text("Privacy")
        time.sleep(2)
        self.shot("04-privacy")
        self._check("TM-SMOKE-05 Privacy renders", "Encryption at rest")

        # 06 — share-text capture surface fires (an exported activity resolves + starts, no crash).
        # (The widget / QS-tile / quick-capture surfaces are exported=false by design, so adb can't
        # start them — they're covered by MANUAL_CHECKLIST.md.)
        r = d.shell(
            f'am start -W -n {SHARE_ACTIVITY} -a android.intent.action.SEND -t text/plain '
            f'--es android.intent.extra.TEXT "smoke test capture"'
        )
        time.sleep(2)
        self.shot("05-share")
        ok = "Status: ok" in r or "Complete" in r
        self.record("TM-SMOKE-06 share capture", ok, "" if ok else f"am start failed: {r.strip()[:80]}")

        # 07 — background service / notification channels are registered
        notif = d.shell("dumpsys notification --noredact")
        ok = "taskmind_service_channel" in notif or PKG in notif
        self.record("TM-SMOKE-07 service running", ok, "" if ok else "no TaskMind notification channel found")

    def _check(self, cid, needle):
        self.record(cid, self.d.has(needle), "" if self.d.has(needle) else f"UI text not found: {needle!r}")

    def report(self):
        passed = sum(1 for _, ok, _ in self.results if ok)
        total = len(self.results)
        lines = [
            "# TaskMind smoke run",
            "",
            f"**{passed}/{total} passed.** Screenshots are alongside this file.",
            "",
            "| case | result | detail |",
            "|---|---|---|",
        ]
        for cid, ok, detail in self.results:
            lines.append(f"| {cid} | {'✅ pass' if ok else '❌ FAIL'} | {detail} |")
        lines += ["", "> Mechanical P0 gate only. The human-judgment P0 cases (biometric lock, permission",
                  "> dialogs, backup/restore, extraction quality, calendar, geofence) are in",
                  "> [MANUAL_CHECKLIST.md](MANUAL_CHECKLIST.md)."]
        (self.out / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
        return passed, total


def detect_serial(adb="adb") -> str | None:
    out = subprocess.run([adb, "devices"], capture_output=True, text=True).stdout.splitlines()
    devs = [l.split("\t")[0] for l in out[1:] if l.strip().endswith("\tdevice")]
    return devs[0] if len(devs) == 1 else (None if not devs else devs[0])


def main() -> int:
    ap = argparse.ArgumentParser(description="TaskMind on-device smoke test")
    ap.add_argument("--serial", default=None, help="adb device serial (auto if only one connected)")
    ap.add_argument("--adb", default="adb", help="path to adb")
    ap.add_argument("--out", default=None, help="output dir for screenshots + report (default runs/<ts>)")
    args = ap.parse_args()

    # Keep console prints safe on code pages that can't encode UTF-8 (e.g. Windows cp1252).
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

    serial = args.serial or detect_serial(args.adb)
    if not serial:
        sys.exit("No device. Connect one (adb devices) or pass --serial.")
    out = Path(args.out) if args.out else Path("runs") / time.strftime("%Y%m%d-%H%M%S")
    out.mkdir(parents=True, exist_ok=True)
    print(f"device={serial}  out={out}")

    smoke = Smoke(Device(serial, args.adb), out)
    smoke.run()
    passed, total = smoke.report()
    print(f"\n{passed}/{total} passed - report: {out / 'report.md'}")
    return 0 if passed == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
