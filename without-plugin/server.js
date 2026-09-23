#!/usr/bin/env node
/* ============================================================
 * FitDeveloper Relay Server — zero dependencies (Node built-ins)
 * Serves the desktop + mobile views and relays step progress.
 * Run:  node server.js          (port 8787)
 * ============================================================ */
const http = require("http");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const os = require("os");

const PORT = parseInt(process.env.PORT || "8787", 10);
const VERSION = "1.2.1";
const STARTED = Date.now();
const PUBLIC = path.join(__dirname, "public");
const SESSION_TTL = 2 * 60 * 60 * 1000; // 2h
const MAX_SESSIONS = 500;

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json",
  ".png": "image/png",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
};

/** @type {Map<string, any>} */
const sessions = new Map();

function newSession(target) {
  if (sessions.size >= MAX_SESSIONS) {
    let oldest = null, oldestT = Infinity;
    for (const s of sessions.values()) if (s.createdAt < oldestT) { oldestT = s.createdAt; oldest = s.id; }
    if (oldest) sessions.delete(oldest);
  }
  const id = crypto.randomUUID().replace(/-/g, "").slice(0, 10);
  const s = {
    id,
    target: Math.max(10, Math.min(5000, parseInt(target, 10) || 200)),
    steps: 0,
    done: false,
    createdAt: Date.now(),
    updatedAt: Date.now(),
    walkerConnected: false,
    walkerLastSeen: 0,
    walkerName: null,
    log: [], // [{t, steps, source}]
  };
  sessions.set(id, s);
  return s;
}

function publicState(s) {
  return {
    id: s.id,
    target: s.target,
    steps: s.steps,
    done: s.steps >= s.target,
    createdAt: s.createdAt,
    updatedAt: s.updatedAt,
    walkerConnected: s.walkerConnected,
    walkerLastSeen: s.walkerLastSeen,
    walkerName: s.walkerName,
  };
}

function getLanIP() {
  const prefs = [];
  for (const list of Object.values(os.networkInterfaces())) {
    for (const ni of list || []) {
      if (ni.family === "IPv4" && !ni.internal) prefs.push(ni.address);
    }
  }
  prefs.sort((a, b) =>
    (b.startsWith("192.168.") ? 1 : 0) - (a.startsWith("192.168.") ? 1 : 0) ||
    (b.startsWith("10.") ? 1 : 0) - (a.startsWith("10.") ? 1 : 0)
  );
  return prefs[0] || "127.0.0.1";
}

function sendJSON(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Cache-Control": "no-store",
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = "";
    req.on("data", (c) => {
      data += c;
      if (data.length > 1e6) req.destroy();
    });
    req.on("end", () => {
      try {
        resolve(data ? JSON.parse(data) : {});
      } catch {
        resolve({});
      }
    });
  });
}

function serveStatic(res, urlPath) {
  let rel = urlPath.split("?")[0];
  if (rel === "/" || rel === "/index.html" || rel === "/desktop") rel = "/index.html";
  if (rel === "/walk" || rel === "/mobile") rel = "/walk.html";
  if (rel === "/favicon.ico") rel = "/icon.svg";
  const file = path.normalize(path.join(PUBLIC, rel));
  if (!file.startsWith(PUBLIC)) return sendJSON(res, 403, { error: "forbidden" });
  fs.readFile(file, (err, buf) => {
    if (err) {
      res.writeHead(404, { "Content-Type": "text/plain" });
      return res.end("404 — not found");
    }
    res.writeHead(200, {
      "Content-Type": MIME[path.extname(file)] || "application/octet-stream",
      "Cache-Control": "no-store",
    });
    res.end(buf);
  });
}

const server = http.createServer(handler);

