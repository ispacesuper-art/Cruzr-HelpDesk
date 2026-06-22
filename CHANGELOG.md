# Changelog

All notable changes to the USC Cruzr Help Desk app are documented here.

## [2.6] — 2026-06-19

### Fixed
- **Voice assistant still interfering:** Faster AssistantManager keep-alive (1.5 s), deferred `LeisureManager.prohibitedLeisure()` via async init (starts 5 s after open — avoids v2.4 startup crash).
- **Mic stolen mid-session:** Re-requests the microphone when the competition API reports the assistant took speech resources back.
- **Broader speech competition:** Also requests optional `audio/stream.speech` alongside recognizer and synthesizer.

## [2.5] — 2026-06-19

### Fixed
- **Startup crash ("Help Desk has stopped"):** Removed `LeisureManager` calls from v2.4 — they crash on some Cruzr builds at app open. Assistant suppression now uses `AssistantManager` keep-alive only.
- Wrapped robot service initialization in a try/catch so a SDK failure shows an error screen instead of closing the app.

### Note
- v2.4 should not be deployed; use v2.5 instead.

## [2.4] — 2026-06-19

### Fixed
- **Cruzr voice assistant interference:** Re-applies suppression every 2 seconds while Help Desk is open (the system assistant can turn itself back on).
- **`LeisureManager.prohibitedLeisure()`** — blocks idle/wake leisure behaviours that compete for the mic.
- Disables voice/wakeup leisure entries via `enableLeisure(key, false)` when detected.

### Changed
- Suppression also runs immediately before each microphone access request.

## [2.3] — 2026-06-19

### Fixed
- **Stopped listening after first answer:** After TTS, the app now force-restarts a fresh recognition session instead of trusting a stale `isRecognizing()` state from barge-in listening during speech.
- **`awaitingResponse` deadlock:** Cleared in a `finally` block after each final utterance so restart scheduling is never blocked.
- **Missing TTS callback:** Added a duration-based fallback timer when Cruzr synthesis `onDone` does not fire, so listening resumes even if the SDK callback is lost.

## [2.2] — 2026-06-19

### Fixed
- **ASR never finishing / no responses:** Replaced `MODE_CONTINUOUS` with `MODE_SINGLE` auto-restart. Continuous mode on Cruzr did not emit final utterance results, so the app never answered.
- Listening now ends when the user **pauses after speaking** (same behaviour as the original tap-to-listen flow).

### Changed
- Auto-listen restarts after each spoken answer (600 ms delay).
- Voice **barge-in** during TTS: speaking again or tapping **Interrupt** cancels the current reply.

## [2.1] — 2026-06-19

### Added
- **Always-on ASR** — listening starts automatically when the app opens (no Listen tap required).
- **`ContinuousSpeechController`** — manages recognition, synthesis, and interrupt logic.
- **Interrupt** button label while the robot is speaking.
- **Stop** pauses listening; **Listen** resumes.

### Note
- v2.1 used `MODE_CONTINUOUS`, which caused “never stops listening / no response” on robot hardware. Fixed in v2.2.

## [2.0] — 2026-06-19

### Added
- **`RobotBootstrap`** — safe SDK init so startup failures do not crash the app.
- **`VoiceAssistantController`** — pauses Cruzr voice assistant via `AssistantManager` (LeisureManager removed from startup for stability).
- Topic buttons use `AppCompatButton` instead of dynamic Material buttons (tablet compatibility).

### Removed
- Map / navigation integration (v1.6–v1.8 caused crashes or deploy issues).

## [1.5] — 2026-06-19

### Added
- Mic competition via `SpeechResourceController` (recognizer + synthesizer).
- Assistant pause on app open; restore on exit.
- `LeisureManager.prohibitedLeisure()` during Help Desk session (later removed in v2.0).

## [1.0–1.4] — 2026-06-19

- Initial Help Desk app: STT, TTS, canned topics, CBIS-signed release APKs.
- Custom APK naming: `USC-Cruzr-HelpDesk-v{version}-{debug|release}.apk`.
- Valid launcher icon, `minSdk 21`, `targetSdk 26`.
