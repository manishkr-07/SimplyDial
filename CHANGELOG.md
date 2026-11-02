# Changelog

All notable changes to **SimplyDial** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
---

## [2.1.0] - 2026-11-02 - Caregiver Quick-Call, Emergency GPS SOS, Quick Decline SMS, Call Reminders, Biometric Lock & Haptic Profiles

### 🆘 Feature — Auto-GPS Location & Battery in Emergency SOS SMS
- **Emergency Location Broadcast**:
  - Automatically queries the device's fine/coarse GPS coordinates and battery percentage when triggering Emergency SOS.
  - Sends a direct SMS with Google Maps link (`https://maps.google.com/?q=lat,lng`) to designated family contacts.
  - Graceful fallback: sends emergency notification with battery percentage even if location permissions or GPS are unavailable.

### ✉️ Feature — 1-Tap Quick Decline SMS ("Quick Replies")
- **Instant Message on Call Rejection**:
  - Long-press or 1-tap quick decline drawer (`QuickDeclineSmsDialog`) on incoming call screen.
  - Bilingual presets for English & Hindi:
    - *"I am in a meeting, will call you back."* / *"मैं अभी बैठक में हूँ, बाद में बात करता हूँ।"*
    - *"Driving right now, please message."* / *"मैं गाड़ी चला रहा हूँ, कृपया संदेश भेजें।"*
    - *"Can't talk now. What's up?"*
  - Custom quick reply composer for instant typing and sending.

### ⏰ Feature — Post-Call Follow-Up Reminder ("Remind Me to Call Back")
- **Intelligent Scheduled Callback Alarm**:
  - 1-tap chip in `PostCallSummaryDialog`: choose 15 minutes, 1 hour, or tomorrow morning (9:00 AM).
  - Background `CallReminderReceiver` schedules exact alarm and triggers a rich notification with instant **"Call Back"** and **"WhatsApp"** action buttons.

### 👨‍👩‍👧 Feature — Caregiver / Family Persistent Quick-Call Notification
- **Lock Screen & Notification Shade Quick Calling**:
  - Displays persistent, low-priority family call buttons in the notification drawer:
    - 📞 Call Trusted Family Contact
    - 📞 Call Speed Dial #1
    - 🚨 Emergency SOS (112)
  - Mode-scoped toggle (Feature 18) in "Customize Interface Features" (default ON in Simpler Mode).

### 🔒 Feature — Biometric & Device PIN Privacy Lock
- **Secure Call Logs & Notes**:
  - Native Android `KeyguardManager` integration: requires fingerprint, face unlock, or device PIN to access Call History and private contact notes.
  - Accessible via Settings: preserves privacy when devices are shared within families.

### 📳 Feature — Tactile Haptic Feedback Profiles & Relationship Tags
- **Tactile Profile Selector**:
  - Choose between **"Reassuring Heavy Buzz"** (long-press haptic feel tailored for elders) and **"Crisp Tactile Click"** (modern subtle tap).
- **Contact Relationship Tags**:
  - Assign relationship chips (👨‍👩‍👧 Family, 🩺 Doctor, 💼 Work, 🤝 Friend, 🚨 Emergency) directly from contact quick action sheets.

---

## [2.0.0] - 2026-11-02 - Offline Spam Shield, Home Widgets Suite & Data Backup/Restore

### 🛡️ Feature — Offline Spam & Robocall Shield (`SpamShieldManager`)
- **100% On-Device Offline Heuristics Engine**:
  - Automatically identifies suspected spam, telemarketers, and robocalls offline without sending user data or phone numbers to any third-party server.
  - Pattern matching for Indian telemarketing prefixes (`140xxxxxxx`, `160xxxxxxx`), commercial toll-free blocks (`1800xxxxxxx`), US/North American telemarketing toll-free series (`+1800`, `+1888`, `+1877`, `+1866`, `+1855`, `+1844`, `+1833`), and repetitive spoofed digit sequences (`00000`, `99999`, etc.).
  - User custom spam blacklist: easily add or remove any phone number to personal offline spam registry.
  - **Live Visual Spam Warning**: Prominent red warning banner (`⚠️ Suspected Spam / Telemarketer`) in `CallActivity` during incoming calls.
  - **Telecom Call Screening Integration**: Automatically silences or auto-declines suspected spam calls in `SimpleCallScreeningService` when auto-decline option is enabled.
  - Mode-scoped toggle (Feature 17) in "Customize Interface Features" with independent toggling for Simpler Mode and Modern Mode.

