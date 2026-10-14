#!/usr/bin/env bash
# Throwaway auth verification for the deployed jar. Starts an isolated
# instance on 127.0.0.1:18092 with an in-memory DB and a dummy password,
# exercises the full login/session/CSRF/logout chain, then shuts down.
set -euo pipefail
cd /root/memorygames-pk
PW='dummy-password-not-a-secret-123'
HASH=$(cd hashtool && printf '%s' "$PW" | java -cp "ext/BOOT-INF/classes:ext/BOOT-INF/lib/*" GenHash.java)
case "$HASH" in \$2*) ;; *) echo "hash format bad"; exit 1;; esac

PK_ADMIN_PASSWORD_HASH="$HASH" nohup java -Xms64m -Xmx256m \
  -Dspring.datasource.url='jdbc:h2:mem:throwaway;DB_CLOSE_DELAY=-1' \
  -jar app.jar --server.address=127.0.0.1 --server.port=18092 \
  > throwaway.log 2>&1 < /dev/null &
PID=$!
echo "throwaway pid=$PID"
cleanup() {
  kill "$PID" 2>/dev/null || true
  sleep 2
  kill -9 "$PID" 2>/dev/null || true
}
trap cleanup EXIT

for _ in $(seq 1 40); do
  curl -fsS -m 2 http://127.0.0.1:18092/actuator/health >/dev/null 2>&1 && break
  sleep 1
done
curl -fsS -m 2 http://127.0.0.1:18092/actuator/health >/dev/null || { echo "throwaway not healthy"; exit 1; }
echo "throwaway healthy"

B=http://127.0.0.1:18092/api/admin
J=/tmp/throwaway-cookies.txt
rm -f "$J"

C1=$(curl -s -o /dev/null -w '%{http_code}' -m 5 -H 'Content-Type: application/json' \
  -d '{"password":"wrong-password-value"}' "$B/auth/login")
echo "wrong password -> $C1 (want 401)"

BODY=$(curl -s -c "$J" -m 5 -H 'Content-Type: application/json' \
  -d "{\"password\":\"$PW\"}" "$B/auth/login")
CSRF=$(printf '%s' "$BODY" | python3 -c 'import sys,json;print(json.load(sys.stdin)["csrfToken"])')
echo "correct password -> 200, csrf len ${#CSRF}"

C2=$(curl -s -o /dev/null -w '%{http_code}' -m 5 -b "$J" "$B/auth/session")
echo "session with cookie -> $C2 (want 200)"

C3=$(curl -s -o /dev/null -w '%{http_code}' -m 5 -b "$J" "$B/dashboard")
echo "dashboard with cookie -> $C3 (want 200)"

C4=$(curl -s -o /dev/null -w '%{http_code}' -m 5 -b "$J" -H 'Content-Type: application/json' \
  -d '{"enabled":false,"message":"","reason":"cloud throwaway self-test"}' "$B/maintenance")
echo "mutation without csrf -> $C4 (want 403)"

C5=$(curl -s -o /dev/null -w '%{http_code}' -m 5 -b "$J" -H "X-Admin-CSRF: $CSRF" \
  -H 'Content-Type: application/json' \
  -d '{"enabled":false,"message":"","reason":"cloud throwaway self-test"}' "$B/maintenance")
echo "mutation with csrf -> $C5 (want 200)"

C6=$(curl -s -o /dev/null -w '%{http_code}' -m 5 -b "$J" -H "X-Admin-CSRF: $CSRF" \
  -X POST "$B/auth/logout")
echo "logout -> $C6 (want 200)"

C7=$(curl -s -o /dev/null -w '%{http_code}' -m 5 -b "$J" "$B/auth/session")
echo "session after logout -> $C7 (want 401)"

rm -f "$J"
if [ "$C1" = 401 ] && [ "$C2" = 200 ] && [ "$C3" = 200 ] && [ "$C4" = 403 ] && [ "$C5" = 200 ] && [ "$C6" = 200 ] && [ "$C7" = 401 ]; then
  echo "ALL PASS"
else
  echo "FAILED"
  exit 1
fi
