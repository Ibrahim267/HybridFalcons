package com.fitdeveloper.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBColor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Settings | Tools | FitDeveloper — the developer stays in control:
 *  - enable/disable the crunch meter engine WITHOUT uninstalling the plugin.
 *    This is the SINGLE master control (since 2.7.0): with the engine ON the
 *    countdown runs and the break fires automatically at 100% — typing is
 *    paused until the walk is verified. With the engine OFF nothing is
 *    watched, nothing fires, nothing is ever blocked.
 *  - choose the TIMER MODE:
 *      * "Continuous countdown" (the DEFAULT) — the break countdown advances
 *        every second you sit at the machine, regardless of typing. Analysis,
 *        reading, thinking and debugging all count as work; the countdown
 *        only pauses while a break is open.
 *      * "Only while typing" — the classic behavior: the countdown arms while
 *        you type (idle &lt; 3 s counts as typing) and decays when you stop.
 *  - freely choose the "minutes until break" ramp (fractional minutes
 *    allowed; default 30) and the steps target
 *  - choose the BREAK SCAN SCREEN (new in 3.3.0) — where the phone scan
 *    surface appears the moment a break opens: "Both" (the DEFAULT — the
 *    classic behavior: the QR in the tool window AND the dashboard opening
 *    in the default browser at the same time), "Plugin only" (scan the QR
 *    right inside the IDE, no browser window ever pops up) or "Browser
 *    only" (the dashboard opens in the browser, the tool window stays a
 *    pure status display with no QR)
 *
 * The 30-minute default follows the best-current evidence on breaking up
 * sedentary work: a ~5-minute light walk for every 30 minutes of sitting
 * (Columbia University Irving Medical Center, MSSE 2023; endorsed by
 * Harvard Health) — exactly the cadence FitDeveloper automates, since the
 * forced break IS a walk (~200 steps ≈ 4-5 minutes).
 *
 * Persisted in the application-level PropertiesComponent; the engine reads
 * the values live every tick, so changes apply the moment Apply is pressed.
 */
public final class FitDeveloperSettings implements Configurable {

    private static final String KEY_ENABLED = "fitdeveloper.enabled";
    /** Ramp in MINUTES since 2.8.0 (before that it was seconds — migrated below). */
    private static final String KEY_RAMP_MIN = "fitdeveloper.rampMinutes";
    /** Pre-2.8.0 key, kept only to migrate an existing user value. */
    private static final String KEY_RAMP_LEGACY_SEC = "fitdeveloper.rampSeconds";
    /** One-time marker: move the 1.5-min demo-era default to the researched 30 min. */
    private static final String KEY_RAMP_UPGRADED = "fitdeveloper.rampMinutes.upgradedTo30";
    private static final String KEY_TARGET = "fitdeveloper.targetSteps";
    private static final String KEY_TIMER_MODE = "fitdeveloper.timerMode";
    /** Where the phone scan surface appears when a break opens (3.3.0). */
    private static final String KEY_BREAK_SCREEN = "fitdeveloper.breakScreen";

    /** Research-backed default (see class javadoc): 30 min at the machine, then a walk. */
    static final double DEFAULT_RAMP_MINUTES = 30.0;

    /** Combo labels — the same two strings are used in the tool window. */
    static final String MODE_CONTINUOUS_LABEL = "Continuous countdown (default) \u2014 counts even when you are not typing";
    static final String MODE_TYPING_LABEL = "Only while typing \u2014 countdown pauses when you stop";

    /** Break scan screen labels — one per mode of {@link #breakScreenMode()}. */
    static final String SCREEN_BOTH_LABEL = "Both (default) \u2014 QR in the plugin AND the dashboard opens in your browser";
    static final String SCREEN_PLUGIN_LABEL = "Plugin only \u2014 scan the QR in the tool window, no browser window opens";
    static final String SCREEN_BROWSER_LABEL = "Browser only \u2014 the dashboard opens in your browser, no QR in the tool window";

    // ---------------- live values used by the engine ----------------

