package com.fitdeveloper.plugin;

import java.awt.Desktop;
import java.net.URI;

/**
 * The crunch-meter engine that lives inside the IDE — now non-intrusive by
 * default.
 *
 * Runs a 1-second tick forever (daemon thread, started with the relay).
 * The meter is driven by the TIMER MODE chosen in Settings | Tools |
 * FitDeveloper (new in 2.6.0):
 *  - "continuous" (the DEFAULT) — pure countdown: the crunch meter fills
 *    +100/ramp every second you spend at the machine, whether you type or
 *    not (analysis/reading/thinking counts as work). There is no decay; the
 *    countdown restarts after each break or when the engine is re-enabled.
 *  - "typing" — the classic mode: sustained typing (idle < 3 s) fills the
 *    meter +100/rampSeconds per second, idle decays it -1.6/s. Stop typing
 *    and the countdown pauses/cools down.
 *
 *  - at 100% the break fires AUTOMATICALLY (the engine's enable switch in
 *    Settings | Tools | FitDeveloper is the single master control): session
 *    created inside the relay, balloon pops, tool window activates with the
 *    QR ready in the Status panel AND the dashboard opens in the default
 *    browser. While that break is open the coding lock swallows keystrokes
 *    until the walk is verified.
 *
 * Break resolution (so nobody gets stuck locked out):
 *  - verified: walker reached the step target
 *  - abandoned: nobody ever connected a walker within 15 min
 *  - stale: a walker WAS connected but has posted nothing for 5 min
 *    (phone closed, walked away) — the IDE releases itself
 *
 * All timing/target values and the engine switch come live from
 * {@link FitDeveloperSettings} (Settings | Tools | FitDeveloper) — no restart
 * needed. Breaks are never started manually anywhere in the UI: the engine
 * alone decides when 100% is reached (product rule since 2.9.0).
 */
public final class FitDeveloperEngine {

    private static final long OPEN_SESSION_GRACE_MS = 10 * 60 * 1000L; // pause window for dashboard breaks
    private static final long NO_WALKER_GIVE_UP_MS = 15 * 60 * 1000L;  // nobody scanned the QR
    private static final long STALE_WALKER_GIVE_UP_MS = 5 * 60 * 1000L; // walker went silent mid-break

    private static volatile boolean started;
    private static volatile double crunch = 0.0;
    private static volatile String forcedSessionId;

    private FitDeveloperEngine() {
    }

    /** Called once from {@link FitDeveloperServer#ensureStarted()}. */
    static void start() {
        if (started) {
            return;
        }
        started = true;
        Thread t = new Thread(FitDeveloperEngine::loop, "fitdeveloper-engine");
        t.setDaemon(true);
        t.start();
        System.out.println("[FitDeveloper] engine running — timer mode: " + FitDeveloperSettings.timerModeName()
                + (FitDeveloperSettings.timerTypingOnly()
                        ? " (counts only while typing)"
                        : " (continuous countdown, typing not required)"));
    }

    /** Current IDE-side crunch level 0..100 (exposed on /api/ide-activity). */
    static double crunch() {
        return crunch;
    }

    /** Session the engine forced, or null while idle (consumed by ide-bridge). */
    static String forcedSessionId() {
        return forcedSessionId;
    }

    /**
     * True while a break is open — engine-forced, or a fresh dashboard one.
     * The coding lock consults this (together with the engine enable switch)
     * at every keystroke.
     */
    static boolean breakActive() {
        if (forcedSessionId != null) {
            return true;
        }
        return FitDeveloperServer.openSession(OPEN_SESSION_GRACE_MS) != null;
    }

    /** Id of the open break session (forced first, else fresh dashboard one), or null. */
    static String activeBreakSessionId() {
        if (forcedSessionId != null) {
            return forcedSessionId;
        }
        FitDeveloperServer.Session s = FitDeveloperServer.openSession(OPEN_SESSION_GRACE_MS);
        return s != null ? s.id : null;
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
        // 0) respect the off switch (Settings | Tools | FitDeveloper)
        if (!FitDeveloperSettings.isEnabled()) {
            forcedSessionId = null;
            crunch = 0;
            return;
        }

        // 1) a break we forced is still open -> pause until it resolves
        if (forcedSessionId != null) {
            FitDeveloperServer.Session s = FitDeveloperServer.sessionById(forcedSessionId);
            long now = System.currentTimeMillis();
            boolean finished = s == null || s.steps >= s.target;               // verified (or evicted)
            boolean abandoned = s != null && !s.walkerConnected
                    && now - s.createdAt > NO_WALKER_GIVE_UP_MS;               // nobody scanned
            boolean stale = s != null && s.walkerConnected                     // walker went silent
                    && now - Math.max(s.updatedAt, s.walkerLastSeen) > STALE_WALKER_GIVE_UP_MS;
            if (finished || abandoned || stale) {
                if (abandoned) {
                    System.out.println("[FitDeveloper] forced break " + forcedSessionId
                            + " abandoned — no walker connected after 15 min");
                }
                if (stale) {
                    System.out.println("[FitDeveloper] forced break " + forcedSessionId
                            + " released — walker silent for 5 min");
                }
                forcedSessionId = null;
                crunch = 0;
            }
            return;
        }

        // 2) any fresh open session (dashboard-triggered) -> it IS the break
        if (FitDeveloperServer.openSession(OPEN_SESSION_GRACE_MS) != null) {
            return;
        }

        // 3) timer-mode math (2.6.0): continuous counts every second at the
        //    machine; typing-only keeps the dashboard's keystroke math.
        double rate = 100.0 / Math.max(5, FitDeveloperSettings.rampSeconds());
        if (FitDeveloperSettings.timerTypingOnly()) {
            long ago = EditorActivityListener.lastActivityAgoSec();
            if (ago >= 0 && ago < 3) {
                crunch = Math.min(100, crunch + rate);
            } else {
                crunch = Math.max(0, crunch - 1.6);
            }
        } else {
            // continuous: thinking, reading and analyzing all count — the
            // countdown only pauses while a break is open (handled above)
            crunch = Math.min(100, crunch + rate);
        }

        // 4) crunch full -> the break fires AUTOMATICALLY (the engine is on,
        //    that is the whole point of the countdown): the QR screen opens
        //    and typing is locked until the walk is verified or the break
        //    auto-releases (15 min no walker / 5 min silent walker).
        if (crunch >= 100) {
            forceBreak();
        }
    }

    private static void forceBreak() {
        crunch = 0;
        int target = FitDeveloperSettings.targetSteps();
        String id = FitDeveloperServer.createForcedSession(target);
        if (id == null) {
            return; // raced with a dashboard session — it will pause us next tick
        }
        forcedSessionId = id;
        System.out.println("[FitDeveloper] auto break " + id
                + " — target " + target + " steps (typing locked until the walk is verified)");
        BreakNotifier.breakStarted(id, target);
        openBreakScreen(id);
    }

    /** Opens the dashboard at /?s=<id> — the page adopts the session and shows the QR. */
    static void openBreakScreen(String id) {
        try {
            Desktop.getDesktop().browse(new URI(FitDeveloperServer.baseUrl() + "/?s=" + id));
        } catch (Throwable ignored) {
        }
    }
}
