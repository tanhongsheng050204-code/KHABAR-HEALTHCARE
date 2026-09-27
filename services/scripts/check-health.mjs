#!/usr/bin/env node

const apiBaseUrl = process.argv[2] ?? "https://khabar-api.vercel.app";
const agentsHealthUrl =
  process.argv[3] ?? new URL("/agents/health", apiBaseUrl).toString();

const checks = [
  {
    name: "API",
    url: new URL("/api/health", apiBaseUrl).toString(),
    expectedStatus: "UP",
  },
  {
    name: "Agents",
    url: agentsHealthUrl,
    expectedStatus: "healthy",
  },
];

let failed = false;

for (const check of checks) {
  try {
    const response = await fetch(check.url, {
      signal: AbortSignal.timeout(15_000),
    });

    if (!response.ok) {
      console.error(`${check.name}: HTTP ${response.status} (${check.url})`);
      failed = true;
      continue;
    }

    const payload = await response.json();
    if (payload.status !== check.expectedStatus) {
      console.error(
        `${check.name}: unexpected health status (${check.url})`,
      );
      failed = true;
      continue;
    }

    console.log(`${check.name}: healthy (${check.url})`);
  } catch (error) {
    const detail = error instanceof Error ? error.message : "request failed";
    console.error(`${check.name}: ${detail} (${check.url})`);
    failed = true;
  }
}

if (failed) {
  process.exitCode = 1;
}
