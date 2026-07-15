#!/data/data/com.termux/files/usr/bin/sh

# --- 1. EXIT HANDLING ---
trap "killall ffmpeg parec lighttpd; exit" INT TERM

# --- 2. GLOBAL CLEANUP & INITIALIZATION ---
killall -9 ffmpeg parec pulseaudio lighttpd 2>/dev/null
pulseaudio -k 2>/dev/null
mkdir -p ~/www
sleep 1

# Create symlinks so web server can access these files for downloading
ln -sf ~/.bashrc ~/www/bashrc.txt
ln -sf ~/BIRDFEEDER-AUDIO.sh ~/www/script.txt
ln -sf ~/birdfeeder.log ~/www/birdfeeder.log

# --- 3. STATUS PAGE GENERATOR ---
generate_status() {
  local html="$HOME/www/index.html"
  local device_name="Moto G Stylus (2022)"
  # Fetch data
  local wifi_info=$(termux-wifi-connectioninfo)
  local batt_info=$(termux-battery-status)
  
  # Network Info
  local ip=$(echo "$wifi_info" | grep -o '"ip": "[^"]*' | cut -d'"' -f4)
  local mac=$(ip link show wlan0 | grep 'link/ether' | awk '{print $2}')
  local model=$(getprop ro.product.model)
  
  # Battery/Temp Info
  local batt_perc=$(echo "$batt_info" | grep -o '"percentage": [0-9]*' | cut -d' ' -f2)
  local batt_plug=$(echo "$batt_info" | grep -o '"plugged": "[^"]*' | cut -d'"' -f4)
  local temp=$(echo "$batt_info" | grep -o '"temperature": [0-9.]*' | cut -d' ' -f2 | cut -d. -f1)

  # Get the last 20 lines of the log
  local log_tail=$(tail -n 20 ~/birdfeeder.log | sed 's/$/<br>/')

  # Write HTML
  cat <<EOF > "$html"
  <html>
    <head>
      <meta charset="UTF-8">
      <meta http-equiv="refresh" content="30">
    </head>
    <body style="font-family: sans-serif;">
      <h1>$device_name Status</h1>
      <ul>
        <li><b>Model:</b> $model</li>
        <li><b>IP Address:</b> $ip</li>
        <li><b>MAC:</b> $mac</li>
        <li><b>Battery:</b> $batt_perc% (Plugged: $batt_plug)</li>
        <li><b>Device Temp:</b> $temp°C</li>
      </ul>
      
      <h3>Configuration Files</h3>
      <p><a href="bashrc.txt" download>Download .bashrc</a> | 
         <a href="script.txt" download>Download Streaming Script</a></p>

      <h3>Recent Log Output (Last 20 lines)</h3>
      <div style="background: #222; color: #0f0; padding: 10px; font-family: monospace; overflow-x: auto;">
        $log_tail
      </div>

      <p><small>Last updated: $(date)</small></p>
    </body>
  </html>
EOF
}

# --- 4. PERSISTENCE & ENVIRONMENT ---
export PATH=/data/data/com.termux/files/usr/bin:$PATH
termux-wake-lock
termux-brightness 0
sshd

# --- 5. WEB SERVER START ---
lighttpd -f $PREFIX/etc/lighttpd/lighttpd.conf 2>/dev/null

# Start background status updater
(
  while true; do
    generate_status
    sleep 60 
  done
) &

# --- 6. AUDIO INITIALIZATION ---
pulseaudio --start --exit-idle-time=-1
sleep 5
pactl load-module module-sles-source 2>/dev/null || true
sleep 2

MIC_ID=$(pactl list sources short | grep "sles-source" | grep -v "monitor" | awk '{print $1}' | head -n 1)

# --- 7. THE STREAMING LOOP ---
while true; do
  generate_status
  
  if [ -z "$MIC_ID" ]; then
    echo "!!! [$(date +%T)] Critical: Mic not found. Retrying in 10s..."
    sleep 10
    continue
  fi

  echo ">>> [$(date +%T)] Starting Stream..."
  
  # Stream logic
  parec --format=s16le --rate=44100 --channels=2 --device="$MIC_ID" | \
  ffmpeg -re -f s16le -ar 44100 -ac 2 -i - \
  -filter:a "pan=mono|c0=0.5*c0+0.5*c1" \
  -c:a libopus -b:a 64k \
  -rtsp_transport tcp \
  -f rtsp rtsp://172.26.16.22:8556/motophone

  sleep 5
done
