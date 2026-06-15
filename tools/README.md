# tools

Developer helper scripts for TaskMind (not shipped in the app).

## `setup_vosk_model.py` / `setup_vosk_model.sh`

Downloads a [Vosk](https://alphacephei.com/vosk/models) speech model and installs it into the app's
private storage on a connected device, so on-device transcription (call/voice recordings and the
Inbox voice-note button) works. The model is deliberately **not** bundled in the APK.

```bash
# Default small Indian-English model (~36 MB)
python tools/setup_vosk_model.py
# or, on Unix:
tools/setup_vosk_model.sh

# Target a specific device / a different model
python tools/setup_vosk_model.py --device <adb-serial> \
  --model-url https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
```

**Requirements:** Python 3.8+, `adb` on PATH, and a **debug** build of TaskMind installed (the script
uses `adb run-as`, which only works on debuggable apps).

After it finishes, open the app → **Settings → Check transcription model** to confirm it loaded.