    static boolean isEnabled() {
        try {
            return !"false".equals(PropertiesComponent.getInstance().getValue(KEY_ENABLED));
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Timer mode. False (the DEFAULT) = continuous countdown — the meter
     * fills every tick no matter what. True = the classic typing-only mode
     * where the countdown arms on keystrokes and decays when idle.
     */
    static boolean timerTypingOnly() {
        try {
            return "typing".equals(PropertiesComponent.getInstance().getValue(KEY_TIMER_MODE));
        } catch (Throwable t) {
            return false;
        }
    }

    /** "continuous" (default) or "typing" — for logs and /api/ide-activity. */
    static String timerModeName() {
        return timerTypingOnly() ? "typing" : "continuous";
    }

    /**
     * Where the phone scan surface appears when a break opens (3.3.0).
     * "both" (the DEFAULT) keeps the classic behavior: the QR appears in
     * the FitDeveloper tool window AND the dashboard opens in the default
     * browser at the same moment. "plugin" shows ONLY the in-IDE QR — the
     * browser never opens. "browser" opens ONLY the dashboard — the tool
     * window stays a pure status display. Any unknown stored value falls
     * back to "both", so an old or corrupted value can never leave the
     * user without ANY scan surface.
     */
    static String breakScreenMode() {
        try {
            String v = PropertiesComponent.getInstance().getValue(KEY_BREAK_SCREEN);
            if ("plugin".equals(v) || "browser".equals(v)) {
                return v;
            }
        } catch (Throwable t) {
            // fall through to the default
        }
        return "both";
    }

    /** True when a break should open the dashboard in the default browser. */
    static boolean openBrowserOnBreak() {
        return !"plugin".equals(breakScreenMode());
    }

    /** True when the QR should appear in the FitDeveloper tool window. */
    static boolean showQrInToolWindow() {
        return !"browser".equals(breakScreenMode());
    }

    /**
     * Minutes until the crunch meter reads 100% — the user-facing unit.
     * Fractional minutes are allowed; default 30 (research-backed, 3.0.0).
     * A legacy pre-2.8.0 seconds value is migrated once; a stored 1.5-min
     * value (the pre-3.0.0 default, usually a migration artifact rather
     * than a deliberate choice) is upgraded to the new default exactly once.
     */
    static double rampMinutes() {
        try {
            PropertiesComponent pc = PropertiesComponent.getInstance();
            if (pc.isValueSet(KEY_RAMP_MIN)) {
                double v = parseDouble(pc.getValue(KEY_RAMP_MIN), DEFAULT_RAMP_MINUTES);
                if (!pc.isValueSet(KEY_RAMP_UPGRADED)) {
                    // exactly once: move the pre-3.0.0 1.5-min default to the
                    // researched 30 min (stored values were usually migration
                    // artifacts, not deliberate choices)
                    pc.setValue(KEY_RAMP_UPGRADED, "true");
                    if (Math.abs(v - 1.5) < 0.001) {
                        v = DEFAULT_RAMP_MINUTES;
                        pc.setValue(KEY_RAMP_MIN, String.valueOf(v));
                        System.out.println("[FitDeveloper] ramp upgraded 1.5 min -> 30 min (research-backed default)");
                    }
                }
                return clampMinutes(v);
            }
            if (pc.isValueSet(KEY_RAMP_LEGACY_SEC)) {
                // one-time migration: 90 s -> 1.5 min etc. — also applies the
                // 3.0.0 default upgrade when the migrated value IS the old default
                double mins = parseDouble(pc.getValue(KEY_RAMP_LEGACY_SEC), 90.0) / 60.0;
                if (Math.abs(mins - 1.5) < 0.001) {
                    mins = DEFAULT_RAMP_MINUTES;
                    System.out.println("[FitDeveloper] ramp upgraded 1.5 min -> 30 min (research-backed default)");
                }
                mins = clampMinutes(mins);
                pc.setValue(KEY_RAMP_UPGRADED, "true");
                pc.setValue(KEY_RAMP_MIN, String.valueOf(mins));
                return mins;
            }
        } catch (Throwable t) {
            // fall through to the default
        }
        return DEFAULT_RAMP_MINUTES;
    }

    /** Internal math unit (engine tick, relay JSON) — derived from {@link #rampMinutes()}. */
    static int rampSeconds() {
        return (int) Math.round(rampMinutes() * 60.0);
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static double clampMinutes(double mins) {
        return Math.max(1.0, Math.min(mins, 120.0));
    }

    /** Steps the phone must reach to verify the break. */
    static int targetSteps() {
        try {
            int v = PropertiesComponent.getInstance().getInt(KEY_TARGET, 200);
            return Math.max(10, Math.min(v, 5000));
        } catch (Throwable t) {
            return 200;
        }
    }

    // ---------------- Swing form ----------------

    private JCheckBox enabledBox;
    private JComboBox<String> modeCombo;
    private JComboBox<String> screenCombo;
    private JSpinner rampSpin;
    private JSpinner targetSpin;

    /** Maps the stored key to the combo label. */
    private static String labelFor(boolean typingOnly) {
        return typingOnly ? MODE_TYPING_LABEL : MODE_CONTINUOUS_LABEL;
    }

    /** Maps the stored break-screen mode to the combo label. */
    private static String screenLabelFor(String mode) {
        if ("plugin".equals(mode)) {
            return SCREEN_PLUGIN_LABEL;
        }
        if ("browser".equals(mode)) {
            return SCREEN_BROWSER_LABEL;
        }
        return SCREEN_BOTH_LABEL;
    }

    @Override
    public String getDisplayName() {
        return "FitDeveloper";
    }

    @Override
    public JComponent createComponent() {
        enabledBox = new JCheckBox("Enable FitDeveloper engine (countdown + automatic walk breaks)");
        enabledBox.setSelected(isEnabled());

        modeCombo = new JComboBox<>(new String[]{MODE_CONTINUOUS_LABEL, MODE_TYPING_LABEL});
        modeCombo.setSelectedItem(labelFor(timerTypingOnly()));

        screenCombo = new JComboBox<>(new String[]{SCREEN_BOTH_LABEL, SCREEN_PLUGIN_LABEL, SCREEN_BROWSER_LABEL});
        screenCombo.setSelectedItem(screenLabelFor(breakScreenMode()));

        rampSpin = new JSpinner(new SpinnerNumberModel(rampMinutes(), 1.0, 120.0, 0.5));
        targetSpin = new JSpinner(new SpinnerNumberModel(targetSteps(), 10, 5000, 1));
        JSpinner.DefaultEditor rampEd = new JSpinner.NumberEditor(rampSpin, "0.#");
        rampSpin.setEditor(rampEd);
        JSpinner.DefaultEditor targetEd = new JSpinner.NumberEditor(targetSpin, "#");
        targetSpin.setEditor(targetEd);

        JButton defaults = new JButton("Reset to defaults (continuous / 30 min / 200 steps / both screens)");
        defaults.addActionListener(e -> {
            modeCombo.setSelectedItem(MODE_CONTINUOUS_LABEL);
            screenCombo.setSelectedItem(SCREEN_BOTH_LABEL);
            rampSpin.setValue(DEFAULT_RAMP_MINUTES);
            targetSpin.setValue(200);
        });

        // Wrapping JTextArea (NOT a fixed-width JLabel): the hint reflows
        // when the settings dialog is resized instead of clipping.
        JTextArea hint = new JTextArea(
                "How it works: with the engine enabled the timer runs (continuous by default — "
                        + "or only-while-typing), and when the meter reaches 100% a break opens "
                        + "AUTOMATICALLY: typing pauses and the QR appears in the FitDeveloper tool "
                        + "window — scan it with your phone and walk until the step target is verified. "
                        + "A break also releases itself (15 min with no walker, 5 min if the walker goes "
                        + "silent). With the engine OFF nothing is watched and nothing is ever blocked. "
                        + "Every value applies the moment you press Apply.\n\n"
                        + "Break scan screen: choose where the phone scan surface appears when a break "
                        + "opens — Both (the QR in the plugin AND the dashboard in your browser), "
                        + "Plugin only (no browser window ever opens) or Browser only (no QR in the "
                        + "tool window).\n\n"
                        + "Why 30 minutes: research recommends a ~5-minute light walk for every 30 "
                        + "minutes of sitting (Columbia University, 2023; Harvard Health) — the "
                        + "recommended default below automates exactly that.");
        hint.setEditable(false);
        hint.setFocusable(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setBorder(null);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        hint.setForeground(new JBColor(new Color(104, 118, 134), new Color(125, 139, 154)));
        hint.setOpaque(false);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 8, 4);
        p.add(enabledBox, c);

        c.gridwidth = 1;
        c.insets = new Insets(4, 4, 4, 4);
        c.gridy = 1;
        p.add(new JLabel("Timer mode:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(modeCombo, c);

        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.gridy = 2;
        p.add(new JLabel("Break scan screen:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(screenCombo, c);

        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.gridy = 3;
        p.add(new JLabel("Minutes until the walk break (recommended 30):"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST;
        p.add(rampSpin, c);

        c.gridx = 0;
        c.weightx = 0;
        c.gridy = 4;
        p.add(new JLabel("Steps required to verify the break (QR target):"), c);
        c.gridx = 1;
        c.weightx = 1;
        p.add(targetSpin, c);

        c.gridx = 0;
        c.gridy = 5;
        p.add(defaults, c);

        c.gridy = 6;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(12, 4, 4, 4);
        p.add(hint, c);

        return p;
    }

    @Override
    public boolean isModified() {
        if (enabledBox == null || modeCombo == null) {
            return false;
        }
        return enabledBox.isSelected() != isEnabled()
                || !labelFor(timerTypingOnly()).equals(modeCombo.getSelectedItem())
                || !screenLabelFor(breakScreenMode()).equals(screenCombo.getSelectedItem())
                || (Double) rampSpin.getValue() != rampMinutes()
                || (Integer) targetSpin.getValue() != targetSteps();
    }

    @Override
    public void apply() {
        if (enabledBox == null || modeCombo == null) {
            return;
        }
        boolean typingOnly = MODE_TYPING_LABEL.equals(modeCombo.getSelectedItem());
        String screen = SCREEN_PLUGIN_LABEL.equals(screenCombo.getSelectedItem()) ? "plugin"
                : SCREEN_BROWSER_LABEL.equals(screenCombo.getSelectedItem()) ? "browser" : "both";
        PropertiesComponent.getInstance().setValue(KEY_ENABLED, String.valueOf(enabledBox.isSelected()));
        PropertiesComponent.getInstance().setValue(KEY_TIMER_MODE, typingOnly ? "typing" : "continuous");
        PropertiesComponent.getInstance().setValue(KEY_BREAK_SCREEN, screen);
        PropertiesComponent.getInstance().setValue(KEY_RAMP_MIN, String.valueOf((Double) rampSpin.getValue()));
        PropertiesComponent.getInstance().setValue(KEY_TARGET, String.valueOf((Integer) targetSpin.getValue()));
        System.out.println("[FitDeveloper] settings applied: enabled=" + enabledBox.isSelected()
                + ", timerMode=" + (typingOnly ? "typing" : "continuous")
                + ", breakScreen=" + screen
                + ", ramp=" + rampMinutes() + " min, target=" + targetSteps() + " steps");
    }

    @Override
    public void reset() {
        if (enabledBox == null || modeCombo == null) {
            return;
        }
        enabledBox.setSelected(isEnabled());
        modeCombo.setSelectedItem(labelFor(timerTypingOnly()));
        screenCombo.setSelectedItem(screenLabelFor(breakScreenMode()));
        rampSpin.setValue(rampMinutes());
        targetSpin.setValue(targetSteps());
    }

    @Override
    public void disposeUIResources() {
        enabledBox = null;
        modeCombo = null;
        screenCombo = null;
        rampSpin = null;
        targetSpin = null;
    }
}
