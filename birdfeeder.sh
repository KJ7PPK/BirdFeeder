#!/bin/bash
set -euo pipefail

#####################
# This script publishes audio from a USB microphone to an RTSP server.
# Built for use with birdnet-go and mediamtx on a Pi Zero 2W.
# Optional static page health monitoring added 4/12/2026.
# Chris Hawthorne, KJ7PPK
#####################

### CONFIGURATION ###
# set to usb mic device id and sample rate
DEVICE="plughw:0,0"
RATE="48000"
# streaming bitrate
BITRATE="128k"
# RTSP publishing url
URL="rtsp://172.26.16.22:8554/birdfeeder2"
# buffer size, 4096 works for pi zero2w
THREAD_QUEUE_SIZE="4096"
# optional gain, so live stream is audible in birdnet before processing
GAIN_DB="15"
# prevent audio clipping just in case, reduce gain if this happens a lot
LIMITER_LEVEL="0.95"

# health monitor generates static html page
STATUS_ENABLE=1
# directory for health file
STATUS_DIR="/tmp/birdfeeder"
# health update interval
STATUS_INTERVAL=30
### END CONFIGURATION ###

### HEALTH FUNCTION ###
write_status() {
    mkdir -p "$STATUS_DIR"
    NOW=$(date)
    LOAD=$(cut -d' ' -f1 /proc/loadavg)
    MEM=$(free -m | awk '/Mem:/ {printf "%s/%s MB", $3, $2}')
    TEMP=$(vcgencmd measure_temp 2>/dev/null | cut -d= -f2)
    WIFI=$(iw dev wlan0 link 2>/dev/null)
    FFMPEG=$(pgrep -af ffmpeg >/dev/null && echo "RUNNING" || echo "STOPPED")
    SERVICE=$(systemctl is-active birdfeeder-audio.service 2>/dev/null || echo "unknown")
    DEVICE_MODEL=$(tr -d '\0' < /proc/device-tree/model 2>/dev/null)
    OS_VERSION=$(grep PRETTY_NAME /etc/os-release | cut -d= -f2 | tr -d '"')
    UPTIME=$(uptime -p)
    CPU=$(top -bn1 | awk '/Cpu\(s\)/ {print 100 - $8}')
    DISK=$(df -h / | awk 'NR==2 {print $3 "/" $2 " (" $5 ")"}')
    THROTTLED=$(vcgencmd get_throttled 2>/dev/null)
    TEMP_RAW=$(vcgencmd measure_temp 2>/dev/null | cut -d= -f2 | tr -d "'C")
    TEMP_C=${TEMP_RAW%.*}
    FFMPEG_PID=$(pgrep -x ffmpeg)
    FFMPEG_STATE="STOPPED"
    FFMPEG_UPTIME="-"
if [ -n "$FFMPEG_PID" ]; then
    FFMPEG_STATE="RUNNING"
    FFMPEG_UPTIME=$(ps -o etime= -p "$FFMPEG_PID" 2>/dev/null)
fi
    HEALTH="OK"
if [ "$FFMPEG_STATE" != "RUNNING" ]; then
    HEALTH="STREAM DOWN"
fi
if [ "$TEMP_C" -gt 75 ] 2>/dev/null; then
    HEALTH="HOT"
fi
    WIFI=$(iw dev wlan0 link 2>/dev/null)
    cat > "$STATUS_DIR/index.html" <<EOF
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>$HOSTNAME Status</title>
</head>
<body style="font-family: monospace; background: #111; color: #eee; padding: 20px;">
<h3>🐦 Birdfeeder Node Status</h2>
<table cellpadding="6" cellspacing="0" border="1" style="border-color:#444;">
<tr><td>Hostname:</td><td>$HOSTNAME</td></tr>
<tr><td>Device</td><td>$DEVICE_MODEL</td></tr>
<tr><td>OS</td><td>$OS_VERSION</td></tr>
<tr><td>Uptime</td><td>$UPTIME</td></tr>
<tr><td>CPU Usage</td><td>$CPU%</td></tr>
<tr><td>Disk</td><td>$DISK</td></tr>
<tr><td>Temp</td><td>$TEMP_RAW</td></tr>
<tr><td>Throttle</td><td>$THROTTLED</td></tr>
<tr><td>Stream</td><td>$FFMPEG_STATE ($FFMPEG_UPTIME)</td></tr>
<tr><td>Time</td><td>$NOW</td></tr>
<tr><td>Service</td><td>$SERVICE</td></tr>
<tr><td>FFmpeg</td><td>$FFMPEG</td></tr>
<tr><td>CPU Load</td><td>$LOAD</td></tr>
<tr><td>Memory</td><td>$MEM</td></tr>
<tr><td>Temperature</td><td>$TEMP</td></tr>
<tr><td>Network Info</td><td>$WIFI</td></tr>
</table>
</body>
</html>
EOF
}

### START HEALTH MONITOR & WEB SERVER
if [ "$STATUS_ENABLE" -eq 1 ]; then
    (
        while true; do
            write_status
            sleep "$STATUS_INTERVAL"
        done
    ) &
    busybox httpd -f -p 8080 -h "$STATUS_DIR" &
fi

### START STREAM
AUDIO_FILTER="volume=${GAIN_DB}dB,alimiter=limit=${LIMITER_LEVEL}"
exec ffmpeg -hide_banner -loglevel warning -nostdin \
-thread_queue_size "$THREAD_QUEUE_SIZE" \
-f alsa -ac 1 -ar "$RATE" -i "$DEVICE" \
-c:a aac -b:a "$BITRATE" \
-ac 1 \
-af "$AUDIO_FILTER" \
-f rtsp -rtsp_transport tcp \
-muxdelay 0 -muxpreload 0 \
"$URL"
