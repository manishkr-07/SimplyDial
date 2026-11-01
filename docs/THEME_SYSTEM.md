# Theme System - Black Diamond AMOLED & Material 3 Slate

## Overview

The **SimplyDial** app features a dual-theme architecture engineered for both battery optimization and high-contrast daytime usability:
1. **Material 3 Slate Light Theme (Default)**: Clean, high-legibility interface built on a modern `#F8FAFC` slate canvas with elevated `#FFFFFF` modular cards, `#2563EB` Royal Blue accents, and `#0F172A` deep ink text with WCAG AAA compliance.
2. **Black Diamond Dark Theme**: Ultra-high contrast AMOLED True Black (`#000000`) paired with Electric Cyan (`#00E5FF`) and Aqua Neon accents for true OLED battery savings.

---

## Palette & Design Tokens

### Dual-Theme Token Reference

| Component / Role | Material 3 Slate Light Mode | Black Diamond AMOLED Dark Mode | Role & Intent |
| :--- | :--- | :--- | :--- |
| **Canvas Background** | `#F8FAFC` (Slate 50) | `#000000` (AMOLED Black) | Main screen background |
| **Card / Tile Surface** | `#FFFFFF` (Pure White) | `#0A0A0A` (Deep Obsidian) | Elevated contact & recents card containers |
| **Secondary Surface / Chips** | `#F1F5F9` (Slate 100) | `#111111` (Elevated Charcoal) | Filter chips, button backdrops |
| **Primary Accent** | `#2563EB` (Royal Blue) | `#00E5FF` (Electric Cyan Neon) | Primary call actions, tab selection, active icons |
| **Primary Variant** | `#1D4ED8` (Blue 700) | `#00B8D4` (Dark Aqua) | Pressed states & variants |
| **Secondary Accent** | `#0D9488` (Teal 600) | `#4DD0E1` (Aqua Highlight) | Secondary indicators & badges |
| **Primary Typography** | `#0F172A` (Slate 900) | `#FFFFFF` (Pure White) | Caller names, dialpad digits (14:1 contrast) |
| **Secondary Typography** | `#475569` (Slate 600) | `#9E9E9E` (Silver Muted) | Phone numbers, timestamps (7:1 contrast) |
| **Border / Stroke** | `#E2E8F0` / `#CBD5E1` | `#26282E` / `#3E424D` | Modular card perimeters & squircle borders |
| **Call Action Color** | `#16A34A` (Emerald 600) | `#00E5FF` (Cyan) / `#1B5E20` | Quick Call hero targets |
| **Missed Call Color** | `#DC2626` (Red 600) | `#E74C3C` (Vivid Crimson) | Missed call alerts & indicators |
| **Glassmorphic Tint** | `#E0F2FE` (Sky-Blue) | `#2E8B57` (Seagreen) | Blur overlay tint in `BlurBackgroundView` |

---

## Architecture & Lifecycle Integration

### 1. Theme Configuration
Theme state is persisted in Android `SharedPreferences` via `Config.kt`:
```kotlin
var isDarkThemeEnabled: Boolean
    get() = prefs.getBoolean(IS_DARK_THEME_ENABLED, false)
    set(value) = prefs.edit().putBoolean(IS_DARK_THEME_ENABLED, value).apply()
```

### 2. Application-Level Initialization
Theme initialization is performed in `FossifyPhoneApp.kt` before any UI activity launches:
```kotlin
class FossifyPhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.initializeTheme(this)
    }
}
```

### 3. Base Activity Theme Dispatcher
`SimpleActivity.kt` applies the designated theme prior to calling `super.onCreate()`:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    ThemeManager.applyTheme(this)
    super.onCreate(savedInstanceState)
}
```

### 4. Dynamic Blur & Glassmorphism
The visual stack supports dynamic real-time blur and glassmorphic overlays (`BlurBackgroundView.kt`, `GradientRingImageView.kt`):
- **Light Theme**: Soft sky-blue blur overlay (`#E0F2FE`) providing gentle depth.
- **Dark Theme**: Calming seagreen / deep aqua blur tone (`#2E8B57`).

---

## Custom Visual Controls

- **`styleContactCard` / `styleCallCard`**: Dynamic card stylers applying squircle corners, borders, and shadows tailored for Simple vs Modern mode in both Light and Dark themes.
- **`SwipeCallButtonsView`**: Fluid swipe gesture handling for accepting and declining incoming calls.
- **`JoystickMessageButton`**: Quick action joystick offering radial SMS, quick reply, or canned message triggers.
- **`WaterRippleView`**: Interactive wave feedback on touch and dial actions.
- **`HorizontalAlphabetSlider`**: Fast-scrolling alphabet indexed contact scrubber.