### 📱 Feature — Home Screen Widgets Suite
- **Speed Dial Photo Favorites Widget (`SpeedDialWidgetProvider`)**:
  - Compact 2x2 / 4x2 home screen widget showing user's top favorite speed-dial contacts.
  - 1-tap direct dialing straight from the Android home screen without launching the full dialer first.
  - Dynamically renders contact photo avatars or stylish initial badges.
- **Quick Dialpad Widget (`DialpadWidgetProvider`)**:
  - Sleek 4x2 home screen dialpad widget with 1-tap quick buttons (`Phone`, `Favorites`, `Recents`, `Search`).
  - Seamlessly launches into `DialpadActivity` with haptic feedback and immediate numeric input readiness.

### 💾 Feature — Local Data Backup & Restore (`BackupManager`)
- **Structured JSON Backup Engine**:
  - Export and restore all SimplyDial custom data in an encrypted/human-readable JSON package:
    - Contact Notes (`contact_note_*`)
    - Speed Dial Favorites with custom photo URI metadata
    - Emergency SOS configuration & designated contacts
    - Custom Spam List & telemarketing filters
    - Complete matrix of all 17 mode-scoped interface customization preferences across Simpler Mode and Modern Mode.
  - Integrated into Settings under the Migration & Data section with native Android Storage Access Framework (`CreateDocument` & `OpenDocument`).
  - Granular restore statistics dialog reporting the exact count of notes, speed dials, and preferences restored.

---

## [1.9.0] - 2026-11-02 - Call Notes, Post-Call Summary, Analytics & Media Studio

### 📝 Feature — In-Call Notes & Post-Call Summary Dialog
- **Live In-Call Notes Badge**:
  - Displays the caller's saved custom note prominently in a sleek rounded pill badge on the incoming and active call screen.
  - 1-tap note editing live during an active call (`showInCallNoteDialog`), allowing users to jot down critical info while speaking.
- **Smart Post-Call Summary Bottom Sheet (`PostCallSummaryDialog`)**:
  - Appears automatically upon call completion (with 8-second auto-dismiss progress timer).
  - Displays formatted call duration, caller name/number, and an inline scratchpad to quickly update or save contact notes.
  - 1-tap quick actions: **WhatsApp**, **Telegram**, and **Call Back**.
  - Distinctly toggleable per mode in "Customize Interface Features".

### 📊 Feature — Call Insights & Analytics Dashboard (`CallAnalyticsDialog`)
- **100% Offline Telecom Intelligence**:
  - Computes complete call analytics from device call logs (`CallLog.Calls`) without any cloud reliance.
  - KPI Hero Card displaying total talk time (hours/minutes) and total calls over the last 30 days.
  - Breakdowns for Incoming, Outgoing, and Missed calls with durations.
  - **Missed Call Callback Rate**: Tracks and calculates the percentage of missed calls that were subsequently returned.
  - **Peak Calling Hours**: Visual distribution bar across Morning (6am-12pm), Afternoon (12pm-6pm), Evening (6pm-10pm), and Night.
  - **Top 5 Frequent Contacts**: Contact rank, name, number, call count, and total duration.
  - Accessible via 3-dots overflow menu ("Call Insights & Analytics").

### 🎙️ Feature — In-App Call Recording Audio Player (`CallRecordingPlayerDialog`)
- **Visual Audio Studio**:
  - Interactive playback modal with SeekBar, play/pause controls, current time, and total duration.
  - 5-second rewind and forward skip buttons for quick audio scrubbing.
  - Variable playback speed controls (`1.0x`, `1.25x`, `1.5x`, `2.0x`).
  - 1-tap direct audio file sharing via Android FileProvider to WhatsApp, Drive, Gmail, or any messenger.
  - Quick action integration: shows a recording badge directly in contact quick action sheets when recordings exist for that caller.
  - Linked "Call Recordings" (`RecordingHistoryActivity`) directly into the main 3-dots menu.

### ✈️ Feature — Direct WhatsApp & Telegram Communication
- **Unified Chat Launcher (`CommunicationHelper`)**:
  - 1-tap WhatsApp and Telegram direct chat action from Contact Quick Actions, Recent Calls, and Post-Call Summary without requiring the caller to be saved in contacts.
  - International number cleaning and E.164 formatting.

