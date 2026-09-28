#!/usr/bin/env node
// Registers Khabar's Telegram webhook once. Asks for the bot token and webhook secret with hidden input
// and never prints them. The secret must equal KHABAR_TELEGRAM_WEBHOOK_SECRET on khabar-api.
//
// For first-time setup, telegram-setup.mjs is simpler: it makes the secret and sets it in Vercel too.
//
// Usage: node scripts/telegram-set-webhook.mjs [apiBaseUrl] [telegramApiBase]
import { describe, hidden } from "./hidden-input.mjs";

const apiBaseUrl = process.argv[2] ?? "https://khabar-api.vercel.app";
const telegram = process.argv[3] ?? "https://api.telegram.org";

// Returns the exit code rather than calling process.exit, which can crash Node on Windows while the
// terminal input is still closing.
async function main() {
  const token = await hidden("Telegram bot token (hidden): ");
  const secret = await hidden("Webhook secret, same as KHABAR_TELEGRAM_WEBHOOK_SECRET (hidden): ");
  if (!token || !secret) {
    console.error("Both values are needed.");
    return 1;
  }
  // BotFather's tokens look like 1234567890:AA... (the bot's number, a colon, then about 35 characters).
  if (!/^\d+:[A-Za-z0-9_-]{30,}$/.test(token)) {
    const hint = /^bot\d/i.test(token) ? " Leave out the leading \"bot\"." : "";
    console.error(`That does not look like a bot token (digits, a colon, then letters and digits); ${describe(token)}.${hint}`);
    return 1;
  }
  if (!/^[A-Za-z0-9_-]{1,256}$/.test(secret)) {
    console.error(`Telegram only accepts letters, digits, _ and - in the secret (1-256 characters); ${describe(secret)}.`);
    return 1;
  }

  const url = new URL("/api/webhooks/telegram", apiBaseUrl).toString();

  // Ask our own webhook first whether it accepts this secret, so a mismatch shows up here and not as a
  // silent bot. An update with no message is acknowledged and ignored. The first call may wait out a cold start.
  let check;
  try {
    check = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Telegram-Bot-Api-Secret-Token": secret },
      body: "{}",
      signal: AbortSignal.timeout(60_000),
    });
  } catch (error) {
    console.error(`Could not reach ${url} (${error.name}). Check the API is up, then try again.`);
    return 1;
  }
  if (check.status === 401) {
    console.error("The API refused this secret: it is not the KHABAR_TELEGRAM_WEBHOOK_SECRET of the running deployment.\n" +
      "Either the value in Vercel differs from the one typed here, or the API was not redeployed after changing it.");
    return 1;
  }
  if (check.status === 503) {
    console.error("The API has no KHABAR_TELEGRAM_WEBHOOK_SECRET yet. Set it in Vercel and redeploy.");
    return 1;
  }
  if (!check.ok) {
    console.error(`The API answered HTTP ${check.status} to a test delivery; not registering.`);
    return 1;
  }
  console.log("The API accepts this secret.");

  const call = async (method, body) => {
    const response = await fetch(`${telegram}/bot${token}/${method}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body ?? {}),
    });
    return response.json().catch(() => ({ ok: false, description: `HTTP ${response.status}` }));
  };

  const set = await call("setWebhook", { url, secret_token: secret, allowed_updates: ["message"], drop_pending_updates: true });
  console.log(set.ok ? `Webhook set: ${url}` : `Telegram refused: ${set.description}`);
  const info = await call("getWebhookInfo");
  if (info.ok) {
    console.log(`Telegram reports: url=${info.result.url} pending=${info.result.pending_update_count}` +
      (info.result.last_error_message ? ` last error=${info.result.last_error_message}` : ""));
  }
  return set.ok ? 0 : 1;
}

process.exitCode = await main();
