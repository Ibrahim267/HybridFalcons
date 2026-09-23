package com.fitdeveloper.plugin;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

/**
 * IDE-side reactions to the break lifecycle:
 *  - break started  -> balloon + auto-open the FitDeveloper tool window
 *  - break verified -> "Crunch Break Verified! +100 Mana" balloon
 *
 * Called from relay (background) threads; everything UI-related is wrapped
 * in invokeLater and hardened with try/catch so a UI hiccup can never
 * break the relay.
 */
public final class BreakNotifier {

    public static final String TOOL_WINDOW_ID = "FitDeveloper";

    private BreakNotifier() {
    }

    public static void breakStarted(final String sessionId, final int target) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Project project = firstProject();
                if (project != null && !project.isDisposed()) {
                    ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
                    if (tw != null) {
                        tw.activate(() -> { });
                    }
                }
                // 3.3.0: the scan-surface wording follows the Break scan
                // screen setting (Settings | Tools | FitDeveloper)
                boolean qrInIde = FitDeveloperSettings.showQrInToolWindow();
                boolean browserToo = FitDeveloperSettings.openBrowserOnBreak();
                String where;
                if (qrInIde && browserToo) {
                    where = "Scan the QR in the FitDeveloper tool window "
                            + "(the dashboard also opened in your browser). ";
                } else if (browserToo) {
                    where = "The walk dashboard just opened in your default "
                            + "browser \u2014 scan the QR there. ";
                } else {
                    where = "Scan the QR in the FitDeveloper tool window. ";
                }
                notify("Crunch break triggered",
                        "The crunch meter hit 100% — a walk break is open. Target: "
                                + target + " steps. " + where + "Typing is locked "
                                + "until the walk is verified.",
                        project);
            } catch (Throwable ignored) {
            }
        });
    }

    public static void completed(final String sessionId, final int steps) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                notify("Crunch Break Verified! +100 Mana",
                        steps + " steps walked — the IDE is unlocked. Go ship it.",
                        firstProject());
            } catch (Throwable ignored) {
            }
        });
    }

    /** Shown by the coding lock when the developer tries to type during an open break. */
    public static void walkReminder(final int remainingSteps) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                // 3.3.0: point at whichever scan surface the setting picked
                String where = FitDeveloperSettings.showQrInToolWindow()
                        ? "Scan the QR in the FitDeveloper tool window and keep walking."
                        : "The walk dashboard is open in your browser \u2014 keep walking.";
                notify("Walk to unlock the IDE",
                        remainingSteps + " more steps and typing works again. " + where,
                        firstProject());
            } catch (Throwable ignored) {
            }
        });
    }

    private static Project firstProject() {
        try {
            Project[] projects = ProjectManager.getInstance().getOpenProjects();
            return projects.length > 0 ? projects[0] : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void notify(String title, String content, Project project) {
        try {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("FitDeveloper")
                    .createNotification(title, content, NotificationType.INFORMATION)
                    .notify(project);
        } catch (Throwable ignored) {
        }
    }
}