### 🔍 Feature — Contact Notes T9 Search Integration
- **Search by Note**:
  - T9 keypad algorithm in `DialpadActivity` now searches across both contact names, phone numbers, and custom contact notes (e.g., typing "Doctor" or its keypad digits finds contacts with that note).

---

### 🗣️ Feature — Spoken Caller Name Announcement (TTS Engine)
- **Voice Caller Announcement**:
  - Implemented `CallerNameAnnouncer` utilizing Android's `TextToSpeech` engine to announce incoming caller names or numbers aloud when an incoming call arrives.
  - Periodic announcement loop (every 3.5s) ensuring elder users and hands-free callers immediately know who is calling before picking up.
  - Multilingual support with automatic locale detection (supports Hindi and English pronunciation).
  - Strict privacy and etiquette controls: automatically mutes when the device is set to Silent or Vibrate mode.
  - Instant audio interrupt: announcements immediately stop when pressing the physical volume rocker, accepting the call, or declining.
  - Added Android 11+ `<queries>` declaration for `android.intent.action.TTS_SERVICE` to ensure zero permission issues.
  - Distinctly toggleable per interface mode via "Customize Interface Features".

### 📶 Feature — Smart Dual-SIM Rules Engine
- **Intelligent SIM Memory**:
  - Implemented `SmartSIMManager` to eliminate repetitive and frustrating SIM selection prompts on dual-SIM devices.
  - Automatically queries the device's call history (`CallLog.Calls`) to detect which SIM slot was previously used for any specific contact or phone number.
  - Caches and remembers the preferred SIM, automatically pre-routing outgoing calls through the appropriate SIM card.
  - Fully integrated into `Activity.getHandleToUse` with fallback to manual prompt if no historical call record exists or if the SIM card was removed.
  - Distinctly configurable per interface mode.

### 🚨 Feature — One-Tap Emergency SOS Floating Pill & Dialog
- **Instant Emergency Access**:
  - Added a high-visibility, accessible Emergency SOS floating pill directly above the Dialpad.
  - Opens `EmergencySOSDialog` with one tap, offering immediate one-touch dialing for universal emergency services (112, 911, etc.) or a designated trusted family emergency contact.
  - Built-in emergency contact setup and configuration flow directly from the dialog.
  - Designed specifically for elder care, senior safety, and urgent crisis situations.
  - Distinctly toggleable per interface mode.

### 🔢 Feature — Dialpad Quick-Action Telecom Shortcuts
- **Telecom Long-Press Key Shortcuts**:
  - Long-press `#`: Instantly toggles the phone between Normal sound and Vibrate mode with clear Toast and haptic feedback.
  - Long-press `*`: Inserts a standard 2-second telecom pause (`,`) for automated phone tree, IVR, and PIN extension dialing.
  - Long-press `0`: Inserts the `+` international country calling prefix.
  - Fully compatible with 1-9 speed dial shortcuts without collision.

### 🎛️ Architecture — 15-Feature Mode-Scoped Customization
- **Total Preference Isolation**:
  - Expanded mode-scoped preference architecture to all 15 interface features.
  - All features are isolated with `_simple` and `_modern` preference keys—enabling or disabling features in Simpler Mode has zero effect on Modern Mode.
  - Interactive segmented mode tabs (`[Simpler Mode]` | `[Modern & Advanced]`) with live `ACTIVE` mode badges and per-mode default reset.


---

## [1.8.2] - 2026-11-02 - Text Visibility Architecture Fix & Version Alignment

### 🎛️ Feature — Mode-Scoped Interface Customization Architecture
- **Independent Feature Configuration per Interface Mode**:
  - Engineered a mode-scoped preference architecture (`Config.getModeBoolean` / `Config.setModeBoolean`) storing settings independently for **Simpler Mode** (`_simple` suffix) and **Modern & Advanced Mode** (`_modern` suffix).
  - Toggling or fine-tuning any interface feature in Simpler Mode is distinctive and completely isolated from Modern Mode—switching modes never resets or overrides custom user configurations.
