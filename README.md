# Help Desk App for Cruzr

Voice-driven help desk for the **USC Cruzr** robot. Guests speak a question; the app uses **Cruzr speech recognition (STT)**, matches it to **canned answers**, and speaks the reply via **Cruzr TTS**.

**Current version:** 2.7 · **Package:** `com.usc.cruzr.helpdesk`

See [CHANGELOG.md](CHANGELOG.md) for version history.

## Features

- **Auto-listen ASR** — starts listening when the app opens; speak, pause, get an answer; listens again after each reply
- **Interrupt / barge-in** — talk over the robot or tap **Interrupt** to stop the current reply
- **Canned responses** — keyword matching in `app/src/main/assets/help_topics.json`
- **Quick topic buttons** — tap instead of speaking
- **Welcome message** — spoken greeting on demand
- **Voice assistant handoff** — aggressively pauses Cruzr system assistant while Help Desk is open (re-applied every 2 s; restored on exit)
- **Mic competition** — requests recognizer + synthesizer via Cruzr competition API

## Architecture

| Class | Role |
|-------|------|
| `CruzrApp` | `Robot.initialize()` on startup |
| `RobotBootstrap` | Safe SDK init; avoids crash if services unavailable |
| `MainActivity` | UI, topic buttons, permissions |
| `ContinuousSpeechController` | Auto ASR loop, TTS, interrupt handling |
| `SpeechResourceController` | Mic/speech resource competition |
| `VoiceAssistantController` | AssistantManager keep-alive (no LeisureManager — crashes on Sunny) |
| `HelpDeskEngine` | Load topics + keyword matching |

## Prerequisites

- Android Studio
- **`cruzr.jar`** in `app/cruzr-libs/` (Ubtech SDK; keep repo private if required by license)
- Physical **Cruzr robot** (speech services do not run on a normal phone/emulator)
- Signed release keystore for CBIS (`keystore.properties` + `helpdesk-release.jks` — not in git)

## Build

```powershell
cd HelpDeskApp
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
```

Release APK:

```
app/build/outputs/apk/release/USC-Cruzr-HelpDesk-v2.7-release.apk
release/USC-Cruzr-HelpDesk-v2.7-release.apk
```

## Install on Cruzr

### CBIS (recommended for fleet deploy)

1. **CBIS → Remote Config → Application**
2. Upload `USC-Cruzr-HelpDesk-v2.7-release.apk`
3. Package: `com.usc.cruzr.helpdesk`
4. Entry activity: `com.usc.cruzr.helpdesk.MainActivity`
5. Ensure robot is **Online** before assigning

### ADB (development)

```powershell
adb connect ROBOT_IP:5555
adb install -r "release\USC-Cruzr-HelpDesk-v2.7-release.apk"
```

## How to use

1. Allow **Microphone** when prompted
2. Status shows **“Listening… speak your question, then pause.”**
3. Ask e.g. *“Where is the library?”* — **pause briefly** so the robot knows you are done
4. Robot speaks the matched answer, then listens again
5. While it is speaking, say something new or tap **Interrupt**
6. Tap **Stop** to pause listening; tap **Listen** to resume
7. Use **Quick topic** buttons anytime (no mic needed)

## Customise answers

Edit `app/src/main/assets/help_topics.json`:

| Field | Purpose |
|-------|---------|
| `label` | Button text |
| `keywords` | Phrases that trigger the topic |
| `response` | Spoken + on-screen answer |

Rebuild, commit, and redeploy after changes.

## Git / GitHub

Repository: [github.com/ispacesuper-art/Cruzr-HelpDesk](https://github.com/ispacesuper-art/Cruzr-HelpDesk)

```powershell
git add -A
git commit -m "Describe your change"
git push origin master
```

Update **CHANGELOG.md** when bumping `versionName` in `app/build.gradle`.

## Troubleshooting

| Issue | What to try |
|-------|-------------|
| App crashes on open | Deploy v2.0+ (hardened startup). Remove stuck CBIS assignment and reinstall. |
| Listens forever, no answer | Use **v2.2+** (single-utterance mode). Pause after speaking. |
| Works once, then stops listening | Use **v2.3+** (force-restart after each answer). Tap **Listen** if needed. |
| “Speech recognition failed” | Voice assistant may hold mic; topic buttons still work. Reopen app. |
| App crashes on open | Deploy **v2.7+** (v2.4/v2.6 LeisureManager crash). Remove stuck CBIS install first. |
| Crashes a few seconds after open | Use **v2.7** — removes the deferred 5 s leisure call. |
| Mic busy / voice assistant interference | **v2.7** — AssistantManager every 1.5 s + mic re-acquire. Leisure API not used (crashes on Sunny). |
| CBIS stuck “Configurating” | Confirm robot **Online**; remove/reassign app in CBIS. |
| No TTS | Check Cruzr speech service / network. Reboot robot if needed. |

## License / SDK note

`cruzr.jar` is Ubtech proprietary SDK material. Do not redistribute publicly unless permitted by your Ubtech/USC agreement.
