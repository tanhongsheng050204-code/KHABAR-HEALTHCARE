"use client"

import { useCallback, useEffect, useState } from "react"
import { Activity, CalendarClock, PlugZap } from "lucide-react"
import { apiRequest } from "@/lib/api"
import type { Assignee, ClinicActivity, ClinicSettings, Integration } from "@/lib/types"
import { SectionHeading, StatusBadge } from "@/components/ui"
import homeStyles from "@/app/home/home.module.css"
import styles from "./case-controls.module.css"
import { CoverageBanner } from "./case-controls"

type Notice = { tone: "error" | "success" | "info"; text: string }

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const
const DAY_WORDS: Record<string, string> = {
  MONDAY: "Monday", TUESDAY: "Tuesday", WEDNESDAY: "Wednesday", THURSDAY: "Thursday", FRIDAY: "Friday", SATURDAY: "Saturday", SUNDAY: "Sunday",
}
const ACTION_WORDS: Record<string, string> = {
  STAFF_INVITED: "invited staff",
  STAFF_REVOKED: "removed access",
  SETTINGS_UPDATED: "changed settings",
  CASE_ASSIGNED: "assigned a case",
  CASE_ACKNOWLEDGED: "acknowledged a case",
  CASE_CONTACT_ATTEMPT: "recorded a call",
  CASE_ESCALATED: "escalated a case",
  CASE_CLOSED: "closed a case",
}
const INTEGRATION_LEVEL: Record<string, string> = { OK: "ok", LIMITED: "watch", OFF: "review", DOWN: "red" }

type RotaRow = { primaryUserId: string; backupUserId: string }