- **Revamped Interactive "Customize Interface Features" Bottom Sheet**:
  - Integrated interactive segmented mode selector tabs (`[Simpler Mode]` | `[Modern & Advanced]`) directly at the top with an `ACTIVE` badge reflecting the app's current mode.
  - Users can freely toggle and customize settings for either mode on the fly with instant animated switch states.
  - Added "Reset to Mode Defaults" targeting specifically the currently viewed mode.
- **Expanded from 6 to 12 Customizable Interface Features**:
  - **Calling & Contact Actions**: Direct 1-Tap Calling, Accidental Call Shield (Call Confirmation Dialog), Modern Quick Actions Sheet, Tap Avatar for Contact Details.
  - **Incoming & In-Call Screen**: Large Tap Answer Buttons (vs Modern Swipe), Fullscreen Incoming Call Display.
  - **Dialpad & Keypad Experience**: Dialpad Keypad Vibrations, Dialpad Audible Tones / Beeps, Auto-Open Dialpad on Launch, Minimal Dialpad (Digits Only / Hide Letters).
  - **Recents & Messaging Shortcuts**: Floating Quick-Message JoyStick, Group Repeated Recent Calls.

### 🌟 Fixed — 3-Dots Popup Menu Text Visibility in Dark & Light Modes
- **Theme Inheritance Fix**: Replaced `ThemeOverlay.MaterialComponents.Toolbar.Surface` with `ThemeOverlay.MaterialComponents.Dark` in Dark Mode and `ThemeOverlay.MaterialComponents.Light` in Light Mode, eliminating black-on-black text rendering inside the Toolbar overflow menu.
- **Explicit Menu Item Typography (`TextAppearance.SimplyDial.PopupMenu.Item`)**: Wired explicit item typography (`android:itemTextAppearance` and `itemTextAppearance`) to `Widget.SimplyDial.PopupMenu` and `Widget.SimplyDial.PopupMenu.Overflow`, ensuring 100% readable text in all popup menus across the app.
- **Dynamic 3-Dots Menu Icon Tinting**: Tinted all popup menu icons dynamically with `@color/primary_color` (Electric Cyan neon `#00E5FF` in Dark Mode, Royal Blue `#2563EB` in Light Mode) for high contrast and visual polish.
- **Direct PopupMenu Compatibility**: Updated `RecentCallsAdapter` and `DialpadActivity` to use `ThemeOverlay.SimplyDial.PopupMenu` and explicit chip text and icon tints.

### 🌓 Fixed — Light & Dark Mode Text & Contrast Inconsistencies
- **Call Confirmation Dialog (`call_confirmation_dialog_background`)**: Upgraded background from hardcoded white (`#FFFFFF`/`#FFFFDD`) to theme-adaptive `@color/dialog_background` with `@color/primary_color` border, fixing invisible white-on-white text in Dark Mode.
- **Leadership & About Dialog Cards (`card_background_dark`)**: Refactored card background to use theme-adaptive `@color/card_background` and `@color/border_color`, preventing dark card collision in Light Mode.
- **Settings Version Badge**: Added dynamic data binding (`binding.settingsHeroVersion.text`) to ensure the Settings Hero Banner accurately reflects `BuildConfig.VERSION_NAME` (`v1.8.2 • EaseUI Suite`) at runtime.
- **Chip Contrast Outline**: Added 1dp `@color/divider_color` border to `settings_chip_bg.xml` for sharp contrast on elevated white surfaces.

### ⚡ Performance & Loading Experience Optimization
- **Centralized In-Memory Contacts Repository (`ContactsRepository`)**:
  - Implemented single-flight asynchronous request deduplication across Contacts, Favorites, Recents, and Dialpad, preventing 3-4 simultaneous duplicate queries against the Android ContactsProvider on cold launch.
  - Startup background preloading initiated immediately in `SplashActivity.initActivity()` and `MainActivity.onCreate()` for sub-second first-screen delivery.
  - 0ms zero-latency instant rendering for tab switching, app resumptions, and T9 dialer lookups via cached memory snapshots.
- **Top-Notch Interactive Shimmer Skeletons**:
  - Completely revamped skeleton drawables (`skeleton_bg`, `skeleton_circle_bg`, `skeleton_rounded`, `skeleton_circle`) to use adaptive `@color/surface_color` cards with subtle `@color/divider_color` outlines, replacing miscolored static blocks.
  - Enabled active shimmering (`app:shimmer_auto_start="true"`, 1100ms fluid wave) with explicit start hooks in `LightContactsAdapter` and `RecentCallsAdapter` for a modern, responsive loading aesthetic.
  - Instant skeleton attachment (<1ms) eliminating blank screens and layout pops during initial cold start.
