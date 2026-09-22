package com.crunchguard.plugin;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs once per opened project: boots the embedded relay server and
 * registers the IDE-wide editor keystroke listener (exactly once, guarded).
 */
public final class CrunchGuardStartup implements StartupActivity.DumbAware {

    private static final AtomicBoolean LISTENER_REGISTERED = new AtomicBoolean(false);

    @Override
    public void runActivity(@NotNull Project project) {
        CrunchGuardServer.ensureStarted();
        if (LISTENER_REGISTERED.compareAndSet(false, true)) {
            EditorFactory.getInstance()
                    .getEventMulticaster()
                    .addDocumentListener(new EditorActivityListener());
        }
    }
}