async function handler(req, res) {
  const url = new URL(req.url, "http://x");
  const p = url.pathname;

  if (req.method === "OPTIONS") return sendJSON(res, 204, {});

  // ---------- API ----------
  if (p === "/api/health") {
    return sendJSON(res, 200, {
      ok: true,
      service: "fitdeveloper",
      version: VERSION,
      uptimeSec: Math.floor((Date.now() - STARTED) / 1000),
      sessions: sessions.size,
      ts: Date.now(),
    });
  }

  if (p === "/api/config") {
    return sendJSON(res, 200, { lanIP: getLanIP(), port: PORT, httpsPort: httpsSrv ? PORT + 1 : null });
  }

  if (p === "/api/session" && req.method === "POST") {
    const body = await readBody(req);
    const s = newSession(body.target);
    console.log(`[session] created ${s.id} target=${s.target}`);
    return sendJSON(res, 200, publicState(s));
  }

  let m = p.match(/^\/api\/session\/([a-z0-9]+)$/i);
  if (m && req.method === "GET") {
    const s = sessions.get(m[1]);
    if (!s) return sendJSON(res, 404, { error: "session not found — scan the QR again" });
    return sendJSON(res, 200, publicState(s));
  }

  m = p.match(/^\/api\/session\/([a-z0-9]+)\/progress$/i);
  if (m && req.method === "POST") {
    const s = sessions.get(m[1]);
    if (!s) return sendJSON(res, 404, { error: "session not found" });
    const body = await readBody(req);
    const steps = parseInt(body.steps, 10);
    if (Number.isFinite(steps)) {
      s.steps = Math.max(s.steps, Math.min(100000, steps)); // monotonic
      s.updatedAt = Date.now();
      if (body.walkerName) s.walkerName = String(body.walkerName).slice(0, 40);
      if (body.source === "walker") {
        s.walkerConnected = true;
        s.walkerLastSeen = Date.now();
      }
      if (s.log.length === 0 || s.log[s.log.length - 1].steps !== s.steps) {
        s.log.push({ t: s.updatedAt, steps: s.steps, source: body.source || "?" });
        if (s.log.length > 500) s.log.shift();
      }
    }
    return sendJSON(res, 200, publicState(s));
  }

  m = p.match(/^\/api\/session\/([a-z0-9]+)\/log$/i);
  if (m && req.method === "GET") {
    const s = sessions.get(m[1]);
    if (!s) return sendJSON(res, 404, { error: "session not found" });
    return sendJSON(res, 200, { id: s.id, log: s.log });
  }

  if (p === "/api/sessions" && req.method === "GET") {
    return sendJSON(res, 200, { count: sessions.size, ids: [...sessions.keys()] });
  }

  // ---------- static ----------
  if (req.method === "GET") return serveStatic(res, p);
  return sendJSON(res, 405, { error: "method not allowed" });
}

// HTTPS listener (self-signed) — required for iOS motion sensors
let httpsSrv = null;
try {
  const https = require("https");
  const cert = fs.readFileSync(path.join(__dirname, "cert.pem"));
  const key = fs.readFileSync(path.join(__dirname, "key.pem"));
  httpsSrv = https.createServer({ cert, key }, handler);
  httpsSrv.listen(PORT + 1, "0.0.0.0", () => {
    console.log(`  🔒 HTTPS listener on https://0.0.0.0:${PORT + 1} (iOS motion needs this)`);
  });
  httpsSrv.on("error", (e) => { console.log("  HTTPS disabled: " + e.message); httpsSrv = null; });
} catch (e) {
  console.log("  HTTPS disabled (no cert.pem/key.pem): " + e.message);
}

// expire old sessions
setInterval(() => {
  const now = Date.now();
  for (const [id, s] of sessions) if (now - s.createdAt > SESSION_TTL) sessions.delete(id);
}, 60 * 1000);

server.on("error", (e) => {
  if (e.code === "EADDRINUSE") {
    console.error(`\n  ⚠ Port ${PORT} is already in use — FitDeveloper is probably already running.`);
    console.error(`    Open http://localhost:${PORT}/ in your browser, or run restart_server.bat.`);
    process.exit(1);
  }
  console.error("  Server error: " + e.message);
});

server.listen(PORT, "0.0.0.0", () => {
  const ip = getLanIP();
  console.log("");
  console.log("  ==============================================================");
  console.log(`   ⚔  FITDEVELOPER v${VERSION} — walk to unlock your IDE`);
  console.log("  ==============================================================");
  console.log(`   Desktop : http://localhost:${PORT}/`);
  console.log(`   Phone   : scan the QR shown when a break triggers`);
  console.log(`             (direct link: http://${ip}:${PORT}/walk)`);
  if (httpsSrv) {
    console.log(`   iOS     : https://${ip}:${PORT + 1}/walk  (accept cert warning)`);
  } else {
    console.log("   iOS     : HTTPS disabled (cert.pem/key.pem missing) —" +
      " Android still works, iOS walkers need the cert files");
  }
  console.log("   NOTE    : phone must be on the SAME Wi-Fi as this PC.");
  console.log("             Phone cannot connect? Run firewall_fix.bat (admin).");
  console.log("  ==============================================================");
  console.log("");
});