- **Recents Query & Blocked Numbers Acceleration**:
  - Added a 30s session TTL cache for `context.getBlockedNumbers()`, eliminating blocking content-provider IPC roundtrips during fast recents filtering.
  - Linked `RecentsHelper` directly to `ContactsRepository` to skip redundant contact enrichment passes.

### 📦 Maintenance & Release Alignment
- Bumped `VERSION_NAME` to `1.8.2` and `VERSION_CODE` to `16` in `gradle.properties`.
- Synchronized all documentation and release descriptions to `1.8.2`.

## [1.8.0] - 2026-11-02 - Multi-Segment UI/UX Polish, Material 3 Light Mode & Deduplication

### 🎨 Added — Material 3 Slate Light Theme (Dark Mode 100% Preserved)
- **Modern Slate Canvas (`#F8FAFC`)**: Redesigned Light Theme canvas with subtle slate tinting, allowing pure white cards (`#FFFFFF`) to visually elevate with crisp Material 3 ambient shadows.
- **Royal Blue Accent (`#2563EB`)**: High-contrast modern primary accent replacing washed-out blue, meeting strict WCAG AAA readability standards.
- **Deep Ink Slate Typography (`#0F172A`)**: Rich Slate 900 text inking for titles and Slate 600 (`#475569`) for subtitles, providing 14:1 and 7.1:1 contrast ratios.
- **Dynamic Button Typography**: Upgraded primary action buttons in bottom sheets and dialogs from hardcoded black text to dynamic `@color/fab_icon` (crisp white text in Light Mode, deep black in Dark Mode).
- **Seamless White System Bars**: Configured `@color/statusbar_color` and `@color/navigationbar_color` to `#FFFFFF` with light status/navigation bar flags, eliminating dark-on-dark status bar clashes.
- **Sky-Blue Glassmorphism**: Updated `BlurBackgroundView` to use modern soft sky-blue tint (`#E0F2FE`) in Light Mode, deprecating obsolete yellowish lemon-chiffon tint.

### 🎛️ Added — Deep Multi-Segment UI/UX Differentiation (Simple vs. Modern)
- **Contacts Tab**:
  - *Simple Mode*: 82dp high-visibility cards, 20dp squircles with `#CBD5E1` / `#3E424D` border, 3.5dp elevation, 56dp oversized avatar, 19.5sp bold typography, emerald green quick-call button (`#16A34A` / `#1B5E20`), and 3-dots menu hidden to eliminate senior anxiety.
  - *Modern Mode*: 68dp sleek card with subtle `#E2E8F0` / `#26282E` stroke, 48dp avatar, 16sp typography, 120Hz scale-down micro-interactions (`0.975x`), and full Quick Actions bottom sheet (WhatsApp, Jitsi Video, SMS, vCard, Notes).
- **Recents Tab**:
  - *Simple Mode*: 82dp high-contrast card, 54dp avatar, 19sp bold name, red-highlighted missed calls, emerald green quick-call button, and hidden analytics/overflow.
  - *Modern Mode*: Material 3 cards, Daily Call Analytics banner (total count, talk time, missed), frequent contacts speed-dial carousel, swipe gestures (swipe-to-call, swipe-to-SMS), and SIM badges with contrast adjustment.
- **Favorites Tab**:
  - *Simple Mode*: High-visibility 2-column grid (`spanCount = 2`) with oversized caller photos and bold names. Tapping immediately launches the Accidental Call Shield.
  - *Modern Mode*: Customizable multi-column grid / list view with drag-and-drop custom order reordering.
- **Dialpad Screen**:
  - *Simple Mode*: Solid keys with high-contrast borders, 34sp digits, 74dp hero call button. Secondary Voice, Video, and clipboard chips hidden for single-action clarity.
  - *Modern Mode*: Tactile `#F1F5F9` / `#121316` keys with cyan/royal blue sub-letters, 28sp digits, flanked Voice and Video buttons, and dynamic clipboard paste / inline contact chips.
- **Accidental Call Shield**:
  - Automatic across all entry points in Simple Mode (Contacts, Recents, Favorites, Dialpad) without requiring manual configuration (`config.showCallConfirmation || config.isSimpleMode`).

