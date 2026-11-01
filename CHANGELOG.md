# Changelog

All notable changes to **SimplyDial** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.7.0] - 2026-11-01 - Call Waiting & Caller ID Architecture Fixes

### 🎛️ Added — Dynamic Interface Mode System & Modern UI/UX
- **Dual Interface Modes**: Added Simple Mode (senior-friendly, 64dp touch targets, Accidental Call Shield, clutter-free) and Modern & Advanced Mode (power-user, Material 3, WhatsApp, Jitsi Video, multi-SIM badges).
- **Interactive Call History Filter Chips**: Real-time filtering by All, Missed (with live count badges), Incoming, and Outgoing calls with tactile haptics.
- **Universal Quick Actions Bottom Sheet (`QuickActionsHelper`)**: Modern slide-up sheet on contacts and recents with 1-tap WhatsApp chat, Jitsi Meet video, SMS, number copying, contact sharing, and call history.
- **Smart Clipboard Detection**: Live phone number detection in clipboard with a 1-tap paste chip on the dialpad.
- **Official 3D SimplyDial Logo**: High-res squircle 3D brand logo across launcher mipmaps, Settings header, and About dialog.

### 📞 Fixed & Added — Call Waiting & Caller ID Engine
- **1. Call Waiting Tone & Vibration Alert (`CallWaitingAlertHelper`)**: Plays telecom-standard call waiting tones (`ToneGenerator.TONE_SUP_CALL_WAITING`) through `STREAM_VOICE_CALL` directly into the earpiece/headset with repeating double-pulse vibrations every 5 seconds.
- **2. High-Priority Heads-Up Banner (`CallNotificationManager`)**: Dedicated notification channel (`simple_dialer_call_waiting_active_v3`) with full-screen intent displaying caller Name, Number, Avatar, and instant Accept/Decline action buttons.
- **3. In-Call Caller ID Display (`CallActivity`)**: Automatically binds and displays waiting caller details (Name, Number, and Photo) on the active call screen instead of stale primary call info.
- **4. Dedicated Accept & Decline Controls**: Added intuitive controls and swipe buttons specifically for handling the incoming waiting call.
- **5. Decline Fix (`declineCall()`)**: Rejecting a second call now dismisses only the waiting call, allowing the user to seamlessly continue their ongoing conversation without hanging up.
- **6. Active Call Display**: Displays current ongoing caller information separately in the top status bar (`"Active: [Name]"`).
- **7. Carrier/OEM Call Waiting Interception (`CarrierCallWaitingNotificationManager`)**: Captures and alerts carrier-level second-call attempts via `PhoneStateListener` even if not routed through `InCallService`.
- **8. Missed Call Waiting Tracker (`CallWaitingAttemptTracker`, `CallWaitingMissedTracker`)**: Logs and notifies users of missed second-call attempts.

### 🔒 Security, Privacy & Repository Polish
- **Header-Based Security & Privacy**: Removed cloud AI dependencies; zero telemetry or external API credentials needed.
- **Shielded Logcat**: Removed HTTP body logging interceptors to ensure private contact lists and numbers are never printed to system logs.
- **Modernized OkHttp API**: Updated `RequestBody.create()` to modern `toRequestBody()` extension.
- **Standalone CI Pipeline**: Added automated GitHub Actions build workflow (`.github/workflows/ci.yml`).
- **Community Hubs**: Added Discord (`discord.gg/hQebWS8qZ`), Reddit (`r/SimplyDial07`), Telegram (`t.me/SimplyDial`), and email links into in-app About Dialog and docs.

---

## [1.0.0] - 2025-10-31 - Complete Feature Suite & Inception Build (July – October 2025)

### 🚀 Highlights
- Project initiated, conceptualized, and primarily built by **Piyush Kumar** between **July 2025 and October 2025**, with engineering co-management by his brother, **Manish Kumar**.
- All core features of SimplyDial were designed, tested, and implemented during this intensive development cycle.

### 👵 EaseUI Accessibility Suite
- **Accidental Call Shield**: Intelligent confirmation popup modal presenting contact photo, full name, number, and an explicit "Tap to Call" confirmation action before dialing.
- **Messages Joystick (`JoystickMessageButton`)**: Ergonomic radial swipe control enabling fast, pre-configured emergency and daily SMS messaging without typing.
- **Tactile Swipe Answering (`SwipeCallButtonsView`)**: Ergonomic vertical swipe-up to accept and swipe-down to decline incoming phone calls.
- **Haptic Ripple Feedback**: Visual water-ripple animations and tactile response across dialer keypad presses.
- **Gradient Ring Contact Inspect**: Tapping contact avatar rings with glowing animated gradient rings allows quick inspection of detailed contact profiles.
- **Full Hindi Localization**: Comprehensive in-app Hindi language translation (`values-hi`) alongside English.

### 💎 Black Diamond AMOLED Theming & UI
- **AMOLED Pure Black (`#000000`)**: Zero-pixel-bleed true black canvas tailored for OLED and AMOLED screens to maximize battery life.
- **Electric Cyan Glow (`#00E5FF`)**: High-contrast modern accent colors across keypad buttons, floating action buttons, and active call indicators.
- **Glassmorphic Overlays (`BlurBackgroundView`)**: Dynamic frosted glass backdrop rendering with real-time blur and light/dark theme adaptation.
- **Independent Theme Selector**: Dedicated toggle in Settings for Light Mode, Black Diamond Dark Mode, or System Default.

### 🎙️ High-Definition Compliant Call Recording
- **Studio-Grade Audio Encoding**: Stereo AAC capture at 256 kbps bitrate and 48 kHz sampling frequency saved in standard `.m4a` format.
- **Multi-Source Fallback Engine**: Multi-tier recording pipeline (`MIC` → `VOICE_COMMUNICATION` → `VOICE_CALL` → `VOICE_RECOGNITION` → `DEFAULT`) to ensure reliable recording across diverse Android chipsets.
- **Two-Party Compliance Beeps**: Mandatory 300ms notification beeps (~900 Hz tone) on recording start and stop to ensure strict legal compliance.
- **First-Time Legal Consent Flow**: Explicit consent modal dialog shown on first recording activation.
- **Live In-Call HUD**: Floating recording timer pill displayed on the active call screen.
- **Post-Call Review Dialog**: Prompt upon call termination to listen, save, rename, or discard recordings immediately.
- **Recording History Browser (`RecordingHistoryActivity`)**: Complete audio manager to play recordings, search by contact/date, share via secure Android `FileProvider`, or batch delete.

### 📹 Jitsi Meet Video Calling
- **Instant Video Conferences**: Launch private Jitsi Meet conference rooms directly from dialpad entries or contact details.
- **Cellular Audio Conflict Protection**: Automatically mutes the cellular microphone when video starts to eliminate acoustic feedback.
- **Accidental Navigation Rejoin**: Interactive dialog allowing users to rejoin active video calls seamlessly if navigated away.
- **SMS Room Invitation Dispatch**: Automated SMS formatting with one-click web links so remote parties can join without installing third-party apps.

### 🎙️ Smart Offline Voice Dialing & Search
- **Instant Speech Dialing**: Speak natural contact names or numbers parsed on-device.
- **Local Offline Fuzzy Resolver (`ContactResolver`)**: High-performance 100% private local contact search with Levenshtein fuzzy matching—zero cloud AI or data leaks.

---

## [Upstream Heritage] - Fossify Phone 1.7.1 (2025-09-12)
### Changed
- Upstream translations across supported locales

### Fixed
- Fixed USSD code handling in speed dial
- Fixed contact number selection on the dial pad screen
