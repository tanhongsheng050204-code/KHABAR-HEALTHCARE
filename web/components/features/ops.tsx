"use client"

import { useCallback, useEffect, useState } from "react"
import { FileText, Scale, Upload } from "lucide-react"
import Link from "next/link"
import { apiRequest } from "@/lib/api"
import type { ImportResult, WorkloadBoard } from "@/lib/types"
import { SectionHeading, StatusBadge } from "@/components/ui"
import { featureMissing } from "./missing"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

type Notice = { tone: "error" | "success" | "info"; text: string }

/** Who holds which follow-up cases; managers can spread the unowned ones evenly, most urgent first. */
export function WorkloadPanel({ notify, canBalance }: { notify: (n: Notice) => void; canBalance: boolean }) {
  const [board, setBoard] = useState<WorkloadBoard | null>(null)
  const [busy, setBusy] = useState(false)
  const load = useCallback(async () => {
    try { setBoard(await apiRequest<WorkloadBoard>("/api/clinic/workload")) } catch (e) { if (!featureMissing(e)) setBoard(null) }
  }, [])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  async function balance() {
    setBusy(true)
    try {
      const result = await apiRequest<{ assigned: number; board: WorkloadBoard }>("/api/clinic/workload/balance", { method: "POST" })
      setBoard(result.board)
      notify({ tone: "success", text: result.assigned ? `${result.assigned} case${result.assigned === 1 ? "" : "s"} assigned. Each one is in the case history.` : "Nothing was unowned." })
    } catch (e) {
      notify({ tone: "error", text: e instanceof Error ? e.message : "The cases could not be balanced." })
    } finally {
      setBusy(false)
    }
  }
  if (!board) return null
  return (
    <section className={homeStyles.peopleCard} aria-label="Workload">
      <SectionHeading eyebrow="Team" title="Who holds the follow-up work"
        action={canBalance ? <button className="button-secondary" disabled={busy || board.unowned === 0} onClick={() => void balance()}><Scale size={14} aria-hidden="true" /> Balance {board.unowned} unowned</button> : undefined} />
      <div className={styles.panel}>
        {board.unowned > 0 && <p className={styles.lead}>{board.unowned} open case{board.unowned === 1 ? "" : "s"} with no owner{board.unownedUrgent ? `, ${board.unownedUrgent} urgent` : ""}.</p>}
        <table className={styles.weights}>
          <thead><tr><th>Staff</th><th>Open</th><th>Urgent</th><th>Past ack time</th><th>Closed this week</th><th>Calls this week</th></tr></thead>
          <tbody>{board.staff.map((p) => <tr key={p.userId}>
            <td>{p.name} <span className={styles.muted}>{p.role.toLowerCase().replace("_", " ")}</span></td>
            <td>{p.open}</td>
            <td>{p.urgent ? <StatusBadge level="red">{p.urgent}</StatusBadge> : 0}</td>
            <td>{p.overdue ? <StatusBadge level="watch">{p.overdue}</StatusBadge> : 0}</td>
            <td>{p.closedThisWeek}</td>
            <td>{p.callsThisWeek}</td>
          </tr>)}</tbody>
        </table>
        <p className={styles.muted}>Balancing gives each unowned case to whoever holds the fewest, most urgent first, and logs it like a manual assignment.</p>
      </div>
    </section>
  )
}

const SAMPLE = "name,ic,phone,language,condition\nRahim bin Ali,590312-10-5566,012-700 0002,BM,diabetes\n"

