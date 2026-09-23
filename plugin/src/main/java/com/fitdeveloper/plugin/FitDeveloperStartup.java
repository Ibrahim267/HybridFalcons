package com.fitdeveloper.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs once per opened project: boots the embedded relay server, registers
 * the IDE-wide editor keystroke listener (exactly once, guarded) and arms
 * the coding lock (typing is blocked while a break is open). Also pops the
 * FitDeveloper tool window open so the engine and its countdown are visible
 * immediately after the project loads.
 */
public final class FitDeveloperStartup implements StartupActivity.DumbAware {

    private static final AtomicBoolean LISTENER_REGISTERED = new AtomicBoolean(false);

    @Override
    public void runActivity(@NotNull Project project) {
        FitDeveloperServer.ensureStarted();
        if (LISTENER_REGISTERED.compareAndSet(false, true)) {
            EditorFactory.getInstance()
                    .getEventMulticaster()
                    .addDocumentListener(new EditorActivityListener());
        }
        CodingLock.install();
        // demo convenience: show the tool window (without stealing focus away
        // from an already visible instance) right after the project opens
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow("FitDeveloper");
            if (tw != null && tw.isAvailable() && !tw.isVisible()) {
                tw.activate(null);
            }
        });
    }
}
