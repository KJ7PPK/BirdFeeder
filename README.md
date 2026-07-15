# Turning legacy Android phones into RTSP/BirdNET-Go audio sources! 
Initially, this Repo served to document the method I used for configuring a PiZ2W to feed MediaMTX for consumption by BirdNET-Go (https://github.com/tphakala/birdnet-go). I have been unhappy with the audio quality from Pi devices after trying half a dozen microphone configurations. I will leave the original Pi script up (it still works), but I'm pivoting focus from RPI to legacy Android phones, which I've found make EXCELLENT streaming appliances for audio (and video, but not implementing that quite yet).

# JULY 2026 UPDATE
I will begin adding the scripts, setup processes, etc. into folders by phone model as the process will naturally vary by phone, Android version, etc. The ideal setup is something flashed to LineageOS, but you can get by with anything that you can run ADB against basically -- we're basically cleaning the phone of bloat, disabling power-save features, installing a few pre-reqs, and setting up our script to run automatically with .bashrc in Termux. MORE TO COME!

# MOTO G STYLUS (2022)
I have successfully deployed a carrier-locked Moto G Stylus (2022) and the audio quality blows the Pi hardware out of the water. Stream is resiliant to reboots and failures & has a basic health webpage much like my Pi Zero script had. Documentation: WIP.
