"use client"

import { useCallback, useEffect, useState } from "react"
import { FlaskConical, Plus, Route, TrendingUp, X } from "lucide-react"
import { apiRequest } from "@/lib/api"
import type { LabEntry, PathwayDefinition, PathwayEntry, RiskRow, RiskWeight, TrendSummary } from "@/lib/types"
import { SectionHeading, StatusBadge } from "@/components/ui"
import { featureMissing } from "./missing"
import { TrendChart } from "./trend-chart"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

type Notice = { tone: "error" | "success" | "info"; text: string }

const OVERALL: Record<string, { level: string; word: string }> = {
  IMPROVING: { level: "ok", word: "improving" },
  WORSENING: { level: "red", word: "worsening" },
  FLAT: { level: "review", word: "steady" },
  NOT_ENOUGH_DATA: { level: "review", word: "not enough data" },
}

/** The last 30 days for a doctor: readings against the pathway targets, dose answers and lab results. */
export function PatientTrendsPanel({ patientId }: { patientId: string }) {
  const [trends, setTrends] = useState<TrendSummary | null>(null)
  const [days, setDays] = useState(30)
  useEffect(() => {
    let live = true
    apiRequest<TrendSummary>(`/api/patients/${patientId}/trends?days=${days}`).then((t) => { if (live) setTrends(t) }).catch(() => undefined)
    return () => { live = false }
  }, [patientId, days])
  if (!trends) return null
  const overall = OVERALL[trends.overall] ?? OVERALL.NOT_ENOUGH_DATA
  return (
    <section className={homeStyles.peopleCard} aria-label="Trends">
      <SectionHeading eyebrow={`Last ${days} days`} title="How the patient is doing"
        action={<span className={styles.row}>
          <StatusBadge level={overall.level}>{overall.word}</StatusBadge>
          <select aria-label="Period" value={days} onChange={(e) => setDays(Number(e.target.value))} className={styles.periodSelect}>
            <option value={14}>14 days</option><option value={30}>30 days</option><option value={90}>90 days</option>
          </select>
        </span>} />
      <div className={styles.panel}>
        <p className={styles.muted}>Directions compare the last 7 days with the 7 before. They describe what the patient sent, not a diagnosis.</p>
        <div className={styles.trendGrid}>
          {trends.series.map((s) => <TrendChart key={s.key} series={s} />)}
        </div>
        {trends.doses.length > 0 && <DoseBars trends={trends} />}
        {trends.notes.map((n) => <p key={n} className={styles.muted}>{n}</p>)}
      </div>
    </section>
  )
}

/** One column per day: taken (filled), missed (red), not answered (outline); a legend names all three. */
function DoseBars({ trends }: { trends: TrendSummary }) {
  const max = Math.max(...trends.doses.map((d) => d.sent), 1)
  return (
    <div className={styles.adherence}>
      <div className={styles.rowBetween}>
        <strong>Dose answers by day</strong>
        <span className={`${styles.direction} ${styles[`dir_${trends.doseDirection}`] ?? ""}`}>{(OVERALL[trends.doseDirection] ?? OVERALL.NOT_ENOUGH_DATA).word}</span>
      </div>
      <div className={styles.doseBars} role="list">
        {trends.doses.map((d) => {
          const unanswered = d.sent - d.taken - d.missed
          return <div key={d.day} role="listitem" className={styles.doseBar} style={{ height: `${(d.sent / max) * 100}%` }}
            title={`${d.day}: ${d.taken} taken, ${d.missed} missed, ${unanswered} not answered`} tabIndex={0}
            aria-label={`${d.day}: ${d.taken} taken, ${d.missed} missed, ${unanswered} not answered`}>
            {unanswered > 0 && <span className={styles.seg_review} style={{ flexGrow: unanswered }} />}
            {d.missed > 0 && <span className={styles.seg_red} style={{ flexGrow: d.missed }} />}
            {d.taken > 0 && <span className={styles.seg_ok} style={{ flexGrow: d.taken }} />}
          </div>
        })}
      </div>
      <div className={styles.legend}>
        <span><i className={styles.seg_ok} /> Taken</span>
        <span><i className={styles.seg_red} /> Missed</span>
        <span><i className={styles.seg_review} /> Not answered</span>
      </div>
    </div>
  )
}

