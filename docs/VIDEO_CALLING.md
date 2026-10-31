# Integrated Video Calling (Jitsi Meet)

## Overview

**SimplyDial** includes built-in video calling capabilities powered by the open-source Jitsi Meet ecosystem. Video calls can be initiated directly from contact profiles or dialpad actions without third-party vendor lock-in.

---

## Architecture & Features

### 1. Auto-Mute Voice Call Transition
When initiating or accepting a video call during an active cellular or SIP voice call:
- `VideoCallManager.muteVoiceCallIfActive()` mutes the device microphone on the cellular connection.
- Prevents microphone hardware contention and dual audio feedback loops.
- Preserves voice call state so the user can smoothly return if desired.

### 2. Video Call Rejoin & Navigation Protection
When a user accidentally navigates away from an active video conference (e.g. presses back or switches apps):
- `VideoCallRejoinDialog` alerts the user.
- Provides immediate one-tap options to:
  1. **Rejoin**: Instantly refocuses the video conference room.
  2. **End Call**: Gracefully terminates the session and frees hardware media streams.

### 3. SMS Room Invitation & Coordination
- Room identifiers are securely generated with high entropy.
- `VideoCallSmsReceiver` captures and formats inbound room invitation links.
- Recipients can join using any standard browser or native Jitsi client via standard HTTPS links (`https://meet.jit.si/<room_id>`).

---

## Technical Specifications

- **Default Bridge**: `meet.jit.si` (or custom configurable self-hosted Jitsi instance)
- **Permissions Required**: `CAMERA`, `RECORD_AUDIO`, `INTERNET`
- **Activities & Components**:
  - `VideoCallActivity.kt` - Main interactive video conference activity
  - `IncomingVideoCallActivity.kt` - Incoming video call alert screen
  - `VideoCallOptionsDialog.kt` - Video configuration bottom sheet
  - `VideoCallRejoinDialog.kt` - Exit interception & resume modal

