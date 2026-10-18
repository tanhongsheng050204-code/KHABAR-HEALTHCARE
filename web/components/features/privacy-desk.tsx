"use client"

import { useCallback, useEffect, useState } from "react"
import { FileWarning, ShieldCheck } from "lucide-react"
import { apiRequest } from "@/lib/api"
import type { PrivacyIncident, PrivacyRequest } from "@/lib/types"
import { SectionHeading, StatusBadge } from "@/components/ui"
import { featureMissing } from "./missing"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

type Notice = { tone: "error" | "success" | "info"; text: string }

const STEPS: { key: string; label: string; at: (i: PrivacyIncident) => string | null }[] = [
  { key: "CONTAINED", label: "Contained", at: (i) => i.containedAt },
  { key: "ASSESSED", label: "Assessed for harm", at: (i) => i.assessedAt },
  { key: "COMMISSIONER_NOTIFIED", label: "Personal Data Protection Commissioner notified", at: (i) => i.commissionerNotifiedAt },
  { key: "PEOPLE_NOTIFIED", label: "People affected notified", at: (i) => i.peopleNotifiedAt },
  { key: "CLOSED", label: "Closed", at: (i) => i.closedAt },
]

function when(at: string | null) {
  return at ? new Date(at).toLocaleString() : null
}

