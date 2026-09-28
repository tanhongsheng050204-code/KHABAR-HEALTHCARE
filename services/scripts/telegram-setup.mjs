#!/usr/bin/env node
// One-step Telegram setup for khabar-api. Asks for the bot token once (hidden) and checks it with Telegram,
// makes a fresh webhook secret, stores both in Vercel as secrets, deploys the API to production, waits until
// the new deployment accepts the secret, then registers the webhook. Neither value is printed or written to
// disk, and both reach the Vercel CLI on stdin rather than on the command line. Running it again replaces
// the secret, so nobody ever needs to copy it.
//
// Usage (from services/): node scripts/telegram-setup.mjs [apiBaseUrl] [telegramApiBase]
import { spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, hidden } from "./hidden-input.mjs";

const apiBaseUrl = process.argv[2] ?? "https://khabar-api.vercel.app";
const telegram = process.argv[3] ?? "https://api.telegram.org";
const servicesDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const webhookUrl = new URL("/api/webhooks/telegram", apiBaseUrl).toString();

/**
 * Runs the Vercel CLI in services/ and resolves with its exit code; `input` goes to its stdin. The arguments
 * are fixed words from this file, never user input, so passing them through the shell is safe (npx is a
 * .cmd file on Windows, which needs one).
 */
function vercel(args, input) {
  return new Promise((done) => {
    const child = spawn(`npx vercel ${args.join(" ")}`, {
      cwd: servicesDir,
      shell: true,
      stdio: [input === undefined ? "inherit" : "pipe", "inherit", "inherit"],
    });
    if (input !== undefined) child.stdin.end(input);
    child.on("error", () => done(1));
    child.on("close", (code) => done(code ?? 1));
  });
}

/**
 * Calls the Bot API, retrying network failures: after the minutes-long deploy, the connection kept open
 * from the first call can be dead, and the next request fails once before a fresh one succeeds.
 */
async function botCall(token, method, body) {
  let failure = "";
  for (let attempt = 1; attempt <= 4; attempt++) {
    try {
      const response = await fetch(`${telegram}/bot${token}/${method}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body ?? {}),
        signal: AbortSignal.timeout(30_000),
      });
      return await response.json().catch(() => ({ ok: false, description: `HTTP ${response.status}` }));
    } catch (error) {
      // The error's code (e.g. ECONNRESET) says what went wrong; its message could contain the URL, so it is not shown.
      failure = `${error.name}${error.cause?.code ? ` ${error.cause.code}` : ""}`;
      await sleep(attempt * 2_000);
    }
  }
  return { ok: false, description: `Telegram not reachable after 4 tries (${failure})` };
}

/** The status our webhook gives a test delivery carrying this secret; 0 if it could not be reached. */
async function webhookStatus(secret) {
  try {
    const response = await fetch(webhookUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Telegram-Bot-Api-Secret-Token": secret },
      body: "{}",
      signal: AbortSignal.timeout(60_000),
    });
    return response.status;
  } catch {
    return 0;
  }
}

const sleep = (ms) => new Promise((done) => setTimeout(done, ms));

// Returns the exit code rather than calling process.exit, which can crash Node on Windows while the
// terminal input is still closing.
async function main() {
  const token = await hidden("Telegram bot token from @BotFather (hidden): ");
  if (!/^\d+:[A-Za-z0-9_-]{30,}$/.test(token)) {
    const hint = /^bot\d/i.test(token) ? " Leave out the leading \"bot\"." : "";
    console.error(`That does not look like a bot token (digits, a colon, then letters and digits); ${describe(token)}.${hint}`);
    return 1;
  }
  const me = await botCall(token, "getMe");
  if (!me.ok) {
    console.error(`Telegram did not accept this token (${me.description}). Copy it again: @BotFather, /mybots, your bot, API Token.`);
    return 1;
  }
  console.log(`1/5 Token works for @${me.result.username}.`);

  const secret = randomBytes(32).toString("hex");
  console.log("2/5 Storing the token and a new webhook secret in Vercel (production, Secret)...");
  for (const [name, value] of [["KHABAR_TELEGRAM_BOT_TOKEN", token], ["KHABAR_TELEGRAM_WEBHOOK_SECRET", secret]]) {
    const code = await vercel(["env", "add", name, "production", "--force", "--sensitive", "--yes"], value);
    if (code !== 0) {
      console.error(`Could not store ${name} in Vercel (exit ${code}). Check \`npx vercel whoami\` and that services/ is linked to khabar-api.`);
      return 1;
    }
  }

  console.log("3/5 Deploying the API to production (a few minutes)...");
  if ((await vercel(["deploy", "--prod"])) !== 0) {
    console.error("The deployment failed; nothing was registered with Telegram. Read the output above, then run this again.");
    return 1;
  }

  console.log("4/5 Waiting for the new deployment to accept the secret...");
  let status = 0;
  for (let attempt = 0; attempt < 20; attempt++) {
    status = await webhookStatus(secret);
    if (status === 200) break;
    await sleep(10_000);
  }
  if (status !== 200) {
    console.error(`The API still answers ${status || "nothing"} to a test delivery with the new secret, so the webhook was not registered.`);
    return 1;
  }

  console.log("5/5 Registering the webhook with Telegram...");
  const set = await botCall(token, "setWebhook", {
    url: webhookUrl, secret_token: secret, allowed_updates: ["message"], drop_pending_updates: true,
  });
  if (!set.ok) {
    console.error(`Telegram refused the webhook: ${set.description}`);
    return 1;
  }
  const info = await botCall(token, "getWebhookInfo");
  console.log(`Done. Telegram reports: url=${info.result?.url} pending=${info.result?.pending_update_count}` +
    (info.result?.last_error_message ? ` last error=${info.result.last_error_message}` : ""));
  console.log(`Open https://t.me/${me.result.username} and press Start.`);
  return 0;
}

process.exitCode = await main();
