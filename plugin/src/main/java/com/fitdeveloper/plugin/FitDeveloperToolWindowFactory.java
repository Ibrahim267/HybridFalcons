package com.fitdeveloper.plugin;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Dimension;
import java.net.URI;

/**
 * The FitDeveloper tool window — a STATUS panel, not a second settings page.
 *
 * Design rule (3.0.0): everything configurable lives in
 * Settings | Tools | FitDeveloper; the tool window only SHOWS what the
 * engine is doing. So the old engine checkbox, timer-mode selector and
 * "Force break now" button are gone — they either duplicated Settings or
 * fought the product rule that breaks come from the engine alone.
 *
 * What remains:
 *   - a BIG ticking break countdown, color-escalating cyan -> amber -> red,
 *     mirrored from the engine — in the DEFAULT "continuous" mode it ticks
 *     every second you sit at the machine (typing NOT required); in the
 *     "only while typing" mode it counts typing seconds and cools down
 *     when you stop
 *   - at 100% the break fires AUTOMATICALLY — typing is locked until the
 *     walk is verified; live step progress while it is open
 *   - 3.4.0: trying to type while a break is open gives instant visual
 *     feedback — the scan QR flashes RED a few times (phone not scanned
 *     yet), or the big step counter flashes red (phone connected but the
 *     step goal not reached yet). The big step counter itself now renders
 *     in a bright high-contrast white instead of the hard-to-read teal.
 *   - 3.4.1: the progress bar caption ("0 / 200 steps", "crunch %") is no
 *     longer the LAF's theme-blue string on the gray track — it is our own
 *     bold ink label on a clear orange fill, readable in both themes.
 *   - a live QR for the phone walker (pure-Java {@link QrCode}, no JCEF
 *     needed — Android Studio has none) that appears ONLY once the countdown
 *     has finished and the break is actually open — scanning it joins that
 *     walk directly; while counting down the panel is hidden entirely; since
 *     3.3.0 the Break scan screen setting can also keep it hidden for good
 *     ("Browser only" — the dashboard in the browser is then the only
 *     scan surface)
 *   - two shortcuts: the Settings page and the browser dashboard
 *
 * The whole layout is GridBag-driven and reflows when the tool window is
 * resized: text blocks are line-wrapping JTextAreas, the progress bar and
 * text stretch horizontally, and a scroll pane catches very small sizes.
 *
 * JCEF (web dashboard tab) is accessed ONLY via reflection — the worst case
 * on any IDE is simply no web tab, never a blank control panel.
 */
public final class FitDeveloperToolWindowFactory implements ToolWindowFactory {

    /* theme-aware palette matching the dashboard (light, dark) */
    private static final JBColor CYAN    = new JBColor(new Color(9, 141, 170),  new Color(34, 211, 238));
    private static final JBColor AMBER   = new JBColor(new Color(176, 124, 16), new Color(251, 191, 36));
    private static final JBColor RED     = new JBColor(new Color(204, 64, 64),  new Color(248, 113, 113));
    private static final JBColor EMERALD = new JBColor(new Color(8, 138, 100),  new Color(52, 211, 153));
    private static final JBColor DIM     = new JBColor(new Color(104, 118, 134), new Color(125, 139, 154));

    /* 3.4.0: the live step counter color — near-white on dark themes,
       near-black on light ones: maximum contrast, no squinting (the old
       teal/emerald read as "blue" and tired the eyes). */
    private static final JBColor BRIGHT  = new JBColor(new Color(27, 31, 36),   new Color(236, 241, 246));
    /* QR module ink — the "normal" color the blocked-typing flash restores to. */
    private static final JBColor QR_INK  = new JBColor(Color.BLACK, new Color(210, 218, 226));
    /* 3.4.1: progress-bar fill — a deep orange that pops on the gray track in
       BOTH themes (the IDE's default progress blue was too close to it). The
       "0 / 200 steps" caption is NOT the LAF string anymore: the LAF paints
       it in theme blue on gray, which is exactly what tired the eyes — the
       caption is now our own {@link #barText} label in the high-contrast
       {@link #BRIGHT} ink, readable on the gray track and the orange fill. */
    private static final JBColor BAR_FILL = new JBColor(new Color(232, 92, 16), new Color(217, 84, 10));

