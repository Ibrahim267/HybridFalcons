# CrunchGuard — IntelliJ Plugin variant

**Walk to unlock your IDE.** This folder is the *with-plugin* version of
CrunchGuard: the whole app lives inside IntelliJ as a plugin. No Node.js,
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
| Relay server | `node server.js` (ports 8787/8788) | **embedded Java relay** (port 8790, auto-fallback up to 8795) |
| Needs Node.js | yes | **no** |

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
3. To try it: open the Gradle panel → `crunchguard-plugin → intellij → runIde`.
   A sandbox IDE starts with the plugin installed.
4. To package it: Gradle panel → `intellij → buildPlugin`.
   The installable zip appears at
   `plugin/build/distributions/CrunchGuard-2.0.0.zip`.

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
plugin\_build_plugin.bat          # -> plugin\build\distributions\crunchguard-plugin-2.0.0.zip
```

The zip name follows `rootProject.name` (`crunchguard-plugin-2.0.0.zip`),
not the plugin id — both install fine.

## Install into your real IDE

1. `Settings/Preferences → Plugins → ⚙ (gear icon) → Install Plugin from Disk…`
2. Pick `plugin/build/distributions/CrunchGuard-2.0.0.zip`.
3. Restart the IDE. The **CrunchGuard** tool window appears on the right
   (sidebar icon), and the relay prints
   `[CrunchGuard] relay on http://localhost:8790` to the IDE console/log.

## Phone / walk flow (same as standalone)

1. Dashboard (tool window) → Start a focus or crunch session.
2. Break triggers → QR appears → scan with your phone (same Wi-Fi).
3. Phone opens `http://<your-LAN-IP>:8790/walk?s=<id>` and counts real steps
   (DeviceMotion peak detection).
4. Progress syncs live; done → verified, Mana awarded.

Phone cannot connect? Run `firewall_fix_plugin.bat` once (admin) — it opens
TCP 8790-8795.

## Notes & limits

- **iOS**: Safari requires HTTPS for motion sensors; the plugin relay serves
  plain HTTP, so real-step counting on iOS needs the standalone variant
  (`../without-plugin`, HTTPS 8788). Android Chrome works fine over HTTP.
- The dashboard page is served with a tiny injected `ide-bridge.js`
  (`IDE plugin mode` badge in the corner). It polls `/api/ide-activity` and
  feeds real editor keystrokes into the crunch meter — delete it from
  `CrunchGuardServer.IDE_BRIDGE_JS` if you want the plain page.
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
└── src/main/java/com/crunchguard/plugin/
    ├── CrunchGuardServer.java          embedded relay (Java port of server.js)
    ├── EditorActivityListener.java     real IDE keystroke counter
    ├── CrunchGuardStartup.java         boots relay + listener at IDE start
    ├── CrunchGuardToolWindowFactory.java  JCEF dashboard tool window
    └── BreakNotifier.java              IDE balloons + tool-window auto-focus
```
