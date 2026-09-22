package com.crunchguard.plugin;

import java.awt.Desktop;
import java.net.URI;

/**
 * THE point of the plugin: a FORCED break engine that lives inside the IDE.
 *
 * Runs a 1-second tick forever (daemon thread, started with the relay):
 *  - samples REAL editor keystroke activity ({@link EditorActivityListener})
 *  - same math as the dashboard page: sustained typing (idle < 3 s) fills the
 *    crunch meter +0.9/s, idle decays it -1.6/s
 *  - at 100% the IDE itself forces the break: the session is created inside
 *    the relay, a notification balloon pops, the CrunchGuard tool window
 *    activates AND the break screen with the QR opens in the default browser.
 *
 * The developer never has to open anything manually — that is the product.
 *
 * Pause rules (so the engine never double-triggers):
 *  - while a break forced by this engine is open (until verified, or given up
 *    after 15 min with no walker connected)
 *  - while any other open session (< 10 min old, not done) exists — e.g. one
 *    triggered from an open dashboard; that session IS the break.
 *
 * All timing/target values and the on/off switch come live from
 * {@link CrunchGuardSettings} (Settings | Tools | CrunchGuard) — no restart
 * needed. The tool window's "Force break now" button calls
 * {@link #forceBreakNow()} for an instant break screen.
 */
public final class CrunchGuardEngine {

    private static final long OPEN_SESSION_GRACE_MS = 10 * 60 * 1000L; // pause window for dashboard breaks
    private static final long NO_WALKER_GIVE_UP_MS = 15 * 60 * 1000L;  // nobody scanned the QR

    private static volatile boolean started;
    private static volatile double crunch = 0.0;
    private static volatile String forcedSessionId;

    private CrunchGuardEngine() {
    }

    /** Called once from {@link CrunchGuardServer#ensureStarted()}. */
    static void start() {
        if (started) {
            return;
        }
        started = true;
        Thread t = new Thread(CrunchGuardEngine::loop, "crunchguard-engine");
        t.setDaemon(true);
        t.start();
        System.out.println("[CrunchGuard] forced-break engine running — watching real editor keystrokes");
    }

    /** Current IDE-side crunch level 0..100 (exposed on /api/ide-activity). */
    static double crunch() {
        return crunch;
    }

    /** Session the engine forced, or null while idle (consumed by ide-bridge). */
    static String forcedSessionId() {
        return forcedSessionId;
    }

    private static void loop() {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            try {
                tick();
            } catch (Throwable ignored) {
                // the engine must never die — the IDE keeps working either way
            }
        }
    }

    private static void tick() {
        // 0) respect the off switch (Settings | Tools | CrunchGuard)
        if (!CrunchGuardSettings.isEnabled()) {
            forcedSessionId = null;
            crunch = 0;
            return;
        }

        // 1) a break we forced is still open -> pause until it resolves
        if (forcedSessionId != null) {
            CrunchGuardServer.Session s = CrunchGuardServer.sessionById(forcedSessionId);
            long now = System.currentTimeMillis();
            boolean finished = s == null || s.steps >= s.target;               // verified (or evicted)
            boolean abandoned = s != null && !s.walkerConnected
                    && now - s.createdAt > NO_WALKER_GIVE_UP_MS;               // nobody scanned
            if (finished || abandoned) {
                if (abandoned) {
                    System.out.println("[CrunchGuard] forced break " + forcedSessionId
                            + " abandoned — no walker connected after 15 min");
                }
                forcedSessionId = null;
                crunch = 0;
            }
            return;
        }

        // 2) any fresh open session (dashboard-triggered) -> it IS the break
        if (CrunchGuardServer.openSession(OPEN_SESSION_GRACE_MS) != null) {
            return;
        }

        // 3) same math as the dashboard page tick, ramp time from settings
        long ago = EditorActivityListener.lastActivityAgoSec();
        if (ago >= 0 && ago < 3) {
            crunch = Math.min(100, crunch + 100.0 / Math.max(5, CrunchGuardSettings.rampSeconds()));
        } else {
            crunch = Math.max(0, crunch - 1.6);
        }

        // 4) sustained crunch -> the IDE forces the break
        if (crunch >= 100) {
            forceBreak();
        }
    }

    private static void forceBreak() {
        crunch = 0;
        int target = CrunchGuardSettings.targetSteps();
        String id = CrunchGuardServer.createForcedSession(target);
        if (id == null) {
            return; // raced with a dashboard session — it will pause us next tick
        }
        forcedSessionId = id;
        System.out.println("[CrunchGuard] FORCED break " + id
                + " — target " + target + " steps (sustained typing detected)");
        BreakNotifier.breakStarted(id, target);
        openBreakScreen(id);
    }

    /** Opens the dashboard at /?s=<id> — the page adopts the session and shows the QR. */
    static void openBreakScreen(String id) {
        try {
            Desktop.getDesktop().browse(new URI(CrunchGuardServer.baseUrl() + "/?s=" + id));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Manual entry point (tool window button): forces a break immediately.
     * If a break is already open, just re-opens its QR screen.
     */
    static void forceBreakNow() {
        try {
            CrunchGuardServer.Session open = CrunchGuardServer.openSession(OPEN_SESSION_GRACE_MS);
            if (open != null) {
                openBreakScreen(open.id);
                return;
            }
            forceBreak();
        } catch (Throwable ignored) {
        }
    }
}
