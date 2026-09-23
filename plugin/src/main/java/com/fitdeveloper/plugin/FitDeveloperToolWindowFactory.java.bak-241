package com.fitdeveloper.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;

/**
 * The FitDeveloper tool window.
 *
 * With JCEF (IntelliJ IDEA runtime): embeds the dashboard directly.
 * Without JCEF (Android Studio runtime): a live control panel —
 *   - a BIG ticking break countdown (90 -> 89 -> 88 ... while you type),
 *     color-escalating cyan -> amber -> red, mirrored from the engine
 *   - live crunch meter + step progress while a break is open
 *   - enable/disable forced breaks WITHOUT uninstalling the plugin
 *   - "Force break now" — instant QR screen
 *   - shortcut to Settings | Tools | FitDeveloper
 *   - "Open Dashboard" for the full browser UI
 */
public final class FitDeveloperToolWindowFactory implements ToolWindowFactory {

    /* theme-aware palette matching the dashboard (light, dark) */
    private static final JBColor CYAN    = new JBColor(new Color(9, 141, 170),  new Color(34, 211, 238));
    private static final JBColor AMBER   = new JBColor(new Color(176, 124, 16), new Color(251, 191, 36));
    private static final JBColor RED     = new JBColor(new Color(204, 64, 64),  new Color(248, 113, 113));
    private static final JBColor EMERALD = new JBColor(new Color(8, 138, 100),  new Color(52, 211, 153));
    private static final JBColor DIM     = new JBColor(new Color(104, 118, 134), new Color(125, 139, 154));

