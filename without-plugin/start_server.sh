#!/bin/bash
# ============================================================
#  CrunchGuard launcher for macOS / Linux
#  Usage:  bash start_server.sh
#  Checks Node, starts the server detached (nohup, logs to
#  server.log), then opens the dashboard in the browser.
# ============================================================
cd "$(dirname "$0")"

if ! command -v node >/dev/null 2>&1; then
  echo ""
  echo "  [CrunchGuard] Node.js is NOT installed."
  echo "  Install the LTS from https://nodejs.org  (or:  brew install node),"
  echo "  then run this script again:  bash start_server.sh"
  echo ""
  exit 1
fi

# already running? restart cleanly
if [ -n "$(lsof -ti :8787 2>/dev/null)" ]; then
  kill $(lsof -ti :8787) $(lsof -ti :8788 2>/dev/null) 2>/dev/null
  sleep 1
fi

nohup node server.js > server.log 2>&1 &
sleep 2
open http://localhost:8787/ 2>/dev/null || xdg-open http://localhost:8787/ 2>/dev/null

echo ""
echo "  ============================================================"
echo "    CrunchGuard is starting - dashboard opening in browser"
echo "    If the page does not load, wait 2s and refresh."
echo ""
echo "    PHONE SETUP: same Wi-Fi as this Mac, then scan the QR"
echo "    shown when a break triggers."
echo "    If macOS asks about incoming network connections -> Allow."
echo ""
echo "    Stop the server:   kill \$(lsof -ti :8787)"
echo "    Logs:              server.log"
echo "  ============================================================"
echo ""
