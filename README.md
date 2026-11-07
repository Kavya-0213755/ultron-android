# Ultron for Android

Native Android app for Ultron, the personal assistant that runs on the home PC.

* Chat with Ultron (streaming answers, approvals for risky actions).
* Voice: tap the mic, or turn on **“Hey Ultron”** — the wake word is detected offline on the phone
  (Vosk), the request is turned into text by the PC (free Groq Whisper / Gemini) and the answer is spoken
  with the phone's own voice.
* Connects at home over Wi-Fi (`http://192.168.x.x:8765`) and from anywhere through Tailscale.
* Can be set as the phone's assistant (long-press home/power) and has a “Talk to Ultron” shortcut.

Every push builds `Ultron.apk` with GitHub Actions (free) and publishes it as the **latest** release.
