package com.crunchguard.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Desktop;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.net.URI;

/**
 * The CrunchGuard dashboard, embedded in the IDE as a JCEF tool window
 * (right side). Loads the SAME front-end as the standalone version, served
 * by the plugin's built-in relay on http://localhost:8790 (falls back to
 * higher ports if busy).
 *
 * If JCEF is unavailable (custom JBR without JCEF), falls back to a Swing
 * panel with an "open in browser" button.
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
                    .createContent(fallbackPanel(url), "Dashboard", false);
            toolWindow.getContentManager().addContent(content);
        }
    }

    private static JComponent fallbackPanel(String url) {
        JBPanel<JBPanel<?>> panel = new JBPanel<>(new GridBagLayout());
        JLabel label = new JLabel("<html><body style='width:280px'>"
                + "JCEF is not available in this runtime, so the dashboard"
                + " cannot be embedded here.<br><br>"
                + "The CrunchGuard relay is still running inside the IDE —"
                + " open the dashboard in your default browser:</body></html>");
        JButton open = new JButton("Open Dashboard  (" + url + ")");
        open.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ignored) {
            }
        });
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = JBUI.insets(12);
        panel.add(label, c);
        c.gridy = 1;
        panel.add(open, c);
        return panel;
    }
}
