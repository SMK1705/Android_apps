#!/usr/bin/env python3
"""Download a Tesseract English model and install it into TaskMind's private storage.

On-device screenshot OCR (Tesseract) needs `eng.traineddata` at
`/data/data/com.rajasudhan.taskmind/files/tessdata/eng.traineddata`. The model is deliberately
NOT bundled in the APK. Requires: Python 3.8+, `adb` on PATH, and a debug build of TaskMind
installed (the script uses `adb run-as`, which only works on debuggable apps).
"""
import argparse
import os
import subprocess
import sys
import tempfile
import urllib.request

PKG = "com.rajasudhan.taskmind"
# "fast" variant: ~12 MB, plenty accurate for screenshots and quick on mobile.
DEFAULT_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata"


def adb(args, device=None):
    cmd = ["adb"] + (["-s", device] if device else []) + args
    return subprocess.run(cmd, check=True, capture_output=True, text=True)


def main():
    ap = argparse.ArgumentParser(description="Install eng.traineddata into TaskMind for on-device OCR.")
    ap.add_argument("--device", help="adb serial, if more than one device is connected")
    ap.add_argument("--url", default=DEFAULT_URL, help="traineddata URL (default: tessdata_fast English)")
    args = ap.parse_args()

    with tempfile.TemporaryDirectory() as tmp:
        local = os.path.join(tmp, "eng.traineddata")
        print(f"Downloading {args.url} ...")
        urllib.request.urlretrieve(args.url, local)
        print(f"Downloaded {os.path.getsize(local) / (1024 * 1024):.1f} MB")

        staging = f"/sdcard/Android/data/{PKG}/files/eng.traineddata"
        print("Pushing to device ...")
        adb(["push", local, staging], args.device)

        # Copy into the app's private tessdata/ folder, as the app's own uid.
        script = (
            f"mkdir -p /data/data/{PKG}/files/tessdata && "
            f"cp /storage/emulated/0/Android/data/{PKG}/files/eng.traineddata "
            f"/data/data/{PKG}/files/tessdata/eng.traineddata"
        )
        adb(["shell", "run-as", PKG, "sh", "-c", script], args.device)

    print(f"Installed to /data/data/{PKG}/files/tessdata/eng.traineddata")
    print("Open TaskMind -> Settings -> Check OCR model to confirm.")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as e:
        print(e.stderr or str(e), file=sys.stderr)
        sys.exit(1)