/** Condition pathways: the doctor starts one with default targets, changes targets, or ends it. */
export function PathwaysPanel({ patientId, notify, canEdit = true }: { patientId: string; notify: (n: Notice) => void; canEdit?: boolean }) {
  const [list, setList] = useState<PathwayEntry[] | null>(null)
  const [definitions, setDefinitions] = useState<PathwayDefinition[]>([])
  const [adding, setAdding] = useState("")
  const [editing, setEditing] = useState<string | null>(null)
  const [draft, setDraft] = useState<Record<string, string>>({})
  const [missing, setMissing] = useState(false)
  const load = useCallback(async () => {
    try {
      setList(await apiRequest<PathwayEntry[]>(`/api/patients/${patientId}/pathways`))
      setDefinitions(await apiRequest<PathwayDefinition[]>("/api/pathways"))
    } catch (e) { if (featureMissing(e)) setMissing(true) }
  }, [patientId])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  async function act(work: () => Promise<unknown>, done: string) {
    try { await work(); notify({ tone: "success", text: done }); await load() }
    catch (e) { notify({ tone: "error", text: e instanceof Error ? e.message : "That did not work." }) }
  }
  const num = (v: string | undefined) => v === undefined || v === "" ? undefined : Number(v)

  if (missing || !list) return null
  const available = definitions.filter((d) => !list.some((p) => p.pathway === d.key))
  return (
    <section className={homeStyles.peopleCard} aria-label="Condition pathways">
      <SectionHeading eyebrow="Care pathway" title="Conditions followed" />
      <div className={styles.panel}>
        {list.length === 0 && <p className={styles.muted}>Not on a pathway yet. A pathway sets targets for the trends and risk score, and adds a line to the daily check-in.</p>}
        <ul className={styles.log}>
          {list.map((p) => <li key={p.id}>
            <div className={styles.rowBetween}>
              <strong><Route size={14} aria-hidden="true" /> {p.label}</strong>
              <span className={styles.row}>
                {p.reviewDue && <StatusBadge level="watch">review due</StatusBadge>}
                {canEdit && <button className="button-quiet" onClick={() => { setEditing(editing === p.id ? null : p.id); setDraft({}) }}>Targets</button>}
                {canEdit && <button className="button-quiet" aria-label={`End ${p.label}`} onClick={() => { if (window.confirm(`End the ${p.label} pathway?`)) void act(() => apiRequest(`/api/patients/${patientId}/pathways/${p.id}`, { method: "DELETE" }), "Pathway ended.") }}><X size={14} aria-hidden="true" /></button>}
              </span>
            </div>
            <span className={styles.muted}>
              {[p.targets.glucoseMax != null && `sugar ≤ ${p.targets.glucoseMax} mmol/L`, p.targets.systolicMax != null && `BP ≤ ${p.targets.systolicMax}/${p.targets.diastolicMax}`,
                p.targets.hba1cMax != null && `HbA1c ≤ ${p.targets.hba1cMax}%`].filter(Boolean).join(" · ") || "No numeric targets"}
              {p.nextReviewOn && ` · next review ${p.nextReviewOn}`}
            </span>
            {editing === p.id && <form className={styles.row} onSubmit={(e) => {
              e.preventDefault()
              void act(() => apiRequest(`/api/patients/${patientId}/pathways/${p.id}`, { method: "PUT", body: JSON.stringify({
                glucoseMax: num(draft.glucoseMax), systolicMax: num(draft.systolicMax), diastolicMax: num(draft.diastolicMax),
                hba1cMax: num(draft.hba1cMax), nextReviewOn: draft.nextReviewOn || undefined }) }), "Targets saved.").then(() => setEditing(null))
            }}>
              {p.targets.glucoseMax != null && <label className={styles.field}>Sugar ≤<input type="number" step="0.1" defaultValue={p.targets.glucoseMax} onChange={(e) => setDraft({ ...draft, glucoseMax: e.target.value })} /></label>}
              {p.targets.systolicMax != null && <label className={styles.field}>BP top ≤<input type="number" defaultValue={p.targets.systolicMax} onChange={(e) => setDraft({ ...draft, systolicMax: e.target.value })} /></label>}
              {p.targets.diastolicMax != null && <label className={styles.field}>BP bottom ≤<input type="number" defaultValue={p.targets.diastolicMax} onChange={(e) => setDraft({ ...draft, diastolicMax: e.target.value })} /></label>}
              {p.targets.hba1cMax != null && <label className={styles.field}>HbA1c ≤<input type="number" step="0.1" defaultValue={p.targets.hba1cMax} onChange={(e) => setDraft({ ...draft, hba1cMax: e.target.value })} /></label>}
              <label className={styles.field}>Next review<input type="date" defaultValue={p.nextReviewOn ?? ""} onChange={(e) => setDraft({ ...draft, nextReviewOn: e.target.value })} /></label>
              <button className="button-primary">Save</button>
            </form>}
          </li>)}
        </ul>
        {canEdit && available.length > 0 && <form className={styles.row} onSubmit={(e) => {
          e.preventDefault()
          if (adding) void act(() => apiRequest(`/api/patients/${patientId}/pathways`, { method: "POST", body: JSON.stringify({ pathway: adding }) }), "Pathway started.").then(() => setAdding(""))
        }}>
          <label className={styles.field}>Start a pathway
            <select value={adding} onChange={(e) => setAdding(e.target.value)}>
              <option value="">Choose…</option>
              {available.map((d) => <option key={d.key} value={d.key}>{d.label}</option>)}
            </select>
          </label>
          <button className="button-secondary" disabled={!adding}><Plus size={14} aria-hidden="true" /> Start</button>
        </form>}
      </div>
    </section>
  )
}

