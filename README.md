# BirdFeeder — Turn e-waste phones into RTSP audio sources

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
_WIP_

---

## Device Kiosk Mode
_WIP_
Display the local health page in kiosk browser mode whenever you physically turn the phone screen on. 

---

## Acknowledgements

- [BirdNET-Go](https://github.com/tphakala/birdnet-go) — the whole reason this project exists
- [Termux](https://github.com/termux/termux-app) / [Termux:Boot](https://github.com/termux/termux-boot) / [Termux:API](https://github.com/termux/termux-api)
- [MediaMTX](https://github.com/bluenviron/mediamtx) — RTSP server used in my environment
- [LineageOS](https://lineageos.org/) — used wherever bootlocker unlocking permits
- [FFmpeg](https://ffmpeg.org/) / [PulseAudio](https://www.freedesktop.org/wiki/Software/PulseAudio/) / [lighttpd](https://www.lighttpd.net/)
