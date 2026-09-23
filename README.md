# ⚔ FitDeveloper — walk to unlock your IDE

**42AD × JetBrains hackathon — "Help the Developer"**

FitDeveloper turns crunch time into real walking breaks. An engine inside
your IDE fills a crunch meter while you work; when it reaches 100% a break
opens **automatically** — typing locks, a QR appears, your phone counts
**real steps** with its motion sensors, progress syncs live through a
built-in relay, and finishing the walk unlocks the IDE with
**"Crunch Break Verified! +100 Mana"**.

Current release: **v3.4.1**.

## The loop

```
┌────────────────── PC / IDE ──────────────────┐      ┌─────── Phone ───────┐
│ engine fills the crunch meter while you work │      │  scan the QR        │
│   ↓ 100% → break opens AUTOMATICALLY         │      │  walk.html counts   │
│   ↓ typing locks (walk-to-unlock)            │  QR  │  real steps with    │
│ relay (Java :8790-8796, http + https)        │◀────▶│  DeviceMotion       │
│   QR encodes https://<LAN-IP>:<port>/walk    │ sync │  live step feed     │
└──────────────────────────────────────────────┘      └─────────────────────┘
      done → "Crunch Break Verified! +100 Mana" → IDE unlocks
```

- **Timer modes** — *Continuous countdown* (default: thinking, reading and
  debugging count as work) or the classic *only-while-typing* mode.
- **Walk-to-unlock coding lock** — keystrokes are swallowed while a break
  is open. It is engineered to be fail-safe (guarded handler chain,
  `require-restart`, unload hook): it can never leave your IDE unable to type.
- **Honest step counting** — every step comes from the phone's real motion
  sensor (accelerometer peak detection >1.2 m/s², 300 ms refractory). There
  is no manual "+1 step" button and no "walk again" reset: a new walk only
  starts from a **new break** once the meter fills again.
- **Self-releasing breaks** — a break never deadlocks the IDE: it releases
  itself after 15 minutes with no walker, or 5 minutes if the walker goes
  silent.
- **Blocked-typing feedback (3.4.0)** — try to type during a break and the
  plugin *shows you why*: the scan QR flashes red a few times until the
  phone is scanned, then the live step counter flashes red until the goal
  is reached (when the QR is shown in the plugin).
- **Engine master switch** — disable it in Settings and nothing is watched,
  nothing fires, nothing is ever blocked. No uninstall needed.

## Two variants

| | Folder | What it is | Needs |
|---|---|---|---|
| 1 | [`without-plugin/`](without-plugin) | Standalone web app — dashboard in any browser tab, `node server.js` relay (HTTP 8787 + HTTPS 8788) | Node.js 18+ |
| 2 | [`plugin/`](plugin) | **IntelliJ Platform plugin** — engine driven by the IDE itself, native status tool window + QR, embedded Java relay (8790-8796), IDE balloons | IntelliJ IDEA / Android Studio 2024.2+ (build via Gradle) |

Both serve the same front-end (`index.html`, `walk.html`) and the same JSON
API — pick whichever fits the moment.

## Quick start — plugin (the full experience)

```bash
cd plugin
gradle buildPlugin      # -> build/distributions/fitdeveloper-plugin-<v>.zip
```

Then `Settings → Plugins → ⚙ → Install Plugin from Disk…`, restart, and the
**FitDeveloper** tool window appears on the right. Offline one-click build:
`_build_plugin.bat` (uses the locally installed Android Studio as the
compile target — no ~1 GB SDK download). Full guide:
[`plugin/README.md`](plugin/README.md)

## Quick start — without-plugin (any OS, 60 seconds)

```bash
cd without-plugin
node server.js          # or start_server.bat / start_server.sh
```

Open http://localhost:8787/ → the local focus countdown runs → break opens →
scan the QR with your phone (same Wi-Fi) → walk → verified.

## Configure — Settings | Tools | FitDeveloper (plugin)