/** Clinic hours, escalation contact, acknowledgement times and the weekly follow-up rota. */
export function ClinicSettingsPanel({ notify }: { notify: (n: Notice | null) => void }) {
  const [settings, setSettings] = useState<ClinicSettings | null>(null)
  const [assignees, setAssignees] = useState<Assignee[]>([])
  const [hours, setHours] = useState("")
  const [contact, setContact] = useState("")
  const [red, setRed] = useState(30)
  const [watch, setWatch] = useState(240)
  const [review, setReview] = useState(1440)
  const [rota, setRota] = useState<Record<string, RotaRow>>({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")

  const load = useCallback(async () => {
    try {
      const [s, a] = await Promise.all([
        apiRequest<ClinicSettings>("/api/clinic/settings"),
        apiRequest<Assignee[]>("/api/clinic/cases/assignees").catch(() => [] as Assignee[]),
      ])
      setSettings(s)
      setAssignees(a)
      setHours(s.hours ?? "")
      setContact(s.escalationContact ?? "")
      setRed(s.redAckMinutes)
      setWatch(s.watchAckMinutes)
      setReview(s.reviewAckMinutes)
      setRota(Object.fromEntries(s.rota.map((r) => [r.day, { primaryUserId: r.primaryUserId, backupUserId: r.backupUserId ?? "" }])))
    } catch (e) {
      setError(e instanceof Error ? e.message : "Clinic settings could not be loaded.")
    }
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  async function save(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError("")
    try {
      const saved = await apiRequest<ClinicSettings>("/api/clinic/settings", {
        method: "PUT",
        body: JSON.stringify({
          hours, escalationContact: contact, redAckMinutes: red, watchAckMinutes: watch, reviewAckMinutes: review,
          rota: DAYS.filter((d) => rota[d]?.primaryUserId).map((d) => ({ day: d, primaryUserId: rota[d].primaryUserId, backupUserId: rota[d].backupUserId || null })),
        }),
      })
      setSettings(saved)
      notify({ tone: "success", text: "Clinic settings and rota saved." })
    } catch (e) {
      setError(e instanceof Error ? e.message : "Settings could not be saved.")
    } finally {
      setBusy(false)
    }
  }

  function setDay(day: string, field: keyof RotaRow, value: string) {
    setRota((all) => ({ ...all, [day]: { ...(all[day] ?? { primaryUserId: "", backupUserId: "" }), [field]: value } }))
  }

  return (
    <section className={homeStyles.peopleCard}>
      <SectionHeading eyebrow="Follow-up operations" title="Clinic settings and rota" />
      {settings && <CoverageBanner coverage={settings.today} />}
      {error && <p role="alert" className={styles.dialogError}>{error}</p>}
      <form className={homeStyles.staffInvite} onSubmit={(e) => void save(e)} style={{ display: "grid", gap: 12 }}>
        <label>Clinic hours<input value={hours} maxLength={200} onChange={(e) => setHours(e.target.value)} placeholder="Mon–Fri 9am–5pm, Sat 9am–12pm" /></label>
        <label>Escalation contact<input value={contact} maxLength={200} onChange={(e) => setContact(e.target.value)} placeholder="Duty doctor, clinic phone" /></label>
        <fieldset style={{ border: 0, padding: 0, display: "grid", gap: 8 }}>
          <legend>Acknowledge within (minutes)</legend>
          <label>Urgent<input type="number" min={5} max={10080} value={red} onChange={(e) => setRed(Number(e.target.value))} /></label>
          <label>Watch<input type="number" min={5} max={10080} value={watch} onChange={(e) => setWatch(Number(e.target.value))} /></label>
          <label>Review<input type="number" min={5} max={10080} value={review} onChange={(e) => setReview(Number(e.target.value))} /></label>
        </fieldset>
        <fieldset style={{ border: 0, padding: 0 }}>
          <legend><CalendarClock size={15} aria-hidden="true" /> Weekly rota</legend>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" }}>
            <thead><tr><th scope="col" style={{ textAlign: "left" }}>Day</th><th scope="col" style={{ textAlign: "left" }}>On duty</th><th scope="col" style={{ textAlign: "left" }}>Backup</th></tr></thead>
            <tbody>
              {DAYS.map((d) => (
                <tr key={d}>
                  <th scope="row" style={{ textAlign: "left", fontWeight: 600 }}>{DAY_WORDS[d]}</th>
                  <td>
                    <select aria-label={`${DAY_WORDS[d]} on duty`} value={rota[d]?.primaryUserId ?? ""} onChange={(e) => setDay(d, "primaryUserId", e.target.value)}>
                      <option value="">Nobody</option>
                      {assignees.map((a) => <option key={a.userId} value={a.userId}>{a.displayName}</option>)}
                    </select>
                  </td>
                  <td>
                    <select aria-label={`${DAY_WORDS[d]} backup`} value={rota[d]?.backupUserId ?? ""} onChange={(e) => setDay(d, "backupUserId", e.target.value)} disabled={!rota[d]?.primaryUserId}>
                      <option value="">No backup</option>
                      {assignees.filter((a) => a.userId !== rota[d]?.primaryUserId).map((a) => <option key={a.userId} value={a.userId}>{a.displayName}</option>)}
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </fieldset>
        <button className="button-primary" disabled={busy}>{busy ? "Saving…" : "Save settings"}</button>
      </form>
      <p className={homeStyles.staffCaution}>These times are this clinic&apos;s own policy. Khabar makes no response-time promise of its own.</p>
    </section>
  )
}

/** Who did what in the clinic. Cases appear by reference only. */
export function ActivityPanel() {
  const [items, setItems] = useState<ClinicActivity[] | null>(null)
  const [error, setError] = useState("")
  const load = useCallback(async () => {
    try { setItems(await apiRequest<ClinicActivity[]>("/api/clinic/activity")) }
    catch (e) { setError(e instanceof Error ? e.message : "Activity could not be loaded.") }
  }, [])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  return (
    <section className={homeStyles.peopleCard}>
      <SectionHeading eyebrow="Audit" title="Clinic activity" action={<button className="button-quiet" onClick={() => void load()}><Activity size={15} aria-hidden="true" /> Refresh</button>} />
      {error && <p role="alert" className={styles.dialogError}>{error}</p>}
      {items && items.length === 0 && <p>No activity yet.</p>}
      <ol className={styles.history} aria-label="Clinic activity, newest first">
        {items?.map((a, i) => (
          <li key={i}>
            <strong>{new Date(a.at).toLocaleString([], { dateStyle: "short", timeStyle: "short" })}</strong> · {a.by} {ACTION_WORDS[a.action] ?? a.action.toLowerCase()} · {a.subject}
          </li>
        ))}
      </ol>
      <p className={homeStyles.staffCaution}>Cases are shown by reference, never by patient name.</p>
    </section>
  )
}

/** Whether each outside service is working, so a gap is visible rather than assumed away. */
export function IntegrationsPanel() {
  const [items, setItems] = useState<Integration[] | null>(null)
  const [error, setError] = useState("")
  const load = useCallback(async () => {
    try { setItems(await apiRequest<Integration[]>("/api/clinic/integrations")) }
    catch (e) { setError(e instanceof Error ? e.message : "Integration health could not be loaded.") }
  }, [])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  return (
    <section className={homeStyles.peopleCard}>
      <SectionHeading eyebrow="Operations" title="Integration health" action={<button className="button-quiet" onClick={() => void load()}><PlugZap size={15} aria-hidden="true" /> Check again</button>} />
      {error && <p role="alert" className={styles.dialogError}>{error}</p>}
      <div className={homeStyles.staffRows}>
        {items?.map((i) => (
          <article className={homeStyles.staffRow} key={i.name}>
            <div><strong>{i.name}</strong><span>{i.detail}</span></div>
            <StatusBadge level={INTEGRATION_LEVEL[i.status] ?? "review"}>{i.status.toLowerCase()}</StatusBadge>
          </article>
        ))}
      </div>
    </section>
  )
}
