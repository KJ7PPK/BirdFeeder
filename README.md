# BirdFeeder 🐦📻

This apk is intended to be used as a launcher to turn cheap Android phones into RTSP stream sources, primarily for consumption by BirdNET-GO. I use <$25 Pixel phones from EBay, but since I recently blocked these devices entirely from Internet access, I found the Termux approach (see my other repo) to be clunky. This single APK will create a PCM RTSP stream on launch and you just point BNG to it. My previous workflow with Termux had all devices streaming to a MediaMTX server, then BirdNET-GO pulled the audio from that. This hosts the stream locally on the device and cuts out the need for MediaMTX altogether.

I am not a fan of clankers, but this was built with Android Studio and the Gemini agent because I don't have time to learn this shit, and I have no plans for building other Android apps. Just throwing it out there. Testing is limited, but I will update my device list below as I convert devices from my various other scripts & methods to the single apk here. 

# Devices Tested Successfully
1. Pixel 3 XL, Android 12, OEM Locked.
2. Pixel 2, Android 15, LineageOS 22.2-20260710-NIGHTLY-walleye
3. Pixel 3, Android 12, Carrier Locked.

# Setup
My workflow is pretty straightforward:
1. Factory reset device.
2. Perform initial setup, bypassing cellular & wifi connections.
3. Debloat using UAD-NG to remove just about everything on the phone.
4. Install BirdFeeder.

# Notes
1. On Pixel phones, you may want to disable the Pixel Setup nonsense with adb (com.google.android.setupwizard)
2. If you're blocking WAN access for your devices, you will want to disable the captive portal junk before connecting to a network --- or forget the network and reconnect to it after disabling the portal stuff. This fixed an issue where my Pixels would boot and start streaming but would never automatically connect to my "No Internet" SSID:
   
   ``adb shell settings put global captive_portal_mode 0``
   
   ``adb shell settings put global captive_portal_detection_enabled 0``

# Features
There are features I'd like to implement, but for now I wanted the app to replace the launcher, start a stream automatically, and restart on network drops/changes. I experimented with AAC but decided that the bandwidth needed for raw PCM isn't a problem. In the future, I'll probably add options to encode the audio and as I install on more devices, I'll expand the scope.
