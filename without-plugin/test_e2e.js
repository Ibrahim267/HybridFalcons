// CrunchGuard end-to-end test — runs on the laptop against localhost:8787
const BASE = "http://localhost:8787";
let pass = 0, fail = 0;

function ok(name, cond, extra) {
  if (cond) { pass++; console.log("  PASS  " + name); }
  else { fail++; console.log("  FAIL  " + name + (extra ? "  -> " + extra : "")); }
}

(async () => {
  // 1. health (now reports version + uptime)
  let r = await fetch(BASE + "/api/health");
  let j = await r.json();
  ok("health ok:true", j.ok === true, JSON.stringify(j));
  ok("health reports version 1.2.x", /^1\.2\./.test(j.version || ""), j.version);
  ok("health reports uptimeSec", typeof j.uptimeSec === "number");

  // 2. config (LAN IP)
  r = await fetch(BASE + "/api/config");
  j = await r.json();
  ok("config lanIP is LAN (not 127.x)", /^192\.168\.|^10\.|^172\./.test(j.lanIP), j.lanIP);
  console.log("  lanIP = " + j.lanIP + ":" + j.port);

  // 3. create session
  r = await fetch(BASE + "/api/session", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ target: 50 })
  });
  j = await r.json();
  const sid = j.id;
  ok("session created target=50", r.status === 200 && j.target === 50 && !!sid, JSON.stringify(j));

  // 4. static index
  r = await fetch(BASE + "/");
  const html = await r.text();
  ok("index.html served", r.status === 200 && html.includes("CrunchGuard"), "status " + r.status);
  ok("index references qrcode lib", html.includes("qrcode.min.js"));

  // 5. walk route
  r = await fetch(BASE + "/walk?s=" + sid);
  const walk = await r.text();
  ok("walk.html served via /walk", r.status === 200 && walk.includes("Walker"), "status " + r.status);

  // 6. progress halfway
  r = await fetch(BASE + "/api/session/" + sid + "/progress", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ steps: 25, source: "walker", walkerName: "Test Phone" })
  });
  j = await r.json();
  ok("progress 25/50, not done", j.steps === 25 && j.done === false, JSON.stringify(j));
  ok("walker connected flag", j.walkerConnected === true);

  // 7. monotonic (lower steps ignored)
  r = await fetch(BASE + "/api/session/" + sid + "/progress", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ steps: 10, source: "walker" })
  });
  j = await r.json();
  ok("monotonic steps (10 ignored, stays 25)", j.steps === 25, "steps=" + j.steps);

  // 8. reach target -> done
  r = await fetch(BASE + "/api/session/" + sid + "/progress", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ steps: 50, source: "walker" })
  });
  j = await r.json();
  ok("target reached -> done:true", j.steps === 50 && j.done === true, JSON.stringify(j));

  // 9. bad session 404
  r = await fetch(BASE + "/api/session/nope123");
  ok("bad session -> 404", r.status === 404);

  // 10. favicon (browsers hit /favicon.ico — must serve the SVG icon)
  r = await fetch(BASE + "/favicon.ico");
  const fav = await r.text();
  ok("favicon.ico serves icon.svg", r.status === 200 && fav.includes("<svg"), "status " + r.status);

  // 11. PWA manifest
  r = await fetch(BASE + "/manifest.json");
  j = await r.json();
  ok("manifest.json served", r.status === 200 && j.name === "CrunchGuard Walker", JSON.stringify(j).slice(0, 80));

  // 12. CORS preflight
  r = await fetch(BASE + "/api/session", { method: "OPTIONS" });
  ok("OPTIONS preflight -> 204 + CORS", r.status === 204 && r.headers.get("access-control-allow-origin") === "*", "status " + r.status);

  // 13. session cap sanity: create 3 more, all valid
  let allOk = true;
  for (let i = 0; i < 3; i++) {
    const rr = await fetch(BASE + "/api/session", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ target: 20 })
    });
    const jj = await rr.json();
    if (rr.status !== 200 || !jj.id) allOk = false;
  }
  ok("rapid session creation (cap logic stable)", allOk);

  console.log("\n  RESULT: " + pass + " passed, " + fail + " failed");
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error("TEST CRASH:", e.message); process.exit(2); });
