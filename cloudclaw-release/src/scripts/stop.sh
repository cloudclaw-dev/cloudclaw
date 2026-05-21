#!/bin/bash
# CloudClaw Stop Script
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [ -f cloudclaw.pid ]; then
    PID=$(cat cloudclaw.pid)
    if kill -0 "$PID" 2>/dev/null; then
        echo "Stopping CloudClaw (PID: $PID)..."
        kill "$PID"
        rm cloudclaw.pid
        echo "Stopped."
    else
        echo "PID $PID is not running. Cleaning up pid file."
        rm cloudclaw.pid
    fi
else
    echo "No PID file found. CloudClaw may not be running."
fi
