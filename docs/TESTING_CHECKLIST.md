# Quality Assurance & Testing Checklist

Use this checklist to verify application integrity prior to release or distribution.

---

## 1. Call Recording Quality & Compliance
- [ ] **First-Time Consent Dialog**:
  - [ ] App prompts with legal consent dialog before the first recording begins.
  - [ ] Rejecting consent aborts recording cleanly.
  - [ ] Accepting consent triggers standard runtime `RECORD_AUDIO` permission check.
- [ ] **Audible Beep Tones**:
  - [ ] 300ms beep sounds at recording initiation.
  - [ ] 300ms beep sounds at recording termination.
- [ ] **In-Call Visuals**:
  - [ ] Red recording indicator pulses.
  - [ ] Live elapsed timer counts up in `MM:SS` format.
  - [ ] Foreground notification shows recording state with quick-stop action.
- [ ] **File Storage & Post-Call Review**:
  - [ ] Audio file saved to `/Download/CallRecordings/` with correct naming syntax.
  - [ ] Recording Review dialog shows playable audio with scrubber, duration, and file size.
  - [ ] Deleting file removes both database entry and physical file.
  - [ ] Sharing file dispatches Android sharesheet with secure `content://` URI.

---

## 2. Theming & AMOLED Black Diamond
- [ ] **Default Theme**:
  - [ ] Clean install initializes in Light Mode.
- [ ] **Theme Switching**:
  - [ ] Toggling Dark Mode in Settings switches to Black Diamond Theme.
  - [ ] Background is pure AMOLED `#000000`.
  - [ ] Accents display Electric Cyan `#00E5FF` highlights.
  - [ ] All dialpad text and icons remain high-contrast and legible.
- [ ] **State Persistence**:
  - [ ] Theme choice persists across full app restarts and OS reboots.

---

## 3. Video Calling (Jitsi Meet)
- [ ] **Outbound Video Call**:
  - [ ] Video call button launches `VideoCallActivity` with camera and mic active.
  - [ ] Active cellular call is auto-muted when video connects.
- [ ] **Rejoin Protection**:
  - [ ] Pressing Android Home or Back prompts `VideoCallRejoinDialog`.
  - [ ] Tapping "Rejoin" brings the call back into focus without disconnection.

---

## 4. Voice Dialing & Smart Search
- [ ] **Local Matching**:
  - [ ] Dialpad voice button captures spoken speech input.
  - [ ] Local fuzzy matcher resolves spoken contact names (e.g., "Call Mom") accurately.
- [ ] **AI-Assisted Matching (Gemini)**:
  - [ ] When `gemini.properties` key is provided, extracts intent and contacts accurately.
  - [ ] If network is disconnected or API key is absent, gracefully falls back to local contact matching.

---

## 5. Standard Phone Capabilities
- [ ] Inbound and outbound cellular voice calls.
- [ ] Speed dial management.
- [ ] Call waiting audio and banner notifications.
- [ ] Contact history export/import.

