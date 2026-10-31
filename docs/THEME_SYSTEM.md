# Theme System - Black Diamond AMOLED & Dynamic Theming

## Overview

The **SimplyDial** app features an advanced dual-theme architecture:
1. **Light Mode (Default)**: Clean, high-legibility interface with subtle warm tones.
2. **Black Diamond Dark Theme**: Ultra-high contrast AMOLED True Black (`#000000`) paired with Electric Cyan (`#00E5FF`) and Aqua Neon accents.

---

## Palette & Design Tokens

### Black Diamond Dark Theme Tokens
| Component / Role | Light Mode Color | Black Diamond Dark Color | Hex Code |
| :--- | :--- | :--- | :--- |
| **True Background** | `#FFFFFF` | AMOLED True Black | `#000000` |
| **Surface Elevated** | `#F5F5F5` | Deep Obsidian | `#0A0A0A` |
| **Interactive Controls** | `#E0E0E0` | Elevated Charcoal | `#111111` |
| **Primary Accent** | `#FF6B35` | Electric Cyan Neon | `#00E5FF` |
| **Primary Variant** | `#E55A2B` | Dark Aqua | `#00B8D4` |
| **Secondary Accent**| `#FFD700` | Soft Cyan / Seagreen | `#4DD0E1` / `#2E8B57` |
| **Primary Text** | `#212121` | Light Grey / Off-White | `#E0E0E0` |
| **Secondary Text** | `#757575` | Muted Silver | `#9E9E9E` |

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
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    ThemeManager.applyTheme(this)
    super.onCreate(savedInstanceState)
}
```

### 4. Dynamic Blur & Glassmorphism
The visual stack supports dynamic real-time blur and glassmorphic overlays (`BlurBackgroundView.kt`, `GradientRingImageView.kt`):
- **Light Theme**: Warm lemon-chiffon blur overlay.
- **Dark Theme**: Calming seagreen / deep aqua blur tone.

---

## Custom Visual Controls

- **`SwipeCallButtonsView`**: Fluid swipe gesture handling for accepting and declining incoming calls.
- **`JoystickMessageButton`**: Quick action joystick offering radial SMS, quick reply, or canned message triggers.
- **`WaterRippleView`**: Interactive wave feedback on touch and dial actions.
- **`HorizontalAlphabetSlider`**: Fast-scrolling alphabet indexed contact scrubber.