const LAB_TESTS = [
  { code: "HBA1C", label: "HbA1c", unit: "%" },
  { code: "LDL", label: "LDL cholesterol", unit: "mmol/L" },
  { code: "EGFR", label: "eGFR", unit: "mL/min/1.73m²" },
  { code: "CREATININE", label: "Creatinine", unit: "µmol/L" },
]

/** Lab results, newest first, each against its target; the doctor adds new ones. */
export function LabsPanel({ patientId, notify, canEdit = true }: { patientId: string; notify: (n: Notice) => void; canEdit?: boolean }) {
  const [labs, setLabs] = useState<LabEntry[] | null>(null)
  const [test, setTest] = useState("HBA1C")
  const [value, setValue] = useState("")
  const [takenOn, setTakenOn] = useState("")
  const [missing, setMissing] = useState(false)
  const load = useCallback(async () => {
    try { setLabs(await apiRequest<LabEntry[]>(`/api/patients/${patientId}/labs`)) }
    catch (e) { if (featureMissing(e)) setMissing(true) }
  }, [patientId])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  async function add(e: React.FormEvent) {
    e.preventDefault()
    try {
      await apiRequest(`/api/patients/${patientId}/labs`, { method: "POST", body: JSON.stringify({ test, value: Number(value), takenOn: takenOn || undefined }) })
      setValue("")
      notify({ tone: "success", text: "Lab result added." })
      await load()
    } catch (err) {
      notify({ tone: "error", text: err instanceof Error ? err.message : "The result could not be added." })
    }
  }
  if (missing || !labs) return null
  const STATUS: Record<string, { level: string; word: string }> = {
    IN_TARGET: { level: "ok", word: "in target" }, OUT_OF_TARGET: { level: "watch", word: "above target" }, NO_TARGET: { level: "review", word: "recorded" },
  }
  return (
    <section className={homeStyles.peopleCard} aria-label="Lab results">
      <SectionHeading eyebrow="Laboratory" title="Lab results" />
      <div className={styles.panel}>
        {labs.length === 0 ? <p className={styles.muted}>No lab results yet.</p> : <ul className={styles.log}>
          {[...labs].reverse().map((l) => <li key={l.id}>
            <div className={styles.rowBetween}>
              <span><FlaskConical size={14} aria-hidden="true" /> <strong>{l.label} {l.value} {l.unit}</strong>{l.target != null && <span className={styles.muted}> · target {l.test === "EGFR" ? "≥" : "≤"} {l.target}</span>}</span>
              <span className={styles.row}><StatusBadge level={(STATUS[l.status] ?? STATUS.NO_TARGET).level}>{l.test === "EGFR" && l.status === "OUT_OF_TARGET" ? "below target" : (STATUS[l.status] ?? STATUS.NO_TARGET).word}</StatusBadge>
                <span className={styles.muted}>{l.takenOn}</span></span>
            </div>
          </li>)}
        </ul>}
        {canEdit && <form className={styles.row} onSubmit={add}>
          <label className={styles.field}>Test
            <select value={test} onChange={(e) => setTest(e.target.value)}>{LAB_TESTS.map((t) => <option key={t.code} value={t.code}>{t.label}</option>)}</select>
          </label>
          <label className={styles.field}>Value ({LAB_TESTS.find((t) => t.code === test)?.unit})
            <input type="number" step="0.1" required value={value} onChange={(e) => setValue(e.target.value)} />
          </label>
          <label className={styles.field}>Taken on<input type="date" value={takenOn} onChange={(e) => setTakenOn(e.target.value)} /></label>
          <button className="button-secondary" disabled={!value}><Plus size={14} aria-hidden="true" /> Add</button>
        </form>}
      </div>
    </section>
  )
}

