package com.crunchguard.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;

/**
 * The CrunchGuard tool window.
 *
 * With JCEF (IntelliJ IDEA runtime): embeds the dashboard directly.
 * Without JCEF (Android Studio runtime): a live control panel —
 *   - crunch status, updating every second
 *   - enable/disable forced breaks WITHOUT uninstalling the plugin
 *   - "Force break now" — instant QR screen
 *   - shortcut to Settings | Tools | CrunchGuard
 *   - "Open Dashboard" for the full browser UI
 */
public final class CrunchGuardToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        CrunchGuardServer.ensureStarted();
        String url = CrunchGuardServer.baseUrl() + "/";
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

    private static JComponent fallbackPanel(Project project, String url) {
        JBPanel<JBPanel<?>> panel = new JBPanel<>(new GridBagLayout());

        JLabel title = new JLabel("CrunchGuard relay is running inside the IDE");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));

        JLabel note = new JLabel("<html><body style='width:340px'>"
                + "This runtime has no embedded browser (JCEF), so the dashboard opens in your "
                + "default browser. The forced-break engine works regardless — it watches your "
                + "real typing and opens the QR screen by itself.</body></html>");

        JLabel status = new JLabel("crunch 0% · target " + CrunchGuardSettings.targetSteps()
                + " steps · engine " + (CrunchGuardSettings.isEnabled() ? "ON" : "OFF"));

        JCheckBox enabled = new JCheckBox("Forced breaks enabled");
        enabled.setSelected(CrunchGuardSettings.isEnabled());
        enabled.addActionListener(e -> {
            PropertiesComponent.getInstance().setValue("crunchguard.enabled", String.valueOf(enabled.isSelected()));
            System.out.println("[CrunchGuard] engine " + (enabled.isSelected() ? "resumed" : "paused") + " (tool window toggle)");
        });

        JButton force = new JButton("Force break now");
        force.addActionListener(e -> {
            try {
                CrunchGuardEngine.forceBreakNow();
            } catch (Throwable ignored) {
            }
        });

        JButton settings = new JButton("Settings...");
        settings.addActionListener(e -> {
            try {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, CrunchGuardSettings.class);
            } catch (Throwable ignored) {
            }
        });

        JButton open = new JButton("Open Dashboard  (" + url + ")");
        open.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ignored) {
            }
        });

        // live status, every second
        new Timer(1000, ev -> {
            try {
                status.setText("crunch " + (int) CrunchGuardEngine.crunch()
                        + "% · target " + CrunchGuardSettings.targetSteps()
                        + " steps · engine " + (CrunchGuardSettings.isEnabled() ? "ON" : "OFF"));
            } catch (Throwable ignored) {
            }
        }).start();

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = JBUI.insets(14, 18, 4, 18);
        panel.add(title, c);
        c.gridy = 1;
        c.insets = JBUI.insets(4, 18, 10, 18);
        panel.add(note, c);
        c.gridy = 2;
        c.insets = JBUI.insets(2, 18, 2, 18);
        panel.add(status, c);
        c.gridy = 3;
        panel.add(enabled, c);

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        buttons.add(force);
        buttons.add(settings);
        buttons.add(open);
        c.gridy = 4;
        c.insets = JBUI.insets(10, 18, 14, 18);
        panel.add(buttons, c);

        return panel;
    }
}