/** Onboarding: bring the patient list in from a spreadsheet saved as CSV, checked row by row before saving. */
export function ImportPatientsPanel({ notify }: { notify: (n: Notice) => void }) {
  const [csv, setCsv] = useState("")
  const [xlsx, setXlsx] = useState<{ name: string; base64: string } | null>(null)
  const [preview, setPreview] = useState<ImportResult | null>(null)
  const [busy, setBusy] = useState(false)
  async function run(dryRun: boolean) {
    setBusy(true)
    try {
      const result = await apiRequest<ImportResult>(`/api/clinic/patients/import?dryRun=${dryRun}`, { method: "POST", body: JSON.stringify(xlsx ? { xlsxBase64: xlsx.base64 } : { csv }) })
      setPreview(result)
      if (result.saved) {
        notify({ tone: "success", text: `${result.ok} patient${result.ok === 1 ? "" : "s"} added.${result.problems ? ` ${result.problems} row${result.problems === 1 ? " was" : "s were"} left out.` : ""}` })
        setCsv("")
        setXlsx(null)
      }
    } catch (e) {
      notify({ tone: "error", text: e instanceof Error ? e.message : "The file could not be read." })
    } finally {
      setBusy(false)
    }
  }
  async function readFile(file: File | undefined) {
    if (!file) return
    setPreview(null)
    if (file.name.toLowerCase().endsWith(".xlsx")) {
      const bytes = new Uint8Array(await file.arrayBuffer())
      let binary = ""
      for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000))
      setXlsx({ name: file.name, base64: btoa(binary) })
      setCsv("")
      return
    }
    setXlsx(null)
    setCsv(await file.text())
  }
  return (
    <section className={homeStyles.peopleCard} aria-label="Import patients">
      <SectionHeading eyebrow="Set up" title="Bring in your patient list" />
      <div className={styles.panel}>
        <p className={styles.lead}>Upload an Excel (.xlsx) or CSV file whose first row names the columns <code>name, ic, phone, language, condition</code>. Khabar checks every row first; nothing is saved until you confirm, and rows with problems are left out.</p>
        <div className={styles.row}>
          <label className={styles.field}>Excel or CSV file<input type="file" accept=".xlsx,.csv,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={(e) => void readFile(e.target.files?.[0])} /></label>
          <button className="button-quiet" onClick={() => { setCsv(SAMPLE); setXlsx(null); setPreview(null) }}>Use an example</button>
          {xlsx && <span className={styles.muted}>{xlsx.name} ready</span>}
        </div>
        <label className={styles.field}>Or paste rows
          <textarea rows={4} value={csv} onChange={(e) => { setCsv(e.target.value); setXlsx(null); setPreview(null) }} placeholder={SAMPLE} />
        </label>
        <div className={styles.row}>
          <button className="button-secondary" disabled={busy || (!csv.trim() && !xlsx)} onClick={() => void run(true)}><FileText size={14} aria-hidden="true" /> Check rows</button>
          {preview && !preview.saved && preview.ok > 0 && <button className="button-primary" disabled={busy} onClick={() => void run(false)}><Upload size={14} aria-hidden="true" /> Add {preview.ok} patient{preview.ok === 1 ? "" : "s"}</button>}
        </div>
        {preview && <table className={styles.weights}>
          <thead><tr><th>Row</th><th>Name</th><th>IC</th><th>Language</th><th>Condition</th><th>Check</th></tr></thead>
          <tbody>{preview.rows.map((r) => <tr key={r.line}>
            <td>{r.line}</td><td>{r.fullName}</td><td>{r.icMasked ?? "—"}</td><td>{r.language ?? "?"}</td><td>{r.condition?.toLowerCase().replace("_", "-") ?? "—"}</td>
            <td>{r.status === "OK" ? <StatusBadge level="ok">ok</StatusBadge> : <span><StatusBadge level={r.status === "DUPLICATE" ? "watch" : "red"}>{r.status.toLowerCase()}</StatusBadge> {r.problems.join(" ")}</span>}</td>
          </tr>)}</tbody>
        </table>}
      </div>
    </section>
  )
}

/** A link to the printable monthly report. */
export function ReportLink() {
  return (
    <section className={homeStyles.peopleCard} aria-label="Outcomes report">
      <SectionHeading eyebrow="For your owner or funder" title="Monthly outcomes report" />
      <div className={styles.panel}>
        <p className={styles.lead}>One page of this month&apos;s follow-up, counted from your own records: who was reached, how fast the clinic responded, doses, missed visits and HbA1c. Print it or save it as a PDF.</p>
        <Link className="button-secondary" href="/home/report"><FileText size={14} aria-hidden="true" /> Open the report</Link>
      </div>
    </section>
  )
}
