#!/usr/bin/env node
// Before a live demo: wake the API and agents from a cold start, then refill the patient graph.
// The free AuraDB instance pauses after a few idle days, and graph writes made while it is paused
// are dropped (PatientGraphSync only logs them), so a demo can silently run on a stale graph.
//
// Usage: node scripts/warm-up.mjs [apiBaseUrl]   (default: the public demo API)
// Exits non-zero if anything is not ready. Uses the demo-only POST /dev/graph/sync, which exists
// only while the API runs with the `local` profile, as the public fictional-data demo does.

const apiBaseUrl = process.argv[2] ?? "https://khabar-api.vercel.app";
let failed = false;

async function getJson(url, init = {}, timeoutMs = 20_000) {
  const response = await fetch(url, { ...init, signal: AbortSignal.timeout(timeoutMs) });
  const body = await response.json().catch(() => null);
  return { status: response.status, body };
}

async function waitFor(name, url, expected, attempts = 5) {
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      const { status, body } = await getJson(url);
      if (status === 200 && body?.status === expected) {
        console.log(`${name}: ready (attempt ${attempt})`);
        return true;
      }
      console.log(`${name}: HTTP ${status}, retrying (${attempt}/${attempts})`);
    } catch (error) {
      const detail = error instanceof Error ? error.message : "request failed";
      console.log(`${name}: ${detail}, retrying (${attempt}/${attempts}); cold starts can take ~15 s`);
    }
  }
  console.error(`${name}: NOT READY after ${attempts} attempts (${url})`);
  failed = true;
  return false;
}

const apiReady = await waitFor("API", new URL("/api/health", apiBaseUrl), "UP");
await waitFor("Agents", new URL("/agents/health", apiBaseUrl), "healthy");

if (apiReady) {
  try {
    const { status, body } = await getJson(new URL("/dev/graph/sync", apiBaseUrl), { method: "POST" }, 120_000);
    if (status === 404) {
      console.log("Patient graph: skipped (no /dev/graph/sync; the API is not running the demo `local` profile)");
    } else if (status !== 200 || !body) {
      console.error(`Patient graph: sync returned HTTP ${status}`);
      failed = true;
    } else if (!body.enabled) {
      console.log("Patient graph: not configured on this API (NEO4J_URI unset); safety checks will show a graph warning");
    } else if (body.written === 0) {
      console.error(
        "Patient graph: 0 patients written. AuraDB is probably PAUSED. Resume the instance at " +
          "https://console.neo4j.io, wait until it shows RUNNING, then run this script again.",
      );
      failed = true;
    } else {
      console.log(`Patient graph: ready (${body.written} patients written)`);
    }
  } catch (error) {
    const detail = error instanceof Error ? error.message : "request failed";
    console.error(`Patient graph: sync failed (${detail})`);
    failed = true;
  }
}

console.log(failed ? "\nNOT READY for a demo. Fix the items above." : "\nReady for a demo.");
if (failed) process.exitCode = 1;