### ⚡ Performance & Deduplication
- **Intelligent Contact & Favorites Deduplication (`List<Contact>.deduplicate`)**:
  - Automatically merges duplicate records originating from multi-account sync (SIM, Google Account, WhatsApp, Device Storage) using aggregate `contactId`, `rawId`, and normalized phone number matching across Contacts, Favorites, and Dialpad T9 search, preserving all phone numbers, photos, notes, and starred status.
- **Sub-Second Cold Start & Query Optimization**:
  - Replaced unindexed queries in `loadLightContactsAsync` with indexed `${DISPLAY_NAME} COLLATE NOCASE ASC`, moving cursor loading to background threads (`ensureBackgroundThread`), applying 2-stage UI batching (<15ms first paint), and synchronizing cross-tab cache sharing.

---

## [1.7.0] - 2026-11-01 - Call Waiting & Caller ID Architecture Fixes

### 🎛️ Added — Dynamic Interface Mode System & Modern UI/UX
- **Distinguishable Elevated Contact & Call Cards**: Redesigned contacts and recents from continuous flat edge-to-edge lists into individual, floating card containers (`styleContactCard` / `styleCallCard`) with 16dp rounded corners, subtle `#26282E` outline borders, 2dp elevation, and 12dp/4dp breathing margins.
- **Dual Interface Modes**: Added Simple Mode (senior-friendly, 64dp touch targets, Accidental Call Shield, clutter-free) and Modern & Advanced Mode (power-user, Material 3, WhatsApp, Jitsi Video, multi-SIM badges).
- **120Hz Smooth Scrolling & View Caching**: Set `setHasFixedSize(true)` and `setItemViewCacheSize(20)` on Recents, Contacts, Favorites, and Dialpad RecyclerViews to eliminate dropped frames and view re-binding during quick fling gestures.
- **Off-Thread Heavy Processing**: Moved call analytics calculations (`sumOf`, `groupBy`, sorting) in `RecentsFragment` and T9 contact matching in `DialpadActivity` to background coroutines (`Dispatchers.Default`) with job cancellation.
- **Glassmorphic Rounded Swipe Actions**: Upgraded swipe-to-call and swipe-to-SMS drawing with anti-aliased 16dp rounded corners (`drawRoundRect`) matching card geometry, plus dynamic alpha fade-ins for icons.
- **Tactile Touch Micro-Interactions**: Added smooth scale feedback (`0.98f` / `0.92f` on down, spring back to `1.0f` on release) to contact cards, call cards, frequent carousel items, and dialpad buttons in Modern & Advanced Mode.
- **Interactive Call History Filter Chips**: Real-time filtering by All, Missed (with live count badges), Incoming, and Outgoing calls with tactile haptics.
- **Universal Quick Actions Bottom Sheet (`QuickActionsHelper`)**: Modern slide-up sheet on contacts and recents with 1-tap WhatsApp chat, Jitsi Meet video, SMS, number copying, contact sharing, and call history.
- **Enhanced Adaptive Dialpad UI**: Redesigned dialpad with dual aesthetic modes: Black Diamond AMOLED keys with electric cyan sub-letters (`#00E5FF`) and neon border strokes in Modern Mode, vs. high-contrast solid keys (`34sp`) in Simple Mode.
- **Hero Call Button & Balanced Action Bar**: Prominent centered 68–74dp hero Call button flanked by sleek circular glass Voice Dial and Video Call buttons (auto-simplified in Senior Mode).
- **Dynamic Smart Dialpad Bar**: Contextual floating chips above the keypad for 1-tap clipboard number paste and inline `+ Add to Contacts` for unsaved numbers.
- **1-Tap Direct Interface Mode Switcher**: Added Interface Mode dialog directly to the main 3-dots toolbar menu with rich visual feature checklists and seamless crash-free switching.
- **Daily Call Analytics**: Glassmorphic summary pill on recents showing today's call count, cumulative talk duration, and missed call count.
- **Top Frequent Contacts Carousel**: Dynamic circular avatar carousel for quick 1-tap access to frequent callers.
- **Post-Call Notes & Reminders**: Quick memo system (`Config.getContactNote` / `setContactNote`) with visual sticky badges on call history items.
- **Instant vCard (.vcf) Export**: Standards-compliant vCard contact generation and sharing via Android FileProvider.
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
