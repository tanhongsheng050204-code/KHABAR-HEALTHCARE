"use client";

import { CheckCircle2, Send } from "lucide-react";
import styles from "@/app/home/home.module.css";

/** Points the patient to the clinic's Telegram bot; linking happens in Telegram by sharing their own number. */
export function TelegramCard({ linked }: { linked: boolean }) {
  const bot = process.env.NEXT_PUBLIC_TELEGRAM_BOT_USERNAME?.replace(/^@/, "");
  if (!bot) return null;
  if (linked) {
    return (
      <section className={styles.telegramCard} aria-label="Telegram">
        <CheckCircle2 size={19} />
        <div>
          <strong>Telegram connected</strong>
          <small>Your care plan and check-ins arrive in Telegram.</small>
        </div>
      </section>
    );
  }
  return (
    <section className={styles.telegramCard} aria-label="Telegram">
      <Send size={19} />
      <div>
        <strong>Get your care plan on Telegram</strong>
        <small>Open the Khabar bot, then tap “Share my phone number”.</small>
      </div>
      <a className="button-secondary" href={`https://t.me/${bot}`} target="_blank" rel="noopener noreferrer">
        Open Telegram
      </a>
    </section>
  );
}
