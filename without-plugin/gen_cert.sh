#!/bin/bash
# ============================================================
#  FitDeveloper cert generator (macOS / Linux / Git Bash)
#  Regenerates the self-signed cert used for HTTPS :8788.
#  iOS motion sensors need HTTPS; Android walkers work over
#  plain HTTP without it. Uses the openssl that ships with
#  macOS (and Git for Windows, if you run this in Git Bash).
# ============================================================
cd "$(dirname "$0")"

if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl not found. On Windows: install Git for Windows and use Git Bash."
  exit 1
fi

openssl req -x509 -newkey rsa:2048 -sha256 -days 365 -nodes \
  -keyout key.pem -out cert.pem \
  -subj "/CN=FitDeveloper"

echo ""
echo "Done: cert.pem + key.pem generated (365 days, self-signed)."
echo "Restart the server:  bash start_server.sh"
