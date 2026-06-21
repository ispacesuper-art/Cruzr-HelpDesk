# Help Desk App for Cruzr

Voice-driven help desk for the Cruzr robot. Guests speak a question; the app uses **Cruzr speech recognition (STT)**, matches it to **canned answers**, and speaks the reply via **Cruzr TTS**.

Inspired by the Cruzr platform app layout: landscape tablet UI, microphone access, and Ubtech speech services.

## Features

- **Listen** — Cruzr `SpeechManager.recognize()` for speech-to-text
- **Canned responses** — keyword matching against `app/src/main/assets/help_topics.json`
- **Quick topic buttons** — tap instead of speaking (Opening hours, Wi-Fi, Library, etc.)
- **Welcome message** — spoken greeting on demand
- **Robot TTS** — answers spoken through `SpeechManager.synthesize()`

## Prerequisites

- Android Studio
- **`cruzr.jar`** in `app/cruzr-libs/` (included in this project; replace with your SDK copy if needed)
- Physical **Cruzr robot** (speech services are not available on a normal phone/emulator)

## Build

1. **File → Open** → select the `HelpDeskApp` folder
2. Wait for Gradle sync
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**

APK output (uniquely named to avoid CBIS duplicate-name issues):

```
app/build/outputs/apk/debug/USC-Cruzr-HelpDesk-v1.0-debug.apk
```

A copy for upload is also placed in:

```
release/USC-Cruzr-HelpDesk-v1.0-debug.apk
```

Or from a terminal:

```powershell
cd "C:\Users\atw010\OneDrive - University of the Sunshine Coast\Desktop\Cruzr\HelpDeskApp"
.\gradlew.bat assembleDebug
```

## Install on the Cruzr robot

1. Connect PC and robot to the **same Wi‑Fi**
2. Enable **Developer options** and **USB debugging** on the robot
3. Connect via ADB:

```powershell
adb connect ROBOT_IP:5555
adb devices
```

4. Install:

```powershell
adb install -r "C:\Users\atw010\OneDrive - University of the Sunshine Coast\Desktop\Cruzr\HelpDeskApp\release\USC-Cruzr-HelpDesk-v1.0-debug.apk"
```

5. Open **Help Desk** from the app drawer

**Tip:** Stop or disable the Cruzr voice assistant first so this app can use the microphone.

## How to use

1. Allow **Microphone** when prompted
2. Tap **Welcome message** for a spoken greeting, or
3. Tap **Listen** → ask e.g. “What are your opening hours?” → wait for the spoken answer
4. Or tap a **Quick topic** button for a canned response without speaking

## Customise answers

Edit `app/src/main/assets/help_topics.json`:

- **`label`** — button text
- **`keywords`** — phrases that trigger the topic
- **`response`** — what the robot says

Rebuild and reinstall after changes.

## Package name

`com.usc.cruzr.helpdesk`

## Troubleshooting

| Issue | Likely cause |
|-------|----------------|
| No speech recognition | Not running on Cruzr; voice assistant holding the mic |
| “Speech recognition failed” | Network/cloud ASR issue; try quick topic buttons |
| App won’t install | Uninstall older debug build first; use `adb uninstall com.usc.cruzr.helpdesk` |
| No TTS | Cruzr speech service busy; reboot robot or stop other speech apps |

## CBIS deployment

For **Remote Config → Application** in CBIS, use a **signed release APK** with `targetSdk` 28–33. For development, **ADB install** is faster.
