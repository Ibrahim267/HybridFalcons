# ⚔ FitDeveloper — walk to unlock your IDE

**42AD × JetBrains hackathon — "Help the Developer"**

FitDeveloper forces real walking breaks during crunch time: your IDE tracks
work/crunch time, a break triggers a QR code, your phone counts actual steps
with its motion sensors, progress syncs live, and completing the walk unlocks
the IDE with **"Crunch Break Verified! +100 Mana"**.

This repository ships **two variants**:

| | Folder | What it is | Needs |
|---|---|---|---|
| 1 | [`without-plugin/`](without-plugin) | Standalone web app — dashboard in any browser tab, `node server.js` relay (HTTP 8787 + HTTPS 8788) | Node.js 18+ |
| 2 | [`plugin/`](plugin) | **IntelliJ Platform plugin** — the same dashboard embedded as a JCEF tool window, crunch meter driven by **real editor keystrokes**, embedded Java relay (port 8790), IDE balloons | IntelliJ IDEA (build via Gradle) |

Both variants share the exact same front-end (`index.html`, `walk.html`,
qrcode lib, icon) and the same JSON API — pick whichever fits the demo.

## Quick start — without-plugin (any OS, 60 seconds)

```bash
cd without-plugin
node server.js          # or start_server.bat (Windows) / bash start_server.sh (macOS/Linux)
```

Open http://localhost:8787/ → press **Space** or *Start* → break triggers →
scan the QR with your phone (same Wi-Fi) → walk → IDE unlocked.

Full guide: [`without-plugin/README.md`](without-plugin/README.md) ·
friend-ready 2-minute setup: [`without-plugin/FRIEND_QUICKSTART.txt`](without-plugin/FRIEND_QUICKSTART.txt)

## Quick start — plugin

```bash
cd plugin
gradle runIde           # sandbox IDE with the plugin loaded
gradle buildPlugin      # -> build/distributions/fitdeveloper-plugin-2.4.1.zip
```

Then `Settings → Plugins → ⚙ → Install Plugin from Disk…`. Full guide:
[`plugin/README.md`](plugin/README.md)

## How it works

```
┌─────────────── PC / IDE ───────────────┐      ┌─────── Phone ───────┐
│ dashboard (browser tab or JCEF window) │      │  scan QR            │
│   ↓ work/crunch timer (plugin: real    │      │  walk.html counts   │
│     editor keystrokes via listener)    │      │  real steps with    │
│   ↓ break triggers                     │  QR  │  DeviceMotion       │
│ relay server (Node :8787 or Java :8790)│◀────▶│  POST /progress     │
│   QR encodes http://<LAN-IP>:<port>/…  │ sync │  live step feed     │
└────────────────────────────────────────┘      └─────────────────────┘
        done → "Crunch Break Verified! +100 Mana" → IDE unlocked
```

- LAN IP is auto-detected at runtime (192.168.x preferred), so the QR works on
  any machine/network with zero configuration.
- Demo Mode (dashboard header) simulates a walker — full flow with no phone.
- Step counting: accel-magnitude peak detection (>1.2 m/s², 300 ms refractory),
  monotonic sync, session TTL 2 h, 500-session cap.

## Repository layout

```
FitDeveloper/
├── without-plugin/     standalone web app (Node relay + same public/ front-end)
│   ├── server.js  package.json  test_e2e.js  check.js
│   ├── start_server.bat / .sh  restart_server.bat  firewall_fix.bat
│   ├── gen_cert.bat / .sh   (regenerate self-signed cert for iOS)
│   ├── README.md  FRIEND_QUICKSTART.txt  LICENSE
│   └── public/ (index.html, walk.html, qrcode.min.js, icon.svg, manifest.json)
├── plugin/             IntelliJ plugin variant (see plugin/README.md)
├── .gitignore
├── LICENSE
└── README.md
```

## Roadmap

- [x] v1.0 — relay + dashboard + walker (browser tab)
- [x] v1.1 — HTTPS for iOS motion sensors, sensor debug panel
- [x] v1.2 — demo-day polish: stats, history, streaks, shortcuts, friend-proof zip
- [x] v1.2.1 — macOS/Linux launchers, auto LAN-IP everywhere
- [x] v2.0 — IntelliJ plugin: JCEF tool window, real keystroke activity, IDE balloons
- [ ] IDEA marketplace polish, per-project crunch profiles, team leaderboards

## License

MIT — see [LICENSE](LICENSE).
