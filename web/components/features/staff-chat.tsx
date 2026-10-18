"use client"

import { useCallback, useEffect, useState } from "react"
import { BellRing, Copy, Send, Sunrise } from "lucide-react"
import { apiRequest } from "@/lib/api"
import type { StaffAlertLog, StaffChannel, StaffLinkCode } from "@/lib/types"
import { SectionHeading, StatusBadge } from "@/components/ui"
import { featureMissing } from "./missing"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

type Notice = { tone: "error" | "success" | "info"; text: string }

const OUTCOME: Record<StaffAlertLog["outcome"], { level: string; word: string }> = {
  SENT: { level: "ok", word: "sent" },
  NOT_SENT: { level: "watch", word: "recorded only" },
  FAILED: { level: "red", word: "failed" },
}
const KIND: Record<StaffAlertLog["kind"], string> = { ALERT: "Alert", BRIEFING: "Morning briefing", TEST: "Test" }

/**
 * The clinic's staff chat: link a Telegram group with a one-time code, switch on counts-only alerts and the
 * morning briefing, send a test, and read every message sent (or only recorded) so far.
 */
export function StaffChatPanel({ notify, canManage }: { notify: (n: Notice) => void; canManage: boolean }) {
  const [channel, setChannel] = useState<StaffChannel | null>(null)
  const [code, setCode] = useState<StaffLinkCode | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const load = useCallback(async () => {
    try { setChannel(await apiRequest<StaffChannel>("/api/clinic/staff-chat")) }
    catch (e) { if (!featureMissing(e)) notify({ tone: "error", text: e instanceof Error ? e.message : "The staff chat could not be loaded." }) }
  }, [notify])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  async function act(label: string, work: () => Promise<void>) {
    setBusy(label)
    try { await work() } catch (e) { notify({ tone: "error", text: e instanceof Error ? e.message : "That did not work." }) } finally { setBusy(null) }
  }
  const save = (alertsOn: boolean, briefingHour: number | null) => act("save", async () => {
    setChannel(await apiRequest<StaffChannel>("/api/clinic/staff-chat", { method: "PUT", body: JSON.stringify({ alertsOn, briefingHour }) }))
    notify({ tone: "success", text: alertsOn ? "Staff alerts are on." : "Staff alerts are off." })
  })

  if (!channel) return null
  return (
    <section className={homeStyles.peopleCard} id="staff-chat">
      <SectionHeading eyebrow="Staff chat" title="Alerts and the morning briefing" action={<StatusBadge level={channel.alertsOn ? "ok" : "review"}>{channel.alertsOn ? "on" : "off"}</StatusBadge>} />
      <div className={styles.panel}>
        <p className={styles.lead}>Khabar can tell your team&apos;s Telegram group when patients are waiting: how many, how many are urgent, and how many are past your acknowledgement time, with a link to the call list. It never sends a patient&apos;s name or words.</p>
        {!channel.telegramConfigured && <p className={styles.muted}>Telegram is not configured on this server, so messages are recorded below but not delivered.</p>}

        <div className={styles.rowBetween}>
          <span>{channel.linked ? <>Staff chat linked{channel.linkedAt ? ` on ${new Date(channel.linkedAt).toLocaleDateString()}` : ""}.</> : "No staff chat linked yet."}</span>
          {canManage && <div className={styles.row}>
            {channel.linked && <button className="button-quiet" disabled={busy === "unlink"} onClick={() => void act("unlink", async () => { setChannel(await apiRequest<StaffChannel>("/api/clinic/staff-chat", { method: "DELETE" })) })}>Unlink</button>}
            <button className="button-secondary" disabled={busy === "code"} onClick={() => void act("code", async () => { setCode(await apiRequest<StaffLinkCode>("/api/clinic/staff-chat/link-code", { method: "POST" })) })}>
              {channel.linked ? "Link a different chat" : "Link a staff chat"}
            </button>
          </div>}
        </div>
        {code && <div className={styles.code}>
          <span>Add the Khabar bot to your staff group, then send:</span>
          <code>{code.command}</code>
          <button className="button-quiet" onClick={() => void navigator.clipboard.writeText(code.command)}><Copy size={14} aria-hidden="true" /> Copy</button>
          <span className={styles.muted}>Valid until {new Date(code.expiresAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}. Then refresh this page.</span>
        </div>}

        {canManage && <div className={styles.row}>
          <label className={styles.switch}><input type="checkbox" checked={channel.alertsOn} disabled={busy === "save"} onChange={(e) => void save(e.target.checked, channel.briefingHour)} />Send staff alerts</label>
          <label className={styles.field}>Morning briefing
            <select value={channel.briefingHour ?? ""} disabled={busy === "save"} onChange={(e) => void save(channel.alertsOn, e.target.value === "" ? null : Number(e.target.value))}>
              <option value="">No briefing</option>
              {[6, 7, 8, 9, 10].map((h) => <option key={h} value={h}>{h}:00</option>)}
            </select>
          </label>
        </div>}
        {canManage && <div className={styles.row}>
          <button className="button-secondary" disabled={busy === "test"} onClick={() => void act("test", async () => {
            const sent = await apiRequest<StaffAlertLog>("/api/clinic/staff-chat/test", { method: "POST" })
            notify({ tone: sent.outcome === "SENT" ? "success" : "info", text: sent.outcome === "SENT" ? "Test sent to the staff chat." : `Test recorded only: ${sent.detail}` })
            await load()
          })}><Send size={15} aria-hidden="true" /> Send a test</button>
          <button className="button-secondary" disabled={busy === "preview"} onClick={() => void act("preview", async () => { setPreview((await apiRequest<{ text: string }>("/api/clinic/staff-chat/briefing")).text) })}><Sunrise size={15} aria-hidden="true" /> Preview today&apos;s briefing</button>
        </div>}
        {preview && <pre className={styles.sent}>{preview}</pre>}

        <SectionHeading eyebrow="Log" title="What was sent" />
        {channel.recent.length === 0 ? <p className={styles.muted}>Nothing yet.</p> : <ul className={styles.log}>
          {channel.recent.map((l, i) => <li key={`${l.at}-${i}`}>
            <div className={styles.rowBetween}><strong><BellRing size={14} aria-hidden="true" /> {KIND[l.kind]}</strong>
              <span className={styles.row}><StatusBadge level={OUTCOME[l.outcome].level}>{OUTCOME[l.outcome].word}</StatusBadge><span className={styles.muted}>{new Date(l.at).toLocaleString()}</span></span></div>
            <pre>{l.text}</pre>
            {l.detail && <span className={styles.muted}>{l.detail}</span>}
          </li>)}
        </ul>}
      </div>
    </section>
  )
}
