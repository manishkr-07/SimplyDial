# SimplyDial

<p align="center">
  <img alt="SimplyDial Banner" src="graphics/simplydial_banner.png" width="100%" />
</p>

<p align="center">
  <b>Connect with Ease</b><br>
  <i>A modern, privacy-first, accessibility-enhanced Android phone app featuring Black Diamond AMOLED theming, intuitive senior-friendly UI controls, HD call recording, Jitsi Meet video calling, and Gemini AI voice dialing.</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white" alt="Android API 26+">
  <img src="https://img.shields.io/badge/Gradle-9.8.2-02303A?logo=gradle&logoColor=white" alt="Gradle 9.8.2">
  <img src="https://img.shields.io/badge/AGP-9.5.0-34A853?logo=android&logoColor=white" alt="AGP 9.5.0">
  <img src="https://img.shields.io/badge/TargetSDK-36-blue?logo=android&logoColor=white" alt="Target SDK 36">
  <img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPLv3">
  <img src="https://img.shields.io/badge/Video%20Call-Jitsi%20Meet-17A0DB?logo=jitsi&logoColor=white" alt="Jitsi Meet">
  <img src="https://img.shields.io/badge/AI%20Voice-Google%20Gemini%203.8%20Flash-4285F4?logo=google&logoColor=white" alt="Gemini AI">
  <img src="https://img.shields.io/badge/Privacy-100%25%20On--Device-green" alt="100% On-Device Privacy">
  <a href="https://discord.gg/hQebWS8qZ"><img src="https://img.shields.io/badge/Discord-Join%20Chat-5865F2?logo=discord&logoColor=white" alt="Discord"></a>
  <a href="https://www.reddit.com/r/SimplyDial07/"><img src="https://img.shields.io/badge/Reddit-r%2FSimplyDial07-FF4500?logo=reddit&logoColor=white" alt="Reddit"></a>
  <a href="https://t.me/SimplyDial"><img src="https://img.shields.io/badge/Telegram-Join%20Channel-24A1DE?logo=telegram&logoColor=white" alt="Telegram"></a>
</p>

---

## 📌 Project Overview

**SimplyDial** is a next-generation, feature-packed Android dialer and calling application designed to make mobile communication seamless, transparent, and accessible for everyone. 

It combines **intuitive senior-friendly accessible controls (EaseUI)** to prevent accidental dials and simplify messaging, an ultra-crisp **Black Diamond AMOLED Dark Theme** for maximum battery life, **studio-grade compliant call recording**, **integrated Jitsi Meet video conferences**, and **conversational AI voice dialing powered by Google Gemini 3.8 Flash**.

---

## ✨ Key Features & Enhancements

### 👵 1. Accessibility & Senior-Friendly "EaseUI"
Designed from the ground up to eliminate dialing anxiety, accidental calls, and complex navigation:
- **Accidental Call Shield**: Built-in **Call Confirmation Dialog** displaying the contact name, photo, and a prominent "Tap to Call" confirmation button before placing a call.
- **Messages Joystick (`JoystickMessageButton`)**: Radial swipe control for rapid, pre-configured SMS messages and quick responses without typing.
- **Tactile Swipe Answering (`SwipeCallButtonsView`)**: Ergonomic swipe-up/down controls to accept or decline incoming calls cleanly.
- **Multilingual Support**: In-app language switcher with full Hindi localization (`values-hi`) and regional voice dial language selection.
- **Profile-Touch Actions**: Tap avatar rings with animated gradients to quickly inspect contact details.
- **Large Touch Targets & Water Ripple Feedback**: Tactile feedback with responsive ripple animations on all keypad presses.

### 💎 2. Black Diamond AMOLED & Dynamic Theming
- **AMOLED True Black (`#000000`)**: Complete dark mode designed for OLED and AMOLED screens with zero pixel bleed and significant battery savings.
- **Electric Cyan (`#00E5FF`) Glow**: Vivid neon accenting across dialpad keys, floating buttons, and card containers.
- **Glassmorphic Overlays**: Dynamic blur backgrounds (`BlurBackgroundView`) with smooth color-switching between light and dark modes.
- **Independent Theme Toggle**: Seamless switching between clean Light Mode (default) and Black Diamond Dark Theme via settings or system default.

### 🎙️ 3. Compliant High-Definition Call Recording
- **Studio-Quality Audio**: Encoded in 256 kbps AAC stereo at 48 kHz in standard `.m4a` format.
- **Hybrid Audio Fallback**: Multi-tier audio fallback (`MIC` → `VOICE_COMMUNICATION` → `VOICE_CALL` → `VOICE_RECOGNITION` → `DEFAULT`) for reliable capture across diverse Android hardware.
- **Two-Party Compliance Beeps**: Plays audible 300ms notification beeps (~900 Hz tone) at recording start and stop to ensure full legal compliance.
- **Mandatory Consent Flow**: First-time consent modal ensures transparency and privacy adherence.
- **Recording History & Review**: Includes live in-call indicator/timer, post-call review dialog, and a full-featured Recording History explorer to play, search, share via secure `FileProvider`, or delete audio files.

