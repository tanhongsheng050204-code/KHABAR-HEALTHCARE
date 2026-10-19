"use client"

import { useEffect, useState } from "react"
import { apiRequest } from "@/lib/api"
import { useUi } from "@/lib/i18n"
import type { TrendSummary } from "@/lib/types"
import { SectionHeading } from "@/components/ui"
import { TrendChart } from "./trend-chart"
import homeStyles from "@/app/home/home.module.css"
import styles from "./features.module.css"

const TITLE: Record<string, string> = { GLUCOSE: "Blood sugar", SYSTOLIC: "Blood pressure (top)", DIASTOLIC: "Blood pressure (bottom)" }

/** The patient's own 30 days of readings against the targets their doctor set. Shown only when there are readings. */
export function MyTrendsPanel({ patientId }: { patientId: string }) {
  const { t, locale } = useUi()
  const [trends, setTrends] = useState<TrendSummary | null>(null)
  useEffect(() => {
    let live = true
    apiRequest<TrendSummary>(`/api/patients/${patientId}/trends?days=30`).then((s) => { if (live) setTrends(s) }).catch(() => undefined)
    return () => { live = false }
  }, [patientId])
  const series = trends?.series.filter((s) => s.key !== "DIASTOLIC") ?? []
  if (series.length === 0) return null
  return (
    <section className={homeStyles.peopleCard} aria-label={t("Your last 30 days")}>
      <SectionHeading eyebrow={t("Your readings")} title={t("Your last 30 days")} />
      <div className={styles.panel}>
        <p className={styles.muted}>{t("The dashed line is the target your doctor set. If a reading worries you, check in or call your clinic.")}</p>
        <div className={styles.trendGrid}>
          {series.map((s) => <TrendChart key={s.key} series={s} locale={locale} titleWord={t(TITLE[s.key] ?? s.label)} t={t} />)}
        </div>
      </div>
    </section>
  )
}
