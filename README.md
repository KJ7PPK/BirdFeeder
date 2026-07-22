# BirdFeeder — Android Phones as RTSP Audio Sources

BirdFeeder turns cheap, locked, or other e-waste Android phones (FRP-locked, carrier-locked, bootloader-locked — the ones nobody wants) into single-purpose headless audio appliances that stream to an RTSP server, particularly for consumption by [BirdNET-Go](https://github.com/tphakala/birdnet-go) for ML bird call identification.

I built this with the intent of deploying Pi Zero W nodes across my property, but I was never able to reduce the noise floor and get audio quality I was happy with, no matter what ADC/USB sound card/shielding combination I tried. I reworked the project to deploy on legacy Android phones instead — and the result has genuinely impressed me, both in audio quality and reliability, so I figured it was worth sharing.

**What does it do?**
- Automatically launches as a service at boot via Termux:Boot, no manual interaction required.
- Runs fully headless via Termux.
- Captures audio via PulseAudio's module-sles-source and publishes it live to an RTSP server using ffmpeg
- Automatically recovers from crashes, network drops, rtsp server reboots, etc.
- Waits for connectivity to RTSP server before initializing stream.
- Configurable options:
  - Codec: Opus, AAC, or uncompressed PCM
  - Bitrate (for Opus/AAC)
  - Channel count: mono or stereo (mono by default.)
  - Gain: adjustable dB boost applied via ffmpeg (gain disabled by default.)
  - RTSP host/port/publish path
  - Quiet hours: pauses stream during specified hours to preserve battery. (Disabled by default.)
  - Node label: a friendly name for the status page, useful when you're running multiple nodes
  - Serves a live, auto-refreshing health status page with device info, battery state, connectivity, logs, temperature, and more.
  - Download links for the running script and boot launcher, for quick reference or debugging.
  - Deliberately avoids noise reduction, normalization, or AGC for optimal ML-based bird classification.
  - Clean slate on each launch - solidifying the device as an appliance and preventing duplicate services/streams/etc.

## Hardware Recommendations

This tool is designed around the cheapest phones you can find on eBay — FRP-locked, carrier-locked, bootloader-locked, cracked, whatever. If it boots and has a working WiFi radio and microphone, it's a candidate. See the [Successfully Deployed Devices](#successfully-deployed-devices) section for hardware I've successfully deployed with -- but I've found that the process is pretty similar across devices, with a few nuances for MDM and FRP locks (details provided where I can).

## Health Status Page Example

<img width="958" height="493" alt="BirdFeeder status page example" src="https://github.com/user-attachments/assets/7a498361-791b-40ca-b034-43c95f4dc0fa" />

---

## Table of Contents

- [Successfully Deployed Devices](#successfully-deployed-devices)
- [Debloating Tools](#debloating-tools)
- [MDM/FRP Locks](#locks)
- [Device Preparation](#device-preparation)
- [BirdFeeder Setup Process](#birdfeeder-setup)
- [Acknowledgements](#acknowledgements)
- [Kiosk Mode (Optional)](#kiosk-mode)

---

## Successfully Deployed Devices
- Pixel 2, Android 15, LineageOS 22.2 _FRP lock bypassed_
- Pixel 3 XL, Android 12, Default ROM (Google Fi) _MDM lock bypassed_
- Moto G Stylus 2022 non-5G, Stock ROM (Cricket)
- Raspberry Pi Zero 2 W (deprecated, script in repo for reference)

---

## Debloating Tools
_WIP_

---

## Locks
_WIP_

---

## Device Preparation
_WIP_

1. Evaluate condition & functionality of phone. Note MDM or FRP locks.
2. Perform factory reset or flash to LineageOS wherever possible.
3. Bypass FRP / Remove MDM as needed. (see [MDM/FRP Locks](#locks))
4. Complete OOBE, skipping all setup including cellular, network, PIN, etc.

## BirdFeeder Setup  
The verbiage varies a bit from device to device, but these are my baseline configurations that I configure on the phone by hand:  
    Settings -> About Phone ->  
	 - Device Name: Configure as you'd like.  
	 - Build number: Tap until developer mode is enabled.  
    Settings -> Network and Internet  
	 - About phone  
	 - Private DNS Mode: Off  
	 - Internet: Connect to your SSID, set to treat as unmetered, choose to use device MAC (not randomized), enable "send device name".  
	 - Internet: Network Preferences: Turn on Wi-Fi Automatically enabled, Notify for public networks disabled.  
	 - Airplane Mode: On  
     - Wi-Fi: On  
    Settings -> System -> Developer Options  
	 - USB debugging: On  
	 - Mobile data always active: Off  
    Settings -> Apps -> Termux:API -> Modify System Settings -> Allowed    

Then, connect to the phone via ADB (either USB or network) and run the following:  
```
adb install com.termux_1022.apk com.termux.api_1002.apk com.termux.boot_1000.apk
adb shell pm grant com.termux android.permission.WRITE_SECURE_SETTINGS # SETTING PERMISSIONS
adb shell pm grant com.termux android.permission.RECORD_AUDIO
adb shell pm grant com.termux.api android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.termux.api android.permission.ACCESS_COARSE_LOCATION
adb shell "/system/bin/device_config set_sync_disabled_for_tests persistent"
adb shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"
adb shell "settings put global settings_enable_monitor_phantom_procs false"
```
Manually, on the device again:  
  - Open each Termux app, grant any permissions requested, click the buttons for disabling battery optimization and granting display over other apps in Termux:Boot.  
  - Settings -> Apps -> Termux:API -> Modify System Settings -> Allowed  
  - Run the following to start our termux service and setup SSH:  
```
pkg update && pkg upgrade
pkg install termux-services openssh
sv-enable sshd
passwd
```
- The "passwd" command is to set your SSH login password. You'll need to exit and restart Termux afterward. From there, we can connect to the device over SSH on port 8022 since we're not rooted.
- Place start-birdfeeder.sh into the boot directory we create below (~/.termux/boot/)
- Place birdfeeder.sh in ~/
```
ssh 1.2.3.4 -p 8022
pkg install ffmpeg pulseaudio lighttpd iproute2 termux-api
mkdir ~/.termux/boot/
chmod +x ~/.termux/boot/start-birdfeeder.sh
chmod +x ~/birdfeeder.sh
```

- Add these three lines to the bottom of the lighttpd configuration file:  
```
nano $PREFIX/etc/lighttpd/lighttpd.conf
server.document-root = "/data/data/com.termux/files/home/www"
server.port = 8080
index-file.names = ( "index.html" )
```

---

## Kiosk Mode
_WIP_  
Display the local health page in kiosk browser whenever you physically turn the phone screen on rather than it sitting at the home screen.

---

## Acknowledgements

- [BirdNET-Go](https://github.com/tphakala/birdnet-go) — the whole reason this project exists
- [Termux](https://github.com/termux/termux-app) / [Termux:Boot](https://github.com/termux/termux-boot) / [Termux:API](https://github.com/termux/termux-api)
- [MediaMTX](https://github.com/bluenviron/mediamtx) — RTSP server used in my environment
- [LineageOS](https://lineageos.org/) — used wherever bootlocker unlocking permits
- [FFmpeg](https://ffmpeg.org/) / [PulseAudio](https://www.freedesktop.org/wiki/Software/PulseAudio/) / [lighttpd](https://www.lighttpd.net/)
