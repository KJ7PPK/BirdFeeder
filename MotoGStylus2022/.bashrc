# Set up standard paths
export PATH=/data/data/com.termux/files/usr/bin:$PATH

# --- Birdfeeder Stream Management ---

start() {
    if pgrep -f BIRDFEEDER-AUDIO.sh > /dev/null; then
        echo "⚠️ Stream is already running (PID: $(pgrep -f BIRDFEEDER-AUDIO.sh))"
    else
        echo "🚀 Starting Birdfeeder Stream..."
        # Launching and redirecting output to log file
        nohup sh ~/BIRDFEEDER-AUDIO.sh > ~/birdfeeder.log 2>&1 &
        
        # Short wait to verify start
        sleep 2
        
        if pgrep -f BIRDFEEDER-AUDIO.sh > /dev/null; then
            echo "✅ Started successfully. View logs with: tail -f ~/birdfeeder.log"
        else
            echo "❌ Failed to start. Check ~/birdfeeder.log for errors."
        fi
    fi
}

stop() {
    if pgrep -f BIRDFEEDER-AUDIO.sh > /dev/null; then
        echo "🛑 Forcefully stopping stream and cleaning up audio processes..."
        
        # Kill the script itself
        pkill -9 -f BIRDFEEDER-AUDIO.sh
        
        # Kill lingering audio processes that might have been left behind
        pkill -9 ffmpeg
        pkill -9 parec
        
        echo "✅ Stream and audio processes stopped."
    else
        echo "⚠️ Stream is not running."
    fi
}

status() {
    if pgrep -f BIRDFEEDER-AUDIO.sh > /dev/null; then
        echo "✅ Stream is running (PID: $(pgrep -f BIRDFEEDER-AUDIO.sh))"
    else
        echo "❌ Stream is NOT running."
    fi
}

# --- Auto-Management Logic ---
# Only run this if the shell is interactive (we are logged in)
if [[ $- == *i* ]]; then
    if pgrep -f BIRDFEEDER-AUDIO.sh > /dev/null; then
        echo "✅ Stream is currently running (PID: $(pgrep -f BIRDFEEDER-AUDIO.sh))."
        read -p "Do you want to stop the stream for a clean terminal? (y/n) " -n 1 -r
        echo # Move to a new line
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            stop
        else
            echo "Leaving stream active in the background."
        fi
    else
        start
    fi
fi
