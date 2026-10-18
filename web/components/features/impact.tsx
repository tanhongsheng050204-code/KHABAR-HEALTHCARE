"use client"

import { useCallback, useEffect, useState } from "react"
import { apiRequest } from "@/lib/api"
import type { Impact } from "@/lib/types"
import { SectionHeading } from "@/components/ui"
import { featureMissing } from "./missing"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

/** What follow-up did for the clinic in the last 30 days, counted from its own records. */
export function ImpactPanel() {
  const [impact, setImpact] = useState<Impact | null>(null)
  const [days, setDays] = useState(30)
  const [error, setError] = useState("")
  const [missing, setMissing] = useState(false)
  const load = useCallback(async () => {
    try { setImpact(await apiRequest<Impact>(`/api/clinic/impact?days=${days}`)) }
    catch (e) { if (featureMissing(e)) setMissing(true); else setError(e instanceof Error ? e.message : "The impact summary could not be loaded.") }
  }, [days])
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])
  if (missing || (!impact && !error)) return null
  return (
    <section className={homeStyles.peopleCard} id="impact" aria-label="What follow-up did">
      <SectionHeading eyebrow={`Last ${days} days`} title="What follow-up did for this clinic"
        action={<select aria-label="Period" value={days} onChange={(e) => setDays(Number(e.target.value))} className={styles.periodSelect}>
          <option value={7}>7 days</option><option value={30}>30 days</option><option value={90}>90 days</option>
        </select>} />
      <div className={styles.panel}>
        {error && <p role="alert">{error}</p>}
        {impact && <div className={styles.tiles}>
          {impact.metrics.map((m) => <div className={styles.tile} key={m.key}>
            <strong>{m.value}{m.of != null && <small> of {m.of}</small>}</strong>
            <span>{m.label}</span>
            {m.note && <em>{m.note}</em>}
          </div>)}
          <div className={styles.tile}>
            <strong>{impact.medianAckMinutes != null ? <>{impact.medianAckMinutes}<small> min</small></> : "—"}</strong>
            <span>Median time to acknowledge a case</span>
            {impact.medianAckMinutes == null && <em>No acknowledged cases in this period.</em>}
          </div>
        </div>}
        <p className={styles.muted}>Counted from this clinic&apos;s own records. Nothing here is an estimate or a comparison with other clinics.</p>
      </div>
    </section>
  )
}
