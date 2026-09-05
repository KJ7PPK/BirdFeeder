# BirdFeeder 🐦📻

This apk is intended to be used as a launcher to turn cheap Android phones into RTSP stream sources, primarily for consumption by BirdNET-GO. I use <$25 Pixel phones from EBay, but since I recently blocked these devices entirely from Internet access, I found the Termux approach (see my other repo) to be clunky. This single APK will create a PCM RTSP stream on launch and you just point BNG to it. My previous workflow with Termux had all devices streaming to a MediaMTX server, then BirdNET-GO pulled the audio from that. This hosts the stream locally on the device and cuts out the need for MediaMTX altogether.

I am not a fan of clankers, but this was built with Android Studio and the Gemini agent because I don't have time to learn this shit, and I have no plans for building other Android apps. Just throwing it out there. Testing is limited, but I will update my device list below as I convert devices from my various other scripts & methods to the single apk here. 

# Devices Tested Successfully
1. Pixel 3 XL, Android 12
2. Pixel 2, Android 15, LineageOS 22.2-20260710-NIGHTLY-walleye
3. Pixel 3, Android 12

# Devices In Progress
1. Moto G Stylus (2022), Android 12 (XT2211-1), app appears to install over ADB but doesn't make it to phone. Working this one.

# Setup
My workflow is pretty straightforward, you need standard adb tools and UAD-NG if you want to debloat:
1. Factory reset device.
2. Perform initial setup, bypassing cellular & wifi connections.
3. Debloat using UAD-NG, my standard is remove everything in the default "recommended" list.
     In addition to the recommended list, I also remove com.google.android.setupwizard, com.android.captiveportallogin, 
5. Install BirdFeeder apk.

# Notes on WAN-restricting devices
1. Android is a dick about connecting to networks that don't provide WAN access. I have worked around this by removing the captiveportal-related packages (above) and running the two items below.
   
   ``adb shell settings put global captive_portal_mode 0``
   
   ``adb shell settings put global captive_portal_detection_enabled 0``
2. If you connect to a network before this is done, you'll need to "forget" it and reconnect. I also choose "advanced" during connection and specify "treat as unmetered" and "Use Device MAC" so my DHCP reservation applies when the device requests an IP.

# Features
There are features I'd like to implement, but for now I wanted the app to replace the launcher, start a stream automatically, and restart on network drops/changes. I experimented with AAC but decided that the bandwidth needed for raw PCM isn't a problem. In the future, I'll probably add options to encode the audio and as I install on more devices, I'll expand the scope.
