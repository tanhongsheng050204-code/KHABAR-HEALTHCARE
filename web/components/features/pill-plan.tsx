"use client"

import { useEffect, useState } from "react"
import { CloudSun, Moon, Sun, Sunrise, Sunset, Utensils } from "lucide-react"
import { apiRequest } from "@/lib/api"
import { useUi } from "@/lib/i18n"
import type { PlanSlot } from "@/lib/types"
import styles from "./features.module.css"

const SLOT_ICON = { sahur: Sunrise, morning: Sun, afternoon: CloudSun, evening: Sunset, berbuka: Sunset, night: Moon } as const

/** Pill colours tell one medicine from another; every pill also carries its name, so colour is never the only cue. */
const PILL_COLOURS = ["var(--tidal-bright)", "var(--amber)", "var(--blue)", "var(--night-2)"]

function Pill({ colour, half }: { colour: string; half: boolean }) {
  return (
    <svg viewBox="0 0 40 20" width="40" height="20" aria-hidden="true" className={styles.pill}>
      <rect x="1" y="1" width="38" height="18" rx="9" fill="var(--paper)" stroke={colour} strokeWidth="2" />
      <path d="M20 1 H30 A9 9 0 0 1 30 19 H20 Z" fill={colour} />
      {half && <rect x="1" y="1" width="19" height="18" rx="9" fill="var(--paper)" opacity="0.85" />}
    </svg>
  )
}

/**
 * Today's medicines as pictures: one row per time of day with a sun or moon, a pill drawn for each tablet, the
 * medicine's name, and whether it goes before or after food. Straight from the doctor's signed plan.
 */
export function PillPlan() {
  const { t } = useUi()
  const [slots, setSlots] = useState<PlanSlot[] | null>(null)
  useEffect(() => {
    let live = true
    apiRequest<PlanSlot[]>("/api/me/medicines-today").then((s) => { if (live) setSlots(s) }).catch(() => { if (live) setSlots([]) })
    return () => { live = false }
  }, [])
  if (!slots || slots.length === 0) return null
  const names = [...new Set(slots.flatMap((s) => s.medicines.map((m) => m.name)))]
  const colour = (name: string) => PILL_COLOURS[names.indexOf(name) % PILL_COLOURS.length]
  return (
    <section className={styles.pillPlan} aria-label={t("My medicines today")}>
      <h2>{t("My medicines today")}</h2>
      {slots.map((s) => {
        const Icon = SLOT_ICON[s.slot as keyof typeof SLOT_ICON] ?? Sun
        return <div key={s.slot} className={styles.pillSlot}>
          <div className={styles.pillWhen}><Icon size={36} aria-hidden="true" /><strong>{t(s.slot)}</strong><span>{s.time}</span></div>
          <ul>
            {s.medicines.map((m) => {
              const whole = Math.floor(m.units)
              const half = m.units - whole >= 0.5
              const count = Math.min(6, whole + (half ? 1 : 0))
              return <li key={m.name}>
                <span className={styles.pills} aria-label={t("{n} tablets", { n: m.units })}>
                  {Array.from({ length: count }, (_, i) => <Pill key={i} colour={colour(m.name)} half={half && i === count - 1} />)}
                </span>
                <span><strong>{m.name}</strong>{m.strengthMg ? ` ${m.strengthMg} mg` : ""}</span>
                {m.timing && <span className={styles.food}><Utensils size={16} aria-hidden="true" /> {m.timing === "before_food" ? t("Before food") : t("After food")}</span>}
              </li>
            })}
          </ul>
        </div>
      })}
    </section>
  )
}
