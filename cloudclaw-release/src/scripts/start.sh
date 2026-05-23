#!/bin/bash
# CloudClaw Start Script (Linux/macOS)
# Usage: ./start.sh [standalone|cluster]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Default profile
PROFILE="${1:-standalone}"

# Java options
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m}"

# Check Java
if ! command -v java &> /dev/null; then
    echo "Error: Java 17+ is required but not found in PATH"
    exit 1
fi

echo "Starting CloudClaw ($PROFILE mode)..."
echo "Logs: logs/cloudclaw.out"
echo "PID file: cloudclaw.pid"
echo "Access: http://localhost:${SERVER_PORT:-8080}"

mkdir -p logs

nohup java $JAVA_OPTS -Dfile.encoding=UTF-8 \
    -jar cloudclaw-app-1.0.0.jar \
    --spring.profiles.active="$PROFILE" \
    --spring.config.additional-location=file:./config/ \
    > logs/cloudclaw.out 2>&1 &

echo $! > cloudclaw.pid
echo "Started with PID $(cat cloudclaw.pid)"