/** PDPA requests from patients, and the clinic's register of personal-data breaches. */
export function PrivacyDeskPanel({ notify }: { notify: (n: Notice) => void }) {
  const [requests, setRequests] = useState<PrivacyRequest[]>([])
  const [incidents, setIncidents] = useState<PrivacyIncident[]>([])
  const [answers, setAnswers] = useState<Record<string, string>>({})
  const [form, setForm] = useState({ title: "", whatHappened: "", peopleAffected: "" })
  const [busy, setBusy] = useState<string | null>(null)
  const [missing, setMissing] = useState(false)
  const load = useCallback(async () => {
    try {
      const [r, i] = await Promise.all([
        apiRequest<PrivacyRequest[]>("/api/clinic/privacy-requests"),
        apiRequest<PrivacyIncident[]>("/api/clinic/incidents"),
      ])
      setRequests(r)
      setIncidents(i)
    } catch (e) {
      if (featureMissing(e)) setMissing(true)
      else notify({ tone: "error", text: e instanceof Error ? e.message : "Privacy requests could not be loaded." })
    }
  }, [notify])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  async function act(label: string, work: () => Promise<void>) {
    setBusy(label)
    try { await work(); await load() } catch (e) { notify({ tone: "error", text: e instanceof Error ? e.message : "That did not work." }) } finally { setBusy(null) }
  }
  function decide(r: PrivacyRequest, status: "DONE" | "DECLINED", eraseIdentity: boolean) {
    void act(r.id, async () => {
      await apiRequest(`/api/clinic/privacy-requests/${r.id}/resolve`, { method: "POST", body: JSON.stringify({ status, resolution: answers[r.id]?.trim() ?? "", eraseIdentity }) })
      notify({ tone: "success", text: eraseIdentity ? "Identity erased. The clinical record is kept for the legal retention period." : "The request was decided and the patient can see the outcome." })
    })
  }
  function step(incident: PrivacyIncident, key: string) {
    let significantHarm: boolean | null = null
    if (key === "ASSESSED") {
      significantHarm = window.confirm("Is significant harm to the people affected likely? OK for yes, Cancel for no.")
    }
    void act(`${incident.id}-${key}`, async () => {
      await apiRequest(`/api/clinic/incidents/${incident.id}/steps`, { method: "POST", body: JSON.stringify({ step: key, significantHarm }) })
    })
  }
  function report(event: React.FormEvent) {
    event.preventDefault()
    void act("report", async () => {
      await apiRequest("/api/clinic/incidents", { method: "POST", body: JSON.stringify({ title: form.title, whatHappened: form.whatHappened, peopleAffected: form.peopleAffected === "" ? null : Number(form.peopleAffected) }) })
      setForm({ title: "", whatHappened: "", peopleAffected: "" })
      notify({ tone: "success", text: "Incident recorded. Work through the steps below." })
    })
  }
  const open = requests.filter((r) => r.status === "OPEN")
  if (missing) return null
  return (
    <section className={homeStyles.peopleCard} id="privacy-desk">
      <SectionHeading eyebrow="PDPA" title="Privacy requests and incidents" action={open.length ? <StatusBadge level="watch">{open.length} open</StatusBadge> : undefined} />
      <div className={styles.panel}>
        <p className={styles.lead}>Patients can download their data themselves. Requests to erase their identity or correct their details come here; tell the patient what you did. Erasing removes the name, IC, phone, Telegram link and sign-in, and keeps the clinical record for as long as medical records must be kept.</p>
        {requests.length === 0 && <p className={styles.muted}>No requests yet.</p>}
        <ul className={styles.log}>
          {requests.map((r) => <li key={r.id}>
            <div className={styles.rowBetween}><strong>{r.kind === "ERASURE" ? "Erase my identity" : "Correct my details"} · {r.patientName ?? "Patient"}</strong>
              <span className={styles.row}><StatusBadge level={r.status === "OPEN" ? "watch" : r.status === "DONE" ? "ok" : "review"}>{r.status.toLowerCase()}</StatusBadge><span className={styles.muted}>{when(r.createdAt)}</span></span></div>
            {r.details && <p className={styles.lead}>“{r.details}”</p>}
            {r.status === "OPEN" ? <>
              <label className={styles.field}>What you did, or why not (the patient sees this)
                <textarea rows={2} value={answers[r.id] ?? ""} onChange={(e) => setAnswers({ ...answers, [r.id]: e.target.value })} />
              </label>
              <div className={styles.row}>
                {r.kind === "ERASURE" && <button className="button-danger" disabled={busy === r.id || (answers[r.id]?.trim().length ?? 0) < 10} onClick={() => decide(r, "DONE", true)}>Erase identity</button>}
                <button className="button-secondary" disabled={busy === r.id || (answers[r.id]?.trim().length ?? 0) < 10} onClick={() => decide(r, "DONE", false)}>Mark done</button>
                <button className="button-quiet" disabled={busy === r.id || (answers[r.id]?.trim().length ?? 0) < 10} onClick={() => decide(r, "DECLINED", false)}>Decline</button>
              </div>
            </> : r.resolution && <span className={styles.muted}>{r.resolution}</span>}
          </li>)}
        </ul>

        <SectionHeading eyebrow="Breach register" title="Personal-data incidents" />
        <form className={styles.releaseForm} onSubmit={report}>
          <label className={styles.field}>What happened, in a few words<input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required maxLength={200} placeholder="e.g. Printed call list left at the front desk" /></label>
          <label className={styles.field}>Details (kinds of data, how it happened; no patient names)<textarea rows={2} value={form.whatHappened} onChange={(e) => setForm({ ...form, whatHappened: e.target.value })} /></label>
          <label className={styles.field}>People affected, if known<input type="number" min={0} value={form.peopleAffected} onChange={(e) => setForm({ ...form, peopleAffected: e.target.value })} /></label>
          <button className="button-secondary" disabled={busy === "report"}><FileWarning size={15} aria-hidden="true" /> Record an incident</button>
        </form>
        {incidents.length > 0 && <ul className={styles.log}>{incidents.map((i) => (
          <li key={i.id}>
            <div className={styles.rowBetween}><strong>{i.title}</strong><StatusBadge level={i.closedAt ? "ok" : "watch"}>{i.closedAt ? "closed" : "open"}</StatusBadge></div>
            <span className={styles.muted}>Discovered {when(i.discoveredAt)}{i.peopleAffected != null ? ` · ${i.peopleAffected} people affected` : ""}</span>
            <ul className={styles.steps}>
              {STEPS.filter((s) => s.key !== "PEOPLE_NOTIFIED" || !i.assessedAt || i.significantHarm).map((s) => {
                const done = when(s.at(i))
                return <li key={s.key}><span>{s.label}</span>{done ? <span className={styles.done}><ShieldCheck size={14} aria-hidden="true" /> {done}</span>
                  : !i.closedAt && <button className="button-quiet" disabled={busy === `${i.id}-${s.key}`} onClick={() => step(i, s.key)}>Mark done</button>}</li>
              })}
            </ul>
          </li>
        ))}</ul>}
      </div>
    </section>
  )
}