/** The call list's risk chip: the score, with every reason one tap away. */
export function RiskChip({ score, reasons }: { score?: number | null; reasons?: string[] }) {
  if (score == null || score === 0) return null
  return (
    <details className={styles.riskChip}>
      <summary aria-label={`Risk score ${score}`}><TrendingUp size={13} aria-hidden="true" /> risk {score}</summary>
      <ul>{(reasons ?? []).map((r) => <li key={r}>{r}</li>)}</ul>
    </details>
  )
}

/** Patients slipping: the five highest follow-up risk scores in the clinic, with why. */
export function SlippingPanel() {
  const [rows, setRows] = useState<RiskRow[] | null>(null)
  useEffect(() => {
    let live = true
    apiRequest<RiskRow[]>("/api/clinic/risk").then((r) => { if (live) setRows(r) }).catch(() => undefined)
    return () => { live = false }
  }, [])
  if (!rows) return null
  const top = rows.filter((r) => r.score > 0).slice(0, 5)
  return (
    <section className={homeStyles.peopleCard} aria-label="Patients slipping">
      <SectionHeading eyebrow="Risk score" title="Who is slipping" />
      <div className={styles.panel}>
        {top.length === 0 ? <p className={styles.muted}>Nobody in follow-up has risk points right now.</p> : <ul className={styles.log}>
          {top.map((r) => <li key={r.patientId}>
            <div className={styles.rowBetween}>
              <a href={`/home/patients/${r.patientId}`}><strong>{r.fullName}</strong></a>
              <StatusBadge level={r.score >= 5 ? "red" : r.score >= 3 ? "watch" : "review"}>score {r.score}</StatusBadge>
            </div>
            <span className={styles.muted}>{r.reasons.map((x) => x.label).join(" · ")}</span>
          </li>)}
        </ul>}
        <p className={styles.muted}>Points for what went wrong lately; the rules and points are listed in Staff &amp; access. A way to sort, not a prediction.</p>
      </div>
    </section>
  )
}

/** The clinic's own points for each risk rule. Doctors and administrators change them. */
export function RiskWeightsPanel({ notify, canManage }: { notify: (n: Notice) => void; canManage: boolean }) {
  const [weights, setWeights] = useState<RiskWeight[] | null>(null)
  useEffect(() => {
    let live = true
    apiRequest<RiskWeight[]>("/api/clinic/risk-weights").then((w) => { if (live) setWeights(w) }).catch(() => undefined)
    return () => { live = false }
  }, [])
  async function change(key: string, points: number) {
    try {
      setWeights(await apiRequest<RiskWeight[]>("/api/clinic/risk-weights", { method: "PUT", body: JSON.stringify({ [key]: points }) }))
      notify({ tone: "success", text: "Risk points saved." })
    } catch (e) {
      notify({ tone: "error", text: e instanceof Error ? e.message : "The points could not be saved." })
    }
  }
  if (!weights) return null
  return (
    <section className={homeStyles.peopleCard} aria-label="Risk score rules">
      <SectionHeading eyebrow="Call list" title="Risk score rules" />
      <div className={styles.panel}>
        <p className={styles.lead}>Each rule adds points to a patient&apos;s score. The score orders patients with the same urgency on the call list; it never moves an urgent reply down.</p>
        <table className={styles.weights}>
          <thead><tr><th>Rule</th><th>Points</th></tr></thead>
          <tbody>{weights.map((w) => <tr key={w.key}>
            <td>{w.label}</td>
            <td>{canManage
              ? <input type="number" min={0} max={10} aria-label={`Points for: ${w.label}`} defaultValue={w.points}
                  onBlur={(e) => { const v = Number(e.target.value); if (v !== w.points) void change(w.key, v) }} />
              : w.points}{w.points !== w.defaultPoints && <span className={styles.muted}> (default {w.defaultPoints})</span>}</td>
          </tr>)}</tbody>
        </table>
      </div>
    </section>
  )
}
