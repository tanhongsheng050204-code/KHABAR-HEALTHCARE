"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { ArrowLeft, Printer } from "lucide-react"
import { apiRequest } from "@/lib/api"
import { getToken } from "@/lib/session"
import type { OutcomesReportData } from "@/lib/types"
import styles from "./report.module.css"

function months(count: number) {
  const out: string[] = []
  const d = new Date()
  for (let i = 0; i < count; i++) {
    const m = new Date(d.getFullYear(), d.getMonth() - i, 1)
    out.push(`${m.getFullYear()}-${String(m.getMonth() + 1).padStart(2, "0")}`)
  }
  return out
}

/** The monthly outcomes report as a printable page. "Save as PDF" is the browser's own print dialog. */
export function OutcomesReportPage() {
  const [month, setMonth] = useState(months(1)[0])
  const [report, setReport] = useState<OutcomesReportData | null>(null)
  const [error, setError] = useState("")
  useEffect(() => {
    if (!getToken()) { window.location.replace("/login"); return }
    let live = true
    apiRequest<OutcomesReportData>(`/api/clinic/report?month=${month}`)
      .then((r) => { if (live) { setReport(r); setError("") } })
      .catch((e) => { if (live) setError(e instanceof Error ? e.message : "The report could not be loaded.") })
    return () => { live = false }
  }, [month])
  const monthName = (m: string) => new Intl.DateTimeFormat("en-MY", { month: "long", year: "numeric" }).format(new Date(`${m}-01T12:00:00`))
  return (
    <main className={styles.page}>
      <div className={styles.toolbar}>
        <Link href="/home"><ArrowLeft size={15} aria-hidden="true" /> Back to clinic</Link>
        <select aria-label="Month" value={month} onChange={(e) => setMonth(e.target.value)}>
          {months(12).map((m) => <option key={m} value={m}>{monthName(m)}</option>)}
        </select>
        <button className="button-primary" onClick={() => window.print()}><Printer size={15} aria-hidden="true" /> Print or save as PDF</button>
      </div>
      {error && <p role="alert">{error}</p>}
      {report && <article className={styles.sheet}>
        <header>
          <p className={styles.kicker}>Follow-up outcomes · {monthName(report.month)}{report.monthInProgress ? " (so far)" : ""}</p>
          <h1>{report.clinicName}</h1>
          <p className={styles.muted}>{report.from} to {report.to} · generated {new Date(report.generatedAt).toLocaleString("en-MY")}</p>
        </header>
        {report.sections.map((s) => <section key={s.title}>
          <h2>{s.title}</h2>
          <table>
            <tbody>{s.lines.map((l) => <tr key={l.key}>
              <th scope="row">{l.label}{l.note && <small>{l.note}</small>}</th>
              <td><strong>{l.value}</strong>{l.of != null && <span> of {l.of}{l.of > 0 && ` · ${Math.round((l.value / l.of) * 100)}%`}</span>}</td>
            </tr>)}</tbody>
          </table>
        </section>)}
        <footer className={styles.muted}>
          Counted from this clinic&apos;s own Khabar records. No estimates, projections or comparisons with other clinics; no patient is named.
          Khabar is a prototype and not a medical device.
        </footer>
      </article>}
    </main>
  )
}
