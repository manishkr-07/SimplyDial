# Changelog

All notable changes to **SimplyDial** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.8.2] - 2026-11-02 - Text Visibility Architecture Fix & Version Alignment

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
