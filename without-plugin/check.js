// CrunchGuard health checker — both HTTP (8787) and HTTPS (8788)
async function probe(url) {
  try {
    const r = await fetch(url, { signal: AbortSignal.timeout(4000) });
    const j = await r.json();
    return "UP   " + url + "  -> " + JSON.stringify(j);
  } catch (e) {
    return "DOWN " + url + "  -> " + (e && e.message ? e.message : e);
  }
}
(async () => {
  console.log(await probe("http://localhost:8787/api/health"));
  // self-signed: allow TLS failure to be reported as cert issue, not DOWN-crash
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
  console.log(await probe("https://localhost:8788/api/health"));
})();
