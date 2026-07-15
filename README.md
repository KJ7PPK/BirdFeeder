# Turning legacy Android phones into RTSP/BirdNET-Go audio sources! 
Initially, this Repo served to document the method I used for configuring a PiZ2W to feed MediaMTX for consumption by BirdNET-Go (https://github.com/tphakala/birdnet-go). I have been unhappy with the audio quality from Pi devices after trying half a dozen microphone configurations. I will leave the original Pi script up (it still works), but I'm pivoting focus from RPI to legacy Android phones, which I've found make EXCELLENT streaming appliances for audio (and video, but not implementing that quite yet).

# JULY 2026 UPDATE
I will begin adding the scripts, setup processes, etc. into folders by phone model as the process will naturally vary by phone, Android version, etc. The ideal setup is something flashed to LineageOS, but you can get by with anything that you can run ADB against basically -- we're basically cleaning the phone of bloat, disabling power-save features, installing a few pre-reqs, and setting up our script to run automatically with .bashrc in Termux. MORE TO COME!

# MOTO G STYLUS (2022)
I have successfully deployed a carrier-locked Moto G Stylus (2022) and the audio quality blows the Pi hardware out of the water. Stream is resiliant to reboots and failures & has a basic health webpage much like my Pi Zero script had. Documentation: WIP.

# The OG - Pi Zero / 2 W RTSP Streaming Script
This script turns a Pi into an appliance for exactly that purpose. I found that the actual capture and stream function leaves plenty of headroom even on a Zero 2W, so since BirdNET-Go can really only tell me if the RTSP stream is up or down, I implemented a simple busybox function to publish a "health" overview page on port 8080. Eventually, I plan to migrate away from the static page generation and publish to MQTT or otherwise integrate into Home Assistant for monitoring the node(s).

# My setup:
1. Raspberry Pi Zero 2W
2. Micro-to-A USB adapter.
3. $1 USB sound card
4. $3 omnidirectional lav microphone.
5. Standard Pi power supply.
6. 3D printed case.
7. MediaMTX running on a separate server alongside BirdNET-Go in Docker on OpenMediaVault.
   
# Functionality
- Captures audio from a USB sound card, encodes in AAC and publishes to an RTSP destination with FFMPEG.
- Optional: applies gain to audio before encoding.
- Optional: generates a static html system health page served on port 8080.

# SETUP BASICS FOR PI ZERO W
1. Put birdfeeder-audio.service in /etc/systemd/system/
2. Put birdfeeder-audio.service in /usr/local/bin/

# AUDIO CONFIGURATION OPTIONS
1. DEVICE: USB mic / sound card hardware ID (find via via arecord -l, mine tends to be 0,0)
2. RATE: Audio capture frequency (set to 48khz as that's my sound card's native)
3. BITRATE: Streaming bitrate, 128k seems to work just fine but this can be dropped down as needed, or increased with higher quality audio input.
4. URL: RTSP publishing path
5. THREAD_QUEUE_SIZE: Prevents loss of audio from spotty connections, good results at 4096.
6. GAIN_DB: Birdnet will normalize levels in clips, but live audio was low. I constantly used gain in the UI. Applying 15db gain worked for me.
7. LIMITER_LEVEL: This is just to prevent clipping, but realistically if this is utilized a lot, gain is too high.

# HEALTH CONFIGURATION OPTIONS
1. STATUS_ENABLE: 1 turns on this functionality, 0 disables it.
2. STATUS_DIR: This is where we're writing the health information to periodically.
3. STATUS_INTERVAL: This is how frequently we're updating the health data file.

# TROUBLESHOOTING BASICS
1. Basically any adjustments you make to the .sh, just reboot the Pi instead of restarting the service.
2. systemctl status birdfeeder-audio.service should tell you what's going on if there are any errors.

# Future Plans
I still need to evaluate power usage and heat tolerance to build a more rugged enclosure, or potentially even migrate away from the Pi Zero hardware. I also need to evaluate the audio quality with a better microphone setup (preferably not USB), but a bird or a cat literally stole my RODE mic a while back. I'm sure there's more.
