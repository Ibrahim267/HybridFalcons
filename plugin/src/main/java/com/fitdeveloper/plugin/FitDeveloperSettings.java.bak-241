package com.fitdeveloper.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.Configurable;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Settings | Tools | FitDeveloper — the developer stays in control:
 *  - enable/disable the forced-break engine WITHOUT uninstalling the plugin
 *  - freely choose the "seconds of sustained typing" before the IDE forces
 *    a break (any value, typed or spun)
 *  - freely choose the steps target the phone must reach to verify it
 *
 * No fixed modes: every value is a plain editable number and applies the
 * moment Apply is pressed.
 *
 * Persisted in the application-level PropertiesComponent; the engine reads
 * the values live every tick, so changes apply immediately (no restart).
 */
public final class FitDeveloperSettings implements Configurable {

    private static final String KEY_ENABLED = "fitdeveloper.enabled";
    private static final String KEY_RAMP = "fitdeveloper.rampSeconds";
    private static final String KEY_TARGET = "fitdeveloper.targetSteps";

    // ---------------- live values used by the engine ----------------

    static boolean isEnabled() {
        try {
            return !"false".equals(PropertiesComponent.getInstance().getValue(KEY_ENABLED));
        } catch (Throwable t) {
            return true;
        }
    }

    /** Seconds of sustained typing that fill the crunch meter to 100%. */
    static int rampSeconds() {
        try {
            int v = PropertiesComponent.getInstance().getInt(KEY_RAMP, 90);
            return Math.max(5, Math.min(v, 3600));
        } catch (Throwable t) {
            return 90;
        }
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
    private JSpinner rampSpin;
    private JSpinner targetSpin;

    @Override
    public String getDisplayName() {
        return "FitDeveloper";
    }

    @Override
    public JComponent createComponent() {
        enabledBox = new JCheckBox("Enable FitDeveloper forced breaks (watch typing, force a walk)");
        enabledBox.setSelected(isEnabled());

        rampSpin = new JSpinner(new SpinnerNumberModel(rampSeconds(), 5, 3600, 1));
        targetSpin = new JSpinner(new SpinnerNumberModel(targetSteps(), 10, 5000, 1));
        JSpinner.DefaultEditor rampEd = new JSpinner.NumberEditor(rampSpin, "#");
        rampSpin.setEditor(rampEd);
        JSpinner.DefaultEditor targetEd = new JSpinner.NumberEditor(targetSpin, "#");
        targetSpin.setEditor(targetEd);

        JButton defaults = new JButton("Reset to defaults (90 s / 200 steps)");
        defaults.addActionListener(e -> {
            rampSpin.setValue(90);
            targetSpin.setValue(200);
        });

        JLabel hint = new JLabel("<html><body style='width:460px'>"
                + "When the meter hits 100%, the IDE itself forces the break: a balloon pops, "
                + "and the QR screen opens in your browser automatically. Uncheck the box to "
                + "stop the engine without uninstalling the plugin. Type any value you like "
                + "— it applies the moment you press Apply."
                + "</body></html>");

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
        p.add(new JLabel("Sustained-typing seconds before the IDE forces a break:"), c);
        c.gridx = 1;
        p.add(rampSpin, c);

        c.gridx = 0;
        c.gridy = 2;
        p.add(new JLabel("Steps required to verify the break (QR target):"), c);
        c.gridx = 1;
        p.add(targetSpin, c);

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        p.add(defaults, c);

        c.gridy = 4;
        c.insets = new Insets(12, 4, 4, 4);
        p.add(hint, c);

        return p;
    }

    @Override
    public boolean isModified() {
        if (enabledBox == null) {
            return false;
        }
        return enabledBox.isSelected() != isEnabled()
                || (Integer) rampSpin.getValue() != rampSeconds()
                || (Integer) targetSpin.getValue() != targetSteps();
    }

    @Override
    public void apply() {
        if (enabledBox == null) {
            return;
        }
        PropertiesComponent.getInstance().setValue(KEY_ENABLED, String.valueOf(enabledBox.isSelected()));
        PropertiesComponent.getInstance().setValue(KEY_RAMP, String.valueOf((Integer) rampSpin.getValue()));
        PropertiesComponent.getInstance().setValue(KEY_TARGET, String.valueOf((Integer) targetSpin.getValue()));
        System.out.println("[FitDeveloper] settings applied: enabled=" + enabledBox.isSelected()
                + ", ramp=" + rampSeconds() + "s, target=" + targetSteps() + " steps");
    }

    @Override
    public void reset() {
        if (enabledBox == null) {
            return;
        }
        enabledBox.setSelected(isEnabled());
        rampSpin.setValue(rampSeconds());
        targetSpin.setValue(targetSteps());
    }

    @Override
    public void disposeUIResources() {
        enabledBox = null;
        rampSpin = null;
        targetSpin = null;
    }
}
