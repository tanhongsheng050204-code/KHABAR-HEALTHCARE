#!/usr/bin/env node
// Registers Khabar's Telegram webhook once. Asks for the bot token and webhook secret with hidden input
// and never prints them. The secret must equal KHABAR_TELEGRAM_WEBHOOK_SECRET on khabar-api.
//
// Usage: node scripts/telegram-set-webhook.mjs [apiBaseUrl] [telegramApiBase]
const apiBaseUrl = process.argv[2] ?? "https://khabar-api.vercel.app";
const telegram = process.argv[3] ?? "https://api.telegram.org";

// Typed characters not yet consumed: piped input can bring both answers in one chunk.
let pending = "";
let lastWasCr = false;

function hidden(question) {
  return new Promise((resolve) => {
    process.stdout.write(question);
    const stdin = process.stdin;
    let value = "";
    const take = (text) => {
      for (let i = 0; i < text.length; i++) {
        const ch = text[i];
        if (ch === "\n" && lastWasCr) {
          lastWasCr = false;
          continue;
        }
        lastWasCr = ch === "\r";
        if (ch === "\r" || ch === "\n") {
          pending = text.slice(i + 1);
          return true;
        }
        if (ch === "\u0003") process.exit(1);
        if (ch === "\u007f" || ch === "\b") value = value.slice(0, -1);
        else value += ch;
      }
      pending = "";
      return false;
    };
    const finish = () => {
      stdin.setRawMode?.(false);
      stdin.pause();
      stdin.off("data", onData);
      process.stdout.write("\n");
      resolve(value.trim());
    };
    const onData = (chunk) => {
      if (take(chunk)) finish();
    };
    if (take(pending)) {
      process.stdout.write("\n");
      resolve(value.trim());
      return;
    }
    stdin.setRawMode?.(true);
    stdin.setEncoding("utf8");
    stdin.resume();
    stdin.on("data", onData);
  });
}

const token = await hidden("Telegram bot token (hidden): ");
const secret = await hidden("Webhook secret, same as KHABAR_TELEGRAM_WEBHOOK_SECRET (hidden): ");
if (!token || !secret) {
  console.error("Both values are needed.");
  process.exit(1);
}
if (!/^[A-Za-z0-9_-]{1,256}$/.test(secret)) {
  console.error("Telegram only accepts letters, digits, _ and - in the secret (1-256 characters).");
  process.exit(1);
}

const url = new URL("/api/webhooks/telegram", apiBaseUrl).toString();
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
if (!set.ok) process.exitCode = 1;
