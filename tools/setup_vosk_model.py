#!/usr/bin/env python3
"""Download a Vosk speech model and install it into the TaskMind app on a connected device.

TaskMind transcribes call/voice recordings and voice notes entirely on-device with Vosk, but the
model is intentionally NOT bundled in the APK (it's tens of MB). This helper downloads a model and
copies it into the app's private storage via `adb run-as`, where TaskMind unpacks it on first use.

The app looks for either of these under its internal files dir:
  - an unpacked model directory  files/vosk-model/   (the folder containing conf/), or
  - a zip                        files/vosk-model.zip (unpacked automatically on first use)

This script pushes the zip form, so the app handles unpacking.

Examples:
    python tools/setup_vosk_model.py                 # default small Indian-English model
    python tools/setup_vosk_model.py --device R5CR   # target a specific adb serial
    python tools/setup_vosk_model.py --model-url https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip

Requirements: Python 3.8+, `adb` on PATH, a debuggable (debug-build) TaskMind install on the device.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

# Small Indian-English model (~36 MB). Swap with --model-url for other languages/sizes.
DEFAULT_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"
DEFAULT_PACKAGE = "com.rajasudhan.taskmind"
DEVICE_TMP = "/data/local/tmp/taskmind-vosk-model.zip"
INTERNAL_REL = "files/vosk-model.zip"  # relative to the app's run-as home (the data dir)


def adb_base(device: str | None) -> list[str]:
    """adb command prefix, optionally pinned to a device serial."""
    return ["adb"] + (["-s", device] if device else [])


def run(cmd: list[str], **kwargs) -> subprocess.CompletedProcess:
    print(f"  $ {' '.join(cmd)}")
    return subprocess.run(cmd, check=True, text=True, **kwargs)


def ensure_adb_device(device: str | None) -> None:
    if shutil.which("adb") is None:
        sys.exit("error: `adb` not found on PATH. Install Android platform-tools first.")
    result = subprocess.run(
        adb_base(device) + ["get-state"], text=True, capture_output=True
    )
    if result.returncode != 0 or result.stdout.strip() != "device":
        sys.exit(
            "error: no ready device. Plug in a device / start an emulator and check `adb devices`."
        )


def download(url: str, dest: Path) -> None:
    print(f"Downloading model:\n  {url}")

    def _progress(block_num: int, block_size: int, total: int) -> None:
        if total > 0:
            pct = min(100, block_num * block_size * 100 // total)
            print(f"\r  {pct:3d}%", end="", flush=True)

    urllib.request.urlretrieve(url, dest, _progress)  # noqa: S310 (trusted vosk URL)
    print(f"\r  done ({dest.stat().st_size // (1024 * 1024)} MB)")


def install(device: str | None, package: str, zip_path: Path) -> None:
    base = adb_base(device)
    print(f"Installing into {package} …")
    run(base + ["push", str(zip_path), DEVICE_TMP])
    # `run-as` runs inside the app sandbox, so we can write to its private files dir on a debug build.
    run(base + ["shell", "run-as", package, "mkdir", "-p", "files"])
    run(base + ["shell", "run-as", package, "cp", DEVICE_TMP, INTERNAL_REL])
    # Drop any previously unpacked model so the new zip is the one that gets expanded.
    subprocess.run(
        base + ["shell", "run-as", package, "rm", "-rf", "files/vosk-model"],
        text=True,
    )
    run(base + ["shell", "rm", "-f", DEVICE_TMP])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--model-url", default=DEFAULT_MODEL_URL, help="Vosk model .zip URL")
    parser.add_argument("--package", default=DEFAULT_PACKAGE, help="App applicationId")
    parser.add_argument("--device", default=None, help="adb device serial (optional)")
    parser.add_argument("--keep", action="store_true", help="Keep the downloaded zip after install")
    args = parser.parse_args()

    ensure_adb_device(args.device)

    tmp_dir = Path(tempfile.mkdtemp(prefix="taskmind-vosk-"))
    zip_path = tmp_dir / "vosk-model.zip"
    try:
        download(args.model_url, zip_path)
        install(args.device, args.package, zip_path)
    except subprocess.CalledProcessError as e:
        sys.exit(f"error: adb step failed ({e.returncode}). Is this a debug build that allows run-as?")
    finally:
        if args.keep:
            final = Path.cwd() / "vosk-model.zip"
            shutil.move(str(zip_path), str(final))
            print(f"Kept download at {final}")
        shutil.rmtree(tmp_dir, ignore_errors=True)

    print(
        "\nDone. Open TaskMind → Settings → 'Check transcription model' to confirm it loads,\n"
        "then use the mic button on the Inbox to add a note by voice."
    )


if __name__ == "__main__":
    main()
