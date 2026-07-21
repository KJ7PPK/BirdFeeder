#!/data/data/com.termux/files/usr/bin/sh
# ============================================================
# CONFIGURATION — edit these directly, then restart the script
# Once device is streaming, cleanest way to change settings is
# to edit variables, save the file, and reboot the phone.
# off to chase hummingbirds - kj7ppk, chris hawthorne
# ============================================================
CODEC=opus            # opus | aac | pcm
BITRATE=64k           # used for opus/aac only
CHANNELS=1            # 1 for mono. Opus will tell BirdNET-Go it's a stereo stream in mono, two identical channels so either is fine.
GAIN_DB=0             # ffmpeg volume filter, in dB. Recommend doing gain adjustments in BirdNET-Go, but the option is here.
NODE_LABEL="Location" # ie "Backyard", "Shop Roof" — shown on status page
RTSP_HOST=1.1.1.1     # update to your RTSP server IP, i.e. MediaMTX
RTSP_PORT=8554        # default rtsp port is 8554 but this can be changed as needed
RTSP_PATH=birdfeeder  # the path we're publishing to after rtsp://ip:port/)
SLEEP_ENABLED=0       # 1 = pause stream during quiet hours (keeps WiFi/SSH/status page up)
SLEEP_START=23:00     # note: this uses the device's time, so configure accordingly.
SLEEP_END=06:00
# ============================================================

# --- 1. EXIT HANDLING ---
trap "killall ffmpeg parec pulseaudio lighttpd; exit" INT TERM
# --- 2. GLOBAL CLEANUP & INITIALIZATION ---
killall -9 ffmpeg parec pulseaudio lighttpd 2>/dev/null
pulseaudio -k 2>/dev/null
rm -f $PREFIX/tmp/pulse-*/pid 2>/dev/null
# termux-services can re-register a runsv-supervised lighttpd across reboots;
# clear it every time rather than relying on a one-time manual disable.
pkill -9 -f "runsv lighttpd" 2>/dev/null
rm -rf $PREFIX/var/service/lighttpd 2>/dev/null
pkill -9 -f lighttpd 2>/dev/null
mkdir -p ~/www
sleep 1
export TZ=$(getprop persist.sys.timezone)
ln -sf ~/.termux/boot/start-birdfeeder.sh ~/www/start-birdfeeder.txt
ln -sf ~/birdfeeder.sh ~/www/birdfeeder.txt
ln -sf ~/birdfeeder.log ~/www/birdfeeder.log

is_sleep_time() {
  [ "$SLEEP_ENABLED" = "1" ] || return 1
  local now=$(date +%H%M)
  local start=$(echo "$SLEEP_START" | tr -d ':')
  local end=$(echo "$SLEEP_END" | tr -d ':')
  if [ "$start" -lt "$end" ]; then
    [ "$now" -ge "$start" ] && [ "$now" -lt "$end" ]
  else
    [ "$now" -ge "$start" ] || [ "$now" -lt "$end" ]
  fi
}

