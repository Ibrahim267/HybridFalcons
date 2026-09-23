package com.fitdeveloper.plugin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * FitDeveloper embedded relay — a faithful Java port of the standalone Node
 * server (without-plugin/server.js). It serves the SAME web front-end
 * (resources/web) and the SAME JSON API, so no Node.js runtime is needed:
 * the IDE plugin is fully self-contained.
 *
 * Extra plugin-only endpoint:
 *   GET /api/ide-activity  -> real editor keystroke activity, consumed by the
 *                             injected ide-bridge.js so the crunch meter is
 *                             driven by actual coding in the IDE.
 *
 * The relay also serves HTTPS (port 8791, self-signed cert bundled at
 * /cert/keystore.p12): iOS only fires DeviceMotion events on secure
 * contexts, so the QR links the phone to the https:// port — that is what
 * makes REAL step counting work from the plugin variant.
 */
public final class FitDeveloperServer {

    public static final String VERSION = "2.4.1-plugin";
    static final long SESSION_TTL_MS = 2L * 60 * 60 * 1000; // 2h, same as server.js
    static final int MAX_SESSIONS = 500;
    static final int PORT_BASE = 8790;
    static final int HTTPS_PORT_BASE = 8791;

    // ---------------- session model (mirrors server.js) ----------------

    static final class Session {
        final String id;
        final long createdAt = System.currentTimeMillis();
        final int target;
        volatile int steps;
        volatile long updatedAt = createdAt;
        volatile boolean walkerConnected;
        volatile long walkerLastSeen;
        volatile String walkerName;
        final List<long[]> log = new ArrayList<>();        // parallel: {t, steps}
        final List<String> logSource = new ArrayList<>();  // parallel: source

        Session(String id, int target) {
            this.id = id;
            this.target = target;
        }
    }

