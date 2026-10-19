"use client"

import { useCallback, useEffect, useState } from "react"
import { BellRing, Copy, Send } from "lucide-react"
import { apiRequest } from "@/lib/api"
import { useUi } from "@/lib/i18n"
import type { CaregiverTelegram, StaffLinkCode } from "@/lib/types"
import { SectionHeading, StatusBadge } from "@/components/ui"
import { featureMissing } from "./missing"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

type Notice = { tone: "error" | "success" | "info"; text: string }

/**
 * A caregiver links their own Telegram chat for alerts about patients who share alerts with them: a quiet patient,
 * missed doses, or a message that may be urgent. Alerts never quote the patient.
 */
export function CaregiverTelegramPanel({ notify, names }: { notify: (n: Notice) => void; names: Record<string, string> }) {
  const { t, locale } = useUi()
  const [status, setStatus] = useState<CaregiverTelegram | null>(null)
  const [code, setCode] = useState<StaffLinkCode | null>(null)
  const [busy, setBusy] = useState(false)
  const load = useCallback(async () => {
    try { setStatus(await apiRequest<CaregiverTelegram>("/api/me/caregiver-telegram")) }
    catch (e) { if (!featureMissing(e)) notify({ tone: "error", text: e instanceof Error ? e.message : t("Try again") }) }
  }, [notify, t])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  async function act(work: () => Promise<void>) {
    setBusy(true)
    try { await work() } catch (e) { notify({ tone: "error", text: e instanceof Error ? e.message : t("Try again") }) } finally { setBusy(false) }
  }
  const KIND: Record<string, string> = {
    QUIET: t("Has not answered for 2 days"),
    MISSED_DOSES: t("Reported missed medicine"),
    URGENT_REPLY: t("Sent a message that may be urgent"),
  }

  if (!status) return null
  return (
    <section className={homeStyles.peopleCard} aria-labelledby="caregiver-telegram-title">
      <SectionHeading eyebrow={t("Alerts")} title={t("Alerts on Telegram")}
        action={<StatusBadge level={status.linked ? "ok" : "review"}>{status.linked ? t("Linked") : t("Not linked")}</StatusBadge>} />
      <div className={styles.panel}>
        <p className={styles.lead} id="caregiver-telegram-title">{t("Get a short Telegram message when someone who shares alerts with you goes quiet, misses medicine, or sends a message that may be urgent. Their words are never quoted.")}</p>
        {!status.telegramConfigured && <p className={styles.muted}>{t("Telegram is not set up on this server yet.")}</p>}
        <div className={styles.row}>
          <button className="button-secondary" disabled={busy} onClick={() => void act(async () => { setCode(await apiRequest<StaffLinkCode>("/api/me/caregiver-telegram/link-code", { method: "POST" })) })}>
            <Send size={15} aria-hidden="true" /> {status.linked ? t("Link a different chat") : t("Connect Telegram")}
          </button>
          {status.linked && <button className="button-quiet" disabled={busy} onClick={() => void act(async () => { setStatus(await apiRequest<CaregiverTelegram>("/api/me/caregiver-telegram", { method: "DELETE" })) })}>{t("Stop alerts")}</button>}
        </div>
        {code && <div className={styles.code}>
          <span>{t("Open the Khabar bot in Telegram and send:")}</span>
          <code>{code.command}</code>
          <button className="button-quiet" onClick={() => void navigator.clipboard.writeText(code.command)}><Copy size={14} aria-hidden="true" /> {t("Copy")}</button>
          <span className={styles.muted}>{t("The code works for 30 minutes.")}</span>
        </div>}
        {status.recent.length > 0 && <ul className={styles.log}>
          {status.recent.slice(0, 5).map((n, i) => <li key={`${n.sentAt}-${i}`}>
            <div className={styles.rowBetween}>
              <span><BellRing size={14} aria-hidden="true" /> {names[n.patientId] ?? ""} · {KIND[n.kind] ?? n.kind}</span>
              <span className={styles.muted}>{new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(new Date(n.sentAt))}</span>
            </div>
          </li>)}
        </ul>}
      </div>
    </section>
  )
}