**This is the ONLY place where the steps target and the break timing can be
changed** (since 3.4.0 the browser dashboard is a pure status display and
carries no config buttons):

| Setting | What it does | Default |
|---|---|---|
| Enable engine | master switch — countdown + automatic walk breaks | on |
| Timer mode | continuous countdown, or only while typing | continuous |
| Break scan screen | where the phone scan surface appears: **Both** (QR in the plugin AND dashboard in the browser), **Plugin only** (never opens a browser), **Browser only** (no QR in the IDE) | both |
| Minutes until the walk break | ramp to 100% crunch, fractional allowed (1–120) | 30 |
| Steps required to verify the break | the QR walk target (10–5000) | 200 |

Every value applies the moment **Apply** is pressed — no IDE restart. The
30-minute default follows the research-backed cadence of a ~5-minute light
walk for every 30 minutes of sitting (Columbia University, 2023; Harvard
Health); ~200 steps ≈ that walk.

## The break, step by step

1. The countdown (or typing meter) reaches 100% → a break opens **by
   itself**: a balloon fires, typing locks, and the scan surface appears
   according to the **Break scan screen** setting.
2. Scan the QR with the phone (or use the browser dashboard that opened).
3. Tap **Enable Motion Sensor** on the phone page — real steps count live
   and sync every 0.8 s; the desktop shows the live counter and progress.
4. Reaching the target verifies the break: **+100 Mana**, confetti, the IDE
   unlocks. Skipped walks degrade gracefully (15/5-minute auto-release).

iOS note: motion sensors only fire on secure pages, so the QR encodes the
plugin's **https** port (8791, self-signed cert bundled) — accept the
certificate warning once. Android Chrome works over either port.

## Relay, ports & firewall

- Plugin relay: HTTP **8790-8795** (first free port) + HTTPS **8791-8796**;
  health at `/api/health` (`{"version":"3.4.1",...}`), engine state at
  `/api/ide-activity`.
- The phone must be on the same LAN; `firewall_fix_plugin.bat` (admin)
  opens the range if Windows blocks it.
- The dashboard page gets a tiny injected `ide-bridge.js` that mirrors the
  engine into the page (live countdown strip, badge, auto-adopt of the
  forced session).

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

## Recent releases

- **3.4.1** — the tool-window progress bar text is readable again: the
  theme's blue-on-gray "0 / 200 steps" caption is replaced by a bold
  high-contrast ink label on a clear orange fill (light and dark themes).
- **3.4.0** — bright high-contrast step counter; steps target & break
  timing are settings-only (the browser dashboard lost its preset buttons
  and is a pure status display); blocked-typing visual feedback (QR / step
  counter flashes red a few times); fixed the web dashboard's live engine
  strip (renaming leftover).
- **3.3.1** — honest walker page: fake "+1 step" and "Walk Again" buttons
  removed; dashboard badge shows the real relay version.
- **3.3.0** — **Break scan screen** setting: Both / Plugin only / Browser
  only; balloons adapt to the chosen mode.
- **3.2.0** — full rebrand to FitDeveloper (folders, code, web, scripts,
  keystore re-key).
- **3.1.0** — QR appears in the tool window only when the break is open;
  emoji-free walker page for phones.
- **3.0.0** — status-panel tool window, in-IDE pure-Java QR, research-backed
  30-minute default ramp.
- **2.6.0 → 2.9.0** — dual timer modes, automatic walk-to-unlock blocking,
  minutes-based ramp, productized dashboard.

## Roadmap

- [x] v1.x — relay + dashboard + walker, HTTPS for iOS, stats & streaks
- [x] v2.x — IntelliJ plugin, real engine, automatic walk-to-unlock breaks
- [x] v3.x — status-panel UX, rebrand, scan-screen choice, honest walker,
      settings-only config, blocked-typing feedback
- [ ] IDEA marketplace polish, per-project crunch profiles, team leaderboards

## License

MIT — see [LICENSE](LICENSE).