### 📹 4. Integrated Video Calling (Jitsi Meet)
- **One-Tap Video Calls**: Launch video conferences directly from dialer input or contact cards.
- **Auto-Mute Cellular Conflict**: Automatically mutes the active phone call microphone when video starts to prevent acoustic feedback.
- **Rejoin Interception**: If the user accidentally navigates away from the video conference screen, an interactive prompt lets them rejoin instantly without dropping the session.
- **SMS Room Invitation**: Automatically formats and sends an SMS invite link so the remote party can join from any smartphone or browser.

### 🧠 5. AI Voice Dialing (Gemini 3.8 Flash + Offline Fallback)
- **Conversational Speech Recognition**: Speak commands naturally (e.g., *"Call Mom"*, *"Dial Dad on mobile"*).
- **Google Gemini 3.8 Flash Engine**: High-performance model with structured JSON output enforcement (`response_schema`), exponential backoff retries, and jitter handling.
- **Offline Fuzzy Matcher**: If no network is available or if an API key is not configured, the app seamlessly falls back to a fast local contact resolver (`ContactResolver`) using Levenshtein distance matching.

### 📞 6. Advanced Call Waiting & Alert Interception
- **Busy-Call Interception**: Visual notifications and alerts when another caller attempts to reach you during an active conversation.
- **Missed Call-Waiting Tracker**: Logs missed call-waiting attempts so no important incoming call goes unnoticed.
- **Carrier Call-Waiting Handling**: Integrates smoothly with native telephony network signals.

### ⚡ 7. Performance & Contact Navigation
- **Shimmer Skeleton Loading**: Fluid placeholder animations while the contact directory loads.
- **Fast Alphabet Slider (`HorizontalAlphabetSlider`)**: Quick alphabetical scrubbing for large contact books.
- **Lightweight Models**: Memory-efficient data structures with background cache enrichment.

---

## 📱 Screenshots

<div align="center">
  <img alt="Dialer Screen" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" width="30%">&nbsp;
  <img alt="Call Screen" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" width="30%">&nbsp;
  <img alt="Contacts List" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" width="30%">
</div>

---

## 🛠️ Tech Stack & Architecture