# --- 3. STATUS PAGE GENERATOR ---
generate_status() {
  local html="$HOME/www/index.html"
  local model=$(getprop ro.product.model)
  local display_name="$model"
  [ -n "$NODE_LABEL" ] && display_name="$NODE_LABEL ($model)"
  local uptime_str=$(uptime -p 2>/dev/null || awk '{printf "%.0f minutes", $1/60}' /proc/uptime)
  local wifi_info=$(termux-wifi-connectioninfo)
  local batt_info=$(termux-battery-status)
  local ip=$(echo "$wifi_info" | grep -o '"ip": "[^"]*' | cut -d'"' -f4)
  local rssi=$(echo "$wifi_info" | grep -o '"rssi": [-0-9]*' | awk '{print $2}')
  local link_speed=$(echo "$wifi_info" | grep -o '"link_speed_mbps": [0-9]*' | awk '{print $2}')
  local mac=$(ip link show wlan0 | grep 'link/ether' | awk '{print $2}')
  local batt_perc=$(echo "$batt_info" | grep -o '"percentage": [0-9]*' | cut -d' ' -f2)
  local batt_plug=$(echo "$batt_info" | grep -o '"plugged": "[^"]*' | cut -d'"' -f4)
  local temp=$(echo "$batt_info" | grep -o '"temperature": [0-9.]*' | cut -d' ' -f2 | cut -d. -f1)
  local svc_ffmpeg="DOWN"; pgrep -x ffmpeg > /dev/null && svc_ffmpeg="UP"
  local svc_audio="DOWN"; pgrep -f 'parec|rec ' > /dev/null && svc_audio="UP"
  local svc_pulse="DOWN"; pgrep -x pulseaudio > /dev/null && svc_pulse="UP"
  local svc_lighttpd="DOWN"; pgrep -f lighttpd > /dev/null && svc_lighttpd="UP"
  local svc_sshd="DOWN"; pgrep -x sshd > /dev/null && svc_sshd="UP"
  local svc_main="DOWN"; pgrep -f "$HOME/birdfeeder.sh" > /dev/null && svc_main="UP"
  status_badge() { [ "$1" = "UP" ] && echo '<span class="badge up">UP</span>' || echo '<span class="badge down">DOWN</span>'; }
  local sleep_badge=""
  if is_sleep_time; then
    sleep_badge="<div class=\"card\"><h3>Quiet Hours Active</h3><p>Stream paused until $SLEEP_END</p></div>"
  fi
  local bw_estimate="$BITRATE ($CODEC, compressed)"
  [ "$CODEC" = "pcm" ] && bw_estimate="~$(( 48000 * 16 * CHANNELS / 1000 )) kbps (PCM 48kHz, ${CHANNELS}ch, uncompressed)"
  local log_tail=$(tail -n 20 ~/birdfeeder.log | sed 's/$/<br>/')
  cat <<EOF > "$html"
  <html>
    <head>
      <meta charset="UTF-8">
      <meta http-equiv="refresh" content="30">
      <style>
        body { font-family: sans-serif; background: #f4f4f4; margin: 0; padding: 20px; }
        h1 { margin-top: 0; }
        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; }
        .card { background: #fff; border-radius: 8px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.15); }
        .card h3 { margin-top: 0; border-bottom: 1px solid #eee; padding-bottom: 8px; }
        .card ul { list-style: none; padding: 0; margin: 0; }
        .card li { padding: 4px 0; }
        .badge { padding: 2px 8px; border-radius: 4px; color: #fff; font-size: 0.85em; }
        .badge.up { background: #2e7d32; }
        .badge.down { background: #c62828; }
        .log { background: #222; color: #0f0; padding: 10px; font-family: monospace;
               overflow-x: auto; border-radius: 6px; font-size: 0.85em; }
      </style>
      <title>
        BirdFeeder Node Status
      </title>  
    </head>
    <body>
      <h1>$display_name</h1>
      <p><small>Last updated: $(date)</small></p>
      $sleep_badge
      <div class="grid">
        <div class="card">
          <h3>Services Status</h3>
          <ul>
            <li>Main Script: $(status_badge "$svc_main")</li>
            <li>Stream (ffmpeg): $(status_badge "$svc_ffmpeg")</li>
            <li>Audio Capture: $(status_badge "$svc_audio")</li>
            <li>PulseAudio: $(status_badge "$svc_pulse")</li>
            <li>Web Server: $(status_badge "$svc_lighttpd")</li>
            <li>SSH: $(status_badge "$svc_sshd")</li>
          </ul>
        </div>
        <div class="card">
          <h3>Node Info</h3>
          <ul>
            <li><b>Model:</b> $model</li>
            <li><b>Uptime:</b> $uptime_str</li>
            <li><b>Battery:</b> $batt_perc% (Plugged: $batt_plug)</li>
            <li><b>Temp:</b> $temp°C</li>
          </ul>
        </div>
        <div class="card">
          <h3>Network</h3>
          <ul>
            <li><b>Signal:</b> $rssi dBm</li>
            <li><b>Link Speed:</b> $link_speed Mbps</li>
            <li><b>IP:</b> $ip</li>
            <li><b>MAC:</b> $mac</li>
          </ul>
        </div>
        <div class="card">
          <h3>Stream Details</h3>
          <ul>
            <li><b>Codec:</b> $CODEC</li>
            <li><b>Channels:</b> $CHANNELS</li>
            <li><b>Gain:</b> ${GAIN_DB} dB</li>
            <li><b>Est. Bandwidth:</b> $bw_estimate</li>
            <li><b>Path:</b> rtsp://$RTSP_HOST:$RTSP_PORT/$RTSP_PATH</li>
          </ul>
        </div>
        <div class="card">
          <h3>Files on Node</h3>
          <ul>
            <li><a href="start-birdfeeder.txt" download>Download Launcher</a></li>
            <li><a href="birdfeeder.txt" download>Download Script</a></li>
          </ul>
        </div>
      </div>
      <h3>Recent Log</h3>
      <div class="log">$log_tail</div>
    </body>
  </html>
EOF
}
# --- 4. PERSISTENCE & ENVIRONMENT ---
export PATH=/data/data/com.termux/files/usr/bin:$PATH
# --- 5. WEB SERVER START ---
lighttpd -f $PREFIX/etc/lighttpd/lighttpd.conf >> ~/birdfeeder.log 2>&1
(
  LOG_RESET_AT=$(date +%s)
  while true; do
    now=$(date +%s)
    if [ $((now - LOG_RESET_AT)) -ge 300 ]; then
      : > ~/birdfeeder.log
      LOG_RESET_AT=$now
      echo ">>> [$(date +%T)] Log cleared (5-minute snapshot)." >> ~/birdfeeder.log
    fi
    generate_status
    sleep 60
  done
) &
# --- 6. WAIT FOR NETWORK ---
WAIT_TARGET=$(ip route | grep default | awk '{print $3}')
[ -z "$WAIT_TARGET" ] && WAIT_TARGET="$RTSP_HOST"
echo ">>> [$(date +%T)] Waiting for network (target: $WAIT_TARGET)..." >> ~/birdfeeder.log
until ping -c1 -W2 "$WAIT_TARGET" > /dev/null 2>&1; do
  sleep 2
done
echo ">>> [$(date +%T)] Network reachable, proceeding." >> ~/birdfeeder.log
# --- 7. AUDIO INITIALIZATION ---
pulseaudio --start --exit-idle-time=-1 >> ~/birdfeeder.log 2>&1
sleep 5
pactl load-module module-sles-source >> ~/birdfeeder.log 2>&1
sleep 2
MIC_ID=$(pactl list sources short | grep "sles-source" | grep -v "monitor" | awk '{print $1}' | head -n 1)
echo ">>> [$(date +%T)] Using PulseAudio source ID: $MIC_ID" >> ~/birdfeeder.log
# --- 8. THE STREAMING LOOP ---
while true; do
  generate_status
  if is_sleep_time; then
    pkill -9 -f 'parec|ffmpeg' 2>/dev/null
    echo ">>> [$(date +%T)] Quiet hours active ($SLEEP_START-$SLEEP_END), stream paused." >> ~/birdfeeder.log
    sleep 60
    continue
  fi
  if [ -z "$MIC_ID" ]; then
    echo "!!! [$(date +%T)] Critical: Mic not found. Retrying in 10s..." >> ~/birdfeeder.log
    sleep 10
    MIC_ID=$(pactl list sources short | grep "sles-source" | grep -v "monitor" | awk '{print $1}' | head -n 1)
    continue
  fi
  echo ">>> [$(date +%T)] Starting Stream (codec=$CODEC, ch=$CHANNELS, gain=${GAIN_DB}dB, path=$RTSP_PATH)..." >> ~/birdfeeder.log
  case "$CODEC" in
    pcm)
      parec --format=s16le --rate=44100 --channels="$CHANNELS" --device="$MIC_ID" 2>>~/birdfeeder.log | \
      ffmpeg -re -f s16le -ar 44100 -ac "$CHANNELS" -i - \
      -af "volume=${GAIN_DB}dB" \
      -acodec pcm_s16be -ar 48000 -ac "$CHANNELS" \
      -rtsp_transport tcp -f rtsp "rtsp://$RTSP_HOST:$RTSP_PORT/$RTSP_PATH" >> ~/birdfeeder.log 2>&1
      ;;
    aac)
      parec --format=s16le --rate=44100 --channels="$CHANNELS" --device="$MIC_ID" 2>>~/birdfeeder.log | \
      ffmpeg -re -f s16le -ar 44100 -ac "$CHANNELS" -i - \
      -af "volume=${GAIN_DB}dB" \
      -c:a aac -b:a "$BITRATE" -ac "$CHANNELS" \
      -rtsp_transport tcp -f rtsp "rtsp://$RTSP_HOST:$RTSP_PORT/$RTSP_PATH" >> ~/birdfeeder.log 2>&1
      ;;
    *)
      parec --format=s16le --rate=44100 --channels="$CHANNELS" --device="$MIC_ID" 2>>~/birdfeeder.log | \
      ffmpeg -re -f s16le -ar 44100 -ac "$CHANNELS" -i - \
      -af "volume=${GAIN_DB}dB" \
      -c:a libopus -b:a "$BITRATE" -ac "$CHANNELS" \
      -rtsp_transport tcp -f rtsp "rtsp://$RTSP_HOST:$RTSP_PORT/$RTSP_PATH" >> ~/birdfeeder.log 2>&1
      ;;
  esac
  echo "!!! [$(date +%T)] Stream ended (exit code $?), retrying in 5s..." >> ~/birdfeeder.log
  sleep 5
done