    private static final Object SESSIONS_LOCK = new Object();
    private static final LinkedHashMap<String, Session> SESSIONS =
            new LinkedHashMap<String, Session>(64, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
                    return size() > MAX_SESSIONS; // evict oldest, like server.js
                }
            };

    private static volatile HttpServer SERVER;
    private static volatile HttpsServer HTTPS;
    private static volatile int PORT = -1;
    private static volatile int HTTPS_PORT = -1;
    private static final long STARTED = System.currentTimeMillis();
    private static final Set<String> COMPLETION_NOTIFIED = ConcurrentHashMap.newKeySet();

    private static final Pattern P_TARGET = Pattern.compile("\"target\"\\s*:\\s*(-?[0-9]+)");
    private static final Pattern P_STEPS = Pattern.compile("\"steps\"\\s*:\\s*(-?[0-9]+)");
    private static final Pattern P_SOURCE = Pattern.compile("\"source\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern P_WALKER = Pattern.compile("\"walkerName\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern P_SESSION = Pattern.compile("^/api/session/([a-z0-9]+)$");
    private static final Pattern P_PROGRESS = Pattern.compile("^/api/session/([a-z0-9]+)/progress$");
    private static final Pattern P_LOG = Pattern.compile("^/api/session/([a-z0-9]+)/log$");

    private FitDeveloperServer() {
    }

    // ---------------- lifecycle ----------------

    public static synchronized void ensureStarted() {
        if (SERVER != null) {
            return;
        }
        if (tryBind()) {
            return;
        }
        System.err.println("[FitDeveloper] relay ports " + PORT_BASE + "-" + (PORT_BASE + 5)
                + " busy — retrying every 3s until one frees up"
                + " (close the other IDE/app that holds them)");
        Thread retry = new Thread(() -> {
            while (SERVER == null) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    return;
                }
                if (tryBind()) {
                    return;
                }
            }
        }, "fitdeveloper-relay-retry");
        retry.setDaemon(true);
        retry.start();
    }

    /**
     * One bind pass over the port range. Synchronized so project startup and
     * the background retry thread can never race into a double bind.
     */
    private static synchronized boolean tryBind() {
        IOException lastError = null;
        for (int port = PORT_BASE; port < PORT_BASE + 6; port++) {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
                server.createContext("/", FitDeveloperServer::dispatch);
                server.setExecutor(Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "fitdeveloper-relay");
                    t.setDaemon(true);
                    return t;
                }));
                server.start();
                SERVER = server;
                PORT = port;
                System.out.println("[FitDeveloper] relay on http://localhost:" + port
                        + "  (phone / LAN: http://" + lanIP() + ":" + port + ")");
                startSweeper();
                FitDeveloperEngine.start();
                startHttps();
                return true;
            } catch (IOException e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            System.err.println("[FitDeveloper] could not bind ports " + PORT_BASE + "-"
                    + (PORT_BASE + 5) + ": " + lastError);
        }
        return false;
    }

    public static boolean isRunning() {
        return SERVER != null && PORT > 0;
    }

    /** HTTPS port for the phone (iOS motion sensors need a secure context), or -1. */
    public static int httpsPort() {
        return HTTPS_PORT;
    }

    public static String baseUrl() {
        return "http://localhost:" + Math.max(PORT, PORT_BASE);
    }

    /**
     * Starts the HTTPS listener (8791..8796) with the bundled self-signed
     * certificate. Same dispatcher as HTTP, so every route works over both.
     * Failure is non-fatal: HTTP keeps working, the QR just falls back.
     */
    private static void startHttps() {
        try {
            char[] pass = "crunchguard".toCharArray();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = FitDeveloperServer.class.getResourceAsStream("/cert/keystore.p12")) {
                if (in == null) {
                    System.out.println("[FitDeveloper] https skipped: bundled keystore missing");
                    return;
                }
                ks.load(in, pass);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, pass);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            for (int port = HTTPS_PORT_BASE; port < HTTPS_PORT_BASE + 6; port++) {
                try {
                    HttpsServer hs = HttpsServer.create(new InetSocketAddress("0.0.0.0", port), 0);
                    hs.setHttpsConfigurator(new HttpsConfigurator(ctx));
                    hs.createContext("/", FitDeveloperServer::dispatch);
                    hs.setExecutor(Executors.newCachedThreadPool(r -> {
                        Thread t = new Thread(r, "fitdeveloper-relay-https");
                        t.setDaemon(true);
                        return t;
                    }));
                    hs.start();
                    HTTPS = hs;
                    HTTPS_PORT = port;
                    System.out.println("[FitDeveloper] relay https on https://localhost:" + port
                            + "  (phone: https://" + lanIP() + ":" + port
                            + " — accept the certificate warning so iOS motion sensors work)");
                    return;
                } catch (IOException e) {
                    // port busy -> try next
                }
            }
            System.out.println("[FitDeveloper] https: no free port in " + HTTPS_PORT_BASE + "-" + (HTTPS_PORT_BASE + 5));
        } catch (Throwable t) {
            System.out.println("[FitDeveloper] https start failed (http still fine): " + t);
        }
    }

    private static void startSweeper() {
        Thread sweeper = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    return;
                }
                long now = System.currentTimeMillis();
                synchronized (SESSIONS_LOCK) {
                    SESSIONS.values().removeIf(s -> now - s.createdAt > SESSION_TTL_MS);
                }
            }
        }, "fitdeveloper-sweeper");
        sweeper.setDaemon(true);
        sweeper.start();
    }

    // ---------------- routing ----------------

    private static void dispatch(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if ("OPTIONS".equals(method)) {
                sendJSON(ex, 204, null);
                return;
            }
            if ("/api/health".equals(path)) {
                synchronized (SESSIONS_LOCK) {
                    sendJSON(ex, 200, "{\"ok\":true,\"service\":\"fitdeveloper\",\"version\":"
                            + esc(VERSION) + ",\"uptimeSec\":" + uptimeSec()
                            + ",\"sessions\":" + SESSIONS.size() + ",\"ts\":" + System.currentTimeMillis() + "}");
                }
                return;
            }
            if ("/api/config".equals(path)) {
                sendJSON(ex, 200, "{\"lanIP\":" + esc(lanIP()) + ",\"port\":" + PORT
                        + ",\"httpsPort\":" + (HTTPS_PORT > 0 ? String.valueOf(HTTPS_PORT) : "null") + "}");
                return;
            }
            if ("/api/ide-activity".equals(path)) {
                long ago = EditorActivityListener.lastActivityAgoSec();
                String fid = FitDeveloperEngine.forcedSessionId();
                boolean typing = ago >= 0 && ago < 3;
                double cr = FitDeveloperEngine.crunch();
                boolean engineOn = FitDeveloperSettings.isEnabled();
                int ramp = Math.max(5, FitDeveloperSettings.rampSeconds());
                double rate = 100.0 / ramp;
                boolean counting = typing && engineOn && cr < 100;
                int toBreak = (int) Math.ceil((100.0 - cr) / rate);
                sendJSON(ex, 200, "{\"keystrokes\":" + EditorActivityListener.keystrokes()
                        + ",\"lastActivityAgoSec\":" + ago
                        + ",\"active\":" + typing
                        + ",\"crunch\":" + String.format(java.util.Locale.ROOT, "%.1f", cr)
                        + ",\"secondsToBreak\":" + (counting ? String.valueOf(toBreak) : "null")
                        + ",\"engineOn\":" + engineOn
                        + ",\"rampSeconds\":" + ramp
                        + ",\"targetSteps\":" + FitDeveloperSettings.targetSteps()
                        + ",\"forcedSession\":" + (fid != null ? esc(fid) : "null")
                        + "}");
                return;
            }
            if ("/api/session".equals(path) && "POST".equals(method)) {
                String body = readBody(ex);
                int target = 200;
                Matcher m = P_TARGET.matcher(body);
                if (m.find()) {
                    target = clamp(parse(m.group(1), 200), 10, 5000);
                }
                Session dup = openSession(10 * 60 * 1000L);
                if (dup != null) {
                    // a break is already running (engine-forced or another tab) — adopt it
                    sendJSON(ex, 200, publicState(dup));
                    return;
                }
                Session s = createSession(target);
                System.out.println("[FitDeveloper] session " + s.id + " created target=" + s.target);
                BreakNotifier.breakStarted(s.id, s.target);
                sendJSON(ex, 200, publicState(s));
                return;
            }
            if ("/api/sessions".equals(path)) {
                synchronized (SESSIONS_LOCK) {
                    StringBuilder ids = new StringBuilder("[");
                    boolean first = true;
                    for (String id : SESSIONS.keySet()) {
                        if (!first) ids.append(',');
                        ids.append(esc(id));
                        first = false;
                    }
                    ids.append(']');
                    sendJSON(ex, 200, "{\"count\":" + SESSIONS.size() + ",\"ids\":" + ids + "}");
                }
                return;
            }
            Matcher mp = P_PROGRESS.matcher(path);
            if (mp.matches() && "POST".equals(method)) {
                Session s = getSession(mp.group(1));
                if (s == null) {
                    sendJSON(ex, 404, "{\"error\":\"session not found\"}");
                    return;
                }
                applyProgress(s, readBody(ex));
                sendJSON(ex, 200, publicState(s));
                return;
            }
            Matcher mg = P_SESSION.matcher(path);
            if (mg.matches() && "GET".equals(method)) {
                Session s = getSession(mg.group(1));
                if (s == null) {
                    sendJSON(ex, 404, "{\"error\":\"session not found — scan the QR again\"}");
                    return;
                }
                sendJSON(ex, 200, publicState(s));
                return;
            }
            Matcher ml = P_LOG.matcher(path);
            if (ml.matches() && "GET".equals(method)) {
                Session s = getSession(ml.group(1));
                if (s == null) {
                    sendJSON(ex, 404, "{\"error\":\"session not found\"}");
                    return;
                }
                sendJSON(ex, 200, "{\"id\":" + esc(s.id) + ",\"log\":" + logArray(s) + "}");
                return;
            }
            if ("GET".equals(method)) {
                serveStatic(ex, path);
                return;
            }
            sendJSON(ex, 405, "{\"error\":\"method not allowed\"}");
        } catch (Exception e) {
            try {
                sendJSON(ex, 500, "{\"error\":" + esc("internal: " + e.getMessage()) + "}");
            } catch (Exception ignored) {
            }
        } finally {
            ex.close();
        }
    }

    // ---------------- API helpers ----------------

    private static Session createSession(int target) {
        synchronized (SESSIONS_LOCK) {
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            Session s = new Session(id, target);
            SESSIONS.put(id, s);
            return s;
        }
    }

    private static Session getSession(String id) {
        synchronized (SESSIONS_LOCK) {
            return SESSIONS.get(id);
        }
    }

    /** Package-private: engine looks up the session it forced. */
    static Session sessionById(String id) {
        synchronized (SESSIONS_LOCK) {
            return SESSIONS.get(id);
        }
    }

    /** Newest not-done session younger than {@code withinMs}, or null. */
    static Session openSession(long withinMs) {
        long now = System.currentTimeMillis();
        synchronized (SESSIONS_LOCK) {
            for (Session s : SESSIONS.values()) {
                if (s.steps < s.target && now - s.createdAt < withinMs) {
                    return s;
                }
            }
        }
        return null;
    }

    /** Engine-forced session; null if an open session already exists (dedupe). */
    static String createForcedSession(int target) {
        synchronized (SESSIONS_LOCK) {
            long now = System.currentTimeMillis();
            for (Session s : SESSIONS.values()) {
                if (s.steps < s.target && now - s.createdAt < 10 * 60 * 1000L) {
                    return null;
                }
            }
            Session s = createSession(target);
            System.out.println("[FitDeveloper] forced session " + s.id + " created target=" + s.target);
            return s.id;
        }
    }

    private static void applyProgress(Session s, String body) {
        Matcher ms = P_STEPS.matcher(body);
        if (!ms.find()) {
            return;
        }
        int steps = parse(ms.group(1), 0);
        if (steps < 0) {
            return;
        }
        Matcher msrc = P_SOURCE.matcher(body);
        String source = msrc.find() ? unescapeJSON(msrc.group(1)) : "?";
        synchronized (s) {
            s.steps = Math.max(s.steps, Math.min(100_000, steps)); // monotonic
            s.updatedAt = System.currentTimeMillis();
            Matcher mw = P_WALKER.matcher(body);
            if (mw.find()) {
                String name = unescapeJSON(mw.group(1));
                s.walkerName = name != null && name.length() > 40 ? name.substring(0, 40) : name;
            }
            if ("walker".equals(source)) {
                s.walkerConnected = true;
                s.walkerLastSeen = System.currentTimeMillis();
            }
            int lastLogged = s.log.isEmpty() ? -1 : (int) s.log.get(s.log.size() - 1)[1];
            if (s.log.isEmpty() || lastLogged != s.steps) {
                s.log.add(new long[]{s.updatedAt, s.steps});
                s.logSource.add(source);
                if (s.log.size() > 500) {
                    s.log.remove(0);
                    s.logSource.remove(0);
                }
            }
            if (s.steps >= s.target && COMPLETION_NOTIFIED.add(s.id)) {
                BreakNotifier.completed(s.id, s.steps);
            }
        }
    }

    private static String publicState(Session s) {
        boolean done = s.steps >= s.target;
        return "{\"id\":" + esc(s.id)
                + ",\"target\":" + s.target
                + ",\"steps\":" + s.steps
                + ",\"done\":" + done
                + ",\"createdAt\":" + s.createdAt
                + ",\"updatedAt\":" + s.updatedAt
                + ",\"walkerConnected\":" + s.walkerConnected
                + ",\"walkerLastSeen\":" + s.walkerLastSeen
                + ",\"walkerName\":" + esc(s.walkerName) + "}";
    }

    private static String logArray(Session s) {
        StringBuilder arr = new StringBuilder("[");
        synchronized (s) {
            for (int i = 0; i < s.log.size(); i++) {
                if (i > 0) {
                    arr.append(',');
                }
                arr.append("{\"t\":").append(s.log.get(i)[0])
                        .append(",\"steps\":").append((int) s.log.get(i)[1])
                        .append(",\"source\":").append(esc(s.logSource.get(i)))
                        .append('}');
            }
        }
        return arr.append(']').toString();
    }

    private static long uptimeSec() {
        return (System.currentTimeMillis() - STARTED) / 1000;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int parse(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---------------- static assets ----------------

    private static void serveStatic(HttpExchange ex, String path) throws IOException {
        String rel = path;
        if ("/".equals(rel) || "/index.html".equals(rel) || "/desktop".equals(rel)) {
            rel = "/index.html";
        } else if ("/walk".equals(rel) || "/mobile".equals(rel)) {
            rel = "/walk.html";
        } else if ("/favicon.ico".equals(rel)) {
            rel = "/icon.svg";
        }

        byte[] body;
        String type;
        switch (rel) {
            case "/index.html":
                body = injectBridge(readResource("/web/index.html"));
                type = "text/html; charset=utf-8";
                break;
            case "/walk.html":
                body = readResource("/web/walk.html");
                type = "text/html; charset=utf-8";
                break;
            case "/ide-bridge.js":
                body = IDE_BRIDGE_JS.getBytes(StandardCharsets.UTF_8);
                type = "application/javascript; charset=utf-8";
                break;
            case "/qrcode.min.js":
                body = readResource("/web/qrcode.min.js");
                type = "application/javascript; charset=utf-8";
                break;
            case "/icon.svg":
                body = readResource("/web/icon.svg");
                type = "image/svg+xml";
                break;
            case "/manifest.json":
                body = readResource("/web/manifest.json");
                type = "application/json";
                break;
            default:
                byte[] nf = "404 — not found".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(404, nf.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(nf);
                }
                return;
        }
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static byte[] readResource(String resPath) throws IOException {
        try (InputStream in = FitDeveloperServer.class.getResourceAsStream(resPath)) {
            if (in == null) {
                throw new IOException("missing bundled resource " + resPath);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    /** Injects the IDE bridge right before </head> of the dashboard page. */
    private static byte[] injectBridge(byte[] html) {
        String page = new String(html, StandardCharsets.UTF_8);
        String tag = "<script src=\"/ide-bridge.js\"></script>";
        if (page.contains("</head>")) {
            page = page.replace("</head>", tag + "</head>");
        }
        return page.getBytes(StandardCharsets.UTF_8);
    }

    // ---------------- misc ----------------

    /** Same preference order as server.js: 192.168.x > 10.x > anything else. */
    static String lanIP() {
        List<String> prefs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                try {
                    if (!ni.isUp() || ni.isLoopback()) {
                        continue;
                    }
                } catch (IOException e) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        prefs.add(a.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String best = null;
        int bestScore = -1;
        for (String ip : prefs) {
            int score = ip.startsWith("192.168.") ? 2 : ip.startsWith("10.") ? 1 : 0;
            if (score > bestScore) {
                best = ip;
                bestScore = score;
            }
        }
        return best != null ? best : "127.0.0.1";
    }

    static String esc(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.append('"').toString();
    }

    static String unescapeJSON(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char n = raw.charAt(++i);
                switch (n) {
                    case '"':
                        b.append('"');
                        break;
                    case '\\':
                        b.append('\\');
                        break;
                    case '/':
                        b.append('/');
                        break;
                    case 'n':
                        b.append('\n');
                        break;
                    case 't':
                        b.append('\t');
                        break;
                    case 'r':
                        b.append('\r');
                        break;
                    default:
                        b.append(n);
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static void sendJSON(HttpExchange ex, int code, String jsonOrNull) {
        try {
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            if (jsonOrNull == null) {
                ex.sendResponseHeaders(code, -1);
            } else {
                byte[] body = jsonOrNull.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(code, body.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static String readBody(HttpExchange ex) {
        try (InputStream in = ex.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1 && out.size() < 1_000_000) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Injected into the dashboard: feeds REAL IDE editor keystrokes into the
     * page's activity listeners (synthetic events) and shows a plugin badge.
     */
    private static final String IDE_BRIDGE_JS = """
            (function () {
              if (window.__CG_PLUGIN__) return;
              window.__CG_PLUGIN__ = true;
              var last = null;
              function poke(n) {
                n = Math.max(1, Math.min(12, n));
                for (var i = 0; i < n; i++) {
                  window.dispatchEvent(new KeyboardEvent('keydown'));
                  window.dispatchEvent(new MouseEvent('mousemove'));
                }
              }
              function poll() {
                fetch('/api/ide-activity')
                  .then(function (r) { return r.json(); })
                  .then(function (j) {
                    if (last !== null && j.keystrokes > last) poke(j.keystrokes - last);
                    last = j.keystrokes;
                    window.__cgEngine = j; /* feeds the dashboard break-countdown strip */
                    if (j.forcedSession && location.search.indexOf('s=' + j.forcedSession) === -1) {
                      location.href = '/?s=' + j.forcedSession;
                      return;
                    }
                    var b = document.getElementById('__cg_badge');
                    if (b) {
                      var t = 'IDE plugin mode - crunch ' + Math.round(j.crunch) + '%';
                      if (j.forcedSession) t = 'IDE plugin mode - BREAK OPEN, walk!';
                      else if (j.secondsToBreak != null) t = 'IDE plugin mode - break in ' + j.secondsToBreak + 's';
                      else if (j.engineOn === false) t = 'IDE plugin mode - engine paused';
                      b.textContent = t;
                    }
                    var sb = document.getElementById('sbState');
                    var ov = document.getElementById('overlay');
                    if (sb && ov && !ov.classList.contains('show')) {
                      if (j.forcedSession) sb.textContent = 'BREAK OPEN - walk to unlock!';
                      else if (j.secondsToBreak != null) sb.textContent = 'armed - forced break in ' + j.secondsToBreak + 's';
                      else if (j.engineOn === false) sb.textContent = 'engine paused';
                      else sb.textContent = 'watching your typing';
                    }
                  })
                  .catch(function () {});
              }
              setInterval(poll, 1000);
              document.addEventListener('DOMContentLoaded', function () {
                var b = document.createElement('div');
                b.id = '__cg_badge';
                b.textContent = 'IDE plugin mode - crunch 0%';
                b.style.cssText = 'position:fixed;bottom:6px;right:8px;z-index:9999;font:10px monospace;color:#22d3ee;background:#0b0f14;border:1px solid #1e2630;border-radius:99px;padding:3px 10px;opacity:.85;pointer-events:none';
                document.body.appendChild(b);
              });
            })();
            """;
}
