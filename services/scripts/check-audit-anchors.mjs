// Compares every anchor in audit-anchors/ with the live audit chain. An anchor is the chain's position and hash
// on one day; if the hash stored at that position today is different, the log was rewritten after that day.
//
//   node scripts/check-audit-anchors.mjs                       # the public demo API
//   node scripts/check-audit-anchors.mjs http://localhost:8080 # another API
//
// Exits nonzero when any anchor no longer matches.
import { readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const api = (process.argv[2] || "https://khabar-api.vercel.app").replace(/\/$/, "");
const dir = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "audit-anchors");

let files = [];
try {
  files = readdirSync(dir).filter((name) => name.endsWith(".json")).sort();
} catch {
  console.log(`No anchors yet (${dir} does not exist).`);
  process.exit(0);
}

let mismatches = 0;
for (const name of files) {
  const anchor = JSON.parse(readFileSync(join(dir, name), "utf8"));
  if (anchor.api && anchor.api.replace(/\/$/, "") !== api) {
    console.log(`${name}: recorded from ${anchor.api}, skipped`);
    continue;
  }
  if (!anchor.seq) {
    console.log(`${name}: empty chain, nothing to compare`);
    continue;
  }
  const response = await fetch(`${api}/api/audit/anchor/${anchor.seq}`);
  if (!response.ok) {
    mismatches++;
    console.log(`${name}: position ${anchor.seq} is missing now (HTTP ${response.status})`);
    continue;
  }
  const live = await response.json();
  if (live.hash === anchor.hash) {
    console.log(`${name}: position ${anchor.seq} matches`);
  } else {
    mismatches++;
    console.log(`${name}: position ${anchor.seq} DOES NOT MATCH (anchored ${anchor.hash}, now ${live.hash})`);
  }
}
if (mismatches > 0) {
  console.log(`${mismatches} anchor(s) no longer match the live chain.`);
  process.exit(1);
}
console.log(`All ${files.length} anchor(s) checked.`);
