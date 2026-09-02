# Project Plan

I need a simple app that hosts an RTSP stream. That stream contains unprocessed audio from the phone's microphone(s). The app itself will show the URL for the RTSP stream, and perhaps a gain adjuster and sound level indicator. Not much else is required.

## Project Brief

# BirdFeeder Project Brief

## Features
1. **Multi-Mic RAW Audio Capture**: Accesses all available physical microphones on the device to capture unprocessed, high-fidelity PCM audio, bypassing system-level enhancements (like AGC and noise suppression) for maximum scientific accuracy.
2. **Synchronous Mono Downmixing**: Intelligently merges multiple microphone signals into a single high-quality mono stream, optimized for BirdNET-Go's spectral analysis.
3. **High-Performance RTSP Streaming**: Implements a lightweight, local RTSP server to broadcast live audio over the network with minimal latency, displaying the connection URL directly in the UI.
4. **Dedicated Launcher Experience**: Transforms the Android device into a single-purpose appliance by serving as the primary Home launcher, ensuring persistent operation and immediate access upon device startup.
5. **Real-time Monitoring & Control**: Provides an interactive UI featuring a sound level meter and manual gain adjustment, strictly following the layout requirements for microphone source selection and port configuration.

## High-Level Technical Stack
- **Language**: Kotlin
- **UI Framework**: **Jetpack Compose** for a modern, declarative interface.
- **Navigation**: **Jetpack Navigation 3** (State-driven) for robust screen management.
- **Adaptive Strategy**: **Compose Material Adaptive** library to ensure the UI remains functional and clear across various phone and tablet form factors.
- **Audio Core**: `AudioRecord` API used with `AudioDeviceInfo` enumeration and `setPreferredDevice()` to capture raw buffers from all physical microphones.
- **Streaming Logic**: A custom or library-based RTSP server integrated within an **Android Foreground Service** to maintain audio capture priority and prevent system termination.
- **Concurrency**: **Kotlin Coroutines and Flow** for high-efficiency, non-blocking audio buffer processing and real-time mixing.
- **Manifest Configuration**: Configured with `android.intent.category.HOME` to fulfill the launcher app requirement.

> [!NOTE]
> The UI Design Image section has been omitted as the image generation tool is currently unavailable. The application will strictly implement the UI elements described: Mic Source selector, Port field, sound level indicator, gain adjuster, and the 'RTSP Stream URL for BirdNET-Go' display.

## Implementation Steps
**Total Duration:** 1h 45m

### Task_1_ProjectFoundation: Configure project manifest for launcher functionality, setup permissions, and initialize Navigation 3 and Adaptive dependencies.
- **Status:** COMPLETED
- **Duration:** 7m 15s

### Task_2_AudioStreamingEngine: Implement an Android Foreground Service that handles multi-mic raw audio capture via AudioRecord, mono downmixing, gain adjustment, and RTSP streaming.
- **Status:** COMPLETED
- **Duration:** 20m 45s

### Task_3_AdaptiveUI: Develop the Jetpack Compose UI with adaptive layouts to display the RTSP URL, a real-time sound level meter, and a gain control slider.
- **Status:** COMPLETED
- **Duration:** 8m 1s

### Task_4_RunAndVerify: Final build and verification of the application on a device/emulator.
- **Status:** COMPLETED
- **Duration:** 18m 59s

### Task_5_CrashFixAndMultiMic: Fix the Android 12 crash when toggling stream. Update engine to capture from all physical mics simultaneously and display active AudioSource mode (Unprocessed vs Voice Rec).
- **Status:** COMPLETED
- **Updates:** Fixed the Android 12 crash by adding permission safety checks for the microphone foreground service. Updated the audio engine to simultaneously capture from all physical microphones with fault tolerance. Added UI reporting for the active audio mode (Unprocessed/Voice Rec) and active microphone IDs. replaced the notification icon with a local resource. verified build.
- **Acceptance Criteria:**
  - App does not crash on toggle
  - All physical mics are captured and downmixed
  - AudioSource mode (UNPROCESSED/VOICE_RECOGNITION) is displayed
- **Duration:** 11m 29s

### Task_6_BirdThemedUI: Consolidate UI into a single-screen bird-themed dashboard with nature colors and bird illustrations.
- **Status:** COMPLETED
- **Updates:** Overhauled the UI into a single-screen dashboard. implemented a nature-themed color palette (Forest Green, Sky Blue, etc.) and added bird motifs using icons and custom Canvas drawings. Consolidated all controls, engine status, and the waveform into a single scrollable view. Removed all Navigation 3 and adaptive scaffold logic to simplify the experience as a dedicated launcher app. verified build success.
- **Acceptance Criteria:**
  - Single-screen layout
  - Bird/Nature theme implemented
  - All controls and status visible on main screen
- **Duration:** 33m 10s

### Task_7_FinalVerification: Perform final testing on Pixel 3 XL to ensure stability and UI satisfaction.
- **Status:** COMPLETED
- **Updates:** The critic_agent has performed the final verification on the physical Pixel 3 XL.
- Confirmed the app is stable and does not crash on Android 12 when toggling the stream.
- Verified the bird-themed, single-screen UI with forest green and sky blue palette.
- confirmed all microphones are detected and captured in 'UNPROCESSED' mode.
- Verified all controls and visualizer are functional on the main dashboard.
- Confirmed Launcher/Home app registration.
The project is complete and stable on the target hardware.
- **Acceptance Criteria:**
  - Stable streaming on Pixel 3 XL
  - UI meets bird-themed requirements
- **Duration:** 5m 21s