    private JLabel cdTitle;
    private JLabel cdNum;
    private JTextArea cdUnit;
    private JTextArea status;
    private JProgressBar bar;
    private JLabel barText; // 3.4.1: our own bar caption (the LAF's stringPainted was theme blue on gray)
    private QrView qrView;
    private JTextArea qrCaption;
    private JBPanel<JBPanel<?>> qrPanel;
    private String qrCurrentUrl = null; // URL currently encoded in the QR (null = hidden)
    private Object browser; // JBCefBrowser via reflection, kept for disposal
    private Timer flashTimer;          // 3.4.0 blocked-typing red-flash burst
    private volatile long flashUntil;  // while set, refresh() must not recolor the flash targets

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        FitDeveloperServer.ensureStarted();
        String url = FitDeveloperServer.baseUrl() + "/";
        var factory = toolWindow.getContentManager().getFactory();

        // Tab 1 — native status panel, ALWAYS present (never blank).
        var nativeContent = factory.createContent(statusPanel(project, url), "Status", false);
        toolWindow.getContentManager().addContent(nativeContent);

        // 3.4.0: blocked-keystroke feedback — the coding lock pulses (rate
        // limited), this panel answers with a short red flash: the scan QR
        // while no phone is connected, the big step counter afterwards.
        BreakFlashHub.setListener(() -> {
            try {
                String fid = FitDeveloperEngine.forcedSessionId();
                if (fid == null) {
                    fid = FitDeveloperEngine.activeBreakSessionId();
                }
                if (fid == null || !FitDeveloperSettings.showQrInToolWindow()) {
                    return; // browser-only mode: the browser page is the feedback surface
                }
                FitDeveloperServer.Session s = FitDeveloperServer.sessionById(fid);
                boolean walkerConnected = s != null && s.walkerConnected;
                boolean goalMet = s != null && s.steps >= s.target;
                if (goalMet) {
                    return; // the lock releases on its own — nothing to complain about
                }
                javax.swing.SwingUtilities.invokeLater(() -> startFlash(walkerConnected));
            } catch (Throwable ignored) {
                // feedback is cosmetic — never let it propagate into the lock
            }
        });

