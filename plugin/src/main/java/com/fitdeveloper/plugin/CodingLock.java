package com.fitdeveloper.plugin;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The "walk to unlock" teeth — armed AUTOMATICALLY whenever the engine is
 * enabled (the engine's enable switch in Settings | Tools | FitDeveloper is
 * the single master control), and engineered so it can never break typing
 * in the IDE.
 *
 * WHY THE OLD VERSION BROKE TYPING (the "can't type until IDE restart" bug):
 * TypedAction.setupHandler() is a GLOBAL monkey-patch of the IDE's typing
 * pipeline. The platform does NOT roll it back when a plugin is dynamically
 * unloaded (uninstall/disable/update without restart, default since 2020.1).
 * The leftover handler then points at a DISPOSED plugin classloader, so every
 * keystroke threw NoClassDefFoundError and the IDE stopped accepting text
 * until it was restarted. No switch setting mattered — the chain itself
 * was dead.
 *
 * THIS VERSION IS SAFE BY CONSTRUCTION (4 layers):
 *  1. require-restart="true" (plugin.xml) — the IDE never hot-unloads this
 *     plugin, so the handler chain can never go stale in the first place.
 *  2. {@link FitDeveloperDynamicHook} — a DynamicPluginListener that removes
 *     this guard from the chain BEFORE the classes are discarded, should a
 *     dynamic unload ever happen anyway.
 *  3. The decision to swallow a keystroke is fully guarded: any failure
 *     defaults to NOT blocking. With the engine disabled this handler is a
 *     pure 2-line pass-through.
 *  4. The delegation to the original handler is fully guarded: if the chain
 *     below us is ever broken, the guard removes itself from the chain and
 *     logs loudly instead of dying on every keystroke.
 *
 * The handler is installed ONCE via {@link TypedAction#setupRawHandler},
 * capturing the previous handler from the call's RETURN VALUE (atomic — no
 * getRawHandler/setRawHandler race). Blocking is decided by a live boolean
 * read at keystroke time (engine enabled AND a break open) — it NEVER touches
 * the handler chain, so enable/disable is instant and error-free by design.
 */
public final class CodingLock {

    /** Class name of the guard handler, used to detect double-installs across classloaders. */
    static final String GUARD_CLASS_NAME = GuardHandler.class.getName();

    private static volatile long lastBalloon;
    private static volatile long lastBrowser;

    private CodingLock() {
    }

    /** Installs the guard once. Never throws; typing stays enabled on any failure. */
    static synchronized void install() {
        try {
            TypedAction typedAction = TypedAction.getInstance();
            TypedActionHandler current = typedAction.getRawHandler();
            if (current != null && GUARD_CLASS_NAME.equals(current.getClass().getName())) {
                System.out.println("[FitDeveloper] coding lock already installed — not double-wrapping");
                return;
            }
            // setupRawHandler RETURNS the previous handler: atomic capture.
            TypedActionHandler previous = typedAction.setupRawHandler(new GuardHandler(current));
            System.out.println("[FitDeveloper] coding lock installed (auto-block armed — keystrokes are swallowed only while a break is open)"
                    + " previous handler: " + (previous != null ? previous.getClass().getName() : "null"));
        } catch (Throwable t) {
            System.out.println("[FitDeveloper] coding lock install failed (typing stays fully enabled): " + t);
        }
    }

    /**
     * Removes the guard from the chain (plugin unload / self-repair).
     * Handles guards from foreign classloaders via reflection.
     */
    static synchronized void uninstall() {
        try {
            TypedAction typedAction = TypedAction.getInstance();
            TypedActionHandler current = typedAction.getRawHandler();
            if (current == null || !GUARD_CLASS_NAME.equals(current.getClass().getName())) {
                return; // not installed (or installed by nobody we can touch)
            }
            Object original;
            if (current instanceof GuardHandler) {
                original = ((GuardHandler) current).original;
            } else {
                // guard from an older instance of this plugin — read its field reflectively
                original = current.getClass().getField("original").get(current);
            }
            if (original == null) {
                System.out.println("[FitDeveloper] coding lock: cannot unwrap — original handler unknown");
                return;
            }
            typedAction.setupRawHandler((TypedActionHandler) original);
            System.out.println("[FitDeveloper] coding lock uninstalled — typing is untouched by this plugin again");
        } catch (Throwable t) {
            System.out.println("[FitDeveloper] coding lock uninstall failed: " + t);
        }
    }

    private static void attention() {
        long now = System.currentTimeMillis();
        if (now - lastBalloon > 15_000) {
            lastBalloon = now;
            BreakNotifier.walkReminder(remainingSteps());
        }
        if (now - lastBrowser > 60_000) {
            lastBrowser = now;
            final String id = FitDeveloperEngine.activeBreakSessionId();
            if (id != null) {
                Thread t = new Thread(() -> FitDeveloperEngine.openBreakScreen(id), "fitdeveloper-reopen");
                t.setDaemon(true);
                t.start();
            }
        }
    }

    private static int remainingSteps() {
        try {
            String id = FitDeveloperEngine.activeBreakSessionId();
            FitDeveloperServer.Session s = id != null ? FitDeveloperServer.sessionById(id) : null;
            if (s != null) {
                return Math.max(1, s.target - s.steps);
            }
        } catch (Throwable ignored) {
        }
        return FitDeveloperSettings.targetSteps();
    }

    /**
     * The guard. Two independent failure domains, both fail-safe:
     *  - the BLOCK decision: any Throwable defaults to "do not block";
     *  - the delegation: if the handler below is broken, self-remove and log.
     */
    private static final class GuardHandler implements TypedActionHandler {

        @Nullable
        final TypedActionHandler original;

        GuardHandler(@Nullable TypedActionHandler original) {
            this.original = original;
        }

        @Override
        public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
            boolean swallow = false;
            try {
                swallow = FitDeveloperSettings.isEnabled() && FitDeveloperEngine.breakActive();
            } catch (Throwable ignored) {
                // any decision failure defaults to NOT blocking — coding always wins
            }
            if (swallow) {
                try {
                    attention();
                } catch (Throwable ignored) {
                }
                return; // break open: walk first, then code
            }
            if (original == null) {
                System.out.println("[FitDeveloper] coding lock: no original handler to delegate to — restarting the IDE once would restore the default chain");
                return;
            }
            try {
                original.execute(editor, charTyped, dataContext);
            } catch (Throwable chainBroken) {
                // The handler below us is broken (stale classloader). Remove
                // OUR wrapper so at least this guard is out of the way, and
                // tell the user exactly what to do. Never rethrow: a thrown
                // error here is what silently killed typing before.
                System.out.println("[FitDeveloper] coding lock: handler chain below is broken ("
                        + chainBroken + ") — removing the FitDeveloper guard; if typing is still dead, restart the IDE (this should no longer happen: the plugin now requires an IDE restart for install/update/uninstall)");
                try {
                    CodingLock.uninstall();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
