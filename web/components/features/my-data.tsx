"use client"

import { useCallback, useEffect, useState } from "react"
import { Download, Eraser, PencilLine } from "lucide-react"
import { apiRequest, apiRoot } from "@/lib/api"
import { getToken } from "@/lib/session"
import { useUi } from "@/lib/i18n"
import type { PrivacyRequest } from "@/lib/types"
import { SectionHeading, StatusBadge } from "@/components/ui"
import { featureMissing } from "./missing"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

type Notice = { tone: "error" | "success" | "info"; text: string }

/** The patient's PDPA rights: download everything, ask for erasure or correction, and see what the clinic decided. */
export function MyDataPanel({ notify }: { notify: (n: Notice) => void }) {
  const { t, locale } = useUi()
  const [requests, setRequests] = useState<PrivacyRequest[]>([])
  const [correction, setCorrection] = useState("")
  const [showCorrection, setShowCorrection] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [missing, setMissing] = useState(false)
  const load = useCallback(async () => {
    try { setRequests(await apiRequest<PrivacyRequest[]>("/api/me/privacy-requests")) }
    catch (e) { if (featureMissing(e)) setMissing(true); setRequests([]) }
  }, [])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  async function download() {
    setBusy("download")
    try {
      const response = await fetch(`${apiRoot}/api/me/export`, { headers: { Authorization: `Bearer ${getToken() ?? ""}` } })
      if (!response.ok) throw new Error(`(${response.status})`)
      const url = URL.createObjectURL(await response.blob())
      const link = document.createElement("a")
      link.href = url
      link.download = "khabar-my-data.json"
      link.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      notify({ tone: "error", text: `${t("Try again")} ${e instanceof Error ? e.message : ""}`.trim() })
    } finally {
      setBusy(null)
    }
  }
  async function ask(kind: "ERASURE" | "CORRECTION") {
    if (kind === "ERASURE" && !window.confirm(t("Erasing removes your name, IC, phone, Telegram link and sign-in. You will not be able to sign in afterwards. Send the request?"))) return
    setBusy(kind)
    try {
      await apiRequest("/api/me/privacy-requests", { method: "POST", body: JSON.stringify({ kind, details: kind === "CORRECTION" ? correction.trim() : null }) })
      setCorrection("")
      setShowCorrection(false)
      notify({ tone: "success", text: t("Your request was sent to your clinic.") })
      await load()
    } catch (e) {
      notify({ tone: "error", text: e instanceof Error ? e.message : t("Try again") })
    } finally {
      setBusy(null)
    }
  }
  const STATUS: Record<PrivacyRequest["status"], { level: string; word: string }> = {
    OPEN: { level: "watch", word: t("Waiting for your clinic") },
    DONE: { level: "ok", word: t("Done") },
    DECLINED: { level: "review", word: t("Declined") },
  }
  if (missing) return null
  return (
    <section className={homeStyles.peopleCard} aria-labelledby="my-data-title">
      <SectionHeading eyebrow={t("Your data")} title={t("Your rights over your data")} />
      <div className={styles.panel}>
        <p className={styles.lead} id="my-data-title">{t("Download everything Khabar holds about you, or ask your clinic to erase your identity or correct your details. Your clinic keeps medical records for as long as the law requires.")}</p>
        <div className={styles.row}>
          <button className="button-secondary" disabled={busy === "download"} onClick={() => void download()}><Download size={15} aria-hidden="true" /> {t("Download my data")}</button>
          <button className="button-secondary" onClick={() => setShowCorrection(!showCorrection)}><PencilLine size={15} aria-hidden="true" /> {t("Ask to correct my details")}</button>
          <button className="button-danger" disabled={busy === "ERASURE"} onClick={() => void ask("ERASURE")}><Eraser size={15} aria-hidden="true" /> {t("Ask to erase my identity")}</button>
        </div>
        {showCorrection && <form className={styles.releaseForm} onSubmit={(e) => { e.preventDefault(); void ask("CORRECTION") }}>
          <label className={styles.field}>{t("What needs correcting?")}
            <textarea rows={2} maxLength={2000} value={correction} onChange={(e) => setCorrection(e.target.value)} />
          </label>
          <button className="button-primary" disabled={busy === "CORRECTION" || !correction.trim()}>{t("Send request")}</button>
        </form>}
        {requests.length > 0 && <>
          <strong>{t("Your requests")}</strong>
          <ul className={styles.log}>
            {requests.map((r) => <li key={r.id}>
              <div className={styles.rowBetween}><span>{r.kind === "ERASURE" ? t("Erase my identity") : t("Correct my details")}</span>
                <StatusBadge level={STATUS[r.status].level}>{STATUS[r.status].word}</StatusBadge></div>
              <span className={styles.muted}>{new Intl.DateTimeFormat(locale, { dateStyle: "medium" }).format(new Date(r.createdAt))}</span>
              {r.resolution && <span>{r.resolution}</span>}
            </li>)}
          </ul>
        </>}
      </div>
    </section>
  )
}
