package com.fitdeveloper.plugin;

/**
 * Tiny decoupling hub between the coding lock (which sees every swallowed
 * keystroke, on the EDT) and the tool window (which owns the Swing widgets).
 *
 * 3.4.0: while a break is open and the developer tries to type, the tool
 * window flashes the scan QR red a few times — or, once the phone is
 * connected but the step goal is not reached yet, the live step counter —
 * so the user SEES why typing is dead instead of having to guess.
 *
 * The lock never touches Swing; the factory never touches the handler
 * chain. Either side can fail without affecting the other, and a missing
 * listener (no tool window built yet) is a harmless no-op.
 */
public final class BreakFlashHub {

    private static volatile Runnable listener;

    private BreakFlashHub() {
    }

    /** Registered by the tool window factory; replaced if a new window opens. */
    public static void setListener(Runnable r) {
        listener = r;
    }

    /**
     * Fired by {@link CodingLock} when a burst of keystrokes is swallowed.
     * Never throws — visual feedback must never be able to break typing.
     */
    public static void pulse() {
        Runnable l = listener;
        if (l != null) {
            try {
                l.run();
            } catch (Throwable ignored) {
            }
        }
    }
}