        // Tab 2 — embedded web dashboard, only when JCEF actually works.
        JComponent web = embeddedDashboard(url);
        if (web != null) {
            try {
                var webContent = factory.createContent(web, "Web Dashboard", false);
                if (browser instanceof Disposable) {
                    webContent.setDisposer((Disposable) browser);
                }
                toolWindow.getContentManager().addContent(webContent);
            } catch (Throwable t) {
                System.out.println("[FitDeveloper] embedded dashboard tab failed, native panel stays: " + t);
            }
        }
        toolWindow.getContentManager().setSelectedContent(nativeContent);
    }

    /**
     * Builds the JCEF dashboard WITHOUT any compile-time reference to JCEF
     * classes. Returns null whenever JCEF is unavailable for any reason —
     * the native status panel keeps working either way.
     */
    private JComponent embeddedDashboard(String url) {
        try {
            Class<?> app = Class.forName("com.intellij.ui.jcef.JBCefApp");
            Object ok = app.getMethod("isSupported").invoke(null);
            if (!(ok instanceof Boolean) || !((Boolean) ok)) {
                return null;
            }
            Class<?> browserCls = Class.forName("com.intellij.ui.jcef.JBCefBrowser");
            Object b = browserCls.getConstructor(String.class).newInstance(url);
            this.browser = b;
            return (JComponent) browserCls.getMethod("getComponent").invoke(b);
        } catch (Throwable t) {
            System.out.println("[FitDeveloper] embedded dashboard unavailable (no JCEF on this IDE) — native panel only");
            return null;
        }
    }

    /** Small wrapping text block that reflows with the tool window width. */
    private static JTextArea wrapArea(String text, float fontScale, JBColor color) {
        JTextArea a = new JTextArea(text);
        a.setEditable(false);
        a.setFocusable(false);
        a.setOpaque(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(null);
        a.setFont(a.getFont().deriveFont(Font.PLAIN, 11f * fontScale));
        a.setForeground(color);
        return a;
    }

    private JComponent statusPanel(Project project, String url) {
        JBPanel<JBPanel<?>> panel = new JBPanel<>(new GridBagLayout());

        JLabel title = new JLabel("\u2694 FitDeveloper", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        JTextArea tagline = wrapArea(
                "The countdown runs while you work. At 100% a walk break opens "
                        + "automatically \u2014 walk until your phone verifies the steps and the IDE unlocks.",
                1.0f, DIM);

        /* ---------- the countdown card ---------- */
        JBPanel<JBPanel<?>> card = new JBPanel<>(new GridBagLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(8, 16, 12, 16)));

        cdTitle = new JLabel("CONNECTING…", SwingConstants.CENTER);
        cdTitle.setForeground(DIM);
        cdTitle.setFont(cdTitle.getFont().deriveFont(Font.BOLD, 11f));

        cdNum = new JLabel("—", SwingConstants.CENTER);
        cdNum.setFont(new Font(Font.MONOSPACED, Font.BOLD, 60));
        cdNum.setForeground(CYAN);

        cdUnit = wrapArea("starting the engine…", 1.0f, DIM);

        bar = new JProgressBar(0, 100);
        bar.setStringPainted(false); // 3.4.1: the LAF string is theme blue on the gray track — unreadable
        bar.setForeground(BAR_FILL);
        bar.setPreferredSize(new Dimension(bar.getPreferredSize().width, 20));
        barText = new JLabel("crunch 0%", SwingConstants.CENTER);
        barText.setFont(barText.getFont().deriveFont(Font.BOLD, 11f));
        barText.setForeground(BRIGHT);

        status = wrapArea(" ", 1.0f, DIM);

        GridBagConstraints cc = new GridBagConstraints();
        cc.gridx = 0;
        cc.weightx = 1;
        cc.fill = GridBagConstraints.HORIZONTAL;
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
        card.add(barText, cc); // same GridBag cell — the ink caption paints on top of the orange fill
        cc.gridy = 4;
        cc.insets = new Insets(8, 0, 0, 0);
        card.add(status, cc);

        /* ---------- phone walker QR (visible only while a break is open) ---------- */
        qrPanel = new JBPanel<>(new GridBagLayout());
        qrPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Phone walker"),
                BorderFactory.createEmptyBorder(2, 10, 8, 10)));
        qrPanel.setVisible(false); // 3.1.0: appears only when the countdown finishes

        qrView = new QrView();
        qrView.setForeground(QR_INK);

        qrCaption = wrapArea("BREAK OPEN \u2014 scan now and walk until the IDE unlocks!",
                1.0f, DIM);

        GridBagConstraints qc = new GridBagConstraints();
        qc.gridx = 0;
        qc.weightx = 1;
        qc.fill = GridBagConstraints.NONE;
        qc.anchor = GridBagConstraints.CENTER;
        qc.insets = new Insets(2, 0, 6, 0);
        qrPanel.add(qrView, qc);
        qc.gridy = 1;
        qc.fill = GridBagConstraints.HORIZONTAL;
        qc.insets = new Insets(0, 0, 0, 0);
        qrPanel.add(qrCaption, qc);

        /* ---------- shortcuts (the controls live in Settings) ---------- */
        JButton settings = new JButton("Open Settings…");
        settings.setToolTipText("Settings | Tools | FitDeveloper — engine, timer mode, minutes, steps");
        settings.addActionListener(e -> {
            try {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, FitDeveloperSettings.class);
            } catch (Throwable ignored) {
            }
        });

        JButton open = new JButton("Browser Dashboard");
        open.setToolTipText(url);
        open.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ignored) {
            }
        });

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 8, 0));
        buttons.add(settings);
        buttons.add(open);
        buttons.setOpaque(false);

        /* live refresh, every second — same data the browser countdown strip uses */
        new Timer(1000, ev -> refresh()).start();

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        c.insets = JBUI.insets(12, 14, 4, 14);
        panel.add(title, c);
        c.gridy = 1;
        c.insets = JBUI.insets(2, 14, 8, 14);
        panel.add(tagline, c);
        c.gridy = 2;
        c.insets = JBUI.insets(2, 14, 6, 14);
        panel.add(card, c);
        c.gridy = 3;
        c.insets = JBUI.insets(0, 10, 6, 10);
        panel.add(qrPanel, c);
        c.gridy = 4;
        c.insets = JBUI.insets(2, 14, 12, 14);
        panel.add(buttons, c);
        c.gridy = 5;
        c.weighty = 1; // absorb extra height so the content stays top-centered
        c.fill = GridBagConstraints.NONE;
        panel.add(new JPanel(), c);

        JScrollPane scroll = new JScrollPane(panel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        return scroll;
    }

    /** The phone-walk URL for the open break session. */
    private static String walkUrl(String sessionId) {
        String base;
        try {
            int https = FitDeveloperServer.httpsPort();
            base = https > 0
                    ? "https://" + FitDeveloperServer.lanIP() + ":" + https
                    : "http://" + FitDeveloperServer.lanIP() + ":" + FitDeveloperServer.port();
        } catch (Throwable t) {
            base = FitDeveloperServer.baseUrl();
        }
        return base + "/walk?s=" + sessionId;
    }

    /** Seconds as a wall clock for the big number: 45 -> "45", 2705 -> "45:05". */
    private static String clock(int sec) {
        return sec >= 60 ? String.format("%d:%02d", sec / 60, sec % 60) : String.valueOf(sec);
    }

    /** Unit words matching {@link #clock(int)}: "minutes:seconds" or "seconds". */
    private static String clockWords(int sec) {
        return sec >= 60 ? "minutes:seconds" : "seconds";
    }

    /** One snapshot of the engine, painted into the big countdown every second. */
    private void refresh() {
        try {
            int target = FitDeveloperSettings.targetSteps();

            // keep the QR in sync with the break lifecycle:
            // visible ONLY while a break session is open (countdown finished)
            String fid = FitDeveloperEngine.forcedSessionId();
            if (fid == null) {
                fid = FitDeveloperEngine.activeBreakSessionId();
            }
            updateQr(fid);

            if (!FitDeveloperSettings.isEnabled()) {
                cdTitle.setText("ENGINE PAUSED");
                cdNum.setText("—");
                cdNum.setForeground(DIM);
                cdUnit.setText("enable the engine in Settings | Tools | FitDeveloper to resume");
                bar.setMaximum(100);
                bar.setValue(0);
                barText.setText("crunch 0%");
                status.setText("engine paused \u2014 nothing is watched, nothing is interrupted");
                return;
            }

            if (fid != null) {
                FitDeveloperServer.Session s = FitDeveloperServer.sessionById(fid);
                int steps = s != null ? s.steps : 0;
                int tgt = s != null ? s.target : target;
                boolean flashing = System.currentTimeMillis() < flashUntil;
                cdTitle.setText("BREAK OPEN — WALK TO UNLOCK");
                cdNum.setText(String.valueOf(steps));
                if (!flashing) {
                    // 3.4.0: bright high-contrast counter (the old teal read as
                    // "blue" and was hard on the eyes); while a blocked-keystroke
                    // flash burst runs, the flash owns this color
                    cdNum.setForeground(BRIGHT);
                    cdUnit.setText("of " + tgt + " steps \u00b7 the IDE unlocks at the finish");
                    status.setText("your phone counts real steps \u2014 typing stays blocked until the walk is verified");
                }
                bar.setMaximum(tgt);
                bar.setValue(steps);
                barText.setText(steps + " / " + tgt + " steps");
                return;
            }

            bar.setMaximum(100);
            double cr = FitDeveloperEngine.crunch();
            int ramp = Math.max(5, FitDeveloperSettings.rampSeconds());
            int left = (int) Math.ceil((100.0 - cr) * ramp / 100.0);
            bar.setValue((int) cr);
            barText.setText("crunch " + (int) cr + "%");

            boolean typingOnly = FitDeveloperSettings.timerTypingOnly();
            String modeNote = typingOnly ? "typing mode" : "continuous countdown";

            if (!typingOnly) {
                /* continuous countdown: ticks every second at the machine,
                   typing or not — the meter never cools down */
                status.setText("target " + target + " steps \u00b7 " + modeNote + " (change in Settings)");
                if (left <= 0) {
                    cdTitle.setText("BREAK DUE");
                    cdNum.setText("0");
                    cdNum.setForeground(RED);
                    cdUnit.setText("opening the walk break \u2014 walk to unlock");
                } else {
                    double frac = ramp > 0 ? (double) left / ramp : 1.0;
                    cdTitle.setText("BREAK DUE IN");
                    cdNum.setText(clock(left));
                    cdNum.setForeground(frac > 0.55 ? CYAN : frac > 0.22 ? AMBER : RED);
                    cdUnit.setText(clockWords(left) + " at this machine until 100% crunch"
                            + " (then the walk starts)");
                }
                return;
            }

            long ago = EditorActivityListener.lastActivityAgoSec();
            boolean typing = ago >= 0 && ago < 3;
            status.setText("target " + target + " steps \u00b7 " + modeNote + " (change in Settings)");

            if (typing) {
                if (left <= 0) {
                    cdTitle.setText("BREAK DUE");
                    cdNum.setText("0");
                    cdNum.setForeground(RED);
                    cdUnit.setText("opening the walk break \u2014 walk to unlock");
                } else {
                    double frac = ramp > 0 ? (double) left / ramp : 1.0;
                    cdTitle.setText("BREAK DUE IN");
                    cdNum.setText(clock(left));
                    cdNum.setForeground(frac > 0.55 ? CYAN : frac > 0.22 ? AMBER : RED);
                    cdUnit.setText(clockWords(left) + " of typing until 100% crunch (then the walk starts)");
                }
            } else {
                cdTitle.setText("COOLING DOWN");
                cdNum.setText("—");
                cdNum.setForeground(DIM);
                cdUnit.setText("idle \u2014 start typing to arm the countdown");
            }
        } catch (Throwable ignored) {
            // never let a UI hiccup kill the timer
        }
    }

    /**
     * 3.4.0 blocked-typing feedback: flash RED a few times so the developer
     * SEES why typing is dead. stepsMode=false flashes the scan QR (phone
     * not scanned yet); stepsMode=true flashes the big step counter (phone
     * connected but the step goal not reached). One burst = 4 red blinks
     * over ~1.4 s. Further pulses are ignored while a burst runs, and
     * {@link #refresh()} leaves the flashed colors alone until it ends
     * ({@code flashUntil}) — afterwards the normal 1 s repaint restores
     * everything by itself.
     */
    private void startFlash(boolean stepsMode) {
        if (flashTimer != null && flashTimer.isRunning()) {
            return;
        }
        flashUntil = System.currentTimeMillis() + 8 * 180L + 250L;
        final int[] tick = {0};
        flashTimer = new Timer(180, ev -> {
            tick[0]++;
            boolean red = tick[0] % 2 == 1;
            if (stepsMode) {
                cdNum.setForeground(red ? RED : BRIGHT);
            } else {
                qrView.setForeground(red ? RED : QR_INK);
                qrCaption.setForeground(red ? RED : DIM);
                qrCaption.setText(red
                        ? "TYPING IS LOCKED \u2014 scan the QR and walk to unlock!"
                        : "BREAK OPEN \u2014 scan now and walk until the IDE unlocks!");
            }
            if (tick[0] >= 8) {
                ((Timer) ev.getSource()).stop();
                if (stepsMode) {
                    cdNum.setForeground(BRIGHT);
                } else {
                    qrView.setForeground(QR_INK);
                    qrCaption.setForeground(DIM);
                    qrCaption.setText("BREAK OPEN \u2014 scan now and walk until the IDE unlocks!");
                }
            }
        });
        flashTimer.start();
    }

    /**
     * Keeps the QR in sync with the break lifecycle (3.1.0 rule): the QR is
     * shown ONLY while a walk break is actually open — i.e. AFTER the
     * countdown has finished. While the countdown runs (or the engine is
     * paused) the panel is hidden, so the tool window stays a pure status
     * display. The QR is regenerated only when the encoded URL changes.
     * Since 3.3.0 the panel is ALSO hidden whenever the Break scan screen
     * setting is "Browser only" — the dashboard in the default browser is
     * then the single scan surface.
     */
    private void updateQr(String sessionId) {
        if (sessionId == null || !FitDeveloperSettings.showQrInToolWindow()) {
            if (qrPanel.isVisible()) {
                qrCurrentUrl = null;
                qrView.setQr(null);
                qrPanel.setVisible(false);
            }
            return;
        }
        String want = walkUrl(sessionId);
        if (!want.equals(qrCurrentUrl)) {
            qrCurrentUrl = want;
            try {
                qrView.setQr(new QrCode(want));
            } catch (Throwable t) {
                qrView.setQr(null);
            }
            qrCaption.setText("BREAK OPEN \u2014 scan now and walk until the IDE unlocks!");
        }
        qrPanel.setVisible(true);
    }

    /**
     * Minimal theme-aware QR renderer: paints the {@link QrCode} matrix with
     * a 4-module quiet zone, scaled to whatever space the tool window gives
     * it. Regeneration is handled by {@link #updateQr(String)}.
     */
    private static final class QrView extends JComponent {
        private QrCode qr;

        void setQr(QrCode q) {
            this.qr = q;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(148, 148);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(64, 64);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (qr == null) {
                return;
            }
            int quiet = 4;
            int cells = qr.size + quiet * 2;
            int cell = Math.max(1, Math.min(getWidth(), getHeight()) / cells);
            int px = cell * cells;
            int ox = (getWidth() - px) / 2;
            int oy = (getHeight() - px) / 2;
            g.setColor(getForeground());
            for (int y = 0; y < qr.size; y++) {
                for (int x = 0; x < qr.size; x++) {
                    if (qr.modules[y][x]) {
                        g.fillRect(ox + (x + quiet) * cell, oy + (y + quiet) * cell, cell, cell);
                    }
                }
            }
        }
    }
}
