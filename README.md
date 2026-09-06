# BirdFeeder 🐦📻

This APK is intended to be used as a launcher to turn cheap Android phones into RTSP stream sources, primarily for consumption by **BirdNET-Go**.

I use <$25 Pixel phones from eBay for wildlife and bird call monitoring. Since I recently blocked these devices entirely from Internet access, I found my previous Termux setup to be clunky. My previous workflow with Termux had all devices streaming to a MediaMTX server, then BirdNET-Go pulled the audio from that. 

**BirdFeeder** replaces that entire chain: this single APK runs as the device launcher, creates an uncompressed PCM RTSP stream locally on the phone upon launch or boot, and cuts out the need for MediaMTX or Termux altogether.

*(Note: If you prefer using easily editable shell scripts and Termux instead of a standalone APK, check out my script-based repository: [BirdFeeder_Termux](https://github.com/KJ7PPK/BirdFeeder_Termux).)*

---

## 🚀 Features

* **Auto-Stream on Boot & Launch ("Plug & Stream"):** Automatically starts the RTSP stream as soon as the app opens or the phone reboots/powers on.
* **Launcher Replacement:** Acts as an Android Home Launcher (`android.intent.category.HOME`) for dedicated kiosk setups.
* **Network Auto-Recovery:** Automatically handles network drops, interface changes, and IP assignments on local Wi-Fi.
* **Uncompressed L16 PCM Audio:** Streams raw 48kHz 16-bit Mono PCM audio directly for maximum recognition accuracy.
* **Hardware Pre-Amp Boost:** Defaults to hardware-boosted microphone input (`AudioSource.MIC`) for capturing distant outdoor sounds, with an option for **Raw (Unprocessed)** audio.
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
| **Pixel 3 XL** | Android 12 | MDM-Locked | Stock Google ROM |
| **Pixel 3** | Android 12 | MDM-Locked | Stock Google ROM |
| **Pixel 2** | Android 15 | Unlocked | LineageOS 22.2 (`NIGHTLY-walleye`) |
| **Moto G Stylus (2022, XT2211-1)** | Android 12 | Cricket Variant | Stock Motorola ROM |

---

## 🛠️ Device Preparation & Debloating

My workflow for setting up a device:

1. **Factory Reset:** Perform a full factory reset on the device.
2. **Initial Setup:** Complete the setup wizard without connecting to Wi-Fi or cellular networks.
3. **Debloat (UAD-NG):** Use [Universal Android Debloater (UAD-NG)](https://github.com/0x10f8/Universal-Android-Debloater-Next-Generation) on the default **"recommended"** list.
   * In addition to the recommended list, also remove `com.google.android.setupwizard` and `com.android.captiveportallogin`.
4. **Suppress System Error Dialogs:** When debloating system packages on stock Android, background services occasionally trigger popup error dialogs ("keeps stopping"). Run these two commands over ADB to suppress all crash and ANR popups globally:
   ```bash
   adb shell settings put global show_first_crash_dialog 0
   adb shell settings put global show_anr_dialog 0
   ```
5. **Install BirdFeeder:** Sideload the signed release APK over ADB:
   ```bash
   adb install -r app-release.apk
   ```
6. **Set Launcher & Battery Exemption:** Open BirdFeeder, tap **Set Home Launcher** to set it as the default home app, and tap **Never Kill (Battery Limit)** to grant background execution.

---

## 🌐 WAN-Blocked Devices

Android resists staying connected to Wi-Fi networks that do not provide WAN/internet access. To run devices on an air-gapped or WAN-blocked local network:

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

## 🎧 Client Playback & Integration

### `ffplay` / `ffmpeg`
```bash
ffplay -rtsp_transport tcp rtsp://<device-ip>:8554/live
```

### BirdNET-Go / `go2rtc`
```yaml
stream:
  url: "rtsp://<device-ip>:8554/live"
  ffmpeg_input_options: "-rtsp_transport tcp"
```
Or with `go2rtc`:
```text
rtsp://<device-ip>:8554/live#transport=tcp
```

---

## 📜 Credits & Acknowledgments

* **[BirdNET-Go](https://github.com/birdnet-team/birdnet-go):** The open-source bird sound identification system that this app streams audio to.
* **[BirdFeeder_Termux](https://github.com/KJ7PPK/BirdFeeder_Termux):** The original shell script and Termux-based audio streaming implementation.
* **[FFmpeg](https://ffmpeg.org/):** The multimedia framework used for RTSP playback testing and stream verification.
* **[go2rtc](https://github.com/AlexxIT/go2rtc):** High-performance streaming engine used for RTSP/WebRTC ingestion.
* **[Universal Android Debloater (UAD-NG)](https://github.com/0x10f8/Universal-Android-Debloater-Next-Generation):** Essential open-source debloating tool for Android hardware preparation.
* **[Jetpack Compose](https://developer.android.com/jetpack/compose):** Modern Android UI toolkit by Google.
* **[Material Design Icons](https://fonts.google.com/icons):** FlutterDash bird icon motif.
* **[RootEncoder / RTSP-Server](https://github.com/pedroSG94/RTSP-Server):** Initial RTSP prototyping & network connection interfaces by PedroSG94.
