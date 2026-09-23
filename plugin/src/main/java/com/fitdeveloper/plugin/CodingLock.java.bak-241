package com.fitdeveloper.plugin;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * The "walk to unlock" teeth of the plugin: while a break is open, typed
 * characters are swallowed — the developer literally cannot keep coding.
 *
 * On each blocked keystroke:
 *  - a balloon reminds them to walk (throttled to one per 15 s)
 *  - the break screen is re-opened in the browser (throttled to one per
 *    60 s, so ignoring the break is not an option, but tab spam is bounded)
 *
 * When no break is open, every keystroke is delegated to the original
 * handler untouched. Installed once from {@link FitDeveloperStartup}.
 */
public final class CodingLock {

    private static volatile boolean installed;
    private static volatile long lastBalloon;
    private static volatile long lastBrowser;
    private static TypedActionHandler original;

    private CodingLock() {
    }

    static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            TypedAction typedAction = TypedAction.getInstance();
            original = typedAction.getRawHandler();
            typedAction.setupHandler(new TypedActionHandler() {
                @Override
                public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
                    try {
                        if (FitDeveloperEngine.breakActive()) {
                            attention();
                            return; // swallow the keystroke — walk first, then code
                        }
                    } catch (Throwable ignored) {
                        // never let the guard break normal typing
                    }
                    original.execute(editor, charTyped, dataContext);
                }
            });
            installed = true;
            System.out.println("[FitDeveloper] coding lock armed — typing is blocked while a break is open");
        } catch (Throwable t) {
            System.out.println("[FitDeveloper] coding lock install failed (typing stays enabled): " + t);
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
}
