# FitDeveloper — IntelliJ Plugin variant

**Walk to unlock your IDE.** This folder is the *with-plugin* version of
FitDeveloper: the whole app lives inside IntelliJ as a plugin. No Node.js,
no separate server window — the relay is embedded in the IDE itself.

> The standalone version (browser + `node server.js`) lives in
> [`../without-plugin`](../without-plugin). Both variants use the exact same
> front-end files — this plugin embeds them as a JCEF tool window.

## What the plugin adds over the standalone version

| | without-plugin (browser tab) | **plugin (this folder)** |
|---|---|---|
| Dashboard | regular browser tab | **JCEF tool window inside the IDE** (right side) + native status panel |
| Crunch engine | the page's own focus timer | **IDE engine** — continuous countdown (default) or only-while-typing, with a live seconds-accurate break countdown |
| Break trigger | QR in browser overlay | QR in the tool window **and/or** the browser dashboard — per the **Break scan screen** setting (Both / Plugin only / Browser only) |
| Forced breaks | not automatic — someone must watch the page | **fully automatic: at crunch 100% the engine forces the break** (balloon + scan surface per setting) |
| Relay server | `node server.js` (ports 8787/8788) | **embedded Java relay** (http 8790+, auto-fallback up to 8795, https 8791+) |
| Needs Node.js | yes | **no** |
| Walk enforcement | honor-system: nobody checks if you keep coding | **typing is BLOCKED while a break is open** — and since 3.4.0 trying to type flashes the QR (or the step counter) red so you see why |
| Phone sensor (iOS) | needs the https:// link | **built-in HTTPS port 8791** — the QR encodes it directly |

## Configure (Settings | Tools | FitDeveloper)

**Since 3.4.0 this settings page is the ONLY place where the steps target
and the break timing can be changed** — the browser dashboard carries no
config buttons anymore and is a pure live status display.

- **Enable FitDeveloper engine** — the master switch (countdown + automatic
  walk breaks). One checkbox, no uninstall needed to stop it.
- **Timer mode** — *Continuous countdown* (default: counts every second at
  the machine, typing or not) or *Only while typing* (arms on keystrokes,
  decays when idle).
- **Break scan screen** — where the phone scan surface appears when a break
  opens: **Both** (QR in the plugin AND the dashboard in the browser,
  default), **Plugin only** (no browser window ever opens), **Browser only**
  (no QR in the tool window).
- **Minutes until the walk break** — the crunch ramp in minutes (fractional,
  1–120). Default **30** — research-backed: a ~5-minute walk for every 30
  minutes of sitting (Columbia University, 2023; Harvard Health).
- **Steps required to verify the break (QR target)** — 10–5000, default 200.

Every value applies the moment **Apply** is pressed — no IDE restart. The
tool window is a pure STATUS panel (big color-escalating countdown, live
step progress while a break is open, QR when the break is open, shortcuts
to Settings and the browser dashboard) — no duplicated controls.

While a break is open the coding lock swallows typed characters; trying to
type makes the scan QR flash red a few times (phone not scanned yet), then
the live step counter flash red (scanned but the goal is not reached).
Finish the walk and typing unlocks by itself — or the break self-releases
after 15 minutes with no walker / 5 minutes of walker silence.

## Build

Requirements: **IntelliJ IDEA** (Community is fine) or Gradle + JDK 17.
The Gradle build auto-downloads the JDK if missing.

### Option A — from IntelliJ IDEA (recommended)

1. `File → Open…` and select this `plugin` folder.
2. Wait for the Gradle sync (first run downloads the IntelliJ SDK — a few
   hundred MB, one time only).
3. To try it: open the Gradle panel → `fitdeveloper-plugin → intellij → runIde`.
   A sandbox IDE starts with the plugin installed.
4. To package it: Gradle panel → `intellij → buildPlugin`.
   The installable zip appears at
   `plugin/build/distributions/fitdeveloper-plugin-3.4.0.zip`.

