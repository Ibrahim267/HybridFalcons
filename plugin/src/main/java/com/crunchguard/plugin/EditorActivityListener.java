package com.crunchguard.plugin;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts REAL keystroke activity inside the IDE editor(s).
 * Registered once at startup on {@link com.intellij.openapi.editor.EditorFactory}'s
 * event multicaster, so every typed change in every open project is counted.
 *
 * The embedded relay exposes it via GET /api/ide-activity and the injected
 * ide-bridge.js uses it to drive the dashboard's crunch meter — this is the
 * key difference from the standalone (without-plugin) version, which can only
 * watch input inside its own browser tab.
 */
public final class EditorActivityListener implements DocumentListener {

    private static final AtomicLong KEYSTROKES = new AtomicLong();
    private static volatile long LAST_ACTIVITY = 0L;

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        KEYSTROKES.incrementAndGet();
        LAST_ACTIVITY = System.currentTimeMillis();
    }

    /** Total document changes since IDE start (proxy for keystrokes). */
    public static long keystrokes() {
        return KEYSTROKES.get();
    }

    /** Seconds since the last editor change; -1 if none yet. */
    public static long lastActivityAgoSec() {
        long last = LAST_ACTIVITY;
        return last == 0L ? -1L : (System.currentTimeMillis() - last) / 1000L;
    }
}
