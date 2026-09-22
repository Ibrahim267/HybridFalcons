package com.crunchguard.plugin;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

/**
 * IDE-side reactions to the break lifecycle:
 *  - break started  -> balloon + auto-open the CrunchGuard tool window
 *  - break verified -> "Crunch Break Verified! +100 Mana" balloon
 *
 * Called from relay (background) threads; everything UI-related is wrapped
 * in invokeLater and hardened with try/catch so a UI hiccup can never
 * break the relay.
 */
public final class BreakNotifier {

    public static final String TOOL_WINDOW_ID = "CrunchGuard";

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
                notify("Crunch break triggered",
                        "Target: " + target + " steps — scan the QR in the CrunchGuard tool window.",
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
                    .getNotificationGroup("CrunchGuard")
                    .createNotification(title, content, NotificationType.INFORMATION)
                    .notify(project);
        } catch (Throwable ignored) {
        }
    }
}