- **Language & Compiler**: [Kotlin](https://kotlinlang.org/) (Coroutines, Flow, Serialization) compiled natively via AGP 9
- **Build System**: Gradle 9.8.2 & Android Gradle Plugin (AGP) 9.5.0
- **SDK Target**: Android 16 (API 36 compileSdk & targetSdk)
- **UI & Layouts**: Android XML Layouts, Custom Views, Glassmorphic Overlays, Shimmer Skeletons
- **Video Conferencing**: [Jitsi Meet](https://jitsi.org/)
- **AI / LLM Integration**: [Google Gemini 3.8 Flash](https://ai.google.dev/) via official REST API with structured JSON schema
- **Networking**: OkHttp with connection timeouts & exponential backoff
- **Audio Capture**: Android `MediaRecorder` with prioritized audio sources and `ToneGenerator`
- **Architecture Pattern**: MVVM with Helper/Manager abstraction layers

---

## 🚀 Getting Started & Building

### Prerequisites
- **JDK 21 LTS** (Eclipse Adoptium or Android Studio JBR)
- **Android SDK** (Target SDK `36`, Compile SDK `36`, Minimum SDK `26`)
- **Gradle 9.8.2** (bundled via `./gradlew` wrapper)

### Setup Instructions

1. **Clone the repository:**
   ```bash
   git clone https://github.com/<your-username>/SimplyDial.git
   cd SimplyDial
   ```

2. **Configure Gemini API Key (Optional):**
   To enable AI voice intent parsing with Google Gemini:
   ```bash
   cp gemini.properties.sample gemini.properties
   ```
   Open `gemini.properties` and add your API key from [Google AI Studio](https://aistudio.google.com/):
   ```properties
   GEMINI_API_KEY=AIzaSy...your_key_here
   ```
   > **Note**: `gemini.properties` is strictly ignored by Git and will never be committed. If left unconfigured, the dialer automatically uses the built-in local fuzzy contact matcher.

3. **Build the Debug APK:**
   * On Linux / macOS:
     ```bash
     ./gradlew assembleCoreDebug
     ```
   * On Windows (PowerShell / Command Prompt):
     ```powershell
     .\gradlew.bat assembleCoreDebug
     ```

   The generated APK will be placed in:
   ```
   app/build/outputs/apk/core/debug/
   ```

---

## 📖 In-Depth Documentation

For detailed technical specifications, architectural diagrams, and flowcharts, refer to the [`docs/`](docs/) directory:

- 🎙️ [Call Recording Architecture & Compliance Guide](docs/CALL_RECORDING.md)
- 💎 [Black Diamond AMOLED & Dynamic Theming System](docs/THEME_SYSTEM.md)
- 📹 [Jitsi Meet Video Calling Integration](docs/VIDEO_CALLING.md)
- ✅ [Release & Quality Assurance Testing Checklist](docs/TESTING_CHECKLIST.md)

---

## 🔒 Security & Privacy

- **100% On-Device Processing**: Call history, recordings, contact data, and preferences remain solely on your device.
- **Zero Third-Party Trackers**: Completely free of analytics, tracking libraries, ads, and telemetry.
- **Sanitized Repository**: Keystore passwords, signing keys, and individual API keys are kept strictly out of version control.
- **Secure File Sharing**: Audio recordings are exposed to external apps exclusively through secure Android `FileProvider` content URIs.

---

## 👨‍💻 Authors, Project Leadership & History

**SimplyDial** was conceptualized, initiated, and is primarily owned by **Piyush Kumar**, who started the project in **July 2025** and led core development through **October 2025** in close collaboration and engineering co-management with his brother, **Manish Kumar**. 

During the intensive **July 2025 – October 2025** phase, all core features of SimplyDial were designed and implemented—including the senior-friendly **EaseUI** accessibility suite, Black Diamond AMOLED dark theming, studio-grade HD call recording, Jitsi Meet video conferencing, and Gemini 1.5 Flash AI voice intent dialing.

In **Late 2026 (v1.7.0)**, the project underwent a dedicated engineering re-update to solve critical telephony call waiting and caller ID challenges, alongside repository sanitization and open-source release polish.

### 📅 Project Evolution & Milestones

| Timeline | Milestone & Core Focus | Led By |
| :--- | :--- | :--- |
| **July 2025 – October 2025** | **Core Build & Complete Feature Suite**<br>• Conceptualized, initiated, and built from the ground up<br>• **EaseUI Accessibility Suite**: Accidental call shield dialog, messages joystick (`JoystickMessageButton`), tactile swipe answer, ripple feedback, Hindi localization<br>• **Black Diamond AMOLED Theme**: Pure black `#000000`, electric cyan glow, glassmorphism (`BlurBackgroundView`)<br>• **HD Call Recording**: 256 kbps stereo AAC `.m4a`, compliance tones, legal consent flow, recording history explorer<br>• **Jitsi Meet Video Calls**: One-tap conference rooms, cellular mic conflict auto-mute, rejoin dialog<br>• **Gemini 1.5 Flash AI Voice**: Conversational speech intent parsing with offline fuzzy fallback | **Piyush Kumar** (Creator & Primary Owner)<br>with **Manish Kumar** (Collaborator & Co-Manager) |
| **Late 2026 (v1.7.0)** | **Call Waiting & Caller ID Architecture Fixes + Public Release**<br>• **Call Waiting Alert**: Telecom-standard incoming call tone and double-pulse vibration repeating every 5 seconds in earpiece/call stream<br>• **Heads-Up Notification**: High-priority alert banner with incoming caller's details and direct Accept/Decline action buttons<br>• **Caller ID**: Displays waiting caller's name, number, and photo on the in-call screen<br>• **Dedicated Controls**: Added intuitive controls for handling incoming call-waiting requests<br>• **Decline Fix**: Rejecting a second call (`declineCall()`) no longer disconnects or interrupts the ongoing conversation<br>• **Active Call Display**: Shows ongoing call information separately in the top status bar (`"Active: [Name]"`<br>• **Carrier/OEM Interception**: Fallback notification manager (`CarrierCallWaitingNotificationManager`) and attempt tracking for non-InCallService calls<br>• **Release & Security Hardening**: Sanitized repo, removed Logcat payload leaks, established standalone GitHub Actions CI | **Piyush Kumar** & **Manish Kumar** |

### 🌐 Official Links & Community
- 🌐 **Website**: [manish.dev](https://manishkr-dev.vercel.app/)
- 💻 **GitHub**: [@manishkr-07 (Manish Kumar)](https://github.com/manishkr-07)
- 💬 **Discord Community**: [discord.gg/hQebWS8qZ](https://discord.gg/hQebWS8qZ)
- 🔴 **Reddit Community**: [r/SimplyDial07](https://www.reddit.com/r/SimplyDial07/)
- ✈️ **Telegram Channel**: [t.me/SimplyDial](https://t.me/SimplyDial)
- ✉️ **Support & Feedback**: [manis781701@zohomail.in](mailto:manis781701@zohomail.in) / [Developer Portal](https://manishkr-dev.vercel.app/)

---

## 🤝 Contributing

Contributions, feedback, and issue reports are welcome! Please review [CONTRIBUTING.md](CONTRIBUTING.md) before opening pull requests.

---

## ⚖️ Upstream Attribution & Open Source Heritage

**SimplyDial** is an independently developed and owned project modified from the open-source [Fossify Phone](https://github.com/FossifyOrg/Phone) codebase under the **GNU General Public License v3.0 (GPLv3)**.

We gratefully acknowledge and credit the **Fossify Organization** and the upstream open-source community for providing the rock-solid, privacy-first telephony foundation upon which **SimplyDial**'s specialized accessibility enhancements (EaseUI), Black Diamond AMOLED design, HD call recording, Jitsi Meet video conferencing, and Gemini AI voice dialing are built.

In full accordance with GPLv3:
- All original open-source licenses and copyright headers are preserved.
- All modifications and enhancements remain open-source and free for the community.

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0** (GPLv3). See the [LICENSE](LICENSE) file for the full license text.