### Option B — from a terminal

```bash
cd plugin
gradle buildPlugin        # or: gradle runIde   (Gradle 8.x, JDK auto-provisioned)
```

### Option C — offline one-click build (works with ZERO extra installs)

`_build_plugin.bat` builds the plugin **without downloading the ~1 GB
IntelliJ SDK**: it uses the locally installed Android Studio (an IntelliJ
Platform 2024.3 IDE) as the compile target and its bundled JBR (full JDK 21)
as the build JVM, with the Gradle 8.12 distribution already cached in
`~\.gradle`. Double-click it, then watch `build_log.txt`.

```bat
plugin\_build_plugin.bat          # -> plugin\build\distributions\fitdeveloper-plugin-3.4.0.zip
```

The zip name follows `rootProject.name` (`fitdeveloper-plugin-3.4.0.zip`),
not the plugin id — both install fine.

## Install into your real IDE

1. `Settings/Preferences → Plugins → ⚙ (gear icon) → Install Plugin from Disk…`
2. Pick `plugin/build/distributions/fitdeveloper-plugin-3.4.0.zip`.
3. Restart the IDE. The **FitDeveloper** tool window appears on the right
   (sidebar icon), and the relay prints
   `[FitDeveloper] relay on http://localhost:8790` to the IDE console/log.

## Phone / walk flow (same as standalone)

1. Work until the crunch meter hits 100% — the break opens automatically.
2. Scan the QR (per the Break scan screen setting: in the tool window,
   in the browser dashboard, or both).
3. Phone opens `https://<your-LAN-IP>:8791/walk?s=<id>` and counts real
   steps (DeviceMotion peak detection — no manual buttons; every step is
   sensor-verified).
4. Progress syncs live; done → verified, Mana awarded, IDE unlocked.

Phone cannot connect? Run `firewall_fix_plugin.bat` once (admin) — it opens
TCP 8790-8795.

## Notes & limits

- **iOS**: Safari requires HTTPS for motion sensors. Since v2.3.0 the plugin
  relay serves HTTPS on port 8791 (self-signed cert bundled) and the QR
  encodes the https:// URL — scan, accept the certificate warning once, and
  real steps work on iOS from the plugin too. Android Chrome also fine.
- The dashboard page is served with a tiny injected `ide-bridge.js`
  (`FitDeveloper - crunch N%` badge in the corner). It polls
  `/api/ide-activity` and mirrors the engine into the page (live break
  countdown strip, auto-adopt of the forced session) — delete it from
  `FitDeveloperServer.IDE_BRIDGE_JS` if you want the plain page.
- Session/progress/log/config APIs are byte-compatible with the standalone
  `server.js`, so `test_e2e.js` from the standalone folder can be pointed at
  the plugin relay by editing only the base URL.

## Files

```
plugin/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── _build_plugin.bat                  offline one-click build (local SDK)
├── firewall_fix_plugin.bat            one-click firewall rule (8790-8795)
├── src/main/resources/META-INF/plugin.xml
├── src/main/resources/web/            SAME front-end as without-plugin/public
└── src/main/java/com/fitdeveloper/plugin/
    ├── FitDeveloperServer.java          embedded relay (Java port of server.js)
    ├── FitDeveloperEngine.java          crunch engine (timer modes, forced breaks)
    ├── FitDeveloperSettings.java        Settings | Tools | FitDeveloper page
    ├── FitDeveloperToolWindowFactory.java  native status panel + in-IDE QR
    ├── CodingLock.java                  walk-to-unlock typing guard (fail-safe)
    ├── FitDeveloperDynamicHook.java     unload-safety for the handler chain
    ├── FitDeveloperStartup.java         boots relay + engine at IDE start
    ├── EditorActivityListener.java      real IDE keystroke activity
    ├── BreakFlashHub.java               lock → tool-window flash signaling
    ├── QrCode.java                      pure-Java QR renderer
    └── BreakNotifier.java              IDE balloons + tool-window auto-focus
```
