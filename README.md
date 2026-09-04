# BirdFeeder 🐦📻

**BirdFeeder** turns any Android phone into a high-performance, turnkey RTSP audio streaming appliance designed for local network wildlife monitoring, bird call recognition (**BirdNET / BirdNET-Go**), and ambient acoustic monitoring.

---

## 🌟 Key Features

* **Uncompressed Studio-Grade Audio:** Streams raw, uncompressed 48kHz 16-bit Mono L16 PCM audio (`pcm_s16be` @ 768 kbps) with zero lossy compression artifacts.
* **Guaranteed RTSP over TCP:** Enforces TCP interleaved streaming (`RTP/AVP/TCP;interleaved=0-1`) for 100% packet delivery over local Wi-Fi networks.
* **Hardware Mic Pre-Amp Boost:** Defaults to `MediaRecorder.AudioSource.MIC` to engage the phone's hardware pre-amp circuit (+15dB to +20dB hardware boost) for capturing distant bird calls and ambient outdoor sounds.
* **Raw Audio Option:** Toggle between **Hardware Boosted** (Default) and **Raw (Unprocessed)** audio modes.
* **Extended Gain Adjuster:** Digital soft-clipping gain slider adjustable from 50% up to **2,000% (20x)**.
* **Plug & Stream (Auto-Start on Boot):** Automatically boots and begins streaming RTSP audio as soon as the phone powers on or is plugged into power.
* **Single-Screen Compact UI:** Fits all visualizers, status indicators, and controls onto a single screen height without scrolling.
* **Dedicated Kiosk / Home Launcher Option:** Built-in shortcut to set BirdFeeder as the default Android Home Launcher for dedicated appliance deployments.
* **Never Kill (Doze Exemption):** Includes battery optimization exemption to prevent Android from restricting or killing the stream during 24/7 background operation.
* **Matching Custom Adaptive Icon:** Distinctive bird motif adaptive launcher icon (`FlutterDash`).

---

## 📊 Technical Specifications

| Specification | Value / Detail |
| :--- | :--- |
| **Audio Encoding** | Linear 16-bit PCM (L16 / `pcm_s16be`) |
| **Sample Rate** | 48,000 Hz |
| **Channels** | Mono (1 Channel) |
| **Bit Depth** | 16-bit Big-Endian |
| **RTSP Transport** | TCP Interleaved (`RTP/AVP/TCP`) |
| **Default RTSP Port** | `8554` |
| **RTSP Stream URL** | `rtsp://<device-ip>:8554/live` |
| **Audio Bitrate** | `768 kbps` |
| **Network Bandwidth** | $\approx 789\text{ kbps}$ ($\approx 0.79\text{ Mbps}$ / $\approx 355\text{ MB/hour}$) |
| **Latency** | $< 50\text{ ms}$ (Ultra-low latency) |

---

## 🎧 Client Playback & Integration

### 1. `ffplay` / `ffmpeg`
Play live audio over TCP from your laptop or server:
```bash
ffplay -rtsp_transport tcp rtsp://<device-ip>:8554/live
```
Or record/probe using `ffmpeg`:
```bash
ffmpeg -rtsp_transport tcp -i rtsp://<device-ip>:8554/live -f null -
```

### 2. BirdNET-Go / `go2rtc`
In `config.yaml` or stream configuration:
```yaml
stream:
  url: "rtsp://<device-ip>:8554/live"
  ffmpeg_input_options: "-rtsp_transport tcp"
```
Or if using `go2rtc` format:
```text
rtsp://<device-ip>:8554/live#transport=tcp
```

---

## 🛠️ Dedicated Hardware Appliance Deployment (ADB Commands)

To convert an Android phone into a dedicated, bloat-free streaming hardware appliance over USB:

### 1. Suppress Crash & ANR Popups Globally
```bash
adb shell settings put global show_first_crash_dialog 0
adb shell settings put global show_anr_dialog 0
```

### 2. Restrict Background Process Limits
```bash
adb shell settings put global max_phantom_processes 1
adb shell settings put global default_background_app_time 0
```

### 3. Disable Non-Essential System Bloatware
```bash
adb shell "for pkg in \
  com.android.chrome \
  com.google.android.youtube \
  com.android.vending \
  com.google.android.googlequicksearchbox \
  com.google.android.apps.safetyhub \
  com.google.android.apps.photos \
  com.google.android.apps.maps \
  com.google.android.apps.docs \
  com.google.android.gm \
  com.google.android.calendar \
  com.google.android.contacts \
  com.google.android.calculator \
  com.google.android.deskclock \
  com.google.android.music \
  com.google.android.videos \
  com.google.android.apps.messaging \
  com.google.android.dialer \
  com.google.android.apps.nbu.files \
  com.google.android.apps.tips \
  com.google.android.projection.gearhead \
  com.google.android.apps.wellbeing \
  com.google.android.marvin.talkback \
  com.google.android.apps.wallpaper \
  com.google.android.apps.wallpaper.nexus \
  com.google.android.apps.dreamliner \
  com.google.android.apps.wearables.maestro.companion \
  com.google.android.apps.scone \
  com.google.audio.hearing.visualization.accessibility.scribe \
  com.google.vr.apps.ornament; \
do pm disable-user --user 0 \$pkg 2>/dev/null; done"
```

---

## 🏗️ Building from Source

Build the debug APK using Gradle:
```bash
./gradlew app:assembleDebug
```
Deploy directly to a connected Android device:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
Released under the [MIT License](LICENSE).