    private JLabel cdTitle;
    private JLabel cdNum;
    private JLabel cdUnit;
    private JProgressBar bar;
    private JLabel status;
    private JCheckBox enabled;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        FitDeveloperServer.ensureStarted();
        String url = FitDeveloperServer.baseUrl() + "/";
        JComponent component;
        if (JBCefApp.isSupported()) {
            JBCefBrowser browser = new JBCefBrowser(url);
            component = browser.getComponent();
            var content = toolWindow.getContentManager().getFactory()
                    .createContent(component, "Dashboard", false);
            content.setDisposer(browser);
            toolWindow.getContentManager().addContent(content);
        } else {
            var content = toolWindow.getContentManager().getFactory()
                    .createContent(fallbackPanel(project, url), "Dashboard", false);
            toolWindow.getContentManager().addContent(content);
        }
    }

    private JComponent fallbackPanel(Project project, String url) {
        JBPanel<JBPanel<?>> panel = new JBPanel<>(new GridBagLayout());

        JLabel title = new JLabel("\u2694 FitDeveloper", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        JLabel note = new JLabel("<html><body style='width:380px;text-align:center'>"
                + "The relay runs inside your IDE — it watches your real typing and forces "
                + "a walking break at 100% crunch. This runtime has no embedded browser "
                + "(JCEF), so the QR screen opens in your default browser.<br>"
                + "<span style='color:#7d8b9a'>dashboard: " + url + "</span></body></html>");
        note.setHorizontalAlignment(SwingConstants.CENTER);
        note.setForeground(DIM);
        note.setFont(note.getFont().deriveFont(Font.PLAIN, 11f));

        /* ---------- the countdown card ---------- */
        JBPanel<JBPanel<?>> card = new JBPanel<>(new GridBagLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(8, 26, 14, 26)));

        cdTitle = new JLabel("CONNECTING…", SwingConstants.CENTER);
        cdTitle.setForeground(DIM);
        cdTitle.setFont(cdTitle.getFont().deriveFont(Font.BOLD, 11f));

        cdNum = new JLabel("—", SwingConstants.CENTER);
        cdNum.setFont(new Font(Font.MONOSPACED, Font.BOLD, 64));
        cdNum.setForeground(CYAN);

        cdUnit = new JLabel("starting the engine…", SwingConstants.CENTER);
        cdUnit.setForeground(DIM);
        cdUnit.setFont(cdUnit.getFont().deriveFont(Font.PLAIN, 11f));

        bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setString("crunch 0%");
        bar.setPreferredSize(new Dimension(330, 22));

        status = new JLabel(" ", SwingConstants.CENTER);
        status.setForeground(DIM);
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 11f));

        GridBagConstraints cc = new GridBagConstraints();
        cc.gridx = 0;
        cc.gridy = 0;
        cc.insets = new Insets(4, 0, 2, 0);
        card.add(cdTitle, cc);
        cc.gridy = 1;
        cc.insets = new Insets(0, 0, 0, 0);
        card.add(cdNum, cc);
        cc.gridy = 2;
        cc.insets = new Insets(0, 0, 8, 0);
        card.add(cdUnit, cc);
        cc.gridy = 3;
        card.add(bar, cc);
        cc.gridy = 4;
        cc.insets = new Insets(8, 0, 0, 0);
        card.add(status, cc);

        /* ---------- controls ---------- */
        enabled = new JCheckBox("Forced breaks enabled");
        enabled.setSelected(FitDeveloperSettings.isEnabled());
        enabled.addActionListener(e -> {
            PropertiesComponent.getInstance().setValue("fitdeveloper.enabled", String.valueOf(enabled.isSelected()));
            System.out.println("[FitDeveloper] engine " + (enabled.isSelected() ? "resumed" : "paused") + " (tool window toggle)");
        });

        JButton force = new JButton("Force break now");
        force.addActionListener(e -> {
            try {
                FitDeveloperEngine.forceBreakNow();
            } catch (Throwable ignored) {
            }
        });

        JButton settings = new JButton("Settings...");
        settings.addActionListener(e -> {
            try {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, FitDeveloperSettings.class);
            } catch (Throwable ignored) {
            }
        });

        JButton open = new JButton("Open Dashboard");
        open.setToolTipText(url);
        open.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ignored) {
            }
        });

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 8, 0));
        buttons.add(force);
        buttons.add(settings);
        buttons.add(open);
        buttons.setOpaque(false);

        /* live refresh, every second — same data the browser countdown strip uses */
        new Timer(1000, ev -> refresh()).start();

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = JBUI.insets(14, 18, 4, 18);
        panel.add(title, c);
        c.gridy = 1;
        c.insets = JBUI.insets(4, 18, 10, 18);
        panel.add(note, c);
        c.gridy = 2;
        c.insets = JBUI.insets(2, 18, 2, 18);
        panel.add(card, c);
        c.gridy = 3;
        c.insets = JBUI.insets(12, 18, 2, 18);
        panel.add(enabled, c);
        c.gridy = 4;
        c.insets = JBUI.insets(10, 18, 16, 18);
        panel.add(buttons, c);

        return panel;
    }

    /** One snapshot of the engine, painted into the big countdown every second. */
    private void refresh() {
        try {
            boolean on = FitDeveloperSettings.isEnabled();
            if (on != enabled.isSelected()) {
                enabled.setSelected(on); // settings page can flip it too
            }
            int target = FitDeveloperSettings.targetSteps();

            if (!on) {
                cdTitle.setText("FORCED BREAKS PAUSED");
                cdNum.setText("—");
                cdNum.setForeground(DIM);
                cdUnit.setText("tick the checkbox below to resume");
                bar.setMaximum(100);
                bar.setValue(0);
                bar.setString("crunch 0%");
                status.setText("engine OFF · nothing will interrupt you");
                return;
            }

            String fid = FitDeveloperEngine.forcedSessionId();
            if (fid == null) {
                fid = FitDeveloperEngine.activeBreakSessionId();
            }
            if (fid != null) {
                FitDeveloperServer.Session s = FitDeveloperServer.sessionById(fid);
                int steps = s != null ? s.steps : 0;
                int tgt = s != null ? s.target : target;
                cdTitle.setText("BREAK OPEN — WALK TO UNLOCK");
                cdNum.setText(String.valueOf(steps));
                cdNum.setForeground(EMERALD);
                cdUnit.setText("of " + tgt + " steps · the IDE unlocks at the finish");
                bar.setMaximum(tgt);
                bar.setValue(steps);
                bar.setString(steps + " / " + tgt + " steps");
                status.setText("your phone counts real steps — coding stays locked until verified");
                return;
            }

            bar.setMaximum(100);
            double cr = FitDeveloperEngine.crunch();
            long ago = EditorActivityListener.lastActivityAgoSec();
            boolean typing = ago >= 0 && ago < 3;
            int ramp = Math.max(5, FitDeveloperSettings.rampSeconds());
            int left = (int) Math.ceil((100.0 - cr) * ramp / 100.0);
            bar.setValue((int) cr);
            bar.setString("crunch " + (int) cr + "%");
            status.setText("target " + target + " steps · engine is watching your typing");

            if (typing) {
                double frac = ramp > 0 ? (double) left / ramp : 1.0;
                cdTitle.setText("FORCED BREAK IN");
                cdNum.setText(String.valueOf(left));
                cdNum.setForeground(frac > 0.55 ? CYAN : frac > 0.22 ? AMBER : RED);
                cdUnit.setText("seconds of typing until the break fires");
            } else {
                cdTitle.setText("COOLING DOWN");
                cdNum.setText("—");
                cdNum.setForeground(DIM);
                cdUnit.setText("idle — start typing to arm the countdown");
            }
        } catch (Throwable ignored) {
            // never let a UI hiccup kill the timer
        }
    }
}
