"use client"

import { useState } from "react"
import { ShieldAlert } from "lucide-react"
import { apiRequest } from "@/lib/api"
import type { SummaryStatus } from "@/lib/types"
import styles from "./features.module.css"

/**
 * A take-home plan the independent check held back. Nothing has been sent; the doctor reads the reason and
 * either fixes the visit or releases the plan with a written reason.
 */
export function HeldPlan({ encounterId, text, language, reason, onReleased }: {
  encounterId: string; text: string; language: string; reason: string; onReleased: (status: SummaryStatus) => void
}) {
  const [note, setNote] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  async function release(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError("")
    try {
      onReleased(await apiRequest<SummaryStatus>(`/api/encounters/${encounterId}/summary/release`, { method: "POST", body: JSON.stringify({ note: note.trim() }) }))
    } catch (e) {
      setError(e instanceof Error ? e.message : "The plan could not be released.")
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className={styles.banner} aria-labelledby="held-plan-title">
      <ShieldAlert size={20} aria-hidden="true" />
      <div className={styles.panelFlat}>
        <strong id="held-plan-title">The take-home plan was held, not sent</strong>
        <p>Khabar checks every medicine and number in the plan against your prescription. This one did not match, so the patient has not received it:</p>
        <p><em>{reason}</em></p>
        <pre className={styles.sent} lang={language}>{text}</pre>
        <form className={styles.releaseForm} onSubmit={(e) => void release(e)}>
          <label className={styles.field}>If the plan is right, say why it is safe to send (at least 10 characters)
            <textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. The dose was changed at this visit and the plan says so." />
          </label>
          {error && <p role="alert">{error}</p>}
          <button className="button-primary" disabled={busy || note.trim().length < 10}>{busy ? "Sending…" : "Release and send to the patient"}</button>
        </form>
        <p className={styles.muted}>Your reason is kept with the plan and the release is in the patient&apos;s access history.</p>
      </div>
    </section>
  )
}
