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
| Dashboard | regular browser tab | **JCEF tool window inside the IDE** (right side) |
| Crunch detection | input inside the browser tab only | **REAL editor keystrokes** from the IDE (every file you type in) |
| Break trigger | QR in browser overlay | QR in the tool window **+ IDE balloon + auto-focus of the tool window** |
| Forced breaks | not automatic — someone must watch the page | **fully automatic: the engine inside the IDE forces the break** (balloon + QR screen opens in your browser by itself) |
| Relay server | `node server.js` (ports 8787/8788) | **embedded Java relay** (port 8790, auto-fallback up to 8795) |
| Needs Node.js | yes | **no** |
| Walk enforcement | honor-system: nobody checks if you keep coding | **typing is BLOCKED while a break is open** (try to type -> the break screen re-opens in your browser) |
| Break countdown | — | **live countdown in the dashboard AND inside the IDE tool window** — ticks 90 → 89 → 88… seconds until the forced break, cyan → amber → pulsing red as it nears zero (idle shows "cooling down", break shows "walk to unlock" + step progress, engine off shows "paused") |
| Phone sensor (iOS) | needs the https:// link | **built-in HTTPS port 8791** — the QR encodes it directly |

## Configure (Settings | Tools | FitDeveloper)

- **Enable/disable the forced-break engine** — one checkbox, no uninstall
  needed to stop it.
- **Sustained-typing seconds before the IDE forces a break** — free numeric
  field (5–3600 s). Defaults to 90.
- **Steps required to verify the break** — free numeric field (10–5000).
  Defaults to 200.

Every value is a plain editable number: type it, press **Apply**, and the
engine picks it up on the next tick — no IDE restart. Small values make the
cycle fast for a quick showing; larger values model a realistic healthy-work
rhythm. The tool window also has **"Force break now"** (instant break screen)
and a live engine on/off checkbox. While a break is open the coding lock
swallows typed characters — finish the walk and typing unlocks by itself.

When a break starts, the tool window shows the QR; the phone walks; steps sync
live; when the target is reached the IDE gets the
**"Crunch Break Verified! +100 Mana"** balloon. Demo Mode still works with no
phone (toggle in the dashboard header).

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
   `plugin/build/distributions/fitdeveloper-plugin-2.4.1.zip`.

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
plugin\_build_plugin.bat          # -> plugin\build\distributions\fitdeveloper-plugin-2.4.1.zip
```

The zip name follows `rootProject.name` (`fitdeveloper-plugin-2.4.1.zip`),
not the plugin id — both install fine.

## Install into your real IDE

1. `Settings/Preferences → Plugins → ⚙ (gear icon) → Install Plugin from Disk…`
2. Pick `plugin/build/distributions/fitdeveloper-plugin-2.4.1.zip`.
3. Restart the IDE. The **FitDeveloper** tool window appears on the right
   (sidebar icon), and the relay prints
   `[FitDeveloper] relay on http://localhost:8790` to the IDE console/log.

## Phone / walk flow (same as standalone)

1. Dashboard (tool window) → Start a focus or crunch session.
2. Break triggers → QR appears → scan with your phone (same Wi-Fi).
3. Phone opens `http://<your-LAN-IP>:8790/walk?s=<id>` and counts real steps
   (DeviceMotion peak detection).
4. Progress syncs live; done → verified, Mana awarded.

Phone cannot connect? Run `firewall_fix_plugin.bat` once (admin) — it opens
TCP 8790-8795.

## Notes & limits

- **iOS**: Safari requires HTTPS for motion sensors. Since v2.3.0 the plugin
  relay serves HTTPS on port 8791 (self-signed cert bundled) and the QR
  encodes the https:// URL — scan, accept the certificate warning once, and
  real steps work on iOS from the plugin too. Android Chrome also fine.
- The dashboard page is served with a tiny injected `ide-bridge.js`
  (`IDE plugin mode` badge in the corner). It polls `/api/ide-activity` and
  feeds real editor keystrokes into the crunch meter — delete it from
  `FitDeveloperServer.IDE_BRIDGE_JS` if you want the plain page.
- Session/progress/log/config APIs are byte-compatible with the standalone
  `server.js`, so `test_e2e.js` from the standalone folder can be pointed at
  the plugin relay by editing only the base URL.

## Files

```
plugin/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── firewall_fix_plugin.bat            one-click firewall rule (8790-8795)
├── src/main/resources/META-INF/plugin.xml
├── src/main/resources/web/            SAME front-end as without-plugin/public
└── src/main/java/com/fitdeveloper/plugin/
    ├── FitDeveloperServer.java          embedded relay (Java port of server.js)
    ├── EditorActivityListener.java     real IDE keystroke counter
    ├── FitDeveloperStartup.java         boots relay + listener at IDE start
    ├── FitDeveloperToolWindowFactory.java  JCEF dashboard tool window
    └── BreakNotifier.java              IDE balloons + tool-window auto-focus
```
