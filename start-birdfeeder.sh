#!/data/data/com.termux/files/usr/bin/sh
export PATH=/data/data/com.termux/files/usr/bin:$PATH
export HOME=/data/data/com.termux/files/home
termux-wake-lock
termux-brightness 0
sshd
if ! pgrep -f "$HOME/birdfeeder.sh" > /dev/null; then
  nohup sh "$HOME/birdfeeder.sh" > "$HOME/birdfeeder.log" 2>&1 &
fi
