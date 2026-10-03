#!/usr/bin/env bash
# Deploy the PK server to the LAN test box (192.168.110.140, port 8090 —
# 8080 is occupied by SRS there).
#
# Usage:
#   ./deploy.sh <user>@192.168.110.140        # remote deploy over SSH
#   SSH_PASSWORD=xxx ./deploy.sh <user>@...   # with sshpass (if installed)
#
# The remote needs Java 17+: apt/yum install openjdk-17-jre (Linux).
set -euo pipefail

HOST="${1:?usage: ./deploy.sh <user>@192.168.110.140}"
PORT=8090
JAR="memorygames-pk-server-0.1.0.jar"
REMOTE_DIR="~/memorygames-pk"

SSH="ssh"
SCP="scp"
if [ -n "${SSH_PASSWORD:-}" ] && command -v sshpass >/dev/null 2>&1; then
    SSH="sshpass -p $SSH_PASSWORD ssh -o StrictHostKeyChecking=accept-new"
    SCP="sshpass -p $SSH_PASSWORD scp -o StrictHostKeyChecking=accept-new"
fi

echo "==> uploading $JAR to $HOST:$REMOTE_DIR"
$SSH "$HOST" "mkdir -p $REMOTE_DIR"
$SCP "$JAR" "$HOST:$REMOTE_DIR/app.jar"

echo "==> restarting service on port $PORT"
$SSH "$HOST" "cd $REMOTE_DIR && \
    pkill -f 'app.jar' 2>/dev/null; sleep 1; \
    nohup java -Xms128m -Xmx512m \
      -Dserver.address=0.0.0.0 -Dserver.port=$PORT \
      -Dspring.datasource.url='jdbc:h2:file:./data/pk;DB_CLOSE_DELAY=-1' \
      -jar app.jar > pk-server.log 2>&1 & \
    sleep 4; tail -n 5 pk-server.log"

echo "==> health check (ws endpoint must answer the HTTP upgrade)"
if $SSH "$HOST" "curl -s -o /dev/null -w '%{http_code}' -m 5 \\
     -H 'Connection: Upgrade' -H 'Upgrade: websocket' \\
     -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' -H 'Sec-WebSocket-Version: 13' \\
     http://127.0.0.1:$PORT/ws" | grep -q 101; then
    echo "OK: PK server is up on ws://$HOST:$PORT/ws"
else
    echo "WARNING: upgrade check did not return 101 — inspect $REMOTE_DIR/pk-server.log"
fi
