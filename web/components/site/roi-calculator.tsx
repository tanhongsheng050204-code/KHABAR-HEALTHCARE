"use client"

import { useId, useState } from "react"
import styles from "./site.module.css"

type Field = { key: keyof Inputs; label: string; hint: string; min: number; max: number; step: number }
type Inputs = { patients: number; nurseRate: number; minutesSaved: number; visitsRecovered: number; visitValue: number; pricePerPatient: number }

const FIELDS: Field[] = [
  { key: "patients", label: "Patients you follow up", hint: "People with diabetes, high blood pressure or after an operation", min: 10, max: 5000, step: 10 },
  { key: "minutesSaved", label: "Nurse minutes saved per patient per month", hint: "Calls not needed because the reply said all is well", min: 0, max: 60, step: 1 },
  { key: "nurseRate", label: "Nurse cost per hour (RM)", hint: "Salary and overheads", min: 0, max: 200, step: 1 },
  { key: "visitsRecovered", label: "Missed visits rebooked per month", hint: "From reminders and no-show follow-up", min: 0, max: 500, step: 1 },
  { key: "visitValue", label: "Income from one visit (RM)", hint: "What a follow-up visit brings in", min: 0, max: 500, step: 1 },
  { key: "pricePerPatient", label: "Khabar price per patient per month (RM)", hint: "Example price; change it to compare", min: 0, max: 50, step: 0.5 },
]

const rm = (n: number) => `RM ${Math.round(n).toLocaleString("en-MY")}`

/**
 * The clinic's own numbers in, an estimate out. Every default is a placeholder for the clinic to replace; nothing
 * here is a claim about what Khabar will save. After a month, the impact panel shows the clinic's real counts.
 */
export function RoiCalculator() {
  const id = useId()
  const [inputs, setInputs] = useState<Inputs>({ patients: 300, nurseRate: 25, minutesSaved: 10, visitsRecovered: 8, visitValue: 35, pricePerPatient: 3 })
  const hours = (inputs.patients * inputs.minutesSaved) / 60
  const staffValue = hours * inputs.nurseRate
  const visitIncome = inputs.visitsRecovered * inputs.visitValue
  const cost = inputs.patients * inputs.pricePerPatient
  const net = staffValue + visitIncome - cost
  return (
    <div className={styles.roi}>
      <form className={styles.roiInputs} onSubmit={(e) => e.preventDefault()} aria-label="Your clinic's numbers">
        {FIELDS.map((f) => <label key={f.key} htmlFor={`${id}-${f.key}`}>
          <span>{f.label}</span>
          <input id={`${id}-${f.key}`} type="number" inputMode="decimal" min={f.min} max={f.max} step={f.step} value={inputs[f.key]}
            onChange={(e) => setInputs({ ...inputs, [f.key]: Math.max(f.min, Math.min(f.max, Number(e.target.value) || 0)) })} />
          <small>{f.hint}</small>
        </label>)}
      </form>
      <div className={styles.roiResult} aria-live="polite">
        <p className={styles.eyebrow}>Your estimate, per month</p>
        <dl>
          <div><dt>Nurse time freed</dt><dd>{Math.round(hours)} hours · {rm(staffValue)}</dd></div>
          <div><dt>Visits rebooked</dt><dd>{inputs.visitsRecovered} · {rm(visitIncome)}</dd></div>
          <div><dt>Khabar at your price</dt><dd>{rm(cost)}</dd></div>
          <div className={styles.roiNet}><dt>{net >= 0 ? "Estimated net gain" : "Estimated net cost"}</dt><dd>{rm(Math.abs(net))}</dd></div>
        </dl>
        <p className={styles.roiNote}>
          These are your numbers, not Khabar&apos;s claims. Start with a pilot and check them against the counts in your
          clinic&apos;s impact panel and monthly report.
        </p>
      </div>
    </div>
  )
}
