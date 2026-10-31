# Call Recording - Production Architecture & Implementation Guide

## Overview

The **SimplyDial** application includes a robust, Google Play Store compliant, high-definition call recording system built upon the open-source Fossify Phone foundation. The implementation ensures user privacy, legal compliance with dual-party notification beeps, and versatile audio capture using fallback mechanisms.

---

## Architecture & Core Components

### 1. Key Components
- **`CallRecordingManager.kt`**: Core recording engine managing state transitions, audio source fallback, file management, and timer updates.
- **`CallRecordingService.kt`**: Foreground service ensuring Android does not terminate active recordings during background operation.
- **`CallActivity.kt`**: Active call UI handling user recording triggers, pulsing indicators, and real-time elapsed duration counters.
- **`CallRecordingConsentDialog.kt`**: Mandatory legal consent prompt shown to the user on initial recording attempt.
- **`RecordingReviewDialog.kt`**: Post-call or on-demand dialog offering playback, metadata inspection, sharing, and renaming/deletion.
- **`RecordingHistoryActivity.kt`**: Dedicated recordings manager screen with sorting, search, filtering, and bulk management.
- **`CallRecording.kt`**: Comprehensive data model with parcelable attributes.

---

## Audio Pipeline & Recording Mechanics

### 1. Hybrid Audio Source Fallback
Because Android hardware and OEM security layers treat call recording audio sources differently across vendors, `CallRecordingManager` tries audio sources in prioritized fallback order:
1. `MediaRecorder.AudioSource.MIC` (Optimal with speakerphone or ambient capture)
2. `MediaRecorder.AudioSource.VOICE_COMMUNICATION` (Acoustic echo cancellation enabled)
3. `MediaRecorder.AudioSource.VOICE_CALL` (Direct line-level call audio on supported devices)
4. `MediaRecorder.AudioSource.VOICE_RECOGNITION`
5. `MediaRecorder.AudioSource.DEFAULT`

### 2. Audio Quality & Encoding Specifications
- **Container**: MPEG-4 (`.m4a`)
- **Codec**: Advanced Audio Coding (AAC)
- **Sample Rate**: 48,000 Hz (48 kHz)
- **Audio Bitrate**: 256 kbps (Studio-grade clarity)
- **Channels**: 2 (Stereo)

### 3. Dual-Party Compliance Beep Tones
To satisfy legal compliance and user privacy across jurisdictions:
- **Recording Start**: 300ms audio beep (~900 Hz tone) generated via `ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)`.
- **Recording Stop**: 300ms termination beep tone.
- Audible to both call participants on supported hardware.

---

## File Storage & Management

- **Storage Location**: `Environment.DIRECTORY_DOWNLOADS/CallRecordings/`
- **Naming Convention**: `CALL_{IN|OUT}_{PhoneNumber}_{Timestamp}.m4a`
- **Privacy & Security**: Stored locally on-device. No telemetry, audio data, or metadata is ever transmitted to remote servers.
- **Sharing**: Integrated with Android `FileProvider` via secure `content://` URIs for safe sharing to external apps.

---

## User Experience & UI Flow

```
[ Active Call ]
      |
      v
[ Tap Record ] ---> [ First Time? ] --- YES ---> [ Consent Dialog ]
      |                    |                             |
      |                    v                             v
      |                   NO                    [ Accept & Grant Mic ]
      |                    |                             |
      |<-------------------+-----------------------------+
      v
[ Start Beep Tone ]
      v
[ Foreground Service + Pulsing Red Indicator + Live Timer ]
      v
[ Stop Recording ]
      v
[ Termination Beep ]
      v
[ Recording Review Dialog (Play / Share / Save / Delete) ]
```

