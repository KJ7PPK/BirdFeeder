# BirdFeeder - Turn Android phones into RTSP Microphones
BirdFeeder is a single APK that creates an uncompressed PCM audio stream using the device's built-in microphones. Created for use with [BirdNET-Go](https://github.com/tphakala/birdnet-go), but could be utilized for other scenarios where you need an RTSP audio stream. **Compatible with Android 8 and up.**

*(Note: BirdFeeder was previously built on a Termux stack and scripts. If you prefer that method, I've archived it here: [BirdFeeder_Termux](https://github.com/KJ7PPK/BirdFeeder_Termux)*)

---

## 🚀 Features

* **Auto-Stream on Boot & Launch ("Plug & Stream"):** Automatically starts the RTSP stream as soon as the app opens or the phone reboots/powers on.
* **Launcher Replacement:** Acts as an Android Home Launcher (`android.intent.category.HOME`) for dedicated setups.
* **Network Auto-Recovery:** Automatically handles network drops, interface changes, and IP assignments on local Wi-Fi.
* **Uncompressed L16 PCM Audio:** Streams raw 48kHz 16-bit Mono PCM audio directly for maximum recognition accuracy.
* **Hardware Pre-Amp Boost:** Defaults to hardware-boosted microphone input (`AudioSource.MIC`) for capturing distant outdoor sounds, with an option for **Raw (Unprocessed)** audio. Note: Raw audio is not amplified, YMMV.
* **Extended Gain Adjuster:** Digital soft-clipping gain slider adjustable up to **2,000% (20x)**.
* **Single-Screen UI:** Compact single-screen layout with live audio visualizer and controls.
* **Battery Saver Exemption:** Includes Doze mode exemption so Android never kills the background stream service.

---

## ⚙️ Technical Specs

* **Audio Format:** Uncompressed 16-bit Mono L16 PCM (`pcm_s16be`)
* **Sample Rate:** 48,000 Hz (Mono, 1 Channel)
* **RTSP Transport:** TCP Interleaved (`RTP/AVP/TCP`)
* **Default Port:** `8554`
* **RTSP Stream URL:** `rtsp://<device-ip>:8554/live`
* **Audio Bitrate:** 768 kbps (~0.79 Mbps total transfer rate with headers / ~355 MB/hour)

---

## 📱 Tested Devices

| Device Model | Android Version | Lock / Variant Status | OS / Firmware Notes |
| :--- | :--- | :--- | :--- |
| **Google Pixel 2** | Android 15 | Unlocked, Rooted | LineageOS 22.2 |
| **Google Pixel 3** | Android 12 | MDM-Locked | OEM ROM |
| **Google Pixel 3 XL** | Android 12 | MDM-Locked | OEM ROM |
| **Motorola Moto G Stylus (2022)** | Android 12 | Carrier-Locked | OEM ROM |
| **OnePlus Nord N200 5G (DE2118)** | Android 12 | Unlocked | OEM OxygenOS ROM |
| **Samsung Galaxy S9** | Android 10 | OEM-Locked | OEM ROM |

---

## 🛠️ Device Preparation/Debloating
Debloating is optional, but recommended. Here's the workflow I have used on all devices listed above:

1. **Factory Reset:** Perform a full factory reset on the device.
2. **Initial Setup:** Complete the setup wizard without connecting to Wi-Fi or cellular networks.
3. **Debloat (UAD-NG):** Use [Universal Android Debloater (UAD-NG)](https://github.com/0x10f8/Universal-Android-Debloater-Next-Generation) on the default **"recommended"** list.
   * In addition to the recommended list, also remove `com.google.android.setupwizard` and `com.android.captiveportallogin`.
   * This would be the time to remove managed device packages for carrier-locked phones.
4. **Suppress System Error Dialogs:** When debloating system packages on stock Android, background services can occasionally trigger popup error dialogs ("keeps stopping"). Run these two commands over ADB to suppress all crash and ANR popups globally:
   ```bash
   adb shell settings put global show_first_crash_dialog 0
   adb shell settings put global show_anr_dialog 0
   ```
5. **Install or Update BirdFeeder:** Sideload the signed release APK over ADB:
   ```bash
   adb install -r BirdFeeder_1.0.apk (or whatever the current apk release name is)
   ```
6. **Set Launcher & Battery Exemption:** Open BirdFeeder, tap **Set Home Launcher** to set it as the default home app, and tap **Never Kill (Battery Limit)** to grant background execution.

---
## 🎧 Client Playback & Integration
### BirdNET-Go
Add the RTSP URL displayed in the BirdFeeder app to your Streams tab in BirdNET-GO, example:
<img width="1340" height="675" alt="image" src="https://github.com/user-attachments/assets/00126e5f-4dde-4c76-a52d-834231f3d00d" />


### `ffplay` / `ffmpeg` (for general testing if needed)
```bash
ffplay -rtsp_transport tcp rtsp://<device-ip>:8554/live
```

---

## 🌐 WAN-Blocked Devices

Android resists staying connected to Wi-Fi networks that do not provide WAN/internet access. To run devices on an air-gapped or WAN-blocked local network, a few extra steps are needed:

1. **Disable Captive Portal Checks via ADB:**
   ```bash
   adb shell settings put global captive_portal_mode 0
   adb shell settings put global captive_portal_detection_enabled 0
   ```
2. **Wi-Fi Network Reconnect:** If you connected to a network before running these commands, **forget** the network and reconnect.
3. **Wi-Fi Network Settings:** Under Wi-Fi Advanced settings for your network:
   * Set **Metered:** *Treat as unmetered*
   * Set **Privacy:** *Use Device MAC* (so your router's DHCP reservation applies consistently).

---

## 📜 Credits & Acknowledgments

* **[BirdNET-Go](https://github.com/tphakala/birdnet-go):** The open-source bird sound identification system that this app streams audio to.
* **[BirdFeeder_Termux](https://github.com/KJ7PPK/BirdFeeder_Termux):** The original shell script and Termux-based audio streaming implementation.
* **[FFmpeg](https://ffmpeg.org/):** The multimedia framework used for RTSP playback testing and stream verification.
* **[go2rtc](https://github.com/AlexxIT/go2rtc):** High-performance streaming engine used for RTSP/WebRTC ingestion.
* **[Universal Android Debloater (UAD-NG)](https://github.com/0x10f8/Universal-Android-Debloater-Next-Generation):** Essential open-source debloating tool for Android hardware preparation.
* **[Jetpack Compose](https://developer.android.com/jetpack/compose):** Modern Android UI toolkit by Google.
* **[Material Design Icons](https://fonts.google.com/icons):** FlutterDash bird icon motif.
* **[RootEncoder / RTSP-Server](https://github.com/pedroSG94/RTSP-Server):** Initial RTSP prototyping & network connection interfaces by PedroSG94.
