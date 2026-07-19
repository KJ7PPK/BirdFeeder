# Turn e-waste Android phones into RTSP audio sources - WIP
**created for use with BirdNET-Go (https://github.com/tphakala/birdnet-go)**

BirdNET-GO's RTSP support has continued getting better and better, and BirdFeeder is the solution for deploying multiple RTSP sources without dipping into hardware mods. This Repo shifted from Pi-specific to Android as phones produce cleaner audio, hold stronger connections, don't require assembly/soldering/enclosures, and the cost is lower. BirdFeeder.sh does the heavy lifting with a handful of dependencies and tweaks required. Handles reboots, crashes, etc. and offers configurable settings for RTSP publish path, codec, bitrate, sleep schedule, etc. along with a lighttpd-based health status page. Any device-specific steps/tweaks/tips will be outlined in subdirectories as I discover them. Documentation will be released after validation with latest script revision on a second wiped device. Currently field-testing with latest BirdNET-Go on a Pixel 3 XL, carrier-locked, fully headless. 

# Health Status Page Example
<img width="1917" height="987" alt="image" src="https://github.com/user-attachments/assets/7a498361-791b-40ca-b034-43c95f4dc0fa" />

**Hardware Recommendations**
This utility is designed to turn the cheapest phones on EBay (FRP-locked, carrier-locked, bootloader locked, etc.) into single-purpose appliances rather than e-waste. That said, I recommend staying away from any Verizon phones since they love to _**fuck**_ their customers with locked bootloaders. Phones that you can flash to LineageOS or otherwise root will make life simpler and give better results, specifically capturing UNPROCESSED audio since whatever pulseaudio gets through termux is what it is. I'd like to deploy a few variants of LG V## which were generally known for superb microphone arrays. In the end, this project aims to make the cheapest devices to acquire do the job in a reliable and effective manner. At a minimum, you need a working digitizer, mostly working screen, and working Wi-Fi radio. Stay away from Verizon phones or anything MDM-locked, but neither is a showstopper - just an annoyance that we overcome through sheer anger-driven determination.  


**Successfully Deployed Devices**

_PIXEL 3 XL, Android 12_, (Carrier and MDM Locked!)
- Currently streaming with latest BirdFeeder script. This runs "headless" compared to the Moto G which relies on Termux as a launcher / in the foreground. This is the basis for how I set up all devices moving forward. Completed 7/18/26 and put into the field.

_Raspberry Pi Zero 2 W_ (In use, but deprecating.)
Script and instructions are in this repo. I will not be doing any further work on Pi hardware for this use, the hardware cost vs. the phone approach is silly and the audio quality is poor.

_MOTO G STYLUS 2022 - Android #?_ (REWORK SOON)
First phone I did this one, stream is resiliant & has basic health page. I will reset and attempt my Android 12 / Pixel streaming method as it is much more refined than what it runs currently.


**_Other Devices In Progress_**

Pixel 2 - LineageOS Android 15 - Ready to install and document, expect to have script specifically for LineageOS devices so will take time.
Pixel 2 XL - Android 12 - MDM lock resolved, bootloader locked. Potentially have dying digitizer, may push this or skip altogether.
Pixel 3 - Android 12 - MDM lock resolved, bootloader locked. Ready to debloat, install, and document.
Pixel 3 XL - Android 12 - MDM lock resolved, bootloader locked. Ready to debloat, install, and document.
