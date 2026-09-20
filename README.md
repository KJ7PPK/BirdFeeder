<img width="708" height="304" alt="birdfeeder_banner" src="https://github.com/user-attachments/assets/bfa74a93-b4dd-4dc9-bae3-894cb13eb89b" />

BirdFeeder is a single APK compatible with Android 8+ that creates an RTSP audio source using built-in and/or external microphones. Designed with [BirdNET-Go](https://github.com/tphakala/birdnet-go) in mind, but generally compatible with any RTSP consumer. 

> Old Android phones are cheaper, easier to deploy, more reliable, and deliver better sound quality than I was able to get within an enthusiast budget from SBC solutions. I am not a developer and not a fan of general or generative AI, but I feel obliged to disclose that I use Gemini in Android Studio for this app. I don't want to learn a new domain from scratch to fulfill the need for a single, single-purpose application. As such, this app is and will always be free and open source. Please provide feedback on device compatibility, bugs, feature requests, etc. as much as you'd like! I hope you find it useful in some way.
> 
> - Chris (KJ7PPK)

---

## 🛠️ Installation / Device Setup
If you just want to occasionally stream to BirdNET-Go, all you need to do is install and run the app. BirdFeeder is designed with single-purpose use in mind, however. The key to deploying a phone as an appliance is proper debloating, removing MDM packages, etc. Debloating is optional, but recommended. Here's the workflow I follow:

1. **Factory Reset:** Perform a full factory reset on the device.
2. **Initial Setup:** Complete the setup wizard without connecting to Wi-Fi or cellular networks.
3. **Debloat (UAD-NG):** Use [Universal Android Debloater (UAD-NG)](https://github.com/0x10f8/Universal-Android-Debloater-Next-Generation) on the default **"recommended"** list.
   * In addition to the recommended list, also remove `com.google.android.setupwizard` and `com.android.captiveportallogin`.
   * Remove carrier-managed packages for carrier-locked phones.
4. **Suppress System Error Dialogs:** Run these ADB commands to suppress crash and ANR popups globally:
   ```bash
   adb shell settings put global show_first_crash_dialog 0
   adb shell settings put global show_anr_dialog 0
   ```
5. **Install or Update BirdFeeder:** Sideload the release APK over ADB:
   ```bash
   adb install -r BirdFeeder_1.2.apk
   ```
6. **Configure Recommended Settings:** Open BirdFeeder, toggle **Set Home Launcher** to set it as default home app, and toggle **Protect from Battery Saver** to grant background execution.

---
## Features
* **Encoding Options:** Choose between hardware-accelerated **AAC-LC (128 kbps default)** for 85%+ network bandwidth savings and reduced device heat, or uncompressed **L16 PCM (768 kbps)**.
* **Microphone Selection & Location Detection:** Route audio from specific physical microphones (`Built-in Mic - Bottom`, `Top`, `Back`, or `Combined Mics`), as well as plugged-in **3.5mm headset/microphones** and **USB audio interfaces**.
* **Audio Processing Modes:** Select from 5 specialized DSP modes tuned for different acoustic environments:
  * **Basic AGC:** Standard hardware pre-amp boost with automatic gain control (300 Hz – 8 kHz).
  * **Raw (Unprocessed):** Direct ADC signal bypassing all hardware filtering (10 Hz – 24 kHz).
  * **Voice Recognition (Recommended for AI):** Tuned for machine learning models & BirdNET-Go; cuts sub-bass wind rumble while preserving high frequencies (100 Hz – 12 kHz).
  * **Camcorder:** Directional video recording capture (150 Hz – 10 kHz).
  * **Voice Performance:** High-fidelity acoustic dynamic range capture for live outdoor sounds (20 Hz – 20 kHz).
* **Discrete Digital Gain Control:** Adjust digital gain from **25% (-12 dB)** up to **2000% (+26 dB)** to amplify distant outdoor bird calls.
* **Appliance Toggles:**
  * **Set Home Launcher:** Toggle switch to set BirdFeeder as default Android Home Launcher (`android.intent.category.HOME`).
  * **Protect from Battery Saver:** Toggle switch to exempt BirdFeeder from Android Doze background battery restrictions.
* **Smart Network Auto-Recovery:** Prevents streaming when no network IP is assigned and pauses/resumes streams automatically on Wi-Fi state changes.
* **Unified Single-Screen UI:** Displays live animated audio visualizer waveform behind the monospace RTSP URL on a single clean card.

---

## ⚙️ Technical Specs

* **Default Audio Format:** Hardware AAC-LC (128 kbps / 48,000 Hz / Mono)
* **Optional Formats:** AAC-LC (96 kbps) or Uncompressed L16 PCM (`pcm_s16be`, 768 kbps)
* **Sample Rate:** 48,000 Hz (Mono, 1 Channel)
* **RTSP Transport:** TCP Interleaved (`RTP/AVP/TCP`)
* **Default Port:** `8554`
* **RTSP Stream URL:** `rtsp://<device-ip>:8554/live`
* **Network Bandwidth:** ~128 kbps (~16 KB/sec / ~60 MB/hour) on AAC-LC default

---

## 📱 Tested Devices

| Device Model | Android Version | Confirmed Version | Lock / Variant Status | OS / Firmware Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Google Pixel 3 XL** | Android 12 | v1.2 | MDM-Locked | OEM ROM |
| **Google Pixel 2** | Android 15 | v1.2 | Unlocked, Rooted | LineageOS 22.2 |
| **Google Pixel 3** | Android 12 | v1.1 | MDM-Locked | OEM ROM |
| **LG Stylo 5** | Android 9 | v1.1 | N/A | N/A |
| **Motorola Moto G Stylus (2022)** | Android 12 | v1.1 | Carrier-Locked | OEM ROM |
| **OnePlus Nord N200 5G (DE2118)** | Android 12 | v1.1 | Unlocked | OEM OxygenOS ROM |
| **Samsung Galaxy S9** | Android 10 | v1.2 | OEM-Locked | OEM ROM |

_Note:_ This is just a list of my devices and reports by others. Confirmed version is simply the latest version of BirdFeeder that I've deployed on the device. Most of my devices are dedicated to running BirdFeeder for BirdNET-Go, and are confined to comms with that server at a firewall level.

---

## 🎧 Client Playback & Integration
### BirdNET-Go
Add the RTSP URL displayed in the BirdFeeder app to your Streams tab in BirdNET-Go, example:
<img width="1340" height="675" alt="image" src="https://github.com/user-attachments/assets/00126e5f-4dde-4c76-a52d-834231f3d00d" />


### `ffplay` / `ffmpeg` (for general testing if needed)
```bash
ffplay -rtsp_transport tcp rtsp://<device-ip>:8554/live
```

---

## 🌐 WAN-Blocked Devices

Android resists staying connected to Wi-Fi networks that do not provide WAN/internet access. To run devices on an air-gapped or WAN-blocked local network:

1. **Disable Captive Portal Checks via ADB:**
   ```bash
   adb shell settings put global captive_portal_mode 0
   adb shell settings put global captive_portal_detection_enabled 0
   ```
2. **Wi-Fi Network Reconnect:** If connected before running these commands, **forget** the network and reconnect.
3. **Wi-Fi Network Settings:** Under Wi-Fi Advanced settings:
   * Set **Metered:** *Treat as unmetered*
   * Set **Privacy:** *Use Device MAC* (so DHCP reservation applies consistently).

---

## 📜 Credits & Acknowledgments

* **[BirdNET-Go](https://github.com/tphakala/birdnet-go):** The open-source bird sound identification system that this app streams audio to.
* **[BirdFeeder_Termux](https://github.com/KJ7PPK/BirdFeeder_Termux):** The original shell script and Termux-based audio streaming implementation.
* **[FFmpeg](https://ffmpeg.org/):** Multimedia framework used for RTSP playback testing and stream verification.
* **[go2rtc](https://github.com/AlexxIT/go2rtc):** High-performance streaming engine used for RTSP/WebRTC ingestion.
* **[Universal Android Debloater (UAD-NG)](https://github.com/0x10f8/Universal-Android-Debloater-Next-Generation):** Essential open-source debloating tool for Android hardware preparation.
* **[Jetpack Compose](https://developer.android.com/jetpack/compose):** Modern Android UI toolkit by Google.
* **[Material Design Icons](https://fonts.google.com/icons):** FlutterDash bird icon motif.
* **[RootEncoder / RTSP-Server](https://github.com/pedroSG94/RTSP-Server):** Initial RTSP prototyping & network connection interfaces by PedroSG94.
