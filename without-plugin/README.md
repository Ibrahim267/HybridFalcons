# ⚔ CrunchGuard

**Enforce physical movement breaks for game developers & 3D artists.**
Desktop IDE view tracks your crunch → triggers a break → shows a QR → you walk N steps with your phone → the IDE unlocks with **+100 Mana**. Built for the 42AD × JetBrains Hackathon — *Help the Developer*.

`v1.2 — demo-day release`: today stats + recent-breaks history, post-break stats card (steps + verify time), level-up toasts, sound toggle, Space/Esc shortcuts, session-expired handling on the phone, one-click firewall fix, friend-proof launcher. **Cross-platform: Windows (.bat) + macOS/Linux (.sh) launchers; LAN IP auto-detected on any machine.**

## Run on your PC (zero dependencies — only Node.js 18+)

```cmd
cd CrunchGuard
start_server.bat        :: Windows - checks Node, starts server, opens the dashboard
:: or manually:  node server.js
```

**macOS / Linux:**

```bash
cd CrunchGuard
bash start_server.sh    # checks Node, starts server (nohup), opens the dashboard
# or manually:  node server.js
```

The LAN IP is **auto-detected at runtime** (`os.networkInterfaces()` — prefers `192.168.x`, then `10.x`), so the QR code always points at the correct address of whichever machine runs the server. Nothing to configure.

- **Desktop view:** http://localhost:8787/
- **Mobile walker:** scan the QR shown when a break triggers (or `http://<LAN-IP>:8787/walk?s=<id>`)
- **HTTPS walker (iOS):** `https://<LAN-IP>:8788/walk?s=<id>` — accept the self-signed certificate warning
- Port: 8787 (change with `set PORT=9000` on Windows / `export PORT=9000` on macOS before running)

## Send to a friend (2-minute test)

1. Zip the folder (or use `CrunchGuard-v1.2.1.zip` as-is) and send it over.
2. Friend installs Node.js LTS (https://nodejs.org) if missing — both launchers detect and link it.
3. Friend runs the launcher for their OS (`start_server.bat` / `bash start_server.sh`), phone joins the **same Wi-Fi**, scan the QR, walk.
4. Full step-by-step with troubleshooting (incl. a Mac section): **FRIEND_QUICKSTART.txt** (inside the zip).

No npm install, no build step, no internet. Everything runs locally on their machine.

## Push to GitHub

`.gitignore` is included — the important part:

- **`cert.pem` / `key.pem` are gitignored on purpose.** GitHub push protection blocks pushes containing private keys. A fresh clone runs HTTP-only (Android walkers work; iOS needs HTTPS) — regenerate the cert with `gen_cert.sh` (macOS/Linux/Git Bash) or `gen_cert.bat` (Windows) and restart.
- Logs, OS junk (`.DS_Store`, `Thumbs.db`) and local-only dev helpers are ignored too.
- The zip / this folder still contains the certs, so the friend-test flow is unaffected.

Quick start for the repo:

```bash
git init && git add . && git commit -m "CrunchGuard v1.2.1 - walk to unlock your IDE"
git branch -M main && git remote add origin <your-repo-url> && git push -u origin main
```

## ⚠ Windows Firewall (first run)

When Windows asks "Allow Node.js on your network?" → **Allow (Private networks)**.
If the phone cannot connect, double-click **`firewall_fix.bat`** (self-elevates and adds both rules):

```cmd
netsh advfirewall firewall add rule name="CrunchGuard HTTP"  dir=in action=allow protocol=TCP localport=8787
netsh advfirewall firewall add rule name="CrunchGuard HTTPS" dir=in action=allow protocol=TCP localport=8788
```

Phone and laptop must be on the **same Wi-Fi**. If venue Wi-Fi blocks devices from seeing each other, use Windows **Mobile Hotspot** (Settings → Network) and reconnect both to it.

## Stage demo script (60 seconds)

1. Desktop view open, timer running. Say: *"Crunch is killing devs — CrunchGuard makes the body a dependency you can't skip."*
2. Click **⚡ Trigger Break Now** → QR overlay appears.
3. Scan with your phone → tap **Enable Motion Sensor** (iOS asks permission — tap Allow).
4. Either walk in place / shake gently, **or** flip **DEMO MODE: INSTANT WALK** on the phone (18 steps/sec).
5. Desktop turns green → **CRUNCH BREAK VERIFIED! +100 Mana** with confetti.
   *Backup:* the **Demo Mode** toggle on the desktop runs a simulated walker with **zero phone** — if Wi-Fi dies, the demo still lands.
6. Point at Hero Stats: level, mana bar, streak, **Today strip + recent verified breaks** — *"health as an RPG progression system."*

Keyboard for the stage: **Space** = start/pause, **Esc** = cancel break.

## Architecture

```
Laptop (Windows)                        Phone
┌────────────────────────┐   HTTP    ┌──────────────────┐
│ node server.js  :8787  │◄──────────┤ walk.html         │
│  • static files        │  POST     │  DeviceMotion API │
│  • in-memory sessions  │  /progress│  peak detection   │
│  • QR session relay    │  every .8s│  Demo Mode        │
│  • HTTPS :8788 (iOS)   │           └──────────────────┘
└────────────────────────┘
Desktop view (index.html) polls the session every 1s → green when target met.
```

- **Zero npm dependencies** — Node built-ins only, vendored QR generator (MIT).
- Works offline (no CDN, no fonts fetched). Nothing leaves the machine.
- Sessions expire after 2h (in-memory, max 500, oldest evicted; restart clears).
- Step detection: accel magnitude minus slow gravity baseline, peak > 1.2 m/s², 300 ms refractory.

## Files

| File | Purpose |
|---|---|
| `server.js` | Relay server: static files + session/progress API (HTTP 8787 + HTTPS 8788) |
| `public/index.html` | Desktop IDE view (timer, crunch meter, QR, Mana/level, history, Demo Mode) |
| `public/walk.html` | Mobile walker view (progress ring, sensor debug, permissions, sync) |
| `public/qrcode.min.js` | Vendored QR generator (MIT, kazuhikoarase) |
| `public/icon.svg`, `public/manifest.json` | PWA icon + manifest |
| `cert.pem` / `key.pem` | Self-signed local cert (30 days) — iOS motion sensors need HTTPS (gitignored; regen via `gen_cert.*`) |
| `gen_cert.sh` / `gen_cert.bat` | Regenerate the self-signed cert after a fresh clone |
| `start_server.bat` | Windows: Node check → detached start → opens dashboard |
| `start_server.sh` | macOS/Linux: Node check → nohup start → opens dashboard |
| `restart_server.bat` | Restarts only CrunchGuard on Windows (never touches other processes) |
| `firewall_fix.bat` | One-click admin firewall rules for 8787/8788 |
| `FRIEND_QUICKSTART.txt` | The 2-minute guide you send with the zip |
| `.gitignore` / `LICENSE` | Repo hygiene (private keys out) / MIT license |
| `test_e2e.js` | End-to-end API + static tests (`node test_e2e.js`) |

## Roadmap (post-hackathon)

- IntelliJ plugin: JCEF tool window embed + real editor keystroke feed + break overlay over the IDE
- D1/Redis persistence, team leaderboards, "boss fight" crunch weeks
- Apple Health / Health Connect step-source fusion
